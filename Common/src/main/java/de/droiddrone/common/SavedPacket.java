package de.droiddrone.common;

import java.net.InetAddress;

public class SavedPacket {
    public final byte packetName;
    public final short packetNum;
    public final byte[] data;
    public final InetAddress ip;
    public final int port;
    public final long timestampCreated;
    public long timestamp;

    public SavedPacket(byte packetName, short packetNum, byte[] data, InetAddress ip, int port) {
        this.packetName = packetName;
        this.packetNum = packetNum;
        this.data = data;
        this.ip = ip;
        this.port = port;
        timestampCreated = System.currentTimeMillis();
        timestamp = timestampCreated;
    }
}
