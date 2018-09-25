/**
 * Transient foreground class which fetches and displays
 * the name of the presently-playing song.
 *
 * Copyright bmir.org and shoutingfire.com 2012,2018
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/**
 * Issues a web request to the server to determine the current song.
 * Returns the current song to the specified listener.
 */
public class NowPlayingThread implements Runnable {

	/**
	 * Ensures only one of these threads is running at any one time.
	 */
	private static Object _mutex = new Object();
	
	/**
	 * Private error message return code. 
	 */
	private final static String RC_UNKNOWN_CURRENT_SONG = "RC_UNKNOWN_CURRENT_SONG";
	
	/**
	 * Logs messages to the console.
     * Enable/disable logging here when publishing to Android Market.
	 */
	private static final String _logTag = NowPlayingThread.class.getName().toString();
	private static void sop(String method, String message) {
		//Log.d(_logTag, method + ": " + message);
	}

	/**
	 * Reference to message strings.
	 */
	private Context _context = null;

	/**
	 * The activity to which this thread will post the current song.
	 */
	private Activity _activity = null;
	
	/**
	 * The current song.
	 */
	private String _song = null;

	/**
	 * Title for the message.
	 */
	private String _msgCurrentSong = null;
	
	/**
	 * Prevent rapid repeated queries.
     * Note: One instance of this object is re-used by Main Activity,
     * so this variable does need not be static.
     * TODO ANDY: Make sure this is true!
	 */
	private long _lastQueryTimestamp = 0;

	/**
	 * Constructor
	 */
	public NowPlayingThread(Context context, Activity activity) {
		_context = context;
		_activity = activity;
		_msgCurrentSong = Constants.APP_NAME_MIXED + ": ";
	}
	
	/**
	 * Truncates a long string to reasonable length for a Toast message.
	 */
	private static final int MAX_TOAST_CHARS = 98;
	private String truncateToastString(String str) {
		if (MAX_TOAST_CHARS < str.length()) {
			str = str.substring(0,MAX_TOAST_CHARS) + "...";
		}
		return str;
	}
	
    /**
     * Converts all HTML entity reference characters to ASCII.
     * Example:  Converts &quot; to "
     * Ref:  http://www.w3schools.com/tags/ref_entities.asp
     */
	private String decodeHtmlEntityReferences(String str) {
		String m = "decodeHtmlEntityReferences";
		// For debug:
		int ixAmpersand = str.indexOf("&");
	    int ixSemicolon = str.indexOf(";"); 
		if (-1 != ixAmpersand && -1 != ixSemicolon && ixAmpersand < ixSemicolon) {
			sop(m, "Decoding!");
		}

		str = str.replaceAll("&quot;", "\"");
		str = str.replaceAll("&apos;", "'");
		str = str.replaceAll("&amp;", "&");
		str = str.replaceAll("&lt;", "<");
		str = str.replaceAll("&gt;", ">");
		
	    return str;	
	}
	
    /**
     * Removes other nuisance junk from the current song.
     */
	private String removeNuisanceStrings(String str) {
		//String m = "removeNuisanceStrings";
		
		str = str.replaceAll(".mp3","");
		str = str.replaceAll(".Mp3","");
		str = str.replaceAll(".mP3","");
		str = str.replaceAll(".MP3","");
		
		if (str.endsWith(" - ")) {
		    str = str.substring(0, str.length() - 3);
		}
		if (str.endsWith(" -")) {
		    str = str.substring(0, str.length() - 2);
		}
		
		str = str.trim();
		
	    return str;	
	}
	
