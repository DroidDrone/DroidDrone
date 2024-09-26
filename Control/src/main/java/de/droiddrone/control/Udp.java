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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.Log;
import de.droiddrone.common.MediaCodecBuffer;
import de.droiddrone.common.ReceiverBuffer;
import de.droiddrone.common.SavedPacket;
import de.droiddrone.common.UdpCommon;
import de.droiddrone.common.UdpPacketData;
import de.droiddrone.common.UdpSender;

import static de.droiddrone.common.Logcat.log;

public class Udp {
    private final String destIpStr;
    private final int port;
    private final String key;
    private final boolean isViewer;
    private InetAddress destIp;
    private DatagramSocket socket;
    private DatagramPacket receiverPacket;
    private Thread receiverThread;
    private final byte[] receiverBuf = new byte[UdpCommon.packetLength];
    private final HashMap<Short, FrameFragments> receivedFrames = new HashMap<>();
    private int threadsId = 0;
    private final Decoder decoder;
    private final Osd osd;
    private FcInfo fcInfo = null;
    private int wrongFramesCount;
    private long lastFrameReceivedTs;
    private long processBitRateChangeTs;
    private long wrongFramesTs;
    private long changeBitRatePauseTs;
    private UdpSender udpSender;
    private ReceiverBuffer receiverBuffer;
    private final MainActivity activity;
    private final Config config;
    private final Rc rc;
    private boolean videoInitialFrameReceived;
    private boolean configReceived;
    private boolean versionMismatch;
    private int pingMs;
    private long lastPingTimestamp;
    private int cameraFps;

    public Udp(Config config, Decoder decoder, Osd osd, Rc rc, MainActivity activity) {
        this.config = config;
        this.destIpStr = config.getIp();
        this.port = config.getPort();
        this.key = config.getKey();
        this.isViewer = config.isViewer();
        this.decoder = decoder;
        this.osd = osd;
        this.rc = rc;
        this.activity = activity;
    }

    public boolean initialize() {
        try {
            destIp = InetAddress.getByName(destIpStr);
        } catch (UnknownHostException e) {
            log("socketInit InetAddress error: " + e);
            return false;
        }
        try {
            if (socket != null) socket.close();
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(UdpCommon.packetLength * 300);
            socket.setSendBufferSize(UdpCommon.packetLength);
            socket.setTrafficClass(0x10);
            udpSender = new UdpSender(socket);
            udpSender.connect(destIp, port);
            receiverBuffer = new ReceiverBuffer(udpSender, false, key, key);
            receiverPacket = new DatagramPacket(receiverBuf, receiverBuf.length);
            wrongFramesCount = 0;
            wrongFramesTs = System.currentTimeMillis();
            receiverThread = new Thread(receiverRun);
            receiverThread.setDaemon(false);
            receiverThread.setName("receiverThread");
            receiverThread.start();
            Thread bufferThread = new Thread(bufferRun);
            bufferThread.setDaemon(false);
            bufferThread.setName("bufferThread");
            bufferThread.start();
            Thread pingThread = new Thread(pingRun);
            pingThread.setDaemon(false);
            pingThread.setName("pingThread");
            pingThread.start();
            if (!isViewer){
                Thread rcThread = new Thread(rcRun);
                rcThread.setDaemon(false);
                rcThread.setName("rcThread");
                rcThread.start();
            }
            return true;
        } catch (Exception e) {
            log("socketInit error: " + e);
            e.printStackTrace();
            return false;
        }
    }

    public boolean isVideoInitialFrameReceived(){
        return videoInitialFrameReceived;
    }

    public boolean isConfigReceived(){
        return configReceived;
    }

    public boolean isVersionMismatch(){
        return versionMismatch;
    }

