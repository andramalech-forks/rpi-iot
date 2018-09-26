package de.sonsts.rpi.i2c.sensor;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.i2c.I2CDevice;

import de.sonsts.rpi.i2c.sensor.ADXL345.DataRate;
import de.sonsts.rpi.i2c.sensor.utils.AccellerometerMesurement;
import de.sonsts.rpi.i2c.sensor.utils.BitUtils;
import de.sonsts.rpi.i2c.sensor.utils.Calibration;
import de.sonsts.rpi.iot.communication.common.Quality;

// /boot/config.txt
// dtparam=i2c1=on
// dtparam=i2c_arm_baudrate=xxx

public class ADXL345 implements Runnable
{
    final static Logger logger = LogManager.getLogger();

    public enum DataRate
    {
        DR3200(0x0F, 3200), DR1600(0x0E, 1600), DR800(0x0D, 800), DR400(0x0C, 400), DR200(0x0B, 200), DR100(0x0A, 100), DR50(0x09, 50), DR25(
                0x08, 25);

        private int mRateCode;
        private int mOutputDataRate;

        private DataRate(int rateCode, int outputDataRate)
        {
            mRateCode = rateCode;
            mOutputDataRate = outputDataRate;
        }

        public int getOutputDataRate()
        {
            return mOutputDataRate;
        }

        public int getBandwidth()
        {
            return mOutputDataRate / 2;
        }

        public int getRateCode()
        {
            return mRateCode;
        }

        public long getMs()
        {
            return (long) ((double) 1 / (double) getOutputDataRate() * 1000);
        }
    }

    public enum CommunicationInterface
    {
        I2C, SPI
    }

    public enum FifoMode
    {
        FFBYPASS(0x00), FFFIFO(0x01 << 6), FFSTREAM(0x02 << 6), FFTRIGGER(0x03 << 6);

        private int mMode;

        private FifoMode(int mode)
        {
            mMode = mode;
        }

        public int getValue()
        {
            return mMode;
        }
    }

    public enum Range
    {
        R2G(0), R4G(1), R8G(2), R16G(3);

        private int mRange;

        private Range(int range)
        {
            mRange = range;
        }

        public int getValue()
        {
            return mRange;
        }
    }

    private final static double SENSORS_GRAVITY_EARTH = (9.80665F);
    private final static double SENSORS_GRAVITY_MOON = (1.6F);
    private final static double SENSORS_GRAVITY_SUN = (275.0F);
    private final static double SENSORS_GRAVITY_STANDARD = (SENSORS_GRAVITY_EARTH);
    private final static double SENSORS_MAGFIELD_EARTH_MAX = (60.0F);
    private final static double SENSORS_MAGFIELD_EARTH_MIN = (30.0F);
    private final static double SENSORS_PRESSURE_SEALEVELHPA = (1013.25F);
    private final static double SENSORS_DPS_TO_RADS = (0.017453293F);
    private final static double SENSORS_GAUSS_TO_MICROTESLA = (100);

    public final static int ADXL345_DEVID = 0x53;

    private final static int ADXL345_REG_R_DEVID = 0x00;// R DeviceID
    // private final static int ADXL345_RESERVED = 0x01 to 0x1C ;// Reserved do not access
    private final static int ADXL345_REG_RW_THRESH_TAP = 0x1D;// R/W Tap threshold
    private final static int ADXL345_REG_RW_OFSX = 0x1E;// R/W X-axis offset
    private final static int ADXL345_REG_RW_OFSY = 0x1F;// R/W Y-axis offset
    private final static int ADXL345_REG_RW_OFSZ = 0x20;// R/W Z-axis offset
    private final static int ADXL345_REG_RW_DUR = 0x21;// R/W Tap duration
    private final static int ADXL345_REG_RW_Latent = 0x22;// R/W Tap latency
    private final static int ADXL345_REG_RW_Window = 0x23;// R/W Tap window
    private final static int ADXL345_REG_RW_THRESH_ACT = 0x24;// R/W Activity threshold
    private final static int ADXL345_REG_RW_THRESH_INACT = 0x25;// R/W Inactivity threshold
    private final static int ADXL345_REG_RW_TIME_INACT = 0x26;// R/W Inactivity time
    private final static int ADXL345_REG_RW_ACT_INACT_CTL = 0x27;// R/W Axis enable control for activity and inactivity detection
    private final static int ADXL345_REG_RW_THRESH_FF = 0x28;// R/W Free-fall threshold
    private final static int ADXL345_REG_RW_TIME_FF = 0x29;// R/W Free-fall time
    private final static int ADXL345_REG_RW_TAP_AXES = 0x2A;// R/W Axis control for single tap/double tap
    private final static int ADXL345_REG_RW_ACT_TAP_STATUS = 0x2B;// R Source of single tap/double tap
    private final static int ADXL345_REG_RW_BW_RATE = 0x2C;// R/W Data rate and power mode control
    private final static int ADXL345_REG_RW_POWER_CTL = 0x2D;// R/W Power-saving features control
    private final static int ADXL345_REG_RW_INT_EN = 0x2E;// R/W Interrupt enable control
    private final static int ADXL345_REG_RW_INT_MAP = 0x2F;// R/W Interrupt mapping control
    private final static int ADXL345_REG_R_INT_SOURCE = 0x30;// R Source of interrupts
    private final static int ADXL345_REG_RW_DATA_FORMAT = 0x31;// R/W Data format control
    private final static int ADXL345_REG_R_DATAX0 = 0x32;// R X-AxisData 0
    private final static int ADXL345_REG_R_DATAX1 = 0x33;// R X-AxisData 1
    private final static int ADXL345_REG_R_DATAY0 = 0x34;// R Y-AxisData 0
    private final static int ADXL345_REG_R_DATAY1 = 0x35;// R Y-AxisData 1
    private final static int ADXL345_REG_R_DATAZ0 = 0x36;// R Z-AxisData 0
    private final static int ADXL345_REG_R_DATAZ1 = 0x37;// R Z-AxisData 1
    private final static int ADXL345_REG_RW_FIFO_CTL = 0x38;// R/W FIFOcontrol
    private final static int ADXL345_REG_R_FIFO_STATUS = 0x39;// R FIFOstatus

