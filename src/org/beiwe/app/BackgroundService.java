package org.beiwe.app;

import java.util.Calendar;

import org.beiwe.app.listeners.AccelerometerListener;
import org.beiwe.app.listeners.BluetoothListener;
import org.beiwe.app.listeners.CallLogger;
import org.beiwe.app.listeners.GPSListener;
import org.beiwe.app.listeners.MMSSentLogger;
import org.beiwe.app.listeners.PowerStateListener;
import org.beiwe.app.listeners.SmsSentLogger;
import org.beiwe.app.listeners.WifiListener;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.survey.SurveyDownloader;
import org.beiwe.app.survey.SurveyScheduler;
import org.beiwe.app.ui.user.LoginActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

//TODO: ack for backgroundprocess and replace with backgroundservice

public class BackgroundService extends Service {
	private Context appContext;
	// TODO: postproduction. Make these private after killing DebugInterfaceActivity
	public GPSListener gpsListener;
	public AccelerometerListener accelerometerListener;
	public BluetoothListener bluetoothListener;
	private static Timer timer;
	
	@Override
	/** onCreate is essentially the constructor for the service, initialize variables here.*/
	public void onCreate() {
		appContext = this.getApplicationContext();
		
		DeviceInfo.initialize( getApplicationContext() );
		PersistentData.initialize( getApplicationContext() );
		TextFileManager.initialize( getApplicationContext() );
		PostRequest.initialize( getApplicationContext() );
		WifiListener.initialize( getApplicationContext() );
		
		gpsListener = new GPSListener(appContext);
		accelerometerListener = new AccelerometerListener( appContext );
		startBluetooth();
		startSmsSentLogger();
		startMmsSentLogger();
		startCallLogger();
		startPowerStateListener();
		registerTimers();
		
		DeviceInfo.getPhoneNumber();
		//If this device is registered, start timers!
		if (PersistentData.isRegistered()) { startTimers(); }
	}

	
	/*##############################################################################
	########################## Android Service Lifecycle ###########################
	##############################################################################*/
	
