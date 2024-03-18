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

import static de.droiddrone.common.Log.log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class UdpSender {
    private final DatagramSocket socket;
    private final Object packetNumberLock = new Object();
    private short nextPacketNumber;
    private final ConcurrentHashMap<Short, SavedPacket> savedPackets = new ConcurrentHashMap<>();
    private Timer timer;
    private InetAddress ip;
    private int port;
    private boolean active;
    private int pingMs;
    private int timerId;

    public UdpSender(DatagramSocket socket) {
        this.socket = socket;
        timerId = 0;
        pingMs = 100;
        nextPacketNumber = -1;
        active = false;
        ip = null;
        port = -1;
    }

    public void setAddress(InetAddress ip, int port){
        this.ip = ip;
        this.port = port;
    }

    public void connect(InetAddress ip, int port){
        setAddress(ip, port);
        synchronized (packetNumberLock) {
            nextPacketNumber = -1;
        }
        savedPackets.clear();
        active = true;
        timerId++;
        timer = new Timer();
        TimerTask tt = new TimerTask() {
            final int id = timerId;
            @Override
            public void run() {
                if (active && id == timerId) {
                    timerRun();
                }else{
                    timer.cancel();
                }
            }
        };
        timer.schedule(tt, 0, 10);
    }

    private void timerRun() {
        ArrayList<Short> keysToRemove = new ArrayList<>();
        long time = System.currentTimeMillis();
        for (ConcurrentHashMap.Entry<Short, SavedPacket> entry : savedPackets.entrySet()) {
            short key = entry.getKey();
            SavedPacket packet = entry.getValue();
            if (time >= packet.timestampCreated + UdpCommon.getPacketLifeTimeMs(packet.packetName)) keysToRemove.add(key);
            if (UdpCommon.isSendPacketReceived(packet.packetName) && time >= packet.timestamp + (long)(pingMs * 1.2f) + 5){
                packet.timestamp = time;
                resendPacket(key);
            }
        }
        for (short key : keysToRemove) savedPackets.remove(key);
    }
    
    public InetAddress getIp(){
    	return this.ip;
    }
    
    public int getPort(){
    	return this.port;
    }

    public void setPing(int pingMs){
        if (pingMs < 0 || pingMs > 1000) return;
        this.pingMs = pingMs;
    }

    public void sendPacket(byte[] data){
        if (data == null || !active || socket == null || socket.isClosed()) return;
        int size = data.length;
        if (size == 0) return;
        byte packetName = data[0];
        short num = -1;
        boolean isNumbered = UdpCommon.isPacketNumbered(packetName);
        if (isNumbered && packetName != UdpCommon.Connect){
            if (size < 3) return;
            num = getNextPacketNumber();
        }
        for (int i = 0; i < 10; i++) {
            try {
                if (isNumbered) {
                    byte[] numBytes = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(num).array();
                    data[1] = numBytes[0];
                    data[2] = numBytes[1];
                    savedPackets.put(num, new SavedPacket(packetName, num, data, ip, port));
                }
                DatagramPacket pak = new DatagramPacket(data, size, ip, port);
                socket.send(pak);
                break;
            }catch (Exception e){
                e.printStackTrace();
                log("sendPacket error: " + e);
            }
        }
    }

    public void requestPacket(ArrayList<Integer> packetNumbers){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.RequestPackets);
            for (int num : packetNumbers) {
                packetData.daos.writeShort(num);
            }
            sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("requestPacket error: " + e);
        }
    }

    public void resendPacket(short packetNumber){
        if (!active) return;
        try {
            SavedPacket packet = savedPackets.get(packetNumber);
            if (packet == null) return;
            byte[] data = packet.data;
            if (data == null || data.length == 0) return;
            DatagramPacket pak = new DatagramPacket(data, data.length, ip, port);
            socket.send(pak);
        } catch (Exception e) {
            e.printStackTrace();
            log("resendPacket error: " + e);
        }
    }

    public void removePacket(short packetNumber){
        savedPackets.remove(packetNumber);
    }

    public void sendConnect(int clientType, String key){
        Thread th = new Thread(() -> {
            try {
                UdpPacketData packetData = new UdpPacketData(UdpCommon.Connect, -1);
                packetData.daos.writeByte(clientType);
                packetData.daos.writeUTF(key);
                sendPacket(packetData.getData());
            } catch (Exception e) {
                e.printStackTrace();
                log("sendConnect error: " + e);
            }
        });
        th.start();
    }

    public void sendPing(boolean toEndPoint){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.Ping);
            packetData.daos.writeBoolean(toEndPoint);
            packetData.daos.writeLong(System.currentTimeMillis());
            sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendPing error: " + e);
        }
    }

    public void sendPingForViewer(){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.Ping);
            packetData.daos.writeBoolean(true);
            packetData.daos.writeLong(System.currentTimeMillis());
            packetData.daos.writeByte(0);//clientId placeholder
            sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendPingForViewer error: " + e);
        }
    }

    public void sendPong(boolean toEndPoint, long time, byte target){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.Pong);
            packetData.daos.writeBoolean(toEndPoint);
            packetData.daos.writeLong(time);
            if (target != -1) packetData.daos.writeByte(target);
            sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendPong error: " + e);
        }
    }

    public void sendPacketReceived(short num){
        try {
            UdpPacketData packetData = new UdpPacketData(UdpCommon.PacketReceived);
            packetData.daos.writeShort(num);
            sendPacket(packetData.getData());
        } catch (Exception e) {
            e.printStackTrace();
            log("sendPacketReceived error: " + e);
        }
    }

    private short getNextPacketNumber() {
        synchronized (packetNumberLock) {
            if (nextPacketNumber == Short.MAX_VALUE) nextPacketNumber = -1;
            nextPacketNumber++;
            return nextPacketNumber;
        }
    }

    public boolean isActive(){
        return active;
    }

    public void close(){
        active = false;
        timerId++;
        savedPackets.clear();
    }
}
