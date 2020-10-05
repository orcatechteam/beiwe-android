package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;

import android.os.Bundle;
import android.widget.TextView;

/**The about page!
 * @author Everyone! */
public class AboutActivityLoggedIn extends SessionActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		
		TextView aboutPageBody = findViewById(R.id.about_page_body);
		aboutPageBody.setText(PersistentData.getAboutPageText());
	}
}
