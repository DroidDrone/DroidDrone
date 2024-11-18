/*
 *  This file is part of DroidDrone.
 *
 *  DroidDrone is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DroidDrone is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DroidDrone.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.droiddrone.common;

public class SettingsCommon {
    // increase when the Control/Flight app is not compatible with the previous version (UDP packets changed, new added etc.)
    public static final int versionCompatibleCode = 9;

    // default settings
    public static final String ip = "";
    public static final int port = UdpCommon.defaultPort;
    public static final String key = "DD";
    public static final int connectionMode = ConnectionMode.overServer;
    public static final boolean isViewer = false;
    public static final int viewersCount = 2;
    public static final boolean useUsbCamera = false;
    public static final int usbCameraFrameFormat = UsbCameraFrameFormat.MJPEG;
    public static final boolean usbCameraReset = true;
    public static final int cameraResolutionWidth = 1920;
    public static final int cameraResolutionHeight = 1080;
    public static final int cameraFpsMin = 30;
    public static final int cameraFpsMax = 60;
    public static final int bitrateLimit = 6000000;
    public static final boolean useExtraEncoder = true;
    public static final int videoRecorderCodec = VideoRecorderCodec.AVC;
    public static final int recordedVideoBitrate = 20000000;
    public static final boolean sendAudioStream = false;
    public static final int audioStreamBitrate = 96000;
    public static final boolean recordAudio = true;
    public static final int recordedAudioBitrate = 192000;
    public static final int telemetryRefreshRate = 10;
    public static final int rcRefreshRate = 25;
    public static final int serialBaudRate = 115200;
    public static final int usbSerialPortIndex = 0;
    public static final boolean useNativeSerialPort = false;
    public static final String nativeSerialPort = "/dev/ttyS0";
    public static final int fcProtocol = FcCommon.FC_PROTOCOL_AUTO;
    public static final int mavlinkTargetSysId = 1;
    public static final int mavlinkGcsSysId = 255;
    public static final boolean connectOnStartup = false;
    public static final boolean invertVideoAxisX = false;
    public static final boolean invertVideoAxisY = false;
    public static final boolean drawOsd = true;
    public static final boolean showDronePhoneBattery = true;
    public static final boolean showControlPhoneBattery = true;
    public static final boolean showNetworkState = true;
    public static final boolean showCameraFps = true;
    public static final boolean showScreenFps = false;
    public static final boolean showVideoBitrate = true;
    public static final boolean showPing = true;
    public static final boolean showVideoRecordButton = true;
    public static final boolean showVideoRecordIndication = true;
    public static final int osdTextColor = 0xFFFFFFFF;
    public static final int vrMode = VrMode.off;
    public static final int vrFrameScale = 100;
    public static final int vrFrameScaleMin = 50;
    public static final int vrFrameScaleMax = 200;
    public static final int vrCenterOffset = 250;
    public static final int vrCenterOffsetMin = 0;
    public static final int vrCenterOffsetMax = 500;
    public static final int vrOsdOffset = 10;
    public static final int vrOsdOffsetMin = 0;
    public static final int vrOsdOffsetMax = 100;
    public static final int vrOsdScale = 100;
    public static final int vrOsdScaleMin = 20;
    public static final int vrOsdScaleMax = 150;
    public static final boolean vrHeadTracking = false;
    public static final int headTrackingAngleLimit = 180;
    public static final int headTrackingAngleLimitMin = 45;
    public static final int headTrackingAngleLimitMax = 360;
    public static final int gyroSamplingPeriodUs = 16666;
    public static final int maxCamerasCount = 3;
    public static final int currentCameraIndex = 0;
    public static final boolean[] cameraEnabled = {true, false, false};
    public static final String[] cameraId = {"0", "1", "3"};
    public static final int mavlinkUdpBridge = MavlinkUdpBridge.disabled;
    public static final String mavlinkUdpBridgeIp = "";
    public static final int mavlinkUdpBridgePort = 14550;

    public static class MavlinkUdpBridge {
        public static final int disabled = 0;
        public static final int connectedIp = 1;
        public static final int specificIp = 2;
        public static final int redirectFromControlDevice = 3;
    }

    public static class VrMode {
        public static final int off = 0;
        public static final int singleCamera = 1;
        public static final int stereoCamera = 2;
    }

    public static class ConnectionMode {
        public static final int overServer = 0;
        public static final int direct = 1;
    }

    public static class UsbCameraFrameFormat {
        public static final int YUV2 = 0;
        public static final int MJPEG = 1;
        public static final int H264 = 2;
    }

    public static class VideoRecorderCodec {
        public static final int AVC = 0;
        public static final int HEVC = 1;
    }
}
