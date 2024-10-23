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
import de.droiddrone.common.Utils;

public class Config {
    private final MainActivity activity;
    private final int versionCode;
    private String ip;
    private int port;
    private String key;
    private boolean isViewer;
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
    private boolean drawOsd;
    private boolean showDronePhoneBattery;
    private boolean showControlPhoneBattery;
    private boolean showNetworkState;
    private boolean showCameraFps;
    private boolean showScreenFps;
    private boolean showVideoBitrate;
    private boolean showPing;
    private boolean showVideoRecordButton;
    private boolean showVideoRecordIndication;
    private int osdTextColor;
    private int vrOsdScale;
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
    private boolean vrHeadTracking;
    private final boolean[] cameraEnabled = new boolean[SettingsCommon.maxCamerasCount];
    private final String[] cameraId = new String[SettingsCommon.maxCamerasCount];
    private final int[] cameraResolutionWidth = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraResolutionHeight = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraFpsMin = new int[SettingsCommon.maxCamerasCount];
    private final int[] cameraFpsMax = new int[SettingsCommon.maxCamerasCount];
    private final boolean[] useUsbCamera = new boolean[SettingsCommon.maxCamerasCount];
    private final int[] usbCameraFrameFormat = new int[SettingsCommon.maxCamerasCount];
    private final boolean[] usbCameraReset = new boolean[SettingsCommon.maxCamerasCount];
    private final int[] rcChannelsMap = new int[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];
    private final int[] headTrackingAngleLimits = new int[3];
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
            usbCameraFrameFormat[i] = Utils.parseInt(preferences.getString("usbCameraFrameFormat" + cameraNum, ""), SettingsCommon.usbCameraFrameFormat);
            usbCameraReset[i] = preferences.getBoolean("usbCameraReset" + cameraNum, SettingsCommon.usbCameraReset);
            int cameraResolutionWidth;
            int cameraResolutionHeight;
            try {
                String cameraResolution = preferences.getString("cameraResolution" + cameraNum, "");
                String[] sizes = cameraResolution.split("x");
                cameraResolutionWidth = Utils.parseInt(sizes[0], SettingsCommon.cameraResolutionWidth);
                cameraResolutionHeight = Utils.parseInt(sizes[1], SettingsCommon.cameraResolutionHeight);
            } catch (Exception e) {
                cameraResolutionWidth = SettingsCommon.cameraResolutionWidth;
                cameraResolutionHeight = SettingsCommon.cameraResolutionHeight;
            }
            if (cameraResolutionWidth != this.cameraResolutionWidth[i] || cameraResolutionHeight != this.cameraResolutionHeight[i])
                decoderConfigChanged = true;
            this.cameraResolutionWidth[i] = cameraResolutionWidth;
            this.cameraResolutionHeight[i] = cameraResolutionHeight;
            try {
                String cameraFps = preferences.getString("cameraFps" + cameraNum, "");
                String[] ranges = cameraFps.split("-");
                cameraFpsMin[i] = Utils.parseInt(ranges[0], SettingsCommon.cameraFpsMin);
                cameraFpsMax[i] = Utils.parseInt(ranges[1], SettingsCommon.cameraFpsMax);
            } catch (Exception e) {
                cameraFpsMin[i] = SettingsCommon.cameraFpsMin;
                cameraFpsMax[i] = SettingsCommon.cameraFpsMax;
            }
        }
        bitrateLimit = Utils.parseInt(preferences.getString("bitrateLimit", ""), SettingsCommon.bitrateLimit);
        useExtraEncoder = preferences.getBoolean("useExtraEncoder", SettingsCommon.useExtraEncoder);
        videoRecorderCodec = Utils.parseInt(preferences.getString("videoRecorderCodec", ""), SettingsCommon.videoRecorderCodec);
        recordedVideoBitrate = Utils.parseInt(preferences.getString("recordedVideoBitrate", ""), SettingsCommon.recordedVideoBitrate);
        sendAudioStream = preferences.getBoolean("sendAudioStream", SettingsCommon.sendAudioStream);
        audioStreamBitrate = Utils.parseInt(preferences.getString("audioStreamBitrate", ""), SettingsCommon.audioStreamBitrate);
        recordAudio = preferences.getBoolean("recordAudio", SettingsCommon.recordAudio);
        recordedAudioBitrate = Utils.parseInt(preferences.getString("recordedAudioBitrate", ""), SettingsCommon.recordedAudioBitrate);
        telemetryRefreshRate = Utils.parseInt(preferences.getString("telemetryRefreshRate", ""), SettingsCommon.telemetryRefreshRate);
        rcRefreshRate = Utils.parseInt(preferences.getString("rcRefreshRate", ""), SettingsCommon.rcRefreshRate);
        serialBaudRate = Utils.parseInt(preferences.getString("serialBaudRate", ""), SettingsCommon.serialBaudRate);
        usbSerialPortIndex = Utils.parseInt(preferences.getString("usbSerialPortIndex", ""), SettingsCommon.usbSerialPortIndex);
        useNativeSerialPort = preferences.getBoolean("useNativeSerialPort", SettingsCommon.useNativeSerialPort);
        nativeSerialPort = preferences.getString("nativeSerialPort", SettingsCommon.nativeSerialPort);
        fcProtocol = Utils.parseInt(preferences.getString("fcProtocol", ""), SettingsCommon.fcProtocol);
        mavlinkTargetSysId = Utils.parseInt(preferences.getString("mavlinkTargetSysId", ""), SettingsCommon.mavlinkTargetSysId);
        mavlinkGcsSysId = Utils.parseInt(preferences.getString("mavlinkGcsSysId", ""), SettingsCommon.mavlinkGcsSysId);
        boolean invertVideoAxisX = preferences.getBoolean("invertVideoAxisX", SettingsCommon.invertVideoAxisX);
        boolean invertVideoAxisY = preferences.getBoolean("invertVideoAxisY", SettingsCommon.invertVideoAxisY);
        if (invertVideoAxisX != this.invertVideoAxisX || invertVideoAxisY != this.invertVideoAxisY) videoFrameOrientationChanged = true;
        this.invertVideoAxisX = invertVideoAxisX;
        this.invertVideoAxisY = invertVideoAxisY;
        drawOsd = preferences.getBoolean("drawOsd", SettingsCommon.drawOsd);
        showDronePhoneBattery = preferences.getBoolean("showDronePhoneBattery", SettingsCommon.showDronePhoneBattery);
        showControlPhoneBattery = preferences.getBoolean("showControlPhoneBattery", SettingsCommon.showControlPhoneBattery);
        showNetworkState = preferences.getBoolean("showNetworkState", SettingsCommon.showNetworkState);
        showCameraFps = preferences.getBoolean("showCameraFps", SettingsCommon.showCameraFps);
        showScreenFps = preferences.getBoolean("showScreenFps", SettingsCommon.showScreenFps);
        showVideoBitrate = preferences.getBoolean("showVideoBitrate", SettingsCommon.showVideoBitrate);
        showPing = preferences.getBoolean("showPing", SettingsCommon.showPing);
        showVideoRecordButton = preferences.getBoolean("showVideoRecordButton", SettingsCommon.showVideoRecordButton);
        showVideoRecordIndication = preferences.getBoolean("showVideoRecordIndication", SettingsCommon.showVideoRecordIndication);
        osdTextColor = preferences.getInt("osdTextColor", SettingsCommon.osdTextColor);
        int vrMode = Utils.parseInt(preferences.getString("vrMode", ""), SettingsCommon.vrMode);
        if (vrMode != this.vrMode) videoFrameOrientationChanged = true;
        this.vrMode = vrMode;
        vrFrameScale = Utils.parseInt(preferences.getString("vrFrameScale", ""), SettingsCommon.vrFrameScale);
        vrCenterOffset = Utils.parseInt(preferences.getString("vrCenterOffset", ""), SettingsCommon.vrCenterOffset);
        vrOsdOffset = Utils.parseInt(preferences.getString("vrOsdOffset", ""), SettingsCommon.vrOsdOffset);
        vrOsdScale = Utils.parseInt(preferences.getString("vrOsdScale", ""), SettingsCommon.vrOsdScale);
        vrHeadTracking = preferences.getBoolean("vrHeadTracking", SettingsCommon.vrHeadTracking);

        // RC channels map
        boolean setDefaultRcMap = true;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            rcChannelsMap[i] = preferences.getInt("rcMap" + i, -1);
            if (rcChannelsMap[i] != -1) setDefaultRcMap = false;
        }
        if (setDefaultRcMap) setDefaultRcMap();

        for (int i = 0; i < 3; i++) {
            headTrackingAngleLimits[i] = preferences.getInt("htAngleLimit" + i, SettingsCommon.headTrackingAngleLimit);
        }
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

    public int[] getHeadTrackingAngleLimits(){
        return headTrackingAngleLimits;
    }

    public int getHeadTrackingChannel(int axis){
        if (axis < 0 || axis >= 3) return -1;
        int code = axis + Rc.HEAD_TRACKING_CODE_OFFSET;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            if (rcChannelsMap[i] == code) return i;
        }
        return -1;
    }

    public boolean isHeadTrackingUsed(){
        if (vrHeadTracking && vrMode != SettingsCommon.VrMode.off) return true;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            if (rcChannelsMap[i] >= Rc.HEAD_TRACKING_CODE_OFFSET
                    && rcChannelsMap[i] < Rc.HEAD_TRACKING_CODE_OFFSET + 3) return true;
        }
        return false;
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

    public void updateHeadTrackingAngleLimit(int angle, int axis){
        if (axis < 0 || axis >= 3) return;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        this.headTrackingAngleLimits[axis] = angle;
        editor.putInt("htAngleLimit" + axis, angle);
        editor.apply();
    }

    public int getCamerasCount(){
        int count = 0;
        for (int i = 0; i < SettingsCommon.maxCamerasCount; i++) {
            if (cameraEnabled[i]) count++;
        }
        return count;
    }

    public int getCameraFpsMin(int cameraIndex) {
        return cameraFpsMin[cameraIndex];
    }

    public int getCameraFpsMax(int cameraIndex) {
        return cameraFpsMax[cameraIndex];
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

    public boolean isDrawOsd() {
        return drawOsd;
    }

    public boolean isShowDronePhoneBattery() {
        return showDronePhoneBattery;
    }

    public boolean isShowControlPhoneBattery() {
        return showControlPhoneBattery;
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

    public int getVrOsdScale() {
        return vrOsdScale;
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

    public int getCameraResolutionWidth(int cameraIndex) {
        return cameraResolutionWidth[cameraIndex];
    }

    public int getCameraResolutionHeight(int cameraIndex) {
        return cameraResolutionHeight[cameraIndex];
    }

    public String getCameraId(int cameraIndex){
        return cameraId[cameraIndex];
    }

    public boolean isUseUsbCamera(int cameraIndex){
        return useUsbCamera[cameraIndex];
    }

    public int getUsbCameraFrameFormat(int cameraIndex){
        return usbCameraFrameFormat[cameraIndex];
    }

    public boolean isUsbCameraReset(int cameraIndex){
        return usbCameraReset[cameraIndex];
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

    public boolean isVrHeadTracking() {
        return vrHeadTracking;
    }

    public boolean isCameraEnabled(int cameraIndex) {
        return cameraEnabled[cameraIndex];
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
