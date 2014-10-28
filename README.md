YouTube WatchMe for Android
===========

The code is a reference implementation for an Android OS application that creates a YouTube Live Streaming event and streams into that event.

For more information, you can read the [Youtube API blog post](http://apiblog.youtube.com/2013/08/heres-my-playlist-so-submit-video-maybe.html).

This application utilizes [YouTube Data API v3](https://developers.google.com/youtube/v3/) , [YouTube Live Streaming API](https://developers.google.com/youtube/v3/live/), [Google Play Services](https://developer.android.com/google/play-services/index.html) and [Plus API](https://developers.google.com/+/mobile/android/Google).

To use this application,

1) In your [Google Developers Console](https://console.developers.google.com),
- Enable the YouTube Data API v3 and Google+ API.
- Create a client ID for Android, using your SHA1 and package name.
2) Include cross-platform compiled streaming libraries.
Either
- libffmpeg.so under src/main/jniLibs/armeabi,
or
- another streaming library with modifying VideoStreamingInterface

![alt tag](http://i59.tinypic.com/e8spqu.png)

![alt tag](http://i61.tinypic.com/16behq1.png)
