package org.orcatech.mobileresearchtool.ui.user;

import org.orcatech.mobileresearchtool.R;
import org.orcatech.mobileresearchtool.session.SessionActivity;
import org.orcatech.mobileresearchtool.storage.PersistentData;

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
