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

package de.droiddrone.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static de.droiddrone.common.Log.*;

import de.droiddrone.common.DataReader;
import de.droiddrone.common.ReceiverBuffer;
import de.droiddrone.common.SavedPacket;
import de.droiddrone.common.UdpCommon;
import de.droiddrone.common.UdpSender;

public class Udp {
	private final Config config;
	private final byte[] receiverBuf;
	private DatagramSocket socket;
	private Thread receiverThread, bufferThread;
	private DatagramPacket receiverPacket;
	private int threadsId = 0;
	private UdpSender[] senders;
	private ReceiverBuffer[] receiverBuffers;
	private int[] clientVersions;
	private int clientsCount;
	private boolean connected = false;
    
	public Udp(Config config) {
		receiverBuf = new byte[UdpCommon.packetLength];
		this.config = config;
	}
	
	public boolean initialize() {
        try {
            if (socket != null) socket.close();
            socket = new DatagramSocket(config.getPort());
            socket.setReceiveBufferSize(UdpCommon.packetLength * 300);
            socket.setSendBufferSize(UdpCommon.packetLength * 30);
            socket.setTrafficClass(0x10);
            receiverPacket = new DatagramPacket(receiverBuf, receiverBuf.length);
            log("Start UDP Socket. Port: " + config.getPort() + " - OK");
            clientsCount = config.getViewerCount() + 2;
            senders = new UdpSender[clientsCount];
            receiverBuffers = new ReceiverBuffer[clientsCount];
            clientVersions = new int[clientsCount];
            receiverThread = new Thread(receiverRun);
            receiverThread.setDaemon(false);
            receiverThread.setName("receiverThread");
            receiverThread.start();
            bufferThread = new Thread(bufferRun);
            bufferThread.setDaemon(false);
            bufferThread.setName("bufferThread");
            bufferThread.start();
            return true;
        } catch (Exception e) {
        	timeLog("Start UDP Socket - error: " + e.toString());
            e.printStackTrace();
            return false;
        }
    }
	
