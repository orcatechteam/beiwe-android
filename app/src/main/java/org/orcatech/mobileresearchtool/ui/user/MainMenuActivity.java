package org.orcatech.mobileresearchtool.ui.user;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.orcatech.mobileresearchtool.BuildConfig;
import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.RunningBackgroundServiceActivity;
import org.orcatech.mobileresearchtool.session.SessionActivity;
import org.orcatech.mobileresearchtool.storage.PersistentData;
import org.orcatech.mobileresearchtool.survey.SurveyActivity;
import org.orcatech.mobileresearchtool.ui.utils.SurveyNotifications;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**The main menu activity of the app. Currently displays 4 buttons - Audio Recording, Graph, Call Clinician, and Sign out.
 * @author Dor Samet */
public class MainMenuActivity extends SessionActivity {
	//extends a SessionActivity

	@SuppressLint("LongLogTag")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.i("MainMenuActivity.onCreate", "isDev: " + BuildConfig.APP_IS_DEV);

		if (BuildConfig.BYPASS_MAIN_CONTENT) {
			setContentView(R.layout.activity_registration_complete);
			return;
		}

		setContentView(R.layout.activity_main_menu);

		Button callClinicianButton = findViewById(R.id.main_menu_call_clinician);
		if(PersistentData.getCallClinicianButtonEnabled()) {
			callClinicianButton.setText(PersistentData.getCallClinicianButtonText());
		}
		else {
			callClinicianButton.setVisibility(View.GONE);
		}

		ArrayList<String> permSurveyIds = new ArrayList<String>();
		for (String surveyId : PersistentData.getSurveyIds() ){
			try {
				JSONObject surveySettings = new JSONObject(PersistentData.getSurveySettings(surveyId));
				if (surveySettings.getBoolean("always_available")) {
					permSurveyIds.add(surveyId);
				}
			}
			catch (JSONException e) {e.printStackTrace();}
		}
		if (permSurveyIds.size() !=0 ) {
			for (int i = 0; i < permSurveyIds.size(); i++) {
				Button button = findViewById(getResources().getIdentifier("permSurvey" + i, "id", this.getPackageName()));
				if (PersistentData.getSurveyType(permSurveyIds.get(i)).equals("audio_survey")){
					button.setText(R.string.permaaudiosurvey);
				}
				button.setTag(R.string.permasurvey, permSurveyIds.get(i));
				button.setVisibility(View.VISIBLE);
			}
		}
	}
	
	/*#########################################################################
	############################## Buttons ####################################
	#########################################################################*/
	public void displaySurvey(View view) {
		Intent activityIntent;
		String surveyId = (String) view.getTag(R.string.permasurvey);

		if (PersistentData.getSurveyType(surveyId).equals("audio_survey")){
			activityIntent = new Intent(getApplicationContext(), SurveyNotifications.getAudioSurveyClass(surveyId));
		} else {
			activityIntent = new Intent(getApplicationContext(), SurveyActivity.class);
		}
		activityIntent.setAction( getApplicationContext().getString(R.string.start_tracking_survey) );
		activityIntent.putExtra("surveyId", surveyId);
		activityIntent.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP );
		startActivity(activityIntent);
	}

//	public void graphResults (View v) { startActivity( new Intent(getApplicationContext(), GraphActivity.class) ); }

	public void callClinician(View view) {
		RunningBackgroundServiceActivity runningBgSvc = new RunningBackgroundServiceActivity();
		runningBgSvc.callClinician(view);
	}
}
