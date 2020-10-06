package org.beiwe.app.listeners;

import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;

/** The CallLogger logs data from voice call, sent or received.
 *  @author Dor Samet */

public class CallLogger extends ContentObserver {

	public static String header = "hashed phone number,call type,timestamp,duration in seconds";

	// URI for the database
	private Uri allCalls = Uri.parse("content://call_log/calls");

	// Last recorded values
	private int lastRecordedID = 0;
	private int lastKnownSize = 0;
	// Fields
	private String id = android.provider.CallLog.Calls._ID;
	private String number = android.provider.CallLog.Calls.NUMBER;
	private String type = android.provider.CallLog.Calls.TYPE; 
	private String date = android.provider.CallLog.Calls.DATE;
	private String duration = android.provider.CallLog.Calls.DURATION;
	
	// Cursor field
	private Cursor textsDBQuery;

	// Context
	private Context appContext = null;

	// Columns that interest us - phone number, type of call, date, duration of call
	String[] fields = {android.provider.CallLog.Calls.NUMBER, 
			android.provider.CallLog.Calls.TYPE, 
			android.provider.CallLog.Calls.DATE, 
			android.provider.CallLog.Calls.DURATION
	};


	/** ContentObservers require a Handler object, we require a context for future logic. */
	public CallLogger(Handler handler, Context context) {
		super(handler);
		appContext = context;

		// Pull database info, set lastKnownSize
		textsDBQuery = appContext.getContentResolver().query(allCalls, null, null, null, android.provider.CallLog.Calls.DEFAULT_SORT_ORDER);
		if (textsDBQuery == null) { //noticed this error for the first time on Wednesday May 11 2016.
			//according to this stack overflow this occurs when we don't have authority (post is from before andrid 6 permissions, unclear what authority means)
			// or when... something goes wrong with the database.  But Actually.
			//So, we simply try again, if it fails again... unknown.
			//http://stackoverflow.com/questions/13080540/what-causes-androids-contentresolver-query-to-return-null
			textsDBQuery = appContext.getContentResolver().query(allCalls, null, null, null, android.provider.CallLog.Calls.DEFAULT_SORT_ORDER);
			if (textsDBQuery == null) {
				TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " restarting Beiwe due to bug in Android's phone call database API.");
				throw new NullPointerException("the user's call logging database was broken, did not succeed in connecting on the second try."); }
		}
		textsDBQuery.moveToFirst();
		lastKnownSize = textsDBQuery.getCount();
//		Log.i("CallLogger", "" + lastKnownSize);
		// Record id of last made call and the date
		if (lastKnownSize != 0) {
			lastRecordedID = textsDBQuery.getInt(textsDBQuery.getColumnIndex(id));
			textsDBQuery.getLong(textsDBQuery.getColumnIndex(android.provider.CallLog.Calls.DATE));
		}
	}

	
	/**onChange receives pushed updates.
	 * We Look for the last row, then goes back until reaching the row of the last recorded call,
	 * then goes back down until reaching the newest line, and records everything to the log file. */
	public void onChange(boolean selfChange) {
		super.onChange(selfChange);
		
		// Database information
		textsDBQuery = appContext.getContentResolver().query(allCalls, null, null, null, android.provider.CallLog.Calls.DEFAULT_SORT_ORDER);

		if(textsDBQuery == null || !textsDBQuery.moveToFirst()) return;
		
		int currentSize = textsDBQuery.getCount();
//		Log.i("Call Log", "" + "Current Size is " + currentSize);
//		Log.i("Call Log", "Last Known Size is " + lastKnownSize);
		
		// Record id of last made call, recod last date
		if (lastKnownSize == 0) {
			lastRecordedID = textsDBQuery.getInt(textsDBQuery.getColumnIndex(id));
			textsDBQuery.getLong(textsDBQuery.getColumnIndex(android.provider.CallLog.Calls.DATE));
		}
		
		// Comparison values
		int currentID = textsDBQuery.getInt(textsDBQuery.getColumnIndex(id));
//		Log.i("Call Log", "" + "Current Size is " + currentID);
//		Log.i("Call Log", "Last Known ID is " + lastRecordedID)
		
		// A call was deleted
		if (currentSize < lastKnownSize) { /*Log.i("Call Logger", "Last Call deleted, Last Call deleted, Last Call deleted, Last Call deleted");*/ }
		else if ( currentSize == lastKnownSize && currentID == lastRecordedID ) { /*Log.i("CallLogger", "Something broke - this doesn't make sense...");*/ }
		else {
			// Log.i("CallLogger", "Last recorded ID " + lastRecordedID);
		
			// 	Descend until reaching the idOfLastCall row
			while (currentID != lastRecordedID) {
				textsDBQuery.moveToNext();
				// Log.i("CallLogger", "Current ID is " + currentID);
				currentID = textsDBQuery.getInt(textsDBQuery.getColumnIndex(id));
			}

			// While there exists a previous row
			while(!textsDBQuery.isBeforeFirst()) {
				// Log.i("Call Logger", "" + (textsDBQuery.getInt(textsDBQuery.getColumnIndex(id))));
				if (currentID <= lastRecordedID) {
					if(!textsDBQuery.moveToPrevious()) break; // Make sure we are pointed at an item that exists
					currentID = textsDBQuery.getInt(textsDBQuery.getColumnIndex(id));
					continue;
				}
				StringBuilder callLoggerLine = new StringBuilder();
				// Add hashed phone number
				callLoggerLine.append(EncryptionEngine.hashPhoneNumber(textsDBQuery.getString(textsDBQuery.getColumnIndex(number))));
				callLoggerLine.append(TextFileManager.DELIMITER);

				// Add call type
				int callType = textsDBQuery.getInt(textsDBQuery.getColumnIndex(type));
				if (callType == CallLog.Calls.OUTGOING_TYPE) { callLoggerLine.append("Outgoing Call"); }
				else if (callType == CallLog.Calls.INCOMING_TYPE) { callLoggerLine.append("Incoming Call"); }
				else { callLoggerLine.append("Missed Call"); }
				callLoggerLine.append(TextFileManager.DELIMITER);

				// Add date
				callLoggerLine.append(textsDBQuery.getLong(textsDBQuery.getColumnIndex(date)));
				callLoggerLine.append(TextFileManager.DELIMITER);
				
				// Add duration
				callLoggerLine.append(textsDBQuery.getInt(textsDBQuery.getColumnIndex(duration)));

				// Log.i("Call Log", callLoggerLine.toString());
				TextFileManager.getCallLogFile().writeEncrypted(callLoggerLine.toString());
				textsDBQuery.moveToPrevious();
			}
		}
		lastKnownSize = currentSize;
		lastRecordedID = currentID;
	}
}