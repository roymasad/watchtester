package com.example.btscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/*
    App Overview:
    The purpose of this app is to test the resilience of background services in receiving web push notifications, bluetooth socket connections,
    REST and websockets or running any background code.
    It works correctly on android 9+ versions.
    The challenge is to get some of the options/features working on android Go versions that disable background notifications,
    services and activities when the phone enters deep sleep or doze mode (10 to 30mins).
    Android Go is usually installed on smart watches with low memory as it requires less than standard android.
    It is very aggressive in turning off background push notifications and services after a while, rendering realtime app communication problematic.

    App functionality:
    -Show you at the bottom if the android is normal or GO version
    -Start a foreground service in the background that runs code and listens to incoming Bluetooth 'hello' ping connections
    -Set up partial wake lock
    -Set up OneSignal push notifications from the foreground service
    -Scans for nearby bluetooth devices
    -Displays a list of paired + discovered bluetooth devices
    -Allows you to tap on any device on the list, then attempts to send that devices a 'hello' ping over bluetooth
    -Goes to the background when you press back instead of exiting
    -Shows a toast when a 'hello ping' is send and the app main activity is open or a push notification if the app is in the background

    Conclusion & Finding:
    Setting up a foreground service successfully continues working on android GO devices and it can receive bluetooth connection requests
    and run other code in the background like interacting with online servers (and databases).
    This was done and achieved though on a java native android app.

    Next Steps:
    -Normal web push notifications are still turned off after 30/40 minutes by android GO, to investigate unlikely fix (custom handler in the foreground service?)
    -Test REST and websocket/firebase online interactions from within the foreground service

    Potential Use Cases:
    Since the foreground services keep running on Android Go, you can monitor online API endpoints to listen to events over websockets to see when new 'event' has happened
    for example when a new request was added to a collection/database(push or poll) then trigger a local notification when the app is in the background to inform the user.
    OR: alternatively, the battery life seemingly good (12 to 14 hours is more than any 8 hour shift), just keep the screen on and dimmed which would keep the app awake without worrying about background doze.
    Finally, while sending BT pings now works to 'sleeping' devices running foreground services that listen, BT itself is not causing this success. It is the foreground service itself.

    General Notes:
    -When sending push notifications or setting up local push channels, always use the highest priority (HIGH or MAX)
    -Use foreground services instead of background services to have your code successfully run in the background on Android GO
    -Request all permissions needed and disable the back button from exiting the app by default (reroute it to send to background)
    -Normal push notifications wont work on android GO even with big apps like Telegram after the phone enters Doze mode
    -Scanning for bluetooth devices requires Location enabled with precise permission, plus enable BT scanning in options (if available)
    -Onesignal requires Firebase messaging, and Firebase requires Google play services to work (not all watches have Google Services and Store)
    -Test if partial wake locks are making any real difference here (in delaying time-to-doze and long term battery consumption)

    Observations:
    -App can run 12 to 14 hours with the screen ON and dimmed to 50% with local notification, bluetooth connectivity and a foreground service

    Required Phone Settings:
    -Battery optimizations turned off for the installed app
    -Enable location services globally and for the app
    -Enable bluetooth scanning in network setting (if option available)
    -Device needs to have Google Play Services/Play Store for the FCM push notifications to arrive (Direct Firebase or through One Signal)
    -App standby needs to be in Active Bucket (it is so by default)
    -App must be whitelisted from doze (check with adb dumpsys, should be good by default if you disabled battery optimizations)
    -In network settings or developer mode, turn off any network auto shutdown (also u can : adb shell settings put global wifi_sleep_policy 2) or 'get'
    -Remove any data restrictions on the app in settings if any are in options
    -Turn off any data saver general option
    -Disable any DND or power saving planes on the device

    Notes for porting to other tech stacks and publishing:
    -App is written in Native Java, so smaller memory footprint (40mb), more tweak control, less heat generation (due to gpu acceleration) than a webview cordova deprecated app
    -All the optimizations done like wakelocks and foreground services 'might' cause an approval problem if the app is published to the play store

    Tests that had no effect:
    -Disabled doze mode temporarily by using: adb shell dumpsys deviceidle disable (check also adb shell dumpsys deviceidle whitelist)
    -Tried to use Firebase push notifications directly with work manager to receive notifications in the background after doze on android GO (it uses a background service that stands no chance in android GO)

    Notes on Hardware:
    -Virtually all of the unbranded chinese watches sold are using Android GO with versions 8 or 9 (GO version can go up to version 13)
    -One can try to use a branded android Wear App to sideload an apk that can work and receive push notifications in the background, but they are more expensive and less configurable
    -Android GO is = "Android low memory mode". It is setup in /vendor/build.prop, ro.config.low_ram = 1 . You cant edit you need to be rooted or have the manufacturer edit the ROM for you and reflash it

    Notes of Troubleshooting:
    -Enable USB debug option (if a device doesnt have it that will be a problem)
    -Install the utility 'scrcpy' to conveniently connect from your terminal to the watches and use your mouse (precise easy remote-in)
    -Always check Logcat in android studio for error messages

    PS:
    -Remember to copy over your own google-services.json file
    -Remember to fill in your own OneSignal App ID.
    -You have to pair the Bluetooth devices you want to test inter Bluetooth communication with

 */


