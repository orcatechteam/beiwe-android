package org.beiwe.app;

import java.util.Calendar;
import java.util.List;
import java.util.Objects;

import org.beiwe.app.listeners.AccelerometerListener;
import org.beiwe.app.listeners.AppUsageListener;
import org.beiwe.app.listeners.GyroscopeListener;
import org.beiwe.app.listeners.BluetoothListener;
import org.beiwe.app.listeners.CallLogger;
import org.beiwe.app.listeners.GPSListener;
import org.beiwe.app.listeners.MMSSentLogger;
import org.beiwe.app.listeners.PowerStateListener;
import org.beiwe.app.listeners.SmsSentLogger;
import org.beiwe.app.listeners.WifiListener;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.networking.SurveyDownloader;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.survey.SurveyScheduler;
import org.beiwe.app.ui.user.LoginActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.dsn.InvalidDsnException;

public class BackgroundService extends Service {
	private Context appContext;
	public GPSListener gpsListener;
	public PowerStateListener powerStateListener;
	public AccelerometerListener accelerometerListener;
	public GyroscopeListener gyroscopeListener;
	public BluetoothListener bluetoothListener;
	public static Timer timer;
	public AppUsageListener appUsageListener;
	
	//localHandle is how static functions access the currently instantiated background service.
	//It is to be used ONLY to register new surveys with the running background service, because
	//that code needs to be able to update the IntentFilters associated with timerReceiver.
	//This is Really Hacky and terrible style, but it is okay because the scheduling code can only ever
	//begin to run with an already fully instantiated background service.
	private static BackgroundService localHandle;
	
	@Override
	/** onCreate is essentially the constructor for the service, initialize variables here. */
	public void onCreate() {
		appContext = this.getApplicationContext();
		try {
			String sentryDsn = BuildConfig.SENTRY_DSN;
			Sentry.init(sentryDsn, new AndroidSentryClientFactory(appContext));
		}
		catch (InvalidDsnException ie){
			Sentry.init(new AndroidSentryClientFactory(appContext));
		}

		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(appContext));
		PersistentData.initialize( appContext );
		TextFileManager.initialize( appContext );
		PostRequest.initialize( appContext );
		localHandle = this;  //yes yes, hacky, I know.
		registerTimers(appContext);
		
