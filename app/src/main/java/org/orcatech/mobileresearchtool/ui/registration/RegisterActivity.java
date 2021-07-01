package org.orcatech.mobileresearchtool.ui.registration;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import org.orcatech.mobileresearchtool.BuildConfig;
import org.orcatech.mobileresearchtool.DeviceInfo;
import org.orcatech.mobileresearchtool.PermissionHandler;
import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.networking.HTTPUIAsync;
import org.orcatech.mobileresearchtool.networking.PostRequest;
import org.orcatech.mobileresearchtool.storage.EncryptionEngine;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.survey.TextFieldKeyboard;
import org.orcatech.mobileresearchtool.ui.utils.AlertsManager;

import java.time.LocalDateTime;

import static org.orcatech.mobileresearchtool.networking.PostRequest.addWebsitePrefix;


/**Activity used to log a user in to the application for the first time. This activity should only be called on ONCE,
 * as once the user is logged in, data is saved on the phone.
 * @author Dor Samet, Eli Jones, Josh Zagorsky */

@SuppressLint("ShowToast")
public class RegisterActivity extends RunningBackgroundServiceActivity {
	private EditText serverUrlInput;
	private EditText userIdInput;
	private EditText tempPasswordInput;
	private EditText newPasswordInput;
	private EditText confirmNewPasswordInput;

	private final static int PERMISSION_CALLBACK = 0; //This callback value can be anything, we are not really using it
	private final static int REQUEST_PERMISSIONS_IDENTIFIER = 1500;
	
