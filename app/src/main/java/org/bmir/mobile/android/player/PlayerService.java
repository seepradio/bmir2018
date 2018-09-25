/**
 * Long-running Android background service plays the music.
 *
 * Copyright bmir.org and shoutingfire.com 2011,2018
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bmir.mobile.android.player;

import java.io.IOException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

public class PlayerService extends Service implements
MediaPlayer.OnPreparedListener, 
MediaPlayer.OnErrorListener, 
MediaPlayer.OnInfoListener, 
MediaPlayer.OnBufferingUpdateListener,
AudioManager.OnAudioFocusChangeListener {

 	/**
	 * For logging
	 */
	private static final String appname = Constants.APP_NAME_MIXED;

	/**
	 * Reference to the Android media player.
	 */
	private MediaPlayer _mediaPlayer = null;

	/**
	 * Reference to this Android application context.
	 */
	private Context _applicationContext = null;
	
	/**
	 * 'State' value strings for this service.
	 */
	public static final String STATE_KEY = "STATE_KEY";
	public static final String STATE_STOPPED = "STATE_STOPPED";
	public static final String STATE_PREPARING = "STATE_PREPARING";
	public static final String STATE_PLAYING = "STATE_PLAYING";
	public static final String STATE_PAUSED = "STATE_PAUSED";

	/**
	 * Possible state values for this service.
	 */
	private enum State {
		Stopped,
		Preparing,
		Playing,
		Paused
	};

	/**
	 * State variable for this service.
	 */
	private State _state = State.Stopped;

	/**
	 * Possible Notification titles.
	 */
	private enum Title {
		Stopped,    // No notification in tray.  Background service.  
		Playing,    // Persistent, non-clearable notification in tray.  Foreground service.
		Error       // Non-persistent, clearable notification in tray.  Background service.
	}
    
	/**
	 * Define actions accepted by this service.
	 * Note: These must match definitions in AndroidManifest.xml
	 */
	public static final String ACTION_BUTTON = Constants.PACKAGE_NAME + ".playerservice.action.BUTTON";
	public static final String ACTION_STATUS = Constants.PACKAGE_NAME + ".playerservice.action.STATUS";

	/**
	 * Used to notify the user.
	 */
	private NotificationManager _notificationManager = null;
	private Notification _notification = null;
	private NotificationCompat.Builder _notificationBuilder = null;
	private static final int NOTIFICATION_ID = Constants.NOTIFICATION_ID;
	private static final String NOTIFICATION_CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID;

	/**
	 * Count of play/stop button clicks while the player is starting.
	 * Implements a mechanism to allow the player to recover from failed connection attempts.
	 */
	private int _clicksWhilePreparing = 0;
	
	/**
	 * The max count of play/stop button clicks while the player is starting before restarting.
     * If the user clicks 3 times while the 'dots' image is visible, the player will be reset.
	 */
	private static final int MAX_CLICKS_WHILE_PREPARING = 3;

	/**
	 * Logs messages to the console.
     * Enable/disable logging here when publishing to Android Market.
	 */
	private static final String _logTag = PlayerService.class.getName().toString();
	private static void sop(String method, String message) {
		//Log.d(_logTag, method + ": " + message);
	}
	
    /** 
     * Not used.
     */
    @Override
    public IBinder onBind(Intent intent) {
    	return null;
    }

	/**
	 * Entry point.  Called when consumer starts this service.
	 */
	@Override
	public synchronized void onCreate() {
		String m = "onCreate";
		sop(m,"Entry.");

		_applicationContext = getApplicationContext();
		
		sop(m,"Exit.");
	}
	
	/**
	 * Entry point.  Also called when consumer starts this service.
	 */
	@Override
	public synchronized int onStartCommand(Intent intent, int flags, int startId) {
		String m = "onStartCommand";
		sop(m,"Entry. startId=" + startId + " intent=" + intent);
		
		// 2011-1213 Tolerate null intent.  NPE reported at Android Market dashboard.
		if (null == intent) {
			sop(m,"Early exit. intent is unexpectedly null. Nothing to do.");
			return START_STICKY;
		}
		
		String action = intent.getAction();
		if (ACTION_BUTTON.equals(action)) {
			
	        if (State.Preparing == _state) {
	        	impatientClicks();
	        }
	        else if (State.Playing == _state || State.Paused == _state) {
	        	stopMusic(true);
	        }
	        else if (State.Stopped == _state) {
	        	prepareMusic();
	        }
	        else {
	        	throw new RuntimeException(appname + " Code Bug: add support for new state=" + getStateString());
	        }
		}
		else if (ACTION_STATUS.equals(action)) {
			broadcastState();
		}
		else {
			throw new RuntimeException(appname + " Error: Received unrecognized intent action. action=" + action);
		}

		sop(m,"Exit. Returning START_STICKY");
		return START_STICKY;
	}

	/**
	 * Handles redundant clicks while music player is starting.
	 */
	private void impatientClicks() {
		String m = "impatientClicks";

		_clicksWhilePreparing++;
		if (MAX_CLICKS_WHILE_PREPARING <= _clicksWhilePreparing) {
			sop(m,"User clicked too many times while preparing. Must be dead. Stopping.");
			stopMusic(true);
		}
		else {
			sop(m,"Ignoring click while preparing. clicks=" + _clicksWhilePreparing + " max=" + MAX_CLICKS_WHILE_PREPARING);
		}
	}
	
	/**
	 * Starts playing the music.
	 * Note: This synchronous method is good for debug, but not for production. 
	 */
	private void prepareMusic() {
		String m = "prepareMusic";
		sop(m,"Entry.");
		
		// Check.
		if (null != _mediaPlayer) {
			throw new RuntimeException(appname + " Error: Code bug.  _mediaPlayer is not null.");
		}

		try {
			// Indicate that we are preparing the player.
			_state = State.Preparing;
			_clicksWhilePreparing = 0;
			notifyUser(Title.Playing, getResources().getString(R.string.STR_PREPARING));
			broadcastState();

			// Check whether the internet is enabled and available.
			if (!Utilities.networkAvailable(this)) {
				sop(m,"ARRGH: Network is not connected.  Try again later.");
				stopMusic(false);
				String message = getResources().getString(R.string.STR_INTERNET_UNAVAILABLE);
				notifyUser(Title.Error, message);
				postToast(message);
				return;
			}
			
			// Set up the Android Media Player.			
			_mediaPlayer = new MediaPlayer();
			// For Android API 26 (Android 8 Oreo) and newer, specify AudioAttributes.
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				sop(m,"Setting audio attributes for Android API 26 and later.");
				AudioAttributes.Builder builder = new AudioAttributes.Builder();
				//builder.setLegacyStreamType(AudioManager.STREAM_MUSIC);
				builder.setUsage(AudioAttributes.USAGE_MEDIA);
				builder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
				AudioAttributes attributes = builder.build();
				_mediaPlayer.setAudioAttributes(attributes);
				sop(m,"Set audio attributes.");
			}
			else {
				sop(m,"Setting audio stream type for older Android APIs before 26.");
				_mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				sop(m,"Set audio stream type.");
			}

			_mediaPlayer.setDataSource(Constants.MEDIA_URL_STRING);
			_mediaPlayer.setOnPreparedListener(this);
			_mediaPlayer.setOnErrorListener(this);
			_mediaPlayer.setOnInfoListener(this);
			_mediaPlayer.setOnBufferingUpdateListener(this);

			// Ask Android to prepare the player asynchronously because this normally takes a few seconds.
			// When ready, Android calls onPrepared().
			sop(m,"Calling mediaPlayer.prepareAsync.");
			_mediaPlayer.prepareAsync();
		} 
		catch (IOException e) {
			sop(m,"Error. Could not prepare media player. " + e.getMessage());

			// Free media player resources.
			_mediaPlayer.release();
			_mediaPlayer = null;

			// Indicate that the player has stopped.
			_state = State.Stopped;
			_clicksWhilePreparing = 0;
			String message = getResources().getString(R.string.STR_MEDIA_PLAYER_TROUBLE); 
			notifyUser(Title.Error, message);
			postToast(message);
		}
		
		sop(m,"Exit.");
	}

	/**
	 * Callback to start playing the music when the Media Player has been prepared.
	 */
	public synchronized void onPrepared(MediaPlayer mediaPlayer) {
		String m = "onPrepared";
		sop(m,"Entry.");
    	
		// Checks.
		if (_mediaPlayer != mediaPlayer) {
			throw new RuntimeException(appname + " Error: Received different media player.");
		}
		if (_state != State.Preparing) {
			throw new RuntimeException(appname + " Error: Code bug.  Received unexpected callback.");
		}

		// Request audio focus from Android.
		boolean granted = getAudioFocus();
		if (!granted) {
			sop(m,"Andriod declined audio focus. Stopping.");
			stopMusic(false);
			String message = getResources().getString(R.string.STR_AUDIO_FOCUS_DECLINED);
			notifyUser(Title.Error, message);
			postToast(message);
			sop(m,"Exit on declined audio focus.");
			return;
		}
    	
		// Start the music.
		sop(m,"Calling mediaPlayer.start().");
		_mediaPlayer.start();

		// Indicate that the player has started.
		_state = State.Playing;
		_clicksWhilePreparing = 0;
		broadcastState();
		notifyUser(Title.Playing, getResources().getString(R.string.STR_SELECT_TO_RETURN));
    	
		sop(m,"Exit.");
	}

	/**
	 * Pauses music.
	 */
	private void pauseMusic() {
		String m = "pauseMusic";
		if (State.Playing == _state & _mediaPlayer.isPlaying()) {
			_mediaPlayer.pause();
			_state = State.Paused;
			sop(m,"Paused music. state=" + getStateString());
			notifyUser(Title.Playing, getResources().getString(R.string.STR_PAUSED));
		}
		else {
			sop(m,"Warning: Can not pause music. state=" + getStateString());
		}
	}
    
    /**
     * Restores music.
     */
    private void resumeMusic() {
    	String m = "resumeMusic";
    	if (State.Paused == _state) {
    		_mediaPlayer.start();
    		_state = State.Playing;
    		sop(m,"Resumed playing music. state=" + getStateString());
    		notifyUser(Title.Playing, getResources().getString(R.string.STR_SELECT_TO_RETURN));
    	}
    	else {
    		sop(m,"Warning: Can not resume playing music. state=" + getStateString());
    	}
    }
    
    /**
     * Error callback from the Media Player.
     */
	@Override
	public synchronized boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
		String m = "onError";
		sop(m,"Entry. what=" + Utilities.getMediaPlayerErrorString(what) + " extra=" + extra);
		//ring errorMessage = getResources().getString(R.string.STR_CONNECT_ERROR);
		
    	// Check.
    	if (_mediaPlayer != mediaPlayer) {
    		throw new RuntimeException(appname + " Error: Received different media player.");
    	}

		// Expand error.
		if (MediaPlayer.MEDIA_ERROR_SERVER_DIED == what) {
			sop(m,"ARRGH: Media server died. Cleaning up.");
		}
		else {
			sop(m,"ARRGH: Unrecognized error from Android. what=" + what);
		}

		// Clean up.
  		stopMusic(false);
		String message = getResources().getString(R.string.STR_CONNECT_ERROR);
		notifyUser(Title.Error, message);
		postToast(message);
		
		// Indicate that we have handled the error.
		sop(m,"Exit. Returning true.");
		return true;
	}
	
    /**
     * Receives warnings from the Media Player.
     */
	@Override
	public synchronized boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
		String m = "onInfo";
		sop(m,"Entry. what=" + Utilities.getMediaPlayerInfoString(what) + " extra=" + extra);

		// Confirm the media player matches.
		if (!mediaPlayer.equals(_mediaPlayer)) {
    		throw new RuntimeException(appname + " Error: Received different media player.");
		}
		
		// Tell Android to discard the info.
		return false;
	}
	
	/**
	 * Stops playing the music.
	 */
	private void stopMusic(boolean sendNotification) {
		String m = "stopMusic";
		sop(m,"Entry. sendNotification=" + sendNotification);
		
		// Stop the music.
		if (null != _mediaPlayer) {
			if (_mediaPlayer.isPlaying()) {
				sop(m,"Stopping _mediaPlayer.");
			    _mediaPlayer.stop();
			}
			_mediaPlayer.release();
			_mediaPlayer = null;
		}

		// Relinquish audio focus to another app.  Ignore return code.
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audioManager.abandonAudioFocus(this);
		
		// Set state.
		_state = State.Stopped;
		_clicksWhilePreparing = 0;
		broadcastState();
		if (sendNotification) {
			notifyUser(Title.Stopped, getResources().getString(R.string.STR_SELECT_TO_RETURN));
		}

		sop(m,"Exit.");
	}
	
	/**
	 * Set volume soft.
	 */
	private void setVolumeSoft() {
		String m = "setVolumeSoft";
		if (State.Playing == _state && _mediaPlayer.isPlaying()) {
			_mediaPlayer.setVolume(0.1f, 0.1f);
			sop(m,"Set volume soft.");
			notifyUser(Title.Playing, getResources().getString(R.string.STR_PLAYING_SOFTLY));
		}
		else {
			sop(m,"Can't change volume. Not playing. state=" + getStateString());
		}
	}
	
	/**
	 * Set volume normal.
	 */
	private void setVolumeNormal() {
	    String m = "setVolumeNormal";	
		if (State.Playing == _state && _mediaPlayer.isPlaying()) {
			_mediaPlayer.setVolume(1.0f, 1.0f);
			sop(m,"Set volume normal.");
			notifyUser(Title.Playing, getResources().getString(R.string.STR_SELECT_TO_RETURN));
		}
		else {
			sop(m,"Can't change volume. Not playing. state=" + getStateString());
		}
	}
	
	/**
	 * Broadcasts the state of this player to the current MainActivity.
	 * Intended for the activity to update its button image.
	 */
	private void broadcastState() {
		String m = "broadcastState";
		sop(m,"Entry.");

		// Get the string for the current state.
		String state = getStateString();

		// Prepare an intent to send to the MainActivity.
		Intent intent = new Intent(MainActivity.ACTION_IMAGE);

		// AD 2018-0914 Added package name.
		intent.setPackage(this.getPackageName());
		Bundle bundle = new Bundle();
		bundle.putString(STATE_KEY, state);
		intent.putExtras(bundle);
		
		sop(m,"Broadcasting state=" + getStateString());
		sendBroadcast(intent);
		sop(m,"Exit. Called sendBroadcast(intent).");
	}

	/**
	 * Returns a string to be used as the Notification Context Title.
	 */
	private String getNotificationContentTitle(Title title) {
		String app_name = Constants.APP_NAME_VERBOSE + " ";
		if (Title.Stopped == title) {
			return app_name + getResources().getString(R.string.STR_TITLE_STOPPED);
		}
		else if (Title.Playing == title) {
			return app_name + getResources().getString(R.string.STR_TITLE_PLAYING);
		}
		else if (Title.Error == title) {
			return app_name + getResources().getString(R.string.STR_TITLE_ERROR);
		}
		else {
			throw new RuntimeException(appname + " Code Bug.  Add new state value to this method.");
		}
	}
	
	/**
	 * Generates a notifications to the user and sets 'foreground service' accordingly.
	 * AD 2018-0915 Rewritten for compatibility with newer Android APIs.
	 */
	private void notifyUser(Title title, String text) {
		String m = "notifyUser";
		sop(m,"===1840> Entry. title=" + title + " text=" + text);

		//-------------------------------
		// Prepare the notification
		//-------------------------------

		CharSequence contentTitle = getNotificationContentTitle(title);  //ok
		CharSequence contentText = text.subSequence(0, text.length());   //ok

		Intent notificationIntent = new Intent(this, MainActivity.class);  //ok?
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);  //ok?

		// Initialization
		/*if (null == _notification) {
			int appicon = Constants.IMG_ICON;
			long when = System.currentTimeMillis();
			_notification = new Notification(appicon, contentTitle, when);
		}*/
		if (null == _notificationManager) {
			_notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); //ok

			// For Android API 26 (Android 8 Oreo) and newer, create a NotificationChannel.
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				sop(m, "===> Handling Android 8, API 26, Oreo");
				CharSequence name = Constants.APP_NAME_MIXED;
				String description = Constants.APP_NAME_MIXED + " channel";
				int importance = NotificationManagerCompat.IMPORTANCE_LOW;
				NotificationChannel notChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
				notChannel.setDescription(description);
				notChannel.enableLights(true);
				notChannel.enableVibration(true);
				notChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
				notChannel.setShowBadge(false);
				_notificationManager.createNotificationChannel(notChannel);
				sop(m,"Created Notification Channel!");
			}
		}

		if (null == _notificationBuilder) {
			_notificationBuilder = new NotificationCompat.Builder(_applicationContext, NOTIFICATION_CHANNEL_ID);
			sop(m,"Created Notification Compat Builder.");
		}
		// Set the title, message, and action.
		// setLatestEventInfo is deprecated.  https://github.com/OneBusAway/onebusaway-android/issues/290
		//_notification.setLatestEventInfo(_applicationContext, contentTitle, contentText, pendingIntent);
		sop(m,"Setting title, message, etc in notification builder.");
		_notificationBuilder.setSmallIcon(Constants.IMG_ICON);
		_notificationBuilder.setContentTitle(contentTitle);
		_notificationBuilder.setContentText(contentText);
		_notificationBuilder.setContentIntent(pendingIntent);
		_notificationBuilder.setWhen(System.currentTimeMillis());

		sop(m,"Building notification...");
		_notification = _notificationBuilder.build();
		sop(m,"Built notification!");

		//-------------------------------
		// Dispatch the notification
		//-------------------------------

		// No notification in tray.  Background service.
		if (Title.Stopped == title) {
			// Stop foreground service.
			sop(m,"Title is stopped. Stopping foreground.");
			stopForeground(true);

			// Cancel the notification.
			sop(m,"Cancelling notification.");
			_notificationManager.cancel(NOTIFICATION_ID); //ok
		}
		// Persistent, non-clearable notification in tray.  Foreground service.
		else if (Title.Playing == title) {

			// Set this service to be a FOREground service, and post the notification.
			sop(m,"Title is playing. Calling startForeground().");
			startForeground(NOTIFICATION_ID, _notification);
			sop(m,"Called startForeground().");

			// startForeground  apparently marks the notification as FLAG_NO_CLEAR.  This is unnecessary.
			// _notification.flags |= Notification.FLAG_NO_CLEAR;
		}
		// Non-persistent, clearable notification in tray.  Background service.
		else if (Title.Error == title) {

			// Stop foreground service.
			sop(m,"Title is error. Stopping foreground.");
			stopForeground(true);

			// Enable the user to clear the notification.
			////TODO??? _notification.flags &= (~Notification.FLAG_NO_CLEAR);

			// Post or clear the notification.
			sop(m,"Clearing notification.");
			_notificationManager.notify(NOTIFICATION_ID, _notification);
		}
		else {
			throw new RuntimeException(appname + " Code bug.  Add new 'title' value to this method.");
		}
		sop(m,"Exit");
	}

	/**
	 * Generates a toast message to the user.
	 */
	private void postToast(String message) {
		String m = "postToast";
		sop(m,"Entry. message=" + message);
		String app_name = Constants.APP_NAME_MIXED;
        Toast.makeText(_applicationContext, app_name + ": " + message, Toast.LENGTH_LONG).show();
    }

	/**
	 * Returns a string representation of the player's current state.
	 */
	private String getStateString() {
		if (State.Playing == _state) { return STATE_PLAYING; }
		else if (State.Paused == _state) { return STATE_PAUSED; }
		else if (State.Preparing == _state) { return STATE_PREPARING; }
		else if (State.Stopped == _state) { return STATE_STOPPED; }
		throw new RuntimeException(appname + " Code bug.  Add new state value to getStateString().");
	}
	
	@Override
	public synchronized void onDestroy() {
		String m = "onDestroy";
		sop(m,"Entry.");
		
		// Release media player resources.
		stopMusic(false);
		
		sop(m,"Exit.");
	}

	/**
	 * Callback for the MediaPlayer.OnBufferingUpdateListener
	 */
	@Override
	public synchronized void onBufferingUpdate(MediaPlayer mp, int percent) {
		String m = "onBufferingUpdate";
		sop(m,"Buffer=" + percent + "%");
		// TODO: Figure out if we can do anything meaningful with this information.
		// This only seems to be called once, when the play button is pressed.
	}

	/**
	 * Returns true if Android grants permission to play music.
	 */
	private boolean getAudioFocus() {
		String m = "getAudioFocus";
		
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		boolean granted = (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == 
		    audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN));
		
		sop(m,"Requested audio focus from Android. Returning granted=" + granted);
        return granted;
	}
	
	/**
	 * Callback for the AudioManager Focus Change listener.
	 */
	@Override
	public synchronized void onAudioFocusChange(int focusChange) {
		String m = "onAudioFocusChange";
		
	    switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
        	sop(m,"Entry. AUDIOFOCUS_GAIN. Resuming music play.");
            resumeMusic();
        	setVolumeNormal();
            break;

        case AudioManager.AUDIOFOCUS_LOSS:
        	sop(m,"Entry. AUDIOFOCUS_LOSS. Andriod blocked focus. Stopping.");
			stopMusic(false);
			String message = getResources().getString(R.string.STR_AUDIO_FOCUS_DECLINED);
			notifyUser(Title.Error, message);
            postToast(message);
			sop(m,"Exit after blocked audio focus.");
            break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        	sop(m,"Entry. AUDIOFOCUS_LOSS_TRANSIENT. Pausing music.");
        	pauseMusic();
            break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        	sop(m,"Entry. AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK. Setting volume loud.");
        	setVolumeSoft();
            break;
	    }
    }
	
	/**
	 * For debug only
	 */
	public static String getMediaURLString() { return Constants.MEDIA_URL_STRING; }
}

