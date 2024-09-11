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

import android.location.Location;
import android.location.LocationManager;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class MapData {
    private final static int positionsMinDist = 5;
    private LatLng homePosition;
    private LatLng dronePosition;
    private final List<LatLng> dronePositions = new ArrayList<>();
    private boolean homePositionChanged;
    private boolean dronePositionChanged;
    private boolean isArmed;

    public void setHomePosition(double lat, double lon){
        LatLng oldHomePosition = homePosition;
        homePosition = new LatLng(lat, lon);
        if (oldHomePosition == null){
            homePositionChanged = true;
        }else{
            if (oldHomePosition.latitude != lat || oldHomePosition.longitude != lon){
                homePositionChanged = true;
            }
        }
    }

    public LatLng getHomePosition(){
        homePositionChanged = false;
        return homePosition;
    }

    public boolean isHomePositionChanged(){
        return homePositionChanged;
    }

    public void setDronePosition(double lat, double lon, boolean isArmed){
        this.isArmed = isArmed;
        LatLng oldDronePosition = dronePosition;
        dronePosition = new LatLng(lat, lon);
        if (oldDronePosition == null){
            dronePositionChanged = true;
            if (isArmed) dronePositions.add(dronePosition);
        }else{
            if (oldDronePosition.latitude != lat || oldDronePosition.longitude != lon){
                dronePositionChanged = true;
                if (isArmed) {
                    Location newLocation = new Location(LocationManager.GPS_PROVIDER);
                    Location oldLocation = new Location(LocationManager.GPS_PROVIDER);
                    newLocation.setLatitude(lat);
                    newLocation.setLongitude(lon);
                    oldLocation.setLatitude(oldDronePosition.latitude);
                    oldLocation.setLongitude(oldDronePosition.longitude);
                    float dist = oldLocation.distanceTo(newLocation);
                    if (dist >= positionsMinDist) dronePositions.add(dronePosition);
                }
            }
        }
    }

    public LatLng getDronePosition(){
        dronePositionChanged = false;
        return dronePosition;
    }

    public boolean isDronePositionChanged(){
        return dronePositionChanged;
    }

    public List<LatLng> getDronePositions(){
        return dronePositions;
    }

    public boolean isArmed() {
        return isArmed;
    }
}