    private final static int ADXL345_MSK_ACT_INACT_CTL_ACT_AC_DC = (1 << 7);// 0 > dc-coupled operation, 1 > ac-coupled operation
    private final static int ADXL345_MSK_ACT_INACT_CTL_ACT_X = (1 << 6);
    private final static int ADXL345_MSK_ACT_INACT_CTL_ACT_Y = (1 << 5);
    private final static int ADXL345_MSK_ACT_INACT_CTL_ACT_Z = (1 << 4);
    private final static int ADXL345_MSK_ACT_INACT_CTL_INACT_AC_DC = (1 << 3);// 0 > dc-coupled operation, 1 > ac-coupled operation
    private final static int ADXL345_MSK_ACT_INACT_CTL_INACT_X = (1 << 2);
    private final static int ADXL345_MSK_ACT_INACT_CTL_INACT_Y = (1 << 1);
    private final static int ADXL345_MSK_ACT_INACT_CTL_INACT_Z = (1 << 0);

    private final static int ADXL345_MSK_TAP_AXES_SUPRESS = (1 << 3);
    private final static int ADXL345_MSK_TAP_AXES_TAP_X_EN = (1 << 2);
    private final static int ADXL345_MSK_TAP_AXES_TAP_Y_EN = (1 << 1);
    private final static int ADXL345_MSK_TAP_AXES_TAP_Z_EN = (1 << 0);

    private final static int ADXL345_MSK_ACT_TAP_STATUS_ACT_X_SRC = (1 << 6);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_ACT_Y_SRC = (1 << 5);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_ACT_Z_SRC = (1 << 4);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_ASLEEP = (1 << 3);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_TAP_X_SRC = (1 << 2);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_TAP_Y_SRC = (1 << 1);
    private final static int ADXL345_MSK_ACT_TAP_STATUS_TAP_Z_SRC = (1 << 0);

    private final static int ADXL345_MSK_BW_RATE_LOW_POWER = (1 << 0);
    private final static int ADXL345_MSK_BW_RATE_RATE = ((1 << 3) | (1 << 2) | (1 << 1) | (1 << 0));

    private final static int ADXL345_MSK_POWER_CTL_LINK = (1 << 5);
    private final static int ADXL345_MSK_POWER_CTL_AUTO_SLEEP = (1 << 4);
    private final static int ADXL345_MSK_POWER_CTL_MEASURE = (1 << 3);
    private final static int ADXL345_MSK_POWER_CTL_SLEEP = (1 << 2);
    private final static int ADXL345_MSK_POWER_CTL_WAKEUP = ((1 << 1) | (1 << 0));

    private final static int ADXL345_MSK_INT_EN_DATA_READY = (1 << 7);
    private final static int ADXL345_MSK_INT_EN_SINGLE_TAP = (1 << 6);
    private final static int ADXL345_MSK_INT_EN_DOUBLE_TAP = (1 << 5);
    private final static int ADXL345_MSK_INT_EN_ACTIVITY = (1 << 4);
    private final static int ADXL345_MSK_INT_EN_INACTIVITY = (1 << 3);
    private final static int ADXL345_MSK_INT_EN_FREE_FALL = (1 << 2);
    private final static int ADXL345_MSK_INT_EN_WATERMARK = (1 << 1);
    private final static int ADXL345_MSK_INT_EN_OVERRUN = (1 << 0);