    private final Runnable receiverRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            receiverThread.setPriority(Thread.MAX_PRIORITY);
            log("Start receiver thread - OK");
            while (socket != null && !socket.isClosed() && id == threadsId) {
                try {
                    socket.receive(receiverPacket);
                    // check IP & port
                    if (!receiverPacket.getAddress().equals(destIp)
                            || receiverPacket.getPort() != port) continue;
                    receiverBuffer.addPacket(receiverPacket);
                } catch (Exception e) {
                    log("Receiver error: " + e);
                }
            }
        }
    };

    private final Runnable bufferRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            log("Start buffer thread - OK");
            while (socket != null && !socket.isClosed() && id == threadsId) {
                SavedPacket packet = null;
                try {
                    do{
                        packet = receiverBuffer.getNextPacket();
                        if (packet != null) processData(packet);
                    }while (packet != null);
                    receiverBuffer.processTimer();
                    Thread.sleep(1);
                } catch (Exception e) {
                    int packetName = -1;
                    if (packet != null) packetName = packet.packetName;
                    log("Receiver buffer error: " + e + ", packetName: " + packetName);
                }
            }
        }
    };

    private final Runnable pingRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            while (id == threadsId) {
                try {
                    if (isConnected()) {
                        if (isViewer) {
                            udpSender.sendPingForViewer();
                        } else {
                            udpSender.sendPing(true);
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    log("Ping thread error: " + e);
                }
            }
        }
    };

    private void processData(SavedPacket packet) {
        DataReader buffer = new DataReader(packet.data, true);
        byte packetName = buffer.readByte();
        if (UdpCommon.isPacketNumbered(packetName)) {
            short num = buffer.readShort();
        }
        switch (packetName) {
            case UdpCommon.VideoFrame:
            case UdpCommon.KeyFrame:
            {
                if (!decoder.isVideoDecoderStarted()) break;
                boolean isKeyFrame = (packetName == UdpCommon.KeyFrame);
                short frameNum = buffer.readShort();
                int offset = buffer.readInt();
                int frameSize = 0;
                if (offset == 0) frameSize = buffer.readInt();
                int dataSize = buffer.getRemaining();
                byte[] buf = new byte[dataSize];
                int read = buffer.read(buf, 0, dataSize);
                if (read == dataSize) {
                    if (dataSize == frameSize) {
                        processBitRateChange();
                        if (isKeyFrame){
                            decoder.videoInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_KEY_FRAME, buf));
                        }else{
                            decoder.videoInputBuffer.offer(new MediaCodecBuffer(buf));
                        }
                        break;
                    }
                    if (frameSize > 0 && !receivedFrames.containsKey(frameNum))
                        receivedFrames.put(frameNum, new FrameFragments(frameSize, isKeyFrame));
                    FrameFragments frame = receivedFrames.get(frameNum);
                    if (frame == null) break;
                    frame.putFragment(offset, buf);
                    if (frame.isCompleted || frame.isStartReceived && frame.isEndReceived) {
                        if (!frame.isCompleted) {
                            if (isKeyFrame){
                                wrongFramesCount += 5;
                            }else{
                                wrongFramesCount++;
                            }
                        }
                        byte[] frameData = frame.getFrame();
                        if (frameData != null && frameData.length > 0) {
                            processBitRateChange();
                            if (frame.isKeyFrame){
                                decoder.videoInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_KEY_FRAME, frameData));
                            }else{
                                decoder.videoInputBuffer.offer(new MediaCodecBuffer(frameData));
                            }
                        } else {
                            if (isKeyFrame){
                                wrongFramesCount += 5;
                            }else{
                                wrongFramesCount++;
                            }
                        }
                        if (receivedFrames.size() == 1) {
                            receivedFrames.remove(frameNum);
                        } else {
                            ArrayList<Short> keysToRemove = new ArrayList<>();
                            for (HashMap.Entry<Short, FrameFragments> entry : receivedFrames.entrySet()) {
                                short key = entry.getKey();
                                frame = entry.getValue();
                                if ((key <= frameNum || key > frameNum + 1000) && (isKeyFrame || !frame.isKeyFrame))
                                    keysToRemove.add(key);
                            }
                            for (short key : keysToRemove) {
                                receivedFrames.remove(key);
                                if (key != frameNum) wrongFramesCount++;
                            }
                        }
                    }
                } else {
                    if (isKeyFrame){
                        wrongFramesCount += 5;
                    }else{
                        wrongFramesCount++;
                    }
                }
                break;
            }
            case UdpCommon.VideoInitialFrame: {
                short width = buffer.readShort();
                short height = buffer.readShort();
                boolean isHevc = buffer.readBoolean();
                boolean isFrontCamera = buffer.readBoolean();
                int dataSize = buffer.getRemaining();
                byte[] buf = new byte[dataSize];
                int read = buffer.read(buf, 0, dataSize);
                if (read == dataSize) {
                    if (videoInitialFrameReceived && decoder.isVideoDecoderStarted() || decoder.videoDecoderInitializationRunning) break;
                    try {
                        decoder.videoDecoderInitializationRunning = true;
                        videoInitialFrameReceived = true;
                        activity.showGlFragment(true);
                        lastFrameReceivedTs = System.currentTimeMillis() + 500;
                        decoder.videoInputBuffer.clear();
                        decoder.videoInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_CODEC_CONFIG, buf));
                        Thread t1 = new Thread(() -> decoder.initializeVideo(isHevc, width, height, isFrontCamera));
                        t1.start();
                    }catch (Exception e){
                        decoder.videoDecoderInitializationRunning = false;
                    }
                }
                break;
            }
            case UdpCommon.AudioInitialFrame:{
                int sampleRate = buffer.readInt();
                byte channelCount = buffer.readByte();
                byte encoding = buffer.readByte();
                int dataSize = buffer.getRemaining();
                byte[] buf = new byte[dataSize];
                int read = buffer.read(buf, 0, dataSize);
                if (read == dataSize) {
                    if (decoder.isAudioDecoderStarted()) break;
                    decoder.audioInputBuffer.clear();
                    decoder.audioInputBuffer.offer(new MediaCodecBuffer(Decoder.BUFFER_FLAG_CODEC_CONFIG, buf));
                    decoder.initializeAudio(sampleRate, channelCount, encoding);
                }
                break;
            }
            case UdpCommon.AudioFrame:{
                if (!decoder.isAudioDecoderStarted()) break;
                int dataSize = buffer.getRemaining();
                byte[] buf = new byte[dataSize];
                buffer.read(buf, 0, dataSize);
                decoder.audioInputBuffer.offer(new MediaCodecBuffer(buf));
                break;
            }
            case UdpCommon.FcInfo:{
                byte fcVariant = buffer.readByte();
                byte fcVersionMajor = buffer.readByte();
                byte fcVersionMinor = buffer.readByte();
                byte fcVersionPatchLevel = buffer.readByte();
                byte apiProtocolVersion = buffer.readByte();
                byte apiVersionMajor = buffer.readByte();
                byte apiVersionMinor = buffer.readByte();
                byte platformType = buffer.readByte();
                fcInfo = new FcInfo(fcVariant, fcVersionMajor, fcVersionMinor, fcVersionPatchLevel, apiProtocolVersion, apiVersionMajor, apiVersionMinor, platformType);
                osd.initialize(fcInfo);
                break;
            }
            case UdpCommon.TelemetryData:{
                processTelemetryData(buffer);
                break;
            }
            case UdpCommon.Pong:
            {
                boolean toEndPoint = buffer.readBoolean();
                long time = buffer.readLong();
                if (toEndPoint){
                    int ping = (int) (System.currentTimeMillis() - time);
                    setPing(ping);
                    osd.setPing(ping);
                }
                break;
            }
            case UdpCommon.ConfigReceived:
            {
                configReceived = true;
                break;
            }
            case UdpCommon.VersionMismatch:
            {
                configReceived = false;
                versionMismatch = true;
                break;
            }
        }
    }

    private void processBitRateChange() {
        if (config.isViewer()) return;
        long current = System.currentTimeMillis();
        if (current - changeBitRatePauseTs > 2000) {
            if (wrongFramesCount > Math.round(getCameraFps() / 30f)
                    || current > lastFrameReceivedTs + 150 || getPing() == -1 || getPing() > 300) {
                wrongFramesTs = current;
                wrongFramesCount = 0;
                changeBitRatePauseTs = current;
                sendChangeBitRate(false);
            }
            if (current - wrongFramesTs >= 10000) {
                wrongFramesTs = current;
                sendChangeBitRate(true);
            }
        }
        lastFrameReceivedTs = current;
        if (current - processBitRateChangeTs >= 1000) {
            wrongFramesCount = 0;
            processBitRateChangeTs = current;
        }
    }

    private void setPing(int pingMs){
        this.pingMs = pingMs;
        lastPingTimestamp = System.currentTimeMillis();
    }

    public int getPing(){
        if (System.currentTimeMillis() - lastPingTimestamp > 2000){
            return -1;
        }else{
            return pingMs;
        }
    }

    private void processTelemetryData(DataReader buffer){
        while (buffer.getRemaining() > 0){
            short code = buffer.readShort();
            if (fcInfo == null && code < FcCommon.DD_TIMERS) return;
            switch (code){
                case FcCommon.DD_AP_ATTITUDE:
                case FcCommon.MSP_ATTITUDE: {
                    short roll = buffer.readShort();
                    short pitch = buffer.readShort();
                    short yaw = buffer.readShort();
                    osd.setAttitude(roll, pitch, yaw);
                    break;
                }
                case FcCommon.MSP_ALTITUDE: {
                    int altitude = buffer.readInt();
                    short altVelocity = buffer.readShort();
                    int altBaro = 0;
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_INAV) altBaro = buffer.readInt();
                    osd.setAltitude(altitude, altVelocity, altBaro);
                    break;
                }
                case FcCommon.MSP_ANALOG: {
                    short mahDrawn = buffer.readShort();
                    short rssi = buffer.readShort();
                    short amperage = buffer.readShort();
                    short voltage = 0;
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) {
                        voltage = buffer.readShort();
                    }
                    osd.setAnalog(mahDrawn, rssi, amperage, voltage);
                    break;
                }
                case FcCommon.MSP_BOXIDS: {
                    short size = buffer.readShort();
                    byte[] data = new byte[size];
                    buffer.read(data, 0, size);
                    osd.setBoxIds(data);
                    break;
                }
                case FcCommon.MSP_BOXNAMES: {
                    short size = buffer.readShort();
                    byte[] data = new byte[size];
                    buffer.read(data, 0, size);
                    osd.setBoxNames(data);
                    break;
                }
                case FcCommon.MSP_BATTERY_CONFIG: {
                    short minCellVoltage = buffer.readShort();
                    short maxCellVoltage = buffer.readShort();
                    short warningCellVoltage = buffer.readShort();
                    osd.setBatteryConfig(minCellVoltage, maxCellVoltage, warningCellVoltage);
                    break;
                }
                case FcCommon.MSP_OSD_CONFIG: {
                    int versionCode = buffer.readShort();
                    if (versionCode != MainActivity.versionCode){
                        versionMismatch = true;
                        break;
                    }
                    switch (fcInfo.getFcVariant()){
                        case FcInfo.FC_VARIANT_INAV:{
                            byte driver = buffer.readByte();
                            if (driver == 0) break;
                            byte videoSystem = buffer.readByte();
                            byte units = buffer.readByte();
                            byte rssiAlarm = buffer.readByte();
                            short capacityWarning = buffer.readShort();
                            short timeAlarm = buffer.readShort();
                            short altAlarm = buffer.readShort();
                            short distAlarm = buffer.readShort();
                            short negAltAlarm = buffer.readShort();
                            int osdItemCount = buffer.readShort();
                            if (osdItemCount == 0) break;
                            short[] osdItems = new short[osdItemCount];
                            for (int i = 0; i < osdItemCount; i++) {
                                osdItems[i] = buffer.readShort();
                            }
                            osd.setOsdConfigInav(videoSystem, units, rssiAlarm, capacityWarning, timeAlarm, altAlarm, distAlarm, negAltAlarm, osdItems);
                            break;
                        }
                        case FcInfo.FC_VARIANT_BETAFLIGHT:{
                            byte videoSystem = buffer.readByte();
                            byte units = buffer.readByte();
                            byte rssiAlarm = buffer.readByte();
                            short capacityWarning = buffer.readShort();
                            short altAlarm = buffer.readShort();
                            short osdItemCount = buffer.readShort();
                            if (osdItemCount <= 0) break;
                            short[] osdItems = new short[osdItemCount];
                            for (int i = 0; i < osdItemCount; i++) {
                                osdItems[i] = buffer.readShort();
                            }
                            int osdStatCount = buffer.readUnsignedByteAsInt();
                            byte[] osdStatItems = null;
                            if (osdStatCount > 0){
                                osdStatItems = new byte[osdStatCount];
                                for (int i = 0; i < osdStatCount; i++) {
                                    osdStatItems[i] = buffer.readByte();
                                }
                            }
                            int osdTimerCount = buffer.readUnsignedByteAsInt();
                            short[] osdTimerItems = null;
                            if (osdTimerCount > 0){
                                osdTimerItems = new short[osdTimerCount];
                                for (int i = 0; i < osdTimerCount; i++) {
                                    osdTimerItems[i] = buffer.readShort();
                                }
                            }
                            int osdWarningsCount = buffer.readUnsignedByteAsInt();
                            int enabledWarnings = buffer.readInt();
                            byte osdSelectedProfile = buffer.readByte();
                            byte cameraFrameWidth = buffer.readByte();
                            byte cameraFrameHeight = buffer.readByte();
                            osd.setOsdConfigBetaflight(videoSystem, units, rssiAlarm, capacityWarning, altAlarm, osdItems,
                                    osdStatItems, osdTimerItems, osdWarningsCount, enabledWarnings, osdSelectedProfile, cameraFrameWidth, cameraFrameHeight);
                            break;
                        }
                    }
                    break;
                }
                case FcCommon.MSP_VTX_CONFIG: {
                    int vtxDevice = buffer.readShort();
                    if (vtxDevice == FcCommon.VTXDEV_UNKNOWN) break;
                    byte band = buffer.readByte();
                    byte channel = buffer.readByte();
                    byte power = buffer.readByte();
                    byte pitMode = buffer.readByte();
                    short frequency = 0;
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) frequency = buffer.readShort();
                    byte deviceIsReady = buffer.readByte();
                    byte lowPowerDisarm = buffer.readByte();
                    osd.setVtxConfig(band, channel, power, pitMode, frequency, deviceIsReady, lowPowerDisarm);
                    break;
                }
                case FcCommon.MSP_BATTERY_STATE: {
                    byte cellCount = buffer.readByte();
                    short capacity = buffer.readShort();
                    short mahDrawn = buffer.readShort();
                    short amperage = buffer.readShort();
                    byte batteryState = buffer.readByte();
                    short voltage = buffer.readShort();
                    osd.setBatteryState(cellCount, capacity, mahDrawn, amperage, batteryState, voltage);
                    break;
                }
                case FcCommon.MSP_STATUS: {
                    short cycleTime = buffer.readShort();
                    short i2cErrorCount = buffer.readShort();
                    short sensorStatus = buffer.readShort();
                    switch (fcInfo.getFcVariant()){
                        case FcInfo.FC_VARIANT_INAV:{
                            byte configProfile = buffer.readByte();
                            osd.setStatusInav(cycleTime, i2cErrorCount, sensorStatus, configProfile);
                            break;
                        }
                        case FcInfo.FC_VARIANT_BETAFLIGHT:{
                            byte currentPidProfileIndex = buffer.readByte();
                            short averageSystemLoad = buffer.readShort();
                            int modeFlagsSize = buffer.readUnsignedByteAsInt();
                            byte[] modeFlags = null;
                            if (modeFlagsSize > 0) {
                                modeFlags = new byte[modeFlagsSize];
                                for (int i = 0; i < modeFlagsSize; i++) {
                                    modeFlags[i] = buffer.readByte();
                                }
                            }
                            int armingFlags = buffer.readInt();
                            byte configStateFlags = buffer.readByte();
                            short coreTemperatureCelsius = buffer.readShort();
                            osd.setStatusBetaflight(cycleTime, i2cErrorCount, sensorStatus, currentPidProfileIndex, averageSystemLoad, modeFlags, armingFlags, configStateFlags, coreTemperatureCelsius);
                            break;
                        }
                    }
                    break;
                }
                case FcCommon.MSP_RAW_GPS: {
                    byte fixType = buffer.readByte();
                    byte numSat = buffer.readByte();
                    int lat = buffer.readInt();
                    int lon = buffer.readInt();
                    short altGps = buffer.readShort();
                    short groundSpeed = buffer.readShort();
                    short groundCourse = buffer.readShort();
                    short hdop = buffer.readShort();
                    int traveledDistance = buffer.readInt();
                    osd.setRawGps(fixType, numSat, lat, lon, altGps, groundSpeed, groundCourse, hdop, traveledDistance);
                    break;
                }
                case FcCommon.MSP_COMP_GPS: {
                    short distanceToHome = buffer.readShort();
                    short directionToHome = buffer.readShort();
                    byte gpsHeartbeat = buffer.readByte();
                    osd.setCompGps(distanceToHome, directionToHome, gpsHeartbeat);
                    break;
                }
                case FcCommon.MSP_OSD_CANVAS: {
                    int cols = buffer.readUnsignedByteAsInt();
                    int rows = buffer.readUnsignedByteAsInt();
                    osd.setCanvasSize(cols, rows);
                    break;
                }
                case FcCommon.MSP2_INAV_STATUS: {
                    short cycleTime = buffer.readShort();
                    short i2cErrorCount = buffer.readShort();
                    short sensorStatus = buffer.readShort();
                    short averageSystemLoad = buffer.readShort();
                    byte profiles = buffer.readByte();
                    int armingFlags = buffer.readInt();
                    int[] modeFlags = null;
                    int modeFlagsSize = buffer.readUnsignedByteAsInt();
                    if (modeFlagsSize > 0) {
                        modeFlags = new int[modeFlagsSize];
                        for (int i = 0; i < modeFlagsSize; i++) {
                            modeFlags[i] = buffer.readInt();
                        }
                    }
                    osd.setInavStatus(cycleTime, i2cErrorCount, sensorStatus, averageSystemLoad, profiles, armingFlags, modeFlags);
                    break;
                }
                case FcCommon.MSP2_INAV_ANALOG: {
                    byte batteryInfo = buffer.readByte();
                    short voltage = buffer.readShort();
                    short amperage = buffer.readShort();
                    int power = buffer.readInt();
                    int mahDrawn = buffer.readInt();
                    int mwhDrawn = buffer.readInt();
                    int batteryRemainingCapacity = buffer.readInt();
                    byte batteryPercentage = buffer.readByte();
                    short rssi = buffer.readShort();
                    osd.setInavAnalog(batteryInfo, voltage, amperage, power, mahDrawn, mwhDrawn, batteryRemainingCapacity, batteryPercentage, rssi);
                    break;
                }
                case FcCommon.DD_TIMERS: {
                    int onTime = buffer.readInt();
                    int flyTime = buffer.readInt();
                    int lastArmTime = buffer.readInt();
                    osd.setTimers(onTime, flyTime, lastArmTime);
                    break;
                }
                case FcCommon.DD_PHONE_BATTERY_STATE: {
                    byte batteryLevel = buffer.readByte();
                    boolean isCharging = buffer.readBoolean();
                    osd.setDronePhoneBatteryState(batteryLevel, isCharging);
                    break;
                }
                case FcCommon.DD_CAMERA_FPS: {
                    short cameraFps = buffer.readShort();
                    this.cameraFps = cameraFps;
                    osd.setCameraFps(cameraFps);
                    break;
                }
                case FcCommon.DD_VIDEO_BIT_RATE: {
                    float videoBitRate = buffer.readFloat();
                    osd.setVideoBitRate(videoBitRate);
                    break;
                }
                case FcCommon.DD_VIDEO_RECORDER_STATE: {
                    boolean isRecording = buffer.readBoolean();
                    int recordingTimeSec = buffer.readInt();
                    osd.setVideoRecorderState(isRecording, recordingTimeSec);
                    break;
                }
                case FcCommon.DD_NETWORK_STATE: {
                    int networkType = buffer.readUnsignedByteAsInt();
                    int rssi = buffer.readUnsignedByteAsInt();
                    osd.setDroneNetworkState(networkType, rssi);
                    break;
                }
                case FcCommon.DD_AP_OSD_CONFIG: {
                    osd.setOsdConfigArduPilot(buffer);
                    break;
                }
                case FcCommon.DD_AP_MODE: {
                    int customMode = buffer.readUnsignedByteAsInt();
                    boolean isArmed = buffer.readBoolean();
                    osd.setArduPilotMode(customMode, isArmed);
                    break;
                }
                case FcCommon.DD_AP_BATTERY_STATUS: {
                    short currentBattery = buffer.readShort();
                    int currentConsumed = buffer.readInt();
                    byte batteryRemaining = buffer.readByte();
                    long faultBitmask = buffer.readUnsignedIntAsLong();
                    osd.setArduPilotBatteryStatus(currentBattery, currentConsumed, batteryRemaining, faultBitmask);
                    break;
                }
                case FcCommon.DD_AP_SYS_STATUS: {
                    byte batteryCellCountDetected = buffer.readByte();
                    int voltageBattery = buffer.readUnsignedShortAsInt();
                    byte batteryRemaining = buffer.readByte();
                    osd.setArduPilotSystemStatus(batteryCellCountDetected, voltageBattery, batteryRemaining);
                    break;
                }
                case FcCommon.DD_AP_STATUS_TEXT: {
                    short severity = (short)buffer.readUnsignedByteAsInt();
                    String message = buffer.readUTF();
                    osd.setArduPilotStatusText(severity, message);
                    break;
                }
                case FcCommon.DD_AP_GPS_RAW_INT: {
                    int fixType = buffer.readUnsignedByteAsInt();
                    int vel = buffer.readUnsignedShortAsInt();
                    int satellitesVisible = buffer.readUnsignedByteAsInt();
                    osd.setArduPilotGpsRawInt(fixType, vel, satellitesVisible);
                    break;
                }
                case FcCommon.DD_AP_GLOBAL_POSITION_INT: {
                    int lat = buffer.readInt();
                    int lon = buffer.readInt();
                    int relativeAlt = buffer.readInt();
                    short vz = buffer.readShort();
                    int traveledDistance = buffer.readInt();
                    int distanceToHome = buffer.readInt();
                    short directionToHome = buffer.readShort();
                    osd.setArduPilotGlobalPositionInt(lat, lon, relativeAlt, vz, traveledDistance, distanceToHome, directionToHome);
                    break;
                }
                case FcCommon.DD_AP_HOME_POSITION: {
                    int lat = buffer.readInt();
                    int lon = buffer.readInt();
                    osd.setArduPilotHomePosition(lat, lon);
                    break;
                }
                case FcCommon.DD_AP_SYSTEM_TIME: {
                    long timeBootMs = buffer.readUnsignedIntAsLong();
                    long flightTime = buffer.readUnsignedIntAsLong();
                    long armingTime = buffer.readUnsignedIntAsLong();
                    osd.setArduPilotSystemTime(timeBootMs, flightTime, armingTime);
                    break;
                }
                case FcCommon.DD_AP_RC_CHANNELS: {
                    int rssi = buffer.readUnsignedByteAsInt();
                    osd.setArduPilotRcChannels(rssi);
                    break;
                }
                case FcCommon.DD_AP_SCALED_PRESSURE: {
                    short temperature = buffer.readShort();
                    osd.setArduPilotScaledPressure(temperature);
                    break;
                }
                case FcCommon.DD_AP_VTX_POWER: {
                    short vtxPower = buffer.readShort();
                    osd.setArduPilotVtxPower(vtxPower);
                    break;
                }
                case FcCommon.DD_AP_VFR_HUD: {
                    int throttle = buffer.readUnsignedByteAsInt();
                    osd.setArduPilotVfrHud(throttle);
                    break;
                }
                default:
                    return;
            }
        }
    }

    public void sendConnect(){
        if (isViewer){
            udpSender.sendConnect(2, key, MainActivity.versionCode);
        }else{
            udpSender.sendConnect(1, key, MainActivity.versionCode);
        }
    }

    public void disconnect(){
        Thread th = new Thread(() -> {
            try {
                UdpPacketData packetData = new UdpPacketData(UdpCommon.Disconnect);
                udpSender.sendPacket(packetData.getData());
            } catch (Exception e) {
                e.printStackTrace();
                Log.log("sendDisconnect error: " + e);
            }
            close();
        });
        th.start();
    }

    public boolean isConnected(){
        return (receiverBuffer != null && receiverBuffer.isConnected());
    }

    public void sendChangeBitRate(boolean increase){
        if (isViewer) return;
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.ChangeBitRate);
            packetData.daos.writeBoolean(increase);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendChangeBitRate error: " + e);
        }
    }

    private void sendRcFrame(short[] rcChannels) {
        try {
            if (rcChannels == null || isViewer) return;
            int channelsCount = rcChannels.length;
            UdpPacketData packetData = new UdpPacketData(UdpCommon.RcFrame);
            packetData.daos.writeByte(channelsCount);
            for (short rcChannel : rcChannels) {
                packetData.daos.writeShort(rcChannel);
            }
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendRcFrame error: " + e);
        }
    }

    private final Runnable rcRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            while (id == threadsId) {
                try {
                    if (isConnected() && configReceived){
                        sendRcFrame(rc.getRcChannels());
                    }
                    Thread.sleep(1000 / config.getRcRefreshRate());
                } catch (Exception e) {
                    log("RC thread error: " + e);
                }
            }
        }
    };

    private void sendStartVideoStream() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.StartVideo);
            packetData.daos.writeBoolean(decoder.isHevcSupported());
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendStartVideoStream error: " + e);
        }
    }

    private void sendGetVideoConfig() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.GetVideoConfig);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetVideoConfig error: " + e);
        }
    }

    public void sendGetFcInfo() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.FcInfo);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetFcInfo error: " + e);
        }
    }

    public void sendGetOsdConfig() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.OsdConfig);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetOsdConfig error: " + e);
        }
    }

    public void sendGetBatteryConfig() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.BatteryConfig);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetBatteryConfig error: " + e);
        }
    }

    public void sendGetBoxIds() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.BoxIds);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetBoxIds error: " + e);
        }
    }

    public void sendGetBoxNames() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.BoxNames);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendGetBoxNames error: " + e);
        }
    }

    public void startVideoStream(){
        Thread t1 = new Thread(() -> {
            if (isViewer){
                sendGetVideoConfig();
            }else{
                sendStartVideoStream();
            }
        });
        t1.start();
    }

    public void sendStartStopRecording(boolean start){
        if (isViewer) return;
        Thread t1 = new Thread(() -> {
            try {
                UdpPacketData packetData = new UdpPacketData(UdpCommon.StartStopRecording);
                packetData.daos.writeBoolean(start);
                udpSender.sendPacket(packetData.getData());
            } catch (Exception e) {
                e.printStackTrace();
                log("sendStartStopRecording error: " + e);
            }
        });
        t1.start();
    }

    public int getCameraFps(){
        return cameraFps;
    }

    public void sendConfig() {
        if (isViewer) return;
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.Config);
            packetData.daos.writeShort(config.getVersionCode());
            packetData.daos.writeUTF(config.getCameraId());
            packetData.daos.writeShort(config.getCameraResolutionWidth());
            packetData.daos.writeShort(config.getCameraResolutionHeight());
            packetData.daos.writeShort(config.getCameraFpsMin());
            packetData.daos.writeShort(config.getCameraFpsMax());
            packetData.daos.writeByte(config.getBitrateLimit() / 1000000);
            packetData.daos.writeBoolean(config.isUseExtraEncoder());
            packetData.daos.writeByte(config.getVideoRecorderCodec());
            packetData.daos.writeByte(config.getRecordedVideoBitrate() / 1000000);
            packetData.daos.writeBoolean(config.isSendAudioStream());
            packetData.daos.writeShort(config.getAudioStreamBitrate() / 1000);
            packetData.daos.writeBoolean(config.isRecordAudio());
            packetData.daos.writeShort(config.getRecordedAudioBitrate() / 1000);
            packetData.daos.writeByte(config.getTelemetryRefreshRate());
            packetData.daos.writeByte(config.getRcRefreshRate());
            packetData.daos.writeInt(config.getSerialBaudRate());
            packetData.daos.writeByte(config.getUsbSerialPortIndex());
            packetData.daos.writeBoolean(config.isUseNativeSerialPort());
            packetData.daos.writeUTF(config.getNativeSerialPort());
            packetData.daos.writeByte(config.getFcProtocol());
            packetData.daos.writeBoolean(config.isUseUsbCamera());
            packetData.daos.writeByte(config.getUsbCameraFrameFormat());
            packetData.daos.writeBoolean(config.isUsbCameraReset());
            packetData.daos.writeByte(config.getMavlinkTargetSysId());
            packetData.daos.writeByte(config.getMavlinkGcsSysId());
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendConfig error: " + e);
        }
    }

    public void close() {
        threadsId++;
        if (receiverBuffer != null){
            receiverBuffer.close();
            receiverBuffer = null;
        }
        if (udpSender != null) {
            udpSender.close();
            udpSender = null;
        }
        if (socket != null) socket.close();
        receivedFrames.clear();
        videoInitialFrameReceived = false;
        configReceived = false;
        versionMismatch = false;
    }
}
