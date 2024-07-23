/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by the
 * java mavlink generator tool. It should not be modified by hand.
 */

// MESSAGE MISSION_CURRENT PACKING
package com.MAVLink.common;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Messages.MAVLinkPayload;
import com.MAVLink.Messages.Units;
import com.MAVLink.Messages.Description;

/**
 * 
        Message that announces the sequence number of the current target mission item (that the system will fly towards/execute when the mission is running).
        This message should be streamed all the time (nominally at 1Hz).
        This message should be emitted following a call to MAV_CMD_DO_SET_MISSION_CURRENT or SET_MISSION_CURRENT.
      
 */
public class msg_mission_current extends MAVLinkMessage {

    public static final int MAVLINK_MSG_ID_MISSION_CURRENT = 42;
    public static final int MAVLINK_MSG_LENGTH = 18;
    private static final long serialVersionUID = MAVLINK_MSG_ID_MISSION_CURRENT;

    
    /**
     * Sequence
     */
    @Description("Sequence")
    @Units("")
    public int seq;
    
    /**
     * Total number of mission items on vehicle (on last item, sequence == total). If the autopilot stores its home location as part of the mission this will be excluded from the total. 0: Not supported, UINT16_MAX if no mission is present on the vehicle.
     */
    @Description("Total number of mission items on vehicle (on last item, sequence == total). If the autopilot stores its home location as part of the mission this will be excluded from the total. 0: Not supported, UINT16_MAX if no mission is present on the vehicle.")
    @Units("")
    public int total;
    
    /**
     * Mission state machine state. MISSION_STATE_UNKNOWN if state reporting not supported.
     */
    @Description("Mission state machine state. MISSION_STATE_UNKNOWN if state reporting not supported.")
    @Units("")
    public short mission_state;
    
    /**
     * Vehicle is in a mode that can execute mission items or suspended. 0: Unknown, 1: In mission mode, 2: Suspended (not in mission mode).
     */
    @Description("Vehicle is in a mode that can execute mission items or suspended. 0: Unknown, 1: In mission mode, 2: Suspended (not in mission mode).")
    @Units("")
    public short mission_mode;
    
    /**
     * Id of current on-vehicle mission plan, or 0 if IDs are not supported or there is no mission loaded. GCS can use this to track changes to the mission plan type. The same value is returned on mission upload (in the MISSION_ACK).
     */
    @Description("Id of current on-vehicle mission plan, or 0 if IDs are not supported or there is no mission loaded. GCS can use this to track changes to the mission plan type. The same value is returned on mission upload (in the MISSION_ACK).")
    @Units("")
    public long mission_id;
    
    /**
     * Id of current on-vehicle fence plan, or 0 if IDs are not supported or there is no fence loaded. GCS can use this to track changes to the fence plan type. The same value is returned on fence upload (in the MISSION_ACK).
     */
    @Description("Id of current on-vehicle fence plan, or 0 if IDs are not supported or there is no fence loaded. GCS can use this to track changes to the fence plan type. The same value is returned on fence upload (in the MISSION_ACK).")
    @Units("")
    public long fence_id;
    
    /**
     * Id of current on-vehicle rally point plan, or 0 if IDs are not supported or there are no rally points loaded. GCS can use this to track changes to the rally point plan type. The same value is returned on rally point upload (in the MISSION_ACK).
     */
    @Description("Id of current on-vehicle rally point plan, or 0 if IDs are not supported or there are no rally points loaded. GCS can use this to track changes to the rally point plan type. The same value is returned on rally point upload (in the MISSION_ACK).")
    @Units("")
    public long rally_points_id;
    

    /**
     * Generates the payload for a mavlink message for a message of this type
     * @return
     */
    @Override
    public MAVLinkPacket pack() {
        MAVLinkPacket packet = new MAVLinkPacket(MAVLINK_MSG_LENGTH,isMavlink2);
        packet.sysid = sysid;
        packet.compid = compid;
        packet.msgid = MAVLINK_MSG_ID_MISSION_CURRENT;

        packet.payload.putUnsignedShort(seq);
        
        if (isMavlink2) {
             packet.payload.putUnsignedShort(total);
             packet.payload.putUnsignedByte(mission_state);
             packet.payload.putUnsignedByte(mission_mode);
             packet.payload.putUnsignedInt(mission_id);
             packet.payload.putUnsignedInt(fence_id);
             packet.payload.putUnsignedInt(rally_points_id);
            
        }
        return packet;
    }

    /**
     * Decode a mission_current message into this class fields
     *
     * @param payload The message to decode
     */
    @Override
    public void unpack(MAVLinkPayload payload) {
        payload.resetIndex();

        this.seq = payload.getUnsignedShort();
        
        if (isMavlink2) {
             this.total = payload.getUnsignedShort();
             this.mission_state = payload.getUnsignedByte();
             this.mission_mode = payload.getUnsignedByte();
             this.mission_id = payload.getUnsignedInt();
             this.fence_id = payload.getUnsignedInt();
             this.rally_points_id = payload.getUnsignedInt();
            
        }
    }

    /**
     * Constructor for a new message, just initializes the msgid
     */
    public msg_mission_current() {
        this.msgid = MAVLINK_MSG_ID_MISSION_CURRENT;
    }

    /**
     * Constructor for a new message, initializes msgid and all payload variables
     */
    public msg_mission_current( int seq, int total, short mission_state, short mission_mode, long mission_id, long fence_id, long rally_points_id) {
        this.msgid = MAVLINK_MSG_ID_MISSION_CURRENT;

        this.seq = seq;
        this.total = total;
        this.mission_state = mission_state;
        this.mission_mode = mission_mode;
        this.mission_id = mission_id;
        this.fence_id = fence_id;
        this.rally_points_id = rally_points_id;
        
    }

    /**
     * Constructor for a new message, initializes everything
     */
    public msg_mission_current( int seq, int total, short mission_state, short mission_mode, long mission_id, long fence_id, long rally_points_id, int sysid, int compid, boolean isMavlink2) {
        this.msgid = MAVLINK_MSG_ID_MISSION_CURRENT;
        this.sysid = sysid;
        this.compid = compid;
        this.isMavlink2 = isMavlink2;

        this.seq = seq;
        this.total = total;
        this.mission_state = mission_state;
        this.mission_mode = mission_mode;
        this.mission_id = mission_id;
        this.fence_id = fence_id;
        this.rally_points_id = rally_points_id;
        
    }

    /**
     * Constructor for a new message, initializes the message with the payload
     * from a mavlink packet
     *
     */
    public msg_mission_current(MAVLinkPacket mavLinkPacket) {
        this.msgid = MAVLINK_MSG_ID_MISSION_CURRENT;

        this.sysid = mavLinkPacket.sysid;
        this.compid = mavLinkPacket.compid;
        this.isMavlink2 = mavLinkPacket.isMavlink2;
        unpack(mavLinkPacket.payload);
    }

                  
    /**
     * Returns a string with the MSG name and data
     */
    @Override
    public String toString() {
        return "MAVLINK_MSG_ID_MISSION_CURRENT - sysid:"+sysid+" compid:"+compid+" seq:"+seq+" total:"+total+" mission_state:"+mission_state+" mission_mode:"+mission_mode+" mission_id:"+mission_id+" fence_id:"+fence_id+" rally_points_id:"+rally_points_id+"";
    }

    /**
     * Returns a human-readable string of the name of the message
     */
    @Override
    public String name() {
        return "MAVLINK_MSG_ID_MISSION_CURRENT";
    }
}
        