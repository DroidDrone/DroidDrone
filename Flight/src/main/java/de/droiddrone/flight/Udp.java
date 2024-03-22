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

import android.media.MediaCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.Log;
import de.droiddrone.common.MediaCodecBuffer;
import de.droiddrone.common.MediaCommon;
import de.droiddrone.common.ReceiverBuffer;
import de.droiddrone.common.SavedPacket;
import de.droiddrone.common.TelemetryData;
import de.droiddrone.common.UdpCommon;
import de.droiddrone.common.UdpPacketData;
import de.droiddrone.common.UdpSender;

import static de.droiddrone.common.Logcat.log;

public class Udp {
    private final String destIpStr;
    private final int port;
    private final String key;
    private final int connectionMode;
    private final byte[] receiverBuf = new byte[UdpCommon.packetLength];
    private final StreamEncoder streamEncoder;
    private final Mp4Recorder mp4Recorder;
    private final Camera camera;
    private final Msp msp;
    private final PhoneTelemetry phoneTelemetry;
    private final Config config;
    private DatagramSocket socket;
    private DatagramPacket receiverPacket;
    private int videoFrameNum = 0;
    private Thread receiverThread;
    private byte[] videoInitialFrame = null;
    private byte[] audioInitialFrame = null;
    private int videoSenderThreadId, audioSenderThreadId, udpThreadsId;
    private UdpSender udpSender;
    private ReceiverBuffer receiverBuffer;
    private boolean isVideoStarting, isAudioStarting;

    public Udp(String destIpStr, int port, String key, int connectionMode, StreamEncoder streamEncoder,
               Mp4Recorder mp4Recorder, Camera camera, Msp msp, PhoneTelemetry phoneTelemetry, Config config){
        this.destIpStr = destIpStr;
        this.port = port;
        this.key = key;
        this.connectionMode = connectionMode;
        this.streamEncoder = streamEncoder;
        this.mp4Recorder = mp4Recorder;
        this.camera = camera;
        this.msp = msp;
        this.phoneTelemetry = phoneTelemetry;
        this.config = config;
        videoSenderThreadId = 0;
        audioSenderThreadId = 0;
        udpThreadsId = 0;
        isVideoStarting = false;
        isAudioStarting = false;
    }

    public boolean initialize(){
        InetAddress destIp;
        try {
            destIp = InetAddress.getByName(destIpStr);
        } catch (UnknownHostException e) {
            log("socketInit InetAddress error: " + e);
            return false;
        }
        try {
            if (socket != null) socket.close();
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(UdpCommon.packetLength * 15);
            socket.setSendBufferSize(UdpCommon.packetLength * 150);
            udpSender = new UdpSender(socket);
            if (connectionMode == 0) udpSender.connect(destIp, port);
            receiverBuffer = new ReceiverBuffer(udpSender, (connectionMode != 0), key, null);
            receiverPacket = new DatagramPacket(receiverBuf, receiverBuf.length);
            receiverThread = new Thread(receiverRun);
            receiverThread.setDaemon(false);
            receiverThread.setName("receiverThread");
            receiverThread.start();
            Thread bufferThread = new Thread(bufferRun);
            bufferThread.setDaemon(false);
            bufferThread.setName("bufferThread");
            bufferThread.start();
            Thread telemetrySenderThread = new Thread(telemetrySenderRun);
            telemetrySenderThread.setDaemon(false);
            telemetrySenderThread.setName("telemetrySenderThread");
            telemetrySenderThread.start();
            return true;
        } catch (Exception e) {
            log("socketInit error: " + e);
            e.printStackTrace();
            return false;
        }
    }

