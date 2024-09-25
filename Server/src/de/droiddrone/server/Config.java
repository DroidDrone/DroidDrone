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

import java.io.File;
import java.io.IOException;

import de.droiddrone.common.SettingsCommon;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static de.droiddrone.common.Log.*;

public class Config {
	public static int maxViewersCount = 8;
	private File file;
	private String key = SettingsCommon.key;
	private String viewerKey = SettingsCommon.key;
	private int viewersCount = SettingsCommon.viewersCount;
	private int port = SettingsCommon.port;
	
	public Config() {
		file = new File("config.cfg");
		try {
			if (file.createNewFile()) {
				if (saveConfig()) {
					log("New config created. Use default key.");
				}
			}else{
				if (loadConfig()) log("Config loaded.");
			}
		} catch (IOException e) {
			timeLog("Config loading error. Use default key.");
			e.printStackTrace();
		}
	}
	
	public String getKey() {
		return key;
	}
	
	public boolean setKey(String key) {
		if (key == null) key = "";
		if (key.length() > 16) {
			log("Error: Maximum 16 characters allowed.");
			return false;
		}
		this.key = key;
		return saveConfig();
	}
	
	public String getViewerKey() {
		return viewerKey;
	}
	
	public boolean setViewerKey(String viewerKey) {
		if (viewerKey == null) viewerKey = "";
		if (viewerKey.length() > 16) {
			log("Error: Maximum 16 characters allowed.");
			return false;
		}
		this.viewerKey = viewerKey;
		return saveConfig();
	}
	
	public int getViewerCount(){
		return viewersCount;
	}
	
	public boolean setViewerCount(int count) {
		if (count < 0 || count > maxViewersCount) {
			log("Values from 0 to " + maxViewersCount + " are allowed.");
			return false;
		}
		this.viewersCount = count;
		return saveConfig();
	}
	
	public int getPort(){
		return port;
	}
	
	public boolean setPort(int port) {
		if (port < 1024 || port > 65535) {
			log("There are values from 1024 to 65535 are allowed.");
            return false;
        }
		this.port = port;
		return saveConfig();
	}
	
	private boolean loadConfig() {
		try {
			DataInputStream dais = new DataInputStream(new FileInputStream(file));
			key = dais.readUTF();
			viewerKey = dais.readUTF();
			viewersCount = dais.readInt();
			port = dais.readInt();
			dais.close();
			return true;
		} catch (Exception e) {
			timeLog("Load config error: " + e.toString());
			saveConfig();
			return false;
		}
	}
	
	private boolean saveConfig() {
		try {
			DataOutputStream daos = new DataOutputStream(new FileOutputStream(file, false));
			daos.writeUTF(key);
			daos.writeUTF(viewerKey);
			daos.writeInt(viewersCount);
			daos.writeInt(port);
			daos.close();
			return true;
		} catch (Exception e) {
			timeLog("Save config error: " + e.toString());
			return false;
		}
	}
}
