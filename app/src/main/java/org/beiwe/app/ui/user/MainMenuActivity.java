package org.beiwe.app.ui.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.survey.SurveyActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**The main menu activity of the app. Currently displays 4 buttons - Audio Recording, Graph, Call Clinician, and Sign out.
 * @author Dor Samet */
public class MainMenuActivity extends SessionActivity {
	//extends a SessionActivity

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_menu);

		Button callClinicianButton = (Button) findViewById(R.id.main_menu_call_clinician);
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
				Button button = (Button) findViewById(getResources().getIdentifier("permSurvey" + i, "id", this.getPackageName()));
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
}
