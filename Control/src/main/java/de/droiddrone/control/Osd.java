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

import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.NetworkState;
import de.droiddrone.common.OsdCommon;
import de.droiddrone.common.Utils;

public class Osd {
    private final GlRenderer renderer;
    public static final int rawPhoneOsdHeight = 50;
    public static final int rawRecButtonOffset = 270;
    private final int phoneBatteryIconSteps = 6;
    private final Config config;
    private FcInfo fcInfo = null;
    private OsdItem[] activeItems = null;
    private String[] boxNames = null;
    private int[] boxIds = null;// permanent IDs
    private FcCommon.BoxMode[] activeBoxModes = null;
    private OsdConfig osdConfig = null;
    private float roll;
    private float pitch;
    private float yaw;
    private int mahDrawn;
    private int rssi; // %
    private float amperage; // A
    private float voltage; // V
    private int capacity;
    private float minCellVoltage;
    private float maxCellVoltage;
    private float warningCellVoltage;
    private boolean hasBatteryConfig;
    private FcCommon.BatteryState batteryState;
    private boolean batteryWasFull;
    private boolean batteryUsesCapacityThresholds;
    private byte batteryCellCount;
    private float power;
    private int mwhDrawn;
    private int batteryRemainingCapacity;
    private byte batteryPercentage = -1;
    private int cycleTime;
    private int i2cErrorCount;
    private short averageSystemLoad;
    private byte batteryProfile;
    private byte configProfile;
    private boolean configStateRebootRequired;
    private byte pidProfileIndex;
    private int coreTemperatureCelsius; // deg C
    private int baroTemperatureCelsius; // deg C
    private final ArmingFlags armingFlags = new ArmingFlags();
    private final SensorStatus sensorStatus = new SensorStatus();
    private int onTime; // s
    private int flyTime; // s
    private int lastArmTime; // s
    private int altitude; // m
    private float altVelocity; // m/s
    private int altBaro;
    private FcCommon.GpsFixTypes fixType;
    private int numSat;
    private float latDeg;
    private float lonDeg;
    private float homeLatDeg;
    private float homeLonDeg;
    private int altGps;
    private float groundSpeed; // km/h
    private float groundCourse;
    private int mahPerKm;
    private int hdop;
    private int distanceToHome; // m
    private float traveledDistance; // m
    private float directionToHome; // grad
    private boolean gpsHeartbeat;
    private String vtxBand;
    private byte vtxChannel;
    private short vtxPower;
    private boolean vtxPitMode;
    private int vtxFrequency;
    private boolean vtxDeviceIsReady;
    private int throttle; // %
    private FcCommon.VtxLowerPowerDisarm vtxLowPowerDisarm;
    private byte dronePhoneBatteryPercentage, controlPhoneBatteryPercentage;
    private boolean dronePhoneBatteryIsCharging, controlPhoneBatteryIsCharging;
    private short cameraFps;
    private short glFps;
    private float videoBitRate;
    private boolean isVideoRecorded;
    private int videoRecordingTimeSec;
    private long videoRecordingBlinkTimestamp;
    private final ArrayList<Integer> pings = new ArrayList<>();
    private int pingMs;
    private long lastPingTimestamp;
    private long lastDataTimestamp;
    private int canvasCols, canvasRows;
    private int screenWidth, screenHeight;
    private float osdWidthFactor, osdHeightFactor;
    private float osdCanvasFactor;
    private float screenFactor;
    private float phoneOsdHeight;
    private boolean wasArmed;
    private boolean isArmed;
    private final OsdStats osdStats;
    private final DecimalFormat format1Digit = new DecimalFormat("0.0");
    private final DecimalFormat format2Digit = new DecimalFormat("0.00");
    private final DecimalFormat format6Digit = new DecimalFormat("0.000000");
    private final ArrayList<Integer> mahKmList = new ArrayList<>();
    private NetworkState droneNetworkState = new NetworkState();
    private NetworkState controlNetworkState = new NetworkState();
    private boolean apOsd1Enabled = true;
    private int apCustomMode = -1;
    private long apBatteryFaultBitmask;
    private final List<ArduPilotStatusText> arduPilotMessages = new ArrayList<>();

    public Osd(GlRenderer renderer, Config config) {
        this.renderer = renderer;
        this.config = config;
        screenWidth = 1920;
        screenHeight = 1080;
        canvasCols = OsdCommon.canvasSizes.PAL_COLS;
        canvasRows = OsdCommon.canvasSizes.PAL_ROWS;
        osdCanvasFactor = 1;
        phoneOsdHeight = rawPhoneOsdHeight;
        osdStats = new OsdStats();
    }

    public void initialize(FcInfo fcInfo){
        this.fcInfo = fcInfo;
        lastDataTimestamp = 0;
    }

    public boolean isInitialized(){
        return (fcInfo != null);
    }

    public void setCanvasSize(int cols, int rows){
        canvasCols = cols;
        canvasRows = rows;
        osdCanvasFactor = OsdCommon.canvasSizes.PAL_COLS / (float) cols;
        renderer.setOsdCanvasFactor(osdCanvasFactor);
        updateOsdFactor();
    }

    public void setScreenSize(int width, int height, float screenFactor){
        screenWidth = width;
        screenHeight = height;
        this.screenFactor = screenFactor;
        if (isDrawPhoneOsd()) {
            phoneOsdHeight = rawPhoneOsdHeight * screenFactor;
        }else{
            phoneOsdHeight = 0;
        }
        updateOsdFactor();
    }

    private boolean isDrawPhoneOsd(){
        return (config.isShowPhoneBattery() || config.isShowCameraFps() || config.isShowScreenFps() || config.isShowVideoBitrate()
                || config.isShowPing() || config.isShowVideoRecordButton() || config.isShowVideoRecordIndication()
                || config.isShowNetworkState());
    }

    private void updateOsdFactor(){
        osdWidthFactor = (float) screenWidth / canvasCols;
        osdHeightFactor = (screenHeight - phoneOsdHeight) / canvasRows;
    }

    private float getOsdItemScreenX(int posX){
        return posX * osdWidthFactor;
    }

    private float getOsdItemScreenY(int posY){
        return screenHeight - phoneOsdHeight - posY * osdHeightFactor;
    }

