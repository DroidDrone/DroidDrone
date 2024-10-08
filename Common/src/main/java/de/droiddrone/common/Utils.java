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

public class Utils {
    public static int UINT16_MAX = 65535;
    public static int INT16_MAX = 32767;
    public static int UINT8_MAX = 255;

    public static int getNextPow2(int i){
        int pow = i == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(i - 1);
        i = (int)Math.pow(2, pow);
        return i;
    }

    public static int parseInt(String str, int defaultValue){
        try{
            return Integer.parseInt(str);
        }catch (Exception e){
            return defaultValue;
        }
    }
}
