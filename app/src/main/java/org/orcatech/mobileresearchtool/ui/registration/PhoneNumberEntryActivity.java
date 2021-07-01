package org.orcatech.mobileresearchtool.ui.registration;

import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.survey.TextFieldKeyboard;
import org.orcatech.mobileresearchtool.ui.utils.AlertsManager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class PhoneNumberEntryActivity extends RunningBackgroundServiceActivity {
	private EditText primaryCarePhone;
	private EditText passwordResetPhone;
	private int phoneNumberLength = 10;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_phone_number_entry);
		primaryCarePhone = findViewById(R.id.primaryCareNumber);
		passwordResetPhone = findViewById(R.id.passwordResetNumber);

		primaryCarePhone.setText("1112223333");
		passwordResetPhone.setText("2223334444");

		TextFieldKeyboard textFieldKeyboard = new TextFieldKeyboard( getApplicationContext() );

		if(PersistentData.getCallClinicianButtonEnabled()) {
			textFieldKeyboard.makeKeyboardBehave(primaryCarePhone);
		}
		else {
			TextView primaryCarePhoneText = findViewById(R.id.primaryCareNumberLabel);
			primaryCarePhoneText.setVisibility(View.GONE);
			primaryCarePhone.setVisibility(View.GONE);
		}

		if(PersistentData.getCallResearchAssistantButtonEnabled()) {
			textFieldKeyboard.makeKeyboardBehave(passwordResetPhone);
		}
		else{
			TextView passwordResetPhoneText = findViewById(R.id.passwordResetNumberLabel);
			passwordResetPhoneText.setVisibility(View.GONE);
			passwordResetPhone.setVisibility(View.GONE);
		}
	}
	
	public void checkAndPromptConsent(View view) {
		String primary = primaryCarePhone.getText().toString().replaceAll("\\D+", "");
		String reset = passwordResetPhone.getText().toString().replaceAll("\\D+", "");

		if(PersistentData.getCallClinicianButtonEnabled()) {
			if (primary == null || primary.length() == 0){
				AlertsManager.showAlert( getString(R.string.enter_clinician_number), this );
				return;
			}
			if (primary.length() != phoneNumberLength){
				AlertsManager.showAlert( String.format( getString(R.string.phone_number_length_error), phoneNumberLength), this );
				return;
			}
		}
		if(PersistentData.getCallResearchAssistantButtonEnabled()) {
			if (reset == null || reset.length() == 0 ){
				AlertsManager.showAlert( getString(R.string.enter_research_assitant_number), this );
				return;
			}
			if (reset.length() != phoneNumberLength){
				AlertsManager.showAlert( String.format( getString(R.string.phone_number_length_error), phoneNumberLength), this );
				return;
			}
		}

		PersistentData.setPrimaryCareNumber(primary);
		PersistentData.setPasswordResetNumber(reset);
		startActivity(new Intent(getApplicationContext(), ConsentFormActivity.class));
		finish();
	}
}
