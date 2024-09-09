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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class CustomFragmentFactory extends FragmentFactory {
    private final FragmentManager fragmentManager;
    private final MainActivity activity;
    private final Config config;
    private final Rc rc;
    private final GlRenderer glRenderer;
    private final String startFragmentTag = "StartFragment";
    private final String glFragmentTag = "GlFragment";
    private final String mapFragmentTag = "MapFragment";
    private final String settingsFragmentTag = "SettingsFragment";
    private final String channelsMappingFragmentTag = "ChannelsMappingFragment";
    private int currentFragmentId;

    public CustomFragmentFactory(FragmentManager fragmentManager, MainActivity activity, Config config, Rc rc, GlRenderer glRenderer) {
        super();
        this.fragmentManager = fragmentManager;
        this.activity = activity;
        this.config = config;
        this.rc = rc;
        this.glRenderer = glRenderer;
    }

    @NonNull
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
        Class<? extends Fragment> fragmentClass = loadFragmentClass(classLoader, className);
        if (fragmentClass == StartFragment.class) return new StartFragment(activity, config);
        if (fragmentClass == ChannelsMappingFragment.class) return new ChannelsMappingFragment(activity, config, rc);
        if (fragmentClass == SettingsFragment.class) return new SettingsFragment(activity);
        if (fragmentClass == GlFragment.class) return new GlFragment(activity, glRenderer);
        if (fragmentClass == MapFragment.class) return new MapFragment(activity);
        return super.instantiate(classLoader, className);
    }

    public StartFragment getStartFragment() {
        return (StartFragment) fragmentManager.findFragmentByTag(startFragmentTag);
    }

    public ChannelsMappingFragment getChannelsMappingFragment() {
        return (ChannelsMappingFragment) fragmentManager.findFragmentByTag(channelsMappingFragmentTag);
    }

    public SettingsFragment getSettingsFragment() {
        return (SettingsFragment) fragmentManager.findFragmentByTag(settingsFragmentTag);
    }

    public GlFragment getGlFragment() {
        return (GlFragment) fragmentManager.findFragmentByTag(glFragmentTag);
    }

    public MapFragment getMapFragment() {
        return (MapFragment) fragmentManager.findFragmentByTag(mapFragmentTag);
    }

    private void showExistingFragment(Fragment fragment){
        hideAllFragments().show(fragment).commit();
    }

    private FragmentTransaction hideAllFragments(){
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.setReorderingAllowed(true);
        for (Fragment fr : fragmentManager.getFragments()){
            fragmentTransaction.hide(fr);
        }
        return fragmentTransaction;
    }

    public void showStartFragment() {
        activity.getWindow().getDecorView().setSystemUiVisibility(MainActivity.showNavigationFlags);
        StartFragment startFragment = getStartFragment();
        if (startFragment != null){
            showExistingFragment(startFragment);
        }else{
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, StartFragment.class, null, startFragmentTag)
                    .commit();
        }
        currentFragmentId = StartFragment.fragmentId;
    }

    public void showGlFragment() {
        activity.getWindow().getDecorView().setSystemUiVisibility(MainActivity.fullScreenFlags);
        GlFragment glFragment = getGlFragment();
        if (glFragment != null){
            showExistingFragment(glFragment);
        }else{
            hideAllFragments()
                    .add(R.id.fragment_container_view, GlFragment.class, null, glFragmentTag)
                    .commit();
        }
        currentFragmentId = GlFragment.fragmentId;
    }

    public void showSettingsFragment() {
        activity.getWindow().getDecorView().setSystemUiVisibility(MainActivity.showNavigationFlags);
        SettingsFragment settingsFragment = getSettingsFragment();
        if (settingsFragment != null){
            showExistingFragment(settingsFragment);
        }else {
            hideAllFragments()
                    .add(R.id.fragment_container_view, SettingsFragment.class, null, settingsFragmentTag)
                    .commit();
        }
        currentFragmentId = SettingsFragment.fragmentId;
    }

    public void showChannelsMappingFragment() {
        activity.getWindow().getDecorView().setSystemUiVisibility(MainActivity.showNavigationFlags);
        ChannelsMappingFragment channelsMappingFragment = getChannelsMappingFragment();
        if (channelsMappingFragment != null){
            showExistingFragment(channelsMappingFragment);
        }else{
            hideAllFragments()
                    .add(R.id.fragment_container_view, ChannelsMappingFragment.class, null, channelsMappingFragmentTag)
                    .commit();
        }
        currentFragmentId = ChannelsMappingFragment.fragmentId;
    }

    public void showMapFragment() {
        activity.getWindow().getDecorView().setSystemUiVisibility(MainActivity.showNavigationFlags);
        MapFragment mapFragment = getMapFragment();
        if (mapFragment != null){
            showExistingFragment(mapFragment);
        }else{
            hideAllFragments()
                    .add(R.id.fragment_container_view, MapFragment.class, null, mapFragmentTag)
                    .commit();
        }
        currentFragmentId = MapFragment.fragmentId;
    }

    public int getCurrentFragmentId() {
        return currentFragmentId;
    }
}
