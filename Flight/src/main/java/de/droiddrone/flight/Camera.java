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

public interface Camera {
    boolean initialize(StreamEncoder streamEncoder, Mp4Recorder mp4Recorder);
    boolean openCamera();
    int getCurrentFps();
    int getTargetFps();
    boolean isOpened(String cameraId);
    void startCapture();
    void stopCapture();
    void startPreview();
    int getWidth();
    int getHeight();
    boolean isFrontFacing();
    void close();
}
