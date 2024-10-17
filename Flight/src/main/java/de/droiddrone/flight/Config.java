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
    private final boolean[] cameraEnabled = new boolean[SettingsCommon.maxCamerasCount];
    private final String[] cameraId = new String[SettingsCommon.maxCamerasCount];
    private final int[] cameraResolutionWidth = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraResolutionHeight = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraFpsMin = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraFpsMax = new int[SettingsCommon.maxCamerasCount];
    private final boolean[] useUsbCamera = new boolean[SettingsCommon.maxCamerasCount];
    private final int[] usbCameraFrameFormat = new int[SettingsCommon.maxCamerasCount];
    private final boolean[] usbCameraReset = new boolean[SettingsCommon.maxCamerasCount];
    private int currentCameraIndex = SettingsCommon.currentCameraIndex;

    public Config(MainActivity activity, int versionCode) {
        this.activity = activity;
        this.versionCode = versionCode;
        System.arraycopy(SettingsCommon.cameraEnabled, 0, cameraEnabled, 0, SettingsCommon.maxCamerasCount);
        System.arraycopy(SettingsCommon.cameraId, 0, cameraId, 0, SettingsCommon.maxCamerasCount);
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
        return cameraId[currentCameraIndex];
    }

    public boolean isUseUsbCamera(){
        return useUsbCamera[currentCameraIndex];
    }

    public int getUsbCameraFrameFormat(){
        return usbCameraFrameFormat[currentCameraIndex];
    }

    public boolean isUsbCameraReset(){
        return usbCameraReset[currentCameraIndex];
    }

    public int getCameraResolutionWidth() {
        return cameraResolutionWidth[currentCameraIndex];
    }

    public int getCameraResolutionHeight() {
        return cameraResolutionHeight[currentCameraIndex];
    }

    public int getCameraFpsMin() {
        return cameraFpsMin[currentCameraIndex];
    }

    public int getCameraFpsMax() {
        return cameraFpsMax[currentCameraIndex];
    }

    public int getCamerasCount(){
        int count = 0;
        for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
            if (cameraEnabled[i]) count++;
        }
        return count;
    }

    public int getCurrentUsbCameraIndex(){
        int index = 0;
        for (int i = 0; i < currentCameraIndex; i++) {
            if (cameraEnabled[i] && useUsbCamera[i]) index++;
        }
        return index;
    }

    public void changeCamera(){
        int count = getCamerasCount();
        if (count == 1) return;
        int camera = currentCameraIndex;
        camera++;
        if (camera >= count) camera = 0;
        currentCameraIndex = camera;
        cameraConfigChanged = true;
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
            // cameras
            for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
                boolean cameraEnabled = true;
                if (i > 0) cameraEnabled = buffer.readBoolean();
                if (this.cameraEnabled[i] != cameraEnabled) {
                    currentCameraIndex = 0;
                    cameraConfigChanged = true;
                }
                this.cameraEnabled[i] = cameraEnabled;
                String cameraId = buffer.readUTF();
                if (cameraId != null && !cameraId.equals(this.cameraId[i]) && currentCameraIndex == i)
                    cameraConfigChanged = true;
                this.cameraId[i] = cameraId;
                int cameraResolutionWidth = buffer.readShort();
                int cameraResolutionHeight = buffer.readShort();
                if ((cameraResolutionWidth != this.cameraResolutionWidth[i] || cameraResolutionHeight != this.cameraResolutionHeight[i])
                        && currentCameraIndex == i) cameraConfigChanged = true;
                this.cameraResolutionWidth[i] = cameraResolutionWidth;
                this.cameraResolutionHeight[i] = cameraResolutionHeight;
                int cameraFpsMin = buffer.readShort();
                int cameraFpsMax = buffer.readShort();
                if ((cameraFpsMin != this.cameraFpsMin[i] || cameraFpsMax != this.cameraFpsMax[i])
                        && currentCameraIndex == i) cameraConfigChanged = true;
                this.cameraFpsMin[i] = cameraFpsMin;
                this.cameraFpsMax[i] = cameraFpsMax;
                boolean useUsbCamera = buffer.readBoolean();
                int usbCameraFrameFormat = buffer.readByte();
                boolean usbCameraReset = buffer.readBoolean();
                if ((useUsbCamera != this.useUsbCamera[i] || usbCameraFrameFormat != this.usbCameraFrameFormat[i]
                        || usbCameraReset != this.usbCameraReset[i]) && currentCameraIndex == i)
                    cameraConfigChanged = true;
                this.useUsbCamera[i] = useUsbCamera;
                this.usbCameraFrameFormat[i] = usbCameraFrameFormat;
                this.usbCameraReset[i] = usbCameraReset;
            }
            // video
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
            // audio
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
            // fc
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
        for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
            String cameraNum = "";
            if (i == 0){
                cameraEnabled[i] = SettingsCommon.cameraEnabled[i];
            }else{
                cameraNum = "_" + (i + 1);
                cameraEnabled[i] = preferences.getBoolean("cameraEnabled" + cameraNum, SettingsCommon.cameraEnabled[i]);
            }
            cameraId[i] = preferences.getString("cameraId" + cameraNum, SettingsCommon.cameraId[i]);
            useUsbCamera[i] = preferences.getBoolean("useUsbCamera" + cameraNum, SettingsCommon.useUsbCamera);
            usbCameraFrameFormat[i] = preferences.getInt("usbCameraFrameFormat" + cameraNum, SettingsCommon.usbCameraFrameFormat);
            usbCameraReset[i] = preferences.getBoolean("usbCameraReset" + cameraNum, SettingsCommon.usbCameraReset);
            cameraResolutionWidth[i] = preferences.getInt("cameraResolutionWidth" + cameraNum, SettingsCommon.cameraResolutionWidth);
            cameraResolutionHeight[i] = preferences.getInt("cameraResolutionHeight" + cameraNum, SettingsCommon.cameraResolutionHeight);
            cameraFpsMin[i] = preferences.getInt("cameraFpsMin" + cameraNum, SettingsCommon.cameraFpsMin);
            cameraFpsMax[i] = preferences.getInt("cameraFpsMax" + cameraNum, SettingsCommon.cameraFpsMax);
        }
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
        for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
            String cameraNum = "";
            if (i > 0) {
                cameraNum = "_" + (i + 1);
                editor.putBoolean("cameraEnabled" + cameraNum, cameraEnabled[i]);
            }
            editor.putString("cameraId" + cameraNum, cameraId[i]);
            editor.putBoolean("useUsbCamera" + cameraNum, useUsbCamera[i]);
            editor.putInt("usbCameraFrameFormat" + cameraNum, usbCameraFrameFormat[i]);
            editor.putBoolean("usbCameraReset" + cameraNum, usbCameraReset[i]);
            editor.putInt("cameraResolutionWidth" + cameraNum, cameraResolutionWidth[i]);
            editor.putInt("cameraResolutionHeight" + cameraNum, cameraResolutionHeight[i]);
            editor.putInt("cameraFpsMin" + cameraNum, cameraFpsMin[i]);
            editor.putInt("cameraFpsMax" + cameraNum, cameraFpsMax[i]);
        }
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
