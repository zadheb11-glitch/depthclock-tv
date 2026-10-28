# Depth Clock TV
Live clock behind mountains (depth effect) for Android TV. Works as an app and as a screensaver.

1. Upload this whole folder to a new GitHub repository.
2. Open the Actions tab, run "Build APK".
3. Download the artifact DepthClockTV-apk and install app-debug.apk on the TV.

Set as screensaver via adb:
adb shell settings put secure screensaver_components com.depthclock.tv/.ClockDream
