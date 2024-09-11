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

import static de.droiddrone.common.Logcat.log;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    public static final int fragmentId = 5;
    private final MainActivity activity;
    private final MapData mapData;
    private GoogleMap googleMap;
    private Marker homeMarker;
    private Marker droneMarker;
    private Polyline polyline;
    private int threadsId;
    private boolean setMapCamera;

    public MapFragment(MainActivity activity, MapData mapData) {
        super(R.layout.fragment_map);
        this.activity = activity;
        this.mapData = mapData;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden){
            stopMapThread();
        }else{
            startMapThread();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        GoogleMapOptions options = new GoogleMapOptions();
        options.mapType(GoogleMap.MAP_TYPE_HYBRID)
                .compassEnabled(true)
                .rotateGesturesEnabled(true)
                .mapToolbarEnabled(true)
                .zoomGesturesEnabled(true)
                .zoomControlsEnabled(true);
        SupportMapFragment mapFragment = SupportMapFragment.newInstance(options);
        getChildFragmentManager().beginTransaction()
                .add(R.id.mapFragment, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
        setMapCamera = true;
    }

    private void startMapThread(){
        threadsId++;
        Thread mapThread = new Thread(mapRun);
        mapThread.setDaemon(false);
        mapThread.setName("mapThread");
        mapThread.start();
    }

    private void stopMapThread(){
        threadsId++;
    }

    private final Runnable mapRun = new Runnable() {
        @Override
        public void run() {
            final int id = threadsId;
            final int intervalMs = 1000;
            while (id == threadsId) {
                try {
                    Thread.sleep(intervalMs);
                    if (googleMap == null) continue;
                    activity.runOnUiThread(() -> {
                        if (mapData.isHomePositionChanged()){
                            LatLng home = mapData.getHomePosition();
                            if (homeMarker == null){
                                homeMarker = googleMap.addMarker(new MarkerOptions()
                                        .position(home)
                                        .title("Home")
                                        .icon(BitmapDescriptorFactory.fromResource(R.raw.marker_home)));
                            }else{
                                homeMarker.setPosition(home);
                            }
                        }
                        if (mapData.isDronePositionChanged()){
                            LatLng drone = mapData.getDronePosition();
                            if (droneMarker == null){
                                droneMarker = googleMap.addMarker(new MarkerOptions()
                                        .position(drone)
                                        .title("Drone")
                                        .icon(BitmapDescriptorFactory.fromResource(R.raw.marker_drone)));
                            }else{
                                droneMarker.setPosition(drone);
                            }
                            if (mapData.isArmed() || setMapCamera){
                                googleMap.moveCamera(CameraUpdateFactory.newLatLng(drone));
                                if (setMapCamera){
                                    googleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                                    setMapCamera = false;
                                }
                            }
                        }
                        List<LatLng> positions = mapData.getDronePositions();
                        if (positions.size() > 1) {
                            if (polyline == null) {
                                polyline = googleMap.addPolyline(new PolylineOptions()
                                        .color(Color.BLUE)
                                        .addAll(positions));
                            } else {
                                polyline.setPoints(positions);
                            }
                        }
                    });
                } catch (Exception e) {
                    log("Map thread error: " + e);
                }
            }
        }
    };

    @Override
    public void onResume() {
        startMapThread();
        super.onResume();
    }

    @Override
    public void onPause() {
        stopMapThread();
        super.onPause();
    }

    @SuppressLint("MissingPermission")
    public void setMyLocationEnabled(){
        if (activity.isLocationPermissionGranted()) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        if (activity.isLocationPermissionGranted()){
            googleMap.setMyLocationEnabled(true);
        }
    }
}