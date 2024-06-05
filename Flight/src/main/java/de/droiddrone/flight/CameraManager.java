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

import android.content.Context;

public class CameraManager {
    private final Context context;
    private final Config config;
    private UsbCamera usbCamera;
    private InternalCamera internalCamera;

    public CameraManager(Context context, Config config) {
        this.context = context;
        this.config = config;
    }

    public Camera getCamera(){
        if (config.isUseUsbCamera()){
            if (usbCamera == null) usbCamera = new UsbCamera(context, config);
            if (internalCamera != null){
                internalCamera.close();
                internalCamera = null;
            }
            return usbCamera;
        }else{
            if (internalCamera == null) internalCamera = new InternalCamera(context, config);
            if (usbCamera != null){
                usbCamera.close();
                usbCamera = null;
            }
            return internalCamera;
        }
    }

    public void close(){
        if (internalCamera != null) {
            internalCamera.close();
            internalCamera = null;
        }
        if (usbCamera != null) {
            usbCamera.close();
            usbCamera = null;
        }
    }
}
