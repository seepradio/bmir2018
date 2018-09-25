/**
 * Defines all app and music stream specific values.
 *
 * Copyright bmir.org and shoutingfire.com 2018
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

public class Constants {

    // This app.
    public static final String PACKAGE_NAME = "org.bmir.mobile.android.player";
    public static final String APP_NAME_LOWER = "shoutingfire";
    public static final String APP_NAME_MIXED = "ShoutingFire";
    public static final String APP_NAME_VERBOSE = "ShoutingFire (formerly BMIR)";
    public static final String APP_NAME_UPPER = "SHOUTINGFIRE";
    public static final int NOTIFICATION_ID = 392919475;
    public static final String NOTIFICATION_CHANNEL_ID = "shoutingfirenotification";

    // Target stream.
    public static final String MEDIA_HOSTNAME = "shoutingfire-ice.streamguys1.com";
    public static final String MEDIA_URL_STRING = "https://" + MEDIA_HOSTNAME + ":80/live";

    // For debug only.
    //public final static String MEDIA_HOSTNAME = "pureradio.eu";
    //public final static String MEDIA_URL_STRING = "http://pureradio.eu:8000/low";

    // For the 'Now Playing' feature.
    public static final String STATUS_URL_STRING = "http://" + MEDIA_HOSTNAME + "/";

    // Image references.
    public static final int IMG_ICON = R.drawable.shoutingfireicon;
    public static final int IMG_DOTS = R.drawable.shoutingfiredots;
    public static final int IMG_PLAY = R.drawable.shoutingfireplay;
    public static final int IMG_STOP = R.drawable.shoutingfirestop;
}
