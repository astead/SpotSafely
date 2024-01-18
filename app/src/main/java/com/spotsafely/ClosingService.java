package com.spotsafely;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ClosingService extends Service {
    public ClosingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Handle application closing
        onDestroy();

        // Destroy the service
        stopSelf();
    }
}