    private String getTimeFormatted(int timeSec) {
        int minutes = timeSec / 60;
        int seconds = timeSec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private class OsdItemVtxChannel extends OsdItem{

        public OsdItemVtxChannel(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText("CH: " + vtxChannel, getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private class OsdItemVtxPower extends OsdItem{

        public OsdItemVtxPower(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.VTX_POWER, String.valueOf(vtxPower), true, false);
        }
    }

    private class OsdItemThrottle extends OsdItem{

        public OsdItemThrottle(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText("THR: " + throttle + "%", getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private class OsdItemCoreTemperature extends OsdItem{

        public OsdItemCoreTemperature(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.TEMPERATURE, coreTemperatureCelsius + " °C", true, false);
        }
    }

    private class OsdItemBaroTemperature extends OsdItem{

        public OsdItemBaroTemperature(@NonNull OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.TEMPERATURE, baroTemperatureCelsius + " °C", true, false);
        }
    }

    private class OsdItemEfficiencyMahPerKm extends OsdItem{

        public OsdItemEfficiencyMahPerKm(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            String value;
            if (mahPerKm == 0){
                value = "-";
            }else{
                value = String.valueOf(mahPerKm);
            }
            renderer.addText("mAh/km: " + value, getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private void calculateMahPerKm(float amperage, float groundSpeed){
        if (amperage <= 0 || groundSpeed <= 5) {
            mahPerKm = 0;
            return;
        }
        mahKmList.add(Math.round(amperage / groundSpeed * 1000));
        float mahPerKmAvg = 0;
        for (int m : mahKmList) mahPerKmAvg += m;
        mahPerKmAvg = mahPerKmAvg / mahKmList.size();
        mahPerKm = Math.round(mahPerKmAvg);
        if (mahKmList.size() >= 10) mahKmList.remove(0);
    }

    private class OsdItemHomeDist extends OsdItem{

        public OsdItemHomeDist(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.distAlarm > 0 && distanceToHome >= osdConfig.distAlarm);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.DIST_HOME, formatDistance(distanceToHome), true, warning);
        }
    }

    private final class OsdItemTripDistance extends OsdItem{

        public OsdItemTripDistance(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.TRIP_DIST, formatDistance(traveledDistance), true, false);
        }
    }

    private String formatDistance(float distance){
        if (Math.abs(distance) > 1000){
            return format2Digit.format(distance / 1000) + " km";
        }else{
            return Math.round(distance) + " m";
        }
    }

    private class OsdItemHomeDir extends OsdItem{

        public OsdItemHomeDir(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            if (distanceToHome > 1) {
                glSprites.addSpriteAngle(SpritesMapping.DIR_TO_HOME, getOsdItemScreenX(posX), getOsdItemScreenY(posY), directionToHome - yaw);
            }else{
                glSprites.addSprite(SpritesMapping.HOME, getOsdItemScreenX(posX), getOsdItemScreenY(posY), 1.8f);
            }
        }
    }

    private class OsdItemHomeDirDist extends OsdItem{

        public OsdItemHomeDirDist(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            if (distanceToHome > 1) {
                glSprites.addSpriteAngle(SpritesMapping.DIR_TO_HOME, getOsdItemScreenX(posX), getOsdItemScreenY(posY), directionToHome - yaw);
            }else{
                glSprites.addSprite(SpritesMapping.HOME, getOsdItemScreenX(posX), getOsdItemScreenY(posY), 1.8f);
            }
            boolean warning = (osdConfig != null && osdConfig.distAlarm > 0 && distanceToHome >= osdConfig.distAlarm);
            renderer.addText(formatDistance(distanceToHome), getOsdItemScreenX(posX) + 1 * osdWidthFactor, getOsdItemScreenY(posY), warning);
        }
    }

    private class OsdItemGpsLon extends OsdItem{

        public OsdItemGpsLon(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText("LON: " + formatLatLon(lonDeg), getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private class OsdItemGpsLat extends OsdItem{

        public OsdItemGpsLat(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText("LAT: " + formatLatLon(latDeg), getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private String formatLatLon(float latLon){
        return format6Digit.format(latLon);
    }

    private class OsdItemGpsSpeed extends OsdItem{

        public OsdItemGpsSpeed(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText("GS: " + Math.round(groundSpeed) + " km/h", getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private class OsdItemOnTime extends OsdItem{

        public OsdItemOnTime(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.ON_TIME, getTimeFormatted(onTime), true, false);
        }
    }

    private final class OsdItemFlyTime extends OsdItemOnTime{

        public OsdItemFlyTime(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.timeAlarmSec > 0 && flyTime >= osdConfig.timeAlarmSec);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.FLY_TIME, getTimeFormatted(flyTime), true, warning);
        }
    }

    private final class OsdItemLastArmTime extends OsdItemOnTime{

        public OsdItemLastArmTime(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.timeAlarmSec > 0 && flyTime >= osdConfig.timeAlarmSec);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.FLY_TIME, getTimeFormatted(lastArmTime), true, warning);
        }
    }

    private final class OsdItemOnTimeFlyTime extends OsdItemOnTime{

        public OsdItemOnTimeFlyTime(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            int icon;
            String timeFormatted;
            boolean warning = false;
            if (isArmed){
                icon = SpritesMapping.FLY_TIME;
                timeFormatted = getTimeFormatted(flyTime);
                warning = (osdConfig != null && osdConfig.timeAlarmSec > 0 && flyTime >= osdConfig.timeAlarmSec);
            }else{
                icon = SpritesMapping.ON_TIME;
                timeFormatted = getTimeFormatted(onTime);
            }
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), icon, timeFormatted, true, warning);
        }
    }

    private boolean isArmed(){
        boolean isArmed = false;
        FcCommon.BoxMode[] activeBoxModes = Osd.this.activeBoxModes;
        if (activeBoxModes != null) {
            for (FcCommon.BoxMode box : activeBoxModes) {
                if (box.boxId == FcCommon.BoxModeIds.BOXARM) {
                    isArmed = true;
                    break;
                }
            }
        }
        return isArmed;
    }

    private final class OsdItemFlyMode extends OsdItem{

        public OsdItemFlyMode(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            FcCommon.BoxMode[] activeBoxModes = Osd.this.activeBoxModes;
            if (activeBoxModes == null || activeBoxModes.length == 0) return;
            int maxPriority = 0;
            int maxPriorityId = -1;
            for (int i = 0; i < activeBoxModes.length; i++) {
                if (activeBoxModes[i].osdPriority > maxPriority){
                    maxPriority = activeBoxModes[i].osdPriority;
                    maxPriorityId = i;
                }
            }
            if (maxPriorityId == -1) return;
            renderer.addText(activeBoxModes[maxPriorityId].boxName, getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private final class OsdItemFlyModeArduPilot extends OsdItem{

        public OsdItemFlyModeArduPilot(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            if (fcInfo == null) return;
            FcCommon.ArduPilotMode[] modes = null;
            switch (FcCommon.PlatformTypesArduPilot.getArduPilotPlatformBaseType(fcInfo.getPlatformType())){
                case FcCommon.PlatformTypesArduPilot.AP_BASE_TYPE_COPTER:
                    modes = FcCommon.ArduPilotModesCopter;
                    break;
                case FcCommon.PlatformTypesArduPilot.AP_BASE_TYPE_PLANE:
                    modes = FcCommon.ArduPilotModesPlane;
                    break;
                case FcCommon.PlatformTypesArduPilot.AP_BASE_TYPE_ROVER:
                    modes = FcCommon.ArduPilotModesRover;
                    break;
            }
            if (modes == null) return;
            for (FcCommon.ArduPilotMode mode : modes){
                if (mode.modeId == apCustomMode){
                    renderer.addText(mode.modeName, getOsdItemScreenX(posX), getOsdItemScreenY(posY));
                    return;
                }
            }
        }
    }

    private final class OsdItemMessages extends OsdItem{

        public OsdItemMessages(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            ArmingFlags armingFlags = Osd.this.armingFlags;
            if (armingFlags.armed) return;
            List<String> messages = new ArrayList<>();
            if (armingFlags.armingDisabledHardwareFailure) messages.add("Hardware failure");
            if (armingFlags.armingDisabledSystemOverloaded) messages.add("System overloaded");
            if (armingFlags.armingDisabledInvalidSetting) messages.add("Invalid setting");
            if (armingFlags.armingDisabledPwmOutputError) messages.add("PWM output error");
            if (armingFlags.armingDisabledNoGyro) messages.add("No gyro");
            if (armingFlags.armingDisabledFailsafeSystem) messages.add("Failsafe");
            if (armingFlags.armingDisabledBoxFailsafe) messages.add("Box failsafe");
            if (armingFlags.armingDisabledBoxKillSwitch) messages.add("Box killswitch");
            if (armingFlags.armingDisabledArmSwitch) messages.add("Arm switch");
            if (armingFlags.armingDisabledNoPrearm) messages.add("No prearm");
            if (armingFlags.armingDisabledThrottle) messages.add("Throttle");
            if (armingFlags.armingDisabledRollPitchNotCentered) messages.add("Roll/Pitch not centered");
            if (armingFlags.armingDisabledRcLink) messages.add("No RC link");
            if (armingFlags.armingDisabledNotLevel) messages.add("Not level");
            if (armingFlags.armingDisabledAccelerometerNotCalibrated) messages.add("Accelerometer not calibrated");
            if (armingFlags.armingDisabledCompassNotCalibrated) messages.add("Compass not calibrated");
            if (armingFlags.armingDisabledSensordCalibrating) messages.add("Sensor calibrating");
            if (armingFlags.armingDisabledAccCalibration) messages.add("Acc calibration");
            if (armingFlags.armingDisabledNavigationUnsafe) messages.add("Navigation unsafe");
            if (armingFlags.armingDisabledCli) messages.add("CLI");
            if (armingFlags.armingDisabledCmsMenu) messages.add("CMS menu");
            if (armingFlags.armingDisabledOsdMenu) messages.add("OSD menu");
            if (armingFlags.armingDisabledServoAutoTrim) messages.add("Servo auto trim");
            if (armingFlags.armingDisabledOom) messages.add("OOM");
            if (armingFlags.armingDisabledDshotBeeper) messages.add("DSHOT beeper");
            if (armingFlags.armingDisabledLandingDetected) messages.add("Landing detected");
            if (armingFlags.armingDisabledBadRxRecovery) messages.add("Bad RX recovery");
            if (armingFlags.armingDisabledRunawayTakeoff) messages.add("Runaway takeoff");
            if (armingFlags.armingDisabledCrashDetected) messages.add("Crash detected");
            if (armingFlags.armingDisabledBootGraceTime) messages.add("Boot grace time");
            if (armingFlags.armingDisabledBst) messages.add("BST");
            if (armingFlags.armingDisabledMsp) messages.add("MSP");
            if (armingFlags.armingDisabledParalyze) messages.add("Paralyze");
            if (armingFlags.armingDisabledResc) messages.add("Resc");
            if (armingFlags.armingDisabledRpmFilter) messages.add("RPM filter");
            if (armingFlags.armingDisabledRebootRequired) messages.add("Reboot required");
            if (armingFlags.armingDisabledDshotBitbang) messages.add("DSHOT bitbang");
            if (armingFlags.armingDisabledMotorProtocol) messages.add("Motor protocol");
            if (messages.isEmpty()) return;
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.ALERT, messages.get(0), true, false);
        }
    }

    private final class OsdItemMessagesArduPilot extends OsdItem{

        public OsdItemMessagesArduPilot(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            if (arduPilotMessages.isEmpty()) return;
            long current = System.currentTimeMillis();
            int msgTime = osdConfig.msgTime;
            if (msgTime < 1 || msgTime > 10) msgTime = 10;
            msgTime *= 1000;
            List<ArduPilotStatusText> toRemove = new ArrayList<>();
            for (ArduPilotStatusText item : arduPilotMessages){
                if (item.startTimeStamp > 0 && item.startTimeStamp + msgTime < current){
                    toRemove.add(item);
                    continue;
                }
                item.show(getOsdItemScreenX(posX), getOsdItemScreenY(posY), renderer);
                break;
            }
            if (!toRemove.isEmpty()) arduPilotMessages.removeAll(toRemove);
        }
    }

    private static final class ArduPilotStatusText{
        final short severity;
        final String message;
        long startTimeStamp;

        public ArduPilotStatusText(short severity, String message) {
            this.severity = severity;
            this.message = message;
            startTimeStamp = 0;
        }

        public void show(float x, float y, GlRenderer renderer){
            if (startTimeStamp == 0) startTimeStamp = System.currentTimeMillis();
            boolean warning = severity <= 3;
            renderer.addText(message, x, y, warning);
        }
    }

    private final class OsdItemAltitude extends OsdItem{

        public OsdItemAltitude(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.altAlarm > 0 && altitude >= osdConfig.altAlarm);
            warning = warning || (osdConfig != null && osdConfig.negAltAlarm != 0 && altitude <= osdConfig.negAltAlarm);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.ALTITUDE, String.valueOf(altitude), true, warning);
        }
    }

    private final class OsdItemGpsSats extends OsdItem{

        public OsdItemGpsSats(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            boolean warning = numSat < osdConfig.warnNumSat;
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.GPS_SAT, String.valueOf(numSat), true, warning);
        }
    }

    private final class OsdItemSysDelay extends OsdItem{

        public OsdItemSysDelay(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            int ping = getPing();
            String text = ping != -1 ? ping + " ms" : "--- ms";
            renderer.addText(text, getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private final class OsdItemSysBitRate extends OsdItem{

        public OsdItemSysBitRate(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText(formatBitRate(videoBitRate), getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private final class OsdItemCrosshairs extends OsdItem{

        public OsdItemCrosshairs(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            float spriteWidthD2 = glSprites.getSpriteWidth(SpritesMapping.CROSSHAIR_4) / 2f;
            float spriteHeightD2 = glSprites.getSpriteHeight(SpritesMapping.CROSSHAIR_4) / 2f;
            float centerX = screenWidth / 2f;
            float centerY = (screenHeight - phoneOsdHeight) / 2f;
            glSprites.addSprite(SpritesMapping.CROSSHAIR_4, centerX - spriteWidthD2, centerY + spriteHeightD2);
        }
    }

    private final class OsdItemArtificialHorizon extends OsdItem{

        public OsdItemArtificialHorizon(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            float sizeFactor = glSprites.getDefaultSizeFactor() / osdCanvasFactor;
            float centerX = screenWidth / 2f;
            float centerY = (screenHeight - phoneOsdHeight) / 2f;
            float horizonWidthD2 = glSprites.getSpriteWidth(SpritesMapping.ARTIFICIAL_HORIZON, sizeFactor) / 2f;
            float horizonHeightD2 = glSprites.getSpriteHeight(SpritesMapping.ARTIFICIAL_HORIZON, sizeFactor) / 2f;
            float offsetY = pitch * screenFactor * 3f;
            glSprites.addSpriteAngle(SpritesMapping.ARTIFICIAL_HORIZON, centerX - horizonWidthD2, centerY + horizonHeightD2 + offsetY, -roll, sizeFactor);
        }
    }

    private final class OsdItemHeadingGrad extends OsdItem{

        public OsdItemHeadingGrad(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText(Math.round(yaw) + "°", getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private class OsdItemCompassBar extends OsdItem{
        private static final float angleStep = 22.5f;
        private static final int fullSpritesOnScreen = 9;
        private final int[] spritesLine = {
                SpritesMapping.HEADING_N, SpritesMapping.HEADING_LINE, SpritesMapping.HEADING_DIVIDED_LINE, SpritesMapping.HEADING_LINE,
                SpritesMapping.HEADING_E, SpritesMapping.HEADING_LINE, SpritesMapping.HEADING_DIVIDED_LINE, SpritesMapping.HEADING_LINE,
                SpritesMapping.HEADING_S, SpritesMapping.HEADING_LINE, SpritesMapping.HEADING_DIVIDED_LINE, SpritesMapping.HEADING_LINE,
                SpritesMapping.HEADING_W, SpritesMapping.HEADING_LINE, SpritesMapping.HEADING_DIVIDED_LINE, SpritesMapping.HEADING_LINE};

        public OsdItemCompassBar(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            float spriteWidth = glSprites.getSpriteWidth(SpritesMapping.HEADING_N);
            float rawSpriteWidth = glSprites.getRawSpriteWidth(SpritesMapping.HEADING_N);
            float totalWidth = fullSpritesOnScreen * spriteWidth;
            float firstSpriteAngle = yaw - 90;
            if (firstSpriteAngle < 0) firstSpriteAngle += 360;
            float x = getOsdItemScreenX(posX);
            float y = getOsdItemScreenY(posY);
            int spriteInd = (int)(firstSpriteAngle / angleStep);
            float firstAngleOffset = angleStep - (firstSpriteAngle - spriteInd * angleStep);
            float firstSpriteWidth = firstAngleOffset * rawSpriteWidth / angleStep;
            float widthDrawed = 0;
            for (int i = 0; i < fullSpritesOnScreen + 1; i++) {
                if (spriteInd >= spritesLine.length) spriteInd = 0;
                if (i == 0){
                    glSprites.addSpriteCutLeft(spritesLine[spriteInd], x, y, firstSpriteWidth);
                    firstSpriteWidth *= glSprites.getDefaultSizeFactor() * screenFactor * osdCanvasFactor;
                    x += firstSpriteWidth;
                    widthDrawed += firstSpriteWidth;
                }else if (i == fullSpritesOnScreen){
                    if (widthDrawed >= totalWidth) break;
                    float lastSpriteWidth = (totalWidth - widthDrawed) / glSprites.getDefaultSizeFactor() / screenFactor / osdCanvasFactor;
                    glSprites.addSpriteCutRight(spritesLine[spriteInd], x, y, lastSpriteWidth);
                }else{
                    glSprites.addSprite(spritesLine[spriteInd], x, y);
                    x += spriteWidth;
                    widthDrawed += spriteWidth;
                }
                spriteInd++;
            }
            x = getOsdItemScreenX(posX) + totalWidth / 2f - glSprites.getSpriteWidth(SpritesMapping.HEADING_CENTER_ARROW) / 2f;
            glSprites.addSprite(SpritesMapping.HEADING_CENTER_ARROW, x, y);
        }
    }

    private final class OsdItemHorizonSidebars extends OsdItem{

        public OsdItemHorizonSidebars(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            GlSprites glSprites = renderer.getGlSpritesObject();
            if (glSprites == null) return;
            float sizeFactor = glSprites.getDefaultSizeFactor() / osdCanvasFactor;
            float centerX = screenWidth / 2f;
            float centerY = (screenHeight - phoneOsdHeight) / 2f;
            float sidebarsOffset = 250 * screenFactor;
            float sidebarWidthD2 = glSprites.getSpriteWidth(SpritesMapping.SIDEBAR, sizeFactor) / 2f;
            float sidebarHeightD2 = glSprites.getSpriteHeight(SpritesMapping.SIDEBAR, sizeFactor) / 2f;
            glSprites.addSprite(SpritesMapping.SIDEBAR, centerX - sidebarsOffset - sidebarWidthD2, centerY + sidebarHeightD2, sizeFactor);
            glSprites.addSprite(SpritesMapping.SIDEBAR, centerX + sidebarsOffset - sidebarWidthD2, centerY + sidebarHeightD2, sizeFactor);
        }
    }

    private final class OsdItemPower extends OsdItem{

        public OsdItemPower(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText(power + " W", getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private final class OsdItemMahDraw extends OsdItem{

        public OsdItemMahDraw(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.capacityWarning > 0 && mahDrawn >= osdConfig.capacityWarning);
            String text = mahDrawn + " mAh";
            renderer.addText(text, getOsdItemScreenX(posX), getOsdItemScreenY(posY), warning);
        }
    }

    private String formatAmperage(float amperage){
        return format2Digit.format(amperage) + " A";
    }

    private final class OsdItemCurrentDraw extends OsdItem{

        public OsdItemCurrentDraw(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            renderer.addText(formatAmperage(amperage), getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private String formatVoltage(float voltage){
        return format2Digit.format(voltage) + "V";
    }

    private int getPhoneBatteryIcon(int batteryPercentage, boolean isCharging){
        if (batteryPercentage <= 0 || batteryPercentage > 100) return SpritesMapping.BATT_ALERT;
        if (isCharging) return SpritesMapping.BATT_CHARGING;
        int step = phoneBatteryIconSteps - Math.round(batteryPercentage * phoneBatteryIconSteps / 100f);
        return SpritesMapping.BATT_FULL + step;
    }

    private class OsdItemMainBattCellVoltage extends OsdItem{

        final int batteryIconSteps = 6;

        public OsdItemMainBattCellVoltage(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        int getBatteryIcon(int batteryPercentage){
            if (batteryState == FcCommon.BatteryState.BATTERY_CRITICAL || batteryState == FcCommon.BatteryState.BATTERY_WARNING
                    || batteryState == FcCommon.BatteryState.BATTERY_NOT_PRESENT || batteryPercentage <= 0 || batteryPercentage > 100
                    || apBatteryFaultBitmask != 0 || voltage < 2) return SpritesMapping.BATT_ALERT;
            int step = batteryIconSteps - Math.round(batteryPercentage * batteryIconSteps / 100f);
            return SpritesMapping.BATT_FULL + step;
        }

        int getBatteryPercentageFromCellVoltage(float cellVoltage){
            if (cellVoltage == 0 || !hasBatteryConfig) return 0;
            if (cellVoltage < minCellVoltage) return 0;
            return Math.round((cellVoltage - minCellVoltage) * 100 / (maxCellVoltage - minCellVoltage));
        }

        float getCellVoltage(byte cellCount){
            return cellCount > 0 ? voltage / cellCount : 0;
        }

        @Override
        public void draw(){
            float cellVoltage = getCellVoltage(batteryCellCount);
            int percentage = batteryPercentage >= 0 ? batteryPercentage : getBatteryPercentageFromCellVoltage(cellVoltage);
            int icon = getBatteryIcon(percentage);
            boolean warning = (icon == SpritesMapping.BATT_ALERT);
            warning = warning || cellVoltage < osdConfig.warnAvgCellVolt;
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), icon, formatVoltage(cellVoltage), true, warning);
        }
    }

    private class OsdItemMainBattVoltage extends OsdItemMainBattCellVoltage{

        public OsdItemMainBattVoltage(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            int percentage = batteryPercentage >= 0 ? batteryPercentage : getBatteryPercentageFromCellVoltage(getCellVoltage(batteryCellCount));
            int icon = getBatteryIcon(percentage);
            boolean warning = (icon == SpritesMapping.BATT_ALERT);
            warning = warning || voltage < osdConfig.warnBatVolt;
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), icon, formatVoltage(voltage), true, warning);
        }
    }

    private class OsdItemMainBattVoltageArduPilot extends OsdItemMainBattCellVoltage{
        public OsdItemMainBattVoltageArduPilot(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            int icon = getBatteryIcon(batteryPercentage);
            boolean warning = (icon == SpritesMapping.BATT_ALERT || voltage < osdConfig.warnBatVolt);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), icon, formatVoltage(voltage), true, warning);
        }
    }

    private class OsdItemMainBattCellVoltageArduPilot extends OsdItemMainBattCellVoltage{
        public OsdItemMainBattCellVoltageArduPilot(OsdItem item) {
            super(item);
        }

        @Override
        public void draw(){
            int icon = getBatteryIcon(batteryPercentage);
            byte cellCount = osdConfig.osdCellCount > 0 ? osdConfig.osdCellCount : batteryCellCount;
            float cellVoltage = getCellVoltage(cellCount);
            boolean warning = (icon == SpritesMapping.BATT_ALERT || cellVoltage < osdConfig.warnAvgCellVolt);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), icon, formatVoltage(cellVoltage), true, warning);
        }
    }

    private final class OsdItemRssi extends OsdItem{

        public OsdItemRssi(OsdItem item) {
            super(item.id, item.isVisible, item.isBlink, item.posX, item.posY, item.variant);
        }

        @Override
        public void draw(){
            boolean warning = (osdConfig != null && osdConfig.rssiAlarm > 0 && rssi <= osdConfig.rssiAlarm);
            renderer.addSpriteWithText(getOsdItemScreenX(posX), getOsdItemScreenY(posY), SpritesMapping.RSSI, rssi + "%", true, warning);
        }
    }

    public class OsdItem{
        public final int id;
        public final boolean isVisible;
        public final boolean isBlink;
        public final byte posX;
        public final byte posY;
        public final byte variant;

        public OsdItem(int id, boolean isVisible, boolean isBlink, byte posX, byte posY, byte variant) {
            this.id = id;
            this.isVisible = isVisible;
            this.isBlink = isBlink;
            this.posX = posX;
            this.posY = posY;
            this.variant = variant;
        }

        public OsdItem(int id, boolean isVisible, byte posX, byte posY) {
            this.id = id;
            this.isVisible = isVisible;
            this.isBlink = false;
            this.posX = posX;
            this.posY = posY;
            this.variant = 0;
        }

        public OsdItem(int id, short item, int fcVariant, byte osdSelectedProfile) {
            this.id = id;
            if (fcVariant == FcInfo.FC_VARIANT_INAV) {
                isVisible = ((item >> 13 & 1) == 1);
                isBlink = ((item >> 12 & 1) == 1);
                posX = (byte) (item & 0x3F);
                posY = (byte) (item >> 6 & 0x3F);
                variant = 0;
            } else {// Betaflight
                isVisible = (item >> (10 + osdSelectedProfile) & 1) == 1;
                isBlink = false;
                posX = (byte) (item >> 5 & 0x20 | item & 0x1F);
                posY = (byte) (item >> 5 & 0x1F);
                variant = (byte) (item >> 14 & 0x3);
            }
        }

        public void draw(){
            renderer.addText("X", getOsdItemScreenX(posX), getOsdItemScreenY(posY));
        }
    }

    private void drawTelemetryDataWarning(int spriteName, String msg){
        GlText glText = renderer.getGlTextObject();
        GlSprites glSprites = renderer.getGlSpritesObject();
        float textLength = 0;
        float spriteWidth = 0;
        if (glText != null) textLength = glText.getLengthInPixels(msg);
        if (glSprites != null) spriteWidth = glSprites.getSpriteWidth(spriteName);
        renderer.addSpriteWithText(screenWidth / 2f - (textLength + spriteWidth) / 2f, (screenHeight - phoneOsdHeight) / 2f, spriteName, msg, true, false);
    }

    private String formatBitRate(float bitRate){
        return format1Digit.format(bitRate) + "Mbit/s";
    }

    private void drawPhoneOsd() {
        GlText glText = renderer.getGlTextObject();
        GlSprites glSprites = renderer.getGlSpritesObject();
        if (glText == null || glSprites == null) return;

        float textSize = 8.5f / osdCanvasFactor;
        float spriteSize = 1 / osdCanvasFactor;
        float xOffset = 5 * screenFactor;
        float y = screenHeight;
        float textSpace = 35 * screenFactor;
        float spriteSpace = 10 * screenFactor;
        String text;

        if (config.isShowPhoneBattery()) {
            int icon = getPhoneBatteryIcon(dronePhoneBatteryPercentage, dronePhoneBatteryIsCharging);
            glSprites.addSprite(icon, xOffset, y, spriteSize);
            xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            if (dronePhoneBatteryPercentage <= 30){
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            text = dronePhoneBatteryPercentage + "%";
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace / 3;

            icon = getPhoneBatteryIcon(controlPhoneBatteryPercentage, controlPhoneBatteryIsCharging);
            glSprites.addSprite(icon, xOffset, y, spriteSize);
            xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            if (controlPhoneBatteryPercentage <= 30){
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            text = controlPhoneBatteryPercentage + "%";
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace;
        }

        if (config.isShowNetworkState()) {
            text = droneNetworkState.getNetworkName() + " " + droneNetworkState.getRssi() + "%/"
                    + controlNetworkState.getNetworkName() + " " + controlNetworkState.getRssi() + "%";
            if (droneNetworkState.getRssi() < 40 || controlNetworkState.getRssi() < 40
                    || droneNetworkState.getNetworkType() < NetworkState.NETWORK_TYPE_LTE
                    || controlNetworkState.getNetworkType() < NetworkState.NETWORK_TYPE_LTE) {
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace;
        }

        if (config.isShowCameraFps()) {
            text = "Cam.FPS: " + cameraFps;
            if (cameraFps < 20) {
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace;
        }

        if (config.isShowScreenFps()) {
            text = "Scr.FPS: " + glFps;
            if (glFps < 20) {
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace;
        }

        if (config.isShowVideoBitrate()) {
            text = formatBitRate(videoBitRate);
            if (videoBitRate < 1.5f) {
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            glText.addText(text, xOffset, y, textSize);
            xOffset += glText.getLengthInPixels(text, textSize) + textSpace;
        }

        if (config.isShowPing()) {
            int ping = getPing();
            text = ping != -1 ? "Ping: " + ping + "ms" : "Ping: ---";
            if (ping > 300 || ping == -1) {
                glSprites.addSprite(SpritesMapping.ALERT, xOffset, y, spriteSize);
                xOffset += glSprites.getSpriteWidth(SpritesMapping.ALERT, spriteSize) + spriteSpace;
            }
            glText.addText(text, xOffset, y, textSize);
        }

        if (isVideoRecorded && config.isShowVideoRecordIndication()){
            text = getTimeFormatted(videoRecordingTimeSec);
            xOffset = screenWidth - rawRecButtonOffset * screenFactor + rawPhoneOsdHeight * screenFactor + spriteSpace;
            long currentTimestamp = System.currentTimeMillis();
            if (videoRecordingBlinkTimestamp == 0) videoRecordingBlinkTimestamp = currentTimestamp;
            if ((currentTimestamp - videoRecordingBlinkTimestamp) / 1500 % 2 == 0){
                glSprites.addSprite(SpritesMapping.VIDEO_RECORDING, xOffset, y - 8 * screenFactor, spriteSize);
            }
            xOffset += glSprites.getSpriteWidth(SpritesMapping.VIDEO_RECORDING, spriteSize) + spriteSpace;
            glText.addText(text, xOffset, y, textSize);
        }
    }

    public void drawItems(){
        if (activeItems == null) {
            drawTelemetryDataWarning(0, "Awaiting OSD initialization...");
            return;
        }
        if (fcInfo != null && fcInfo.getFcVariant() == FcInfo.FC_VARIANT_ARDUPILOT){
            if (!apOsd1Enabled){
                drawTelemetryDataWarning(0, "OSD Screen1 is disabled.");
                return;
            }
        }
        boolean drawStatistic = wasArmed && !isArmed;
        if (drawStatistic && osdStats.lastArmTime > 5){
            osdStats.draw();
        }else{
            if (isTelemetryDataReceived()) {
                for (OsdItem item : activeItems) {
                    if (item == null) continue;
                    try {
                        item.draw();
                    } catch (Exception e) {
                        //
                    }
                }
            } else {
                drawTelemetryDataWarning(SpritesMapping.ALERT, "No telemetry data!");
            }
            if (isDrawPhoneOsd()) drawPhoneOsd();
        }
    }

    private void updateLastDataTimestamp(){
        lastDataTimestamp = System.currentTimeMillis();
    }

    private boolean isTelemetryDataReceived(){
        return (lastDataTimestamp + 1500 > System.currentTimeMillis());
    }

    public void setTimers(int onTime, int flyTime, int lastArmTime){// DD_TIMERS
        this.onTime = onTime;
        this.flyTime = flyTime;
        this.lastArmTime = lastArmTime;
        osdStats.setTimers(onTime, flyTime, lastArmTime);
    }

    public void setDronePhoneBatteryState(byte dronePhoneBatteryPercentage, boolean dronePhoneBatteryIsCharging){// DD_PHONE_BATTERY_STATE
        this.dronePhoneBatteryPercentage = dronePhoneBatteryPercentage;
        this.dronePhoneBatteryIsCharging = dronePhoneBatteryIsCharging;
    }

    public void setControlPhoneBatteryState(byte controlPhoneBatteryPercentage, boolean controlPhoneBatteryIsCharging){
        this.controlPhoneBatteryPercentage = controlPhoneBatteryPercentage;
        this.controlPhoneBatteryIsCharging = controlPhoneBatteryIsCharging;
    }

    public void setDroneNetworkState(int networkType, int rssi){// DD_NETWORK_STATE
        droneNetworkState = new NetworkState(networkType, rssi);
    }

    public void setControlNetworkState(NetworkState networkState){
        controlNetworkState = networkState;
    }

    public void setCameraFps(short cameraFps){// DD_CAMERA_FPS
        this.cameraFps = cameraFps;
    }

    public void setGlFps(short glFps){
        this.glFps = glFps;
    }

    public void setVideoBitRate(float videoBitRate){// DD_VIDEO_BIT_RATE
        this.videoBitRate = videoBitRate;
        osdStats.setBitRate(videoBitRate);
    }

    public void setVideoRecorderState(boolean isRecording, int recordingTimeSec){// DD_VIDEO_RECORDER_STATE
        this.isVideoRecorded = isRecording;
        this.videoRecordingTimeSec = recordingTimeSec;
        if (!isRecording) videoRecordingBlinkTimestamp = 0;
        renderer.setRecButtonState(isRecording);
    }

    public void setPing(int pingMs){
        pings.add(pingMs);
        int avgPing = 0;
        for (int p : pings) avgPing += p;
        avgPing = avgPing / pings.size();
        if (pings.size() >= 10) pings.remove(0);
        this.pingMs = avgPing;
        lastPingTimestamp = System.currentTimeMillis();
        osdStats.setPing(avgPing);
    }

    private int getPing(){
        if (System.currentTimeMillis() - lastPingTimestamp > 2000){
            return -1;
        }else{
            return pingMs;
        }
    }

    public void setStatusBetaflight(short cycleTime, short i2cErrorCount, short sensorStatus, byte currentPidProfileIndex, short averageSystemLoad,
                                    byte[] modeFlags, int armingFlags, byte configStateFlags, short coreTemperatureCelsius){//MSP_STATUS
        updateLastDataTimestamp();
        this.cycleTime = cycleTime & 0xFFFF;
        this.i2cErrorCount = i2cErrorCount & 0xFFFF;
        this.sensorStatus.sensorAcc = ((sensorStatus & 1) == 1);
        this.sensorStatus.sensorBaro = ((sensorStatus >> 1 & 1) == 1);
        this.sensorStatus.sensorMag = ((sensorStatus >> 2 & 1) == 1);
        this.sensorStatus.sensorGps = ((sensorStatus >> 3 & 1) == 1);
        this.sensorStatus.sensorRangefinder = ((sensorStatus >> 4 & 1) == 1);
        this.sensorStatus.sensorGyro = ((sensorStatus >> 5 & 1) == 1);
        pidProfileIndex = currentPidProfileIndex;
        this.averageSystemLoad = averageSystemLoad;
        //modeFlags
        this.armingFlags.armingDisabledNoGyro = ((armingFlags & 1) == 1);
        this.armingFlags.armingDisabledFailsafeSystem = ((armingFlags >> 1 & 1) == 1);
        this.armingFlags.armingDisabledRcLink = ((armingFlags >> 2 & 1) == 1);
        this.armingFlags.armingDisabledBadRxRecovery = ((armingFlags >> 3 & 1) == 1);
        this.armingFlags.armingDisabledBoxFailsafe = ((armingFlags >> 4 & 1) == 1);
        this.armingFlags.armingDisabledRunawayTakeoff = ((armingFlags >> 5 & 1) == 1);
        this.armingFlags.armingDisabledCrashDetected = ((armingFlags >> 6 & 1) == 1);
        this.armingFlags.armingDisabledThrottle = ((armingFlags >> 7 & 1) == 1);
        this.armingFlags.armingDisabledNotLevel = ((armingFlags >> 8 & 1) == 1);
        this.armingFlags.armingDisabledBootGraceTime = ((armingFlags >> 9 & 1) == 1);
        this.armingFlags.armingDisabledNoPrearm = ((armingFlags >> 10 & 1) == 1);
        this.armingFlags.armingDisabledSystemOverloaded = ((armingFlags >> 11 & 1) == 1);
        this.armingFlags.armingDisabledSensordCalibrating = ((armingFlags >> 12 & 1) == 1);
        this.armingFlags.armingDisabledCli = ((armingFlags >> 13 & 1) == 1);
        this.armingFlags.armingDisabledCmsMenu = ((armingFlags >> 14 & 1) == 1);
        this.armingFlags.armingDisabledBst = ((armingFlags >> 15 & 1) == 1);
        this.armingFlags.armingDisabledMsp = ((armingFlags >> 16 & 1) == 1);
        this.armingFlags.armingDisabledParalyze = ((armingFlags >> 17 & 1) == 1);
        this.armingFlags.armingDisabledNavigationUnsafe = ((armingFlags >> 18 & 1) == 1);
        this.armingFlags.armingDisabledResc = ((armingFlags >> 19 & 1) == 1);
        this.armingFlags.armingDisabledRpmFilter = ((armingFlags >> 20 & 1) == 1);
        this.armingFlags.armingDisabledRebootRequired = ((armingFlags >> 21 & 1) == 1);
        this.armingFlags.armingDisabledDshotBitbang = ((armingFlags >> 22 & 1) == 1);
        this.armingFlags.armingDisabledAccCalibration = ((armingFlags >> 23 & 1) == 1);
        this.armingFlags.armingDisabledMotorProtocol = ((armingFlags >> 24 & 1) == 1);
        this.armingFlags.armingDisabledArmSwitch = ((armingFlags >> 25 & 1) == 1);
        configStateRebootRequired = ((configStateFlags & 1) == 1);
        this.coreTemperatureCelsius = coreTemperatureCelsius;
        activeBoxModes = FcCommon.getActiveBoxesBtfl(modeFlags, boxIds);
        isArmed = isArmed();
        if (isArmed) wasArmed = true;
    }

    public void setStatusInav(short cycleTime, short i2cErrorCount, short sensorStatus, byte configProfile){//MSP_STATUS
        updateLastDataTimestamp();
        this.cycleTime = cycleTime & 0xFFFF;
        this.i2cErrorCount = i2cErrorCount & 0xFFFF;
        this.sensorStatus.sensorAcc = ((sensorStatus & 1) == 1);
        this.sensorStatus.sensorBaro = ((sensorStatus >> 1 & 1) == 1);
        this.sensorStatus.sensorMag = ((sensorStatus >> 2 & 1) == 1);
        this.sensorStatus.sensorGps = ((sensorStatus >> 3 & 1) == 1);
        this.sensorStatus.sensorRangefinder = ((sensorStatus >> 4 & 1) == 1);
        this.sensorStatus.sensorOpFlow = ((sensorStatus >> 5 & 1) == 1);
        this.sensorStatus.sensorPitot = ((sensorStatus >> 6 & 1) == 1);
        this.sensorStatus.sensorTemp = ((sensorStatus >> 7 & 1) == 1);
        this.sensorStatus.hardwareFailure = ((sensorStatus >> 15 & 1) == 1);
        this.configProfile = configProfile;
    }

    public void setInavStatus(short cycleTime, short i2cErrorCount, short sensorStatus, short averageSystemLoad, byte profiles, int armingFlags, int[] modeFlags) {//MSP2_INAV_STATUS
        updateLastDataTimestamp();
        this.cycleTime = cycleTime & 0xFFFF;
        this.i2cErrorCount = i2cErrorCount & 0xFFFF;
        this.sensorStatus.sensorAcc = ((sensorStatus & 1) == 1);
        this.sensorStatus.sensorBaro = ((sensorStatus >> 1 & 1) == 1);
        this.sensorStatus.sensorMag = ((sensorStatus >> 2 & 1) == 1);
        this.sensorStatus.sensorGps = ((sensorStatus >> 3 & 1) == 1);
        this.sensorStatus.sensorRangefinder = ((sensorStatus >> 4 & 1) == 1);
        this.sensorStatus.sensorOpFlow = ((sensorStatus >> 5 & 1) == 1);
        this.sensorStatus.sensorPitot = ((sensorStatus >> 6 & 1) == 1);
        this.sensorStatus.sensorTemp = ((sensorStatus >> 7 & 1) == 1);
        this.sensorStatus.hardwareFailure = ((sensorStatus >> 15 & 1) == 1);
        this.averageSystemLoad = averageSystemLoad;
        batteryProfile = (byte) (profiles >> 4 & 0xF);
        configProfile = (byte) (profiles & 0xF);
        this.armingFlags.armed = ((armingFlags >> 2 & 1) == 1);
        this.armingFlags.wasEverArmed = ((armingFlags >> 3 & 1) == 1);
        this.armingFlags.simulatorModeHitl = ((armingFlags >> 4 & 1) == 1);
        this.armingFlags.simulatorModeSitl = ((armingFlags >> 5 & 1) == 1);
        this.armingFlags.armingDisabledFailsafeSystem = ((armingFlags >> 7 & 1) == 1);
        this.armingFlags.armingDisabledNotLevel = ((armingFlags >> 8 & 1) == 1);
        this.armingFlags.armingDisabledSensordCalibrating = ((armingFlags >> 9 & 1) == 1);
        this.armingFlags.armingDisabledSystemOverloaded = ((armingFlags >> 10 & 1) == 1);
        this.armingFlags.armingDisabledNavigationUnsafe = ((armingFlags >> 11 & 1) == 1);
        this.armingFlags.armingDisabledCompassNotCalibrated = ((armingFlags >> 12 & 1) == 1);
        this.armingFlags.armingDisabledAccelerometerNotCalibrated = ((armingFlags >> 13 & 1) == 1);
        this.armingFlags.armingDisabledArmSwitch = ((armingFlags >> 14 & 1) == 1);
        this.armingFlags.armingDisabledHardwareFailure = ((armingFlags >> 15 & 1) == 1);
        this.armingFlags.armingDisabledBoxFailsafe = ((armingFlags >> 16 & 1) == 1);
        this.armingFlags.armingDisabledBoxKillSwitch = ((armingFlags >> 17 & 1) == 1);
        this.armingFlags.armingDisabledRcLink = ((armingFlags >> 18 & 1) == 1);
        this.armingFlags.armingDisabledThrottle = ((armingFlags >> 19 & 1) == 1);
        this.armingFlags.armingDisabledCli = ((armingFlags >> 20 & 1) == 1);
        this.armingFlags.armingDisabledCmsMenu = ((armingFlags >> 21 & 1) == 1);
        this.armingFlags.armingDisabledOsdMenu = ((armingFlags >> 22 & 1) == 1);
        this.armingFlags.armingDisabledRollPitchNotCentered = ((armingFlags >> 23 & 1) == 1);
        this.armingFlags.armingDisabledServoAutoTrim = ((armingFlags >> 24 & 1) == 1);
        this.armingFlags.armingDisabledOom = ((armingFlags >> 25 & 1) == 1);
        this.armingFlags.armingDisabledInvalidSetting = ((armingFlags >> 26 & 1) == 1);
        this.armingFlags.armingDisabledPwmOutputError = ((armingFlags >> 27 & 1) == 1);
        this.armingFlags.armingDisabledNoPrearm = ((armingFlags >> 28 & 1) == 1);
        this.armingFlags.armingDisabledDshotBeeper = ((armingFlags >> 29 & 1) == 1);
        this.armingFlags.armingDisabledLandingDetected = ((armingFlags >> 30 & 1) == 1);
        activeBoxModes = FcCommon.getActiveBoxesInav(modeFlags, boxIds);
        isArmed = isArmed();
        if (isArmed) wasArmed = true;
    }

    public void setInavAnalog(byte batteryInfo, short voltage, short amperage, int power, int mahDrawn,
                              int mwhDrawn, int batteryRemainingCapacity, byte batteryPercentage, short rssi){//MSP2_INAV_ANALOG
        updateLastDataTimestamp();
        batteryWasFull = ((batteryInfo & 1) == 1);
        batteryUsesCapacityThresholds = ((batteryInfo >> 1 & 1) == 1);
        byte state = (byte) (batteryInfo >> 2 & 3);
        if (state < FcCommon.BatteryState.values().length) batteryState = FcCommon.BatteryState.values()[state];
        batteryCellCount = (byte) (batteryInfo >> 4 & 0xF);
        this.voltage = (voltage & 0xFFFF) * 0.01f;
        this.amperage = amperage * 0.01f;
        this.power = power * 0.01f;
        this.mahDrawn = mahDrawn;
        this.mwhDrawn = mwhDrawn;
        this.batteryRemainingCapacity = batteryRemainingCapacity;
        this.batteryPercentage = batteryPercentage;
        this.rssi = Math.round(rssi / (float)FcCommon.MAX_RSSI * 100f);
        osdStats.setBatteryVoltage(this.voltage);
        osdStats.setRssi(this.rssi);
        osdStats.setCurrent(this.amperage);
        osdStats.setUsedMah(this.mahDrawn);
        calculateMahPerKm(this.amperage, this.groundSpeed);
    }

    public void setVtxConfig(byte band, byte channel, byte power, byte pitMode, short frequency, byte deviceIsReady, byte lowPowerDisarm){
        updateLastDataTimestamp();
        if (fcInfo == null) return;
        if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_INAV) {
            if (band == 1) vtxBand = "A";
            if (band == 2) vtxBand = "B";
            if (band == 3) vtxBand = "E";
            if (band == 4) vtxBand = "F";
            if (band == 5) vtxBand = "Raceband";
        }else if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) {
            if (band == 0) vtxBand = "Fatshark";
            if (band == 1) vtxBand = "Raceband";
            if (band == 2) vtxBand = "E";
            if (band == 3) vtxBand = "B";
            if (band == 4) vtxBand = "A";
        }
        vtxChannel = channel;
        vtxPower = power;
        vtxPitMode = (pitMode == 1);
        vtxFrequency = frequency;
        vtxDeviceIsReady = (deviceIsReady == 1);
        if (lowPowerDisarm >= 0 && lowPowerDisarm < FcCommon.VtxLowerPowerDisarm.values().length){
            vtxLowPowerDisarm = FcCommon.VtxLowerPowerDisarm.values()[lowPowerDisarm];
        }
    }

    public void setBatteryState(byte cellCount, short capacity, short mahDrawn, short amperage, byte batteryState, short voltage) {//MSP_BATTERY_STATE
        updateLastDataTimestamp();
        this.batteryCellCount = cellCount;
        this.capacity = capacity & 0xFFFF;
        this.mahDrawn = mahDrawn & 0xFFFF;
        this.amperage = amperage * 0.01f;
        if (batteryState >= 0 && batteryState < FcCommon.BatteryState.values().length) this.batteryState = FcCommon.BatteryState.values()[batteryState];
        this.voltage = (voltage & 0xFFFF) * 0.01f;
        osdStats.setBatteryVoltage(this.voltage);
        osdStats.setCurrent(this.amperage);
        osdStats.setUsedMah(this.mahDrawn);
        calculateMahPerKm(this.amperage, this.groundSpeed);
    }

    public void setAnalog(short mahDrawn, short rssi, short amperage, short voltage) {//MSP_ANALOG
        updateLastDataTimestamp();
        this.mahDrawn = mahDrawn & 0xFFFF;
        this.rssi = Math.round(rssi / (float) FcCommon.MAX_RSSI * 100f);
        this.amperage = amperage * 0.01f;
        if (fcInfo == null) return;
        if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT && voltage != 0) {
            this.voltage = (voltage & 0xFFFF) * 0.01f;
            osdStats.setBatteryVoltage(this.voltage);
        }
        osdStats.setRssi(this.rssi);
        osdStats.setCurrent(this.amperage);
        osdStats.setUsedMah(this.mahDrawn);
        calculateMahPerKm(this.amperage, this.groundSpeed);
    }

    public void setAttitude(short roll, short pitch, short yaw) {//MSP_ATTITUDE
        updateLastDataTimestamp();
        this.roll = roll * 0.1f;
        this.pitch = pitch * 0.1f;
        this.yaw = yaw;
    }

    public void setAltitude(int altitude, short altVelocity, int altBaro) {//MSP_ALTITUDE
        updateLastDataTimestamp();
        this.altitude = Math.round(altitude / 100f);
        this.altVelocity = altVelocity / 100f;
        this.altBaro = Math.round(altBaro / 100f);
        osdStats.setAltitude(this.altitude);
    }

    public void setRawGps(byte fixType, byte numSat, int lat, int lon, short altGps, short groundSpeed, short groundCourse, short hdop){//MSP_RAW_GPS
        updateLastDataTimestamp();
        if (fixType >= 0 && fixType < FcCommon.GpsFixTypes.values().length) this.fixType = FcCommon.GpsFixTypes.values()[fixType];
        this.numSat = numSat & 0xFF;
        float latDeg = lat / 10000000f;
        float lonDeg = lon / 10000000f;
        this.altGps = altGps;
        this.groundSpeed = groundSpeed * 0.036f;
        this.groundCourse = groundCourse * 0.1f;
        this.hdop = hdop;
        osdStats.setSpeed(this.groundSpeed);
        calculateTraveledDist(latDeg, lonDeg);
        calculateMahPerKm(this.amperage, this.groundSpeed);
    }

    public void setCompGps(short distanceToHome, short directionToHome, byte gpsHeartbeat) {//MSP_COMP_GPS
        updateLastDataTimestamp();
        this.distanceToHome = distanceToHome & 0xFFFF;
        this.directionToHome = directionToHome;
        this.gpsHeartbeat = (gpsHeartbeat > 0);
        osdStats.setHomeDistance(this.distanceToHome);
    }

    public void setArduPilotMode(int customMode, boolean isArmed){
        updateLastDataTimestamp();
        apCustomMode = customMode;
        this.isArmed = isArmed;
        if (isArmed) wasArmed = true;
    }

    public void setArduPilotSystemStatus(byte batteryCellCountDetected, int voltageBattery, short currentBattery, byte batteryRemaining){
        updateLastDataTimestamp();
        if (batteryCellCountDetected > 0) this.batteryCellCount = batteryCellCountDetected;
        if (voltageBattery != Utils.UINT16_MAX){
            this.voltage = voltageBattery / 1000f;
            osdStats.setBatteryVoltage(this.voltage);
        }
        if (currentBattery > 0) {
            this.amperage = currentBattery / 1000f;
            osdStats.setCurrent(this.amperage);
            calculateMahPerKm(this.amperage, this.groundSpeed);
        }
        if (batteryRemaining > 0) this.batteryPercentage = batteryRemaining;
    }

    public void setArduPilotStatusText(short severity, String message){
        updateLastDataTimestamp();
        if (message == null || message.isEmpty()) return;
        for (ArduPilotStatusText item : arduPilotMessages){
            if (item.message.equals(message)) return;
        }
        if (arduPilotMessages.size() > 5) arduPilotMessages.remove(0);
        arduPilotMessages.add(new ArduPilotStatusText(severity, message));
    }

    public void setArduPilotBatteryStatus(short currentBattery, int currentConsumed, byte batteryRemaining, long faultBitmask){
        updateLastDataTimestamp();
        if (currentBattery > 0) {
            this.amperage = currentBattery / 100f;
            osdStats.setCurrent(this.amperage);
        }
        if (currentConsumed > 0) {
            this.mahDrawn = currentConsumed;
            osdStats.setUsedMah(currentConsumed);
        }
        if (batteryRemaining > 0) this.batteryPercentage = batteryRemaining;
        this.apBatteryFaultBitmask = faultBitmask;
    }

    public void setArduPilotGpsRawInt(int fixType, int vel, int satellitesVisible){
        updateLastDataTimestamp();
        if (fixType >= 0 && fixType < FcCommon.GpsFixTypes.values().length) this.fixType = FcCommon.GpsFixTypes.values()[fixType];
        if (vel != Utils.UINT16_MAX) {
            this.groundSpeed = vel * 0.036f;
            calculateMahPerKm(this.amperage, this.groundSpeed);
            osdStats.setSpeed(this.groundSpeed);
        }
        if (satellitesVisible != Utils.UINT8_MAX){
            this.numSat = satellitesVisible;
        }
    }

    public void setArduPilotGlobalPositionInt(int lat, int lon, int relativeAlt, short vz) {
        updateLastDataTimestamp();
        float latDeg = lat / 10000000f;
        float lonDeg = lon / 10000000f;
        this.altitude = Math.round(relativeAlt / 1000f);
        this.altVelocity = vz / 100f;
        calculateTraveledDist(latDeg, lonDeg);
        calculateHomeDistDir();
    }

    private void calculateTraveledDist(float newLatDeg, float newLonDeg){
        if (fixType == FcCommon.GpsFixTypes.GPS_FIX_3D && groundSpeed > 0.2f && this.latDeg != 0) {
            Location oldLocation = new Location(LocationManager.GPS_PROVIDER);
            Location newLocation = new Location(LocationManager.GPS_PROVIDER);
            oldLocation.setLatitude(this.latDeg);
            oldLocation.setLongitude(this.lonDeg);
            newLocation.setLatitude(newLatDeg);
            newLocation.setLongitude(newLonDeg);
            traveledDistance += oldLocation.distanceTo(newLocation);
            osdStats.setTraveledDistance(traveledDistance);
        }
        this.latDeg = newLatDeg;
        this.lonDeg = newLonDeg;
    }

    private void calculateHomeDistDir(){
        if (fixType == FcCommon.GpsFixTypes.GPS_FIX_3D && latDeg != 0 && homeLatDeg != 0) {
            Location droneLocation = new Location(LocationManager.GPS_PROVIDER);
            Location homeLocation = new Location(LocationManager.GPS_PROVIDER);
            droneLocation.setLatitude(latDeg);
            droneLocation.setLongitude(lonDeg);
            homeLocation.setLatitude(homeLatDeg);
            homeLocation.setLongitude(homeLonDeg);
            distanceToHome = Math.round(droneLocation.distanceTo(homeLocation));
            osdStats.setHomeDistance(distanceToHome);
            directionToHome = droneLocation.bearingTo(homeLocation);
        }
    }

    public void setArduPilotHomePosition(int lat, int lon){
        updateLastDataTimestamp();
        float latDeg = lat / 10000000f;
        float lonDeg = lon / 10000000f;
        this.homeLatDeg = latDeg;
        this.homeLonDeg = lonDeg;
        calculateHomeDistDir();
    }

    public void setArduPilotSystemTime(long timeBootMs, long flightTime, long armingTime){
        updateLastDataTimestamp();
        this.onTime = Math.round(timeBootMs / 1000f);
        this.flyTime = Math.round(flightTime / 1000f);
        this.lastArmTime = Math.round(armingTime / 1000f);
        osdStats.setTimers(this.onTime, this.flyTime, this.lastArmTime);
    }

    public void setArduPilotRcChannels(int rssi){
        updateLastDataTimestamp();
        if (rssi != Utils.UINT8_MAX){
            this.rssi = Math.round(rssi / 254f * 100);
            osdStats.setRssi(this.rssi);
        }
    }

    public void setArduPilotScaledPressure(short temperature){
        updateLastDataTimestamp();
        this.baroTemperatureCelsius = Math.round(temperature / 100f);
    }

    public void setArduPilotVtxPower(short vtxPower){
        updateLastDataTimestamp();
        this.vtxPower = vtxPower;
    }

    public void setArduPilotVfrHud(int throttle){
        updateLastDataTimestamp();
        this.throttle = throttle;
    }

    private void initOsdItemsArduPilot(OsdConfig osdConfig) {
        OsdItem[] allItems = osdConfig.osdItems;
        int activeCount = 0;
        int c = 0;
        for (OsdItem item : allItems) {
            if (item != null && item.isVisible) activeCount++;
        }
        updateLastDataTimestamp();
        activeItems = new OsdItem[activeCount];
        for (OsdItem item : allItems) {
            if (item == null || !item.isVisible) continue;
            if (item.id < 0 || item.id >= OsdCommon.AP_OSD_ITEMS.length){
                activeItems[c] = item;// wrong id, use default class
                continue;
            }
            String osdItemName = OsdCommon.AP_OSD_ITEMS[item.id];
            switch (osdItemName) {
                case OsdCommon.AP_OSD_ALTITUDE:
                    activeItems[c] = new OsdItemAltitude(item);
                    break;
                case OsdCommon.AP_OSD_BAT_VOLT:
                    activeItems[c] = new OsdItemMainBattVoltageArduPilot(item);
                    break;
                case OsdCommon.AP_OSD_RSSI:
                    activeItems[c] = new OsdItemRssi(item);
                    break;
                case OsdCommon.AP_OSD_CURRENT:
                    activeItems[c] = new OsdItemCurrentDraw(item);
                    break;
                case OsdCommon.AP_OSD_BATUSED:
                    activeItems[c] = new OsdItemMahDraw(item);
                    break;
                case OsdCommon.AP_OSD_SATS:
                    activeItems[c] = new OsdItemGpsSats(item);
                    break;
                case OsdCommon.AP_OSD_FLTMODE:
                    activeItems[c] = new OsdItemFlyModeArduPilot(item);
                    break;
                case OsdCommon.AP_OSD_MESSAGE:
                    activeItems[c] = new OsdItemMessagesArduPilot(item);
                    break;
                case OsdCommon.AP_OSD_GSPEED:
                    activeItems[c] = new OsdItemGpsSpeed(item);
                    break;
                case OsdCommon.AP_OSD_HORIZON:
                    activeItems[c] = new OsdItemArtificialHorizon(item);
                    break;
                case OsdCommon.AP_OSD_HOME:
                    activeItems[c] = new OsdItemHomeDirDist(item);
                    break;
                case OsdCommon.AP_OSD_HEADING:
                    activeItems[c] = new OsdItemHeadingGrad(item);
                    break;
                case OsdCommon.AP_OSD_COMPASS:
                    activeItems[c] = new OsdItemCompassBar(item);
                    break;
                case OsdCommon.AP_OSD_GPSLAT:
                    activeItems[c] = new OsdItemGpsLat(item);
                    break;
                case OsdCommon.AP_OSD_GPSLONG:
                    activeItems[c] = new OsdItemGpsLon(item);
                    break;
                case OsdCommon.AP_OSD_TEMP:
                    activeItems[c] = new OsdItemBaroTemperature(item);
                    break;
                case OsdCommon.AP_OSD_DIST:
                    activeItems[c] = new OsdItemTripDistance(item);
                    break;
                case OsdCommon.AP_OSD_FLTIME:
                    activeItems[c] = new OsdItemFlyTime(item);
                    break;
                case OsdCommon.AP_OSD_EFF:
                    activeItems[c] = new OsdItemEfficiencyMahPerKm(item);
                    break;
                case OsdCommon.AP_OSD_SIDEBARS:
                    activeItems[c] = new OsdItemHorizonSidebars(item);
                    break;
                case OsdCommon.AP_OSD_CRSSHAIR:
                    activeItems[c] = new OsdItemCrosshairs(item);
                    break;
                case OsdCommon.AP_OSD_HOMEDIST:
                    activeItems[c] = new OsdItemHomeDist(item);
                    break;
                case OsdCommon.AP_OSD_HOMEDIR:
                    activeItems[c] = new OsdItemHomeDir(item);
                    break;
                case OsdCommon.AP_OSD_CELLVOLT:
                    activeItems[c] = new OsdItemMainBattCellVoltageArduPilot(item);
                    break;
                case OsdCommon.AP_OSD_VTX_PWR:
                    activeItems[c] = new OsdItemVtxPower(item);
                    break;
                case OsdCommon.AP_OSD_THROTTLE:
                    activeItems[c] = new OsdItemThrottle(item);
                    break;
            }
            if (activeItems[c] == null) activeItems[c] = item;// OSD item not implemented. Use parent class
            c++;
        }
    }

    private void initOsdItems(OsdConfig osdConfig) {
        if (fcInfo == null) return;
        OsdItem[] allItems = osdConfig.osdItems;
        int activeCount = 0;
        int c = 0;
        for (OsdItem item : allItems) {
            if (item != null && item.isVisible) activeCount++;
        }
        updateLastDataTimestamp();
        activeItems = new OsdItem[activeCount];
        for (OsdItem item : allItems) {
            if (item == null || !item.isVisible) continue;
            // these IDs are the same for INAV and BTFL:
            if (item.id == OsdCommon.InavOsdItems.OSD_RSSI_VALUE.ordinal()) {
                activeItems[c] = new OsdItemRssi(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_MAIN_BATT_VOLTAGE.ordinal()) {
                activeItems[c] = new OsdItemMainBattVoltage(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_CROSSHAIRS.ordinal()) {
                activeItems[c] = new OsdItemCrosshairs(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_ARTIFICIAL_HORIZON.ordinal()) {
                activeItems[c] = new OsdItemArtificialHorizon(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_ALTITUDE.ordinal()) {
                activeItems[c] = new OsdItemAltitude(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_GPS_SATS.ordinal()) {
                activeItems[c] = new OsdItemGpsSats(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_HORIZON_SIDEBARS.ordinal()) {
                activeItems[c] = new OsdItemHorizonSidebars(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_FLYMODE.ordinal()) {
                activeItems[c] = new OsdItemFlyMode(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_VTX_CHANNEL.ordinal()) {
                activeItems[c] = new OsdItemVtxChannel(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_CURRENT_DRAW.ordinal()) {
                activeItems[c] = new OsdItemCurrentDraw(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_MAH_DRAWN.ordinal()) {
                activeItems[c] = new OsdItemMahDraw(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_GPS_SPEED.ordinal()) {
                activeItems[c] = new OsdItemGpsSpeed(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_POWER.ordinal()) {
                activeItems[c] = new OsdItemPower(item);
            } else if (item.id == OsdCommon.InavOsdItems.OSD_HEADING_GRAPH.ordinal()) {
                activeItems[c] = new OsdItemCompassBar(item);
            } else {
                // FC specific IDs:
                switch (fcInfo.getFcVariant()) {
                    case FcInfo.FC_VARIANT_INAV:
                        if (item.id == OsdCommon.InavOsdItems.OSD_MAIN_BATT_CELL_VOLTAGE.ordinal()) {
                            activeItems[c] = new OsdItemMainBattCellVoltage(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_ONTIME.ordinal()) {
                            activeItems[c] = new OsdItemOnTime(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_FLYTIME.ordinal()) {
                            activeItems[c] = new OsdItemFlyTime(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_ONTIME_FLYTIME.ordinal()) {
                            activeItems[c] = new OsdItemOnTimeFlyTime(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_MESSAGES.ordinal()) {
                            activeItems[c] = new OsdItemMessages(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_GPS_LON.ordinal()) {
                            activeItems[c] = new OsdItemGpsLon(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_GPS_LAT.ordinal()) {
                            activeItems[c] = new OsdItemGpsLat(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_HOME_DIR.ordinal()) {
                            activeItems[c] = new OsdItemHomeDir(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_HOME_DIST.ordinal()) {
                            activeItems[c] = new OsdItemHomeDist(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_EFFICIENCY_MAH_PER_KM.ordinal()) {
                            activeItems[c] = new OsdItemEfficiencyMahPerKm(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_VTX_POWER.ordinal()) {
                            activeItems[c] = new OsdItemVtxPower(item);
                            break;
                        }
                        if (item.id == OsdCommon.InavOsdItems.OSD_TRIP_DIST.ordinal()) {
                            activeItems[c] = new OsdItemTripDistance(item);
                            break;
                        }
                        break;
                    case FcInfo.FC_VARIANT_BETAFLIGHT:
                        if (item.id == OsdCommon.BtflOsdItems.OSD_AVG_CELL_VOLTAGE.ordinal()) {
                            activeItems[c] = new OsdItemMainBattCellVoltage(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_GPS_LON.ordinal()) {
                            activeItems[c] = new OsdItemGpsLon(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_GPS_LAT.ordinal()) {
                            activeItems[c] = new OsdItemGpsLat(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_HOME_DIR.ordinal()) {
                            activeItems[c] = new OsdItemHomeDir(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_HOME_DIST.ordinal()) {
                            activeItems[c] = new OsdItemHomeDist(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_WARNINGS.ordinal()) {
                            activeItems[c] = new OsdItemMessages(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_EFFICIENCY.ordinal()) {
                            activeItems[c] = new OsdItemEfficiencyMahPerKm(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_ITEM_TIMER_1.ordinal()) {
                            initBfTimer(0, c, item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_ITEM_TIMER_2.ordinal()) {
                            initBfTimer(1, c, item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_CORE_TEMPERATURE.ordinal()) {
                            activeItems[c] = new OsdItemCoreTemperature(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_FLIGHT_DIST.ordinal()) {
                            activeItems[c] = new OsdItemTripDistance(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_SYS_BITRATE.ordinal()) {
                            activeItems[c] = new OsdItemSysBitRate(item);
                            break;
                        }
                        if (item.id == OsdCommon.BtflOsdItems.OSD_SYS_DELAY.ordinal()) {
                            activeItems[c] = new OsdItemSysDelay(item);
                            break;
                        }
                        break;
                }
            }
            if (activeItems[c] == null) activeItems[c] = item;// OSD item not implemented. Use parent class
            c++;
        }
    }

    private void initBfTimer(int timerId, int osdItemId, OsdItem item){
        if (osdConfig == null || item == null || activeItems == null || osdConfig.osdTimers == null) return;
        if (osdItemId < 0 || osdItemId >= activeItems.length) return;
        if (timerId < 0 || timerId >= osdConfig.osdTimers.length) return;
        switch (osdConfig.osdTimers[timerId].source){
            case OSD_TIMER_SRC_ON:
                activeItems[osdItemId] = new OsdItemOnTime(item);
                break;
            case OSD_TIMER_SRC_TOTAL_ARMED:
                activeItems[osdItemId] = new OsdItemFlyTime(item);
                break;
            case OSD_TIMER_SRC_LAST_ARMED:
                activeItems[osdItemId] = new OsdItemLastArmTime(item);
                break;
            case OSD_TIMER_SRC_ON_OR_ARMED:
                activeItems[osdItemId] = new OsdItemOnTimeFlyTime(item);
                break;
        }
    }

    public void setOsdConfigInav(byte videoSystem, byte units, byte rssiAlarm, short capacityWarning, short timeAlarm, short altAlarm, short distAlarm, short negAltAlarm, short[] osdItems){
        osdConfig = new OsdConfig(videoSystem, units, rssiAlarm, capacityWarning, timeAlarm, altAlarm, distAlarm, negAltAlarm, osdItems);
        initCanvasInav(videoSystem);
        initOsdItems(osdConfig);
    }

    public void setOsdConfigBetaflight(byte videoSystem, byte units, byte rssiAlarm, short capacityWarning, short altAlarm, short[] osdItems, byte[] osdStatItems,
                                       short[] osdTimerItems, int osdWarningsCount, int enabledWarnings, byte osdSelectedProfile, byte cameraFrameWidth, byte cameraFrameHeight){
        osdConfig = new OsdConfig(videoSystem, units, rssiAlarm, capacityWarning, altAlarm, osdItems,
                osdStatItems, osdTimerItems, osdWarningsCount, enabledWarnings, osdSelectedProfile, cameraFrameWidth, cameraFrameHeight);
        initCanvasBetaflight(videoSystem);
        initOsdItems(osdConfig);
    }

    public void setOsdConfigArduPilot(DataReader buffer){
        apOsd1Enabled = buffer.readBoolean();
        byte videoSystem = buffer.readByte();//osd1TxtRes
        byte units = buffer.readByte();
        byte msgTime = buffer.readByte();
        byte rssiAlarm = buffer.readByte();
        byte warnNumSat = buffer.readByte();
        float warnBatVolt = buffer.readFloat();
        float warnAvgCellVolt = buffer.readFloat();
        byte osdCellCount = buffer.readByte();
        byte osdItemsCount = buffer.readByte();
        OsdItem[] osdItems = new OsdItem[osdItemsCount];
        for (int i = 0; i < osdItemsCount; i++) {
            boolean isEnabled = buffer.readBoolean();
            byte x = buffer.readByte();
            byte y = buffer.readByte();
            osdItems[i] = new OsdItem(i, isEnabled, x, y);
        }
        osdConfig = new OsdConfig(videoSystem, units, msgTime, rssiAlarm, warnNumSat, warnBatVolt, warnAvgCellVolt, osdCellCount, osdItems);
        initCanvasArduPilot(videoSystem);
        initOsdItemsArduPilot(osdConfig);
    }

    private void initCanvasInav(byte videoSystem){
        switch (videoSystem){
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_AUTO:
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_BFCOMPAT:
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_PAL:
                setCanvasSize(OsdCommon.canvasSizes.PAL_COLS, OsdCommon.canvasSizes.PAL_ROWS);
                break;
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_NTSC:
                setCanvasSize(OsdCommon.canvasSizes.NTSC_COLS, OsdCommon.canvasSizes.NTSC_ROWS);
                break;
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_HDZERO:
                setCanvasSize(OsdCommon.canvasSizes.HDZERO_COLS, OsdCommon.canvasSizes.HDZERO_ROWS);
                break;
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_DJIWTF:
                setCanvasSize(OsdCommon.canvasSizes.DJI_COLS, OsdCommon.canvasSizes.DJI_ROWS);
                break;
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_BFCOMPAT_HD:
            case OsdCommon.InavVideoSystem.VIDEO_SYSTEM_AVATAR:
                setCanvasSize(OsdCommon.canvasSizes.HD_AVATAR_COLS, OsdCommon.canvasSizes.HD_AVATAR_ROWS);
                break;
        }
    }

    private void initCanvasBetaflight(byte videoSystem){
        switch (videoSystem){
            case OsdCommon.BtflVideoSystem.VIDEO_SYSTEM_AUTO:
            case OsdCommon.BtflVideoSystem.VIDEO_SYSTEM_PAL:
                setCanvasSize(OsdCommon.canvasSizes.PAL_COLS, OsdCommon.canvasSizes.PAL_ROWS);
                break;
            case OsdCommon.BtflVideoSystem.VIDEO_SYSTEM_NTSC:
                setCanvasSize(OsdCommon.canvasSizes.NTSC_COLS, OsdCommon.canvasSizes.NTSC_ROWS);
                break;
            case OsdCommon.BtflVideoSystem.VIDEO_SYSTEM_HD:
                setCanvasSize(OsdCommon.canvasSizes.HD_AVATAR_COLS, OsdCommon.canvasSizes.HD_AVATAR_ROWS);
                break;
        }
    }

    private void initCanvasArduPilot(byte videoSystem){
        switch (videoSystem){
            case 0:
                setCanvasSize(OsdCommon.canvasSizes.PAL_COLS, OsdCommon.canvasSizes.PAL_ROWS);
                break;
            case 1:
                setCanvasSize(OsdCommon.canvasSizes.HDZERO_COLS, OsdCommon.canvasSizes.HDZERO_ROWS);
                break;
            case 2:
                setCanvasSize(OsdCommon.canvasSizes.DJI_COLS, OsdCommon.canvasSizes.DJI_ROWS);
                break;
        }
    }

    public OsdConfig getOsdConfig(){
        return osdConfig;
    }

    public void setBoxIds(byte[] data){
        boxIds = FcCommon.getBoxIds(data);
    }

    public boolean isHasBoxIds(){
        return boxIds != null || (fcInfo != null && fcInfo.getFcVariant() == FcInfo.FC_VARIANT_ARDUPILOT);
    }

    public void setBoxNames(byte[] data){
        boxNames = FcCommon.getBoxNames(data);
    }

    public void setBatteryConfig(short minCellVoltage, short maxCellVoltage, short warningCellVoltage){// MSP_BATTERY_CONFIG
        this.minCellVoltage = minCellVoltage / 100f;
        this.maxCellVoltage = maxCellVoltage / 100f;
        this.warningCellVoltage = warningCellVoltage / 100f;
        hasBatteryConfig = true;
    }

    public boolean isHasBatteryConfig(){
        return hasBatteryConfig || (fcInfo != null && fcInfo.getFcVariant() != FcInfo.FC_VARIANT_BETAFLIGHT);
    }

    private class OsdStats{
        final int statsCount = 14;
        boolean[] statEnabled = new boolean[statsCount];
        private int onTime;
        private int flyTime;
        private int lastArmTime;
        private float maxSpeed;
        private int maxDistance;
        private float traveledDistance;
        private float minBatteryVoltage = -1;
        private float endBatteryVoltage;
        private int minRssi = -1;
        private float maxCurrent;
        private int usedMah;
        private int maxAltitude;
        private int maxPing;
        private float minBitRate = -1;
        private boolean isInitialized;

        public OsdStats(){

        }

        private void initBfStatTimer(int timerId){
            if (osdConfig == null || osdConfig.osdTimers == null) return;
            if (timerId < 0 || timerId >= osdConfig.osdTimers.length) return;
            switch (osdConfig.osdTimers[timerId].source){
                case OSD_TIMER_SRC_ON:
                    statEnabled[0] = true;
                    break;
                case OSD_TIMER_SRC_TOTAL_ARMED:
                    statEnabled[1] = true;
                    break;
                case OSD_TIMER_SRC_LAST_ARMED:
                    statEnabled[2] = true;
                    break;
                case OSD_TIMER_SRC_ON_OR_ARMED:
                    statEnabled[0] = true;
                    statEnabled[1] = true;
                    break;
            }
        }

        private void initialize(){
            if (osdConfig == null || osdConfig.osdStats == null){
                for (int i = 0; i < statsCount; i++) statEnabled[i] = true;
            }else{
                if (fcInfo != null && fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) {
                    for (OsdConfig.OsdStat stat : osdConfig.osdStats) {
                        if (!stat.enabled) continue;
                        if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_TIMER_1.ordinal()) {
                            initBfStatTimer(0);
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_TIMER_2.ordinal()) {
                            initBfStatTimer(1);
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MAX_SPEED.ordinal()) {
                            statEnabled[3] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MAX_DISTANCE.ordinal()) {
                            statEnabled[4] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_TOTAL_DIST.ordinal()) {
                            statEnabled[5] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MIN_BATTERY.ordinal()) {
                            statEnabled[6] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_END_BATTERY.ordinal()) {
                            statEnabled[7] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MIN_RSSI.ordinal()) {
                            statEnabled[8] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MAX_CURRENT.ordinal()) {
                            statEnabled[9] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_USED_MAH.ordinal()) {
                            statEnabled[10] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MAX_ALTITUDE.ordinal()) {
                            statEnabled[11] = true;
                        } else if (stat.id == OsdCommon.BtflOsdStats.OSD_STAT_MIN_LINK_QUALITY.ordinal()) {
                            statEnabled[12] = true;
                            statEnabled[13] = true;
                        }
                    }
                }
            }
            isInitialized = true;
        }

        public void draw(){
            if (!isInitialized){
                initialize();
                return;
            }
            GlText glText = renderer.getGlTextObject();
            if (glText == null) return;
            final int statsColls = 2;
            final float collWidth = screenWidth / (float) statsColls;
            final float startOffset = 50 * screenFactor;
            final float textSize = 10 / osdCanvasFactor;
            final float rowOffset = glText.getLineHeight(textSize) * 1.5f;
            int currentColl = 0;
            float x;
            float y = screenHeight - phoneOsdHeight - startOffset;
            String text = null;
            for (int i = 0; i < statsCount; i++) {
                if (!statEnabled[i]) continue;
                x = currentColl * collWidth + startOffset;
                switch(i){
                    case 0:
                        if (onTime == 0) break;
                        text = "On time: " + getTimeFormatted(onTime);
                        break;
                    case 1:
                        if (flyTime == 0) break;
                        text = "Fly time: " + getTimeFormatted(flyTime);
                        break;
                    case 2:
                        if (lastArmTime == 0) break;
                        text = "Last arm time: " + getTimeFormatted(lastArmTime);
                        break;
                    case 3:
                        if (maxSpeed == 0) break;
                        text = "Max speed: " + Math.round(maxSpeed) + " km/h";
                        break;
                    case 4:
                        if (maxDistance == 0) break;
                        text = "Max distance to home: " + formatDistance(maxDistance);
                        break;
                    case 5:
                        if (traveledDistance == 0) break;
                        text = "Total distance: " + formatDistance(traveledDistance);
                        break;
                    case 6:
                        if (minBatteryVoltage == -1) break;
                        text = "Min battery voltage: " + formatVoltage(minBatteryVoltage);
                        break;
                    case 7:
                        text = "End battery voltage: " + formatVoltage(endBatteryVoltage);
                        break;
                    case 8:
                        if (minRssi == -1) break;
                        text = "Min RSSI: " + minRssi + "%";
                        break;
                    case 9:
                        if (maxCurrent == 0) break;
                        text = "Max current: " + formatAmperage(maxCurrent);
                        break;
                    case 10:
                        if (usedMah == 0) break;
                        text = "Used mAh: " + usedMah + " mAh";
                        break;
                    case 11:
                        if (maxAltitude == 0) break;
                        text = "Max altitude: " + maxAltitude + " m";
                        break;
                    case 12:
                        text = "Max ping: " + maxPing + " ms";
                        break;
                    case 13:
                        if (minBitRate == -1) break;
                        text = "Min bitrate: " + formatBitRate(minBitRate);
                        break;
                }
                if (text != null){
                    glText.addText(text, x, y, textSize);
                    text = null;
                    currentColl++;
                    if (currentColl == statsColls){
                        currentColl = 0;
                        y -= rowOffset;
                    }
                }
            }
        }

        public void setTimers(int onTime, int flyTime, int lastArmTime){
            if (wasArmed && !isArmed) return;
            this.onTime = onTime;
            this.flyTime = flyTime;
            this.lastArmTime = lastArmTime;
        }

        public void setSpeed(float speed){
            if (wasArmed && !isArmed) return;
            if (speed > maxSpeed) maxSpeed = speed;
        }

        public void setHomeDistance(int distance){
            if (wasArmed && !isArmed) return;
            if (distance > maxDistance) maxDistance = distance;
        }

        public void setTraveledDistance(float distance){
            if (wasArmed && !isArmed) return;
            traveledDistance = distance;
        }

        public void setBatteryVoltage(float voltage){
            if (wasArmed && !isArmed) return;
            endBatteryVoltage = voltage;
            if (voltage < minBatteryVoltage || minBatteryVoltage == -1) minBatteryVoltage = voltage;
        }

        public void setRssi(int rssi){
            if (wasArmed && !isArmed) return;
            if (rssi < minRssi || minRssi == -1) minRssi = rssi;
        }

        public void setCurrent(float current){
            if (wasArmed && !isArmed) return;
            if (current > maxCurrent) maxCurrent = current;
        }

        public void setUsedMah(int mAh){
            if (wasArmed && !isArmed) return;
            usedMah = mAh;
        }

        public void setAltitude(int altitude){
            if (wasArmed && !isArmed) return;
            if (altitude > maxAltitude) maxAltitude = altitude;
        }

        public void setPing(int ping){
            if (wasArmed && !isArmed) return;
            if (ping > maxPing) maxPing = ping;
        }

        public void setBitRate(float bitRate){
            if (wasArmed && !isArmed || bitRate == 0) return;
            if (bitRate < minBitRate || minBitRate == -1) minBitRate = bitRate;
        }
    }

    public class OsdConfig{
        public final byte videoSystem;
        public final byte units;
        public final byte osdSelectedProfile;
        public final byte rssiAlarm;
        public final short capacityWarning;
        public final int timeAlarmSec;
        public final short altAlarm;
        public final short distAlarm;
        public final short negAltAlarm;
        public final OsdItem[] osdItems;
        public final int osdItemsCount;
        public final OsdStat[] osdStats;
        public final byte[] rawOsdStatItems;
        public final int osdStatsCount;
        public final OsdTimer[] osdTimers;
        public final short[] rawOsdTimerItems;
        public final int osdTimersCount;
        public final OsdWarning[] osdWarnings;
        public final int osdWarningsCount;
        public final int rawEnabledWarnings;
        public final int cameraFrameWidth;
        public final int cameraFrameHeight;
        public byte msgTime;
        public byte warnNumSat;
        public float warnBatVolt;
        public float warnAvgCellVolt;
        public byte osdCellCount;

        public OsdConfig(byte videoSystem, byte units, byte rssiAlarm, short capacityWarning, short timeAlarm, short altAlarm, short distAlarm, short negAltAlarm, short[] osdItems) {
            this.videoSystem = videoSystem;
            this.units = units;
            osdSelectedProfile = 1;
            this.rssiAlarm = rssiAlarm;
            this.capacityWarning = capacityWarning;
            this.timeAlarmSec = timeAlarm * 60;
            this.altAlarm = altAlarm;
            this.distAlarm = distAlarm;
            this.negAltAlarm = negAltAlarm;
            int count = osdItems.length;
            this.osdItems = new OsdItem[count];
            for (int i = 0; i < count; i++) {
                this.osdItems[i] = new OsdItem(i, osdItems[i], FcInfo.FC_VARIANT_INAV, osdSelectedProfile);
            }
            osdItemsCount = count;
            osdStats = null;
            rawOsdStatItems = null;
            osdStatsCount = 0;
            osdTimers = null;
            rawOsdTimerItems = null;
            osdTimersCount = 0;
            osdWarnings = null;
            osdWarningsCount = 0;
            rawEnabledWarnings = 0;
            cameraFrameWidth = 0;
            cameraFrameHeight = 0;
        }

        public OsdConfig(byte videoSystem, byte units, byte msgTime, byte rssiAlarm, byte warnNumSat, float warnBatVolt, float warnAvgCellVolt, byte osdCellCount, OsdItem[] osdItems){
            this.videoSystem = videoSystem;
            this.units = units;
            this.msgTime = msgTime;
            this.rssiAlarm = rssiAlarm;
            this.warnNumSat = warnNumSat;
            this.warnBatVolt = warnBatVolt;
            this.warnAvgCellVolt = warnAvgCellVolt;
            this.osdCellCount = osdCellCount;
            capacityWarning = 0;
            timeAlarmSec = 0;
            altAlarm = 0;
            distAlarm = 0;
            negAltAlarm = 0;
            osdSelectedProfile = 1;
            osdItemsCount = osdItems.length;
            this.osdItems = osdItems;
            osdStats = null;
            rawOsdStatItems = null;
            osdStatsCount = 0;
            osdTimers = null;
            rawOsdTimerItems = null;
            osdTimersCount = 0;
            osdWarnings = null;
            osdWarningsCount = 0;
            rawEnabledWarnings = 0;
            cameraFrameWidth = 0;
            cameraFrameHeight = 0;
        }

        public OsdConfig(byte videoSystem, byte units, byte rssiAlarm, short capacityWarning, short altAlarm, short[] osdItems, byte[] osdStatItems,
                         short[] osdTimerItems, int osdWarningsCount, int enabledWarnings, byte osdSelectedProfile, byte cameraFrameWidth, byte cameraFrameHeight) {
            this.videoSystem = videoSystem;
            this.units = units;
            this.osdSelectedProfile = osdSelectedProfile;
            this.rssiAlarm = rssiAlarm;
            this.capacityWarning = capacityWarning;
            this.timeAlarmSec = 0;
            this.altAlarm = altAlarm;
            this.distAlarm = 0;
            this.negAltAlarm = 0;
            int count = osdItems.length;
            this.osdItems = new OsdItem[count];
            for (int i = 0; i < count; i++) {
                this.osdItems[i] = new OsdItem(i, osdItems[i], FcInfo.FC_VARIANT_BETAFLIGHT, osdSelectedProfile);
            }
            osdItemsCount = count;
            rawOsdStatItems = osdStatItems;
            if (osdStatItems != null) {
                count = osdStatItems.length;
                this.osdStats = new OsdStat[count];
                for (int i = 0; i < count; i++) {
                    this.osdStats[i] = new OsdStat(i, osdStatItems[i] == 1);
                }
                osdStatsCount = count;
            }else{
                osdStats = null;
                osdStatsCount = 0;
            }
            rawOsdTimerItems = osdTimerItems;
            if (osdTimerItems != null) {
                count = osdTimerItems.length;
                osdTimers = new OsdTimer[count];
                for (int i = 0; i < count; i++) {
                    osdTimers[i] = new OsdTimer(osdTimerItems[i] & 0x0F, (osdTimerItems[i] >> 4) & 0x0F, (osdTimerItems[i] >> 8) & 0xFF);
                }
                osdTimersCount = count;
            }else{
                osdTimers = null;
                osdTimersCount = 0;
            }
            rawEnabledWarnings = enabledWarnings;
            if (osdWarningsCount > 0){
                osdWarnings = new OsdWarning[osdWarningsCount];
                for (int i = 0; i < osdWarningsCount; i++) {
                    osdWarnings[i] = new OsdWarning(i, (enabledWarnings >> i & 1) == 1);
                }
                this.osdWarningsCount = osdWarningsCount;
            }else{
                osdWarnings = null;
                this.osdWarningsCount = 0;
            }

            this.cameraFrameWidth = cameraFrameWidth & 0xFF;
            this.cameraFrameHeight = cameraFrameHeight & 0xFF;
        }

        public class OsdWarning{
            public final int id;
            public final boolean enabled;

            public OsdWarning(int id, boolean enabled) {
                this.id = id;
                this.enabled = enabled;
            }
        }

        public class OsdTimer{
            public final OsdCommon.BtflOsdTimerSource source;
            public final int precision;
            public final int alarm;

            public OsdTimer(int source, int precision, int alarm) {
                if (source >= 0 && source < OsdCommon.BtflOsdTimerSource.values().length){
                    this.source = OsdCommon.BtflOsdTimerSource.values()[source];
                }else{
                    this.source = OsdCommon.BtflOsdTimerSource.OSD_TIMER_SRC_COUNT;
                }
                this.precision = precision;
                this.alarm = alarm & 0xFF;
            }
        }

        public class OsdStat{
            public final int id;
            public final boolean enabled;

            public OsdStat(int id, boolean enabled) {
                this.id = id;
                this.enabled = enabled;
            }
        }
    }

    public static class ArmingFlags{
        public boolean armed;
        public boolean wasEverArmed;
        public boolean simulatorModeHitl;
        public boolean simulatorModeSitl;
        public boolean armingDisabledFailsafeSystem;
        public boolean armingDisabledNotLevel;
        public boolean armingDisabledSensordCalibrating;
        public boolean armingDisabledSystemOverloaded;
        public boolean armingDisabledNavigationUnsafe;
        public boolean armingDisabledCompassNotCalibrated;
        public boolean armingDisabledAccelerometerNotCalibrated;
        public boolean armingDisabledArmSwitch;
        public boolean armingDisabledHardwareFailure;
        public boolean armingDisabledBoxFailsafe;
        public boolean armingDisabledBoxKillSwitch;
        public boolean armingDisabledRcLink;
        public boolean armingDisabledThrottle;
        public boolean armingDisabledCli;
        public boolean armingDisabledCmsMenu;
        public boolean armingDisabledOsdMenu;
        public boolean armingDisabledRollPitchNotCentered;
        public boolean armingDisabledServoAutoTrim;
        public boolean armingDisabledOom;
        public boolean armingDisabledInvalidSetting;
        public boolean armingDisabledPwmOutputError;
        public boolean armingDisabledNoPrearm;
        public boolean armingDisabledDshotBeeper;
        public boolean armingDisabledLandingDetected;
        public boolean armingDisabledNoGyro;
        public boolean armingDisabledBadRxRecovery;
        public boolean armingDisabledRunawayTakeoff;
        public boolean armingDisabledCrashDetected;
        public boolean armingDisabledBootGraceTime;
        public boolean armingDisabledBst;
        public boolean armingDisabledMsp;
        public boolean armingDisabledParalyze;
        public boolean armingDisabledResc;
        public boolean armingDisabledRpmFilter;
        public boolean armingDisabledRebootRequired;
        public boolean armingDisabledDshotBitbang;
        public boolean armingDisabledAccCalibration;
        public boolean armingDisabledMotorProtocol;
    }

    public static class SensorStatus{
        public boolean sensorAcc;
        public boolean sensorBaro;
        public boolean sensorMag;
        public boolean sensorGps;
        public boolean sensorRangefinder;
        public boolean sensorOpFlow;
        public boolean sensorPitot;
        public boolean sensorTemp;
        public boolean sensorGyro;
        public boolean hardwareFailure;
    }
}
