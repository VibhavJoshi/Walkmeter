/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janacare.walkmeter;


import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;


import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Service that receives ActivityRecognition updates. It receives updates
 * in the background, even if the main Activity is not visible.
 */
public class ActivityRecognitionIntentService extends IntentService {

	static int min_on_foot_today;
	static int min_on_foot_yesterday;
	static int highest_steps;
	String Activity;
	Calendar cal;
	// Formats the timestamp in the log
	private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZ";

	// Delimits the timestamp from the log info
	private static final String LOG_DELIMITER = ";;";

	// A date formatter
	private SimpleDateFormat mDateFormat;

	// Store the app's shared preferences repository
	SharedPreferences mPrefs;

	public ActivityRecognitionIntentService() {
		// Set the label for the service's background thread
		super("ActivityRecognitionIntentService");
	}

	/**
	 * Called when a new activity detection update is available.
	 */
	@Override
	protected void onHandleIntent(Intent intent) {

		Log.d("inside onStartUpdates","got intent");
		// Get a handle to the repository
		mPrefs = getApplicationContext().getSharedPreferences(
				ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);

		// Get a date formatter, and catch errors in the returned timestamp
		try {
			mDateFormat = (SimpleDateFormat) DateFormat.getDateTimeInstance();
		} catch (Exception e) {
			Log.e(ActivityUtils.APPTAG, getString(R.string.date_format_error));
		}

		// Format the timestamp according to the pattern, then localize the pattern
		mDateFormat.applyPattern(DATE_FORMAT_PATTERN);
		mDateFormat.applyLocalizedPattern(mDateFormat.toLocalizedPattern());

		// If the intent contains an update
		if (ActivityRecognitionResult.hasResult(intent)) {

			// Get the update
			ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

			// Log the update
			logActivityRecognitionResult(result);

			countOnFootTime(result);

			// Get the most probable activity from the list of activities in the update
			DetectedActivity mostProbableActivity = result.getMostProbableActivity();

			// Get the confidence percentage for the most probable activity
			int confidence = mostProbableActivity.getConfidence();

			// Get the type of activity
			int activityType = mostProbableActivity.getType();

			// Check to see if the repository contains a previous activity
			if (!mPrefs.contains(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE)) {

				// This is the first type an activity has been detected. Store the type
				Editor editor = mPrefs.edit();
				editor.putInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, activityType);
				editor.commit();

				// If the repository contains a type
			} else if (
					// If the current type is "moving"
					isMoving(activityType)

					&&

					// The activity has changed from the previous activity
					activityChanged(activityType)

					// The confidence level for the current activity is > 50%
					&& (confidence >= 50)) {

				// Notify the user
				// Disabling notifications
				//sendNotification();
			}
		}
	}

	/**
	 * Post a notification to the user. The notification prompts the user to click it to
	 * open the device's GPS settings
	 */
	private void sendNotification() {

		// Create a notification builder that's compatible with platforms >= version 4
		NotificationCompat.Builder builder =
				new NotificationCompat.Builder(getApplicationContext());

		// Set the title, text, and icon
		builder.setContentTitle(getString(R.string.app_name))
		.setContentText(getString(R.string.turn_on_GPS))
		.setSmallIcon(R.drawable.ic_notification)

		// Get the Intent that starts the Location settings panel
		.setContentIntent(getContentIntent());

		// Get an instance of the Notification Manager
		NotificationManager notifyManager = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);

		// Build the notification and post it
		notifyManager.notify(0, builder.build());
	}
	/**
	 * Get a content Intent for the notification
	 *
	 * @return A PendingIntent that starts the device's Location Settings panel.
	 */
	private PendingIntent getContentIntent() {

		// Set the Intent action to open Location Settings
		Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

		// Create a PendingIntent to start an Activity
		return PendingIntent.getActivity(getApplicationContext(), 0, gpsIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
	}

	/**
	 * Tests to see if the activity has changed
	 *
	 * @param currentType The current activity type
	 * @return true if the user's current activity is different from the previous most probable
	 * activity; otherwise, false.
	 */
	private boolean activityChanged(int currentType) {

		// Get the previous type, otherwise return the "unknown" type
		int previousType = mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE,
				DetectedActivity.UNKNOWN);

		// If the previous type isn't the same as the current type, the activity has changed
		if (previousType != currentType) {
			return true;

			// Otherwise, it hasn't.
		} else {
			return false;
		}
	}

	/**
	 * Determine if an activity means that the user is moving.
	 *
	 * @param type The type of activity the user is doing (see DetectedActivity constants)
	 * @return true if the user seems to be moving from one location to another, otherwise false
	 */
	private boolean isMoving(int type) {
		switch (type) {
		// These types mean that the user is probably not moving
		case DetectedActivity.STILL :
		case DetectedActivity.TILTING :
		case DetectedActivity.UNKNOWN :
			return false;
		default:
			return true;
		}
	}

	/**
	 * Write the activity recognition update to the log file

	 * @param result The result extracted from the incoming Intent
	 */
	private void logActivityRecognitionResult(ActivityRecognitionResult result) {
		// Get all the probably activities from the updated result
		Log.d("inside onStartUpdates","logging result");
		for (DetectedActivity detectedActivity : result.getProbableActivities()) {

			// Get the activity type, confidence level, and human-readable name
			int activityType = detectedActivity.getType();
			int confidence = detectedActivity.getConfidence();
			String activityName = getNameFromType(activityType);

			// Make a timestamp
			String timeStamp = mDateFormat.format(new Date());

			// Get the current log file or create a new one, then log the activity
			LogFile.getInstance(getApplicationContext()).log(
					timeStamp +
					LOG_DELIMITER +
					getString(R.string.log_message, activityType, activityName, confidence)
					);

			LogData(timeStamp + " " + activityName + " " + confidence);
		}
	}

	/**
	 * Map detected activity types to strings
	 *
	 * @param activityType The detected activity type
	 * @return A user-readable name for the type
	 */
	private String getNameFromType(int activityType) {
		switch(activityType) {
		case DetectedActivity.IN_VEHICLE:
			return "in_vehicle";
		case DetectedActivity.ON_BICYCLE:
			return "on_bicycle";
		case DetectedActivity.ON_FOOT:
			return "on_foot";
		case DetectedActivity.STILL:
			return "still";
		case DetectedActivity.UNKNOWN:
			return "unknown";
		case DetectedActivity.TILTING:
			return "tilting";

		}
		return "unknown";
	}

	public void LogData(String input)
	{

		Log.d("in LogData","in LogData " + input);
		try
		{
			File root = new File(Environment.getExternalStorageDirectory().getPath() + "/activityrecognition");
			File ActivityLog = new File(Environment.getExternalStorageDirectory().getPath() + "/activityrecognition", "ActivityLog.txt");
			if(root.isDirectory())
			{
				Log.d("root exists","root exists");
				if(ActivityLog.exists())
				{

					FileWriter outFile = new FileWriter(ActivityLog, true);
					Log.d("writing","writing " + ActivityLog.getAbsolutePath());
					PrintWriter out = new PrintWriter(outFile);
					out.println(input);
					out.flush();
					out.close();
				}
				else
				{
					Log.d("writing2","writing2");
					ActivityLog.createNewFile();
					FileWriter outFile = new FileWriter(ActivityLog, true);
					PrintWriter out = new PrintWriter(outFile);
					out.println(input);
					out.flush();
					out.close();
				}
			}
			else
			{
				root.mkdir();
				ActivityLog.createNewFile();

				FileWriter outFile = new FileWriter(ActivityLog, true);

				PrintWriter out = new PrintWriter(outFile);
				out.println(input);
				out.flush();
				Log.d("writing3","writing3");
				out.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void countOnFootTime(ActivityRecognitionResult result)
	{
		DetectedActivity mostPActivity = result.getMostProbableActivity();
		int confidence = mostPActivity.getConfidence();
		int activityType = mostPActivity.getType();



		cal = Calendar.getInstance();

		Log.d("start of day", "" + cal.getTime());

		Activity = getNameFromType(activityType);

		Log.d("intent activity type", Activity);
		if(!mPrefs.contains("bucket_start_time"))
		{
			Editor editor = mPrefs.edit();
			editor.putLong("bucket_start_time",cal.getTimeInMillis());
			editor.putLong("Start_of_day", cal.getTimeInMillis());
			editor.putInt("minutes_today",0);
			editor.putInt("minutes_yesterday", 0);
			editor.putInt("minutes_highest", 0);
			editor.putInt("activityType", activityType);
			editor.putInt("confidence", confidence);
			editor.putInt(Constants.KEY_STEPS_TODAY, 0);
			editor.putInt("steps_yesterday", 0);
			editor.putString("highest_date",cal.getTime().toString() );
			editor.commit();
		}
		else  	
		{
			Long currentTime = cal.getTimeInMillis();
			Long BucketStart = mPrefs.getLong("bucket_start_time", currentTime);
			Long BucketEnd = BucketStart + 60000;
			Long BucketCheck = BucketStart + 120000;

			Log.d("bucketStart", Long.toString(BucketStart));
			Log.d("currentTime", Long.toString(currentTime));
			Log.d("bucketEnd", Long.toString(BucketEnd));
			Log.d("BucketCheck", Long.toString(BucketCheck));

			if(currentTime >= BucketCheck)
			{
				BucketStart = currentTime;
				Editor editor = mPrefs.edit();
				editor.putLong("bucket_start_time",currentTime);
				editor.putInt("activityType", activityType);
				editor.putInt("confidence", confidence);
				editor.commit();
			}
			else if(currentTime >= BucketEnd)
			{
				Log.d("IntentService","bucket over " + mPrefs.getInt("minutes_highest", 0));
				Editor editor = mPrefs.edit();
				
				int StoredActivity = mPrefs.getInt("activityType", 0);
				if(StoredActivity == DetectedActivity.ON_FOOT)
				{
					//Long StartDay = cal2.getTimeInMillis();
					//if(!(StartDay > mPrefs.getLong("Start_of_day", 0)))
					Long prevStart = mPrefs.getLong("Start_of_day", 0);
					Calendar prevStartCal = Calendar.getInstance();
					prevStartCal.setTimeInMillis(prevStart);
					if(cal.get(Calendar.DAY_OF_YEAR) == prevStartCal.get(Calendar.DAY_OF_YEAR))
					{	
						int prev_steps = mPrefs.getInt(Constants.KEY_STEPS_TODAY, 0);
						editor.putInt(Constants.KEY_STEPS_TODAY, prev_steps + 1);
						min_on_foot_today = mPrefs.getInt(Constants.KEY_STEPS_TODAY, 0);
						min_on_foot_yesterday = mPrefs.getInt("steps_yesterday", 0);
						Log.d("counting on_foot",Integer.toString(mPrefs.getInt(Constants.KEY_STEPS_TODAY, 0)));
					}

					else
					{
						editor.putLong("Start_of_day", cal.getTimeInMillis());
						editor.putInt("steps_yesterday", mPrefs.getInt(Constants.KEY_STEPS_TODAY,-1 ));
						editor.putInt(Constants.KEY_STEPS_TODAY, 1);
						min_on_foot_today = mPrefs.getInt(Constants.KEY_STEPS_TODAY, 0);
						min_on_foot_yesterday = mPrefs.getInt("steps_yesterday", 0);
					}

					


				}	

				Log.d("IntentService","" +mPrefs.getInt(Constants.KEY_STEPS_TODAY, -1));
				Log.d("IntentService","" + mPrefs.getInt("minutes_highest", -1));
				
				if(mPrefs.getInt("minutes_highest",0) == 0)
				{
					editor.putInt("minutes_highest", min_on_foot_today);
					editor.putString("highest_date",cal.getTime().toString() );
					editor.commit();
				}
				if(mPrefs.getInt(Constants.KEY_STEPS_TODAY, 0) >= mPrefs.getInt("minutes_highest",0) )
				{
					
					Log.d("IntentService","steps today > minutes highest");
					editor.putInt("minutes_highest",mPrefs.getInt(Constants.KEY_STEPS_TODAY,-1));
					editor.putString("highest_date",cal.getTime().toString() );
					editor.commit();
				}

				BucketStart = BucketEnd;
				editor.putLong("bucket_start_time",currentTime);
				editor.putInt("activityType", activityType);
				editor.putInt("confidence", confidence);
				editor.commit();
			}
			else if(BucketEnd >= currentTime && currentTime >= BucketStart)
			{
				int StoredActivityType = mPrefs.getInt("activityType", 0);
				int StoredConfidence = mPrefs.getInt("confidence", 0);

				if(confidence > StoredConfidence)
				{
					Editor editor = mPrefs.edit();
					editor.putInt("activityType", activityType);
					editor.putInt("confidence", confidence);
					editor.commit();
				}
			}
		}

	}
}