	private final Runnable receiverRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            if (receiverThread == null) return;
            receiverThread.setPriority(Thread.MAX_PRIORITY);
            log("Start receiver thread - OK");
            while (socket != null && !socket.isClosed() && id == threadsId) {
                try {
                	socket.receive(receiverPacket);
                    int packetSize = receiverPacket.getLength();
                    InetAddress ip = receiverPacket.getAddress();
                    int port = receiverPacket.getPort();
                    if (packetSize < 1) continue;
                    byte[] tmp = receiverPacket.getData();
                    byte[] data = new byte[packetSize];
                    System.arraycopy(tmp, 0, data, 0, packetSize);
                    DataReader buffer = new DataReader(data, true);
                    byte packetName = buffer.readByte();
                    int clientId = getClientIndex(ip, port);
                    if (packetName == UdpCommon.Connect){
                    	if (clientId != -1 && senders[clientId].isActive() && receiverBuffers[clientId] != null) {
                    		receiverBuffers[clientId].addPacket(receiverPacket);
                    		continue;
                    	}
                    	buffer.readShort();//num
                    	byte type = buffer.readByte();
                    	String key = buffer.readUTF();
                    	short version = buffer.readShort();
                    	switch (type) {
                        	case 0://drone
                        	case 1://control
                        	{
                        		if (!key.equals(config.getKey())) break;
                        		if (senders[type] != null && senders[type].isActive()) break;
                        		senders[type] = new UdpSender(socket);
                        		senders[type].setAddress(ip, port);
                        		if (receiverBuffers[type] != null) receiverBuffers[type].close();
                        		receiverBuffers[type] = new ReceiverBuffer(senders[type], true, config.getKey(), config.getViewerKey());
                        		clientVersions[type] = version;
                        		connected = true;
                        		clientId = type;
                        		if (type == 0) timeLog("Drone is connected. IP: " + ip + ", port: " + port);
                        		if (type == 1) timeLog("Controller is connected. IP: " + ip + ", port: " + port);
                        		break;
                        	}
                        	case 2://viewer
                        	{
                        		if (!key.equals(config.getViewerKey())) break;
                        		int viewerId = getFreeViewerIndex();
                        		if (viewerId == -1) break;
                        		senders[viewerId] = new UdpSender(socket);
                        		senders[viewerId].setAddress(ip, port);
                        		if (receiverBuffers[viewerId] != null) receiverBuffers[viewerId].close();
                        		receiverBuffers[viewerId] = new ReceiverBuffer(senders[viewerId], true, config.getKey(), config.getViewerKey());
                        		clientVersions[viewerId] = version;
                        		connected = true;
                        		clientId = viewerId;
                        		timeLog("Viewer " + (viewerId - 1) + " is connected. IP: " + ip + ", port: " + port);
                        		break;
                        	}
                    	}
                    }
                    if (packetName == UdpCommon.Disconnect && clientId != -1){
                    	disconnectClient(clientId);
                		continue;
                    }
                    if (clientId != -1 && receiverBuffers[clientId] != null) {
                    	receiverBuffers[clientId].addPacket(receiverPacket);
                    }
                } catch (IOException e) {
                	timeLog("UDP Socket: " + e.toString());
                } catch (Exception e) {
                	timeLog("UDP Socket error: " + e.toString());
                }
            }
            close();
        }
	};
	
	private final Runnable bufferRun = new Runnable() {
        public void run() {
            final int id = threadsId;
            if (bufferThread == null) return;
            log("Start buffer thread - OK");
            while (socket != null && !socket.isClosed() && id == threadsId) {
                try {
                    SavedPacket packet;
                    long timestamp = System.currentTimeMillis();
                    for (int i = 0; i < clientsCount; i++){
                    	if (receiverBuffers[i] == null) continue;
                    	do{
                            packet = receiverBuffers[i].getNextPacket();
                            if (packet != null) processData(packet, i);
                        }while (packet != null);
                        receiverBuffers[i].processTimer();
                        if (!receiverBuffers[i].isConnected() && timestamp > receiverBuffers[i].getCreatedTimestamp() + 10000) {
                        	disconnectClient(i);
                        }
                    }
                    Thread.sleep(1);
                } catch (Exception e) {
                    log("Receiver buffer error: " + e);
                }
            }
        }
    };
	
	private void processData(SavedPacket packet, int clientId) {
		switch (packet.packetName) {
			case UdpCommon.StartVideo:
			case UdpCommon.ChangeBitRate:
			case UdpCommon.StartStopRecording:
			case UdpCommon.Config:
			case UdpCommon.RcFrame:
				// send to drone from controller only
				if (clientId != 1) break;
				if (senders[0] != null) senders[0].sendPacket(packet.data);
				break;
			case UdpCommon.GetVideoConfig:
			case UdpCommon.OsdConfig:
			case UdpCommon.BatteryConfig:
			case UdpCommon.BoxIds:
			case UdpCommon.BoxNames:
				// send to drone
				if (senders[0] != null) senders[0].sendPacket(packet.data);
				break;
			case UdpCommon.ConfigReceived:
				// send to controller only
				if (clientId != 0) break;
				if (senders[1] != null) senders[1].sendPacket(packet.data);
				break;
			case UdpCommon.Ping:
				if (packet.data.length > 10) {
					packet.data[10] = (byte) clientId;
				}
				if (clientId == 0 && senders[1] != null) senders[1].sendPacket(packet.data);
				if (clientId >= 1 && senders[0] != null) senders[0].sendPacket(packet.data);
			break;
			case UdpCommon.Pong:
				if (packet.data.length > 10) {
					byte target = packet.data[10];
					if (target >= 0 && target < clientsCount && senders[target] != null) {
						senders[target].sendPacket(packet.data);
					}
					break;
				}
				if (clientId == 0 && senders[1] != null) senders[1].sendPacket(packet.data);
				if (clientId >= 1 && senders[0] != null) senders[0].sendPacket(packet.data);
			break;
			case UdpCommon.FcInfo:
				if (clientId >= 1 && senders[0] != null) {
					senders[0].sendPacket(packet.data);
					break;
				}
			default:
				for (int i = 0; i < clientsCount; i++) {
					if (i == clientId || senders[i] == null || !senders[i].isActive()) continue;
					senders[i].sendPacket(packet.data);
				}
		}
	}
	
	private void disconnectClient(int clientId) {
		if (senders[clientId] != null) senders[clientId].close();
		if (receiverBuffers[clientId] != null) receiverBuffers[clientId].close();
		senders[clientId] = null;
		receiverBuffers[clientId] = null;
		clientVersions[clientId] = 0;
		if (clientId == 0) timeLog("Drone is disconnected.");
		if (clientId == 1) timeLog("Controller is disconnected.");
		if (clientId > 1) timeLog("Viewer " + (clientId - 1) + " is disconnected.");
	}
	
	public boolean isConnected(){
		return this.connected;
	}
	
	private int getFreeViewerIndex() {
		int maxCount = config.getViewerCount();
		if (maxCount <= 0) return -1;
		for (int i = 2; i < clientsCount; i++) {
			if (senders[i] == null) return i;
		}
		return -1;
	}
	
	private int getClientIndex(InetAddress ip, int port){
		if (ip == null) return -1;
		for (int i = 0; i < clientsCount; i++) {
        	if (senders[i] != null){
        		if (ip.equals(senders[i].getIp()) && port == senders[i].getPort()) return i;
        	}
        }
		return -1;
	}
	
	public void close(){
		threadsId++;
		connected = false;
		if (socket != null){
			socket.close();
			socket = null;
		}
		for (int i = 0; i < clientsCount; i++) {
        	if (senders[i] != null && senders[i].isActive()) senders[i].close();
        	if (receiverBuffers[i] != null) receiverBuffers[i].close();
        }
	}
}
