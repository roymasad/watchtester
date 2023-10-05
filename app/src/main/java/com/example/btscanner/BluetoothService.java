package com.example.btscanner;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.FirebaseApp;

public class BluetoothService extends Service {

    private Context foregroundActivityContext;
    String uuid = "8bf5fd6f-344e-4303-92dd-a7aee674ac86";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private boolean isAppForeground = true;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "BT_Scan";

    private Handler handler = new Handler();

    private InputStream inputStream;

    private byte[] buffer;

    // Declare a WakeLock variable at the class level
    private WakeLock wakeLock;

    // TODO put your own OneSignal App ID here
    private static final String ONESIGNAL_APP_ID = "";

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Bluetooth here
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

       // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.btscanner.APP_FOREGROUND");
        filter.addAction("com.example.btscanner.APP_BACKGROUND");
        registerReceiver(appStateReceiver, filter);

        // Inside your activity's or service's onCreate or onStartCommand method
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BTS_SC_WakeLockTag");

        // Acquire the wake lock when you need to keep the CPU awake
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        // Verbose Logging set to help debug issues, remove before releasing your app.
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize Firebase
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        DatabaseReference databaseReference = firebaseDatabase.getReference("orders");

        // listen to event changes in the service for the 'orders' reference in the firebase realtime database
        ChildEventListener ordersListener = new ChildEventListener() {

            private boolean firstLoad = true; // skip first load existing data

            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {

                if (firstLoad) {

                    firstLoad = false;
                    return;
                }

                String field1 = (String) dataSnapshot.child("field1").getValue();

                Log.d("BT_SC", "field1: " + field1);

                showMessage("Firebase new: " + field1);

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors
            }
        };

        // Add the ChildEventListener to the "orders" reference, load only last entry (skip existing data)
        databaseReference.limitToLast(1).addChildEventListener(ordersListener);


    }



    // Method to set the foreground activity's context
    public void setForegroundActivityContext(Context context) {
        Log.i("BT_SC", "Setting Service Context ");
        Log.i("BT_SC", context.toString());

        foregroundActivityContext = context;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Configure the service as a foreground service here
        createNotificationChannel();
        NotificationCompat.Builder builder = createNotification();
        Notification notification = builder.build();

        // Configure the service as a foreground service here
        startForeground(NOTIFICATION_ID, notification);

        // Set up Bluetooth socket and listening logic here
        listenForBluetoothMessages();

        return START_STICKY;
    }


    private void createNotificationChannel() {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Service",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

    }

    @SuppressLint("MissingPermission")
    private void listenForBluetoothMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("BT_SC", "Listening");
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BTSCAN", UUID.fromString(uuid));

                    socket = serverSocket.accept();
                    Log.i("BT_SC", "Got BT Request");

                    // Get the input stream from the Bluetooth socket
                    inputStream = socket.getInputStream();

                    buffer = new byte[1024];
                    int bytes;

                    // The loop to continuously read messages
                    while (true) {

                        // Read bytes from the input stream
                        bytes = inputStream.read(buffer);

                        if (bytes != -1) {
                            // Convert the received bytes to a string message
                            String message = new String(buffer, 0, bytes);
                            showMessage(message);
                            Log.i("BT_SC", "Msg : " + message);

                            // reset socket
                            socket.close();
                            //serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BTSCAN", UUID.fromString(uuid));
                            socket = serverSocket.accept();
                            inputStream = socket.getInputStream();
                            buffer = new byte[1024];

                        }

                    }
                } catch (Exception e) {
                    Log.e("BT_SC", "Error " + e.getMessage());
                    e.printStackTrace();
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }).start();
    }


    @SuppressLint("MissingPermission")
    private void showMessage(String message) {
        Log.i("BT_SC", "Message in foreground? " + isAppForeground);
        if (isAppForeground) {
            // Display a toast when the app is in the foreground

            Intent intent = new Intent("com.example.btscanner.NEW_MESSAGE");
            intent.putExtra("message", message);
            // you could simply use 'this' here also without adding extra code to calculate the foreground context
            LocalBroadcastManager.getInstance(foregroundActivityContext).sendBroadcast(intent);
            Log.i("BT_SC", "Broadcast from background " + "com.example.btscanner.NEW_MESSAGE");
        } else {
            // Create a notification when the app is in the background
            NotificationCompat.Builder builder = createNotification()
                    .setContentText(message);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private NotificationCompat.Builder createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Bluetooth Service")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true);

    }

    private final BroadcastReceiver appStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.btscanner.APP_FOREGROUND".equals(intent.getAction())) {
                isAppForeground = true;
                Log.i("BT_SC", "App in Foreground");
            } else if ("com.example.btscanner.APP_BACKGROUND".equals(intent.getAction())) {
                isAppForeground = false;
                Log.i("BT_SC", "App in Background");
            }
        }
    };

    @Override
    public void onDestroy() {
        // Clean up resources, including closing the Bluetooth socket
        try {
            if (socket != null) {
                socket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onDestroy();
        unregisterReceiver(appStateReceiver);

        // Release the wake lock when you're done with it (e.g., in onDestroy or when your task is complete)
//        if (wakeLock.isHeld()) {
//            wakeLock.release();
//        }
    }
    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    private final IBinder localBinder = new LocalBinder();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }


}
