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

package de.droiddrone.flight;

import static de.droiddrone.common.Logcat.log;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.MediaCommon;
import de.droiddrone.common.UdpCommon;

public class Config {
    private final MainActivity activity;
    private final int versionCode;
    private String ip;
    private int port;
    private String key;
    private int connectionMode;
    private String cameraId;
    private boolean useUsbCamera;
    private int usbCameraFrameFormat;
    private boolean usbCameraReset;
    private int cameraResolutionWidth;
    private int cameraResolutionHeight;
    private int cameraFpsMin;
    private int cameraFpsMax;
    private int bitrateLimit;
    private boolean useExtraEncoder;
    private int videoRecorderCodec;
    private int recordedVideoBitrate;
    private boolean sendAudioStream;
    private int audioStreamBitrate;
    private boolean recordAudio;
    private int recordedAudioBitrate;
    private int telemetryRefreshRate;
    private int rcRefreshRate;
    private int serialBaudRate;
    private int usbSerialPortIndex;
    private boolean useNativeSerialPort;
    private String nativeSerialPort;
    private int fcProtocol;
    private int mavlinkTargetSysId;
    private int mavlinkGcsSysId;
    private boolean connectOnStartup;

    public Config(MainActivity activity, int versionCode) {
        this.activity = activity;
        this.versionCode = versionCode;
        loadConfig();
    }

    public String getIp(){
        return ip;
    }

    public String getKey(){
        return key;
    }

    public int getPort(){
        return port;
    }

    public int getConnectionMode(){
        return connectionMode;
    }

    public String getCameraId() {
        return cameraId;
    }

    public boolean isUseUsbCamera(){
        return useUsbCamera;
    }

    public int getUsbCameraFrameFormat(){
        return usbCameraFrameFormat;
    }

    public boolean isUsbCameraReset(){
        return usbCameraReset;
    }

    public int getCameraResolutionWidth() {
        return cameraResolutionWidth;
    }

    public int getCameraResolutionHeight() {
        return cameraResolutionHeight;
    }

    public int getCameraFpsMin() {
        return cameraFpsMin;
    }

    public int getCameraFpsMax() {
        return cameraFpsMax;
    }

    public int getBitrateLimit() {
        return bitrateLimit;
    }

    public boolean isUseExtraEncoder() {
        return useExtraEncoder;
    }

    public String getVideoRecorderCodecMime(){
        switch (videoRecorderCodec){
            default:
            case 0:
                return MediaCommon.avcCodecMime;
            case 1:
                return MediaCommon.hevcCodecMime;
        }
    }

    public int getRecordedVideoBitrate() {
        return recordedVideoBitrate;
    }

    public boolean isSendAudioStream() {
        return sendAudioStream;
    }

    public int getAudioStreamBitrate() {
        return audioStreamBitrate;
    }

    public boolean isRecordAudio() {
        return recordAudio;
    }

    public int getRecordedAudioBitrate() {
        return recordedAudioBitrate;
    }

    public int getTelemetryRefreshRate() {
        return telemetryRefreshRate;
    }

    public int getRcRefreshRate() {
        return rcRefreshRate;
    }

    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    public int getUsbSerialPortIndex() {
        return usbSerialPortIndex;
    }

    public boolean isUseNativeSerialPort() {
        return useNativeSerialPort;
    }

    public String getNativeSerialPort(){
        return nativeSerialPort;
    }

    public int getFcProtocol() {
        return fcProtocol;
    }

    public short getMavlinkTargetSysId() {
        return (short)mavlinkTargetSysId;
    }

    public short getMavlinkGcsSysId() {
        return (short)mavlinkGcsSysId;
    }

    public boolean isConnectOnStartup(){
        return connectOnStartup;
    }

    public int processReceivedConfig(DataReader buffer){
        try {
            int version = buffer.readShort();
            if (version != versionCode) return -1;
            cameraId = buffer.readUTF();
            cameraResolutionWidth = buffer.readShort();
            cameraResolutionHeight = buffer.readShort();
            cameraFpsMin = buffer.readShort();
            cameraFpsMax = buffer.readShort();
            bitrateLimit = buffer.readUnsignedByteAsInt() * 1000000;
            useExtraEncoder = buffer.readBoolean();
            videoRecorderCodec = buffer.readByte();
            recordedVideoBitrate = buffer.readUnsignedByteAsInt() * 1000000;
            sendAudioStream = buffer.readBoolean();
            audioStreamBitrate = buffer.readShort() * 1000;
            recordAudio = buffer.readBoolean();
            recordedAudioBitrate = buffer.readShort() * 1000;
            telemetryRefreshRate = buffer.readUnsignedByteAsInt();
            rcRefreshRate = buffer.readUnsignedByteAsInt();
            serialBaudRate = buffer.readInt();
            usbSerialPortIndex = buffer.readByte();
            useNativeSerialPort = buffer.readBoolean();
            nativeSerialPort = buffer.readUTF();
            fcProtocol = buffer.readByte();
            useUsbCamera = buffer.readBoolean();
            usbCameraFrameFormat = buffer.readByte();
            usbCameraReset = buffer.readBoolean();
            mavlinkTargetSysId = buffer.readUnsignedByteAsInt();
            mavlinkGcsSysId = buffer.readUnsignedByteAsInt();
            if (!updateConfig()) return -2;
            return 0;
        }catch (Exception e){
            log("processReceivedConfig error: " + e);
            return -3;
        }
    }

