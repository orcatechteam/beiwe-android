package org.beiwe.app.listeners;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import org.beiwe.app.CrashHandler;
import org.beiwe.app.storage.TextFileManager;

import java.util.Objects;

/**MMSSentLogger listens for outgoing MMSes.
 * In order to catch outgoing MMSes we need to monitor the texts database using a ContentObserver
 * and then make a database query based on the 
 * 
 * @author Eli */

public class MMSSentLogger extends ContentObserver{
	
	Context appContext = null;
	
	/** ContentObservers require a Handler object for instantiation,
	 * and our future logic will require a Context.
	 * @param handler
	 * @param context */
	public MMSSentLogger (Handler handler, Context context ) {
		super(handler);
		this.appContext = context;
	}
	
	@Override
	public void onChange(boolean selfChange){
		super.onChange(selfChange);

		try {
			Cursor cursor = appContext.getContentResolver().query( Uri.parse("content://mms"), null, null, null, "date DESC");
			//test the MMS and SMS cursors for basic validity, move them into position.  If anything is invalid, exit.
			if ( !checkAndPositionCursor(cursor) ){ return; }
			//test that the data in these cursors are useful, if not, exit.
			if ( !checkValidData(Objects.requireNonNull(cursor)) ) { return; }

			long timestamp = cursor.getInt(cursor.getColumnIndexOrThrow("date")) * 1000L;
			String write_to_file = timestamp + TextFileManager.DELIMITER + "" + TextFileManager.DELIMITER + "sent MMS" + TextFileManager.DELIMITER + "MMS";
			TextFileManager.getTextsLogFile().writeEncrypted(write_to_file);
		}
		catch (Exception e) { CrashHandler.writeCrashlog(e, appContext); }
	}
	
	/**Checks for basic validity of our database cursors, moves database cursor to correct location. 
	 * @param cursor A database cursor object
	 * @return returns false if cursor is invalid */
	public boolean checkAndPositionCursor(Cursor cursor){
		if (cursor == null || cursor.getCount() < 1) {
//			Log.e("MMS validity check failed","invalid cursor");
			return false; }
		
		//move cursor to most recent update (this should hold true, but databases can have concurrent access etc.)
		cursor.moveToFirst();
//		Log.i("MMS COUNT", "Count: " + cursor.getCount())
		return true;
	}

	/** Checks for data validity */
	public boolean checkValidData(Cursor cursor){
		//if the message box is not box number 2 (sent) then this was one of those many,
		// many, extra, dumb, onChange triggers, and we return false.
		try {
			if (cursor.getInt( cursor.getColumnIndexOrThrow("msg_box") ) != 2 ) { return false; }
		}
		catch (IllegalArgumentException e) {
			//this is the error that we should get if the column does not exist
			return false;
		}
		catch (IllegalStateException e) {
			//cursor.getInt throws an IllegalStateException occasionally, with the following message:
			//Couldn't read row 0, col -1 from CursorWindow.  Make sure the Cursor is initialized correctly before accessing data from it.
			//This does not occur when the user receives or sends MMS messages, it just happens, though it happens more frequently if
			//the user is "fiddling" (selecting an image, for instance) with MMS.
			//The good thing is that if this happens, the cursor is invalid. 
			return false;
		}
		//http://developer.android.com/reference/android/database/sqlite/SQLiteCursor.html
		//this is not valid, I have been assuming I was dealing with a sqlitecursor but apparently the following method is not defined, so maybe not.
//		try {
//			cursor.checkPosition();
//		}
//		catch (CursorIndexOutOfBoundsException e) {
//			return false;
//		}
		return true;
	}
	
	//The following is the code that was written to work out what the data coming in meant.
	// Since this is probably going to be useful to someone in the future, keep it.
//	public void print_things_from_valid_sources( Cursor cursor ){
//		
//		for (String column : cursor.getColumnNames() ) {
//			int index = cursor.getColumnIndex(column);
//			try {
//				switch ( cursor.getType( index ) ) {
//
//				case android.database.Cursor.FIELD_TYPE_BLOB:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + ", type: blob - " + new String(cursor.getBlob(index) ) );
//					break;
//				case android.database.Cursor.FIELD_TYPE_FLOAT:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + ", type: float, " + cursor.getFloat(index) );
//					break;
//				case android.database.Cursor.FIELD_TYPE_INTEGER:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + ", type: int, " + cursor.getInt(index) );
//					break;
//				case android.database.Cursor.FIELD_TYPE_NULL:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + ", type: null" );
//					break;
//				case android.database.Cursor.FIELD_TYPE_STRING:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + ", type: string - " + cursor.getString(index) );
//					break;
//				default:
//					Log.i("MMS DUMP", "name: " + column + ", index: " + index + " did not match a type.");
//				}
//			}
//			catch (Exception e) { 
//				Log.e("MMS CRAP?", "SOMETHING BAD HAPPEND!");
//				e.printStackTrace();
//			}
//		}
//	}
//	
//	private String getMmsText(String mmsid) {
//	    InputStream inputStream = null;
//	    StringBuilder builder = new StringBuilder();
//	    try {
//	        inputStream = appContext.getContentResolver().openInputStream( Uri.parse("content://mms/part/" + mmsid) );
//	        if (inputStream != null) { 
//	            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8") );
//	            String temp = reader.readLine();
//	            while (temp != null) {
//	                builder.append(temp);
//	                temp = reader.readLine();
//	            }
//	        }
//	    } catch (IOException e) {}
//	    finally {
//	        if (inputStream != null) { try { inputStream.close(); }
//	            catch (IOException e) {}
//	        }
//	    }
//	    return builder.toString();
//	}
}
