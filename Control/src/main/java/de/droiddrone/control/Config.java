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

package de.droiddrone.control;

import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;

import de.droiddrone.common.SettingsCommon;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.UdpCommon;

public class Config {
    private final MainActivity activity;
    private final int versionCode;
    private String ip;
    private int port;
    private String key;
    private boolean isViewer;
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
    private boolean invertVideoAxisX;
    private boolean invertVideoAxisY;
    private boolean sendAudioStream;
    private int audioStreamBitrate;
    private boolean recordAudio;
    private int recordedAudioBitrate;
    private boolean showPhoneBattery;
    private boolean showNetworkState;
    private boolean showCameraFps;
    private boolean showScreenFps;
    private boolean showVideoBitrate;
    private boolean showPing;
    private boolean showVideoRecordButton;
    private boolean showVideoRecordIndication;
    private int osdTextColor;
    private int telemetryRefreshRate;
    private int rcRefreshRate;
    private int serialBaudRate;
    private int usbSerialPortIndex;
    private boolean useNativeSerialPort;
    private String nativeSerialPort;
    private int fcProtocol;
    private int mavlinkTargetSysId;
    private int mavlinkGcsSysId;
    private int vrMode;
    private int vrFrameScale;
    private int vrCenterOffset;
    private int vrOsdOffset;
    private final int[] rcChannelsMap = new int[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];
    private boolean decoderConfigChanged;
    private boolean videoFrameOrientationChanged;

    public Config(MainActivity activity, int versionCode) {
        this.activity = activity;
        this.versionCode = versionCode;
        loadConfig();
        decoderConfigChanged = false;
    }

    public boolean isDecoderConfigChanged() {
        return decoderConfigChanged;
    }

    public void decoderConfigUpdated(){
        decoderConfigChanged = false;
    }

    public boolean isVideoFrameOrientationChanged() {
        return videoFrameOrientationChanged;
    }

    public void videoFrameOrientationUpdated(){
        videoFrameOrientationChanged = false;
    }

