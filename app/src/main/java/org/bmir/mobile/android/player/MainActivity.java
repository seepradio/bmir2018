/**
 * Transient foreground class which starts/stops/queries the player.
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
////import android.media.AudioAttributes.Builder;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.security.NetworkSecurityPolicy;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

public class MainActivity extends Activity
implements MediaPlayer.OnPreparedListener,
		MediaPlayer.OnBufferingUpdateListener,
		MediaPlayer.OnInfoListener,
		MediaPlayer.OnErrorListener,
		MediaPlayer.OnCompletionListener
{

	/**
	 * Commands to this service.
	 * Note: This must match definitions in AndroidManifest.xml
	 */
	public static final String ACTION_IMAGE = Constants.PACKAGE_NAME + ".mainactivity.action.IMAGE";

	/**
	 * Logs messages to the console.
	 * Enable/disable logging here when publishing to Android Market.
	 */
	private static final String _logTag = MainActivity.class.getName().toString();
	private static void sop(String method, String message) {
		//Log.d(_logTag, method + ": " + message);
	}

	/**
	 * A thread object to query the current song.
	 */
	NowPlayingThread _nowPlayingThread = null;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    String m = "onCreate";
	    super.onCreate(savedInstanceState);
	    sop(m,"Entry.");

	    // Paint the screen.
	    setContentView(R.layout.main);

	    // Create a thread object to query the current song.
	    // AD 2018-0915 Suppress this since neither stream is sending the info.
	    // _nowPlayingThread = new NowPlayingThread(this, this);

	    //sop(m,"checking isCleartextTrafficPermitted() for " + Constants.MEDIA_HOSTNAME);
	    //boolean permitted = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(Constants.MEDIA_HOSTNAME);
	    //sop(m,"permitted=" + permitted);

	    sop(m,"Exit.");
	}

	/**
	 * Handles onClick for the play/stop ImageButton.
	 */
	public void handleButton(View view) {
	 	String m = "handleButton";
	 	sop(m,"Entry. Calling MediaPlayerService...");

		boolean productionMode = true;

	 	if (productionMode) {
	 	 	// Inform the player service that the user clicked the button.
	 	 	// Note: If the service has already been started, the running service receives this intent.
	 	 	// AD 2018-0914 Updated for Android API 28.
			Intent intent = new Intent(PlayerService.ACTION_BUTTON);
			intent.setPackage(this.getPackageName());
			startService(intent);

	 	}
	 	else {
	 		// Start the music stream inline.  For debug purposes only.
	 		debugStartMusic();
	 	}

	 	sop(m,"Exit");
	}

	@Override
	protected void onStop() {
		String m = "onStop";
		sop(m,"Entry");
		super.onStop();
		sop(m,"Exit");
	}

	@Override
	protected void onDestroy() {
		String m = "onDestroy";
		sop(m,"Entry");
		super.onDestroy();
		sop(m,"Exit");
	}

	/**
	 * This broadcast receiver listens for state changes from the player service
	 * and changes the image on the button accordingly.
	 */
	private BroadcastReceiver _mainActivityBroadcastReceiver =
        	new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String m = "BroadcastReceiver/onReceive";
				sop(m,"Entry.");

				// 2011-1213 Tolerate null intent.  Added prophylactically, not actually reported by a user.
				if (null == intent) {
					sop(m,"Early exit. intent is unexpectedly null. Nothing to do.");
					return;
				}

				// Access the play/stop button.
				ImageButton imageButton = (ImageButton)findViewById(R.id.playStopButton);

				// Change the image corresponding to the state of the player service.
				String state = intent.getExtras().getString(PlayerService.STATE_KEY);
				if (PlayerService.STATE_PLAYING.equals(state) ||
					PlayerService.STATE_PAUSED.equals(state)) {
					sop(m,"Setting button image to 'stop'.");
					imageButton.setImageResource(Constants.IMG_STOP);
					if (null != _nowPlayingThread) {
					    sop(m,"Starting nowPlayingThread.");
			            _nowPlayingThread.go();
					}
				}
				else if (PlayerService.STATE_PREPARING.equals(state)) {
					sop(m,"Setting button image to 'dots'.");
					imageButton.setImageResource(Constants.IMG_DOTS);
				}
				else if (PlayerService.STATE_STOPPED.equals(state)) {
					sop(m,"Setting button image to 'play'.");
					imageButton.setImageResource(Constants.IMG_PLAY);
				}
				else {
					sop(m, "ERROR: Unrecognized state value: " + state);
				}
				sop(m,"Exit.");
			}
		};

	/**
	 * Registers the broadcastReceiver to receive state changes from the running service when this activity activates.
	 */
	public void onResume() {
		String m = "onResume";
		sop(m,"Entry.");
		super.onResume();

		sop(m,"Enabling the service to notify this application of state changes.");
		registerReceiver(_mainActivityBroadcastReceiver, new IntentFilter(ACTION_IMAGE));

		sop(m,"Requesting status from the service in order to update the MainActivity button image.");
 	 	// Note: If the service has already been started, the running service receives this intent.
		// AD 2018-0914 Updated for Android API 28.
		Intent intent = new Intent(PlayerService.ACTION_STATUS);
		intent.setPackage(this.getPackageName());
		startService(intent);

		sop(m,"Exit.");
	}

	/**
	 * Unregisters the broadcastReceiver from receiving state changes from the running service when this activity deactivates.
	 */
	public void onPause() {
		String m = "onPause";
		super.onPause();

		sop(m,"Disabling the service from notifying this application of state changes.");
		unregisterReceiver(_mainActivityBroadcastReceiver);
	}

	// For debug only
	public void onPrepared(MediaPlayer mp) {
		String m = "onPrepared";
		sop(m,"Prepared!!");
	}

	// For debug only
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		String m = "onBufferingUpdate";
		sop(m,"percent=" + percent);
	}

	// For debug only
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		String m = "onInfo";
		sop(m,"what=" + what + " extra=" + extra);
		return false;
	}

	// For debug only
	public boolean onError(MediaPlayer mp, int what, int extra) {
		String m = "onError";
		sop(m,"what=" + what + " extra=" + extra);
		return false;
	}

	// For debug only
	public void onCompletion(MediaPlayer mp) {
		String m = "onCompletion";
		sop(m,"Completed!!");
	}

	/**
	 * Simple synchronous example, for debug only.  Not for production use.
	 * From http://developer.android.com/guide/topics/media/mediaplayer.html
	 * To stop, go to Settings-> Applications-> Manage Applications-> appname-> Stop application.
	 */
	private void debugStartMusic() {
		String m = "debugStartMusic";
		sop(m,"Entry.");

		String url = PlayerService.getMediaURLString();

		sop(m,"checking isCleartextTrafficPermitted() for " + Constants.MEDIA_HOSTNAME);
		boolean permitted = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(Constants.MEDIA_HOSTNAME);
		sop(m,"permitted=" + permitted);

		MediaPlayer mediaPlayer = new MediaPlayer();
		// For Android API 26 (Android 8 Oreo) and newer, specify AudioAttributes.
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			sop(m,"Setting audio attributes for Android API 26 and later.");
			// Alternative: Review AudioAttributesCompat.Builder() in uamp.MusicSerice.kt
			AudioAttributes.Builder builder = new AudioAttributes.Builder();
			builder.setUsage(AudioAttributes.USAGE_MEDIA);
			builder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
			AudioAttributes attributes = builder.build();
			mediaPlayer.setAudioAttributes(attributes);
			sop(m,"Set audio attributes.");
		}
		else {
			sop(m,"Setting audio stream type for older Android APIs before 26.");
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			sop(m,"Set audio stream type.");
		}
		try {
			sop(m,"Setting listeners for debug.");
			mediaPlayer.setOnPreparedListener(this);
			mediaPlayer.setOnBufferingUpdateListener(this);
			mediaPlayer.setOnInfoListener(this);
			mediaPlayer.setOnErrorListener(this);
			mediaPlayer.setOnCompletionListener(this);

			sop(m,"Setting data source: " + url);
			mediaPlayer.setDataSource(url);
			sop(m,"Calling prepare().");
			mediaPlayer.prepare();
			sop(m,"Called prepare().");
		}
		catch(Exception e) {
			sop(m,"ERROR: Caught expection e=" + e.getMessage());
		}
		sop(m,"Calling start().");
		mediaPlayer.start();

		sop(m,"Exit. Called start().");
    }
}
