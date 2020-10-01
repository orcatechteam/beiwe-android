package org.beiwe.app.listeners;

import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.beiwe.app.storage.TextFileManager;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

public class AppUsageListener {
	private PackageManager pkgManager;
	private UsageStatsManager usageStatsManager;

	public static String header = "appLabel, appPackageName, lastTimeUsed, lastTimeVisible, totalTimeInForeground, totalTimeVisible";

	public AppUsageListener(Context applicationContext) {
		this.pkgManager = applicationContext.getPackageManager();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
			this.usageStatsManager = (UsageStatsManager) applicationContext.getSystemService(Context.USAGE_STATS_SERVICE);
		}
	}

	public void getAppUsage() throws PackageManager.NameNotFoundException {
		// @TODO [~] add conditional to check of the app has permission, if not, fire the startActivityForResult
//		startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 2);

		// @TODO [~] update to pull the start date from Juno i.e. enrollment date
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
			UsageStats appStats = usageStatsMap.get(app.packageName);
			if (appStats == null) {
				continue;
			}

			long totalTimeInForeground = appStats.getTotalTimeInForeground();
			if (totalTimeInForeground <= 0) {
				continue;
			}

			Long javaTimeCode = System.currentTimeMillis();
			long lastTimeUsed = appStats.getLastTimeUsed();
			long lastTimeVisible = appStats.getLastTimeVisible();
			long totalTimeVisible = appStats.getTotalTimeVisible();

			ArrayList<Object> appStatsForExport = new ArrayList<>();
			appStatsForExport.add(javaTimeCode);
			appStatsForExport.add(appLabel);
			appStatsForExport.add(app.packageName);
			appStatsForExport.add(lastTimeUsed);
			appStatsForExport.add(lastTimeVisible);
			appStatsForExport.add(totalTimeInForeground);
			appStatsForExport.add(totalTimeVisible);
			String appUsageLogEntry = TextUtils.join(TextFileManager.DELIMITER, appStatsForExport);
			Log.i("AppUsageListener", appUsageLogEntry);

			TextFileManager.getAppUsageLogFile().writeEncrypted(appUsageLogEntry);
		}
	}
}