    private void loadConfig(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        ip = preferences.getString("ip", SettingsCommon.ip);
        port = preferences.getInt("port", SettingsCommon.port);
        key = preferences.getString("key", SettingsCommon.key);
        isViewer = preferences.getBoolean("isViewer", SettingsCommon.isViewer);
        cameraId = preferences.getString("cameraId", SettingsCommon.cameraId);
        useUsbCamera = preferences.getBoolean("useUsbCamera", SettingsCommon.useUsbCamera);
        usbCameraFrameFormat = parseInt(preferences.getString("usbCameraFrameFormat", ""), SettingsCommon.usbCameraFrameFormat);
        usbCameraReset = preferences.getBoolean("usbCameraReset", SettingsCommon.usbCameraReset);
        int cameraResolutionWidth;
        int cameraResolutionHeight;
        try{
            String cameraResolution = preferences.getString("cameraResolution", "");
            String[] sizes = cameraResolution.split("x");
            cameraResolutionWidth = parseInt(sizes[0], SettingsCommon.cameraResolutionWidth);
            cameraResolutionHeight = parseInt(sizes[1], SettingsCommon.cameraResolutionHeight);
        }catch (Exception e){
            cameraResolutionWidth = SettingsCommon.cameraResolutionWidth;
            cameraResolutionHeight = SettingsCommon.cameraResolutionHeight;
        }
        if (cameraResolutionWidth != this.cameraResolutionWidth || cameraResolutionHeight != this.cameraResolutionHeight) decoderConfigChanged = true;
        this.cameraResolutionWidth = cameraResolutionWidth;
        this.cameraResolutionHeight = cameraResolutionHeight;
        try{
            String cameraFps = preferences.getString("cameraFps", "");
            String[] ranges = cameraFps.split("-");
            cameraFpsMin = parseInt(ranges[0], SettingsCommon.cameraFpsMin);
            cameraFpsMax = parseInt(ranges[1], SettingsCommon.cameraFpsMax);
        }catch (Exception e){
            cameraFpsMin = SettingsCommon.cameraFpsMin;
            cameraFpsMax = SettingsCommon.cameraFpsMax;
        }
        bitrateLimit = parseInt(preferences.getString("bitrateLimit", ""), SettingsCommon.bitrateLimit);
        useExtraEncoder = preferences.getBoolean("useExtraEncoder", SettingsCommon.useExtraEncoder);
        videoRecorderCodec = parseInt(preferences.getString("videoRecorderCodec", ""), SettingsCommon.videoRecorderCodec);
        recordedVideoBitrate = parseInt(preferences.getString("recordedVideoBitrate", ""), SettingsCommon.recordedVideoBitrate);
        sendAudioStream = preferences.getBoolean("sendAudioStream", SettingsCommon.sendAudioStream);
        audioStreamBitrate = parseInt(preferences.getString("audioStreamBitrate", ""), SettingsCommon.audioStreamBitrate);
        recordAudio = preferences.getBoolean("recordAudio", SettingsCommon.recordAudio);
        recordedAudioBitrate = parseInt(preferences.getString("recordedAudioBitrate", ""), SettingsCommon.recordedAudioBitrate);
        telemetryRefreshRate = parseInt(preferences.getString("telemetryRefreshRate", ""), SettingsCommon.telemetryRefreshRate);
        rcRefreshRate = parseInt(preferences.getString("rcRefreshRate", ""), SettingsCommon.rcRefreshRate);
        serialBaudRate = parseInt(preferences.getString("serialBaudRate", ""), SettingsCommon.serialBaudRate);
        usbSerialPortIndex = parseInt(preferences.getString("usbSerialPortIndex", ""), SettingsCommon.usbSerialPortIndex);
        useNativeSerialPort = preferences.getBoolean("useNativeSerialPort", SettingsCommon.useNativeSerialPort);
        nativeSerialPort = preferences.getString("nativeSerialPort", SettingsCommon.nativeSerialPort);
        fcProtocol = parseInt(preferences.getString("fcProtocol", ""), SettingsCommon.fcProtocol);
        mavlinkTargetSysId = parseInt(preferences.getString("mavlinkTargetSysId", ""), SettingsCommon.mavlinkTargetSysId);
        mavlinkGcsSysId = parseInt(preferences.getString("mavlinkGcsSysId", ""), SettingsCommon.mavlinkGcsSysId);
        boolean invertVideoAxisX = preferences.getBoolean("invertVideoAxisX", SettingsCommon.invertVideoAxisX);
        boolean invertVideoAxisY = preferences.getBoolean("invertVideoAxisY", SettingsCommon.invertVideoAxisY);
        if (invertVideoAxisX != this.invertVideoAxisX || invertVideoAxisY != this.invertVideoAxisY) videoFrameOrientationChanged = true;
        this.invertVideoAxisX = invertVideoAxisX;
        this.invertVideoAxisY = invertVideoAxisY;
        showPhoneBattery = preferences.getBoolean("showPhoneBattery", SettingsCommon.showPhoneBattery);
        showNetworkState = preferences.getBoolean("showNetworkState", SettingsCommon.showNetworkState);
        showCameraFps = preferences.getBoolean("showCameraFps", SettingsCommon.showCameraFps);
        showScreenFps = preferences.getBoolean("showScreenFps", SettingsCommon.showScreenFps);
        showVideoBitrate = preferences.getBoolean("showVideoBitrate", SettingsCommon.showVideoBitrate);
        showPing = preferences.getBoolean("showPing", SettingsCommon.showPing);
        showVideoRecordButton = preferences.getBoolean("showVideoRecordButton", SettingsCommon.showVideoRecordButton);
        showVideoRecordIndication = preferences.getBoolean("showVideoRecordIndication", SettingsCommon.showVideoRecordIndication);
        osdTextColor = preferences.getInt("osdTextColor", SettingsCommon.osdTextColor);
        int vrMode = parseInt(preferences.getString("vrMode", ""), SettingsCommon.vrMode);
        if (vrMode != this.vrMode) videoFrameOrientationChanged = true;
        this.vrMode = vrMode;
        vrFrameScale = parseInt(preferences.getString("vrFrameScale", ""), SettingsCommon.vrFrameScale);
        vrCenterOffset = parseInt(preferences.getString("vrCenterOffset", ""), SettingsCommon.vrCenterOffset);
        vrOsdOffset = parseInt(preferences.getString("vrOsdOffset", ""), SettingsCommon.vrOsdOffset);
        // RC channels map
        boolean setDefaultRcMap = true;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            rcChannelsMap[i] = preferences.getInt("rcMap" + i, -1);
            if (rcChannelsMap[i] != -1) setDefaultRcMap = false;
        }
        if (setDefaultRcMap) setDefaultRcMap();
    }

    // Taranis Q X7 defaults
    private void setDefaultRcMap(){
        rcChannelsMap[0] = MotionEvent.AXIS_Y;// A
        rcChannelsMap[1] = MotionEvent.AXIS_Z;// E
        rcChannelsMap[2] = MotionEvent.AXIS_RX;//R
        rcChannelsMap[3] = MotionEvent.AXIS_X;// T
        rcChannelsMap[4] = MotionEvent.AXIS_RY;
        rcChannelsMap[5] = MotionEvent.AXIS_RZ;
        rcChannelsMap[6] = MotionEvent.AXIS_THROTTLE;
        rcChannelsMap[7] = MotionEvent.AXIS_RUDDER;
        rcChannelsMap[8] = KeyEvent.KEYCODE_BUTTON_A + Rc.KEY_CODE_OFFSET;
        rcChannelsMap[9] = KeyEvent.KEYCODE_BUTTON_B + Rc.KEY_CODE_OFFSET;
        rcChannelsMap[10] = KeyEvent.KEYCODE_BUTTON_C + Rc.KEY_CODE_OFFSET;
        rcChannelsMap[11] = KeyEvent.KEYCODE_BUTTON_X + Rc.KEY_CODE_OFFSET;
    }

    public int getVersionCode(){
        return versionCode;
    }

    public int[] getRcChannelsMap(){
        return rcChannelsMap;
    }

    public void updateChannelMap(int channel, int code){
        if (channel < 0 || channel >= FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT) return;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            if (rcChannelsMap[i] == code) rcChannelsMap[i] = -1;
        }
        rcChannelsMap[channel] = code;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            editor.putInt("rcMap" + i, rcChannelsMap[i]);
        }
        editor.apply();
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

    public int getVideoRecorderCodec() {
        return videoRecorderCodec;
    }

    public int getRecordedVideoBitrate() {
        return recordedVideoBitrate;
    }

    public boolean isInvertVideoAxisX() {
        return invertVideoAxisX;
    }

    public boolean isInvertVideoAxisY() {
        return invertVideoAxisY;
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

    public boolean isShowPhoneBattery() {
        return showPhoneBattery;
    }

    public boolean isShowNetworkState() {
        return showNetworkState;
    }

    public boolean isShowCameraFps() {
        return showCameraFps;
    }

    public boolean isShowScreenFps() {
        return showScreenFps;
    }

    public boolean isShowVideoBitrate() {
        return showVideoBitrate;
    }

    public boolean isShowPing() {
        return showPing;
    }

    public boolean isShowVideoRecordButton() {
        return showVideoRecordButton;
    }

    public boolean isShowVideoRecordIndication() {
        return showVideoRecordIndication;
    }

    public int getOsdTextColor() {
        return osdTextColor;
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

    public int getMavlinkTargetSysId(){
        return mavlinkTargetSysId;
    }

    public int getMavlinkGcsSysId() {
        return mavlinkGcsSysId;
    }

    public int getCameraResolutionWidth() {
        return cameraResolutionWidth;
    }

    public int getCameraResolutionHeight() {
        return cameraResolutionHeight;
    }

    public String getCameraId(){
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

    public int getVrMode() {
        return vrMode;
    }

    public int getVrFrameScale() {
        return vrFrameScale;
    }

    public int getVrCenterOffset() {
        return vrCenterOffset;
    }

    public int getVrOsdOffset() {
        return vrOsdOffset;
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

    public boolean isViewer(){
        return isViewer;
    }

    private int parseInt(String str, int defaultValue){
        try{
            return Integer.parseInt(str);
        }catch (Exception e){
            return defaultValue;
        }
    }

    public boolean updateConfig(){
        StartFragment fragment = activity.getStartFragment();
        if (fragment == null) return false;
        ip = fragment.etIp.getText().toString();
        if (ip.isBlank()) return false;
        try {
            port = Integer.parseInt(fragment.etPort.getText().toString());
        }catch (NumberFormatException e){
            return false;
        }
        if (port < 1024 || port > 65535) {
            port = UdpCommon.defaultPort;
            return false;
        }
        key = fragment.etKey.getText().toString();
        isViewer = fragment.isViewer.isChecked();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("ip", ip);
        editor.putInt("port", port);
        editor.putString("key", key);
        editor.putBoolean("isViewer", isViewer);
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            editor.putInt("rcMap" + i, rcChannelsMap[i]);
        }
        editor.apply();
        loadConfig();
        return true;
    }
}
