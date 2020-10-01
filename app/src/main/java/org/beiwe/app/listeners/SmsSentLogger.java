package org.beiwe.app.listeners;

import org.beiwe.app.CrashHandler;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony.TextBasedSmsColumns;

/**
 * Records outgoing SMS messages when they're sent
 * 
 * The listeners for incoming and outgoing SMS messages are in different
 * classes because they need to use different methods.  You CAN register
 * a BroadcastReceiver to listen for INCOMING texts, but if you register a
 * BroadcastReceiver to listen for OUTGOING texts, it WON'T pick them up if
 * they're sent with a typical Android default text-messaging app.
 * 
 * Because of that, this class listens for outgoing texts by registering a
 * ContentObserver that watches the file "content://sms" to see when it's
 * changed.
 * 
 * @author Josh Zagorsky June 2014
 */
public class SmsSentLogger extends ContentObserver {	

	public static String header = "timestamp,hashed phone number,sent vs received,message length,time sent";

	private TextFileManager smsLogFile = null;
	private Handler handler = null;
	private Context appContext = null;
	
	public SmsSentLogger(Handler theHandler, Context context) {
		super(theHandler);
		theHandler = handler;
		appContext = context;
		smsLogFile = TextFileManager.getTextsLogFile();
	}
	
	
	@Override
	public void onChange(boolean selfChange) {
		super.onChange(selfChange);		
		try {
			Cursor cursor = appContext.getContentResolver().query( Uri.parse("content://sms"), null, null, null, "date DESC" );
			//Check if this cursor has data
			if(cursor!=null && cursor.moveToFirst()) {
				String address = cursor.getString(cursor.getColumnIndex("address"));
				String body = cursor.getString(cursor.getColumnIndex("body"));
				String timestamp = cursor.getString(cursor.getColumnIndex("date"));
				int msgType = cursor.getInt(cursor.getColumnIndex("type"));

				/* Improvement idea: we could log all message types; TextBasedSmsColumns has 6 types of messages:
				 * https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html
				 * That would provide more data on draft messages, messages not sent immediately, etc.
				 * We could also use this class to log incoming messages as well as outgoing messages. */
				if (msgType == TextBasedSmsColumns.MESSAGE_TYPE_SENT) {
					//			"timestamp,hashed phone number,sent vs received,message length,time sent";
					String data = "" + timestamp + TextFileManager.DELIMITER;
					data += EncryptionEngine.hashPhoneNumber(address) + TextFileManager.DELIMITER;
					data += "sent SMS" + TextFileManager.DELIMITER;
					data += body.length();

					smsLogFile.writeEncrypted(data);

					/* Note: Android often records a text message multiple times. This
					 * may happen as a message is moved among the outbox, sent, and
					 * other folders. The good news is that when a duplicate text
					 * message is recorded, we get several identical lines (same
					 * message length, same hashed phone number, and same timestamp),
					 * so it should be easy to identify duplicate entries. */
				}
				cursor.close();
			}
		}
		catch (Exception e) {
			smsLogFile.writeEncrypted("" + System.currentTimeMillis() + TextFileManager.DELIMITER + "SMS Sent Logger had an error and failed to record an SMS");
			CrashHandler.writeCrashlog(e, appContext);
		}
	}	
}

///* This is a more brittle way to check if a message is a sent message: check if the
//* message's "_id" is new, and if its "protocol" is null. The reason for this check is that
//* every time a message gets sent, the system registers multiple new messages (on one
//* testing phone, 4 new messages appear each time); one as its moved to the queue, one as
//* its moved to the outbox, etc. */
//
//// Make this a class variable:
//String idOfLastSmsSent = null;
//
//// Put these lines inside onChange():
//String id = cursor.getString(cursor.getColumnIndex("_id"));
//String protocol = cursor.getString(cursor.getColumnIndex("protocol"));
//
//// Make this the if() statement:
////if ((protocol == null) && (idIsNew(id))) {
//
//// Uncomment this function:
//// Check if the message's ID hasn't been encountered before: http://stackoverflow.com/a/8242090
//private boolean idIsNew(String id) {
//	if ((idOfLastSmsSent == null) || (!idOfLastSmsSent.equals(id))) {
//		idOfLastSmsSent = id; // Reset idOfLastSmsSent
//		return true;
//	}
//	return false; // This is not a new/unique ID; return false
//}
