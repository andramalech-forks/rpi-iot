```
################################################################################
#!/bin/sh
#
#  Copyright (c) 2018, Michael Sonst, All Rights Reserved.
# 
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
# 
#  http://www.apache.org/licenses/LICENSE-2.0
# 
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
################################################################################


# rpi-iot
sudo apt-get -y install git
git clone https://github.com/msonst/rpi-iot.git
sudo chmod +x rpi-iot/rpi-iot-server/server-install.sh
sudo chmod +x rpi-iot/rpi-iot-thing/thing-install.sh

# Server
cd rpi-iot/rpi-iot-server
./server-install.sh

# Thing
cd rpi-iot/rpi-iot-thing
./thing-install.sh

```