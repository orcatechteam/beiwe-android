package org.beiwe.app.listeners;

import org.beiwe.app.PermissionHandler;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;

/* Notes/observation on Location Services:
 * We are passing in "0" as the minimum time for location updates to be pushed to us, this results in about
 * 1 update every second.  This is based on logs made using a nexus 7 tablet.
 * This makes sense, GPSs on phones do not Have that kind of granularity/resolution.
 * However, we need the resolution in milliseconds for the line-by-line encryption scheme.
 * So, we grab the system time instead.  This may add a fraction of a second to the timestamp.
 * 
 * We are NOT recording which location provider provided the update, or which location providers
 * are available on a given device. */

public class GPSListener implements LocationListener {
	
	public static String header = "timestamp, latitude, longitude, altitude, accuracy";
	
	private Context appContext;
	private PackageManager pkgManager;
	private LocationManager locationManager;

	private Boolean enabled = null;
	//does not have an explicit "exists" boolean.  Use check_status() function, it will return false if there is no GPS.

	private void makeDebugLogStatement(String message) {
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + message);
		Log.w("GPS recording warning", message);
	}

	/** Listens for GPS updates from the network GPS location provider and/or the true
	 * GPS provider, both if possible.  It is NOT activated upon instantiation.  Requires an
	 * application Context object be passed in in order to interface with location services.
	 * When activated using the turn_on() function it will log any location updates to the GPS log.
	 * @param appContext A Context provided an Activity or Service. */
	public GPSListener (Context appContext) {
		this.appContext = appContext;
		pkgManager = this.appContext.getPackageManager();
		enabled = false;
		Log.d("initializing GPS...", "initializing GPS...");
		//There is a possibility (mostly in development) that this will not be instantiated all the time, so we instantiate an extra one here.
		locationManager = (LocationManager) this.appContext.getSystemService(Context.LOCATION_SERVICE);
	}

	/** Turns on GPS providers, provided they are accessible. Handles permission errors appropriately */
	@SuppressWarnings("MissingPermission")
	public synchronized void turn_on() {
		Boolean coarsePermissible = PermissionHandler.checkAccessCoarseLocation(appContext);
		Boolean finePermissible = PermissionHandler.checkAccessFineLocation(appContext);

		if ( !coarsePermissible ) { makeDebugLogStatement("Beiwe has not been granted permissions for coarse location updates."); }
		if ( !finePermissible ) { makeDebugLogStatement("Beiwe has not been granted permissions for fine location updates."); }

		Boolean fineExists = pkgManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
		Boolean coarseExists = pkgManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);

		if ( !fineExists ){ makeDebugLogStatement("Fine location updates are unsupported on this device."); }
		if ( !coarseExists ){ makeDebugLogStatement("Coarse location updates are unsupported on this device."); }
		if ( !fineExists & !coarseExists ) { return; }

		// if already enabled return true.  We want the above logging, do not refactor to earlier in the logic.
		if ( enabled ) { return; }

		//Instantiate a new location manager (looks like the fine and coarse available variables get confused if we use an old one.)
		locationManager = (LocationManager) this.appContext.getSystemService(Context.LOCATION_SERVICE);

		//If the feature exists, request locations from it. (enable if their boolean flag is true.)
		if ( fineExists && finePermissible && coarsePermissible) { // parameters: provider, minTime, minDistance, listener);
			//AndroidStudio insists that both of these require the same location permissions, which seems to be correct
			// since there is only one toggle in userland anyway, yes or no to location permissions.
			Objects.requireNonNull(locationManager).requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
		}
		if ( coarseExists && finePermissible && coarsePermissible) { // parameters: provider, minTime, minDistance, listener);
			Objects.requireNonNull(locationManager).requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
		}

		//Verbose statements on the quality of GPS data streams.
		Boolean fineAvailable = Objects.requireNonNull(locationManager).isProviderEnabled(LocationManager.GPS_PROVIDER);
		Boolean coarseAvailable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		//using single & because we don't want to short-circuit these logical statements, we want exclusive behavior.
		if (!fineAvailable) { makeDebugLogStatement("GPS data stream warning: fine location updates are currently disabled."); }
		if (!coarseAvailable) { makeDebugLogStatement("GPS data stream warning: coarse location updates are currently disabled."); }

		enabled = true;
	}

	/** Disable all location updates */
	@SuppressWarnings("MissingPermission")
	public synchronized void turn_off(){
		// pretty confident this cannot fail.
		locationManager.removeUpdates(this);
		enabled = false;
	}
	
	/** pushes an update to us whenever there is a location update. */
	@Override
	public void onLocationChanged(Location location) {
		Long javaTimeCode = System.currentTimeMillis();
//		Log.d("GPSListener", "gps update...");
		//order: time, latitude, longitude, altitude, horizontal_accuracy\n

		// Latitude and longitude offset should be 0 unless GPS fuzzing is enabled
		double latitude = (location.getLatitude() + PersistentData.getLatitudeOffset());
		double longitude = ((location.getLongitude() + PersistentData.getLongitudeOffset() + 180.0) % 360) - 180.0;

		String data = javaTimeCode.toString() + TextFileManager.DELIMITER
				+ latitude + TextFileManager.DELIMITER
				+ longitude + TextFileManager.DELIMITER
				+ location.getAltitude() + TextFileManager.DELIMITER
				+ location.getAccuracy();
		//note, altitude is notoriously inaccurate, getAccuracy only applies to latitude/longitude
		TextFileManager.getGPSFile().writeEncrypted(data);
	}
	
	/*  We do not actually need to implement any of the following overrides.
	 *  When a provider has a changed we do not need to record it, and we have
	 *  not encountered any corner cases where these are relevant. */
	
//  arg0 for Provider Enabled/Disabled is a string saying "network" or "gps".
	@Override
	public void onProviderDisabled(String arg0) { } // Log.d("A location provider was disabled.", arg0); }
	@Override
	public void onProviderEnabled(String arg0) { } //Log.d("A location provider was enabled.", arg0); }
	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		//Called when the provider status changes, when a provider is unable to fetch a location,
		// or if the provider has recently become available after a period of unavailability.
		// arg0 is the name of the provider that changed status.
		// arg1 is the status of the provider. 0=out of service, 1=temporarily unavailable, 2=available
	}
}