public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceListAdapter;
    private ListView deviceListView;

    private BluetoothService service;

    // Unique UUID for the BT service that both server and client must match
    String uuid = "8bf5fd6f-344e-4303-92dd-a7aee674ac86";

    private Handler handler = new Handler();

    // Declare socket as an instance variable
    private BluetoothSocket socket;

    private PowerManager.WakeLock wakeLock;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep the screen on, needs matching attribute in the layout xml
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get the current window attributes
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();

        // Set the screen brightness to 0.5f for 50% brightness
        layoutParams.screenBrightness = 0.5f;

        // Apply the new attributes to the window
        getWindow().setAttributes(layoutParams);


        // Link View and Adapter
        deviceListView = findViewById(R.id.deviceListView);
        deviceListAdapter = new ArrayAdapter<String>(this, R.layout.list_item_layout, android.R.id.text1);
        deviceListView.setAdapter(deviceListAdapter);

        // Get BT
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Enable BT if it's displayed
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, 1);
        }

        // Check for permissions on startup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 0);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.FOREGROUND_SERVICE}, 5);
        }

        // enable fine location permission (needed for nearby Bluetooth device scans on newer androids)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                case PackageManager.PERMISSION_DENIED:
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            3);
                    break;
            }
        }

        // Open settings if location is turned off
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isGpsEnabled) {
            startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 4);
        }


        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        // used for activity local broadcasts
        registerReceiver(receiver, filter);

        // used for receiving background broadcasts from service
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("com.example.btscanner.NEW_MESSAGE"));

        // Add event to scan button click
        Button scanButton = findViewById(R.id.scanButton);
        //scanButton.setOnClickListener(view -> startDiscovery());
        // Check if the device is running Android Go
        boolean isAndroidGo = isAndroidGoDevice();
        if (isAndroidGo) {
            scanButton.setText("Android GO.");
        } else {
            scanButton.setText("Android.");
        }

        // Add click event handler to the device list
        deviceListView.setOnItemClickListener(this::sendBTMessage);

        // Start the background foreground BluetoothService when your app starts
        Intent bluetoothServiceIntent = new Intent(this, BluetoothService.class);
        startService(bluetoothServiceIntent);

        // Bind to the service and set the foreground activity's context
        bindService(bluetoothServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);


        // List paired devices (add to discovered)
        @SuppressLint("MissingPermission") Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            deviceListAdapter.add(device.getName() + "\n" + device.getAddress());
        }

        // Auto start discovering BT unpaired devices
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        // Inside your activity's or service's onCreate or onStartCommand method
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BTS_SC_WakeLockTag");

        // Acquire the wake lock when you need to keep the CPU awake
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }


    }
    @SuppressLint("MissingPermission")
    private void sendBTMessage(AdapterView<?> adapterView, View view, int i, long l) {

        Log.i("BT_SC", "Sending");
        String deviceInfo = deviceListAdapter.getItem(i);
        String[] parts = deviceInfo.split("\n");
        String deviceName = parts[1]; // Extract the device name

        // IF The device is already paired, establish a Bluetooth socket connection (we are assuming they are paired for now)
        Toast.makeText(MainActivity.this, "Sending message to " + deviceName, Toast.LENGTH_SHORT).show();

        // Cancel discovery because it otherwise slows down the connection.
        bluetoothAdapter.cancelDiscovery();

        BluetoothDevice selectedDevice = bluetoothAdapter.getRemoteDevice(deviceName);

        if (selectedDevice != null) {
            // Now you have the selected BluetoothDevice object representing the remote device.
            // You can use this object to establish a connection and send messages to the device.

            // Create an RFCOMM Bluetooth socket
            socket = null;
            try {
                socket = selectedDevice.createRfcommSocketToServiceRecord(UUID.fromString(uuid)); // prepare to connect to BT 'server'

            } catch (IOException e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                try {
                    Log.e("BT_SC", "Error " + e.getMessage());
                    socket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                throw new RuntimeException(e);
            }

            // Connect to the Bluetooth socket
            try {
                socket.connect();   // Note this is blocking, if it cant connect it will hang up a few secs before failing

                // Use the Bluetooth socket to send the message to the selected device
                if (socket != null) {
                    try {
                        OutputStream outputStream = socket.getOutputStream();
                        @SuppressLint("MissingPermission") String messageToSend = "Hello from " + bluetoothAdapter.getName() + "\n";
                        byte[] bytesToSend = messageToSend.getBytes();
                        outputStream.write(bytesToSend);
                        outputStream.flush();
                        Log.i("BT_SC", "Sent : "+ messageToSend);


                        // Post a Runnable with a delay of 2000 milliseconds (1 second) to auto close (and give the stream time to flush)
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Perform the action you want to delay here
                                if (socket != null) {
                                    try {
                                        socket.close();
                                        Log.i("BT_SC", "Closing Socket after 2 seconds");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }, 2000);


                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("BT_SC", "Error " + e.getMessage());
                        e.printStackTrace();
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }

            } catch (IOException e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                try {
                    Log.e("BT_SC", "Error " + e.getMessage());
                    socket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                throw new RuntimeException(e);
            }


        } else {
            // Handle the case where the device with the specified address was not found.
            // You might want to display an error message or take appropriate action.

            Toast.makeText(MainActivity.this, "Error Sending", Toast.LENGTH_SHORT).show();
        }

    }

    // Create a BroadcastReceiver for BT ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Toast.makeText(context, "Received "+action, Toast.LENGTH_SHORT).show(); // Shows us something was received
            //Log.i("BT_SC", "Intent received: " + action);

            // This intent adds newly discovered BT devices to llist
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                // Add to Listview
                deviceListAdapter.add(deviceName + "\n" + deviceHardwareAddress);
            }

            // This intent shows any message receive from devices (will be sent by receiving background service)
            if ("com.example.btscanner.NEW_MESSAGE".equals(action)) {
                String message = intent.getStringExtra("message");
                // Update the UI with the received message (e.g., show a Toast)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);

        // Stop the BluetoothService when your app is destroyed
        Intent bluetoothServiceIntent = new Intent(this, BluetoothService.class);
        stopService(bluetoothServiceIntent);

        // Release the wake lock when you're done with it (e.g., in onDestroy or when your task is complete)
//        if (wakeLock.isHeld()) {
//            wakeLock.release();
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sendBroadcast(new Intent("com.example.btscanner.APP_FOREGROUND")); // Sync app state with background service
    }

    @Override
    protected void onPause() {
        super.onPause();
        sendBroadcast(new Intent("com.example.btscanner.APP_BACKGROUND"));
    }

    // Used for binding background service to this activity (service will know the 'this' context of the activity)
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            BluetoothService.LocalBinder localBinder = (BluetoothService.LocalBinder) binder;
            service = localBinder.getService();

            // Set the foreground activity's context in the service
            service.setForegroundActivityContext(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    private boolean isAndroidGoDevice() {
        // Check if the device is running Android Go by using the feature check
        return getPackageManager().hasSystemFeature("android.hardware.ram.low");
    }

    // important, send the app to the background when the user pressed 'back' instead of closing it by default
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
