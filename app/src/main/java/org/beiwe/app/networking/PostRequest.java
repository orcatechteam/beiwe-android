package org.beiwe.app.networking;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.beiwe.app.BuildConfig;
import org.beiwe.app.CrashHandler;
import org.beiwe.app.DeviceInfo;
import org.beiwe.app.R;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.SetDeviceSettings;
import org.beiwe.app.storage.TextFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;

/** PostRequest is our class for handling all HTTP operations we need; they are all in the form of HTTP post requests. 
 * All HTTP connections are HTTPS, and automatically include a password and identifying information. 
 * @author Josh, Eli, Dor */

//TODO: Low priority. Eli. clean this up and update docs. It does not adequately state that it puts into any request automatic security parameters, and it is not obvious why some of the functions exist (minimal http thing)
public class PostRequest {
	private static Context appContext;

	/**Uploads must be initialized with an appContext before they can access the wifi state or upload a _file_. */
	private PostRequest( Context applicationContext ) { appContext = applicationContext; }

	/** Simply runs the constructor, using the applcationContext to grab variables.  Idempotent. */
	public static void initialize(Context applicationContext) { new PostRequest(applicationContext); }

	private static final Object FILE_UPLOAD_LOCK = new Object() {}; //Our lock for file uploading


	/*##################################################################################
	 ##################### Publicly Accessible Functions ###############################
	 #################################################################################*/


	/**For use with Async tasks.
	 * This opens a connection with the server, sends the HTTP parameters, then receives a response code, and returns it.
	 * @param parameters HTTP parameters
	 * @return serverResponseCode */
	public static int httpRegister( String parameters, String url ) {
		try {
			return doRegisterRequest( parameters, new URL(url) ); }
		catch (MalformedURLException e) {
			Log.e("PostRequestFileUpload", "malformed URL");
			e.printStackTrace(); 
			return 0; }
		catch (NoSuchAlgorithmException e){
			Log.e("PostRequestFileUpdate", "could not create digest");
			e.printStackTrace();
			return 0; }
		catch (JSONException e){
			Log.e("PostRequestFileUpdate", "could not parse JSON");
			e.printStackTrace();
			return 0; }
		catch (IOException e) {
			e.printStackTrace();
			Log.e("PostRequest","Network error: " + e.getMessage());
			return 502; }
	}

	/**For use with Async tasks.
	 * This opens a connection with the server, sends the HTTP parameters, then receives a response code, and returns it.
	 * This function exists to resend registration data if we are using non anonymized hashing
	 * @param parameters HTTP parameters
	 * @return serverResponseCode */
	public static int httpRegisterAgain( String parameters, String url ) {
		try {
			return doRegisterRequestSimple( parameters, new URL(url) ); }
		catch (MalformedURLException e) {
			Log.e("PostRequestFileUpload", "malformed URL");
			e.printStackTrace();
			return 0; }
		catch (NoSuchAlgorithmException e){
			Log.e("PostRequestFileUpdate", "could not create digest");
			e.printStackTrace();
			return 0; }
		catch (JSONException e){
			Log.e("PostRequestFileUpdate", "could not parse JSON");
			e.printStackTrace();
			return 0; }
		catch (IOException e) {
			e.printStackTrace();
			Log.e("PostRequest","Network error: " + e.getMessage());
			return 502; }
	}

	/**For use with Async tasks.
	 * Makes an HTTP post request with the provided URL and parameters, returns the server's response code from that request
	 * @param parameters HTTP parameters
	 * @return an int of the server's response code from the HTTP request */
	public static int httpRequestcode( String parameters, String url, String newPassword ) {
		try {
			return doPostRequestGetResponseCode( parameters, new URL(url), newPassword ); }
		catch (MalformedURLException e) {
			Log.e("PosteRequestFileUpload", "malformed URL");
			e.printStackTrace(); 
			return 0; }
		catch (NoSuchAlgorithmException e){
			Log.e("PostRequestFileUpdate", "could not create digest");
			e.printStackTrace();
			return 0; }
		catch (JSONException e){
			Log.e("PostRequestFileUpdate", "could not parse JSON");
			e.printStackTrace();
			return 0; }
		catch (IOException e) {
			Log.e("PostRequest","Unable to establish network connection");
			return 502; }
	}

