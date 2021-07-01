package org.orcatech.mobileresearchtool.ui.registration;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.networking.SurveyDownloader;
import org.orcatech.mobileresearchtool.storage.DataStream;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.storage.TextFileManager;
import org.orcatech.mobileresearchtool.ui.LoadingActivity;

public class ConsentFormActivity extends RunningBackgroundServiceActivity {
	private static String LOG_TAG = "ConsentFormActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_consent_form);
		
		TextView consentFormBody = findViewById(R.id.consent_form_body);
		consentFormBody.setText(PersistentData.getConsentFormText());
	}
	
	/** On the press of the do not consent button, we pop up an alert, allowing the user
	 * to press "Cancel" if they did not mean to press the do not consent. */
	public void doNotConsentButton(View view) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ConsentFormActivity.this);
		alertBuilder.setTitle("Do Not Consent");
		alertBuilder.setMessage(getString(R.string.doNotConsentAlert));
		alertBuilder.setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
				System.exit(0);
			}
		});
		alertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
			}} );
		alertBuilder.create().show();
	}
	
	public void consentButton(View view) {
		PersistentData.setRegistered(true);
		PersistentData.loginOrRefreshLogin();

		for (DataStream ds : DataStream.values()) {
			Log.i(LOG_TAG, ds.toString() + ": " + PersistentData.getDataStreamVal(ds));
		}

		// Download the survey questions and schedule the surveys
		SurveyDownloader.downloadSurveys(getApplicationContext());

		
		// Create new data files, these will now have a patientID prepended to those files
		TextFileManager.initialize(getApplicationContext());
		TextFileManager.makeNewFilesForEverything();
		
		//This is important.  we need to start timers...
		backgroundService.doSetup();
		
		// Start the Main Screen Activity, destroy this activity
		startActivity(new Intent(getApplicationContext(), LoadingActivity.class) );
		finish();
	}
}