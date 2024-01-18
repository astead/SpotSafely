package com.spotsafely;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;

public class myBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private MainActivity activity;


    public myBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, MainActivity activity) {
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //Log.d("ARG-TRACE","BroadcastReceiver: onReceive: ENTRY");

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: WIFI_P2P_STATE_CHANGED_ACTION.");
            // Check to see if Wi-Fi is enabled and notify appropriate activity

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct is enabled
                activity.setIsWifiP2pEnabled(true);
            } else {
                // Wi-Fi Direct is not enabled
                activity.setIsWifiP2pEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: WIFI_P2P_PEERS_CHANGED_ACTION.");
            // Call WifiP2pManager.requestPeers() to get a list of current peers
            if (manager != null) {
                if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermission();
                }
                Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: calling requestPeers.");
                manager.requestPeers(channel, activity.peerListListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: WIFI_P2P_CONNECTION_CHANGED_ACTION.");
            // Respond to new connection or disconnections
            WifiP2pInfo extra_info = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            Log.d("ARG-TRACE", "BroadcastReceiver: extra info: " + extra_info);

            // Connection state changed! We should probably do something about that.
            if (manager != null) {
                if (!extra_info.isGroupOwner) {
                    Log.d("ARG-TRACE", "          calling requestConnectionInfo for the client.");
                    manager.requestConnectionInfo(channel, activity.clientConnectionInfoListener);
                } else {
                    if (extra_info.groupFormed) {
                        Log.d("ARG-TRACE", "          calling requestConnectionInfo for the server.");
                        manager.requestConnectionInfo(channel, activity.serverConnectionInfoListener);
                    }
                }
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.");
            // Respond to this device's wifi state changing

            WifiP2pDevice extra_info = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            //Log.d("ARG-TRACE", "BroadcastReceiver: extra info: " + extra_info);
            //DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager()
            //       .findFragmentById(R.id.frag_list);
            //fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(
            //        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));

        } else {
            Log.d("ARG-TRACE", "BroadcastReceiver: onReceive: Something else: " + action);

        }
    }
}