	/**For use with Async tasks.
	 * Makes an HTTP post request with the provided URL and parameters, returns a string of the server's entire response. 
	 * @param parameters HTTP parameters
	 * @param urlString a string containing a url
	 * @return a string of the contents of the return from an HTML request.*/
	//TODO: Eli. low priority. investigate the android studio warning about making this a package local function
	public static String httpRequestString(String parameters, String urlString)  {
		try {
			return doPostRequestGetResponseString( parameters, urlString ); }
		catch (NoSuchAlgorithmException e){
			Log.e("PostRequestFileUpdate", "Download File failed with exception: " + e);
			e.printStackTrace();
			throw new NullPointerException("Download File failed.");}
		catch (JSONException e){
			Log.e("PostRequestFileUpdate", "Download File failed with exception: " + e);
			e.printStackTrace();
			throw new NullPointerException("Download File failed."); }
		catch (IOException e) {
			Log.e("PostRequest error", "Download File failed with exception: " + e);
			e.printStackTrace();
			throw new NullPointerException("Download File failed."); }
	}
	
	/*##################################################################################
	 ################################ Common Code ######################################
	 #################################################################################*/

	/**Creates an HTTP connection with minimal settings.  Some network funcitonality
	 * requires this minimal object.
	 * @param url a URL object
	 * @return a new HttpsURLConnection with minimal settings applied
	 * @throws IOException This function can throw 2 kinds of IO exceptions: IOExeptions and ProtocolException*/
	private static HttpsURLConnection minimalHTTP(URL url) throws IOException {
		// Create a new HttpsURLConnection and set its parameters
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setUseCaches(false);
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("Cache-Control", "no-cache");
		connection.setRequestProperty("Authorization", "Bearer " + Base64.encodeToString(DeviceInfo.getAndroidID().getBytes(), Base64.URL_SAFE| Base64.NO_PADDING | Base64.NO_WRAP));
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(5000);
		connection.setChunkedStreamingMode(1024);
		return connection;
	}


	/**For use with functionality that requires additional parameters be added to an HTTP operation.
	 * @param parameters a string that has been created using the makeParameters function
	 * @param url a URL object
	 * @return a new HttpsURLConnection with common settings */
	private static HttpsURLConnection setupHTTP( String parameters, URL url, String newPassword ) throws IOException, NoSuchAlgorithmException, JSONException {
		HttpsURLConnection connection = minimalHTTP(url);

		byte[] securityBytes = securityParameters(newPassword).getBytes();
		byte[] parameterBytes = parameters.getBytes();

		ByteBuffer buff = ByteBuffer.wrap(new byte[securityBytes.length + parameterBytes.length]);
		buff.put(securityBytes);
		buff.put(parameterBytes);

		byte[] content = buff.array();

		connection.setRequestProperty("X-Content-Digest", computeDigest(url, content));

		DataOutputStream request = new DataOutputStream( connection.getOutputStream() );
		request.write( content );
		request.flush();
		request.close();

		return connection;
	}

	/**Takes the content and computes a base64 digest string to include in the POST header. This allows
	 * the server to ensure that the POST body has not been altered
	 * @param content the content of the POST body
	 * @return a base64 encoded string representing the digest  */
	private static String computeDigest(URL url, byte[] content) throws IOException, NoSuchAlgorithmException, JSONException {
		String nonce = getNonce(url);
		String unix = Long.toString(System.currentTimeMillis() / 1000L);
		String base64 = Base64.encodeToString(content, Base64.NO_WRAP);
		String parts = String.join(",",base64, nonce, unix);
		byte[] sha256 =  MessageDigest.getInstance("SHA-256").digest(parts.getBytes(StandardCharsets.UTF_8));
		return unix + " " + Base64.encodeToString(sha256,Base64.NO_WRAP );
	}

	/** Makes a GET request to the server to receive a one time use token.
	 * @param url the url being used to data upload. The path will be replace with the path to get the nonce
	 * @return a one time token string  */
	private static String getNonce(URL url) throws IOException, JSONException {
		String data = doGetRequestGetResponseString("", new URL(url, "/nonce").toString());
		return new JSONObject(data).getString("nonce");
	}

	/**Reads in the response data from an HttpsURLConnection, returns it as a String.
	 * @param connection an HttpsURLConnection
	 * @return a String containing return data
	 * @throws IOException */
	private static String readResponse(HttpsURLConnection connection) throws IOException {
		Integer responseCode = connection.getResponseCode();
		if (responseCode == 200) {
			BufferedReader reader = new BufferedReader(new InputStreamReader( new DataInputStream( connection.getInputStream() ) ) );
			String line;
			StringBuilder response = new StringBuilder();
			while ( (line = reader.readLine() ) != null) { response.append(line); }
			return response.toString();
		}
		return responseCode.toString();
	}


	/*##################################################################################
	 ####################### Actual Post Request Functions #############################
	 #################################################################################*/
	
	private static String doPostRequestGetResponseString(String parameters, String urlString) throws IOException, NoSuchAlgorithmException, JSONException {
		HttpsURLConnection connection = setupHTTP( parameters, new URL( urlString ), null );
		connection.connect();
		String data = readResponse(connection);
		connection.disconnect();
		return data;
	}