	/** Users will go into this activity first to register information on the phone and on the server. */
	@SuppressLint("SetTextI18n")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_register);

		if (!BuildConfig.CUSTOMIZABLE_SERVER_URL) {
//			TextView serverUrlCaption = findViewById(R.id.serverUrlCaption);
//			EditText serverUrlInput = findViewById(R.id.serverUrlInput);
//			serverUrlCaption.setVisibility(View.GONE);
//			serverUrlInput.setVisibility(View.GONE);
			TextInputLayout serverUrlInputLayout = findViewById(R.id.serverUrlInputLayout);
			serverUrlInputLayout.setVisibility(View.GONE);
		}

		serverUrlInput = findViewById(R.id.serverUrlInput);
		userIdInput = findViewById(R.id.registerUserIdInput);
		tempPasswordInput = findViewById(R.id.registerTempPasswordInput);
		newPasswordInput = findViewById(R.id.registerNewPasswordInput);
		confirmNewPasswordInput = findViewById(R.id.registerConfirmNewPasswordInput);
		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(getApplicationContext());
		textFieldKeyboard.makeKeyboardBehave(serverUrlInput);
		textFieldKeyboard.makeKeyboardBehave(userIdInput);
		textFieldKeyboard.makeKeyboardBehave(tempPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(newPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(confirmNewPasswordInput);

//		newPasswordInput.setHint(String.format(getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()));
//		confirmNewPasswordInput.setHint(String.format(getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()));
		TextInputLayout registerNewPasswordInputInputLayout = findViewById(R.id.registerNewPasswordInputInputLayout);
		registerNewPasswordInputInputLayout.setHelperText(String.format(getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()));

		Log.d("RegAct.onCreate", "onCreate!!! " + LocalDateTime.now());
		boolean testing = true;
		if (testing) {
//			serverUrlInput.setText("someotherstudyserver.net");
//			serverUrlInput.setText("10.0.2.2:8080");
//			serverUrlInput.setText("rnb02281.lan:8081");
			serverUrlInput.setText("10.0.2.2:8081");
			userIdInput.setText("1");
			tempPasswordInput.setText("temporary");
			newPasswordInput.setText("newpass999!"); // <<< This is the password if it asks for on login
			confirmNewPasswordInput.setText("newpass999!");
		}
		TextInputLayout serverUrlInputLayout = findViewById(R.id.serverUrlInputLayout);

		serverUrlInputLayout.setVisibility(View.GONE);
	}

	public synchronized void registerButtonPressed2(View view) {
		Log.i("RegAct.regBtnPressed2", "reg btn pressed!!!");

		String serverUrl = serverUrlInput.getText().toString();
		String userID = userIdInput.getText().toString();
		String tempPassword = tempPasswordInput.getText().toString();
		String newPassword = newPasswordInput.getText().toString();

		if (isValidRegistration(serverUrl, userID, tempPassword, newPassword)) {
			PersistentData.setServerUrl(serverUrl);
			PersistentData.setLoginCredentials(userID, tempPassword);
//			String regUrl = addWebsitePrefix(getApplicationContext().getString(R.string.register_url)).replace("https://", "http://");
			String regUrl = addWebsitePrefix(getApplicationContext().getString(R.string.register_url));
			Log.i("RegAct.regUrl", regUrl);
			tryToRegisterWithTheServer(this, regUrl, newPassword);
		}
	}

	private boolean isValidRegistration(String serverUrl, String userID, String tempPassword, String newPassword) {
		boolean validRegistration = true;

		TextInputLayout serverUrlInputLayout = findViewById(R.id.serverUrlInputLayout);
		TextInputLayout registerUserIdInputLayout = findViewById(R.id.registerUserIdInputLayout);
		TextInputLayout registerTempPasswordInputLayout = findViewById(R.id.registerTempPasswordInputLayout);
		TextInputLayout registerNewPasswordInputInputLayout = findViewById(R.id.registerNewPasswordInputInputLayout);
		TextInputLayout registerConfirmNewPasswordInputLayout = findViewById(R.id.registerConfirmNewPasswordInputLayout);
		serverUrlInputLayout.setError(null);
		registerUserIdInputLayout.setError(null);
		registerTempPasswordInputLayout.setError(null);
		registerNewPasswordInputInputLayout.setError(null);
		registerConfirmNewPasswordInputLayout.setError(null);

		if ((BuildConfig.CUSTOMIZABLE_SERVER_URL) && (serverUrl.length() == 0)) {
			// If the study URL is empty, alert the user
			validRegistration = false;
			serverUrlInputLayout.setError(getString(R.string.url_too_short));
		}
		if (userID.length() == 0) {
			validRegistration = false;
			// If the user id length is too short, alert the user
			registerUserIdInputLayout.setError(getString(R.string.invalid_user_id));
		}
		if (tempPassword.length() < 1) {
			validRegistration = false;
			// If the temporary registration password isn't filled in
			registerTempPasswordInputLayout.setError(getString(R.string.empty_temp_password));
		}
		if (!PersistentData.passwordMeetsRequirements(newPassword)) {
			validRegistration = false;
			// If the new password has too few characters
			String errorMessage = String.format(getString(R.string.password_too_short), PersistentData.minPasswordLength());
			registerNewPasswordInputInputLayout.setError(errorMessage);
		}
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();
		if (!newPassword.equals(confirmNewPassword)) {
			validRegistration = false;
			// If the new password doesn't match the confirm new password
			registerConfirmNewPasswordInputLayout.setError(getString(R.string.password_mismatch));
		}
		return validRegistration;
	}

	/** Registration sequence begins here, called when the submit button is pressed.
	 * @param view */
	public synchronized void registerButtonPressed(View view) {
		String serverUrl = serverUrlInput.getText().toString();
		String userID = userIdInput.getText().toString();
		String tempPassword = tempPasswordInput.getText().toString();
		String newPassword = newPasswordInput.getText().toString();
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();

		if ((serverUrl.length() == 0) && (BuildConfig.CUSTOMIZABLE_SERVER_URL)) {
			// If the study URL is empty, alert the user
			AlertsManager.showAlert(getString(R.string.url_too_short), getString(R.string.couldnt_register), this);
		} else if (userID.length() == 0) {
			// If the user id length is too short, alert the user
			AlertsManager.showAlert(getString(R.string.invalid_user_id), getString(R.string.couldnt_register), this);
		} else if (tempPassword.length() < 1) {
			// If the temporary registration password isn't filled in
			AlertsManager.showAlert(getString(R.string.empty_temp_password), getString(R.string.couldnt_register), this);
		} else if (!PersistentData.passwordMeetsRequirements(newPassword)) {
			// If the new password has too few characters
			String alertMessage = String.format(getString(R.string.password_too_short), PersistentData.minPasswordLength());
			AlertsManager.showAlert(alertMessage, getString(R.string.couldnt_register), this);
		} else if (!newPassword.equals(confirmNewPassword)) {
			// If the new password doesn't match the confirm new password
			AlertsManager.showAlert(getString(R.string.password_mismatch), getString(R.string.couldnt_register), this);
		} else {
			if (BuildConfig.CUSTOMIZABLE_SERVER_URL) {
				PersistentData.setServerUrl(serverUrl);
			}
			PersistentData.setLoginCredentials(userID, tempPassword);
			tryToRegisterWithTheServer(this, addWebsitePrefix(getApplicationContext().getString(R.string.register_url)), newPassword);
		}
	}
	
	
	/**Implements the server request logic for user, device registration. 
	 * @param url the URL for device registration*/
	static private void tryToRegisterWithTheServer(final Activity currentActivity, final String url, final String newPassword) {
		new HTTPUIAsync(url, currentActivity ) {
			@Override
			protected Void doInBackground(Void... arg0) {
				DeviceInfo.initialize(currentActivity.getApplicationContext());
				// Always use anonymized hashing when first registering the phone.
				parameters= PostRequest.makeParameter("bluetooth_id", DeviceInfo.getBluetoothMAC() ) +
							PostRequest.makeParameter("new_password", EncryptionEngine.safeHash(newPassword)) +
							PostRequest.makeParameter("phone_number", ((RegisterActivity) activity).getPhoneNumber() ) +
							PostRequest.makeParameter("device_id", DeviceInfo.getAndroidID() ) +
							PostRequest.makeParameter("device_os", "Android") +
							PostRequest.makeParameter("os_version", DeviceInfo.getAndroidVersion() ) +
							PostRequest.makeParameter("hardware_id", DeviceInfo.getHardwareId() ) +
							PostRequest.makeParameter("brand", DeviceInfo.getBrand() ) +
							PostRequest.makeParameter("manufacturer", DeviceInfo.getManufacturer() ) +
							PostRequest.makeParameter("model", DeviceInfo.getModel() ) +
							PostRequest.makeParameter("product", DeviceInfo.getProduct() ) +
							PostRequest.makeParameter("beiwe_version", DeviceInfo.getBeiweVersion() );
				Log.i("RegAct.params", parameters);
				Log.i("RegAct.url", url);
				responseCode = PostRequest.httpRegister(parameters, url);

				// If we are not using anonymized hashing, resubmit the phone identifying information
				if (responseCode == 200 && !PersistentData.getUseAnonymizedHashing()) { // This short circuits so if the initial register fails, it won't try here
					try {
						//Sleep for one second so the backend does not receive information with overlapping timestamps
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					parameters= PostRequest.makeParameter("bluetooth_id", DeviceInfo.getBluetoothMAC() ) +
							PostRequest.makeParameter("new_password", EncryptionEngine.safeHash(newPassword)) +
							PostRequest.makeParameter("phone_number", ((RegisterActivity) activity).getPhoneNumber() ) +
							PostRequest.makeParameter("device_id", DeviceInfo.getAndroidID() ) +
							PostRequest.makeParameter("device_os", "Android") +
							PostRequest.makeParameter("os_version", DeviceInfo.getAndroidVersion() ) +
							PostRequest.makeParameter("hardware_id", DeviceInfo.getHardwareId() ) +
							PostRequest.makeParameter("brand", DeviceInfo.getBrand() ) +
							PostRequest.makeParameter("manufacturer", DeviceInfo.getManufacturer() ) +
							PostRequest.makeParameter("model", DeviceInfo.getModel() ) +
							PostRequest.makeParameter("product", DeviceInfo.getProduct() ) +
							PostRequest.makeParameter("beiwe_version", DeviceInfo.getBeiweVersion() );
					int resp = PostRequest.httpRegisterAgain(parameters, url);
				}
				return null;
			}
		
			@Override
			protected void onPostExecute(Void arg) {
				super.onPostExecute(arg);
				if (responseCode == 200) {
					PersistentData.setPassword(newPassword);

//					if (PersistentData.getCallClinicianButtonEnabled() || PersistentData.getCallResearchAssistantButtonEnabled()) {
//						activity.startActivity(new Intent(activity.getApplicationContext(), PhoneNumberEntryActivity.class));
//					}
//					else{
//						activity.startActivity(new Intent(activity.getApplicationContext(), ConsentFormActivity.class));
//					}
					activity.startActivity(new Intent(activity.getApplicationContext(), ConsentFormActivity.class));
					activity.finish();
				} else {
					AlertsManager.showAlert(responseCode, currentActivity.getString(R.string.couldnt_register), currentActivity);
				}
			}
		};
	}
	
	/**This is the fuction that requires SMS permissions.  We need to supply a (unique) identifier for phone numbers to the registration arguments.
	 * @return */
	@SuppressLint("HardwareIds")
    private String getPhoneNumber() {
		TelephonyManager phoneManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		String phoneNumber;
		try {
			// If READ_TEXT_AND_CALL_LOGS is true, we should not be able to get here without having
			// asked for the SMS permission.  If it's false, we don't have permission to do this.
			phoneNumber = phoneManager.getLine1Number();
		} catch (SecurityException e) {
			phoneNumber = "";
		}
		if (phoneNumber == null) { return EncryptionEngine.hashPhoneNumber(""); }
		return EncryptionEngine.hashPhoneNumber(phoneNumber);
	}
	
	
	/*####################################################################
	###################### Permission Prompting ##########################
	####################################################################*/
	
	private static Boolean prePromptActive = false;
	private static Boolean postPromptActive = false;
	private static Boolean thisResumeCausedByFalseActivityReturn = false;
	private static Boolean aboutToResetFalseActivityReturn = false;
	private static Boolean activityNotVisible = false;

	private void goToSettings() {
		// Log.i("reg", "goToSettings");
        Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
        myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(myAppSettings, REQUEST_PERMISSIONS_IDENTIFIER);
    }

	
	@Override
	protected void onResume() {  
		Log.i("reg", "onResume");
		super.onResume();
		activityNotVisible = false;
		
		// This used to be in an else block, its idempotent and we appear to have been having problems with it not having been run.
		DeviceInfo.initialize(getApplicationContext());
		
		if (aboutToResetFalseActivityReturn) {
			aboutToResetFalseActivityReturn = false;
			thisResumeCausedByFalseActivityReturn = false;
			return;
		}
		if (BuildConfig.READ_TEXT_AND_CALL_LOGS &&
				getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
				!PermissionHandler.checkAccessReadSms(getApplicationContext()) &&
				!thisResumeCausedByFalseActivityReturn) {
			Log.i("show", "Should show? " + shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS));
			if (shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) ) {
				if (!prePromptActive && !postPromptActive ) { showPostPermissionAlert(this); } 
			}
			//else if (!prePromptActive && !postPromptActive ) { showPrePermissionAlert(this); }
		}
	}
	
	@Override
	protected void onPause() {
		Log.i("reg", "onPause");
		super.onPause();
		activityNotVisible = true;
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.i("reg", "onActivityResult. requestCode: " + requestCode + ", resultCode: " + resultCode );
		super.onActivityResult(requestCode, resultCode, data);
		aboutToResetFalseActivityReturn = true;
    }

	@Override
	public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
		Log.i("reg", "onRequestPermissionResult: " + grantResults.length + ", visible: " + activityNotVisible);
		if (activityNotVisible) return; //this is identical logical progression to the way it works in SessionActivity.
		for (int i = 0; i < grantResults.length; i++) {
			if ( permissions[i].equals( Manifest.permission.READ_SMS ) ) {
				Log.i("permiss", "permission return: " + permissions[i] + ", grant return: " + grantResults[i] + ", capability: " + getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
				if ( grantResults[i] == PermissionHandler.PERMISSION_GRANTED ) {
					break;
				}
				//(shouldShow... "This method returns true if the app has requested this permission previously and the user denied the request.")
				if ( shouldShowRequestPermissionRationale(permissions[i]) ) {
					showPostPermissionAlert(this);
				}
			} else {
				Log.i("permiss", "permission return: " + permissions[i]);
			}
		}
	}
	
	/* Message Popping */
	
	public static void showPrePermissionAlert(final Activity activity) {
		// Log.i("reg", "showPreAlert");
		if (prePromptActive) { return; }
		prePromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(R.string.permission_registration_read_sms_alert);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			activity.requestPermissions(new String[]{ Manifest.permission.READ_SMS }, PERMISSION_CALLBACK );
			prePromptActive = false;
		} } );
		builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) { } } ); //Okay button
		builder.create().show();
	}
	
	public static void showPostPermissionAlert(final RegisterActivity activity) {
		// Log.i("reg", "showPostAlert");
		if (postPromptActive) { return; }
		postPromptActive = true;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Permissions Requirement:");
		builder.setMessage(R.string.permission_registration_actually_need_sms_alert);
		builder.setOnDismissListener( new DialogInterface.OnDismissListener() { @Override public void onDismiss(DialogInterface dialog) {
			thisResumeCausedByFalseActivityReturn = true;
			activity.goToSettings();
			postPromptActive = false;
		} } );
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface arg0, int arg1) {  } } ); //Okay button
		builder.create().show();
	}
}
