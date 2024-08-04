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

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkPayload;
import com.MAVLink.common.msg_autopilot_version;
import com.MAVLink.common.msg_command_ack;
import com.MAVLink.common.msg_command_long;
import com.MAVLink.common.msg_param_request_read;
import com.MAVLink.common.msg_param_value;
import com.MAVLink.common.msg_timesync;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.minimal.msg_heartbeat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.DataWriter;
import de.droiddrone.common.FcCommon;
import de.droiddrone.common.OsdCommon;
import de.droiddrone.common.FcInfo;
import de.droiddrone.common.TelemetryData;

public class Mavlink {
    private final Serial serial;
    private final Config config;
    public final ArrayBlockingQueue<TelemetryData> telemetryOutputBuffer = new ArrayBlockingQueue<>(30);
    private final int systemId = 255;
    private final int componentId = 1;
    private final short targetSystem = 1;
    private final short targetComponent = 1;
    private final int fcVariant;
    private final int apiProtocolVersion;
    private int apiVersionMajor;
    private int apiVersionMinor;
    private int fcVersionMajor;
    private int fcVersionMinor;
    private int fcVersionPatchLevel;
    private FcInfo fcInfo;
    private boolean isMavlink2;
    private int threadsId;
    private boolean isHeartBeatReceived;
    private int sequence = 0;
    private boolean runGetOsdConfig;
    private FcParams fcParams;

    public Mavlink(Serial serial, Config config) {
        this.serial = serial;
        this.config = config;
        isMavlink2 = true;
        fcVariant = FcInfo.FC_VARIANT_ARDUPILOT;
        apiProtocolVersion = 0;
        apiVersionMajor = -1;
        apiVersionMinor = -1;
        fcVersionMajor = -1;
        fcVersionMinor = -1;
        fcVersionPatchLevel = -1;
        isHeartBeatReceived = false;
    }

    public boolean isInitialized(){
        boolean isInitialized = (apiVersionMajor != -1 && apiVersionMinor != -1
                && fcVersionMajor != -1 && fcVersionMinor != -1 && fcVersionPatchLevel != -1);
        if (isInitialized && fcInfo == null) setFcInfo();
        return isInitialized;
    }

    private void setFcInfo(){
        fcInfo = new FcInfo(fcVariant, fcVersionMajor, fcVersionMinor, fcVersionPatchLevel, apiProtocolVersion, apiVersionMajor, apiVersionMinor);
        log(fcInfo.getFcName() + " Ver. " + fcInfo.getFcVersionStr() + " detected.");
        log("Mavlink API Ver.: " + fcInfo.getFcApiVersionStr());
    }

    public FcInfo getFcInfo(){
        return fcInfo;
    }

    public void runGetOsdConfig(){
        runGetOsdConfig = true;
    }

    public void initialize() {
        fcParams = new FcParams(this);
        telemetryOutputBuffer.clear();
        threadsId++;
        Thread mavlinkThread = new Thread(mavlinkRun);
        mavlinkThread.setDaemon(false);
        mavlinkThread.setName("mavlinkThread");
        mavlinkThread.start();
    }

