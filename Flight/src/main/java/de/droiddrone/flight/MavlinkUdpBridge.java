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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.droiddrone.common.SettingsCommon;
import de.droiddrone.common.UdpCommon;

public class MavlinkUdpBridge {
    private final byte[] receiverBuf = new byte[UdpCommon.packetLength];
    private final ConcurrentLinkedQueue<byte[]> receivedPackets = new ConcurrentLinkedQueue<>();
    private final Config config;
    private final Mavlink mavlink;
    private Udp udp;
    private DatagramSocket socket;
    private DatagramPacket receiverPacket;
    private InetAddress ip;
    private int port;
    private boolean initialized;
    private int threadsId;

    public MavlinkUdpBridge(Config config, Mavlink mavlink) {
        this.config = config;
        this.mavlink = mavlink;
        initialized = false;
    }

    public void setUdp(Udp udp){
        this.udp = udp;
    }

    public void initialize(){
        if (udp == null || config.getMavlinkUdpBridge() == SettingsCommon.MavlinkUdpBridge.disabled) return;
        if (config.getMavlinkUdpBridge() == SettingsCommon.MavlinkUdpBridge.connectedIp) {
            ip = udp.getSenderIp();
        }else{
            try {
                ip = InetAddress.getByName(config.getMavlinkUdpBridgeIp());
            } catch (UnknownHostException e) {
                log("MavlinkUdpBridge InetAddress error: " + e);
                return;
            }
        }
        if (ip == null) return;
        port = config.getMavlinkUdpBridgePort();
        try {
            if (socket != null) socket.close();
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(UdpCommon.packetLength * 2);
            socket.setSendBufferSize(UdpCommon.packetLength * 2);
            receiverPacket = new DatagramPacket(receiverBuf, receiverBuf.length);
            threadsId++;
            Thread receiverThread = new Thread(receiverRun);
            receiverThread.setDaemon(false);
            receiverThread.setName("mavlinkUdpReceiverThread");
            receiverThread.setPriority(Thread.MAX_PRIORITY);
            receiverThread.start();
            Thread bufferThread = new Thread(bufferRun);
            bufferThread.setDaemon(false);
            bufferThread.setName("mavlinkUdpBufferThread");
            bufferThread.start();
            initialized = true;
        } catch (Exception e) {
            log("MavlinkUdpBridge initialize error: " + e);
            initialized = false;
        }
    }

    private final Runnable receiverRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            while (socket != null && !socket.isClosed() && id == threadsId) {
                try {
                    socket.receive(receiverPacket);
                    // check IP & port
                    if (!receiverPacket.getAddress().equals(ip)
                            || receiverPacket.getPort() != port) continue;
                    addPacket(receiverPacket);
                } catch (Exception e) {
                    log("Mavlink UDP receiver error: " + e);
                }
            }
        }
    };

    private void addPacket(DatagramPacket packet) {
        if (!initialized) return;
        int packetSize = packet.getLength();
        if (packetSize < MAVLinkPacket.MAVLINK2_NONPAYLOAD_LEN) return;
        byte[] tmp = packet.getData();
        byte[] data = new byte[packetSize];
        System.arraycopy(tmp, 0, data, 0, packetSize);
        receivedPackets.add(data);
    }

    private byte[] getNextPacket(){
        if (!initialized) return null;
        return receivedPackets.poll();
    }

    private final Runnable bufferRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            byte[] packet;
            while (socket != null && !socket.isClosed() && id == threadsId) {
                try {
                    do{
                        packet = getNextPacket();
                        if (packet != null) mavlink.processReceivedUdpData(packet);
                    }while (packet != null);
                    Thread.sleep(1);
                } catch (Exception e) {
                    log("Mavlink UDP buffer error: " + e);
                }
            }
        }
    };

    public void sendPacket(byte[] data){
        if (data == null || !initialized || socket == null || socket.isClosed()) return;
        try {
            DatagramPacket pak = new DatagramPacket(data, data.length, ip, port);
            socket.send(pak);
        } catch (Exception e) {
            log("MavlinkUdpBridge - sendPacket error: " + e);
        }
    }

    public boolean isInitialized(){
        return initialized;
    }

    public void close(){
        initialized = false;
        threadsId++;
        ip = null;
        try {
            if (socket != null) socket.close();
        }catch (Exception ignored){}
        receivedPackets.clear();
    }
}
