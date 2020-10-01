package org.beiwe.app.listeners;

import org.beiwe.app.storage.TextFileManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;



public class GyroscopeListener implements SensorEventListener {
    public static String header = "timestamp,accuracy,x,y,z";

    private SensorManager gyroSensorManager;
    private Sensor gyroSensor;
    private Context appContext;
    private PackageManager pkgManager;
    public Boolean exists = null;
    private Boolean enabled = null;

    private String accuracy;

    /** Returns a boolean of whether the Gyroscope is recording */
    public Boolean check_status(){
        if (exists) return enabled;
        return false; }

    /**Listens for gyroscope updates.  NOT activated on instantiation.
     * Use the turn_on() function to log any gyroscope updates to the
     * gyroscope log.
     * @param applicationContext a Context from an activity or service. */

    public GyroscopeListener(Context applicationContext){
        this.appContext = applicationContext;
        this.pkgManager = appContext.getPackageManager();
        this.accuracy = "unknown";
        this.exists = pkgManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);

        if (this.exists) {
            enabled = false;
            this.gyroSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
            if (this.gyroSensorManager ==  null ) {
                Log.e("Gyroscope Problems", "gyroSensorManager does not exist? (1)" );
                TextFileManager.getDebugLogFile().writeEncrypted("gyroSensorManager does not exist? (1)");
                exists = false;	}

            this.gyroSensor = gyroSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (this.gyroSensor == null ) {
                Log.e("Gyroscope Problems", "gyroSensor does not exist? (2)" );
                TextFileManager.getDebugLogFile().writeEncrypted("gyroSensor does not exist? (2)");
                exists = false;	}
        } }

    public synchronized void turn_on() {
        if ( !gyroSensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL) ) {
            Log.e("Gyroscope", "Gyroscope is broken");
            TextFileManager.getDebugLogFile().writeEncrypted("Trying to start gyroscope session, device cannot find gyroscope."); }
        enabled = true;	}


    public synchronized void turn_off(){
        gyroSensorManager.unregisterListener(this);
        enabled = false;
    }

    /** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
     * (only triggered by the system.) */
    @Override
    public synchronized void onAccuracyChanged(Sensor arg0, int arg1) {	accuracy = "" + arg1; }

    /** On receipt of a sensor change, record it.  Include accuracy.
     * (only ever triggered by the system.) */
    @Override
    @SuppressLint("DefaultLocale")
    public synchronized void onSensorChanged(SensorEvent arg0) {
        Long javaTimeCode = System.currentTimeMillis();
        float[] values = arg0.values;
        String value0= String.format("%.16f", values[0]);
        String value1= String.format("%.16f", values[1]);
        String value2= String.format("%.16f", values[2]);
        String data = javaTimeCode.toString() + ',' + accuracy + ',' + value0 + ',' + value1 + ',' + value2;
        TextFileManager.getGyroFile().writeEncrypted(data);
    }
}
