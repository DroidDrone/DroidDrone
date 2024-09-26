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
import de.droiddrone.common.SettingsCommon;
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
    private boolean cameraConfigChanged;
    private boolean recorderConfigChanged;
    private boolean audioStreamConfigChanged;
    private boolean fcConfigChanged;
    private boolean rcConfigChanged;

    public Config(MainActivity activity, int versionCode) {
        this.activity = activity;
        this.versionCode = versionCode;
        loadConfig();
    }

    public boolean isAudioStreamConfigChanged() {
        return audioStreamConfigChanged;
    }

    public void audioStreamConfigUpdated(){
        audioStreamConfigChanged = false;
    }

    public boolean isFcConfigChanged() {
        return fcConfigChanged;
    }

    public void fcConfigUpdated(){
        fcConfigChanged = false;
    }

    public boolean isRcConfigChanged() {
        return rcConfigChanged;
    }

    public void rcConfigUpdated(){
        rcConfigChanged = false;
    }

    public boolean isCameraConfigChanged() {
        return cameraConfigChanged;
    }

    public void cameraConfigUpdated(){
        cameraConfigChanged = false;
    }

    public boolean isRecorderConfigChanged() {
        return recorderConfigChanged;
    }

    public void recorderConfigUpdated(){
        recorderConfigChanged = false;
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
            case SettingsCommon.VideoRecorderCodec.AVC:
                return MediaCommon.avcCodecMime;
            case SettingsCommon.VideoRecorderCodec.HEVC:
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
            String cameraId = buffer.readUTF();
            if (cameraId != null && !cameraId.equals(this.cameraId)) cameraConfigChanged = true;
            this.cameraId = cameraId;
            int cameraResolutionWidth = buffer.readShort();
            int cameraResolutionHeight = buffer.readShort();
            if (cameraResolutionWidth != this.cameraResolutionWidth || cameraResolutionHeight != this.cameraResolutionHeight) cameraConfigChanged = true;
            this.cameraResolutionWidth = cameraResolutionWidth;
            this.cameraResolutionHeight = cameraResolutionHeight;
            int cameraFpsMin = buffer.readShort();
            int cameraFpsMax = buffer.readShort();
            if (cameraFpsMin != this.cameraFpsMin || cameraFpsMax != this.cameraFpsMax) cameraConfigChanged = true;
            this.cameraFpsMin = cameraFpsMin;
            this.cameraFpsMax = cameraFpsMax;
            bitrateLimit = buffer.readUnsignedByteAsInt() * 1000000;
            boolean useExtraEncoder = buffer.readBoolean();
            if (useExtraEncoder != this.useExtraEncoder){
                cameraConfigChanged = true;
                recorderConfigChanged = true;
            }
            this.useExtraEncoder = useExtraEncoder;
            int videoRecorderCodec = buffer.readByte();
            if (videoRecorderCodec != this.videoRecorderCodec){
                cameraConfigChanged = true;
                recorderConfigChanged = true;
            }
            this.videoRecorderCodec = videoRecorderCodec;
            int recordedVideoBitrate = buffer.readUnsignedByteAsInt() * 1000000;
            if (recordedVideoBitrate != this.recordedVideoBitrate){
                cameraConfigChanged = true;
                recorderConfigChanged = true;
            }
            this.recordedVideoBitrate = recordedVideoBitrate;
            boolean sendAudioStream = buffer.readBoolean();
            if (sendAudioStream != this.sendAudioStream) audioStreamConfigChanged = true;
            this.sendAudioStream = sendAudioStream;
            int audioStreamBitrate = buffer.readShort() * 1000;
            if (audioStreamBitrate != this.audioStreamBitrate) audioStreamConfigChanged = true;
            this.audioStreamBitrate = audioStreamBitrate;
            boolean recordAudio = buffer.readBoolean();
            if (recordAudio != this.recordAudio) recorderConfigChanged = true;
            this.recordAudio = recordAudio;
            int recordedAudioBitrate = buffer.readShort() * 1000;
            if (recordedAudioBitrate != this.recordedAudioBitrate) recorderConfigChanged = true;
            this.recordedAudioBitrate = recordedAudioBitrate;
            int telemetryRefreshRate = buffer.readUnsignedByteAsInt();
            if (telemetryRefreshRate != this.telemetryRefreshRate) fcConfigChanged = true;
            this.telemetryRefreshRate = telemetryRefreshRate;
            int rcRefreshRate = buffer.readUnsignedByteAsInt();
            if (rcRefreshRate != this.rcRefreshRate) rcConfigChanged = true;
            this.rcRefreshRate = rcRefreshRate;
            int serialBaudRate = buffer.readInt();
            int usbSerialPortIndex = buffer.readByte();
            boolean useNativeSerialPort = buffer.readBoolean();
            if (serialBaudRate != this.serialBaudRate || usbSerialPortIndex != this.usbSerialPortIndex
                    || useNativeSerialPort != this.useNativeSerialPort) fcConfigChanged = true;
            this.serialBaudRate = serialBaudRate;
            this.usbSerialPortIndex = usbSerialPortIndex;
            this.useNativeSerialPort = useNativeSerialPort;
            String nativeSerialPort = buffer.readUTF();
            if (nativeSerialPort != null && !nativeSerialPort.equals(this.nativeSerialPort)) fcConfigChanged = true;
            this.nativeSerialPort = nativeSerialPort;
            int fcProtocol = buffer.readByte();
            if (fcProtocol != this.fcProtocol) fcConfigChanged = true;
            this.fcProtocol = fcProtocol;
            boolean useUsbCamera = buffer.readBoolean();
            int usbCameraFrameFormat = buffer.readByte();
            boolean usbCameraReset = buffer.readBoolean();
            if (useUsbCamera != this.useUsbCamera || usbCameraFrameFormat != this.usbCameraFrameFormat
                    || usbCameraReset != this.usbCameraReset) cameraConfigChanged = true;
            this.useUsbCamera = useUsbCamera;
            this.usbCameraFrameFormat = usbCameraFrameFormat;
            this.usbCameraReset = usbCameraReset;
            int mavlinkTargetSysId = buffer.readUnsignedByteAsInt();
            int mavlinkGcsSysId = buffer.readUnsignedByteAsInt();
            if (mavlinkTargetSysId != this.mavlinkTargetSysId || mavlinkGcsSysId != this.mavlinkGcsSysId) fcConfigChanged = true;
            this.mavlinkTargetSysId = mavlinkTargetSysId;
            this.mavlinkGcsSysId = mavlinkGcsSysId;
            if (!updateConfig()) return -2;
            return 0;
        }catch (Exception e){
            log("processReceivedConfig error: " + e);
            return -3;
        }
    }

    private void loadConfig() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        ip = preferences.getString("ip", SettingsCommon.ip);
        port = preferences.getInt("port", SettingsCommon.port);
        key = preferences.getString("key", SettingsCommon.key);
        connectionMode = preferences.getInt("connectionMode", SettingsCommon.connectionMode);
        cameraId = preferences.getString("cameraId", SettingsCommon.cameraId);
        useUsbCamera = preferences.getBoolean("useUsbCamera", SettingsCommon.useUsbCamera);
        usbCameraFrameFormat = preferences.getInt("usbCameraFrameFormat", SettingsCommon.usbCameraFrameFormat);
        usbCameraReset = preferences.getBoolean("usbCameraReset", SettingsCommon.usbCameraReset);
        cameraResolutionWidth = preferences.getInt("cameraResolutionWidth", SettingsCommon.cameraResolutionWidth);
        cameraResolutionHeight = preferences.getInt("cameraResolutionHeight", SettingsCommon.cameraResolutionHeight);
        cameraFpsMin = preferences.getInt("cameraFpsMin", SettingsCommon.cameraFpsMin);
        cameraFpsMax = preferences.getInt("cameraFpsMax", SettingsCommon.cameraFpsMax);
        bitrateLimit = preferences.getInt("bitrateLimit", SettingsCommon.bitrateLimit);
        useExtraEncoder = preferences.getBoolean("useExtraEncoder", SettingsCommon.useExtraEncoder);
        videoRecorderCodec = preferences.getInt("videoRecorderCodec", SettingsCommon.videoRecorderCodec);
        recordedVideoBitrate = preferences.getInt("recordedVideoBitrate", SettingsCommon.recordedVideoBitrate);
        sendAudioStream = preferences.getBoolean("sendAudioStream", SettingsCommon.sendAudioStream);
        audioStreamBitrate = preferences.getInt("audioStreamBitrate", SettingsCommon.audioStreamBitrate);
        recordAudio = preferences.getBoolean("recordAudio", SettingsCommon.recordAudio);
        recordedAudioBitrate = preferences.getInt("recordedAudioBitrate", SettingsCommon.recordedAudioBitrate);
        telemetryRefreshRate = preferences.getInt("telemetryRefreshRate", SettingsCommon.telemetryRefreshRate);
        rcRefreshRate = preferences.getInt("rcRefreshRate", SettingsCommon.rcRefreshRate);
        serialBaudRate = preferences.getInt("serialBaudRate", SettingsCommon.serialBaudRate);
        usbSerialPortIndex = preferences.getInt("usbSerialPortIndex", SettingsCommon.usbSerialPortIndex);
        useNativeSerialPort = preferences.getBoolean("useNativeSerialPort", SettingsCommon.useNativeSerialPort);
        nativeSerialPort = preferences.getString("nativeSerialPort", SettingsCommon.nativeSerialPort);
        fcProtocol = preferences.getInt("fcProtocol", SettingsCommon.fcProtocol);
        mavlinkTargetSysId = preferences.getInt("mavlinkTargetSysId", SettingsCommon.mavlinkTargetSysId);
        mavlinkGcsSysId = preferences.getInt("mavlinkGcsSysId", SettingsCommon.mavlinkGcsSysId);
        connectOnStartup = preferences.getBoolean("connectOnStartup", SettingsCommon.connectOnStartup);
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
        if (connectionMode < 0) connectionMode = SettingsCommon.connectionMode;
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
