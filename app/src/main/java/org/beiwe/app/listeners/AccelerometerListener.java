package org.beiwe.app.listeners;

import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class AccelerometerListener implements SensorEventListener{
	public static String header = "timestamp,accuracy,x,y,z";
	
	private SensorManager accelSensorManager;
	private Sensor accelSensor;

	private Context appContext;
	private PackageManager pkgManager;

	private Boolean exists = null;
	private Boolean enabled = null;
	
	private String accuracy;

	/** Returns a boolean of whether the accelerometer is recording */
	public Boolean check_status(){ 
		if (exists) return enabled;
		return false; }
	
	/**Listens for accelerometer updates.  NOT activated on instantiation.
	 * Use the turn_on() function to log any accelerometer updates to the 
	 * accelerometer log.
	 * @param applicationContext a Context from an activity or service. */
	public AccelerometerListener(Context applicationContext){
		this.appContext = applicationContext;
		this.pkgManager = appContext.getPackageManager();
		this.accuracy = "unknown";
		this.exists = pkgManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
		
		if (this.exists) {
			enabled = false;
			this.accelSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
			if (this.accelSensorManager ==  null ) { 
				Log.e("Accelerometer Problems", "accelSensorManager does not exist? (1)" );
				TextFileManager.getDebugLogFile().writeEncrypted("accelSensorManager does not exist? (1)");
				exists = false;	}
			
			this.accelSensor = accelSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if (this.accelSensor == null ) { 
				Log.e("Accelerometer Problems", "accelSensor does not exist? (2)" );
				TextFileManager.getDebugLogFile().writeEncrypted("accelSensor does not exist? (2)");
				exists = false;	}
	} }
	 
	public synchronized void turn_on() {
		if ( !accelSensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL) ) {
			Log.e("Accelerometer", "Accelerometer is broken");
			TextFileManager.getDebugLogFile().writeEncrypted("Trying to start Accelerometer session, device cannot find accelerometer."); }
		enabled = true;	}
	
	public synchronized void turn_off(){
		accelSensorManager.unregisterListener(this);
		enabled = false; }
	
	/** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
	 * (only triggered by the system.) */
	@Override
	public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }
	
	/** On receipt of a sensor change, record it.  Include accuracy. 
	 * (only ever triggered by the system.) */
	@Override
	public synchronized void onSensorChanged(SensorEvent arg0) {
//		Log.e("Accelerometer", "accelerometer update");
		Long javaTimeCode = System.currentTimeMillis();
		float[] values = arg0.values;
		String data = javaTimeCode.toString() + ',' + accuracy + ',' + values[0] + ',' + values[1] + ',' + values[2];
		TextFileManager.getAccelFile().writeEncrypted(data);
	}
}