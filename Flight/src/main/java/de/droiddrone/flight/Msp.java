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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.DataWriter;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.TelemetryData;

public class Msp {
    private static final byte MSP_HEADER_START = 0x24;//$
    private static final byte MSP_HEADER_V1 = 0x4D;//M
    private static final byte MSP_HEADER_V2 = 0x58;//X
    private static final byte MSP_HEADER_REQUEST = 0x3C;//<
    private static final byte MSP_HEADER_RESPONSE = 0x3E;//>
    private static final byte MSP_HEADER_ERROR = 0x21;//!
    private static final byte MSP_V1_HEADER_SIZE = 5;
    private static final byte MSP_V2_HEADER_SIZE = 8;
    private static final byte MSP_CRC_SIZE = 1;
    private static final byte MSP_V1_MIN_REQUEST_SIZE = MSP_V1_HEADER_SIZE + MSP_CRC_SIZE;
    private static final byte MSP_V2_MIN_REQUEST_SIZE = MSP_V2_HEADER_SIZE + MSP_CRC_SIZE;
    private final Serial serial;
    private final Config config;
    public final ArrayBlockingQueue<TelemetryData> telemetryOutputBuffer = new ArrayBlockingQueue<>(30);
    private int fcVariant;
    private int apiProtocolVersion;
    private int apiVersionMajor;
    private int apiVersionMinor;
    private int fcVersionMajor;
    private int fcVersionMinor;
    private int fcVersionPatchLevel;
    private int threadsId;
    private boolean isInitialized;
    private FcInfo fcInfo;
    private boolean runFcInit;
    private boolean runGetBoxNames;
    private boolean runGetRxMap;
    private boolean runGetBoxIds;
    private byte bfBoxNamesPage = 0;
    private byte bfBoxIdsPage = 0;
    private final byte[][] bfBoxNamesData = new byte[FcCommon.BF_BOXMODES_PAGE_COUNT][];
    private final byte[][] bfBoxIdsData = new byte[FcCommon.BF_BOXMODES_PAGE_COUNT][];
    private boolean runGetOsdConfig;
    private boolean runGetBatteryConfig;
    private long onTimestamp, flyTimestamp;
    private int onTime, flyTime, lastArmTime, flyTimeSave;
    private int[] rxMap = null;
    private int[] boxIds = null;
    private int[] modeFlagsInav = null;
    private byte[] modeFlagsBtfl = null;
    private byte[] osdConfig = null;
    private boolean oldCamSwitchState;
    private int rcMinPeriod;
    private long rcLastFrame;
    private int platformType;

    public Msp(Serial serial, Config config){
        this.serial = serial;
        this.config = config;
        fcVariant = FcInfo.FC_VARIANT_UNKNOWN;
        apiProtocolVersion = -1;
        apiVersionMajor = -1;
        apiVersionMinor = -1;
        fcVersionMajor = -1;
        fcVersionMinor = -1;
        fcVersionPatchLevel = -1;
        platformType = -1;
    }

    public boolean isInitialized(){
        isInitialized = (fcVariant != FcInfo.FC_VARIANT_UNKNOWN && apiProtocolVersion != -1 && apiVersionMajor != -1 && apiVersionMinor != -1
                && fcVersionMajor != -1 && fcVersionMinor != -1 && fcVersionPatchLevel != -1 && platformType != -1);
        if (isInitialized && fcInfo == null) setFcInfo();
        return isInitialized;
    }

    private void setFcInfo(){
        fcInfo = new FcInfo(fcVariant, fcVersionMajor, fcVersionMinor, fcVersionPatchLevel, apiProtocolVersion, apiVersionMajor, apiVersionMinor, platformType);
        onTimestamp = System.currentTimeMillis();
        flyTimestamp = 0;
        runFcInit = false;
        runGetRxMap = true;
        log(fcInfo.getFcName() + " Ver. " + fcInfo.getFcVersionStr() + " detected.");
        log("MSP API Ver.: " + fcInfo.getFcApiVersionStr());
    }

    public void setRcMinPeriod(){
        rcMinPeriod = 1000 / config.getRcRefreshRate() / 2;
    }