    public void sendConnect(){
        udpSender.sendConnect(0, key, MainActivity.versionCode);
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

    private final Runnable receiverRun = new Runnable() {
        public void run() {
            final int id = udpThreadsId;
            receiverThread.setPriority(Thread.MAX_PRIORITY);
            log("Start receiver thread - OK");
            while (socket != null && !socket.isClosed() && id == udpThreadsId) {
                try {
                    socket.receive(receiverPacket);
                    receiverBuffer.addPacket(receiverPacket);
                } catch (Exception e) {
                    log("Receiver error: " + e);
                }
            }
        }
    };

    private final Runnable telemetrySenderRun = new Runnable() {
        public void run() {
            final int id = udpThreadsId;
            log("Start telemetry sender thread - OK");
            while (id == udpThreadsId) {
                try {
                    sendTelemetryData();
                    Thread.sleep(10);
                } catch (Exception e) {
                    log("Telemetry thread error: " + e);
                }
            }
        }
    };

    private final Runnable bufferRun = new Runnable() {
        public void run() {
            final int id = udpThreadsId;
            log("Start buffer thread - OK");
            while (socket != null && !socket.isClosed() && id == udpThreadsId) {
                try {
                    SavedPacket packet;
                    do{
                        packet = receiverBuffer.getNextPacket();
                        if (packet != null) processData(packet);
                    }while (packet != null);
                    receiverBuffer.processTimer();
                    Thread.sleep(1);
                } catch (Exception e) {
                    log("Receiver buffer error: " + e);
                }
            }
        }
    };

    private void startVideoStream(boolean isHevc) {
        if (isVideoStarting) return;
        isVideoStarting = true;
        videoSenderThreadId++;
        if (!camera.initialize()) {
            log("Camera initialize error.");
            isVideoStarting = false;
            return;
        }
        streamEncoder.setDefaultHevc(isHevc);
        mp4Recorder.close();
        videoInitialFrame = null;
        videoFrameNum = 0;
        if (camera.isOpened(config.getCameraId())) {
            camera.captureSessionInit();
        }else{
            if (!camera.openCamera(streamEncoder, mp4Recorder)) {
                log("Camera open error.");
                isVideoStarting = false;
                return;
            }
        }
        streamEncoder.startSendFrames();
        Thread videoSenderThread = new Thread(videoSenderRun);
        videoSenderThread.setDaemon(false);
        videoSenderThread.setName("videoSenderThread");
        videoSenderThread.start();
        isVideoStarting = false;
    }

    private void startAudioStream(){
        if (isAudioStarting) return;
        isAudioStarting = true;
        if (streamEncoder.isAudioSending()){
            if (streamEncoder.getAudioBitRate() == config.getAudioStreamBitrate()) {
                sendAudioInitialFrame(audioInitialFrame);
                isAudioStarting = false;
                return;
            }else{
                streamEncoder.stopAudioEncoder();
            }
        }
        audioSenderThreadId++;
        audioInitialFrame = null;
        streamEncoder.startAudioEncoder();
        if (streamEncoder.isAudioSending()){
            Thread audioSenderThread = new Thread(audioSenderRun);
            audioSenderThread.setDaemon(false);
            audioSenderThread.setName("audioSenderThread");
            audioSenderThread.start();
        }
        isAudioStarting = false;
    }

    public boolean isConnected(){
        return (receiverBuffer != null && receiverBuffer.isConnected());
    }

    private void processData(SavedPacket packet) {
        DataReader buffer = new DataReader(packet.data, true);
        byte packetName = buffer.readByte();
        if (UdpCommon.isPacketNumbered(packetName)) {
            short num = buffer.readShort();
        }
        switch (packetName) {
            case UdpCommon.StartVideo:
            {
                boolean isHevc = buffer.readBoolean();
                startVideoStream(isHevc);
                if (config.isSendAudioStream()) startAudioStream();
                break;
            }
            case UdpCommon.GetVideoConfig:
            {
                sendVideoInitialFrame(null);
                if (config.isSendAudioStream()) sendAudioInitialFrame(null);
                break;
            }
            case UdpCommon.ChangeBitRate:
            {
                boolean increase = buffer.readBoolean();
                streamEncoder.changeBitRate(increase);
                break;
            }
            case UdpCommon.Ping:
            {
                boolean toEndPoint = buffer.readBoolean();
                long time = buffer.readLong();
                byte target = -1;
                if (buffer.getRemaining() > 0) target = buffer.readByte();
                udpSender.sendPong(toEndPoint, time, target);
                break;
            }
            case UdpCommon.FcInfo:
            {
                if (!msp.isInitialized()) break;
                sendFcInfo(msp.getFcInfo());
                break;
            }
            case UdpCommon.OsdConfig:
            {
                if (!msp.isInitialized()) break;
                msp.runGetOsdConfig();
                break;
            }
            case UdpCommon.BatteryConfig:
            {
                if (!msp.isInitialized()) break;
                msp.runGetBatteryConfig();
                break;
            }
            case UdpCommon.BoxIds:
            {
                if (!msp.isInitialized()) break;
                msp.runGetBoxIds();
                break;
            }
            case UdpCommon.BoxNames:
            {
                if (!msp.isInitialized()) break;
                msp.runGetBoxNames();
                break;
            }
            case UdpCommon.StartStopRecording:
            {
                boolean start = buffer.readBoolean();
                boolean withAudio = config.isRecordAudio();
                if (start){
                    if (config.isUseExtraEncoder()){
                        mp4Recorder.startRecording(withAudio);
                    }else{
                        mp4Recorder.startRecording(withAudio, streamEncoder);
                    }
                }else{
                    mp4Recorder.stopRecording();
                }
                break;
            }
            case UdpCommon.Config:
            {
                int resCode = config.processReceivedConfig(buffer);
                if (resCode == 0) {
                    sendConfigReceived();
                    msp.setRawRcMinPeriod();
                }else if (resCode == -1){
                    sendVersionMismatch();
                }
                break;
            }
            case UdpCommon.RcFrame:
            {
                byte channelsCount = buffer.readByte();
                if (channelsCount <= 0 || channelsCount > FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT) break;
                if (channelsCount * 2 != buffer.getRemaining()) break;
                short[] rcChannels = new short[channelsCount];
                for (int i = 0; i < channelsCount; i++) {
                    rcChannels[i] = buffer.readShort();
                }
                msp.setRawRc(rcChannels);
                break;
            }
        }
    }

    private void startStopRecording() {
        if (mp4Recorder.isRecording()) {
            mp4Recorder.stopRecording();
        }else{
            boolean withAudio = config.isRecordAudio();
            if (config.isUseExtraEncoder()) {
                mp4Recorder.startRecording(withAudio);
            }else{
                mp4Recorder.startRecording(withAudio, streamEncoder);
            }
        }
    }

    private final Runnable videoSenderRun = new Runnable() {
        public void run() {
            final int id = videoSenderThreadId;
            log("Video sender thread is running");
            while (id == videoSenderThreadId) {
                try {
                    MediaCodecBuffer buf = streamEncoder.videoStreamOutputBuffer.take();
                    if (buf == null) continue;
                    switch (buf.flags){
                        case MediaCodec.BUFFER_FLAG_CODEC_CONFIG:
                            sendVideoInitialFrame(buf.data);
                            break;
                        case MediaCodec.BUFFER_FLAG_KEY_FRAME:
                            sendKeyFrame(buf.data);
                            break;
                        default:
                            sendVideoFrame(buf.data);
                            break;
                    }
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    };

    private final Runnable audioSenderRun = new Runnable() {
        public void run() {
            final int id = audioSenderThreadId;
            log("Audio sender thread is running");
            while (id == audioSenderThreadId && streamEncoder != null && streamEncoder.isAudioSending()) {
                try {
                    if (!config.isSendAudioStream()){
                        streamEncoder.stopAudioEncoder();
                        break;
                    }
                    MediaCodecBuffer buf = streamEncoder.audioOutputBuffer.take();
                    if (buf == null) continue;
                    if (buf.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        sendAudioInitialFrame(buf.data);
                    } else {
                        sendAudioFrame(buf.data);
                    }
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    };

    private void sendTelemetryData(){
        try {
            TelemetryData buf;
            boolean sendData = false;
            UdpPacketData packetData = new UdpPacketData(UdpCommon.TelemetryData);
            do{
                buf = msp.telemetryOutputBuffer.poll();
                if (buf != null){
                    sendData = true;
                    fillTelemetryPacketData(buf, packetData);
                }
                buf = phoneTelemetry.telemetryOutputBuffer.poll();
                if (buf != null){
                    sendData = true;
                    fillTelemetryPacketData(buf, packetData);
                }
            }while (buf != null);
            if (sendData) udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            log("sendTelemetryData error: " + e);
        }
    }

    private void fillTelemetryPacketData(TelemetryData buf, UdpPacketData packetData){
        FcInfo fcInfo = msp.getFcInfo();
        try {
            if (fcInfo == null && buf.code < FcCommon.DD_TIMERS) return;
            packetData.daos.writeShort(buf.code);
            DataReader buffer = new DataReader(buf.data, false);
            switch (buf.code){
                case FcCommon.MSP_ATTITUDE: {
                    packetData.daos.writeShort(buffer.readShort());//roll
                    packetData.daos.writeShort(buffer.readShort());//pitch
                    packetData.daos.writeShort(buffer.readShort());//yaw
                    break;
                }
                case FcCommon.MSP_ALTITUDE: {
                    packetData.daos.writeInt(buffer.readInt());//altitude
                    packetData.daos.writeShort(buffer.readShort());//altVelocity
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_INAV){
                        packetData.daos.writeInt(buffer.readInt());//altBaro
                    }
                    break;
                }
                case FcCommon.MSP_ANALOG: {
                    buffer.readByte();//legacyVoltage
                    packetData.daos.writeShort(buffer.readShort());//mahDrawn
                    packetData.daos.writeShort(buffer.readShort());//rssi
                    packetData.daos.writeShort(buffer.readShort());//amperage
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) {
                        packetData.daos.writeShort(buffer.readShort());//voltage
                    }
                    break;
                }
                case FcCommon.MSP_BOXIDS:
                case FcCommon.MSP_BOXNAMES: {
                    byte[] data = buffer.getData();
                    packetData.daos.writeShort(data.length);
                    packetData.daos.write(data, 0, data.length);
                    break;
                }
                case FcCommon.MSP_BATTERY_CONFIG: {
                    switch (fcInfo.getFcVariant()){
                        case FcInfo.FC_VARIANT_INAV:{
                            buffer.readShort();//voltageScale
                            buffer.readByte();//voltageSource
                            buffer.readByte();//configCells
                            buffer.readShort();//cellDetect
                            packetData.daos.writeShort(buffer.readShort());//minCellVoltage
                            packetData.daos.writeShort(buffer.readShort());//maxCellVoltage
                            packetData.daos.writeShort(buffer.readShort());//warningCellVoltage
                            buffer.readShort();//currentOffset
                            buffer.readShort();//currentScale
                            buffer.readInt();//batteryCapacity
                            buffer.readInt();//capacityWarning
                            buffer.readInt();//capacityCritical
                            buffer.readByte();//capacityUnit
                            break;
                        }
                        case FcInfo.FC_VARIANT_BETAFLIGHT:{
                            buffer.readByte();//vbatMinCellVoltage - legacy
                            buffer.readByte();//vbatMaxCellVoltage - legacy
                            buffer.readByte();//vbatWarningCellVoltage - legacy
                            buffer.readShort();//batteryCapacity
                            buffer.readByte();//voltageMeterSource
                            buffer.readByte();//currentMeterSource
                            packetData.daos.writeShort(buffer.readShort());//minCellVoltage
                            packetData.daos.writeShort(buffer.readShort());//maxCellVoltage
                            packetData.daos.writeShort(buffer.readShort());//warningCellVoltage
                            break;
                        }
                    }
                    break;
                }
                case FcCommon.MSP_OSD_CONFIG: {
                    packetData.daos.writeShort(MainActivity.versionCode);
                    switch (fcInfo.getFcVariant()){
                        case FcInfo.FC_VARIANT_INAV:{
                            byte driver = buffer.readByte();
                            packetData.daos.writeByte(driver);
                            if (driver == 0) break;
                            packetData.daos.writeByte(buffer.readByte());//videoSystem
                            packetData.daos.writeByte(buffer.readByte());//units
                            packetData.daos.writeByte(buffer.readByte());//rssiAlarm
                            packetData.daos.writeShort(buffer.readShort());//capacityWarning
                            packetData.daos.writeShort(buffer.readShort());//timeAlarm
                            packetData.daos.writeShort(buffer.readShort());//altAlarm
                            packetData.daos.writeShort(buffer.readShort());//distAlarm
                            packetData.daos.writeShort(buffer.readShort());//negAltAlarm
                            int osdItemCount = buffer.getRemaining() / 2;
                            packetData.daos.writeShort(osdItemCount);
                            if (osdItemCount == 0) break;
                            for (int i = 0; i < osdItemCount; i++) {
                                packetData.daos.writeShort(buffer.readShort());//osdItem
                            }
                            break;
                        }
                        case FcInfo.FC_VARIANT_BETAFLIGHT:{
                            buffer.readByte();//osdFlags
                            packetData.daos.writeByte(buffer.readByte());//videoSystem
                            packetData.daos.writeByte(buffer.readByte());//units
                            packetData.daos.writeByte(buffer.readByte());//rssiAlarm
                            packetData.daos.writeShort(buffer.readShort());//capacityWarning
                            buffer.readByte();//unused
                            int osdItemCount = buffer.readUnsignedByteAsInt();
                            packetData.daos.writeShort(buffer.readShort());//altAlarm
                            packetData.daos.writeShort(osdItemCount);
                            if (osdItemCount <= 0) break;
                            for (int i = 0; i < osdItemCount; i++) {
                                packetData.daos.writeShort(buffer.readShort());//osdItem
                            }
                            int osdStatCount = buffer.readUnsignedByteAsInt();
                            packetData.daos.writeByte(osdStatCount);
                            if (osdStatCount > 0){
                                for (int i = 0; i < osdStatCount; i++) {
                                    packetData.daos.writeByte(buffer.readByte());//osdStat
                                }
                            }
                            int osdTimerCount = buffer.readUnsignedByteAsInt();
                            packetData.daos.writeByte(osdTimerCount);
                            if (osdTimerCount > 0){
                                for (int i = 0; i < osdTimerCount; i++) {
                                    packetData.daos.writeShort(buffer.readShort());//osdTimer
                                }
                            }
                            buffer.readShort();//legacyWarnings
                            packetData.daos.writeByte(buffer.readByte());//osdWarningsCount
                            packetData.daos.writeInt(buffer.readInt());//enabledWarnings
                            buffer.readByte();//osdProfileCount
                            packetData.daos.writeByte(buffer.readByte());//osdSelectedProfile
                            buffer.readByte();//overlayRadioMode
                            packetData.daos.writeByte(buffer.readByte());//cameraFrameWidth
                            packetData.daos.writeByte(buffer.readByte());//cameraFrameHeight
                            break;
                        }
                    }
                    break;
                }
                case FcCommon.MSP_VTX_CONFIG: {
                    int vtxDevice = buffer.readUnsignedByteAsInt();
                    packetData.daos.writeShort(vtxDevice);
                    if (vtxDevice == FcCommon.VTXDEV_UNKNOWN) break;
                    packetData.daos.writeByte(buffer.readByte());//band
                    packetData.daos.writeByte(buffer.readByte());//channel
                    packetData.daos.writeByte(buffer.readByte());//power
                    packetData.daos.writeByte(buffer.readByte());//pitMode
                    if (fcInfo.getFcVariant() == FcInfo.FC_VARIANT_BETAFLIGHT) packetData.daos.writeShort(buffer.readShort());//frequency
                    packetData.daos.writeByte(buffer.readByte());//deviceIsReady
                    packetData.daos.writeByte(buffer.readByte());//lowPowerDisarm
                    break;
                }
                case FcCommon.MSP_BATTERY_STATE: {
                    packetData.daos.writeByte(buffer.readByte());//cellCount
                    packetData.daos.writeShort(buffer.readShort());//capacity
                    buffer.readByte();//legacyVoltage
                    packetData.daos.writeShort(buffer.readShort());//mahDrawn
                    packetData.daos.writeShort(buffer.readShort());//amperage
                    packetData.daos.writeByte(buffer.readByte());//batteryState
                    packetData.daos.writeShort(buffer.readShort());//voltage
                    break;
                }
                case FcCommon.MSP_STATUS: {
                    packetData.daos.writeShort(buffer.readShort());//cycleTime
                    packetData.daos.writeShort(buffer.readShort());//i2cErrorCount
                    packetData.daos.writeShort(buffer.readShort());//sensorStatus
                    switch (fcInfo.getFcVariant()){
                        case FcInfo.FC_VARIANT_INAV:{
                            buffer.readInt();//legacyModeFlags
                            packetData.daos.writeByte(buffer.readByte());//configProfile
                            break;
                        }
                        case FcInfo.FC_VARIANT_BETAFLIGHT:{
                            int firstModeFlag = buffer.readInt();
                            packetData.daos.writeByte(buffer.readByte());//currentPidProfileIndex
                            packetData.daos.writeShort(buffer.readShort());//averageSystemLoad
                            buffer.readShort();//unused
                            byte[] modeFlags;
                            int modeFlagsSize = buffer.readByte()+4;
                            packetData.daos.writeByte(modeFlagsSize);
                            if (modeFlagsSize > 0) {
                                modeFlags = new byte[modeFlagsSize];
                                modeFlags[0] = (byte) (firstModeFlag & 0xFF);
                                modeFlags[1] = (byte) (firstModeFlag >> 8 & 0xFF);
                                modeFlags[2] = (byte) (firstModeFlag >> 16 & 0xFF);
                                modeFlags[3] = (byte) (firstModeFlag >> 24 & 0xFF);
                                for (int i = 4; i < modeFlagsSize; i++) {
                                    modeFlags[i] = buffer.readByte();
                                }
                                for (int i = 0; i < modeFlagsSize; i++) {
                                    packetData.daos.writeByte(modeFlags[i]);
                                }
                            }
                            buffer.readByte();//armingFlagsCount
                            packetData.daos.writeInt(buffer.readInt());//armingFlags
                            packetData.daos.writeByte(buffer.readByte());//configStateFlags
                            short coreTemperatureCelsius = 0;
                            if (fcInfo.getMspApiVersionMajor() > 1 || fcInfo.getMspApiVersionMajor() == 1 && fcInfo.getMspApiVersionMinor() > 45) {
                                coreTemperatureCelsius = buffer.readShort();
                            }
                            packetData.daos.writeShort(coreTemperatureCelsius);
                            break;
                        }
                    }
                    break;
                }
                case FcCommon.MSP_RAW_GPS: {
                    packetData.daos.writeByte(buffer.readByte());//fixType
                    packetData.daos.writeByte(buffer.readByte());//numSat
                    packetData.daos.writeInt(buffer.readInt());//lat
                    packetData.daos.writeInt(buffer.readInt());//lon
                    packetData.daos.writeShort(buffer.readShort());//altGps
                    packetData.daos.writeShort(buffer.readShort());//groundSpeed
                    packetData.daos.writeShort(buffer.readShort());//groundCourse
                    packetData.daos.writeShort(buffer.readShort());//hdop
                    break;
                }
                case FcCommon.MSP_COMP_GPS: {
                    packetData.daos.writeShort(buffer.readShort());//distanceToHome
                    packetData.daos.writeShort(buffer.readShort());//directionToHome
                    packetData.daos.writeByte(buffer.readByte());//gpsHeartbeat
                    break;
                }
                case FcCommon.MSP_OSD_CANVAS: {
                    packetData.daos.writeByte(buffer.readByte());//cols
                    packetData.daos.writeByte(buffer.readByte());//rows
                    break;
                }
                case FcCommon.MSP2_INAV_STATUS: {
                    packetData.daos.writeShort(buffer.readShort());//cycleTime
                    packetData.daos.writeShort(buffer.readShort());//i2cErrorCount
                    packetData.daos.writeShort(buffer.readShort());//sensorStatus
                    packetData.daos.writeShort(buffer.readShort());//averageSystemLoad
                    packetData.daos.writeByte(buffer.readByte());//profiles
                    packetData.daos.writeInt(buffer.readInt());//armingFlags
                    int modeFlagsSize = (int)Math.ceil((buffer.getRemaining() - 1) / 4.0);
                    packetData.daos.writeByte(modeFlagsSize);
                    if (modeFlagsSize > 0) {
                        for (int i = 0; i < modeFlagsSize; i++) {
                            packetData.daos.writeInt(buffer.readInt());//modeFlags
                        }
                    }
                    break;
                }
                case FcCommon.MSP2_INAV_ANALOG: {
                    packetData.daos.writeByte(buffer.readByte());//batteryInfo
                    packetData.daos.writeShort(buffer.readShort());//voltage
                    packetData.daos.writeShort(buffer.readShort());//amperage
                    packetData.daos.writeInt(buffer.readInt());//power
                    packetData.daos.writeInt(buffer.readInt());//mahDrawn
                    packetData.daos.writeInt(buffer.readInt());//mwhDrawn
                    packetData.daos.writeInt(buffer.readInt());//batteryRemainingCapacity
                    packetData.daos.writeByte(buffer.readByte());//batteryPercentage
                    packetData.daos.writeShort(buffer.readShort());//rssi
                    break;
                }
                case FcCommon.DD_TIMERS: {
                    packetData.daos.writeInt(buffer.readInt());//onTime
                    packetData.daos.writeInt(buffer.readInt());//flyTime
                    packetData.daos.writeInt(buffer.readInt());//lastArmTime
                    break;
                }
                case FcCommon.DD_PHONE_BATTERY_STATE: {
                    packetData.daos.writeByte(buffer.readByte());//batteryLevel
                    packetData.daos.writeBoolean(buffer.readBoolean());//isCharging
                    break;
                }
                case FcCommon.DD_CAMERA_FPS: {
                    packetData.daos.writeShort(buffer.readShort());//cameraFps
                    break;
                }
                case FcCommon.DD_VIDEO_BIT_RATE: {
                    packetData.daos.writeFloat(buffer.readFloat());//videoBitRate
                    break;
                }
                case FcCommon.DD_VIDEO_RECORDER_STATE: {
                    packetData.daos.writeBoolean(buffer.readBoolean());//isRecording
                    packetData.daos.writeInt(buffer.readInt());//recordingTimeSec
                    break;
                }
                case FcCommon.DD_VIDEO_RECORDER_START_STOP: {//BOXCAMERA2
                    startStopRecording();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log("fillTelemetryPacketData error: " + e + ", Code: " + buf.code);
        }
    }

    private void sendFcInfo(FcInfo fcInfo){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.FcInfo);
            packetData.daos.writeByte(fcInfo.getFcVariant());
            packetData.daos.writeByte(fcInfo.getFcVersionMajor());
            packetData.daos.writeByte(fcInfo.getFcVersionMinor());
            packetData.daos.writeByte(fcInfo.getFcVersionPatchLevel());
            packetData.daos.writeByte(fcInfo.getMspProtocolVersion());
            packetData.daos.writeByte(fcInfo.getMspApiVersionMajor());
            packetData.daos.writeByte(fcInfo.getMspApiVersionMinor());
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendFcInfo error: " + e);
        }
    }

    private void sendVideoInitialFrame(byte[] buf) {
        if (videoInitialFrame == null && (buf == null || buf.length == 0)) return;
        if (buf == null) {
            buf = videoInitialFrame;
        } else {
            videoInitialFrame = buf.clone();
        }
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.VideoInitialFrame);
            packetData.daos.writeShort(camera.cameraResolution.getWidth());
            packetData.daos.writeShort(camera.cameraResolution.getHeight());
            packetData.daos.writeBoolean(streamEncoder.getCurrentCodecType().equals(MediaCommon.hevcCodecMime));
            packetData.daos.writeBoolean(camera.frontFacing);
            packetData.daos.write(buf, 0, buf.length);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendVideoInitialFrame error: " + e);
        }
    }

    private void sendKeyFrame(byte[] buf) {
        if (socket == null || socket.isClosed()) return;
        if (videoFrameNum == Short.MAX_VALUE) videoFrameNum = 0;
        videoFrameNum++;
        int size = buf.length;
        int offset = 0;
        while (offset < size && udpSender != null) {
            try {
                UdpPacketData packetData = new UdpPacketData(UdpCommon.KeyFrame);
                packetData.daos.writeShort(videoFrameNum);
                packetData.daos.writeInt(offset);
                int headerSize = 9;
                if (offset == 0) {
                    packetData.daos.writeInt(size);
                    headerSize += 4;
                }
                int dataSize = UdpCommon.packetLength - headerSize;
                if (size - offset <= dataSize) {
                    packetData.daos.write(buf, offset, size - offset);
                    offset = size;
                } else {
                    packetData.daos.write(buf, offset, dataSize);
                    offset += dataSize;
                }
                udpSender.sendPacket(packetData.getData());
            } catch (Exception e) {
                e.printStackTrace();
                log("sendKeyFrame error: " + e);
            }
        }
    }

    private void sendVideoFrame(byte[] buf) {
        if (socket == null || socket.isClosed()) return;
        if (videoFrameNum == Short.MAX_VALUE) videoFrameNum = 0;
        videoFrameNum++;
        int size = buf.length;
        int offset = 0;
        while (offset < size && udpSender != null) {
            try {
                UdpPacketData packetData = new UdpPacketData(UdpCommon.VideoFrame);
                packetData.daos.writeShort(videoFrameNum);
                packetData.daos.writeInt(offset);
                int headerSize = 9;
                if (offset == 0) {
                    packetData.daos.writeInt(size);
                    headerSize += 4;
                }
                int dataSize = UdpCommon.packetLength - headerSize;
                if (size - offset <= dataSize) {
                    packetData.daos.write(buf, offset, size - offset);
                    offset = size;
                } else {
                    packetData.daos.write(buf, offset, dataSize);
                    offset += dataSize;
                }
                udpSender.sendPacket(packetData.getData());
            } catch (Exception e) {
                e.printStackTrace();
                log("sendVideoFrame error: " + e);
            }
        }
    }

    private void sendAudioInitialFrame(byte[] buf) {
        if (audioInitialFrame == null && (buf == null || buf.length == 0)) return;
        if (buf == null) {
            buf = audioInitialFrame;
        } else {
            audioInitialFrame = buf.clone();
        }
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.AudioInitialFrame);
            packetData.daos.writeInt(streamEncoder.getAudioFormat().getSampleRate());
            packetData.daos.writeByte(streamEncoder.getAudioFormat().getChannelCount());
            packetData.daos.writeByte(streamEncoder.getAudioFormat().getEncoding());
            packetData.daos.write(buf, 0, buf.length);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendAudioInitialFrame error: " + e);
        }
    }

    private void sendAudioFrame(byte[] buf) {
        if (socket == null || socket.isClosed() || buf == null) return;
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.AudioFrame);
            packetData.daos.write(buf, 0, buf.length);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendAudioFrame error: " + e);
        }
    }

    private void sendConfigReceived() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.ConfigReceived);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendConfigReceived error: " + e);
        }
    }

    private void sendVersionMismatch() {
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.VersionMismatch);
            udpSender.sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendVersionMismatch error: " + e);
        }
    }

    public void close() {
        videoSenderThreadId++;
        audioSenderThreadId++;
        udpThreadsId++;
        if (receiverBuffer != null) {
            receiverBuffer.close();
            receiverBuffer = null;
        }
        if (udpSender != null) {
            udpSender.close();
            udpSender = null;
        }
        if (socket != null) socket.close();
    }
}
