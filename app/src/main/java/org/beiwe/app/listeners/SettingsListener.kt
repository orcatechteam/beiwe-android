package org.beiwe.app.listeners

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DEFAULT_ALL
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.beiwe.app.R
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.networking.PostRequest.addWebsitePrefix
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.ui.user.MainMenuActivity
import org.json.JSONException
import org.json.JSONObject
import kotlin.reflect.KFunction

class SettingsListener(val appContext: Context) {
	companion object {
		const val LOG_TAG: String = "SettingsListener"
		const val APP_NOTIFICATION_CHANNEL_ID = "com.beiwe.app.APP_NOTIFICATION"
	}

	val stopTime = System.currentTimeMillis() + 1000 * 60 * 60 // One hour to upload files
	val settingsFrequency: Long = 1000 * 10 // 1000 * 60 * 60 * 24 // 24 hrs
	private lateinit var existingSettings: MutableMap<String, Boolean>

	fun checkSettings() {
		Log.i("SettingsListener", "Checking settings...")
		createNotificationChannel()
		getExistingSettings()
		val settingsURL = addWebsitePrefix("/download_settings")
		SettingsAsyncTask(settingsURL).execute()
		// @TODO [~] Start a countdown to stop checking after an hour of attempting using `stopTime`
	}

	private fun getExistingSettings() {
		existingSettings = mutableMapOf(
				"accelerometer" to PersistentData.getAccelerometerEnabled(),
				"gps" to PersistentData.getGpsEnabled(),
				"calls" to PersistentData.getCallsEnabled(),
				"texts" to PersistentData.getTextsEnabled(),
				"wifi" to PersistentData.getWifiEnabled(),
				"bluetooth" to PersistentData.getBluetoothEnabled(),
				"power_state" to PersistentData.getPowerStateEnabled(),
				"gyro" to PersistentData.getGyroscopeEnabled()
		)
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

	private fun marshalSettings() {

	}

	private fun processSettings(settings: JSONObject) {
		val permissions = listOf<String>("requested", "denied", "enabled", "disabled")
		val changes = ArrayList<SettingModel>()

		// @TODO [X] Loop through all the settings and check PersistentData for value
		settings.keys().forEach {
			var sM: SettingModel? = null
			if (existingSettings.containsKey(it)) {
				sM = SettingModel(it, existingSettings.getValue(it), settings.getBoolean(it))
			}
			// @TODO [X] If incoming value != stored value, keep track of it somewhere so we can store the new value
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

		val setters = mapOf(
				"accelerometer" to PersistentData::setAccelerometerEnabled,
				"gps" to PersistentData::setGpsEnabled,
				"calls" to PersistentData::setCallsEnabled,
				"texts" to PersistentData::setTextsEnabled,
				"wifi" to PersistentData::setWifiEnabled,
				"bluetooth" to PersistentData::setBluetoothEnabled,
				"power_state" to PersistentData::setPowerStateEnabled,
				"gyro" to PersistentData::setGyroscopeEnabled
		)

		// @TODO [~] Take action on the settings that have changed
		changed.forEach {
			val (key) = it
			Log.i(LOG_TAG, "`$key` is changed? ${it.isChanged()}")
			Log.i(LOG_TAG, it.toString())
			// @TODO [~] Save new setting
			if (setters.containsKey(key)) {
				Log.i(LOG_TAG, "Triggering notification for `$key`...")
				setters[key]?.invoke(it.settingsEnabled)
				existingSettings[key] = it.settingsEnabled

				// @TODO [X] Trigger notification
				if (it.settingsEnabled) {
					handleNotification(key)
				}

				// @TODO [X] Send a report back to the server to update beiwe_device_settings
				// @TODO [X] Create a settings JSON to send to the producer
				val settingsUploadURL = addWebsitePrefix("/upload_settings")
				SettingsUploadAsyncTask(settingsUploadURL).execute()
			}
		}
		// @TODO [~] Add a retry interval if the settings pull fails.
	}

	private fun handleNotification(key: String) {
		// @TODO [~] Update with an intent that will handle requesting the perm... check RegisterActivity or MainActivity
		val intent = Intent(appContext, MainMenuActivity::class.java)
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

	data class SettingModel(val key: String = "", val persistentDataEnabled: Boolean = false, val settingsEnabled: Boolean = false) {
		fun isChanged(): Boolean {
			return settingsEnabled != persistentDataEnabled
		}

		fun msg(): String {
			return "${key}: persist: ${persistentDataEnabled}, settings: $settingsEnabled"
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
			val gson = Gson()
			val smJson = gson.toJson(existingSettings)
			Log.d(LOG_TAG, "smJson: `$smJson`")
			try {
				return PostRequest.httpRequestString(PostRequest.makeParameter("settings", smJson), url)
			} catch (e: Exception) {
				Log.e(LOG_TAG, "SettingsUploadTask//error encountered: `$e`")
			}
			return null
		}

		override fun onPostExecute(result: String?) {
			super.onPostExecute(result)
//			unmarshalSettings(result)
		}
	}
}