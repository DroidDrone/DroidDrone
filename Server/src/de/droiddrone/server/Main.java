package de.droiddrone.server;

import static de.droiddrone.common.Log.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Scanner;

public class Main {
	final static int versionCode = 1;
	final static String versionName = "1.0.1";
	private static Udp udp;
	private static Config config;
	
	public static void main(String[] args) {
		log("Start server time: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime()));
		log("Version: " + versionName);
		log("----------------------");
		printCommands();
		config = new Config();
		udp = new Udp(config);
		if (!udp.initialize()) log("UDP initialization error!");
		Scanner console = new Scanner(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		while (console.hasNextLine()) {
			String s = console.nextLine();
			if (s == null) continue;
			s = s.toLowerCase();
			if (s.equals("help") || s.equals("0") || s.isEmpty()) {
				printCommands();
				continue;
			}
			if (s.equals("exit") || s.equals("1")) {
				log("Exiting...");
				udp.close();
				System.exit(0);
			}
			if (s.equals("restart") || s.equals("2")) {
				log("Restarting...");
				udp.close();
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				udp = new Udp(config);
				if (!udp.initialize()) log("UDP initialization error!");
				continue;
			}
			if (s.equals("setport") || s.equals("3")) {
				log("Enter the new port: ");
				int port = 0;
				try {
					port = Integer.parseInt(console.nextLine());
				}catch (Exception e) {
					log("Wrong input.");
					continue;
				}
				if (config.setPort(port)) log("Port has been updated. A restart is required to update the changes.");
				continue;
			}
			if (s.equals("setkey") || s.equals("4")) {
				log("Enter the new key: ");
				if (config.setKey(console.nextLine()))
					log("Key has been updated.");
				continue;
			}
			if (s.equals("setviewerkey") || s.equals("5")) {
				log("Enter the new viewer key: ");
				if (config.setViewerKey(console.nextLine())) log("Viewer key has been updated.");
				continue;
			}
			if (s.equals("setmaxviewers") || s.equals("6")) {
				log("Enter the new max viewers count: ");
				int count = 0;
				try {
					count = Integer.parseInt(console.nextLine());
				}catch (Exception e) {
					log("Wrong input.");
					continue;
				}
				if (config.setViewerCount(count)) {
					log("Viewers count has been set to: " + count);
					if (udp.isConnected()) log("A restart is required to update the changes.");
				}
				continue;
			}
			log("Kommand \"" + s + "\" not found.");
		}
	}
	
	private static void printCommands() {
		log("Command list:");
		log("0. help");
		log("1. exit");
		log("2. restart");
		log("3. setPort");
		log("4. setKey");
		log("5. setViewerKey");
		log("6. setMaxViewers");
	}
}
