package de.droiddrone.common;

public class Utils {
    public static int getNextPow2(int i){
        int pow = i == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(i - 1);
        i = (int)Math.pow(2, pow);
        return i;
    }
}
