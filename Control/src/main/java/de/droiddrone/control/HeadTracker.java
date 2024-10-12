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

package de.droiddrone.control;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import de.droiddrone.common.SettingsCommon;

public class HeadTracker implements SensorEventListener {
    private final Rc rc;
    private final GlRenderer glRenderer;
    private final SensorManager sensorManager;
    private final float[] gyroAxes = new float[3];
    private boolean isRegistered;

    public HeadTracker(Context context, Rc rc, GlRenderer glRenderer) {
        this.rc = rc;
        this.glRenderer = glRenderer;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        isRegistered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroAxes, 0, 3);
            rc.setGyroValues(gyroAxes);
            glRenderer.setGyroValues(gyroAxes);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void resetRcChannels(){
        rc.resetHeadTrackingAxes();
    }

    public void start(){
        if (sensorManager == null || isRegistered) return;
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SettingsCommon.gyroSamplingPeriodUs);
            isRegistered = true;
        }
    }

    public void pause(){
        if (sensorManager == null || !isRegistered) return;
        sensorManager.unregisterListener(this);
        isRegistered = false;
    }
}