    /**
     * Extracts the 'current song' string from the HTML response string.
     * Expects the page to look like this:
     * 
     * <td>Current Song:</td>
     * <td class="streamdata">Goodiebag - hestedoktoren</td>
     * </tr>
     * 
     * Returns RC_UNKNOWN_CURRENT_SONG upon error.
     */
    private String getCurrentSongFromStatusPage(String page) {
        String m = "getCurrentSongFromStatusPage";
        sop(m,"Entry.");

        // Bozo error.
        if (null == page) {
            sop(m,"Early exit. Consumer error. page is null.");
            return RC_UNKNOWN_CURRENT_SONG;
        }

        // Find "Current Song"
        int ixCurrentSong = page.indexOf("Current Song");
        if (-1 == ixCurrentSong) {
            sop(m,"Early exit. Could not find 'Current Song' in response.");
            return RC_UNKNOWN_CURRENT_SONG;
        }

        // Find next 'td' tag.
        int ixTDStart = page.indexOf("<td", ixCurrentSong);
        if (-1 == ixTDStart) {
            sop(m,"Early exit. Could not find next '<td' in response.");
            return RC_UNKNOWN_CURRENT_SONG;
        }

        // Find closing of 'td' tag.
        int ixTDClose = page.indexOf(">", ixTDStart);
        if (-1 == ixTDClose) {
            sop(m,"Early exit. Could not find next '>' in response.");
            return RC_UNKNOWN_CURRENT_SONG;
        }

        // Find next 'end td' tag.
        int ixTDEnd = page.indexOf("</td", ixTDClose);
        if (-1 == ixTDEnd) {
            sop(m,"Early exit. Could not find next '</td' in response.");
            return RC_UNKNOWN_CURRENT_SONG;
        }

        String rc = page.substring(1 + ixTDClose, ixTDEnd);
                
        sop(m,"Exit. Returning rc=>>>" + rc + "<<<");
        return rc;
    }

    /**
     * Issues a web request to the server status page.
     * 
     * Returns RC_UNKNOWN_CURRENT_SONG upon error.
     */
    private String getServerStatusPage() {
        String m = "getServerStatusPage";
        String rc;
        BufferedReader br = null;
        sop(m,"Entry.");
        
        try {
            URL url = new URL(Constants.STATUS_URL_STRING);

            URLConnection uc = url.openConnection();
            br = new BufferedReader(new InputStreamReader(uc.getInputStream()));

            // Safety valve to constrain the following while loop.
            // The page was originally 81 lines long, so 810 should be plenty.
            int numLines = 0; 
            
            rc = "";
            String line;
            while ((null != (line = br.readLine())) && (810 > (numLines++))) {
                // sop(m,"line=" + line);
                rc += line;
            }
        }
        catch(Exception e) {
        	sop(m,"Caught e=" + e.getMessage());
        	rc = RC_UNKNOWN_CURRENT_SONG;
        }
        
        if (null != br) {
        	try { br.close(); } catch(Exception e) { ; }
        }

        sop(m,"Exit.");
        return rc;
    }
    
    /**
     * Determines the currently playing song and posts it to the activity.
     */
    private void getCurrentSong() {
    	String m = "getCurrentSong";
    	sop(m,"Entry.");
		String page = getServerStatusPage();
		if (null != page && 3 < page.length() && !RC_UNKNOWN_CURRENT_SONG.equals(page)) {
			String song = getCurrentSongFromStatusPage(page);
			if (null != song && 3 < song.length() && !RC_UNKNOWN_CURRENT_SONG.equals(song)) {
				// Convert escaped strings into readable characters.
				song = decodeHtmlEntityReferences(song);
				// Remove other objectionable strings.
				song = this.removeNuisanceStrings(song);
				// Do not display huge long messages as Toasts.
				song = truncateToastString(song);
				// Save it for easy access by the following runnable.  (Hack!)
				_song = song;
				_activity.runOnUiThread(new Runnable() {
				    public void run() {
				    	if (null != _song && 3 < _song.length()) {
				            Toast.makeText(_activity, _msgCurrentSong + _song, Toast.LENGTH_LONG).show();
				    	}
				    }
				});
			}
		}
		
    	sop(m,"Exit");
    }

	@Override
	public void run() {
		String m = "run";
		sop(m,"Entry.");   		
		synchronized(_mutex) {
			try {
				// Do not get the current song too frequently.
				if ((0 == _lastQueryTimestamp) || (501 < (System.currentTimeMillis() - _lastQueryTimestamp))) {
				    getCurrentSong();
				}
				else {
					sop(m,"Skipping repeated query.");
				}
				_lastQueryTimestamp = System.currentTimeMillis();
			}
			catch(Exception e) {
				sop(m,"Caught exception e=" + e.getMessage());
			}
		}
		sop(m,"Exit.");
	}

	/**
	 * Convenience method spawns a thread for this class and starts it.
	 */
	public void go() {
		String m = "go";
		sop(m,"Entry.");
    	new Thread(this).start();
    	sop(m,"Exit.");
	}
}
