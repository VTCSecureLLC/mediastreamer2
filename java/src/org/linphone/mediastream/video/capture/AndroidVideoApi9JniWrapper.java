/*
AndroidVideoApi9JniWrapper.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.linphone.mediastream.video.capture;

import java.util.List;

import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class AndroidVideoApi9JniWrapper {
	static private Camera mCamera;
	static private boolean isFlashOn;

	static public int detectCameras(int[] indexes, int[] frontFacing, int[] orientation) {
		return AndroidVideoApi5JniWrapper.detectCameras(indexes, frontFacing, orientation);
	}

	/**
	 * Return the hw-available available resolution best matching the requested one.
	 * Best matching meaning :
	 * - try to find the same one
	 * - try to find one just a little bigger (ex: CIF when asked QVGA)
	 * - as a fallback the nearest smaller one
	 * @param requestedW Requested video size width
	 * @param requestedH Requested video size height
	 * @return int[width, height] of the chosen resolution, may be null if no
	 * resolution can possibly match the requested one
	 */
	static public int[] selectNearestResolutionAvailable(int cameraId, int requestedW, int requestedH) {
		Log.d("selectNearestResolutionAvailable: " + cameraId + ", " + requestedW + "x" + requestedH);
		return AndroidVideoApi5JniWrapper.selectNearestResolutionAvailableForCamera(cameraId, requestedW, requestedH);
	}

	public static Object startRecording(int cameraId, int width, int height, int fps, int rotation, final long nativePtr) {
		android.util.Log.e("Info", "startRecording!!!!!");
		Log.d("startRecording(" + cameraId + ", " + width + ", " + height + ", " + fps + ", " + rotation + ", " + nativePtr + ")");
		try {
		Camera camera = Camera.open(cameraId);
		Parameters params = camera.getParameters();

		params.setPreviewSize(width, height);
		int[] chosenFps = findClosestEnclosingFpsRange(fps*1000, params.getSupportedPreviewFpsRange());
		params.setPreviewFpsRange(chosenFps[0], chosenFps[1]);
		camera.setParameters(params);

		int bufferSize = (width * height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
		camera.addCallbackBuffer(new byte[bufferSize]);
		camera.addCallbackBuffer(new byte[bufferSize]);

		camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
			public void onPreviewFrame(byte[] data, Camera camera) {
				// forward image data to JNI
				if (data == null) {
					// It appears that there is a bug in the camera driver that is asking for a buffer size bigger than it should
					Parameters params = camera.getParameters();
					Size size = params.getPreviewSize();
					int bufferSize = (size.width * size.height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
					bufferSize += bufferSize / 20;
					camera.addCallbackBuffer(new byte[bufferSize]);
				} else if (AndroidVideoApi5JniWrapper.isRecording) {
					AndroidVideoApi5JniWrapper.putImage(nativePtr, data);
					camera.addCallbackBuffer(data);
				}
			}
		});

		setCameraDisplayOrientation(rotation, cameraId, camera);
			mCamera = camera;
			if (mCamera.getParameters().getFlashMode() == Parameters.FLASH_MODE_ON || mCamera.getParameters().getFlashMode() == Parameters.FLASH_MODE_TORCH){
				isFlashOn = true;
				android.util.Log.e("Info", "isFlashOn = true");
			}
			else{
				isFlashOn = false;
				android.util.Log.e("Info", "isFlashOn = false");
			}

		camera.startPreview();
//			if(cameraId ==0)
		//	turnOnFlashing(isFlashOn);
		AndroidVideoApi5JniWrapper.isRecording = true;
		Log.d("Returning camera object: " + camera);
		return camera;
		} catch (Exception exc) {
			exc.printStackTrace();
			return null;
		}
	}

	public static void stopRecording(Object cam) {
		android.util.Log.e("Info", "stopRecording!!!!!");
		turnOnFlashing(isFlashOn);
		AndroidVideoApi5JniWrapper.isRecording = false;
		AndroidVideoApi8JniWrapper.stopRecording(cam);
	}

	public static void setPreviewDisplaySurface(Object cam, Object surf) {
		AndroidVideoApi5JniWrapper.setPreviewDisplaySurface(cam, surf);
	}

	private static void setCameraDisplayOrientation(int rotationDegrees, int cameraId, Camera camera) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + rotationDegrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - rotationDegrees + 360) % 360;
		}

		Log.w("Camera preview orientation: "+ result);
		try {
			camera.setDisplayOrientation(result);
		} catch (Exception exc) {
			Log.e("Failed to execute: camera[" + camera + "].setDisplayOrientation(" + result + ")");
			exc.printStackTrace();
		}
	}

	private static int[] findClosestEnclosingFpsRange(int expectedFps, List<int[]> fpsRanges) {
		Log.d("Searching for closest fps range from " + expectedFps);
		if (fpsRanges == null || fpsRanges.size() == 0) {
			return new int[] { 0, 0 };
		}

		// init with first element
		int[] closestRange = fpsRanges.get(0);
		int measure = Math.abs(closestRange[0] - expectedFps)
				+ Math.abs(closestRange[1] - expectedFps);
		for (int[] curRange : fpsRanges) {
			if (curRange[0] > expectedFps || curRange[1] < expectedFps) continue;
			int curMeasure = Math.abs(curRange[0] - expectedFps)
					+ Math.abs(curRange[1] - expectedFps);
			if (curMeasure < measure) {
				closestRange=curRange;
				measure = curMeasure;
				Log.d("a better range has been found: w="+closestRange[0]+",h="+closestRange[1]);
			}
		}
		Log.d("The closest fps range is w="+closestRange[0]+",h="+closestRange[1]);
		return closestRange;
	}

	public static boolean isFlashOn() {
		return isFlashOn;
	}

	public static void setFlashOn(boolean isFlashOn) {
		AndroidVideoApi9JniWrapper.isFlashOn = isFlashOn;
	}

	public static boolean turnOnFlashing(boolean isFlashOn) {
		try {
			if (mCamera != null) {
				Parameters params = mCamera.getParameters();
//				isFlashOn = true;
				if (isFlashOn) {
					if (true/*params.getSupportedFlashModes() != null && params.getSupportedFlashModes().contains(Parameters.FLASH_MODE_TORCH)*/) {
						android.util.Log.e("Info", "camera set FLASH_MODE_TORCH");
						params.setFlashMode(Parameters.FLASH_MODE_TORCH);
						mCamera.setParameters(params);
						isFlashOn = true;
						return true;
					} else {
						android.util.Log.e("Info", "camera hasn't FLASH_MODE_TORCH");
						isFlashOn = false;
						return false;
					}
				} else {
					android.util.Log.e("Info", "camera FLASH_MODE_OFF");
					params.setFlashMode(Parameters.FLASH_MODE_OFF);
					mCamera.setParameters(params);
					isFlashOn = false;
					return true;
				}

			} else {
				android.util.Log.e("Info", "camera is null");
				isFlashOn = false;
				return false;
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			return false;
		}

	}

	public static boolean isFlashingOn() {
		if (isFlashOn)
			return true;
		else return false;
	}
}
