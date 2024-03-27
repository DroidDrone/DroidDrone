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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReceiverBuffer {
    private final boolean isServer;
    private final UdpSender udpSender;
    private final String controlKey, viewerKey;
    private final int disconnectTimeMs = 4000;
    private final ConcurrentHashMap<Short, SavedPacket> numberedBuffer = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SavedPacket> unnumberedBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SavedPacket> rejectedPackets = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Short, Long> lastRequestedPackets = new ConcurrentHashMap<>();
    private short nextPacketNum;
    private int bufferSize, packetCount, packetTimer, pingTimer;
    private final ArrayList<Integer> bufferSizes = new ArrayList<>();
    private final ArrayList<Integer> pings = new ArrayList<>();
    private int pingMs;
    private int lastPacketTimer;
    private boolean isActive;
    private boolean isConnected;
    private int recoverCounter;
    private final long createdTimestamp;

    public ReceiverBuffer(UdpSender udpSender, boolean isServer, String controlKey, String viewerKey){
        this.udpSender = udpSender;
        this.isServer = isServer;
        this.controlKey = controlKey;
        this.viewerKey = viewerKey;
        bufferSize = 30;
        packetCount = 0;
        packetTimer = 0;
        pingTimer = 0;
        nextPacketNum = 0;
        pingMs = 100;
        lastPacketTimer = 0;
        recoverCounter = 0;
        isActive = true;
        isConnected = false;
        createdTimestamp = System.currentTimeMillis();
    }

    public long getCreatedTimestamp(){
        return createdTimestamp;
    }

    public void addPacket(DatagramPacket packet) {
        if (!isActive) return;
        int packetSize = packet.getLength();
        InetAddress ip = packet.getAddress();
        int port = packet.getPort();
        if (packetSize < 1) return;
        byte[] tmp = packet.getData();
        byte[] data = new byte[packetSize];
        System.arraycopy(tmp, 0, data, 0, packetSize);
        DataReader buffer = new DataReader(data, true);
        byte packetName = buffer.readByte();
        if (UdpCommon.isPacketNumbered(packetName)){
            short num = buffer.readShort();
            if (num < -1) return;
            if (packetName == UdpCommon.Connect){
                if (isConnected()) return;
                byte clientType = buffer.readByte();
                String key = buffer.readUTF();
                if (key == null) return;
                if ((clientType == 0 || clientType == 1) && key.equals(controlKey) || clientType == 2 && key.equals(viewerKey)){
                    restart();
                    udpSender.connect(ip, port);
                    isConnected = true;
                }else{
                    return;
                }
            }
            if (!isConnected) return;
            lastPacketTimer = disconnectTimeMs;
            int maxPacketNumDiff = 1000;
            if (num >= nextPacketNum && num < nextPacketNum + maxPacketNumDiff || num < nextPacketNum - Short.MAX_VALUE + maxPacketNumDiff){
                numberedBuffer.put(num, new SavedPacket(packetName, num, data, ip, port));
                packetCount++;
                recoverCounter = 0;
            }else{
                rejectedPackets.add(new SavedPacket(packetName, num, data, ip, port));
                recoverCounter++;
                if (recoverCounter > bufferSize * 2){
                    restart();
                    numberedBuffer.put(num, new SavedPacket(packetName, num, data, ip, port));
                    nextPacketNum = num;
                }
            }
        }else{
            if (!isConnected) return;
            lastPacketTimer = disconnectTimeMs;
            unnumberedBuffer.add(new SavedPacket(packetName, (short) -1, data, ip, port));
        }
    }

    public SavedPacket getNextPacket(){
        if (!isActive) return null;
        SavedPacket packet = unnumberedBuffer.poll();
        if (packet != null){
            if (checkPacket(packet)) {
                return packet;
            }else{
                return getNextPacket();
            }
        }
        packet = numberedBuffer.remove(nextPacketNum);
        if (packet == null){
            if (numberedBuffer.isEmpty()) return null;
            if (numberedBuffer.size() >= bufferSize){
                short minNum = -1;
                for (ConcurrentHashMap.Entry<Short, SavedPacket> entry : numberedBuffer.entrySet()) {
                    short key = entry.getKey();
                    if (key < minNum || minNum == -1) minNum = key;
                }
                if (minNum != -1){
                    nextPacketNum = minNum;
                    return getNextPacket();
                }
            }
            ArrayList<Integer> requestedPackets = new ArrayList<>();
            int num;
            for (int i = 0; i < 10; i++) {
                num = nextPacketNum + i;
                if (num > Short.MAX_VALUE) num = num - Short.MAX_VALUE - 1;
                if (numberedBuffer.containsKey((short)num)) break;
                if (lastRequestedPackets.containsKey((short)num)) continue;
                requestedPackets.add(num);
                lastRequestedPackets.put((short)num, System.currentTimeMillis() + pingMs + 20);
            }
            udpSender.requestPacket(requestedPackets);
        }else{
            if (nextPacketNum == Short.MAX_VALUE){
                nextPacketNum = 0;
            }else{
                nextPacketNum++;
            }
            if (checkPacket(packet)) {
                return packet;
            }else{
                return getNextPacket();
            }
        }
        return null;
    }

    private boolean checkPacket(SavedPacket packet){
        try {
            DataReader buffer = new DataReader(packet.data, true);
            byte packetName = buffer.readByte();
            short num;
            if (UdpCommon.isPacketNumbered(packetName)){
                num = buffer.readShort();
                if (UdpCommon.isSendPacketReceived(packetName)) udpSender.sendPacketReceived(num);
            }
            switch (packetName) {
                case UdpCommon.Ping:
                case UdpCommon.Pong: {
                    boolean toEndPoint = buffer.readBoolean();
                    long time = buffer.readLong();
                    byte target = -1;
                    if (buffer.getRemaining() > 0) target = buffer.readByte();
                    if (toEndPoint && isServer) return true;
                    if (packetName == UdpCommon.Ping) {
                        udpSender.sendPong(toEndPoint, time, target);
                    } else {
                        if (toEndPoint) {
                            return true;
                        } else {
                            calculatePing(time);
                        }
                    }
                    return false;
                }
                case UdpCommon.PacketReceived:
                {
                    num = buffer.readShort();
                    udpSender.removePacket(num);
                    return false;
                }
                case UdpCommon.RequestPackets:
                {
                    int c = buffer.getRemaining() / 2;
                    for (int i = 0; i < c; i++) {
                        num = buffer.readShort();
                        udpSender.resendPacket(num);
                    }
                    return false;
                }
                case UdpCommon.Connect:
                {
                    if (isServer) udpSender.sendPacket(packet.data);
                    return false;
                }
            }
        }catch (Exception e){
            log("checkPacket error: " + e);
        }
        return true;
    }

    public void processTimer(){
        if (!isActive) return;
        bufferCleanup();
        processLastPacketTimer();
        processPing();
        calculateBufferSize();
        processRejectedPackets();
    }

    private void bufferCleanup(){
        ArrayList<Short> keysToRemove = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        for (ConcurrentHashMap.Entry<Short, Long> entry : lastRequestedPackets.entrySet()) {
            short key = entry.getKey();
            long time = entry.getValue();
            if (currentTime >= time) keysToRemove.add(key);
        }
        for (short key : keysToRemove) lastRequestedPackets.remove(key);

        keysToRemove.clear();
        final int maxLatency = pingMs * 4;
        for (ConcurrentHashMap.Entry<Short, SavedPacket> entry : numberedBuffer.entrySet()) {
            short key = entry.getKey();
            SavedPacket packet = entry.getValue();
            if (currentTime >= packet.timestampCreated + UdpCommon.getPacketLifeTimeMs(packet.packetName) + maxLatency) keysToRemove.add(key);
        }
        for (short key : keysToRemove) {
            numberedBuffer.remove(key);
        }
    }

    private void processLastPacketTimer(){
        if (lastPacketTimer > 0) lastPacketTimer--;
    }

    private void processPing(){
        if (!isConnected()) return;
        pingTimer++;
        if (pingTimer >= 500){
            pingTimer = 0;
            udpSender.sendPing(false);
        }
    }

    private void calculateBufferSize(){
        packetTimer++;
        if (packetTimer >= pingMs){
            packetTimer = 0;
            int size = Math.round(packetCount * 1.5f) + 1;
            packetCount = 0;
            bufferSizes.add(size);
            if (bufferSizes.size() >= 10){
                int avgSize = 0;
                for (int c : bufferSizes) avgSize += c;
                avgSize = avgSize / bufferSizes.size();
                bufferSizes.remove(0);
                bufferSize = avgSize;
            }
        }
    }

    private void processRejectedPackets(){
        SavedPacket packet = rejectedPackets.poll();
        while (packet != null){
            if (UdpCommon.isPacketNumbered(packet.packetName) && UdpCommon.isSendPacketReceived(packet.packetName)){
                udpSender.sendPacketReceived(packet.packetNum);
            }
            packet = rejectedPackets.poll();
        }
    }

    private void calculatePing(long time){
        int ping = (int) (System.currentTimeMillis() - time);
        if (Math.abs(ping - pingMs) > 1000) return;
        pings.add(ping);
        int avgPing = 0;
        for (int p : pings) avgPing += p;
        avgPing = avgPing / pings.size();
        pingMs = avgPing;
        udpSender.setPing(avgPing);
        if (pings.size() >= 10) pings.remove(0);
    }

    public int getCurrentPingMs(){
        return pingMs;
    }

    public boolean isConnected(){
        return (isActive && isConnected && lastPacketTimer > 0);
    }

    private void restart(){
        nextPacketNum = -1;
        recoverCounter = 0;
        numberedBuffer.clear();
        unnumberedBuffer.clear();
        rejectedPackets.clear();
        lastRequestedPackets.clear();
    }

    public void close(){
        isActive = false;
        isConnected = false;
        lastPacketTimer = 0;
        numberedBuffer.clear();
        unnumberedBuffer.clear();
        rejectedPackets.clear();
        lastRequestedPackets.clear();
    }
}
