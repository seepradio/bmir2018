/**
 * Copyright bmir.org and shoutingfire.com 2011,2012
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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import android.app.NotificationManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class Utilities {

	/**
	 * Logs messages to the console.
     * Enable/disable logging here when publishing to Android Market.
	 */
	private static final String _logTag = Utilities.class.getName().toString();
	private static void sop(String method, String message) {
		//Log.d(_logTag, method + ": " + message);
	}
	
	/** 
	 * Checks whether the network connection is available.
	 */
	public static boolean networkAvailable(Context context) {
		String m = "networkAvailable";
		boolean rc = false;
		sop(m,"Entry.");
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		sop(m,"connectivityManager=" + connectivityManager);
		if (null != connectivityManager) {
			NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
			sop(m,"networkInfo=" + networkInfo);
			if (null != networkInfo) {
				rc = networkInfo.isConnected();
			}
		}
		sop(m,"Exit. rc=" + rc);
		return rc;
	}
	
	/** 
	 * Checks whether a server is pingable on the network.
	 * TODO: Suppress caching.
	 */
	public static boolean ipAvailable(String hostname) {
		String m = "ipAvailable";
		int timeout = 3000; // ms
		try {
			//java.security.Security.setProperty("networkaddress.cache.ttl", "0");
			InetAddress.getByName(hostname).isReachable(timeout);
			Log.e(m,"Hostname is pingable. hostname=" + hostname);
			return true;
		} catch (UnknownHostException e) {
			Log.e(m,"Hostname is unknown. hostname=" + hostname);
			return false;
		} catch (IOException e) {
			Log.e(m,"Hostname is not pingable. hostname=" + hostname);
			return false;
		}
	}
	
	/**
	 * Checks whether a service is reachable via 'HTTP HEAD' request.
	 */
	public static boolean urlAvailable(String url) {
		String m = "urlAvailable(String url)";
		try {
		    return urlAvailable(new URL(url));
		}
		catch(MalformedURLException e) {
		    Log.e(m,"ARRGH: Caught MalformedURLException. Returning false. url=" + url + " e=" + e.getMessage());
		    return false;
		}
	}
	
	/**
	 * Checks whether a service is reachable via 'HTTP HEAD' request.
	 * TODO: Suppress caching!
	 */
	public static boolean urlAvailable(URL url) {
		String m = "urlAvailable(URL url)";
		sop(m,"url=" + url);
		try {
			URLConnection urlConnection = url.openConnection();
			HttpURLConnection httpURLConnection = (HttpURLConnection)urlConnection;
		    httpURLConnection.setRequestMethod("HEAD");
		    int responseCode = httpURLConnection.getResponseCode();
		    sop(m,"responseCode=" + responseCode);
		    if (HttpURLConnection.HTTP_OK == responseCode) {
			    sop(m,"URL is reachable. Returning true. url=" + url);
		    	return true;
		    }
		}
		catch(Exception e) { 
			Log.e(m,"Caught exception issuing HEAD request to URL. url=" + url + " e=" + e.getMessage()); 
		}
		sop(m,"Exit. Returning false.");
		return false;
	}
	
	/**
	 * Removes a notification to the user.
	 */
	public static void cancelNotification(Context context, int id) {
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(context.NOTIFICATION_SERVICE);
		notificationManager.cancel(id);  
	}
	
	/**
	 * Returns a human-readable string for Android Media Player error contstants.
	 */
	public static String getMediaPlayerErrorString(int i) {
		switch(i) {
		case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK: return "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK(" + MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK + ")";
		case MediaPlayer.MEDIA_ERROR_SERVER_DIED: return "MEDIA_ERROR_SERVER_DIED(" + MediaPlayer.MEDIA_ERROR_SERVER_DIED + ")";
		case MediaPlayer.MEDIA_ERROR_UNKNOWN: return "MEDIA_ERROR_UNKNOWN(" + MediaPlayer.MEDIA_ERROR_UNKNOWN + ")";
		}
		return "Unknown(" + i + ")";
	}

	/**
	 * Returns a human-readable string for Android Media Player info contstants.
	 */
	public static String getMediaPlayerInfoString(int i) {
		switch(i) {
		case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING: return "MEDIA_INFO_BAD_INTERLEAVING(" + MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING + ")";
		case MediaPlayer.MEDIA_INFO_METADATA_UPDATE: return "MEDIA_INFO_METADATA_UPDATE(" + MediaPlayer.MEDIA_INFO_METADATA_UPDATE + ")";
		case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE: return "MEDIA_INFO_NOT_SEEKABLE(" + MediaPlayer.MEDIA_INFO_NOT_SEEKABLE + ")";
		case MediaPlayer.MEDIA_INFO_UNKNOWN: return "MEDIA_INFO_UNKNOWN(" + MediaPlayer.MEDIA_INFO_UNKNOWN + ")";
		case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING: return "MEDIA_INFO_VIDEO_TRACK_LAGGING(" + MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING + ")";
		}
		return "Unknown(" + i + ")";
	}
}
