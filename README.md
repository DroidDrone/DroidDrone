## What is DroidDrone

With the DroidDrone you can easily control your [INAV](https://github.com/iNavFlight/inav) or [Betaflight](https://github.com/betaflight/betaflight) drone via the Internet. You only need two Android phones (requires version 7.0 or later) - one is on the drone and connects to the flight controller with the OTG USB and the other connects to your remote controller.

## Install and configure

You have to install the [Flight](https://github.com/IvanSchulz/DroidDrone/blob/master/Flight/release/Flight-release.apk?raw=true) and [Control](https://github.com/IvanSchulz/DroidDrone/blob/master/Control/release/Control-release.apk?raw=true) apps on the phones.
Flight app controls your drone via MSP commands, so you need to change your receiver type to MSP in your INAV or Betaflight configurator:

![Receiver](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_receiver.JPG?raw=true)

> In the Modes tab you can also add a Camera Control 2 Mode to start or stop video recording.

### Network connection

The mobile phones usually do not have a public IP address and you have two options to connect both together:
1. Use **Direct connection mode** - if both phones are in the same VPN network (or Wi-Fi network for testing).

   You should connect the control app to another phone's IP.
   > Note that the VPN connection has a little more delay due to encryption.
   
2. **Connect over server** - you start a [Server app](https://github.com/IvanSchulz/DroidDrone/blob/master/Server/release/DD_Server.zip?raw=true) (Java) on your PC or virtual server and connect both phones to its public IP address.
   
   ![Server](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_server.JPG?raw=true)

You can also use your home PC as a server, but you have to configure port forwarding (default is 6286 / UDP) in your router and allow this port in the firewall first (and don't forget to turn off the sleep mode...).
> Of course, your router should have a public IP for it to work, but most internet providers give one.

If you use the server app, you can also connect other phones as viewers. You need to configure a different key for viewers. The default key is "DD" for both - controller and viewer.

### Settings & Usage

In the Control app you can configure various settings, such as Camera ID. If your phone has a wide-angle camera, it's best to use it. A wide-angle camera usually has ID 2 or 3, but on some phones, it can be different (my old Redmi has ID 21...).

> Cameras with high frame rates have lower internal latency, but wide-angle cameras on most Android phones work only with 30 FPS. You may be able to use the main camera with an external wide-angle lens.

Before the first flight you also need to check in the settings of control app that the channels mapping is correct for your remote controller:

![Channels map](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_channels_map.jpg?raw=true)

The channels mapping in your FC (AERT, TAER etc. in your receiver tab) is automatically recognized.

**Note that every time before connect the control app you need to move all sticks and switches on your transmitter to correctly recognize all channels. Some channels can only work as two position switches via USB.**

**Please only use the DroidDrone if your drone has a GPS and return to home (GPS rescue) works correctly. It's recommended to configure a switch for this in the Modes tab.**

**For your first flight you should use Angle or GPS Hold mode, only when everything is running smoothly you can switch to Acro.**

The Flight App can run as a service when your screen turned off.
**The Control app must always be in the foreground. If it is not in the foreground or screen will be turned off then it causes a failsafe.**

Not all OSD elements (but many important ones) are supported by DroidDrone.

![OSD](https://github.com/IvanSchulz/DroidDrone/blob/master/Resources/Screenshots/Scr_osd.jpg?raw=true)

At the top of the screen you can see the specific OSD items such as the battery level for both phones, camera and OpenGL FPS, video bitrate and network latency. You can also start and stop video recording with the button on the right.
The recorded video should be saved in the "Android/media/de.droiddrone.flight/Video" folder.

#### DroidDrone currently supports:

* INAV version 7.0.0, 7.1.0 (MSP API 2.5)
* Betaflight 4.4.2, 4.4.3 (MSP API 1.45, 1.46)

## Todo
* ArduPilot support
* Google Maps tracking
* External USB cameras support
