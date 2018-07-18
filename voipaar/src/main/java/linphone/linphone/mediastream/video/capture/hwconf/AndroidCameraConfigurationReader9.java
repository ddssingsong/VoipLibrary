/*
AndroidCameraConf9.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package linphone.linphone.mediastream.video.capture.hwconf;

import java.util.ArrayList;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;

/**
 * Android cameras detection, using SDK >= 9
 *
 */
class AndroidCameraConfigurationReader9 {
	static public AndroidCameraConfiguration.AndroidCamera[] probeCameras() {
		List<AndroidCameraConfiguration.AndroidCamera> cam = new ArrayList<AndroidCameraConfiguration.AndroidCamera>(Camera.getNumberOfCameras());
		
		for(int i=0; i<Camera.getNumberOfCameras(); i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			Camera c = Camera.open(i);
			cam.add(new AndroidCameraConfiguration.AndroidCamera(i, info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT, info.orientation, c.getParameters().getSupportedPreviewSizes()));
			c.release();
		}
		
		AndroidCameraConfiguration.AndroidCamera[] result = new AndroidCameraConfiguration.AndroidCamera[cam.size()];
		result = cam.toArray(result);
		return result;
	}
}
