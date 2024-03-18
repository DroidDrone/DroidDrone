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
