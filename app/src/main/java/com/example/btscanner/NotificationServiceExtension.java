package com.example.btscanner;

import android.util.Log;

import com.onesignal.notifications.IActionButton;
import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;


public class NotificationServiceExtension implements INotificationServiceExtension {

    @Override
    public void onNotificationReceived(INotificationReceivedEvent event) {
        Log.v("BT_SC", "IRemoteNotificationReceivedHandler fired" + " with INotificationReceivedEvent: " + event.toString());

        IDisplayableMutableNotification notification = event.getNotification();

        if (notification.getActionButtons() != null) {
            for (IActionButton button : notification.getActionButtons()) {
                Log.v("BT_SC", "ActionButton: " + button.toString());
            }
        }

    }
}