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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.janacare.walkmeter.ActivityUtils.REQUEST_TYPE;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
public class MainActivity extends Activity {
	private static final String TAG = MainActivity.class.getSimpleName();
	
	TextView showStepsToday;
	TextView showStepsYesterday;
	TextView showStepsHighest;
	TextView tvwindow_title;
	TextView headToday;
	TextView headYesterday;
	TextView headHighest;
	TextView tvtodayAfter;
	
	Button btnCount;
	SharedPreferences vPref;
    
	private static final int MAX_LOG_SIZE = 5000;

    // Instantiates a log file utility object, used to log status updates
    private LogFile mLogFile;
    
    private REQUEST_TYPE mRequestType;


    IntentFilter mBroadcastFilter;

    // Instance of a local broadcast manager
    private LocalBroadcastManager mBroadcastManager;

    // The activity recognition update request object
    private DetectionRequester mDetectionRequester;

    // The activity recognition update removal object
    private DetectionRemover mDetectionRemover;

    /*
     * Set main UI layout, get a handle to the ListView for logs, and create the broadcast
     * receiver.
     */
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vPref = getApplicationContext().getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);   
        
        // Set the main layout
    
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.pedometer);
        
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.window_title);
        
        showStepsToday = (TextView)findViewById(R.id.tvStepsToday);
        showStepsYesterday = (TextView)findViewById(R.id.tvStepsYesterday);
        showStepsHighest = (TextView)findViewById(R.id.tvStepsHighest);
        btnCount = (Button)findViewById(R.id.btnCount);
        tvwindow_title = (TextView)findViewById(R.id.tvwindow_tite);
        tvtodayAfter = (TextView)findViewById(R.id.tvStepsTodayAfter);
        headToday = (TextView)findViewById(R.id.tvStepsToday1);
        headYesterday = (TextView)findViewById(R.id.tvStepsYesterday2);
        headHighest = (TextView)findViewById(R.id.tvStepsHighest3);
        
        Typeface regular = Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf");
        Typeface thin = Typeface.createFromAsset(getAssets(), "Roboto-Thin.ttf");
    	
        
        tvwindow_title.setTypeface(regular);
        showStepsToday.setTypeface(thin);
        showStepsYesterday.setTypeface(thin);
        showStepsHighest.setTypeface(thin);
        
        tvtodayAfter.setTypeface(regular);
        headToday.setTypeface(regular);
        headYesterday.setTypeface(regular);
        headHighest.setTypeface(regular);
        btnCount.setTypeface(regular);
        
        
        
        int i = vPref.getInt(Constants.KEY_STEPS_TODAY, 0);
    	int j = vPref.getInt("steps_yesterday", 0);
    	int k = vPref.getInt("minutes_highest", i);
    	Log.d("step  in", Long.toString(i));
    	Log.d("show steps", "showStepsToday: " + showStepsToday);
    	
    	showStepsToday.setText(Integer.toString(i));
    	showStepsYesterday.setText(Integer.toString(j));
    	showStepsHighest.setText(Integer.toString(k));
      
    	
        
        mBroadcastManager = LocalBroadcastManager.getInstance(this);

        // Create a new Intent filter for the broadcast receiver
        mBroadcastFilter = new IntentFilter(ActivityUtils.ACTION_REFRESH_STATUS_LIST);
        mBroadcastFilter.addCategory(ActivityUtils.CATEGORY_LOCATION_SERVICES);

        // Get detection requester and remover objects
        mDetectionRequester = new DetectionRequester(this);
        mDetectionRemover = new DetectionRemover(this);

        // Create a new LogFile object
        mLogFile = LogFile.getInstance(this);

        btnCount.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub

				Log.d(TAG,"before btn action" + vPref.getBoolean("btnFlag", false));

				Editor editor = vPref.edit();

				if(!vPref.getBoolean("btnFlag", false))
				{

					Log.d(TAG,"first time btn pressed");
					if (!servicesConnected()) 
					{
						return;
					}
					mRequestType = ActivityUtils.REQUEST_TYPE.ADD;
					mDetectionRequester.requestUpdates();
					btnCount.setText("Stop Counting");
					
					editor.putBoolean("btnFlag", true);
					editor.commit();
					
					Log.d(TAG,"" + vPref.getBoolean("btnFlag", false ));
				}
				else
				{

					Log.d(TAG, "in onClick after btn shows stop counting");
					if(!servicesConnected()) 
					{

						return;
					}

					mRequestType = ActivityUtils.REQUEST_TYPE.REMOVE;
					PendingIntent pendingIntent = mDetectionRequester.getRequestPendingIntent();

					if (pendingIntent == null) 
					{
						Intent intent = new Intent(getApplicationContext(), ActivityRecognitionIntentService.class);
						pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
					}

					if (pendingIntent != null) 
					{
						// Pass the remove request to the remover object
						mDetectionRemover.removeUpdates(pendingIntent);
						pendingIntent.cancel();
					}

					editor.putBoolean("btnFlag", false);
					editor.commit();
					btnCount.setText("Start Counting!");
				}
								       
			        
					
				
				
					
			}
		});
    
    }

    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case ActivityUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :

                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:

                        // If the request was to start activity recognition updates
                        if (ActivityUtils.REQUEST_TYPE.ADD == mRequestType) {

                            // Restart the process of requesting activity recognition updates
                            mDetectionRequester.requestUpdates();

                        // If the request was to remove activity recognition updates
                        } else if (ActivityUtils.REQUEST_TYPE.REMOVE == mRequestType ){

                                /*
                                 * Restart the removal of all activity recognition updates for the 
                                 * PendingIntent.
                                 */
                                mDetectionRemover.removeUpdates(
                                    mDetectionRequester.getRequestPendingIntent());

                        }
                    break;

                    // If any other result was returned by Google Play services
                    default:

                        // Report that Google Play services was unable to resolve the problem.
                        Log.d(ActivityUtils.APPTAG, getString(R.string.no_resolution));
                }

            // If any other request code was received
            default:
               // Report that this Activity received an unknown requestCode
               Log.d(ActivityUtils.APPTAG,
                       getString(R.string.unknown_activity_request_code, requestCode));

               break;
        }
    }

    
    
    @Override
	protected void onResume() {
	    super.onResume();
	
	    // Register the broadcast receiver
	    mBroadcastManager.registerReceiver(
	            updateListReceiver,
	            mBroadcastFilter);
	    
	    int i = vPref.getInt(Constants.KEY_STEPS_TODAY, 0);
		int j = vPref.getInt("steps_yesterday", 0);
		int k = vPref.getInt("minutes_highest", i);
		Log.d("step  in", Long.toString(i));
		Log.d("show steps", "showStepsToday: " + showStepsToday);
		
		showStepsToday.setText(Integer.toString(i));
		showStepsYesterday.setText(Integer.toString(j));
		showStepsHighest.setText(Integer.toString(k));
	    
	    if(!vPref.getBoolean("btnFlag", false))
	    {
	    	btnCount.setText("Start Counting!");
	    }
	    else
	    {
	    	btnCount.setText("Stop Counting");
	    }
	    // Load updated activity history
	    updateActivityHistory();
	}

	
	
    @Override
    protected void onPause() {

        // Stop listening to broadcasts when the Activity isn't visible.
        mBroadcastManager.unregisterReceiver(updateListReceiver);

        super.onPause();
    }

 
    
    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {

            // In debug mode, log the status
            Log.d(ActivityUtils.APPTAG, getString(R.string.play_services_available));

            // Continue
            return true;

        // Google Play services was not available for some reason
        } else {

            // Display an error dialog
            GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0).show();
            return false;
        }
    }
    /**
     * Respond to "Start" button by requesting activity recognition
     * updates.
     * @param view The view that triggered this method.
     */
       public void onStopUpdates(View view) {

        // Check for Google Play services
        if (!servicesConnected()) {

            return;
        }

        /*
         * Set the request type. If a connection error occurs, and Google Play services can
         * handle it, then onActivityResult will use the request type to retry the request
         */
        mRequestType = ActivityUtils.REQUEST_TYPE.REMOVE;
        
        PendingIntent pendingIntent = mDetectionRequester.getRequestPendingIntent();
        
        if (pendingIntent == null) {
        	Intent intent = new Intent(getApplicationContext(), ActivityRecognitionIntentService.class);
        	pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        if (pendingIntent != null) {
        	// Pass the remove request to the remover object
        	mDetectionRemover.removeUpdates(pendingIntent);

        	/*
        	 * Cancel the PendingIntent. Even if the removal request fails, canceling the PendingIntent
        	 * will stop the updates.
        	 */
        	pendingIntent.cancel();
        }
        
        // TODO: Update toggle button to "Start pedometer"
    }

    /**
     * Display the activity detection history stored in the
     * log file
     */
    
    private void updateActivityHistory() {
        // Try to load data from the history file
    	Log.d(TAG, "updateActivityHistory()");
    	try {
            // Load log file records into the List
            List<Spanned> activityDetectionHistory =
                    mLogFile.loadLogFile();

            // Clear the adapter of existing data
           // mStatusAdapter.clear();

            // Add each element of the history to the adapter
            for (Spanned activity : activityDetectionHistory) {
             //   mStatusAdapter.add(activity);
            }

            // If the number of loaded records is greater than the max log size
            //if (mStatusAdapter.getCount() > MAX_LOG_SIZE) 
            {

                // Delete the old log file
                if (!mLogFile.removeLogFiles()) {

                    // Log an error if unable to delete the log file
                    Log.e(ActivityUtils.APPTAG, getString(R.string.log_file_deletion_error));
                }
            
            }

            // Trigger the adapter to update the display
      //      mStatusAdapter.notifyDataSetChanged();

        // If an error occurs while reading the history file
        } catch (IOException e) {
            Log.e(ActivityUtils.APPTAG, e.getMessage(), e);
        }
    }

    /**
     * Broadcast receiver that receives activity update intents
     * It checks to see if the ListView contains items. If it
     * doesn't, it pulls in history.
     * This receiver is local only. It can't read broadcast Intents from other apps.
     */
    BroadcastReceiver updateListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            /*
             * When an Intent is received from the update listener IntentService, update
             * the displayed log.
             */
            updateActivityHistory();
        }
    };
}