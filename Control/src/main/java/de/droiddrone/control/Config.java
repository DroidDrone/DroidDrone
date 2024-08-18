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
    private int serialPortIndex;
    private final int[] rcChannelsMap = new int[FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT];

    public Config(MainActivity activity, int versionCode) {
        this.activity = activity;
        this.versionCode = versionCode;
        loadConfig();
    }

    private void loadConfig(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        cameraId = preferences.getString("cameraId", "");
        useUsbCamera = preferences.getBoolean("useUsbCamera", false);
        usbCameraFrameFormat = parseInt(preferences.getString("usbCameraFrameFormat", ""), 1);
        usbCameraReset = preferences.getBoolean("usbCameraReset", true);
        try{
            String cameraResolution = preferences.getString("cameraResolution", "");
            String[] sizes = cameraResolution.split("x");
            cameraResolutionWidth = parseInt(sizes[0], 1920);
            cameraResolutionHeight = parseInt(sizes[1], 1080);
        }catch (Exception e){
            cameraResolutionWidth = 1920;
            cameraResolutionHeight = 1080;
        }
        try{
            String cameraFps = preferences.getString("cameraFps", "");
            String[] ranges = cameraFps.split("-");
            cameraFpsMin = parseInt(ranges[0], 60);
            cameraFpsMax = parseInt(ranges[1], 90);
        }catch (Exception e){
            cameraFpsMin = 60;
            cameraFpsMax = 90;
        }
        bitrateLimit = parseInt(preferences.getString("bitrateLimit", ""), 6000000);
        useExtraEncoder = preferences.getBoolean("useExtraEncoder", true);
        recordedVideoBitrate = parseInt(preferences.getString("recordedVideoBitrate", ""), 20000000);
        invertVideoAxisX = preferences.getBoolean("invertVideoAxisX", false);
        invertVideoAxisY = preferences.getBoolean("invertVideoAxisY", false);
        sendAudioStream = preferences.getBoolean("sendAudioStream", false);
        audioStreamBitrate = parseInt(preferences.getString("audioStreamBitrate", ""), 96000);
        recordAudio = preferences.getBoolean("recordAudio", true);
        recordedAudioBitrate = parseInt(preferences.getString("recordedAudioBitrate", ""), 192000);
        showPhoneBattery = preferences.getBoolean("showPhoneBattery", true);
        showNetworkState = preferences.getBoolean("showNetworkState", true);
        showCameraFps = preferences.getBoolean("showCameraFps", true);
        showScreenFps = preferences.getBoolean("showScreenFps", false);
        showVideoBitrate = preferences.getBoolean("showVideoBitrate", true);
        showPing = preferences.getBoolean("showPing", true);
        showVideoRecordButton = preferences.getBoolean("showVideoRecordButton", true);
        showVideoRecordIndication = preferences.getBoolean("showVideoRecordIndication", true);
        osdTextColor = preferences.getInt("osdTextColor", 0xFFFFFFFF);
        telemetryRefreshRate = parseInt(preferences.getString("telemetryRefreshRate", ""), 10);
        rcRefreshRate = parseInt(preferences.getString("rcRefreshRate", ""), 20);
        serialBaudRate = parseInt(preferences.getString("serialBaudRate", ""), 115200);
        serialPortIndex = parseInt(preferences.getString("serialPortIndex", ""), 0);
        ip = preferences.getString("ip", "");
        port = preferences.getInt("port", UdpCommon.defaultPort);
        key = preferences.getString("key", "DD");
        isViewer = preferences.getBoolean("isViewer", false);
        // RC channels map
        boolean setDefaultRcMap = true;
        for (int i = 0; i < FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT; i++) {
            rcChannelsMap[i] = preferences.getInt("rcMap" + i, -1);
            if (rcChannelsMap[i] != -1) setDefaultRcMap = false;
        }
        if (setDefaultRcMap) setDefaultRcMap();
    }

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

    public int getSerialPortIndex() {
        return serialPortIndex;
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
