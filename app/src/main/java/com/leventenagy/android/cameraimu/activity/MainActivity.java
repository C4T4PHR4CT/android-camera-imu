package com.leventenagy.android.cameraimu.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.leventenagy.android.cameraimu.R;
import com.leventenagy.android.cameraview.widget.CameraView;

class SensorData {
	public String name;
	public Object value;

	public SensorData(String name, Object value) {
		this.name = name;
		this.value = value;
	}
}

@TargetApi(Build.VERSION_CODES.R)
public class MainActivity extends Activity implements SensorEventListener {
	private final String[] permissions = new String[] {Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE};
	private int iterator = 0;

	private final String path = "/storage/emulated/0/camera_imu";

	private static boolean frontFacing = false;

	private static CameraView cameraView;

	private SensorManager sensorManager;
	private final Map<String, Object> sensors = new HashMap<String, Object>();

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 69) {
			if (Environment.isExternalStorageManager()) {
				File directory = new File(path);
				if (!directory.exists()) {
					Toast.makeText(this, "creating", Toast.LENGTH_SHORT).show();
					directory.mkdir();
				}
			} else {
				Toast.makeText(this, R.string.error_camera, Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] per, int[] grantResults) {
		if (requestCode == iterator && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, R.string.error_camera, Toast.LENGTH_SHORT).show();
			finish();
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			iterator++;
			if (iterator >= permissions.length) {
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				Uri uri = Uri.fromParts("package", this.getPackageName(), null);
				intent.setData(uri);
				startActivityForResult(intent, 69);
			}
			while (iterator < permissions.length)
				if (checkSelfPermission(permissions[iterator]) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{permissions[iterator]}, iterator);
					break;
				} else
					iterator++;
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		checkPermissions();

		setContentView(R.layout.activity_main);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		ImageButton button = (ImageButton) this.findViewById(R.id.imageButton);
		button.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				switch(event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						button.setColorFilter(Color.argb(150, 150, 150, 150));
						cameraView.setVisibility(View.GONE);
						cameraView.getCamera().takePicture(null, null, new Camera.PictureCallback() {
							@Override
							public void onPictureTaken(byte[] bytes, Camera camera) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										cameraView.setVisibility(View.VISIBLE);
										camera.cancelAutoFocus();
										camera.startPreview();
										writeJpg(bytes);
										writeJson(sensors);
									}
								});
							}
						});
						break;
					case MotionEvent.ACTION_UP:
						button.setColorFilter(null);
						break;
				}
				return true;
			}
		});
		ImageButton button2 = (ImageButton) this.findViewById(R.id.imageButton2);
		button2.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				switch(event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						button2.setColorFilter(Color.argb(150, 150, 150, 150));
						invertCamera();
						break;
					case MotionEvent.ACTION_UP:
						button2.setColorFilter(null);
						break;
				}
				return true;
			}
		});

		cameraView = (CameraView) this.findViewById(R.id.cameraView);
		cameraView.setUseOrientationListener(true);
	}

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Do something here if sensor accuracy changes.
	}

	@Override
	public final void onSensorChanged(SensorEvent event) {
		sensors.put(event.sensor.getStringType(),event.values);
	}

	@Override
	public void onResume() {
		super.onResume();
		openCameraView();
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public void onPause() {
		super.onPause();
		closeCameraView();
		sensorManager.unregisterListener(this);

	}

	private void writeJpg(byte[] value){
		@SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File file = new File(path+"/"+timeStamp+".jpg");
		try {
			FileOutputStream outputStream = new FileOutputStream(file.getAbsoluteFile());
			outputStream.write(value);
			outputStream.flush();
			outputStream.close();
		}
		catch (IOException e){
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void writeJson(Object value){
		@SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File file = new File(path+"/"+timeStamp+".json");
		try {
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			Gson gson = new Gson();
			bw.write(gson.toJson(value));
			bw.flush();
			bw.close();
		}
		catch (IOException e){
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void checkPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			while (iterator < permissions.length)
				if (checkSelfPermission(permissions[iterator]) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{permissions[iterator]}, iterator);
					break;
				} else
					iterator++;
	}

	private void openCameraView() {
		cameraView.openAsync(CameraView.findCameraId(getFacing()));
	}

	private void closeCameraView() {
		cameraView.close();
	}

	private void invertCamera() {
		frontFacing ^= true;
		closeCameraView();
		openCameraView();
	}

	private int getFacing() {
		return frontFacing ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
	}
}