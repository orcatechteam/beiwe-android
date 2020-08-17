package org.beiwe.app.listeners;

import org.beiwe.app.storage.TextFileManager;

import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;

import org.beiwe.app.R;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

public class AppUsageListener {
	private Context appContext;
	private PackageManager pkgManager;
	private UsageStatsManager usageStatsManager;

	public AppUsageListener(Context applicationContext) {
		this.appContext = applicationContext;
		this.pkgManager = appContext.getPackageManager();
		this.usageStatsManager = (UsageStatsManager) this.appContext.getSystemService(Context.USAGE_STATS_SERVICE);
	}

	@SuppressLint("LongLogTag")
	public void getAppUsage() throws PackageManager.NameNotFoundException {
		Log.i("AppUsageListener.getAppUsage", "<# # # # #>");

		// @TODO add conditional to check of the app has permission, if not, fire the startActivityForResult
//		startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 2);

		// @TODO update to pull the start date from Juno i.e. enrollment date
		long startMS = new GregorianCalendar(2019,0, 1).getTimeInMillis();
		long endMS = System.currentTimeMillis();
		assert this.usageStatsManager != null;
		Map<String, UsageStats> usageStatsMap = this.usageStatsManager.queryAndAggregateUsageStats(startMS, endMS);

		List<PackageInfo> apps = pkgManager.getInstalledPackages(0);
		int appsSize = apps.size();
		for (int i = 0; i < appsSize; i++) {
			PackageInfo app = apps.get(i);
			ApplicationInfo appInfo = pkgManager.getApplicationInfo(app.packageName, 0);
			CharSequence appLabel = pkgManager.getApplicationLabel(appInfo);
			long totalTimeUsageInMS = usageStatsMap.get(app.packageName).getTotalTimeInForeground();
			if (totalTimeUsageInMS > 0) {
				float totalTimeUsageInMinutes = totalTimeUsageInMS / 60000;
				String appUsageLogEntry = appLabel + ": " + totalTimeUsageInMinutes + " minutes (" + totalTimeUsageInMS + " ms)";
				Log.i("AppUsageListener.getAppUsage//totalTimeInForeground", appUsageLogEntry);
//				TextFileManager.getAppUsageLogFile().safeWritePlaintext(appUsageLogEntry);
//				TextFileManager.getAppUsageLogFile().writeEncrypted(appUsageLogEntry);
			}
		}

		Log.i("AppUsageListener.getAppUsage", "</# # # # #>");
	}
}