    private void loadConfig() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        ip = preferences.getString("ip", "");
        port = preferences.getInt("port", UdpCommon.defaultPort);
        key = preferences.getString("key", "DD");
        connectionMode = preferences.getInt("connectionMode", 0);
        cameraId = preferences.getString("cameraId", "0");
        useUsbCamera = preferences.getBoolean("useUsbCamera", false);
        usbCameraFrameFormat = preferences.getInt("usbCameraFrameFormat", 1);
        usbCameraReset = preferences.getBoolean("usbCameraReset", true);
        cameraResolutionWidth = preferences.getInt("cameraResolutionWidth", 1920);
        cameraResolutionHeight = preferences.getInt("cameraResolutionHeight", 1080);
        cameraFpsMin = preferences.getInt("cameraFpsMin", 30);
        cameraFpsMax = preferences.getInt("cameraFpsMax", 60);
        bitrateLimit = preferences.getInt("bitrateLimit", 6000000);
        useExtraEncoder = preferences.getBoolean("useExtraEncoder", true);
        videoRecorderCodec = preferences.getInt("videoRecorderCodec", 0);
        recordedVideoBitrate = preferences.getInt("recordedVideoBitrate", 20000000);
        sendAudioStream = preferences.getBoolean("sendAudioStream", false);
        audioStreamBitrate = preferences.getInt("audioStreamBitrate", 96000);
        recordAudio = preferences.getBoolean("recordAudio", true);
        recordedAudioBitrate = preferences.getInt("recordedAudioBitrate", 192000);
        telemetryRefreshRate = preferences.getInt("telemetryRefreshRate", 10);
        rcRefreshRate = preferences.getInt("rcRefreshRate", 25);
        serialBaudRate = preferences.getInt("serialBaudRate", 115200);
        usbSerialPortIndex = preferences.getInt("usbSerialPortIndex", 0);
        useNativeSerialPort = preferences.getBoolean("useNativeSerialPort", false);
        nativeSerialPort = preferences.getString("nativeSerialPort", "/dev/ttyS0");
        fcProtocol = preferences.getInt("fcProtocol", FcCommon.FC_PROTOCOL_AUTO);
        mavlinkTargetSysId = preferences.getInt("mavlinkTargetSysId", 1);
        mavlinkGcsSysId = preferences.getInt("mavlinkGcsSysId", 255);
        connectOnStartup = preferences.getBoolean("connectOnStartup", false);
    }

    public boolean updateConfig(){
        ip = activity.etIp.getText().toString();
        try {
            port = Integer.parseInt(activity.etPort.getText().toString());
        }catch (NumberFormatException e){
            return false;
        }
        if (port < 1024 || port > 65535) {
            port = UdpCommon.defaultPort;
            return false;
        }
        key = activity.etKey.getText().toString();
        connectionMode = activity.connectionMode.getSelectedItemPosition();
        if (connectionMode < 0) connectionMode = 0;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("ip", ip);
        editor.putInt("port", port);
        editor.putString("key", key);
        editor.putInt("connectionMode", connectionMode);
        editor.putString("cameraId", cameraId);
        editor.putBoolean("useUsbCamera", useUsbCamera);
        editor.putInt("usbCameraFrameFormat", usbCameraFrameFormat);
        editor.putBoolean("usbCameraReset", usbCameraReset);
        editor.putInt("cameraResolutionWidth", cameraResolutionWidth);
        editor.putInt("cameraResolutionHeight", cameraResolutionHeight);
        editor.putInt("cameraFpsMin", cameraFpsMin);
        editor.putInt("cameraFpsMax", cameraFpsMax);
        editor.putInt("bitrateLimit", bitrateLimit);
        editor.putBoolean("useExtraEncoder", useExtraEncoder);
        editor.putInt("videoRecorderCodec", videoRecorderCodec);
        editor.putInt("recordedVideoBitrate", recordedVideoBitrate);
        editor.putBoolean("sendAudioStream", sendAudioStream);
        editor.putInt("audioStreamBitrate", audioStreamBitrate);
        editor.putBoolean("recordAudio", recordAudio);
        editor.putInt("recordedAudioBitrate", recordedAudioBitrate);
        editor.putInt("telemetryRefreshRate", telemetryRefreshRate);
        editor.putInt("rcRefreshRate", rcRefreshRate);
        editor.putInt("serialBaudRate", serialBaudRate);
        editor.putInt("usbSerialPortIndex", usbSerialPortIndex);
        editor.putBoolean("useNativeSerialPort", useNativeSerialPort);
        editor.putString("nativeSerialPort", nativeSerialPort);
        editor.putInt("fcProtocol", fcProtocol);
        editor.putInt("mavlinkTargetSysId", mavlinkTargetSysId);
        editor.putInt("mavlinkGcsSysId", mavlinkGcsSysId);
        connectOnStartup = activity.cbConnectOnStartup.isChecked();
        editor.putBoolean("connectOnStartup", connectOnStartup);
        editor.apply();
        return true;
    }
}