	private static int doPostRequestGetResponseCode(String parameters, URL url, String newPassword) throws IOException, NoSuchAlgorithmException, JSONException {
		HttpsURLConnection connection = setupHTTP(parameters, url, newPassword);
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}

	private static String doGetRequestGetResponseString(String parameters, String urlString) throws IOException {
		Log.i("nonce", "making request to " + urlString);
		URL url = new URL(urlString);
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setUseCaches(false);
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(5000);
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Authorization", "Bearer " + Base64.encodeToString(DeviceInfo.getAndroidID().getBytes(), Base64.URL_SAFE| Base64.NO_PADDING | Base64.NO_WRAP));
		connection.connect();
		String data = readResponse(connection);
		connection.disconnect();
		return data;
	}


	private static int doRegisterRequest(String parameters, URL url) throws IOException, NoSuchAlgorithmException, JSONException {
		Log.i("register", "making request to " + url);
		HttpsURLConnection connection = setupHTTP(parameters, url, null);
		int response = connection.getResponseCode();
		Log.i("register", "response code: " + response);
		if ( response == 200 ) {
			String responseBody = readResponse(connection);
			try {
				JSONObject responseJSON = new JSONObject(responseBody);
				String key = responseJSON.getString("client_public_key");
				response = writeKey(key, response);
				JSONObject deviceSettings = responseJSON.getJSONObject("device_settings");
				SetDeviceSettings.writeDeviceSettings(deviceSettings);
			} catch (JSONException e) {
				Log.e("register", responseBody);
				// this gets called once per app lifecycle, print the error because this is a pain to debug.
				e.printStackTrace();
				CrashHandler.writeCrashlog(e, appContext); 
			}
		}
		connection.disconnect();
		return response;
	}

	// Simple registration that does not parse response data.
	// This is used for resubmitting non anonymized identifier data during registration
	private static int doRegisterRequestSimple(String parameters, URL url) throws IOException, NoSuchAlgorithmException, JSONException  {
		HttpsURLConnection connection = setupHTTP(parameters, url, null);
		int response = connection.getResponseCode();
		String responseBody = readResponse(connection);
		connection.disconnect();
		return response;
	}
	
	private static int writeKey(String key, int httpResponse) {
		if ( !key.startsWith("MIIBI") ) {
			Log.e("PostRequest - register", " Received an invalid encryption key from server: " + key );
			return 2; }
		// Log.d( "PostRequest", "Received a key: " + key );
		TextFileManager.getKeyFile().deleteSafely();
		TextFileManager.getKeyFile().safeWritePlaintext( key );
		return httpResponse;
	}
	

	/** Constructs and sends a multipart HTTP POST request with a file attached.
	 * This function uses minimalHTTP() directly because it needs to add a header (?) to the
	 * HttpsURLConnection object before it writes a file to it.
	 * This function had performance issues with large files, these have been resolved by conversion
	 * to use buffered file reads and http/tcp stream writes.
	 * @param file the File to be uploaded
	 * @param uploadUrl the destination URL that receives the upload
	 * @return HTTP Response code as int
	 * @throws IOException */
	private static int doFileUpload(File file, URL uploadUrl, long stopTime) throws IOException, NoSuchAlgorithmException, JSONException {
		if (file.length() >  1024*1024*10) { Log.i("upload", "file length: " + file.length() ); }

		byte[] securityBytes = securityParameters(null).getBytes();
		byte[] nameBytes = makeParameter("file_name", file.getName() ).getBytes();
		byte[] fileBytes = "file=".getBytes();

		ByteBuffer buff = ByteBuffer.wrap(new byte[securityBytes.length + nameBytes.length + fileBytes.length]);
		buff.put(securityBytes);
		buff.put(nameBytes);
		buff.put(fileBytes);

		byte[] content = buff.array();

		HttpsURLConnection connection = minimalHTTP( uploadUrl );
		connection.setRequestProperty("X-Content-Digest-Type", "md5");
		connection.setRequestProperty("X-Content-Digest", computeDigest(uploadUrl, TextFileManager.calculateMD5(file,content)));

		BufferedOutputStream request = new BufferedOutputStream( connection.getOutputStream() , 65536);
		BufferedInputStream inputStream = new BufferedInputStream( new FileInputStream(file) , 65536);

		request.write( content );

//		long start = System.currentTimeMillis();
		// Read in data from the file, and pour it into the POST request stream
		int data;
		int i = 0;
		while ( ( data = inputStream.read() ) != -1 ) {
			request.write((char) data);
			i++;
			//This check has been profiled, it causes no slowdown in upload speeds, and vastly improves upload behavior.
			if (i % 65536 == 0 && stopTime<System.currentTimeMillis()) {
				connection.disconnect();
				return -1;
			}
		}
//		long stop = System.currentTimeMillis();
//		if (file.length() >  1024*1024*10) {
//			Log.w("upload", "speed: " + (file.length() / ((stop - start) / 1000)) / 1024 + "KBps");
//		}
		inputStream.close();
		request.write("".getBytes());
		request.flush();
		request.close();

		// Get HTTP Response. Pretty sure this blocks, nothing can really be done about that.
		int response = connection.getResponseCode();
		connection.disconnect();
		if (BuildConfig.APP_IS_DEV) { Log.d("uploading", "finished attempt to upload " +
				file.getName() + "; received code " + response); }
		return response;
	}