    public FcInfo getFcInfo(){
        return fcInfo;
    }

    public void initialize() {
        runFcInit = true;
        onTimestamp = 0;
        flyTimestamp = 0;
        flyTimeSave = 0;
        onTime = 0;
        flyTime = 0;
        lastArmTime = 0;
        setRcMinPeriod();
        telemetryOutputBuffer.clear();
        threadsId++;
        Thread mspThread = new Thread(mspRun);
        mspThread.setDaemon(false);
        mspThread.setName("mspThread");
        mspThread.start();
    }

    public void runGetBoxIds(){
        runGetBoxIds = true;
    }

    public void runGetBoxNames(){
        runGetBoxNames = true;
    }

    public void runGetOsdConfig(){
        runGetOsdConfig = true;
    }

    public void runGetBatteryConfig(){
        runGetBatteryConfig = true;
    }

    public void close(){
        threadsId++;
        isInitialized = false;
        osdConfig = null;
        telemetryOutputBuffer.clear();
    }

    private final Runnable mspRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            final int timerDelayMs = 1000 / config.getTelemetryRefreshRate();
            int timerDiv = 0;
            log("Start MSP thread - OK");
            while (id == threadsId) {
                try {
                    timerDiv++;

                    if (!isInitialized && isInitialized()) setFcInfo();
                    if (runFcInit) {
                        if (fcVariant == 0) getFcVariant();
                        if (apiVersionMajor == -1) getMspApiVersion();
                        if (fcVersionMajor == -1) getFcVersion();
                        if (platformType == -1 && fcVariant != 0) getMixerConfig();
                        Thread.sleep(timerDelayMs);
                        continue;
                    }

                    if (timerDiv % 5 == 0) {
                        if (runGetBoxNames || runGetBoxIds) {
                            switch (fcVariant){
                                case FcInfo.FC_VARIANT_INAV:
                                    if (runGetBoxIds) getBoxIds();
                                    if (runGetBoxNames) getBoxNames();
                                    break;
                                case FcInfo.FC_VARIANT_BETAFLIGHT:
                                    if (runGetBoxIds) getBoxIds(bfBoxIdsPage);
                                    if (runGetBoxNames) getBoxNames(bfBoxNamesPage);
                                    break;
                            }
                        }
                        if (runGetOsdConfig) getOsdConfig();
                        if (runGetRxMap) getRxMap();
                        if (runGetBatteryConfig) {
                            if (fcVariant == FcInfo.FC_VARIANT_INAV){
                                runGetBatteryConfig = false;
                            }else {
                                getBatteryConfig();
                            }
                        }
                        switch (fcVariant){
                            case FcInfo.FC_VARIANT_INAV:
                                getInavStatus();
                                break;
                            case FcInfo.FC_VARIANT_BETAFLIGHT:
                                getStatus();
                                break;
                        }
                        checkModeFlags();
                        processOnTimeFlyTime();
                    }

                    if (timerDiv % 10 == 0) {
                        timerDiv = 0;
                        getBatteryState();
                        getRawGps();
                        getCompGps();
                        getVtxConfig();
                        switch (fcVariant){
                            case FcInfo.FC_VARIANT_INAV:
                                getInavAnalog();
                                break;
                            case FcInfo.FC_VARIANT_BETAFLIGHT:
                                getAnalog();
                                break;
                        }
                    }

                    getAttitude();
                    getAltitude();
                    Thread.sleep(timerDelayMs);
                } catch (Exception e) {
                    log("MSP thread error: " + e);
                }
            }
        }
    };

    public static class MspPacket{
        public final byte version;
        public final byte type;
        public final byte flag;
        public final int code;
        public final int payloadSize;
        public final byte[] payload;

        public MspPacket(byte type, byte flag, int code, int payloadSize, byte[] payload) {
            this.version = MSP_HEADER_V2;
            this.type = type;
            this.flag = flag;
            this.code = code;
            this.payloadSize = payloadSize;
            this.payload = payload;
        }

        public MspPacket(byte type, int code, int payloadSize, byte[] payload) {
            this.version = MSP_HEADER_V1;
            this.type = type;
            this.flag = 0;
            this.code = code;
            this.payloadSize = payloadSize;
            this.payload = payload;
        }
    }

    private List<MspPacket> parsePackets(byte[] data){
        DataReader reader = new DataReader(data, false);
        List<MspPacket> packets = new ArrayList<>();
        while (reader.getRemaining() > 0) {
            byte headerStart = reader.readByte();
            if (headerStart != MSP_HEADER_START) return packets;
            byte magic = reader.readByte();
            if (magic != MSP_HEADER_V1 && magic != MSP_HEADER_V2) return packets;
            boolean isMsp2 = magic == MSP_HEADER_V2;
            byte type = reader.readByte();
            if (type != MSP_HEADER_REQUEST && type != MSP_HEADER_RESPONSE && type != MSP_HEADER_ERROR) return packets;
            byte[] payload = null;
            if (isMsp2){
                if (reader.getRemaining() < MSP_V2_MIN_REQUEST_SIZE - 3) return packets;
                byte flag = reader.readByte();
                int code = reader.readUnsignedShortAsInt();
                int payloadSize = reader.readUnsignedShortAsInt();
                if (reader.getRemaining() < payloadSize + 1) return packets;
                if (payloadSize > 0) {
                    payload = new byte[payloadSize];
                    reader.read(payload, 0, payloadSize);
                }
                int checksum = reader.readUnsignedByteAsInt();
                if (checksum == calculateCrcV2(flag, code, payloadSize, payload)){
                    packets.add(new MspPacket(type, flag, code, payloadSize, payload));
                }else{
                    return packets;
                }
            }else{
                if (reader.getRemaining() < MSP_V1_MIN_REQUEST_SIZE - 3) return packets;
                int payloadSize = reader.readUnsignedByteAsInt();
                if (reader.getRemaining() < payloadSize + 2) return packets;
                int code = reader.readUnsignedByteAsInt();
                if (payloadSize > 0) {
                    payload = new byte[payloadSize];
                    reader.read(payload, 0, payloadSize);
                }
                int checksum = reader.readUnsignedByteAsInt();
                if (checksum == calculateCrcV1(code, payloadSize, payload)){
                    packets.add(new MspPacket(type, code, payloadSize, payload));
                }
            }
        }
        return packets;
    }

    public void processData(byte[] buf, int dataLength){
        if (dataLength <= MSP_V1_HEADER_SIZE) return;
        byte[] data = new byte[dataLength];
        System.arraycopy(buf, 0, data, 0, dataLength);
        List<MspPacket> packets = null;
        try {
            packets = parsePackets(data);
        }catch (Exception e){
            log("Msp - parsePackets error: " + e);
        }
        if (packets == null || packets.isEmpty()) return;
        for (MspPacket packet : packets) {
            try {
                if (packet.type == MSP_HEADER_ERROR) {
                    StringBuilder s = new StringBuilder();
                    for (byte b : data) s.append(String.valueOf(b)).append(" ");
                    log("MSP error received: " + s);
                    continue;
                }
                if (packet.type != MSP_HEADER_RESPONSE) continue;
                DataReader buffer = new DataReader(packet.payload, false);
                switch (packet.code) {
                    case FcCommon.MSP_API_VERSION:
                        apiProtocolVersion = buffer.readUnsignedByteAsInt();
                        apiVersionMajor = buffer.readUnsignedByteAsInt();
                        apiVersionMinor = buffer.readUnsignedByteAsInt();
                        break;
                    case FcCommon.MSP_FC_VARIANT: {
                        String fcStr = buffer.readBufferAsString();
                        if (FcInfo.INAV_ID.equals(fcStr)) fcVariant = FcInfo.FC_VARIANT_INAV;
                        if (FcInfo.BETAFLIGHT_ID.equals(fcStr))
                            fcVariant = FcInfo.FC_VARIANT_BETAFLIGHT;
                        break;
                    }
                    case FcCommon.MSP_FC_VERSION:
                        fcVersionMajor = buffer.readUnsignedByteAsInt();
                        fcVersionMinor = buffer.readUnsignedByteAsInt();
                        fcVersionPatchLevel = buffer.readUnsignedByteAsInt();
                        break;
                    case FcCommon.MSP_MIXER_CONFIG:
                        platformType = buffer.readUnsignedByteAsInt();
                        break;
                    case FcCommon.MSP2_INAV_MIXER:
                        buffer.readUnsignedByteAsInt();// motorDirectionInverted
                        buffer.readUnsignedByteAsInt();// 0
                        buffer.readUnsignedByteAsInt();// motorstopOnLow
                        platformType = buffer.readUnsignedByteAsInt();
                        break;
                    case FcCommon.MSP_BOXNAMES: {
                        switch (fcVariant) {
                            case FcInfo.FC_VARIANT_INAV: {
                                runGetBoxNames = false;
                                telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                                break;
                            }
                            case FcInfo.FC_VARIANT_BETAFLIGHT: {
                                byte[] pageData = buffer.getData();
                                if (bfBoxNamesPage > 0) {
                                    boolean found = false;
                                    for (int i = 0; i < bfBoxNamesPage; i++) {
                                        if (Arrays.equals(bfBoxNamesData[i], pageData)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (found) break;
                                }
                                bfBoxNamesData[bfBoxNamesPage] = pageData;
                                bfBoxNamesPage++;
                                if (bfBoxNamesPage == FcCommon.BF_BOXMODES_PAGE_COUNT || pageData == null && bfBoxNamesPage > 1) {
                                    runGetBoxNames = false;
                                    bfBoxNamesPage = 0;
                                    int total = 0;
                                    int offset = 0;
                                    for (int i = 0; i < FcCommon.BF_BOXMODES_PAGE_COUNT; i++) {
                                        if (bfBoxNamesData[i] == null) continue;
                                        total += bfBoxNamesData[i].length;
                                    }
                                    if (total > 0) {
                                        byte[] allPages = new byte[total];
                                        for (int i = 0; i < FcCommon.BF_BOXMODES_PAGE_COUNT; i++) {
                                            if (bfBoxNamesData[i] == null) continue;
                                            int length = bfBoxNamesData[i].length;
                                            System.arraycopy(bfBoxNamesData[i], 0, allPages, offset, length);
                                            offset += length;
                                        }
                                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, allPages));
                                    }
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case FcCommon.MSP_BOXIDS: {
                        switch (fcVariant) {
                            case FcInfo.FC_VARIANT_INAV: {
                                runGetBoxIds = false;
                                boxIds = FcCommon.getBoxIds(buffer.getData());
                                telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                                break;
                            }
                            case FcInfo.FC_VARIANT_BETAFLIGHT: {
                                byte[] pageData = buffer.getData();
                                if (bfBoxIdsPage > 0) {
                                    boolean found = false;
                                    for (int i = 0; i < bfBoxIdsPage; i++) {
                                        if (Arrays.equals(bfBoxIdsData[i], pageData)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (found) break;
                                }
                                bfBoxIdsData[bfBoxIdsPage] = pageData;
                                bfBoxIdsPage++;
                                if (bfBoxIdsPage == FcCommon.BF_BOXMODES_PAGE_COUNT || pageData == null && bfBoxIdsPage > 1) {
                                    runGetBoxIds = false;
                                    bfBoxIdsPage = 0;
                                    int total = 0;
                                    int offset = 0;
                                    for (int i = 0; i < FcCommon.BF_BOXMODES_PAGE_COUNT; i++) {
                                        if (bfBoxIdsData[i] == null) continue;
                                        total += bfBoxIdsData[i].length;
                                    }
                                    if (total > 0) {
                                        byte[] allPages = new byte[total];
                                        for (int i = 0; i < FcCommon.BF_BOXMODES_PAGE_COUNT; i++) {
                                            if (bfBoxIdsData[i] == null) continue;
                                            int length = bfBoxIdsData[i].length;
                                            System.arraycopy(bfBoxIdsData[i], 0, allPages, offset, length);
                                            offset += length;
                                        }
                                        boxIds = FcCommon.getBoxIds(allPages);
                                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, allPages));
                                    }
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case FcCommon.MSP_BATTERY_CONFIG: {
                        runGetBatteryConfig = false;
                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                        break;
                    }
                    case FcCommon.MSP_RX_MAP: {
                        if (buffer.getSize() == 0) break;
                        rxMap = new int[buffer.getSize()];
                        for (int i = 0; i < buffer.getSize(); i++) {
                            rxMap[i] = buffer.readUnsignedByteAsInt();
                        }
                        runGetRxMap = false;
                        break;
                    }
                    case FcCommon.MSP_OSD_CONFIG: {
                        runGetOsdConfig = false;
                        osdConfig = buffer.getData();
                        if (fcVariant == FcInfo.FC_VARIANT_BETAFLIGHT) {
                            getOsdCanvas();
                        } else {
                            telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                        }
                        break;
                    }
                    case FcCommon.MSP_OSD_CANVAS: {
                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                        if (osdConfig != null)
                            telemetryOutputBuffer.offer(new TelemetryData(FcCommon.MSP_OSD_CONFIG, osdConfig));
                        break;
                    }
                    case FcCommon.MSP2_INAV_STATUS: {
                        buffer.readShort();//cycleTime
                        buffer.readShort();//i2cErrorCount
                        buffer.readShort();//sensorStatus
                        buffer.readShort();//averageSystemLoad
                        buffer.readByte();//profiles
                        buffer.readInt();//armingFlags
                        int[] modeFlags = null;
                        int modeFlagsSize = (int) Math.ceil((buffer.getRemaining() - 1) / 4.0);
                        if (modeFlagsSize > 0) {
                            modeFlags = new int[modeFlagsSize];
                            for (int i = 0; i < modeFlagsSize; i++) {
                                modeFlags[i] = buffer.readInt();
                            }
                        }
                        this.modeFlagsInav = modeFlags;
                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                        break;
                    }
                    case FcCommon.MSP_STATUS: {
                        switch (fcVariant) {
                            case FcInfo.FC_VARIANT_INAV:
                                telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                                break;
                            case FcInfo.FC_VARIANT_BETAFLIGHT: {
                                buffer.readShort();//cycleTime
                                buffer.readShort();//i2cErrorCount
                                buffer.readShort();//sensorStatus
                                int firstModeFlag = buffer.readInt();
                                buffer.readByte();//currentPidProfileIndex
                                buffer.readShort();//averageSystemLoad
                                buffer.readShort();//unused
                                byte[] modeFlags;
                                int modeFlagsSize = buffer.readByte() + 4;
                                if (modeFlagsSize > 0) {
                                    modeFlags = new byte[modeFlagsSize];
                                    modeFlags[0] = (byte) (firstModeFlag & 0xFF);
                                    modeFlags[1] = (byte) (firstModeFlag >> 8 & 0xFF);
                                    modeFlags[2] = (byte) (firstModeFlag >> 16 & 0xFF);
                                    modeFlags[3] = (byte) (firstModeFlag >> 24 & 0xFF);
                                    for (int i = 4; i < modeFlagsSize; i++) {
                                        modeFlags[i] = buffer.readByte();
                                    }
                                    this.modeFlagsBtfl = modeFlags;
                                }
                                telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                                break;
                            }
                        }
                        break;
                    }
                    case FcCommon.MSP_ATTITUDE:
                    case FcCommon.MSP_ALTITUDE:
                    case FcCommon.MSP_ANALOG:
                    case FcCommon.MSP_VTX_CONFIG:
                    case FcCommon.MSP_BATTERY_STATE:
                    case FcCommon.MSP_RAW_GPS:
                    case FcCommon.MSP_COMP_GPS:
                    case FcCommon.MSP2_INAV_ANALOG: {
                        telemetryOutputBuffer.offer(new TelemetryData(packet.code, buffer.getData()));
                        break;
                    }
                }
            } catch (Exception e) {
                log("MSP - processData error: " + e + ", MSP code: " + packet.code);
            }
        }
    }

    private void checkModeFlags(){
        boolean isArmed = false;
        boolean isCamSwitch = false;
        FcCommon.BoxMode[] activeBoxModes = null;
        switch (fcVariant){
            case FcInfo.FC_VARIANT_INAV:
                activeBoxModes = FcCommon.getActiveBoxesInav(modeFlagsInav, boxIds);
                break;
            case FcInfo.FC_VARIANT_BETAFLIGHT:
                activeBoxModes = FcCommon.getActiveBoxesBtfl(modeFlagsBtfl, boxIds);
                break;
        }
        if (activeBoxModes != null) {
            for (FcCommon.BoxMode box : activeBoxModes) {
                if (box.boxId == FcCommon.BoxModeIds.BOXARM) isArmed = true;
                if (box.boxId == FcCommon.BoxModeIds.BOXCAMERA2) isCamSwitch = true;
            }
        }

        if (isArmed){
            if (flyTimestamp == 0) flyTimestamp = System.currentTimeMillis();
        }else{
            if (flyTimestamp != 0) flyTimeSave = flyTimeSave + (int) (System.currentTimeMillis() - flyTimestamp) / 1000;
            flyTimestamp = 0;
        }

        if (isCamSwitch && !oldCamSwitchState){
            telemetryOutputBuffer.offer(new TelemetryData(FcCommon.DD_VIDEO_RECORDER_START_STOP, new byte[1]));
        }
        oldCamSwitchState = isCamSwitch;
    }

    private void processOnTimeFlyTime(){ // we don't have these values via MSP
        long current = System.currentTimeMillis();
        if (onTimestamp != 0) onTime = (int) (current - onTimestamp) / 1000;
        if (flyTimestamp != 0) {
            lastArmTime = (int) (current - flyTimestamp) / 1000;
            flyTime = flyTimeSave + lastArmTime;
        }
        DataWriter writer = new DataWriter(false);
        writer.writeInt(onTime);
        writer.writeInt(flyTime);
        writer.writeInt(lastArmTime);
        telemetryOutputBuffer.offer(new TelemetryData(FcCommon.DD_TIMERS, writer.getData()));
    }

    public void setRawRc(short[] rcChannels){
        if (rcChannels == null || rcChannels.length > FcCommon.MAX_SUPPORTED_RC_CHANNEL_COUNT) return;
        long current = System.currentTimeMillis();
        if (current - rcLastFrame < rcMinPeriod){
            rcLastFrame = current;
            return;
        }
        rcLastFrame = current;
        DataWriter writer = new DataWriter(false);
        short[] mappedChannels = processRxMap(rcChannels);
        for (short rcChannel : mappedChannels) {
            writer.writeShort(rcChannel);
        }
        serial.writeDataMsp(getMspRequestWithPayload(FcCommon.MSP_SET_RAW_RC, writer.getData()), true);
    }
    
    private short[] processRxMap(short[] rcChannels){
        if (rxMap == null || rxMap.length < 4) return rcChannels;
        short[] mappedChannels = rcChannels.clone();
        for (int i = 0; i < rxMap.length; i++) {
            if (rxMap[i] < 0 || rxMap[i] >= rcChannels.length) return rcChannels;
            mappedChannels[rxMap[i]] = rcChannels[i];
        }
        return mappedChannels;
    }

    public void getBatteryState(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_BATTERY_STATE), true);
    }

    public void getRxMap(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_RX_MAP), true);
    }

    public void getInavStatus(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP2_INAV_STATUS), true);
    }

    public void getInavAnalog(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP2_INAV_ANALOG), true);
    }

    public void getAttitude(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_ATTITUDE), true);
    }

    public void getAltitude(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_ALTITUDE), true);
    }

    public void getAnalog(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_ANALOG), true);
    }

    public void getOsdConfig(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_OSD_CONFIG), true);
    }

    public void getBatteryConfig(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_BATTERY_CONFIG), true);
    }

    public void getVtxConfig(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_VTX_CONFIG), true);
    }

    public void getBoxNames(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_BOXNAMES), true);
    }

    public void getBoxNames(byte page){
        byte[] data = getMspRequestWithPayload(FcCommon.MSP_BOXNAMES, new byte[] { page });
        serial.writeDataMsp(data, true);
    }

    public void getBoxIds(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_BOXIDS), true);
    }

    public void getBoxIds(byte page){
        byte[] data = getMspRequestWithPayload(FcCommon.MSP_BOXIDS, new byte[] { page });
        serial.writeDataMsp(data, true);
    }

    public void getStatus(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_STATUS), true);
    }

    public void getRawGps(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_RAW_GPS), true);
    }

    public void getCompGps(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_COMP_GPS), true);
    }

    public void getOsdCanvas(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_OSD_CANVAS), true);
    }

    public void getFcVersion(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_FC_VERSION), false);
    }

    public void getFcVariant(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_FC_VARIANT), false);
    }

    public void getMspApiVersion(){
        serial.writeDataMsp(getMspRequest(FcCommon.MSP_API_VERSION), false);
    }

    public void getMixerConfig(){
        switch (fcVariant){
            case FcInfo.FC_VARIANT_INAV:
                serial.writeDataMsp(getMspRequest(FcCommon.MSP2_INAV_MIXER), false);
                break;
            case FcInfo.FC_VARIANT_BETAFLIGHT:
                serial.writeDataMsp(getMspRequest(FcCommon.MSP_MIXER_CONFIG), false);
                break;
        }
    }

    private byte[] getMspRequest(short cmd){
        byte[] data = {MSP_HEADER_START, MSP_HEADER_V2, MSP_HEADER_REQUEST, 0, (byte) (cmd & 0xFF), (byte) (cmd >> 8 & 0xFF), 0, 0, 0};
        setCrcV2(data);
        return data;
    }

    private byte[] getMspRequestWithPayload(short cmd, byte[] payload){
        int payloadSize = payload.length;
        int totalSize = payloadSize + MSP_V2_MIN_REQUEST_SIZE;
        byte[] data = new byte[totalSize];
        byte[] header = {MSP_HEADER_START, MSP_HEADER_V2, MSP_HEADER_REQUEST, 0, (byte) (cmd & 0xFF), (byte) (cmd >> 8 & 0xFF), (byte) (payloadSize & 0xFF), (byte) (payloadSize >> 8 & 0xFF)};
        System.arraycopy(header, 0, data, 0, MSP_V2_HEADER_SIZE);
        System.arraycopy(payload, 0, data, MSP_V2_HEADER_SIZE, payloadSize);
        setCrcV2(data);
        return data;
    }

    private void setCrcV2(byte[] data){
        if (data == null || data.length < MSP_V2_MIN_REQUEST_SIZE) return;
        data[data.length-1] = (byte)calculateCrcV2(data, data.length);
    }

    private int calculateCrcV2(byte[] data, int dataLength){
        int crc = 0;
        for (int i = 3; i < dataLength-1; i++) {
            crc = crc8_dvb_s2(crc, data[i]);
        }
        return crc;
    }

    private int calculateCrcV2(byte flag, int code, int payloadSize, byte[] payload){
        int crc = 0;
        crc = crc8_dvb_s2(crc, flag);
        crc = crc8_dvb_s2(crc, code & 0xFF);
        crc = crc8_dvb_s2(crc, code >> 8 & 0xFF);
        crc = crc8_dvb_s2(crc, payloadSize & 0xFF);
        crc = crc8_dvb_s2(crc, payloadSize >> 8 & 0xFF);
        if (payloadSize > 0){
            for (int i = 0; i < payloadSize; i++) {
                crc = crc8_dvb_s2(crc, payload[i]);
            }
        }
        return crc;
    }

    private int calculateCrcV1(int code, int payloadSize, byte[] payload){
        int crc = 0;
        crc ^= code;
        crc ^= payloadSize;
        if (payloadSize > 0){
            for (int i = 0; i < payloadSize; i++) {
                crc ^= payload[i];
            }
        }
        return crc;
    }

    private int crc8_dvb_s2(int crc, int a) {
        crc ^= a;
        for (int i = 0; i < 8; i++) {
            if ((crc & 0x80) != 0) {
                crc = (crc << 1) ^ 0xD5;
            } else {
                crc = crc << 1;
            }
        }
        crc &= 0xFF;
        return crc;
    }
}