		doSetup();
	}

	public void doSetup() {
		//Accelerometer and power state don't need permissons
		startPowerStateListener();
		gpsListener = new GPSListener(appContext); // Permissions are checked in the broadcast receiver
		WifiListener.initialize( appContext );
		if ( PersistentData.getAccelerometerEnabled() ) { accelerometerListener = new AccelerometerListener( appContext ); }
		if ( PersistentData.getGyroscopeEnabled() ) { gyroscopeListener = new GyroscopeListener( appContext ); }
		//Bluetooth, wifi, gps, calls, and texts need permissions
		if ( PermissionHandler.confirmBluetooth(appContext)) { startBluetooth(); }
//		if ( PermissionHandler.confirmWifi(appContext) ) { WifiListener.initialize( appContext ); }
		if ( PermissionHandler.confirmTexts(appContext) ) { startSmsSentLogger(); startMmsSentLogger(); }
		else if(PersistentData.getTextsEnabled() ){ sendBroadcast(Timer.checkForSMSEnabled); }
		if ( PermissionHandler.confirmCalls(appContext) ) { startCallLogger(); }
		else if (PersistentData.getCallsEnabled() ) { sendBroadcast(Timer.checkForCallsEnabled); }
		//Only do the following if the device is registered
		if ( PersistentData.isRegistered() ) {
			DeviceInfo.initialize( appContext ); //if at registration this has already been initialized. (we don't care.)
			startTimers();
		}
	}
	
	/** Stops the BackgroundService instance. */
	public void stop() { if (BuildConfig.APP_IS_BETA) { this.stopSelf(); } }
	
	/*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/
	
	/** Initializes the Bluetooth listener 
	 * Note: Bluetooth has several checks to make sure that it actually exists on the device with the capabilities we need.
	 * Checking for Bluetooth LE is necessary because it is an optional extension to Bluetooth 4.0. */
	@SuppressLint("LongLogTag")
    public void startBluetooth(){
		//Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument constructor in order for android can instantiate it on broadcast receipts.
		//The following check must be made, but it requires a Context that we cannot pass into the BluetoothListener, so we do the check in the BackgroundService.
		if ( appContext.getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) && PersistentData.getBluetoothEnabled() ) {
			this.bluetoothListener = new BluetoothListener();
			if ( this.bluetoothListener.isBluetoothEnabled() ) {
//				Log.i("Background Service", "success, actually doing bluetooth things.");
				registerReceiver(this.bluetoothListener, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED") ); }
			else {
				//TODO: Low priority. Eli. Track down why this error log pops up, cleanup.  -- the above check should be for the (new) doesBluetoothCapabilityExist function instead of isBluetoothEnabled
				Log.e("Background Service", "bluetooth Failure. Should not have gotten this far.");
				TextFileManager.getDebugLogFile().writeEncrypted("bluetooth Failure, device should not have gotten to this line of code"); }
		}
		else {
			if (PersistentData.getBluetoothEnabled()) {
				TextFileManager.getDebugLogFile().writeEncrypted("Device does not support bluetooth LE, bluetooth features disabled.");
				Log.w("BackgroundService bluetooth init", "Device does not support bluetooth LE, bluetooth features disabled."); }
			// else { Log.d("BackgroundService bluetooth init", "Bluetooth not enabled for study."); }
			this.bluetoothListener = null; }
	}
	
	/** Initializes the sms logger. */
	public void startSmsSentLogger() {
		SmsSentLogger smsSentLogger = new SmsSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsSentLogger); }
	
	public void startMmsSentLogger(){
		MMSSentLogger mmsMonitor = new MMSSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://mms/"), true, mmsMonitor); }

	/** Initializes the call logger. */
	private void startCallLogger() {
		CallLogger callLogger = new CallLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://call_log/calls/"), true, callLogger); }
	
	/** Initializes the PowerStateListener. 
	 * The PowerStateListener requires the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents
	 * be registered programatically. They do not work if registered in the app's manifest.
	 * Same for the ACTION_POWER_SAVE_MODE_CHANGED and ACTION_DEVICE_IDLE_MODE_CHANGED filters,
	 * though they are for monitoring deeper power state changes in 5.0 and 6.0, respectively. */
	@SuppressLint("InlinedApi")
	private void startPowerStateListener() {
		if(powerStateListener == null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
			}
			if (android.os.Build.VERSION.SDK_INT >= 23) {
				filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
			}
			powerStateListener = new PowerStateListener();
			registerReceiver(powerStateListener, filter);
			PowerStateListener.start(appContext);
		}
	}
	
	
	/** create timers that will trigger events throughout the program, and
	 * register the custom Intents with the controlMessageReceiver. */
	@SuppressWarnings("static-access")
	public static void registerTimers(Context appContext) {
		localHandle.timer = new Timer(localHandle);
		IntentFilter filter = new IntentFilter();
		filter.addAction( appContext.getString( R.string.turn_accelerometer_off ) );
		filter.addAction(appContext.getString( R.string.turn_gyroscope_off ));
		filter.addAction( appContext.getString( R.string.turn_accelerometer_on ) );
		filter.addAction( appContext.getString( R.string.turn_gyroscope_on ) );
		filter.addAction( appContext.getString( R.string.turn_bluetooth_off ) );
		filter.addAction( appContext.getString( R.string.turn_bluetooth_on ) );
		filter.addAction( appContext.getString( R.string.turn_gps_off ) );
		filter.addAction( appContext.getString( R.string.turn_gps_on ) );
		filter.addAction( appContext.getString( R.string.signout_intent ) );
		filter.addAction( appContext.getString( R.string.voice_recording ) );
		filter.addAction( appContext.getString( R.string.run_wifi_log ) );
		filter.addAction( appContext.getString( R.string.upload_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.create_new_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.check_for_new_surveys_intent ) );
		filter.addAction( appContext.getString( R.string.check_for_sms_enabled ) );
		filter.addAction( appContext.getString( R.string.check_for_calls_enabled ) );
		filter.addAction( ConnectivityManager.CONNECTIVITY_ACTION );
		filter.addAction("crashBeiwe");
		filter.addAction("enterANR");
		filter.addAction(appContext.getString(R.string.check_app_usage_intent));
		List<String> surveyIds = PersistentData.getSurveyIds();
		for (String surveyId : surveyIds) { filter.addAction(surveyId); }
		appContext.registerReceiver(localHandle.timerReceiver, filter);
	}
	
	/*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/
	
	public void startTimers() {
		Long now = System.currentTimeMillis();
		Log.i("BackgroundService", "running startTimer logic.");
		if (PersistentData.getAccelerometerEnabled()) {  //if accelerometer data recording is enabled and...
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.turn_accelerometer_on )) < now || //the most recent accelerometer alarm time is in the past, or...
					!timer.alarmIsSet(Timer.accelerometerOnIntent) ) { //there is no scheduled accelerometer-on timer.
				sendBroadcast(Timer.accelerometerOnIntent); // start accelerometer timers (immediately runs accelerometer recording session).
				//note: when there is no accelerometer-off timer that means we are in-between scans.  This state is fine, so we don't check for it.
			}
			else if(timer.alarmIsSet(Timer.accelerometerOffIntent)
					&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_accelerometer_on )) - PersistentData.getAccelerometerOffDurationMilliseconds() + 1000 > now ) {
				accelerometerListener.turn_on();
			}
		}
		if (PersistentData.getGyroscopeEnabled()) {  //if gyroscope data recording is enabled and...
			if(PersistentData.getMostRecentAlarmTime( getString(R.string.turn_gyroscope_on )) < now || //the most recent gyroscope alarm time is in the past, or...
					!timer.alarmIsSet(Timer.gyroscopeOnIntent) ) { //there is no scheduled gyroscope-on timer.
				sendBroadcast(Timer.gyroscopeOnIntent); // start gyroscope timers (immediately runs gyroscope recording session).
				//note: when there is no gyroscope-off timer that means we are in-between scans.  This state is fine, so we don't check for it.
			}
			else if(timer.alarmIsSet(Timer.gyroscopeOffIntent)
					&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gyroscope_on )) - PersistentData.getGyroscopeOffDurationMilliseconds() + 1000 > now ) {
				gyroscopeListener.turn_on();
			}
		}
		if ( PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gps_on )) < now || !timer.alarmIsSet(Timer.gpsOnIntent) ) {
			sendBroadcast( Timer.gpsOnIntent ); }
		else if(PersistentData.getGpsEnabled() && timer.alarmIsSet(Timer.gpsOffIntent)
				&& PersistentData.getMostRecentAlarmTime(getString( R.string.turn_gps_on )) - PersistentData.getGpsOffDurationMilliseconds() + 1000 > now ) {
			gpsListener.turn_on();
		}
		
		if ( PersistentData.getMostRecentAlarmTime( getString(R.string.run_wifi_log)) < now || //the most recent wifi log time is in the past or
				!timer.alarmIsSet(Timer.wifiLogIntent) ) {
			sendBroadcast( Timer.wifiLogIntent ); }
		
		//if Bluetooth recording is enabled and there is no scheduled next-bluetooth-enable event, set up the next Bluetooth-on alarm.
		//(Bluetooth needs to run at absolute points in time, it should not be started if a scheduled event is missed.)
		if ( PermissionHandler.confirmBluetooth(appContext) && !timer.alarmIsSet(Timer.bluetoothOnIntent)) {
			timer.setupExactSingleAbsoluteTimeAlarm(PersistentData.getBluetoothTotalDurationMilliseconds(), PersistentData.getBluetoothGlobalOffsetMilliseconds(), Timer.bluetoothOnIntent); }
		
		// Functionality timers. We don't need aggressive checking for if these timers have been missed, as long as they run eventually it is fine.
		if (!timer.alarmIsSet(Timer.uploadDatafilesIntent)) { timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequencyMilliseconds(), Timer.uploadDatafilesIntent); }
		if (!timer.alarmIsSet(Timer.createNewDataFilesIntent)) { timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequencyMilliseconds(), Timer.createNewDataFilesIntent); }
		if (!timer.alarmIsSet(Timer.checkForNewSurveysIntent)) { timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequencyMilliseconds(), Timer.checkForNewSurveysIntent); }

		if (!timer.alarmIsSet(Timer.appUsageIntent)) {
			timer.setupExactSingleAlarm(PersistentData.getCheckAppUsageFrequencyMilliseconds(), Timer.appUsageIntent);
		}

		//checks for the current expected state for survey notifications,
		for (String surveyId : PersistentData.getSurveyIds() ){
			if ( PersistentData.getSurveyNotificationState(surveyId) || PersistentData.getMostRecentSurveyAlarmTime(surveyId) < now ) {
				//if survey notification should be active or the most recent alarm time is in the past, trigger the notification.
				SurveyNotifications.displaySurveyNotification(appContext, surveyId); } }
		
		//checks that surveys are actually scheduled, if a survey is not scheduled, schedule it!
		for (String surveyId : PersistentData.getSurveyIds() ) {
			if ( !timer.alarmIsSet( new Intent(surveyId) ) ) { SurveyScheduler.scheduleSurvey(surveyId); } }

		Intent restartServiceIntent = new Intent( getApplicationContext(), BackgroundService.class);
		restartServiceIntent.setPackage( getPackageName() );
		PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, 0 );
		AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService( Context.ALARM_SERVICE );
		Objects.requireNonNull(alarmService).setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000 * 60 * 2, 1000 * 60 * 2, restartServicePendingIntent);
	}
	
	/**Refreshes the logout timer.
	 * This function has a THEORETICAL race condition, where the BackgroundService is not fully instantiated by a session activity,
	 * in this case we log an error to the debug log, print the error, and then wait for it to crash.  In testing on a (much) older
	 * version of the app we would occasionally see the error message, but we have never (august 10 2015) actually seen the app crash
	 * inside this code. */
	public static void startAutomaticLogoutCountdownTimer(){
		if (timer == null) {
			Log.e("bacgroundService", "timer is null, BackgroundService may be about to crash, the Timer was null when the BackgroundService was supposed to be fully instantiated.");
			TextFileManager.getDebugLogFile().writeEncrypted("our not-quite-race-condition encountered, Timer was null when the BackgroundService was supposed to be fully instantiated");
		}
		timer.setupExactSingleAlarm(PersistentData.getMillisecondsBeforeAutoLogout(), Timer.signoutIntent);
		PersistentData.loginOrRefreshLogin();
	}
	
	/** cancels the signout timer */
	public static void clearAutomaticLogoutCountdownTimer() { timer.cancelAlarm(Timer.signoutIntent); }
	
	/** The Timer requires the BackgroundService in order to create alarms, hook into that functionality here. */
	public static void setSurveyAlarm(String surveyId, Calendar alarmTime) { timer.startSurveyAlarm(surveyId, alarmTime); }

	public static void cancelSurveyAlarm(String surveyId) { timer.cancelAlarm(new Intent(surveyId)); }
	
	/**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
	 * and then runs the appropriate code for that trigger. 
	 * Note: every condition has a return statement at the end; this is because the trigger survey notification
	 * action requires a fairly expensive dive into PersistantData JSON unpacking.*/
	private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
		@SuppressLint("LongLogTag")
        @Override public void onReceive(Context appContext, Intent intent) {
			Log.d("BackgroundService - timers", "Received broadcast: " + intent.toString() );
			TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " Received Broadcast: " + intent.toString() );
			String broadcastAction = intent.getAction();

			/* For GPS and Accelerometer the failure modes are:
			  1. If a recording event is triggered and followed by Doze being enabled then Beiwe will record until the Doze period ends.
			  2. If, after Doze ends, the timers trigger out of order Beiwe ceaces to record and triggers a new recording event in the future. */

			/* Disable active sensor */
			if (Objects.requireNonNull(broadcastAction).equals( appContext.getString(R.string.turn_accelerometer_off) ) ) {
				accelerometerListener.turn_off();
				return; }
			if (broadcastAction.equals( appContext.getString(R.string.turn_gyroscope_off) ) ) {
				gyroscopeListener.turn_off();
				return; }
			if (broadcastAction.equals( appContext.getString(R.string.turn_gps_off) ) ) {
				if ( PermissionHandler.checkGpsPermissions(appContext) ) { gpsListener.turn_off(); }
				return; }

			/* Enable active sensors, reset timers. */
			//Accelerometer. We automatically have permissions required for accelerometer.
			if (broadcastAction.equals( appContext.getString(R.string.turn_accelerometer_on) ) ) {
				if ( !PersistentData.getAccelerometerEnabled() ) { Log.e("BackgroundService Listener", "invalid Accelerometer on received"); return; }
				accelerometerListener.turn_on();
				//start both the sensor-off-action timer, and the next sensor-on-timer.
				timer.setupExactSingleAlarm(PersistentData.getAccelerometerOnDurationMilliseconds(), Timer.accelerometerOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getAccelerometerOffDurationMilliseconds() + PersistentData.getAccelerometerOnDurationMilliseconds(), Timer.accelerometerOnIntent);
				//record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash. 
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_accelerometer_on), alarmTime );
				return; }
			//Gyroscope. Almost identical logic to accelerometer above.
			if (broadcastAction.equals( appContext.getString(R.string.turn_gyroscope_on) ) ) {
				if ( !PersistentData.getGyroscopeEnabled() || !gyroscopeListener.exists ) { Log.e("BackgroundService Listener", "invalid Gyroscope on received"); return; }
				gyroscopeListener.turn_on();
				//start both the sensor-off-action timer, and the next sensor-on-timer.
				timer.setupExactSingleAlarm(PersistentData.getGyroscopeOnDurationMilliseconds(), Timer.gyroscopeOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getGyroscopeOffDurationMilliseconds() + PersistentData.getGyroscopeOnDurationMilliseconds(), Timer.gyroscopeOnIntent);
				//record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash.
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gyroscope_on), alarmTime );
				return;
			}
			//GPS. Almost identical logic to accelerometer above.
			if (broadcastAction.equals( appContext.getString(R.string.turn_gps_on) ) ) {
				if ( !PersistentData.getGpsEnabled() ) { Log.e("BackgroundService Listener", "invalid GPS on received"); return; }
				gpsListener.turn_on();
				timer.setupExactSingleAlarm(PersistentData.getGpsOnDurationMilliseconds(), Timer.gpsOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getGpsOnDurationMilliseconds() + PersistentData.getGpsOffDurationMilliseconds(), Timer.gpsOnIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gps_on), alarmTime );
				return; }
			//run a wifi scan.  Most similar to GPS, but without an off-timer.
			if (broadcastAction.equals( appContext.getString(R.string.run_wifi_log) ) ) {
				if ( !PersistentData.getWifiEnabled() ) { Log.e("BackgroundService Listener", "invalid WiFi scan received"); return; }
				if ( PermissionHandler.checkWifiPermissions(appContext) ) { WifiListener.scanWifi(); }
				else { TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Wifi."); }
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getWifiLogFrequencyMilliseconds(), Timer.wifiLogIntent);
				PersistentData.setMostRecentAlarmTime( getString(R.string.run_wifi_log), alarmTime );
				return; }
			
			/* Bluetooth timers are unlike GPS and Accelerometer because it uses an absolute-point-in-time as a trigger, and therefore we don't need to store most-recent-timer state.
			  The Bluetooth-on action sets the corresponding Bluetooth-off timer, the Bluetooth-off action sets the next Bluetooth-on timer.*/
			if (broadcastAction.equals( appContext.getString(R.string.turn_bluetooth_on) ) ) {
				if ( !PersistentData.getBluetoothEnabled() ) { Log.e("BackgroundService Listener", "invalid Bluetooth on received"); return; }
				if ( PermissionHandler.checkBluetoothPermissions(appContext) ) {
					if (bluetoothListener != null) bluetoothListener.enableBLEScan(); }
				else { TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Bluetooth."); }
				timer.setupExactSingleAlarm(PersistentData.getBluetoothOnDurationMilliseconds(), Timer.bluetoothOffIntent);
				return; }
			if (broadcastAction.equals( appContext.getString(R.string.turn_bluetooth_off) ) ) {
				if ( PermissionHandler.checkBluetoothPermissions(appContext) ) {
					if ( bluetoothListener != null) bluetoothListener.disableBLEScan(); }
				timer.setupExactSingleAbsoluteTimeAlarm(PersistentData.getBluetoothTotalDurationMilliseconds(), PersistentData.getBluetoothGlobalOffsetMilliseconds(), Timer.bluetoothOnIntent);
				return; }			

			if (broadcastAction.equals(appContext.getString(R.string.check_app_usage_intent))) {
				if (PermissionHandler.checkAppUsagePermission(appContext) && appUsageListener != null) {
					try {
						Log.i("BackgroundService", "Starting app usage...");
						appUsageListener.getAppUsage();
					} catch (PackageManager.NameNotFoundException e) {
						e.printStackTrace();
					}
				}
				timer.setupExactSingleAlarm(PersistentData.getCheckAppUsageFrequencyMilliseconds(), Timer.appUsageIntent);
				return;
			}
			//starts a data upload attempt.
			if (broadcastAction.equals( appContext.getString(R.string.upload_data_files_intent) ) ) {
				PostRequest.uploadAllFiles();
				timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequencyMilliseconds(), Timer.uploadDatafilesIntent);
				return; }
			//creates new data files
			if (broadcastAction.equals( appContext.getString(R.string.create_new_data_files_intent) ) ) {
				TextFileManager.makeNewFilesForEverything();
				timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequencyMilliseconds(), Timer.createNewDataFilesIntent);
                PostRequest.uploadAllFiles();
				return; }
			//Downloads the most recent survey questions and schedules the surveys.
			if (broadcastAction.equals( appContext.getString(R.string.check_for_new_surveys_intent))) {
				SurveyDownloader.downloadSurveys( getApplicationContext() );
				timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequencyMilliseconds(), Timer.checkForNewSurveysIntent);
				return; }
			// Signs out the user. (does not set up a timer, that is handled in activity and sign-in logic) 
			if (broadcastAction.equals( appContext.getString(R.string.signout_intent) ) ) {
				PersistentData.logout();
				Intent loginPage = new Intent(appContext, LoginActivity.class);
				loginPage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				appContext.startActivity(loginPage);
				return; }

			if (broadcastAction.equals( appContext.getString(R.string.check_for_sms_enabled) ) ) {
				if ( PermissionHandler.confirmTexts(appContext) ) { startSmsSentLogger(); startMmsSentLogger(); }
				else if (PersistentData.getTextsEnabled() ) { timer.setupExactSingleAlarm(30000L, Timer.checkForSMSEnabled); }
			}
			if (broadcastAction.equals( appContext.getString(R.string.check_for_calls_enabled) ) ) {
				if ( PermissionHandler.confirmCalls(appContext) ) { startCallLogger(); }
				else if (PersistentData.getCallsEnabled() ) { timer.setupExactSingleAlarm(30000L, Timer.checkForCallsEnabled); }
			}
			//checks if the action is the id of a survey (expensive), if so pop up the notification for that survey, schedule the next alarm
			if ( PersistentData.getSurveyIds().contains( broadcastAction ) ) {
//				Log.i("BACKGROUND SERVICE", "new notification: " + broadcastAction);
				SurveyNotifications.displaySurveyNotification(appContext, broadcastAction);
				SurveyScheduler.scheduleSurvey(broadcastAction);
				return; }

			if ( PersistentData.isRegistered() && broadcastAction.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
				if(Objects.requireNonNull(networkInfo).getType() == ConnectivityManager.TYPE_WIFI) {
					PostRequest.uploadAllFiles();
					return;
				}
			}

			//this is a special action that will only run if the app device is in debug mode.
			if (broadcastAction.equals("crashBeiwe") && BuildConfig.APP_IS_BETA) {
				throw new NullPointerException("beeeeeoooop."); }
			//this is a special action that will only run if the app device is in debug mode.
			if (broadcastAction.equals("enterANR") && BuildConfig.APP_IS_BETA) {
				try {
					Thread.sleep(100000);
				}
				catch(InterruptedException ie) {
					ie.printStackTrace();
				}
			}
		}
	};
		
	/*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/
	@Override
	public IBinder onBind(Intent arg0) { return new BackgroundServiceBinder(); }
	
	/**A public "Binder" class for Activities to access.
	 * Provides a (safe) handle to the background Service using the onStartCommand code
	 * used in every RunningBackgroundServiceActivity */
	public class BackgroundServiceBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }
	
	/*##############################################################################
	########################## Android Service Lifecycle ###########################
	##############################################################################*/
	
	/** The BackgroundService is meant to be all the time, so we return START_STICKY */
	@Override public int onStartCommand(Intent intent, int flags, int startId){ //Log.d("BackgroundService onStartCommand", "started with flag " + flags );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"started with flag " + flags);
		return START_STICKY;
		//we are testing out this restarting behavior for the service.  It is entirely unclear that this will have any observable effect.
		//return START_REDELIVER_INTENT;
	}
	//(the rest of these are identical, so I have compactified it)
	@Override public void onTaskRemoved(Intent rootIntent) { //Log.d("BackroundService onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onTaskRemoved called with intent: " + rootIntent.toString());
		restartService(); }
	@Override public boolean onUnbind(Intent intent) { //Log.d("BackroundService onUnbind", "onUnbind called with intent: " + intent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onUnbind called with intent: " + intent.toString());
		restartService();
		return super.onUnbind(intent); }
	@Override public void onDestroy() { //Log.w("BackgroundService", "BackgroundService was destroyed.");
		//note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"BackgroundService was destroyed.");
		restartService();
		super.onDestroy(); }
	@Override public void onLowMemory() { //Log.w("BackroundService onLowMemory", "Low memory conditions encountered");
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onLowMemory called.");
		restartService(); }
	
	/** Sets a timer that starts the service if it is not running in ten seconds. */
	private void restartService(){
		//how does this even...  Whatever, 10 seconds later the background service will start.
		Intent restartServiceIntent = new Intent( getApplicationContext(), this.getClass() );
	    restartServiceIntent.setPackage( getPackageName() );
	    PendingIntent restartServicePendingIntent = PendingIntent.getService( getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT );
	    AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService( Context.ALARM_SERVICE );
	    Objects.requireNonNull(alarmService).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restartServicePendingIntent);
	}
	
	public void crashBackgroundService() { if (BuildConfig.APP_IS_BETA) {
		throw new NullPointerException("stop poking me!"); } }
}