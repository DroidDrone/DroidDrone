package de.droiddrone.common;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Log {
	public static void log(String msg) {
		System.out.println(msg);
	}
	
	public static void timeLog(String msg){
		System.out.println(msg+" - "+new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime()));
	}
}
