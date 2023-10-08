# watchtester
Testing Background App Notifications for Android Go Smartwatch 

Version 0.02

# App Overview:
The purpose of this app is to test the resilience of background services in receiving web push notifications, bluetooth socket connections,
REST and websockets or running any background code.
It works correctly on android 9+ versions.
The challenge is to get some of the options/features working on android Go versions that disable background notifications,
services and activities when the phone enters deep sleep or doze mode (10 to 30mins).
Android Go is usually installed on smart watches with low memory as it requires less than standard android.
It is very aggressive in turning off background push notifications and services after a while, rendering realtime app communication problematic.

# App functionality:
* Show you at the bottom if the android is normal or GO version
* Start a foreground service in the background that runs code and listens to incoming Bluetooth 'hello' ping connections
* Set up partial wake lock
* Set up OneSignal push notifications from the foreground service
* Scans for nearby bluetooth devices
* Displays a list of paired + discovered bluetooth devices
* Allows you to tap on any device on the list, then attempts to send that devices a 'hello' ping over bluetooth
* Goes to the background when you press back instead of exiting
* Shows a toast when a 'hello ping' is send and the app main activity is open or a push notification if the app is in the background
* Tapping on the bottom 'Android' bottom will write new entry in the firebase realtime database in the 'orders' reference, 
      this will be detected by a firebase childAdded event listener in the foreground service running in the background,
      and a toast or push notification will show the newly added firebase object on this app and any other device with the app installed on

# Conclusion & Finding:
What worked:
Setting up a foreground service successfully continues working on android GO devices and it can receive bluetooth connection requests
and run other code in the background like interacting with online servers (and databases).
This was done and achieved though on a java native android app.
Firebase real time event listeners running in the foreground service successfully continue to receive online updates after doze mode on Android Go
What didn't:
Normal web notifications from OneSignal and Firebase still wont be received when device is in doze mode on Android go, even if they were
started from the foreground service.

# Next Steps:
* Test more scenarios to receive online updates from within the foreground service

# Potential Use Cases:
Since the foreground services keep running on Android Go, you can monitor online API endpoints to listen to events over websockets to see when new 'event' has happened
for example when a new request was added to a collection/database(push or poll) then trigger a local notification when the app is in the background to inform the user.
OR: alternatively, the battery life seemingly good (12 to 14 hours is more than any 8 hour shift), just keep the screen on and dimmed which would keep the app awake without worrying about background doze.
Finally, while sending BT pings now works to 'sleeping' devices running foreground services that listen, BT itself is not causing this success. It is the foreground service itself.

# General Notes:
* When sending push notifications or setting up local push channels, always use the highest priority (HIGH or MAX)
* Use foreground services instead of background services to have your code successfully run in the background on Android GO
* Request all permissions needed and disable the back button from exiting the app by default (reroute it to send to background)
* Normal push notifications wont work on android GO even with big apps like Telegram after the phone enters Doze mode
* Scanning for bluetooth devices requires Location enabled with precise permission, plus enable BT scanning in options (if available)
* Onesignal requires Firebase messaging, and Firebase requires Google play services to work (not all watches have Google Services and Store)
* Test if partial wake locks are making any real difference here (in delaying time-to-doze and long term battery consumption)

# Observations:
* App can run 12 to 14 hours with the screen ON and dimmed to 50% with local notification, bluetooth connectivity and a foreground service

# Required Phone Settings:
* Battery optimizations turned off for the installed app
* Enable location services globally and for the app
* Enable bluetooth scanning in network setting (if option available)
* Device needs to have Google Play Services/Play Store for the FCM push notifications to arrive (Direct Firebase or through One Signal)
* App standby needs to be in Active Bucket (it is so by default)
* App must be whitelisted from doze (check with adb dumpsys, should be good by default if you disabled battery optimizations)
* In network settings or developer mode, turn off any network auto shutdown (also u can : adb shell settings put global wifi_sleep_policy 2) or 'get'
* Remove any data restrictions on the app in settings if any are in options
* Turn off any data saver general option
* Disable any DND or power saving plans on the device

# Notes for porting to other tech stacks and publishing:
* App is written in Native Java, so smaller memory footprint (40mb), more tweak control, less heat generation (due to gpu acceleration) than a webview cordova deprecated app
* All the optimizations done like wakelocks and foreground services 'might' cause an approval problem if the app is published to the play store

# Tests that had no effect:
* Disabled doze mode temporarily by using: adb shell dumpsys deviceidle disable (check also adb shell dumpsys deviceidle whitelist)
* Tried to use Firebase push notifications directly with work manager to receive notifications in the background after doze on android GO (it uses a background service that stands no chance in android GO)

# Notes on Hardware:
* Virtually all of the unbranded chinese watches sold are using Android GO with versions 8 or 9 (GO version can go up to version 13)
* One can try to use a branded android Wear App to sideload an apk that can work and receive push notifications in the background, but they are more expensive and less configurable
* Android GO is = "Android low memory mode". It is setup in /vendor/build.prop, ro.config.low_ram = 1 . You cant edit you need to be rooted or have the manufacturer edit the ROM for you and reflash it

# Notes of Troubleshooting:
* Enable USB debug option (if a device doesnt have it that will be a problem)
* Install the utility 'scrcpy' to conveniently connect from your terminal to the watches and use your mouse (precise easy remote-in)
* Always check Logcat in android studio for error messages

PS:
* Place your correct android app firebase google-services.json file in the /app folder (make sure realtime database is enabled)
* Remember to fill in your own OneSignal App ID.
* You have to pair the Bluetooth devices you want to test inter Bluetooth communication with