	//#######################################################################################
	//################################## File Upload ########################################
	//#######################################################################################


	/** Uploads all available files on a separate thread. */
	public static void uploadAllFiles() {
		// determine if you are allowed to upload over WiFi or cellular data, return if not.
		if ( !NetworkUtility.canUpload(appContext) ) { return; }

		Log.i("DOING UPLOAD STUFF", "DOING UPLOAD STUFF");
		// Run the HTTP POST on a separate thread
		Thread uploaderThread = new Thread( new Runnable() {
			@Override public void run() { doUploadAllFiles(); }
		}, "uploader_thread");
		uploaderThread.start();
	}

	/** Uploads all files to the Beiwe server.
	 * Files get deleted as soon as a 200 OK code in received from the server. */
	private static void doUploadAllFiles(){
		synchronized (FILE_UPLOAD_LOCK) {
			//long stopTime = System.currentTimeMillis() + PersistentData.getUploadDataFilesFrequencyMilliseconds();
			long stopTime = System.currentTimeMillis() + 1000 * 60 * 60; //One hour to upload files
			String[] files = TextFileManager.getAllUploadableFiles();
			Log.i("uploading", "uploading " + files.length + " files");
			File file = null;
			URL uploadUrl = null; //set up url, write a crash log and fail gracefully if this ever breaks.
			try {
				uploadUrl = new URL(addWebsitePrefix(appContext.getResources().getString(R.string.data_upload_url)));
			} catch (MalformedURLException e) {
				CrashHandler.writeCrashlog(e, appContext);
				return;
			}

			for (String fileName : TextFileManager.getAllUploadableFiles()) {
				try {
					file = new File(appContext.getFilesDir() + "/" + fileName);
//				Log.d("uploading", "uploading " + file.getName());
					if (PostRequest.doFileUpload(file, uploadUrl, stopTime) == 200) {
						TextFileManager.delete(fileName);
					}
				} catch (JSONException e){
					Log.w("PostRequest.java", "Failed to upload file " + fileName + ". Raised exception: " + e.getCause());
				} catch (NoSuchAlgorithmException e){
					Log.w("PostRequest.java", "Failed to upload file " + fileName + ". Raised exception: " + e.getCause());
				} catch (IOException e) {
					Log.w("PostRequest.java", "Failed to upload file " + fileName + ". Raised exception: " + e.getCause());
				}

				if (stopTime < System.currentTimeMillis()) {
					Log.w("UPLOAD STUFF", "shutting down upload due to time limit, we should never reach this.");
                    TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis()+" upload time limit of 1 hr reached, there are likely files still on the phone that have not been uploaded." );
					CrashHandler.writeCrashlog(new Exception("Upload took longer than 1 hour"), appContext);
                    return;
				}
			}
			Log.i("DOING UPLOAD STUFF", "DONE WITH UPLOAD");
		}
	}


	//#######################################################################################
	//############################### UTILITY FUNCTIONS #####################################
	//#######################################################################################

	public static String makeParameter(String key, String value) { return key + "=" + value + "&"; }

	/** Create the 3 standard security parameters for POST request authentication.
	 *  @param newPassword If this is a Forgot Password request, pass in a newPassword string from
	 *  a text input field instead of from the device storage.
	 *  @return a String of the securityParameters to append to the POST request */
	public static String securityParameters(String newPassword) {
		String patientId = PersistentData.getPatientID();
		String deviceId = DeviceInfo.getAndroidID();
		String password = PersistentData.getPassword();
		if (newPassword != null) password = newPassword;

		return makeParameter("patient_id", patientId) +
				makeParameter("password", password) +
				makeParameter("device_id", deviceId);
	}

	public static String addWebsitePrefix(String URL){
		String serverUrl = PersistentData.getServerUrl();
		if ((BuildConfig.CUSTOMIZABLE_SERVER_URL) && (serverUrl != null)) {
			return serverUrl + URL;
		} else {
			// If serverUrl == null, this should be an old version of the app that didn't let the
			// user specify the URL during registration, so assume the URL is either
			// studies.beiwe.org or staging.beiwe.org.
			if (BuildConfig.APP_IS_BETA) return appContext.getResources().getString(R.string.staging_website) + URL;
			else return appContext.getResources().getString(R.string.production_website) + URL;
		}
	}

}