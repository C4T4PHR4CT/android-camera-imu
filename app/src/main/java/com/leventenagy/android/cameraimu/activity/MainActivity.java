package com.leventenagy.android.cameraimu.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.leventenagy.android.cameraimu.R;
import com.leventenagy.android.cameraview.widget.CameraView;

public class MainActivity extends Activity {
	private final String[] permissions = new String[] {android.Manifest.permission.CAMERA,android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
	private int iterator = 0;

	private static boolean frontFacing = false;

	private CameraView cameraView;

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] _, int[] grantResults) {
		if (requestCode == iterator && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, R.string.error_camera, Toast.LENGTH_SHORT).show();
			finish();
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			while (iterator < permissions.length)
				if (checkSelfPermission(permissions[iterator]) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{permissions[iterator]}, iterator);
					break;
				} else
					iterator++;
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		checkPermissions();

		cameraView = new CameraView(this);
		cameraView.setUseOrientationListener(true);
		cameraView.setOnClickListener(v -> {writeFile("cica");});

		setContentView(cameraView);
	}

	@Override
	public void onResume() {
		super.onResume();
		openCameraView();
	}

	@Override
	public void onPause() {
		super.onPause();
		closeCameraView();
	}

	private void writeFile(String value){
		@SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES)+"/"+timeStamp+".txt");
		Toast.makeText(this, getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString(), Toast.LENGTH_LONG).show();
		try {
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(value);
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
