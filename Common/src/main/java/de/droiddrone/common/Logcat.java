package de.droiddrone.common;

public class Logcat {
    private final static String tag = "DD";
    public static void log(String msg){
        android.util.Log.i(tag, msg);
    }
}
