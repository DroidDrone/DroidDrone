## What is DroidDrone

With the DroidDrone you can easily control your [INAV](https://github.com/iNavFlight/inav), [ArduPilot](https://github.com/ArduPilot/ardupilot), [Betaflight](https://github.com/betaflight/betaflight) or [PX4](https://github.com/PX4/PX4-Autopilot) drone via the Internet. You only need two Android phones (requires min version 8, Android 11+ is recommended) - one is on the drone and connects to the flight controller with the OTG USB and the other connects to your remote controller.

## Install and configure

You have to install the Flight and Control apps on the phones. You can download them from the [Releases](https://github.com/DroidDrone/DroidDrone/releases) page.

### INAV or Betaflight setup
Flight app controls the INAV or Betaflight drone via MSP commands, so you need to change your receiver type to MSP in your configurator:

![Receiver](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_receiver.JPG?raw=true)

> In the Modes tab you can also add a Camera Control 2 Mode to start or stop video recording.

### ArduPilot setup
ArduPilot should work with the defaults. However, you need to check these settings: Mavlink target system ID should match the parameter SYSID_THISMAV (default 1) in ArduPilot settings and Mavlink GCS system ID - the SYSID_MYGCS (default 255). Ignore MAVLink Overrides bitmask in the RC_OPTIONS parameter must be deactivated. You can set the RC_OVERRIDE_TIME parameter to one second (default is 3).

> You can set the "Camera Record Video" RC option (166) to the free channel to start and stop video recording on Android.

### Connect to the FC
You can use a USB to UART/TTL converter and connect it to the MSP/Mavlink enabled UART or simply connect the phone to the FC's USB port. If you are using an Android mini PC with GPIO and native serial port, you can connect it directly to the UART. In the control app settings, you should then enable the native serial port, set the port address and the FC protocol (MSP or Mavlink). The settings are sent to the flight app when the connection is established.

> If you want to use an Android mini PC, make sure it supports a hardware accelerated H265 (HEVC) encoder.

### Network connection
The mobile phones usually do not have a public IP address and you have two options to connect both together:
1. Use **Direct connection mode** - if both phones are in the same VPN network (or Wi-Fi network for testing).

   You should connect the control app to another phone's local IP.

   You can use the [ZeroTier](https://github.com/zerotier/ZeroTierOne) as a free VPN server (however, bandwidth may be limited on the free plan) - register an account and create your network. Then you can download the ZeroTier One app and connect your phones.
   > Note that the VPN connection has a little more delay due to encryption.
   
3. **Connect over server** - you start a Server app (Java) on your PC or virtual server and connect both phones to its public IP address.
   
   ![Server](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_server.JPG?raw=true)
   
> Java JRE is required to run the server app. You can download it from [here](https://www.java.com/en/download/).

You can also use your home PC as a server, but you have to configure port forwarding (default is 6286 / UDP) in your router and allow this port in the firewall first (and don't forget to turn off the sleep mode...).
> Of course, your router should have a public IP for it to work, but most internet providers give one.

If you use the server app, you can also connect other phones as viewers. You need to configure a different key for viewers. The default key is "DD" for both - controller and viewer.

### Settings & Usage

In the Control app you can configure various settings, such as Camera ID (for internal phone cameras). If your phone has a wide-angle camera, it's best to use it. A wide-angle camera usually has ID 2 or 3, but on some phones, it can be different (my old Redmi has ID 21...).

You can also connect an external USB camera (UVC) using the USB OTG splitter (however, some power-hungry cameras may not work stably via phone OTG splitter, please check before flying).

You can configure up to three cameras and then switch them during the flight.

> Cameras with high frame rates have lower internal latency.

Before the first flight you also need to check in the settings of control app that the channels mapping is correct for your remote controller:

![Channels map](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_channels_map.jpg?raw=true)

The channels mapping in your FC is automatically recognized.

You can assign up to three RC channels to the phone gyroscope axis to use them for camera head tracking in VR mode.

**Note that every time before connect the control app you need to move all sticks and switches on your transmitter to correctly recognize all channels. Some channels can only work as two position switches via USB.**

**Please only use the DroidDrone if your drone has a GPS and return to home/launch (GPS rescue) works correctly. It's recommended to configure a switch for this mode.**

**For your first flight you should use Angle/Stabilize or position Hold mode, only when everything is running smoothly you can switch to Acro.**

The Flight App can run as a service when your screen turned off.
**The Control app must always be in the foreground. If it is not in the foreground or screen will be turned off then it causes a failsafe.**

> It's probably a good idea to set the power saving settings for the Flight app to "No restrictions", but I don't have any problems with the default settings either.

Not all OSD elements (but many important ones) are supported by DroidDrone.

![OSD](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_osd.jpg?raw=true)

At the top of the screen you can see the specific OSD items such as the battery level and cellular signal strength for both phones, camera and OpenGL FPS, video bitrate and network latency. You can also start and stop video recording with the button on the right.
The recorded video should be saved in the "Android/media/de.droiddrone.flight/Video" folder.

#### QGroundControl or Mission Planner connect

If you are using an ArduPilot or PX4 drone, you can connect it to QGroundControl or Mission Planner via UDP. You need to configure the Mavlink UDP bridge in the control App settings:
- Send to connected IP - Use it when GCS is running on the same PC as the DroidDrone server.
- Send to specific IP - If the GCS PC has a public IP or is on the same VPN network.
- Send to control device and then forward to IP - If the GCS PC is behind the NAT but in the same local network as the control app.

Note that UDP port 14550 should be open in your router/firewall.

#### DroidDrone currently supports:

* INAV version 7+ ([MSP API](https://github.com/iNavFlight/inav/blob/master/src/main/msp/msp_protocol.h) 2.5)
* Betaflight 4.4.2+ ([MSP API](https://github.com/betaflight/betaflight/blob/master/src/main/msp/msp_protocol.h) 1.45, 1.46, 1.47)
* ArduPilot 4.5.4+ ([Mavlink](https://mavlink.io/en/messages/common.html) 2.3)
* PX4 1.15.0+ ([Mavlink](https://mavlink.io/en/messages/common.html) 2.3)

## Contributing & Development

You can create a fork and contribute your pull requests to this project or create issues, discussions and documentation.

You should use the latest Android Studio to compile the app sources and the Eclipse IDE for Server.
To create a sprites map you can use the Adobe Air ShoeBox app and the BMFont for font textures.
