package org.beiwe.app.ui.registration;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import org.beiwe.app.BuildConfig;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.session.ResetPassword;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.survey.TextFieldKeyboard;
import org.beiwe.app.ui.user.LoginActivity;

/**
 * @author Dor Samet, Eli Jones
 */
public class ForgotPasswordActivity extends RunningBackgroundServiceActivity {
	private EditText tempPasswordInput;
	private EditText newPasswordInput;
	private EditText confirmNewPasswordInput;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_forgot_password);

		/* Add the user's Patient ID to the heading in the activity, so the user can tell it to the
		 * administrator when the user calls the research assistant asking for a temporary password. */
		TextView instructionsText = findViewById(R.id.forgotPasswordInstructionsText);
		String instructionsTextWithPlaceholder = getApplicationContext().getString(R.string.forgot_password_instructions_text);
		String instructionsTextFilledOut = String.format(instructionsTextWithPlaceholder, PersistentData.getPatientID());
		instructionsText.setText(instructionsTextFilledOut);

		tempPasswordInput = findViewById(R.id.forgotPasswordTempPasswordInput);
		newPasswordInput = findViewById(R.id.forgotPasswordNewPasswordInput);
		confirmNewPasswordInput = findViewById(R.id.forgotPasswordConfirmNewPasswordInput);
		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(getApplicationContext());
		textFieldKeyboard.makeKeyboardBehave(tempPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(newPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(confirmNewPasswordInput);
/*
		if(!PersistentData.getCallResearchAssistantButtonEnabled()) {
			Button callButton = findViewById(R.id.reset_password_call_button);
			callButton.setVisibility(View.GONE);
		}
*/
	}

	private boolean isValidForgotPass(String tempPassword, String newPassword) {
		boolean validRegistration = true;

		TextInputLayout forgotTempPasswordInputLayout = findViewById(R.id.forgotTempPasswordInputLayout);
		TextInputLayout forgotNewPasswordInputInputLayout = findViewById(R.id.forgotNewPasswordInputInputLayout);
		TextInputLayout forgotConfirmNewPasswordInputLayout = findViewById(R.id.forgotConfirmNewPasswordInputLayout);
		forgotTempPasswordInputLayout.setError(null);
		forgotNewPasswordInputInputLayout.setError(null);
		forgotConfirmNewPasswordInputLayout.setError(null);

		if (tempPassword.length() < 1) {
			validRegistration = false;
			// If the temporary registration password isn't filled in
			forgotTempPasswordInputLayout.setError(getString(R.string.empty_temp_password));
		}
		if (!PersistentData.passwordMeetsRequirements(newPassword)) {
			validRegistration = false;
			// If the new password has too few characters
			String errorMessage = String.format(getString(R.string.password_too_short), PersistentData.minPasswordLength());
			forgotNewPasswordInputInputLayout.setError(errorMessage);
		}
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();
		if (!newPassword.equals(confirmNewPassword)) {
			validRegistration = false;
			// If the new password doesn't match the confirm new password
			forgotConfirmNewPasswordInputLayout.setError(getString(R.string.password_mismatch));
		}
		return validRegistration;
	}

	/** Kill this activity and go back to the homepage */
	public void cancelButtonPressed(View view) {
//		this.finish();
		startActivity(new Intent(getApplicationContext(), LoginActivity.class));
		finish();
	}

	/** calls the reset password HTTPAsync query. */
	public void registerNewPassword(View view) {
		// Get the user's temporary password (they get this from a human admin by calling the research assistant)
		String tempPassword = tempPasswordInput.getText().toString();

		// Get the new, permanent password the user wants
		String newPassword = newPasswordInput.getText().toString();

		// Get the confirmation of the new, permanent password (should be the same as the previous field)
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();

		if (isValidForgotPass(tempPassword, newPassword)) {
			/* Pass all three to the ResetPassword class, which will check validity, and, if valid,
			 * reset the permanent password */
			ResetPassword resetPassword = new ResetPassword(this);
			resetPassword.checkInputsAndTryToResetPassword(tempPassword, newPassword, confirmNewPassword, "forgot");
		}
	}

	public void callResetPassword(View view) {
		super.callResearchAssistant(view);
	}

	@Override
	public void onBackPressed() { }
}
