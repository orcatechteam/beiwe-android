package org.orcatech.mobileresearchtool.ui.registration;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.session.ResetPassword;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.survey.TextFieldKeyboard;

/**
 * An activity to manage users who forgot their passwords.
 * @author Dor Samet
 */

@SuppressLint("ShowToast")
public class ResetPasswordActivity extends RunningBackgroundServiceActivity {
	private EditText currentPasswordInput;
	private EditText newPasswordInput;
	private EditText confirmNewPasswordInput;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_reset_password);

		currentPasswordInput = findViewById(R.id.resetPasswordCurrentPasswordInput);
		newPasswordInput = findViewById(R.id.resetPasswordNewPasswordInput);
		confirmNewPasswordInput = findViewById(R.id.resetPasswordConfirmNewPasswordInput);
		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard(getApplicationContext());
		textFieldKeyboard.makeKeyboardBehave(currentPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(newPasswordInput);
		textFieldKeyboard.makeKeyboardBehave(confirmNewPasswordInput);
	}

	/** Kill this activity and go back to the homepage */
	public void cancelButtonPressed(View view) {
		this.finish();
	}

	/** calls the reset password HTTPAsync query. */
	public void registerNewPassword(View view) {
		// Get the user's current password
		String currentPassword = currentPasswordInput.getText().toString();

		// Get the new, permanent password the user wants
		String newPassword = newPasswordInput.getText().toString();
		
		// Get the confirmation of the new, permanent password (should be the same as the previous field)
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();

		if (isValidResetPass(currentPassword, newPassword)) {
			/* Pass all three to the ResetPassword class, which will check validity, and, if valid,
			 * reset the permanent password */
			ResetPassword resetPassword = new ResetPassword(this);
			resetPassword.checkInputsAndTryToResetPassword(currentPassword, newPassword, confirmNewPassword, "reset");
		}
	}

	private boolean isValidResetPass(String currentPassword, String newPassword) {
		boolean validResetPass = true;

		TextInputLayout resetPasswordCurrentPasswordInputLayout = findViewById(R.id.resetPasswordCurrentPasswordInputLayout);
		TextInputLayout resetPasswordNewPasswordInputLayout = findViewById(R.id.resetPasswordNewPasswordInputLayout);
		TextInputLayout resetPasswordConfirmNewPasswordInputLayout = findViewById(R.id.resetPasswordConfirmNewPasswordInputLayout);
		resetPasswordCurrentPasswordInputLayout.setError(null);
		resetPasswordNewPasswordInputLayout.setError(null);
		resetPasswordConfirmNewPasswordInputLayout.setError(null);

		if (currentPassword.length() < 1) {
			validResetPass = false;
			// If the temporary registration password isn't filled in
			resetPasswordCurrentPasswordInputLayout.setError("Please enter the current password.");
		}
		if (!PersistentData.checkPassword(currentPassword)) {
			validResetPass = false;
			resetPasswordCurrentPasswordInputLayout.setError("Current password is incorrect.");
		}
		if (!PersistentData.passwordMeetsRequirements(newPassword)) {
			validResetPass = false;
			// If the new password has too few characters
			String errorMessage = String.format(getString(R.string.password_too_short), PersistentData.minPasswordLength());
			resetPasswordNewPasswordInputLayout.setError(errorMessage);
		}
		String confirmNewPassword = confirmNewPasswordInput.getText().toString();
		if (!newPassword.equals(confirmNewPassword)) {
			validResetPass = false;
			// If the new password doesn't match the confirm new password
			resetPasswordConfirmNewPasswordInputLayout.setError(getString(R.string.password_mismatch));
		}
		return validResetPass;
	}
}