	@Override
	/** The BackgroundService is meant to be all the time, so we return START_STICKY */
	// We could also use, and may change it if we encounter problems, START_REDELIVER_INTENT, which has nearly identical behavior.
	public int onStartCommand(Intent intent, int flags, int startId){ //Log.d("BackroundProcess onStartCommand", "started with flag " + flags );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"started with flag " + flags);
		return START_STICKY; }
	//(the rest of these are identical, so I have compactified it)
	@Override public void onTaskRemoved(Intent rootIntent) { //Log.d("BackroundProcess onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onTaskRemoved called with intent: " + rootIntent.toString());
		restartService(); }
	@Override public boolean onUnbind(Intent intent) { //Log.d("BackroundProcess onUnbind", "onUnbind called with intent: " + intent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onUnbind called with intent: " + intent.toString());
		restartService();
		return super.onUnbind(intent); }
	@Override public void onDestroy() { //Log.w("BackgroundProcess", "BACKGROUNDPROCESS WAS DESTROYED.");
		//note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"BACKGROUNDPROCESS WAS DESTROYED.");
		restartService();
		super.onDestroy(); }
	@Override public void onLowMemory() { //Log.w("BackroundProcess onLowMemory", "Low memory conditions encountered");
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" "+"onLowMemory called.");
		restartService(); }
	
	/** Sets a timer that starts the service if it is not running in ten seconds. */
	private void restartService(){
		//how does this even...  Whatever, 10 seconds later the background service will start.
		Intent restartServiceIntent = new Intent( getApplicationContext(), this.getClass() );
	    restartServiceIntent.setPackage( getPackageName() );
	    // TODO: PendingIntent.FLAG_ONE_SHOT would probably be better if changed to FLAG_CANCEL_CURRENT, but this might be a pain to test
	    PendingIntent restartServicePendingIntent = PendingIntent.getService( getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT );
	    AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService( Context.ALARM_SERVICE );
	    alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restartServicePendingIntent);
	}
	
	/** Stops the BackgroundProcess instance. */
	public void stop() { this.stopSelf(); }
	
	/*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/
	
	/** Initializes the Bluetooth listener 
	 * Note: Bluetooth has several checks to make sure that it actually exists on the device with the capabilities we need.
	 * Checking for Bluetooth LE is necessary because it is an optional extension to Bluetooth 4.0. */
	public void startBluetooth(){
		//Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument constructor so android can instantiate it on broadcast receipts.
		//The following check must be made, but it requires a Context that we cannot pass into the BluetoothListener, so we do the check in the BackgroundProcess.
		if ( appContext.getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) ) {
			this.bluetoothListener = new BluetoothListener(); 
			if ( this.bluetoothListener.isBluetoothEnabled() ) {
				Log.i("Background Process", "success, actually doing bluetooth things.");
				registerReceiver(this.bluetoothListener, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED") );
			} else {
				Log.e("Background Process", "bluetooth Failure. Should not have gotten this far.");
				TextFileManager.getDebugLogFile().writeEncrypted("bluetooth Failure, device should not have gotten to this line of code");
			}
		}
		else {
			TextFileManager.getDebugLogFile().writeEncrypted("Device does not support bluetooth LE, bluetooth features disabled.");
			this.bluetoothListener = null; } 
	}
	
	/** Initializes the sms logger. */
	public void startSmsSentLogger() {
		SmsSentLogger smsSentLogger = new SmsSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsSentLogger); }
	
	public void startMmsSentLogger(){
		MMSSentLogger mmsMonitor = new MMSSentLogger(new Handler(), appContext);
		//this is retarded, it needs to be mms-sms here, and just "mms" in the mms code
		this.getContentResolver().registerContentObserver(Uri.parse("content://mms-sms/conversation?simple=true"), true, mmsMonitor); }
	
	/** Initializes the call logger. */
	private void startCallLogger() {
		CallLogger callLogger = new CallLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://call_log/calls/"), true, callLogger); }
	
	/** Initializes the PowerStateListener. 
	 * The PowerStateListener required the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents
	 * be registered programmatically.  They do not work if registered in the app's manifest. */
	private void startPowerStateListener() {
		IntentFilter filter = new IntentFilter(); 
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver( (BroadcastReceiver) new PowerStateListener(), filter);
		PowerStateListener.start();
	}
	
	/** create timers that will trigger events throughout the program, and
	 * register the custom Intents with the controlMessageReceiver. */
	private void registerTimers() {
		timer = new Timer(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction( appContext.getString( R.string.accelerometer_off ) );
		filter.addAction( appContext.getString( R.string.accelerometer_on ) );
		filter.addAction( appContext.getString( R.string.bluetooth_off ) );
		filter.addAction( appContext.getString( R.string.bluetooth_on ) );
		filter.addAction( appContext.getString( R.string.gps_off ) );
		filter.addAction( appContext.getString( R.string.gps_on ) );
		filter.addAction( appContext.getString( R.string.signout_intent ) );
		filter.addAction( appContext.getString( R.string.voice_recording ) );
		filter.addAction( appContext.getString( R.string.daily_survey ) );
		filter.addAction( appContext.getString( R.string.weekly_survey ) );
		filter.addAction( appContext.getString( R.string.run_wifi_log ) );
		filter.addAction( appContext.getString( R.string.upload_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.create_new_data_files_intent ) );
		filter.addAction( appContext.getString( R.string.check_for_new_surveys_intent ) );
		registerReceiver(timerReceiver, filter);
	}
	
	//TODO: Eli/Josh. check that registerTimers and startTimers code is still okay after custom timers are implemented.
	
	/*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/
	
	public void startTimers() {
		// Sensor timers.
		if (!timer.alarmIsSet(Timer.accelerometerOnIntent) && !timer.alarmIsSet(Timer.accelerometerOffIntent)) {
			timer.setupFuzzySinglePowerOptimizedAlarm( Timer.ACCELEROMETER_OFF_MINIMUM_DURATION, Timer.accelerometerOnIntent); }
		if (!timer.alarmIsSet(Timer.gpsOnIntent) && !timer.alarmIsSet(Timer.gpsOffIntent)) {
			timer.setupFuzzySinglePowerOptimizedAlarm( Timer.GPS_OFF_MINIMUM_DURATION, Timer.gpsOnIntent); }
		if (!timer.alarmIsSet(Timer.bluetoothOnIntent) && !timer.alarmIsSet(Timer.bluetoothOffIntent)) {
			timer.setupExactTimeAlarm(Timer.BLUETOOTH_PERIOD, Timer.BLUETOOTH_START_TIME_IN_PERIOD, Timer.bluetoothOnIntent); }
		if (!timer.alarmIsSet(Timer.wifiLogIntent)) {
			timer.setupFuzzyPowerOptimizedRepeatingAlarm(Timer.WIFI_LOG_PERIOD, Timer.wifiLogIntent); }
		
		// Functionality timers.
		if (!timer.alarmIsSet(Timer.uploadDatafilesIntent)) {	
			timer.setupFuzzyPowerOptimizedRepeatingAlarm(Timer.UPLOAD_DATA_FILES_PERIOD, Timer.uploadDatafilesIntent); }
		if (!timer.alarmIsSet(Timer.createNewDataFilesIntent)) {
			timer.setupFuzzyPowerOptimizedRepeatingAlarm(Timer.CREATE_NEW_DATA_FILES_PERIOD, Timer.createNewDataFilesIntent); }
		if (!timer.alarmIsSet(Timer.checkForNewSurveysIntent)) {
			timer.setupFuzzyPowerOptimizedRepeatingAlarm(Timer.CHECK_FOR_NEW_SURVEYS_PERIOD, Timer.checkForNewSurveysIntent); }

		//checks for the current expected state with app notifications. (must be run before we potentially set new alarms)
		Long now = System.currentTimeMillis();
		//TODO: Eli. fairly sure that these should run the (to be implemented) getPriorSurveyAlarmTime function.  (fully aware that the use of "prior" can be confusing.
		for (String surveyId : PersistentData.getSurveyIds() ){
			if ( PersistentData.getSurveyNotificationState(surveyId) || PersistentData.getPriorSurveyAlarmTime(surveyId) < now ) {
				SurveyNotifications.displaySurveyNotification(appContext, surveyId);
			}
		}
		
		for (String surveyId : PersistentData.getSurveyIds() ) { //check each survey to ensure it is scheduled.
			if ( !timer.alarmIsSet( new Intent(surveyId) ) ) { SurveyScheduler.scheduleSurvey(surveyId); } }
	}
	
	public static void startAutomaticLogoutCountdownTimer(){
		//TODO: Eli/Josh.  this.
		//note: this function is static due to the evolution of the connections activities have to the background process,
		// it probably is better practice to make this non-static, but we are leaving it as is so we don't have to test
		// this type of low-level operational difference.
		if (timer == null) {
			Log.w("bacgroundProcess", "timer is null, this is about to crash");
			TextFileManager.getDebugLogFile().writeEncrypted("our not-quite-race-condition encountered, the timer was null when the background process was supposed to be instantiated");
		}
		timer.setupExactSingleAlarm(Timer.MILLISECONDS_BEFORE_AUTO_LOGOUT, Timer.signoutIntent);
		PersistentData.loginOrRefreshLogin();
	}
	
	/** cancels the signout timer */
	public static void clearAutomaticLogoutCountdownTimer() { timer.cancelAlarm(Timer.signoutIntent); }
	
	/** The Timer requires the BackgroundService in order to create alarms, hook into that functionality here. */
	public static void setSurveyAlarm(String surveyId, Calendar alarmTime) { timer.startSurveyAlarm(surveyId, alarmTime); }
	
	/**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
	 * and then runs the appropriate code for that trigger. */
	private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context appContext, Intent intent) {
			Log.d("BackgroundService - timers", "Received broadcast: " + intent.toString() );
			TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " Received Broadcast: " + intent.toString() );
			String broadcastAction = intent.getAction();
			
			//sets the next trigger time for the accelerometer to record data 
			if (broadcastAction.equals( appContext.getString(R.string.accelerometer_off) ) ) {
				accelerometerListener.turn_off();
				timer.setupFuzzySinglePowerOptimizedAlarm(Timer.ACCELEROMETER_OFF_MINIMUM_DURATION, Timer.accelerometerOnIntent);
				return; }
			
			//sets a timer that will turn off the accelerometer
			if (broadcastAction.equals( appContext.getString(R.string.accelerometer_on) ) ) {
				accelerometerListener.turn_on();
				timer.setupExactSingleAlarm(Timer.ACCELEROMETER_ON_DURATION, Timer.accelerometerOffIntent);
				return; }
			
			//sets the next trigger time for the bluetooth scan to record data
			if (broadcastAction.equals( appContext.getString(R.string.bluetooth_off) ) ) {
				if (bluetoothListener != null) bluetoothListener.disableBLEScan();
				timer.setupExactTimeAlarm(Timer.BLUETOOTH_PERIOD, Timer.BLUETOOTH_START_TIME_IN_PERIOD, Timer.bluetoothOnIntent);
				return; }
			
			//sets a timer that will turn off the bluetooth scan
			if (broadcastAction.equals( appContext.getString(R.string.bluetooth_on) ) ) {
				if (bluetoothListener != null) bluetoothListener.enableBLEScan();
				timer.setupExactSingleAlarm(Timer.BLUETOOTH_ON_DURATION, Timer.bluetoothOffIntent);
				return; }
			
			//sets the next trigger time for the gps to record data
			if (broadcastAction.equals( appContext.getString(R.string.gps_off) ) ) {
				gpsListener.turn_off();
				timer.setupFuzzySinglePowerOptimizedAlarm(Timer.GPS_OFF_MINIMUM_DURATION, Timer.gpsOnIntent);
				return; }
			
			//sets a timer that will turn off the gps
			if (broadcastAction.equals( appContext.getString(R.string.gps_on) ) ) {
				gpsListener.turn_on();
				timer.setupExactSingleAlarm(Timer.GPS_ON_DURATION, Timer.gpsOffIntent);
				return; }
			
			//runs a wifi scan
			if (broadcastAction.equals( appContext.getString(R.string.run_wifi_log) ) ) {
				WifiListener.scanWifi();
				return; }
						
			//runs the user signout logic, bumping the user to the login screen.
			if (broadcastAction.equals( appContext.getString(R.string.signout_intent) ) ) {
				PersistentData.logout();
				Intent loginPage = new Intent(appContext, LoginActivity.class);
				loginPage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				appContext.startActivity(loginPage);
				return; }
			
			//starts a data upload attempt.
			if (broadcastAction.equals( appContext.getString(R.string.upload_data_files_intent) ) ) {
				PostRequest.uploadAllFiles();
				return; }

			//creates new data files
			if (broadcastAction.equals( appContext.getString(R.string.create_new_data_files_intent) ) ) {
				TextFileManager.makeNewFilesForEverything();
				return; }

			//Downloads the most recent survey questions and schedules the surveys.
			if (broadcastAction.equals( appContext.getString(R.string.check_for_new_surveys_intent))) {
				SurveyDownloader.downloadSurveys( getApplicationContext() );
				return; }
			
			//checks if the action is the id of a survey, if so pop up the notification for that survey, schedule the next alarm
			if ( PersistentData.getSurveyIds().contains( broadcastAction ) ) {
				SurveyNotifications.displaySurveyNotification(appContext, broadcastAction);
				//TODO: Eli. make sure that the survey notification code properly sets the notification state
				SurveyScheduler.scheduleSurvey(broadcastAction);
				return; }
		}
	};
	
	/*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/
	@Override
	public IBinder onBind(Intent arg0) { return new BackgroundProcessBinder(); }
	
	/**A public "Binder" class for Activities to access.
	 * Provides a (safe) handle to the background process using the onStartCommand code
	 * used in every RunningBackgroundProcessActivity */
	public class BackgroundProcessBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }
}