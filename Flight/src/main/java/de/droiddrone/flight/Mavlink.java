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
import com.MAVLink.common.msg_timesync;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.minimal.msg_heartbeat;

import java.util.ArrayList;
import java.util.List;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.FcInfo;

public class Mavlink {
    private final Serial serial;
    private final Config config;
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
        return (apiVersionMajor != -1 && apiVersionMinor != -1
                && fcVersionMajor != -1 && fcVersionMinor != -1 && fcVersionPatchLevel != -1);
    }

    private void setFcInfo(){
        fcInfo = new FcInfo(fcVariant, fcVersionMajor, fcVersionMinor, fcVersionPatchLevel, apiProtocolVersion, apiVersionMajor, apiVersionMinor);
        log(fcInfo.getFcName() + " Ver. " + fcInfo.getFcVersionStr() + " detected.");
        log("Mavlink API Ver.: " + fcInfo.getFcApiVersionStr());
    }

    public FcInfo getFcInfo(){
        return fcInfo;
    }

    public void initialize() {
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
                    if (isHeartBeatReceived && !isInitialized()) {
                        getFcVersion();
                        Thread.sleep(timerDelayMs);
                        continue;
                    }
                    Thread.sleep(timerDelayMs);
                } catch (Exception e) {
                    log("Mavlink thread error: " + e);
                }
            }
        }
    };

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
    }
}
