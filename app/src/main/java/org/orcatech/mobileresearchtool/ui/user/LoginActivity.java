package org.orcatech.mobileresearchtool.ui.user;

import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.survey.TextFieldKeyboard;
import org.orcatech.mobileresearchtool.ui.registration.ForgotPasswordActivity;
//import org.beiwe.app.ui.utils.AlertsManager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;


/**The LoginActivity presents the user with a password prompt.
 * When the app has had no activity (defined as the time since a SessionActiviy last loaded)
 * it bumps the user to this screen.  This timer occurs in the Background Service, so the
 * timer still triggers even if the app has been backgrounded or killed from the task switcher.
 * @authors Dor Samet, Eli Jones */
public class LoginActivity extends RunningBackgroundServiceActivity {	
	private EditText password;
	private Context appContext;
	
	@Override
    /*The login activity prompts the user for the password. */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		appContext = getApplicationContext();

//		Log.i("LoginActivity.onCreate", "isDev: " + BuildConfig.APP_IS_DEV);

		setContentView(R.layout.activity_login);
		password = findViewById(R.id.passwordInput);
//		password.setText("newpass999!");

		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(appContext);
		textFieldKeyboard.makeKeyboardBehave(password);

		password.setOnEditorActionListener((v, actionId, event) -> {
			Log.i("LoginAct", "key press!");
			return false;
		});
	}
	
	
	/**The Login Button
	 * IF session is logged in (value in shared prefs), keep the session logged in.
	 * IF session is not logged in, wait for user input.
	 * @param view*/
	public void loginButton(View view) {
		TextInputLayout passwordInputLayout = findViewById(R.id.passwordInputLayout);
		EditText passwordInput = findViewById(R.id.passwordInput);
		passwordInputLayout.setError(null);
		if ( PersistentData.checkPassword( passwordInput.getText().toString() ) ) {
			PersistentData.loginOrRefreshLogin();
			finish();
			return;
		}
		String password = passwordInput.getText().toString();
		String errMsg = "Incorrect password";
		if (password.length() == 0) {
			errMsg = "Please enter a password";
		}
		passwordInputLayout.setError(errMsg);
//		AlertsManager.showAlert("Incorrect password", this);

	}
	
	
	/**Move user to the forgot password activity.
	 * @param view */
	public void forgotPassword(View view) {
		startActivity( new Intent(appContext, ForgotPasswordActivity.class) );
		finish();
	}
	
	@Override
	/* LoginActivity needs to suppress use of the back button, otherwise it would log the user in without a password. */
	public void onBackPressed() { }
}