    private final static int ADXL345_MSK_INT_MAP_DATA_READY = (1 << 7);
    private final static int ADXL345_MSK_INT_MAP_SINGLE_TAP = (1 << 6);
    private final static int ADXL345_MSK_INT_MAP_DOUBLE_TAP = (1 << 5);
    private final static int ADXL345_MSK_INT_MAP_ACTIVITY = (1 << 4);
    private final static int ADXL345_MSK_INT_MAP_INACTIVITY = (1 << 3);
    private final static int ADXL345_MSK_INT_MAP_FREE_FALL = (1 << 2);
    private final static int ADXL345_MSK_INT_MAP_WATERMARK = (1 << 1);
    private final static int ADXL345_MSK_INT_MAP_OVERRUN = (1 << 0);

    private final static int ADXL345_MSK_INT_SRC_DATA_READY = (1 << 7);
    private final static int ADXL345_MSK_INT_SRC_SINGLE_TAP = (1 << 6);
    private final static int ADXL345_MSK_INT_SRC_DOUBLE_TAP = (1 << 5);
    private final static int ADXL345_MSK_INT_SRC_ACTIVITY = (1 << 4);
    private final static int ADXL345_MSK_INT_SRC_INACTIVITY = (1 << 3);
    private final static int ADXL345_MSK_INT_SRC_FREE_FALL = (1 << 2);
    private final static int ADXL345_MSK_INT_SRC_WATERMARK = (1 << 1);
    private final static int ADXL345_MSK_INT_SRC_OVERRUN = (1 << 0);

    private final static int ADXL345_MSK_DATA_FORMAT_SELF_TEST = (1 << 7);
    private final static int ADXL345_MSK_DATA_FORMAT_SPI = (1 << 6);
    private final static int ADXL345_MSK_DATA_FORMAT_INT_INVERT = (1 << 5);
    private final static int ADXL345_MSK_DATA_FORMAT_FULL_RES = (1 << 3);
    private final static int ADXL345_MSK_DATA_FORMAT_JUSTIFY = (1 << 2);
    private final static int ADXL345_MSK_DATA_FORMAT_RANGE = ((1 << 1) | (1 << 0));

    private final static int ADXL345_MSK_FIFO_CTL_FIFO_MODE = (1 << 0);
    private final static int ADXL345_MSK_FIFO_CTL_TRIGGER = (1 << 0);
    private final static int ADXL345_MSK_FIFO_CTL_SAMPLES = ((1 << 4) | (1 << 3) | (1 << 2) | (1 << 1) | (1 << 0));

    private final static int ADXL345_MSK_FIFO_STATUS_FIFO_TRIG = (1 << 7);
    private final static int ADXL345_MSK_FIFO_STATUS_ENTRIES = ((1 << 5) | (1 << 4) | (1 << 3) | (1 << 2) | (1 << 1) | (1 << 0));
    public static final int SAMPLECOUNT = 32;

    private final double ADXL345_MG2G_MULTIPLIER = (0.004); // 4mg per lsb

    private I2CDevice mAdxl345;

    private Range mRange;

    private DataRate mBaudRate;
    private Queue<AccellerometerMesurement> mSamples = new LinkedList<AccellerometerMesurement>();
    private double mGain = ADXL345_MG2G_MULTIPLIER * SENSORS_GRAVITY_STANDARD;
    private Calibration[] mCalibration;
    private CommunicationInterface mCommunicationInterface;

    private static double getMax(double[] da)
    {
        double max = da[0];

        for (double d : da)
        {
            max = Math.max(max, d);
        }

        return max;
    }

    private static double getMin(double[] da)
    {
        double min = da[0];

        for (double d : da)
        {
            min = Math.min(min, d);
        }

        return min;
    }

    private static double getMean(double[] da)
    {
        double mean = 0;

        for (double d : da)
        {
            mean += d;
        }

        return mean / da.length;
    }

    /*
     * To be called after configuration, before measuring
     */
    private void init() throws Exception
    {
        if (null == mAdxl345)
        {
            throw new Exception("Device not initialized");
        }

        mCalibration = new Calibration[3];

        mCalibration[Axis.X.getValue()] = new Calibration(0, 0, 0);
        mCalibration[Axis.Y.getValue()] = new Calibration(0, 0, 0);
        mCalibration[Axis.Z.getValue()] = new Calibration(0, 0, 0);

        writeRegister(ADXL345_REG_RW_BW_RATE, ADXL345_MSK_BW_RATE_RATE, mBaudRate.getRateCode());
        writeRegister(ADXL345_REG_RW_FIFO_CTL, ADXL345_MSK_FIFO_CTL_FIFO_MODE, FifoMode.FFFIFO.getValue());
        writeRegister(ADXL345_REG_RW_FIFO_CTL, ADXL345_MSK_FIFO_CTL_SAMPLES, 0x0F);
        writeRegister(ADXL345_REG_RW_DATA_FORMAT, ADXL345_MSK_DATA_FORMAT_RANGE, mRange.getValue());
        writeRegister(ADXL345_REG_RW_POWER_CTL, ADXL345_MSK_POWER_CTL_MEASURE);
    }

