package org.beiwe.app.ui.user;

import org.apache.http.util.EncodingUtils;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import static org.beiwe.app.networking.PostRequest.addWebsitePrefix;

/**
 * The activity that shows the graph to the user. Displays the Beiwe webpage that houses the graph.
 * It also features the options to call clinician, as well as immediate sign out
 * 
 * @author Dor Samet
 */
@SuppressLint("SetJavaScriptEnabled")
public class GraphActivity extends SessionActivity {
	//extends SessionActivity

	/**
	 * Loads the web view by sending an HTTP POST to the website. Currently not in HTTPS
	 * 
	 * Consider removing the Lint warning about the Javascript
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_graph);

		Button callClinicianButton = findViewById(R.id.graph_call_clinician);
		if(PersistentData.getCallClinicianButtonEnabled()) {
			callClinicianButton.setText(PersistentData.getCallClinicianButtonText());
		}
		else {
			callClinicianButton.setVisibility(View.GONE);
		}

		// Instantiating web view to be embedded in the page
		WebView browser = findViewById(R.id.graph_pastResults);
		WebSettings browserSettings = browser.getSettings();
		browserSettings.setBuiltInZoomControls(true);
		browserSettings.setDisplayZoomControls(false);
		browserSettings.setSupportZoom(true);
		browser.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				view.loadUrl(url);
				return true;
			}
		});

		// Enable Javascript to display the graph, as well as initial scale
		browserSettings.setJavaScriptEnabled(true);
//		browser.setInitialScale(100);
//		browser.setFitsSystemWindows(true);
//		browser.setOverScrollMode(android.view.View.OVER_SCROLL_ALWAYS);
		browser.setNetworkAvailable(true);

		//TODO: Low priority. Eli. find a way to Kill this use of securityparameters, make securityparameters private.
		String postData = PostRequest.securityParameters(null);
		String graphUrl = addWebsitePrefix(getApplicationContext().getString(R.string.graph_url));
		browser.postUrl(graphUrl, EncodingUtils.getBytes(postData, "BASE64"));
	}

	public void callClinician(View view) {
		RunningBackgroundServiceActivity runningBgSvc = new RunningBackgroundServiceActivity();
		runningBgSvc.callClinician(view);
	}
}