    private final Runnable mavlinkRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            final int timerDelayMs = 1000;
            log("Start Mavlink thread - OK");
            while (id == threadsId) {
                try {
                    if (!isInitialized()) {
                        if (isHeartBeatReceived) getFcVersion();
                        Thread.sleep(timerDelayMs);
                        continue;
                    }
                    if (runGetOsdConfig) {
                        if (fcParams.isOsdConfigInitialized()){
                            runGetOsdConfig = false;
                            fcParams.sendOsdConfig();
                        } else {
                            fcParams.initializeOsdConfig();
                        }
                    }
                    Thread.sleep(timerDelayMs);
                } catch (Exception e) {
                    log("Mavlink thread error: " + e);
                }
            }
        }
    };

    private void requestFcParameter(String paramIdStr){
        if (paramIdStr == null || paramIdStr.isEmpty() || paramIdStr.length() > 16) return;
        byte[] paramId = new byte[16];
        System.arraycopy(paramIdStr.getBytes(StandardCharsets.US_ASCII), 0, paramId, 0, paramIdStr.length());
        MAVLinkPacket packet = new msg_param_request_read((short)-1, targetSystem, targetComponent, paramId, systemId, componentId, isMavlink2).pack();
        packet.seq = getSequence();
        serial.writeDataMavlink(packet.encodePacket());
    }

    private void getFcVersion(){
        MAVLinkPacket packet = new msg_command_long(msg_autopilot_version.MAVLINK_MSG_ID_AUTOPILOT_VERSION, 0, 0, 0, 0, 0, 0,
                MAV_CMD.MAV_CMD_REQUEST_MESSAGE, targetSystem, targetComponent, (short)0, systemId, componentId, isMavlink2).pack();
        packet.seq = getSequence();
        serial.writeDataMavlink(packet.encodePacket());
    }

    public void processData(byte[] buf, int dataLength){
        if (dataLength < MAVLinkPacket.MAVLINK1_HEADER_LEN) return;
        byte[] data = new byte[dataLength];
        System.arraycopy(buf, 0, data, 0, dataLength);
        List<MAVLinkPacket> packets = parsePackets(data);
        if (packets == null || packets.isEmpty()) return;
        for (MAVLinkPacket packet : packets) {
            log("msgid: " + packet.msgid);
            switch (packet.msgid) {
                case msg_heartbeat.MAVLINK_MSG_ID_HEARTBEAT: {
                    if (isHeartBeatReceived) break;
                    msg_heartbeat message = new msg_heartbeat(packet);
                    isMavlink2 = message.isMavlink2;
                    apiVersionMajor = message.isMavlink2 ? 2 : 1;
                    apiVersionMinor = message.mavlink_version;
                    isHeartBeatReceived = true;
                    break;
                }
                case msg_timesync.MAVLINK_MSG_ID_TIMESYNC: {
                    msg_timesync message = new msg_timesync(packet);
                    log(message.toString());
                    break;
                }
                case msg_command_ack.MAVLINK_MSG_ID_COMMAND_ACK: {
                    msg_command_ack message = new msg_command_ack(packet);
                    log(message.toString());
                    break;
                }
                case msg_autopilot_version.MAVLINK_MSG_ID_AUTOPILOT_VERSION: {
                    msg_autopilot_version message = new msg_autopilot_version(packet);
                    fcVersionMajor = (int) (message.flight_sw_version >> 24 & 0xFF);
                    fcVersionMinor = (int) (message.flight_sw_version >> 16 & 0xFF);
                    fcVersionPatchLevel = (int) (message.flight_sw_version >> 8 & 0xFF);
                    byte fw_type = (byte) (message.flight_sw_version & 0xFF);
                    setFcInfo();
                    break;
                }
                case msg_param_value.MAVLINK_MSG_ID_PARAM_VALUE: {
                    msg_param_value message = new msg_param_value(packet);
                    log(message.toString());
                    if (fcParams == null) break;
                    fcParams.setParam(message.getParam_Id(), message.param_value);
                    break;
                }
            }
        }
    }

    private static class OsdItemParam{
        private final String osdItemName;
        private boolean isEnabled;
        private boolean isEnabledReceived;
        private byte x;
        private boolean isXReceived;
        private byte y;
        private boolean isYReceived;

        public OsdItemParam(String osdItemName){
            this.osdItemName = osdItemName;
            isEnabled = false;
            isEnabledReceived = false;
            x = 0;
            isXReceived = false;
            y = 0;
            isYReceived = false;
        }

        public String getOsdItemName(){
            return osdItemName;
        }

        public void setEnabled(boolean isEnabled){
            this.isEnabled = isEnabled;
            isEnabledReceived = true;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public void setX(byte x) {
            this.x = x;
            isXReceived = true;
        }

        public byte getX() {
            return x;
        }

        public void setY(byte y) {
            this.y = y;
            isYReceived = true;
        }

        public byte getY() {
            return y;
        }

        public boolean isInitialized(){
            return isEnabledReceived && isXReceived && isYReceived;
        }
    }

    private static class FcParams{
        private final Mavlink mavlink;
        private final OsdItemParam[] osdItems = new OsdItemParam[OsdCommon.AP_OSD_ITEMS.length];
        boolean osd1Enabled;
        boolean osd1EnabledReceived;
        byte osd1TxtRes;
        boolean osd1TxtResReceived;
        byte osdUnits;
        boolean osdUnitsReceived;
        byte osdMsgTime;
        boolean osdMsgTimeReceived;
        byte osdWarnRssi;
        boolean osdWarnRssiReceived;
        byte osdWarnNumSat;
        boolean osdWarnNumSatReceived;
        byte osdWarnBatVolt;
        boolean osdWarnBatVoltReceived;
        byte osdWarnAvgCellVolt;
        boolean osdWarnAvgCellVoltReceived;

        private FcParams(Mavlink mavlink) {
            this.mavlink = mavlink;
            int count = OsdCommon.AP_OSD_ITEMS.length;
            for (int i = 0; i < count; i++) {
                osdItems[i] = new OsdItemParam(OsdCommon.AP_OSD_ITEMS[i]);
            }
        }

        private String getOsdItemNameFromEn(String osdEnParam){
            return osdEnParam.substring(5, osdEnParam.length() - 3);
        }

        private String getOsdItemNameFromXY(String osdXYParam){
            return osdXYParam.substring(5, osdXYParam.length() - 2);
        }

        private void setOsdItemParam(String paramId, float paramValue){
            if (paramId == null) return;
            int l = paramId.length();
            if (l < 8) return;
            String osdItemName;
            OsdItemParam osdItemParam;
            if (paramId.substring(l - 3).equals("_EN")){
                osdItemName = getOsdItemNameFromEn(paramId);
                osdItemParam = getOsdItemParamFromName(osdItemName);
                if (osdItemParam == null) return;
                osdItemParam.setEnabled((int)paramValue != 0);
            } else if (paramId.substring(l - 2).equals("_X")){
                osdItemName = getOsdItemNameFromXY(paramId);
                osdItemParam = getOsdItemParamFromName(osdItemName);
                if (osdItemParam == null) return;
                osdItemParam.setX((byte)paramValue);
            } else if (paramId.substring(l - 2).equals("_Y")){
                osdItemName = getOsdItemNameFromXY(paramId);
                osdItemParam = getOsdItemParamFromName(osdItemName);
                if (osdItemParam == null) return;
                osdItemParam.setY((byte)paramValue);
            }
        }

        private OsdItemParam getOsdItemParamFromName(String osdItemName){
            for (OsdItemParam item : osdItems){
                if (item.getOsdItemName().equals(osdItemName)) return item;
            }
            return null;
        }

        public void initializeOsdConfig(){
            new Thread(() -> {
                if (!osd1EnabledReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD1_ENABLE);
                if (!osd1TxtResReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD1_TXT_RES);
                if (!osdUnitsReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_UNITS);
                if (!osdMsgTimeReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_MSG_TIME);
                if (!osdWarnRssiReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_W_RSSI);
                if (!osdWarnNumSatReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_W_NSAT);
                if (!osdWarnBatVoltReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_W_BATVOLT);
                if (!osdWarnAvgCellVoltReceived) mavlink.requestFcParameter(FcCommon.AP_PARAM_OSD_W_AVGCELLV);
                for (OsdItemParam item : osdItems){
                    if (item.isInitialized()) continue;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        //
                    }
                    String osdItemName = item.getOsdItemName();
                    if (!item.isEnabledReceived) mavlink.requestFcParameter("OSD1_" + osdItemName + "_EN");
                    if (!item.isXReceived) mavlink.requestFcParameter("OSD1_" + osdItemName + "_X");
                    if (!item.isYReceived) mavlink.requestFcParameter("OSD1_" + osdItemName + "_Y");
                }
            }).start();
        }

        public boolean isOsdConfigInitialized(){
            if (!osd1EnabledReceived || !osd1TxtResReceived || !osdUnitsReceived
                    || !osdMsgTimeReceived || !osdWarnRssiReceived || !osdWarnNumSatReceived
                    || !osdWarnBatVoltReceived || !osdWarnAvgCellVoltReceived) return false;
            for (OsdItemParam item : osdItems){
                if (!item.isInitialized()) {
                    return false;
                }
            }
            return true;
        }

        public void sendOsdConfig(){
            DataWriter buffer = new DataWriter(false);
            buffer.writeBoolean(osd1Enabled);
            buffer.writeByte(osd1TxtRes);
            buffer.writeByte(osdUnits);
            buffer.writeByte(osdMsgTime);
            buffer.writeByte(osdWarnRssi);
            buffer.writeByte(osdWarnNumSat);
            buffer.writeByte(osdWarnBatVolt);
            buffer.writeByte(osdWarnAvgCellVolt);
            int osdItemsCount = osdItems.length;
            buffer.writeByte((byte) osdItemsCount);
            for (OsdItemParam osdItem : osdItems) {
                buffer.writeBoolean(osdItem.isEnabled());
                buffer.writeByte(osdItem.getX());
                buffer.writeByte(osdItem.getY());
            }
            mavlink.telemetryOutputBuffer.offer(new TelemetryData(FcCommon.DD_AP_OSD_CONFIG, buffer.getData()));
        }

        public void setParam(String paramId, float paramValue){
            switch (paramId){
                case FcCommon.AP_PARAM_OSD1_ENABLE:
                    osd1Enabled = ((int)paramValue != 0);
                    osd1EnabledReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD1_TXT_RES:
                    osd1TxtRes = (byte)paramValue;
                    osd1TxtResReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_UNITS:
                    osdUnits = (byte)paramValue;
                    osdUnitsReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_MSG_TIME:
                    osdMsgTime = (byte)paramValue;
                    osdMsgTimeReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_W_RSSI:
                    osdWarnRssi = (byte)paramValue;
                    osdWarnRssiReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_W_NSAT:
                    osdWarnNumSat = (byte)paramValue;
                    osdWarnNumSatReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_W_BATVOLT:
                    osdWarnBatVolt = (byte)paramValue;
                    osdWarnBatVoltReceived = true;
                    break;
                case FcCommon.AP_PARAM_OSD_W_AVGCELLV:
                    osdWarnAvgCellVolt = (byte)paramValue;
                    osdWarnAvgCellVoltReceived = true;
                    break;
                default:
                    if (paramId.contains("OSD1_") &&
                            (paramId.contains("_EN") || paramId.contains("_X") || paramId.contains("_Y"))) {
                        setOsdItemParam(paramId, paramValue);
                    }
                    break;
            }
        }
    }

    private List<MAVLinkPacket> parsePackets(byte[] data){
        DataReader reader = new DataReader(data, false);
        List<MAVLinkPacket> packets = new ArrayList<>();
        while (reader.getRemaining() > 0) {
            int magic = reader.readUnsignedByteAsInt();
            int payloadLength = reader.readUnsignedByteAsInt();
            boolean isMavlink2 = false;
            if (magic == MAVLinkPacket.MAVLINK_STX_MAVLINK2) {
                isMavlink2 = true;
            } else if (magic != MAVLinkPacket.MAVLINK_STX_MAVLINK1) {
                return packets;
            }
            MAVLinkPacket packet = new MAVLinkPacket(payloadLength, isMavlink2);
            if (isMavlink2) {
                if (reader.getSize() < MAVLinkPacket.MAVLINK2_NONPAYLOAD_LEN) return packets;
                packet.incompatFlags = reader.readUnsignedByteAsInt();
                if (packet.incompatFlags != 0) return packets;
                packet.compatFlags = reader.readUnsignedByteAsInt();
                packet.seq = reader.readUnsignedByteAsInt();
                packet.sysid = reader.readUnsignedByteAsInt();
                packet.compid = reader.readUnsignedByteAsInt();
                packet.msgid = reader.readUnsignedInt24AsInt();
                byte[] payloadData = new byte[payloadLength];
                reader.read(payloadData, 0, payloadLength);
                packet.payload = new MAVLinkPayload();
                packet.payload.putArray(payloadData);
                int crc1 = reader.readUnsignedByteAsInt();
                int crc2 = reader.readUnsignedByteAsInt();
                if (!packet.generateCRC(payloadLength)) return packets;
                if (packet.crc.getLSB() != crc1 || packet.crc.getMSB() != crc2) return packets;
            } else {
                if (reader.getSize() < MAVLinkPacket.MAVLINK1_NONPAYLOAD_LEN) return packets;
                packet.seq = reader.readUnsignedByteAsInt();
                packet.sysid = reader.readUnsignedByteAsInt();
                packet.compid = reader.readUnsignedByteAsInt();
                packet.msgid = reader.readUnsignedByteAsInt();
                byte[] payloadData = new byte[payloadLength];
                reader.read(payloadData, 0, payloadLength);
                packet.payload = new MAVLinkPayload();
                packet.payload.putArray(payloadData);
                int crc1 = reader.readUnsignedByteAsInt();
                int crc2 = reader.readUnsignedByteAsInt();
                if (!packet.generateCRC(payloadLength)) return packets;
                if (packet.crc.getLSB() != crc1 || packet.crc.getMSB() != crc2) return packets;
            }
            packets.add(packet);
        }
        return packets;
    }

    private synchronized int getSequence(){
        int seq = sequence;
        sequence++;
        if (sequence >= 256) sequence = 0;
        return seq;
    }

    public void close(){
        threadsId++;
        apiVersionMajor = -1;
        apiVersionMinor = -1;
        fcVersionMajor = -1;
        fcVersionMinor = -1;
        fcVersionPatchLevel = -1;
        isHeartBeatReceived = false;
        fcParams = null;
        telemetryOutputBuffer.clear();
    }
}