    private double readValue(Axis axis) throws Exception
    {
        int lA = 0, hA = 0;
        byte lV = 0, hV = 0;

        switch (axis)
        {
            case X:
                lA = ADXL345_REG_R_DATAX0;
                hA = ADXL345_REG_R_DATAX1;
                break;
            case Y:
                lA = ADXL345_REG_R_DATAY0;
                hA = ADXL345_REG_R_DATAY1;
                break;
            case Z:
                lA = ADXL345_REG_R_DATAZ0;
                hA = ADXL345_REG_R_DATAZ1;
                break;
            default:
                break;
        }

        lV = (byte) i2cRead(lA);
        hV = (byte) i2cRead(hA);

        return toDouble(hV, lV) * ADXL345_MG2G_MULTIPLIER * SENSORS_GRAVITY_STANDARD;
    }

    private int i2cRead(int reg) throws Exception
    {
        int result = 0;

        result = mAdxl345.read(reg);

        return result;
    }

    private int i2cRead(int reg, byte[] buffer) throws Exception
    {
        int result = 0;

        result = mAdxl345.read(reg, buffer, 0, buffer.length);

        return result;
    }

    public ADXL345(I2CDevice dev, DataRate dataRate, Range range, CommunicationInterface communicationInterface) throws Exception
    {
        if ((null == dev) || (null == dataRate) || (null == range))
        {
            throw new IllegalArgumentException("Parameter can't be null");
        }

        mAdxl345 = dev;
        mBaudRate = dataRate;
        mRange = range;
        mCommunicationInterface = communicationInterface;

        init();
        calibrate();
    }

    private void writeRegister(int register, int mask, int value) throws Exception
    {
        int current = i2cRead(register);
        int newValue = BitUtils.setValue(value, current, mask);

        mAdxl345.write(register, (byte) newValue);
    }

    private void writeRegister(int regiter, int value) throws Exception
    {
        writeRegister(regiter, 0xFF, value);
    }

    public Calibration calibrate(Axis axis) throws Exception
    {
        double[] buffer = new double[20];

        for (int i = 0; i < 20; i++)
        {
            // consecutive reads to the same register
            buffer[i] = readValue(axis);
        }

        return new Calibration(getMean(buffer), getMax(buffer), getMin(buffer));
    }

    public void calibrate() throws Exception
    {
        mCalibration[Axis.X.getValue()] = calibrate(Axis.X);
        mCalibration[Axis.Y.getValue()] = calibrate(Axis.Y);
        mCalibration[Axis.Z.getValue()] = calibrate(Axis.Z);
    }

    @Override
    public void run()
    {
        try
        {
            int entries = 0;
            entries = i2cRead(ADXL345_REG_R_FIFO_STATUS) & ADXL345_MSK_FIFO_STATUS_ENTRIES;

            if ((SAMPLECOUNT <= entries) && (1 > mSamples.size()))
            {
                bufferSamples(entries);
                entries = i2cRead(ADXL345_REG_R_FIFO_STATUS) & ADXL345_MSK_FIFO_STATUS_ENTRIES;
            }
        }
        catch (Exception e1)
        {
            e1.printStackTrace();
        }
    }

    private static int toDouble(int high, int low)
    {
        int retVal = 0;

        retVal = (high << 8) | (low);

        return retVal;
    }

    private void bufferSamples(int entries) throws Exception
    {
        AccellerometerMesurement measurement = new AccellerometerMesurement();

        long timeStamp = System.currentTimeMillis();
        long ms = mBaudRate.getMs();

        for (int i = entries - 1; i >= 0; i--)
        {
            byte[] buffer = new byte[6];

            int count = i2cRead(ADXL345_REG_R_DATAX0, buffer);
            if (buffer.length != count)
            {
                throw new Exception("Read failed");
            }

            double values = 0;

            long sampleTimeStamp = timeStamp - (long) (i * ms);
            for (int j = 0; j < 3; j++)
            {
                values = toDouble(buffer[j * 2 + 1], buffer[j * 2]) * mGain;
                measurement.add(Axis.values()[j], sampleTimeStamp, values, Quality.GOOD);
            }
        }

        mSamples.add(measurement);
    }

    public AccellerometerMesurement getMeasurement()
    {
        return mSamples.poll();
    }
}
