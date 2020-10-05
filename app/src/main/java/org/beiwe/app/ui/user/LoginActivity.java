package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.survey.TextFieldKeyboard;
import org.beiwe.app.ui.registration.ForgotPasswordActivity;
import org.beiwe.app.ui.utils.AlertsManager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


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

		setContentView(R.layout.activity_login);
		password = findViewById(R.id.editText2);

		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(appContext);
		textFieldKeyboard.makeKeyboardBehave(password);
	}
	
	
	/**The Login Button
	 * IF session is logged in (value in shared prefs), keep the session logged in.
	 * IF session is not logged in, wait for user input.
	 * @param view*/
	public void loginButton(View view) {		
		if ( PersistentData.checkPassword( password.getText().toString() ) ) {
			PersistentData.loginOrRefreshLogin();
			finish();
			return;
		}
		AlertsManager.showAlert("Incorrect password", this);
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