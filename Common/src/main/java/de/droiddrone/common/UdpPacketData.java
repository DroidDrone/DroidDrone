package de.droiddrone.common;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UdpPacketData {
    private final ByteArrayOutputStream baos;
    public final DataOutputStream daos;

    public UdpPacketData(byte packetName) throws IOException {
        baos = new ByteArrayOutputStream();
        daos = new DataOutputStream(baos);
        boolean isNumbered = UdpCommon.isPacketNumbered(packetName);
        daos.writeByte(packetName);
        if (isNumbered) daos.writeShort(0);// number placeholder
    }

    public UdpPacketData(byte packetName, int num) throws IOException {
        baos = new ByteArrayOutputStream();
        daos = new DataOutputStream(baos);
        daos.writeByte(packetName);
        daos.writeShort(num);
    }

    public byte[] getData() throws IOException {
        daos.close();
        return baos.toByteArray();
    }
}
