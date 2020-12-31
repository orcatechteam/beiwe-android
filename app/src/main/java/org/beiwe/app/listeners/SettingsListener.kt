package org.beiwe.app.listeners

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DEFAULT_ALL
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import org.beiwe.app.R
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.networking.PostRequest.addWebsitePrefix
import org.beiwe.app.storage.DataStream
import org.beiwe.app.storage.DataStreamPermission
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.ui.user.MainMenuActivity
import org.json.JSONException
import org.json.JSONObject

class SettingsListener(val appContext: Context) {
	companion object {
		const val LOG_TAG: String = "SettingsListener"
		const val APP_NOTIFICATION_CHANNEL_ID = "com.beiwe.app.APP_NOTIFICATION"
	}

	val stopTime = System.currentTimeMillis() + 1000 * 60 * 60 // One hour to upload files
	val settingsFrequency: Long = 1000 * 10 // 1000 * 60 * 60 * 24 // 24 hrs
	private lateinit var existingSettings: MutableMap<String, DataStreamPermission>

	fun checkSettings() {
		Log.i("SettingsListener", "Checking settings...")
		createNotificationChannel()
		getExistingSettings()
		val settingsURL = addWebsitePrefix("/download_settings")
		SettingsAsyncTask(settingsURL).execute()
		// @TODO [~] Start a countdown to stop checking after an hour of attempting using `stopTime`???
	}

	private fun getExistingSettings() {
		if (!::existingSettings.isInitialized) {
			existingSettings = mutableMapOf()
		}
		for (ds in DataStream.values()) {
			existingSettings[ds.toString()] = PersistentData.getDataStreamVal(ds)
		}
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val name = "Beiwe app notification channel"
			val descriptionText = "Beiwe app notification channel"
			val appNotificationChannel = NotificationChannel(
					APP_NOTIFICATION_CHANNEL_ID,
					name,
					NotificationManager.IMPORTANCE_HIGH
			).apply {
				description = descriptionText
				importance = NotificationManager.IMPORTANCE_HIGH
			}
			val notificationManager = NotificationManagerCompat.from(appContext)
			notificationManager.createNotificationChannel((appNotificationChannel))
		}
	}

	private fun unmarshalSettings(settings: String?) {
		if (settings == null) {
			Log.e(LOG_TAG, "Error getting settings...")
			return
		}
		Log.i(LOG_TAG, "Got settings from the server... `${settings}`")
		try {
			val obj = JSONObject(settings)
			processSettings(obj.getJSONObject("device_settings"))
		} catch (e: JSONException) {
			Log.e(LOG_TAG, "unmarshalSettings//JSONException encountered: `$e`")
			// @TODO [~] Trigger retry
		} catch (e: NullPointerException) {
			Log.e(LOG_TAG, "unmarshalSettings//NullPointerException encountered: `$e`")
			// @TODO [~] Trigger retry
		}
	}

	private fun marshalExistingSettings(): String? {
		val gson = Gson()
		return gson.toJson(existingSettings)
	}

	private fun processSettings(settings: JSONObject) {
		val changes = ArrayList<SettingModel>()

		settings.keys().forEach {
			var sM: SettingModel? = null
			if (existingSettings.containsKey(it)) {
				val settingPerm = DataStreamPermission.valueOf(settings.getString(it))
				sM = SettingModel(it, existingSettings.getValue(it), settingPerm)
			}
			if (sM != null && sM.isChanged()) {
				changes.add(sM)
				Log.i(LOG_TAG, sM.msg())
			}
		}

		Log.i(LOG_TAG, "Finished processing settings... found ${changes.size} changes")
		handleChangedSettings(changes)
	}

	private fun handleChangedSettings(changed: ArrayList<SettingModel>) {
		if (changed.size == 0) {
			return
		}

		var uploadChanges = false

		changed.forEach {
			val (key, persistentData, incomingSettings) = it
			var saveChange = false

			// existing stream is "disabled" and will become "requested"
			if (persistentData.isDisabled && incomingSettings.isRequested) {
				Log.i(LOG_TAG, "`$key` will change from `disabled` to `requested`")
				handleNotification(key)
				saveChange = true
			}

			// existing stream is "enabled" and will become "disabled"
			if (persistentData.isEnabled && incomingSettings.isDisabled) {
				Log.i(LOG_TAG, "`$key` will change from `enabled` to `disabled`")
				saveChange = true
			}

			if (saveChange) {
				PersistentData.setDataStreamVal(key, incomingSettings.toString())
				existingSettings[key] = incomingSettings
				uploadChanges = true
			}
		}

		val settingsUploadURL = addWebsitePrefix("/upload_settings")
		if (uploadChanges) {
			SettingsUploadAsyncTask(settingsUploadURL).execute()
		}

		// @TODO [~] Add a retry interval if the settings pull fails???
	}

	private fun handleNotification(key: String) {
		val intent = Intent(appContext, MainMenuActivity::class.java)
		intent.putExtra("changedPermission", key)
		val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, 0)
		val notification = NotificationCompat.Builder(appContext, APP_NOTIFICATION_CHANNEL_ID)
				.run {
					// @TODO [~] Replace text & title copy with real info...
					setContentTitle("Settings changed")
					setContentText("The setting for `$key` has changed and requires your attention")
					setSmallIcon(R.drawable.ic_recording_notification)
					setContentIntent(pendingIntent)
					setDefaults(DEFAULT_ALL)
					setAutoCancel(true)
					setCategory(NotificationCompat.CATEGORY_EVENT)
					setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
					priority = NotificationCompat.PRIORITY_MAX
					build()
				}
		with(NotificationManagerCompat.from(appContext)) {
			notify(
					System.currentTimeMillis().toInt(),
					notification
			)
		}
	}

	data class SettingModel(val key: String = "", val persistentData: DataStreamPermission, val incomingSettings: DataStreamPermission) {
		fun isChanged(): Boolean {
			return incomingSettings != persistentData
		}

		fun msg(): String {
			return "${key}: persist: ${persistentData}, incomingSettings: $incomingSettings"
		}
	}

	@SuppressLint("StaticFieldLeak")
	inner class SettingsAsyncTask constructor(val url: String): AsyncTask<String, String, String>() {
		override fun doInBackground(vararg params: String?): String? {
			try {
				return PostRequest.httpRequestString("", url)
			} catch (e: Exception) {
				Log.e(LOG_TAG, "SettingsAsyncTask//error encountered: `$e`")
			}
			return null
		}

		override fun onPostExecute(result: String?) {
			super.onPostExecute(result)
			unmarshalSettings(result)
		}
	}

	@SuppressLint("StaticFieldLeak")
	inner class SettingsUploadAsyncTask constructor(val url: String): AsyncTask<String, String, String>() {
		override fun doInBackground(vararg params: String?): String? {
			val existingSettingsJson = marshalExistingSettings()
			Log.d(LOG_TAG, "existingSettingsJson: `$existingSettingsJson`")
			/*
			try {
				return PostRequest.httpRequestString(PostRequest.makeParameter("settings", smJson), url)
			} catch (e: Exception) {
				Log.e(LOG_TAG, "SettingsUploadTask//error encountered: `$e`")
			}
			*/
			return null
		}

		override fun onPostExecute(result: String?) {
			super.onPostExecute(result)
//			unmarshalSettings(result)
		}
	}
}