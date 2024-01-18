package com.spotsafely;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.EthernetNetworkSpecifier;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;

    Button simpleButton, btnStart, btnJoin;
    TextView txtInfo;
    ListView list;

    String shutdown_signal = "$$SHUTDOWN_MY_SOCKET$$";
    WifiManager wifiManager;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    myBroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<>();
    List<WifiP2pDevice> connectedDevices = new ArrayList<>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;
    String owner_name;

    int groupOwnerIntent = 0;
    boolean host_started = false;
    boolean client_started = false;
    boolean group_started = false;
    boolean is_connected = false;
    boolean trying_to_connect = false;

    float volume_level = 0;

    String myP2pName = "";
    boolean sentName = false;

    private String send_message = "0";

    ThreadHandler serverClass;
    ClientClass clientClass;

    boolean start_driving;
    boolean in_alarm;

    private MediaPlayer mediaPlayer;

    /*
    TODO:
     - Better looking app:
        - re-do buttons so they are drawn the same as the txtinfo?
     - Functionality:
         - client: lost focus while holding: should show unsafe?
    */

    static class ConnectedSpotter {
        protected String name;
        protected String socket_address;
        protected boolean status;
        protected Long last_comm;

        public ConnectedSpotter(String name) {
            this.name = name;
            this.status = false;
            this.last_comm = System.currentTimeMillis();
        }

        public void set_socketAddress(String socket_address) {
            this.socket_address = socket_address;
            this.last_comm = System.currentTimeMillis();
        }

        public void set_code(boolean status) {
            this.status = status;
            this.last_comm = System.currentTimeMillis();
        }
    }

    List<ConnectedSpotter> connectedSpotters = new ArrayList<>();


    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialWork();
        exqListener();

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            Log.d("ARG-TRACE", "onCreate: WiFi it off, trying to turn it on.");
            boolean ret = wifiManager.setWifiEnabled(true);
            if (!ret) {
                txtInfo.setText(R.string.wifi_off);
                btnJoin.setEnabled(false);
                btnJoin.setVisibility(View.GONE);
                btnStart.setEnabled(false);
                btnStart.setVisibility(View.GONE);
            }
        } else {
            Log.d("ARG-TRACE", "onCreate: WiFi it on.");

        }
    }

    private void set_safe(boolean is_safe) {
        Log.d("ARG-TRACE", "Set safe/unsafe spotter background");
        if (is_safe) {
            simpleButton.setBackgroundColor(Color.argb(255,84,130,53));
        } else {
            simpleButton.setBackgroundColor(Color.BLACK);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void exqListener() {
        /*
        TODO: Need to handle losing window focus and make sure we treat that like
         letting go of the button.
        simpleButton.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View view, @NonNull WindowInsets windowInsets) {

                return null;
            }
        });
         */
        simpleButton.setOnTouchListener((view, motionEvent) -> {
            if (client_started && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_DOWN: about to call executor.");
                send_message = "1";

                runOnUiThread(() -> set_safe(true));

                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    while (send_message.equals("1") && clientClass != null && client_started) {
                        if (!sentName) {
                            if (!myP2pName.equals("")) {
                                // Try to send our initial device name to connect name and IP
                                Log.d("ARG-TRACE", "          sending device name: " + myP2pName);
                                clientClass.write(myP2pName.getBytes());
                                sentName = true;
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                Log.d("ARG-TRACE", "          can't send device name because it is null");
                            }
                        }

                        if (clientClass != null) {
                            clientClass.write(send_message.getBytes());
                        } else {
                            Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_DOWN: can't send because clientClass is null.");
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.d("ARG-TRACE", "Exception while sleeping: " + e.getMessage());
                        }
                    }
                });


            }
            if (client_started && motionEvent.getAction() == MotionEvent.ACTION_UP) {
                Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_UP: now we are unsafe.");
                send_message = "0";

                runOnUiThread(() -> set_safe(false));

                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    if (!sentName && myP2pName.equals("")) {

                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                            requestPermission();
                        }
                        manager.requestDeviceInfo(channel, wifiP2pDevice -> {
                            if (wifiP2pDevice != null) {
                                myP2pName = wifiP2pDevice.deviceName;
                            }
                        });
                    }
                    if (!sentName && !myP2pName.equals("") && clientClass != null) {
                        // Before sending an unsafe signal, let's again make sure to send the initial device name to connect name and IP
                        Log.d("ARG-TRACE", "          sending device name: " + myP2pName);
                        clientClass.write(myP2pName.getBytes());
                        sentName = true;
                    } else {
                        if (!sentName && myP2pName.equals("")) {
                            Log.d("ARG-TRACE", "          can't send Name because it is null");
                        }
                        if (!sentName && !myP2pName.equals("") && clientClass == null) {
                            Log.d("ARG-TRACE", "          can't send Name clientClass is null");
                        }
                    }

                    if (clientClass != null) {
                        Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_UP: calling client.write.");
                        clientClass.write(send_message.getBytes());
                    } else {
                        Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_UP: can't send because clientClass is null.");
                    }
                });
            }
            if ((host_started || in_alarm) && motionEvent.getAction() == MotionEvent.ACTION_UP) {
                Log.d("ARG-TRACE", "simpleButton:OnTouch:ACTION_UP: host_started or in_alarm.");
                if (in_alarm) {
                    Log.d("ARG-TRACE", "          was in alarm, calling stop alarm.");
                    stopAlarmSound();
                } else {
                    if (!start_driving) {
                        Log.d("ARG-TRACE", "          was not driving, starting to drive.");
                        start_driving = true;
                        simpleButton.setText(R.string.stop_driving);

                        // Make sure the screen doesn't turn off
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                        // We need to start monitoring our list of connected spotters
                        ExecutorService executor = Executors.newSingleThreadExecutor();

                        executor.execute(() -> {
                            while (start_driving && !in_alarm) {
                                try {

                                    for (ConnectedSpotter tmp : connectedSpotters) {
                                        if (!tmp.status || tmp.last_comm < System.currentTimeMillis() - 1000) {
                                            playAlarmSound();

                                            runOnUiThread(this::update_listView_colors);
                                            break;
                                        }
                                    }

                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });

                    } else {
                        Log.d("ARG-TRACE", "          was driving, stopping driving.");
                        start_driving = false;
                        simpleButton.setText(R.string.start_driving);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            }
            return true;
        });

        btnStart.setOnClickListener(view -> {
            Log.d("ARG-TRACE", "btnStart:OnClick");

            if (!host_started) {
                Log.d("ARG-TRACE", "          Host was not started");
                if (!group_started) {
                    Log.d("ARG-TRACE", "          Group was not started");
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                        requestPermission();
                    }

                    groupOwnerIntent = 15;

                    Log.d("ARG-TRACE", "          Calling createGroup");
                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("ARG-TRACE", "          createGroup:onSuccess: we started the group.");

                            host_started = true;
                            group_started = true;
                            btnStart.setText(R.string.stop);
                            txtInfo.setText(R.string.spotters_are_joining);
                            btnJoin.setEnabled(false);
                            btnJoin.setVisibility(View.GONE);

                            serverClass = new ThreadHandler();
                            serverClass.start();

                            simpleButton.setText(R.string.start_driving);
                            simpleButton.setEnabled(true);
                            simpleButton.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onFailure(int i) {
                            Log.d("ARG-TRACE", "          createGroup:onFailure: something went wrong:" + i);
                            if (i == 0) {
                                requestPermission();
                            }
                            if (i == 2) {
                                txtInfo.setText(R.string.network_is_busy_re_start_wifi);
                            }
                        }
                    });
                }
            } else {
                Log.d("ARG-TRACE", "          Host was started");
                shutdown_host();
            }
        });

        btnJoin.setOnClickListener(view -> {
            Log.d("ARG-TRACE", "btnJoin:OnClick: ENTRY");
            if (!client_started) {
                Log.d("ARG-TRACE", "          client not started");
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission();
                }

                groupOwnerIntent = 0;

                client_started = true;
                btnJoin.setText(R.string.cancel);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    Log.d("ARG-TRACE", "          client_started:" + client_started);
                    Log.d("ARG-TRACE", "          trying_to_connect:" + trying_to_connect);
                    Log.d("ARG-TRACE", "          is_connected:" + is_connected);

                    while (client_started && !trying_to_connect && !is_connected) {
                        Log.d("ARG-TRACE", "          calling discoverPeers");
                        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("ARG-TRACE", "          discoverPeers:onSuccess: did we finish finding peers?.");
                                txtInfo.setText(R.string.select_the_driver_s_phone);
                                btnStart.setEnabled(false);
                                btnStart.setVisibility(View.GONE);
                            }

                            @Override
                            public void onFailure(int i) {
                                Log.d("ARG-TRACE", "          discoverPeers:onFailure: " + i);
                                if (i == 0) {
                                    requestPermission();
                                }
                            }
                        });

                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });


            } else {
                Log.d("ARG-TRACE", "          client is already started");
                shutdown_client();
            }
        });

        list.setOnItemClickListener((adapterView, view, i, l) -> {
            if (client_started) {
                Log.d("ARG-TRACE", "list:OnItemClick: ENTRY");
                WifiP2pDevice tmpDevice = null;

                String selectedGroupOwner = (String) list.getItemAtPosition(i);
                owner_name = selectedGroupOwner;
                for (WifiP2pDevice wifiP2pDevice : deviceArray) {
                    if (selectedGroupOwner.equals(wifiP2pDevice.deviceName)) {
                        tmpDevice = wifiP2pDevice;
                        break;
                    }
                }
                if (tmpDevice == null) {
                    Log.d("ARG-TRACE", "          For some reason the device name is not found in the device array.");
                    return;
                }

                final WifiP2pDevice device = tmpDevice;
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.groupOwnerIntent = groupOwnerIntent;

                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission();
                }

                Log.d("ARG-TRACE", "          calling connect");
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("ARG-TRACE", "          connect:onSuccess");
                        String connection_status = getString(R.string.connecting_to) + owner_name;
                        txtInfo.setText(connection_status);
                        btnJoin.setText(R.string.disconnect);
                        trying_to_connect = true;
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d("ARG-TRACE", "          onFailure: Not sure what happened?.");
                        txtInfo.setText(R.string.not_connected);
                    }
                });
            }
        });
    }

    public void shutdown_host() {
        Log.d("ARG-TRACE", "shutdown_host: ENTRY");
        if (group_started) {
            Log.d("ARG-TRACE", "          Force closing the serverSocket's socket.");
            try {
                serverClass.shutdown_sockets();
                serverClass.serverSocket.close();
            } catch (IOException e) {
                Log.d("ARG-TRACE", "       Hit an exception closing server socket from main: " + e.getMessage());
            }

            Log.d("ARG-TRACE", "          Group was started");

            Log.d("ARG-TRACE", "          calling removeGroup");
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("ARG-TRACE", "          removeGroup:onSuccess: we removed the group.");
                }

                @Override
                public void onFailure(int i) {
                    Log.d("ARG-TRACE", "          removeGroup:onFailure: something went wrong:" + i);
                }
            });

            host_started = false;
            group_started = false;
            btnStart.setText(R.string.driver);
            txtInfo.setText(R.string.driver_or_spotter);
            btnJoin.setEnabled(true);
            btnJoin.setVisibility(View.VISIBLE);
            start_driving = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            simpleButton.setText("-");
            simpleButton.setEnabled(false);
            simpleButton.setVisibility(View.GONE);
            list.setAdapter(null);

            RelativeLayout rl = findViewById(R.id.main_layout);
            if (rl != null) {
                rl.setBackgroundColor(Color.WHITE);
            }
        } else {
            Log.d("ARG-TRACE", "btnStart:OnClick: Group not started");
        }
    }

    public void shutdown_client() {
        Log.d("ARG-TRACE", "shutdown_client: ENTRY");
        if (is_connected) {
            Log.d("ARG-TRACE", "          client is already connected");

            Log.d("ARG-TRACE", "          ideally socket is closed before we remove ourselves from the group?");
            if (clientClass != null) {
                if (clientClass.socket.isConnected()) {
                    Log.d("ARG-TRACE", "               client socket still conencted");

                    clientClass.shutdown_socket();

                } else {
                    Log.d("ARG-TRACE", "               client socket NOT conencted");
                    runOnUiThread(() -> {
                        finalize_shutdown_client();
                    });
                }
            } else {
                Log.d("ARG-TRACE", "               clientClass is null");
                runOnUiThread(() -> {
                    finalize_shutdown_client();
                });
            }
        } else {
            runOnUiThread(() -> {
                finalize_shutdown_client();
            });
        }
    }

    public void finalize_shutdown_client() {
        Log.d("ARG-TRACE", "shutdown_client: ENTRY");
        if (is_connected) {
            Log.d("ARG-TRACE", "          calling removeGroup");
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("ARG-TRACE", "          removeGroup:onSuccess: cancel connection.");
                }

                @Override
                public void onFailure(int i) {
                    Log.d("ARG-TRACE", "          removeGroup:onFailure: something went wrong:" + i);
                }
            });

            client_started = true;
            btnJoin.setText(R.string.cancel);
            btnStart.setEnabled(false);
            btnStart.setVisibility(View.GONE);
            txtInfo.setText(R.string.select_the_driver_s_phone);
            simpleButton.setEnabled(false);
            simpleButton.setVisibility(View.GONE);
            is_connected = false;
            owner_name = "";
            list.setAdapter(null);
            sentName = false;
            myP2pName = "";

            RelativeLayout rl = findViewById(R.id.main_layout);
            if (rl != null) {
                rl.setBackgroundColor(Color.WHITE);
            }
        }

        if (trying_to_connect) {
            manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("ARG-TRACE", "          CancelConnect:onSuccess: Cancelled trying to connect.");
                }

                @Override
                public void onFailure(int i) {
                    Log.d("ARG-TRACE", "          CancelConnect:onFailure: something went wrong:" + i);
                }
            });

            btnJoin.setText(R.string.spotter);
            trying_to_connect = false;
        }

        Log.d("ARG-TRACE", "          calling stopPeerDiscovery");
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d("ARG-TRACE", "          stopPeerDiscovery:onSuccess: we stopped looking for a group.");
            }

            @Override
            public void onFailure(int i) {
                Log.d("ARG-TRACE", "          stopPeerDiscovery:onFailure: something went wrong: " + i);
            }
        });

        client_started = false;
        btnJoin.setText(R.string.spotter);
        btnStart.setEnabled(true);
        btnStart.setVisibility(View.VISIBLE);
        txtInfo.setText(R.string.driver_or_spotter);
        list.setAdapter(null);

        simpleButton.setText("-");
        simpleButton.setEnabled(false);
        simpleButton.setVisibility(View.GONE);

    }

    WifiP2pManager.ConnectionInfoListener clientConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            Log.d("ARG-TRACE", "clientConnectionInfoAvailable: ENTRY");
            if (client_started && wifiP2pInfo.groupFormed) {
                Log.d("ARG-TRACE", "          we joined a group.");
                String connection_status = getString(R.string.connected_to) + owner_name;
                txtInfo.setText(connection_status);
                btnJoin.setText(R.string.leave);

                simpleButton.setText(R.string.hold_when_safe);

                trying_to_connect = false;
                is_connected = true;
                sentName = false;

                Log.d("ARG-TRACE", "          Now that we are connected, stop looking for peers.");
                manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("ARG-TRACE", "          successfully stopped looking for peers.");
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d("ARG-TRACE", "          something went wrong trying to stop looking for peers.");
                    }
                });
                list.setAdapter(null);

                Log.d("ARG-TRACE", "          start the client");
                // Start client
                clientClass = new ClientClass(wifiP2pInfo.groupOwnerAddress);
                clientClass.start();

                simpleButton.setEnabled(true);
                simpleButton.setVisibility(View.VISIBLE);

                if (!sentName && myP2pName.equals("")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                            requestPermission();
                        }
                        manager.requestDeviceInfo(channel, wifiP2pDevice -> {
                            if (wifiP2pDevice != null) {
                                myP2pName = wifiP2pDevice.deviceName;
                            }
                        });
                    }
                }
            } else {
                Log.d("ARG-TRACE", "          group is not formed.");

                if (!trying_to_connect && is_connected && !wifiP2pInfo.groupFormed) {
                    // If we were connect, let's reset everything because the connection must have dropped.
                    Log.d("ARG-TRACE", "          We are the client and we were disconnected.");

                    shutdown_client();

                } else {
                    Log.d("ARG-TRACE", "          ignoring.");

                }

            }
        }
    };
    WifiP2pManager.ConnectionInfoListener serverConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            Log.d("ARG-TRACE", "clientConnectionInfoAvailable: ENTRY");

            Log.d("ARG-TRACE", "          either group is not formed or we are the owner.");
            if (wifiP2pInfo.groupFormed) {
                Log.d("ARG-TRACE", "          a group is formed and we are the owner.");
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("ARG-TRACE", "          Missing permissions.");
                    requestPermission();
                }
                Log.d("ARG-TRACE", "          requesting group info.");
                manager.requestGroupInfo(channel, wifiP2pGroup -> {
                    Log.d("ARG-TRACE", "          getting list of connected devices.");
                    Collection<WifiP2pDevice> devices = wifiP2pGroup.getClientList();

                    // Convert the collection of devices to a list
                    List<WifiP2pDevice> currentConnectedDevices = new ArrayList<>(devices);

                    // Compare the new list with the previous state
                    List<WifiP2pDevice> newlyConnectedDevices = new ArrayList<>(currentConnectedDevices);
                    newlyConnectedDevices.removeAll(connectedDevices);

                    List<WifiP2pDevice> disconnectedDevices = new ArrayList<>(connectedDevices);
                    disconnectedDevices.removeAll(currentConnectedDevices);

                    // Update the previous state with the current list
                    connectedDevices = currentConnectedDevices;

                    // Show the new list of connected devices
                    String[] deviceNames = new String[connectedDevices.size()];
                    int i = 0;
                    for (WifiP2pDevice device : devices) {
                        deviceNames[i] = device.deviceName;
                        i++;
                    }
                    try {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.redlistview, deviceNames);
                        list.setAdapter(adapter);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    // Signal any changes
                    if (!newlyConnectedDevices.isEmpty()) {
                        if (start_driving) {
                            playAlarmSound();
                        }
                        // New devices connected
                        for (WifiP2pDevice device : newlyConnectedDevices) {
                            Log.d("ARG-TRACE", "          Newly connected device: " + device.deviceName);
                            Log.d("ARG-TRACE", "          device: " + device);
                            connectedSpotters.add(new ConnectedSpotter(device.deviceName));
                        }
                    }

                    if (!disconnectedDevices.isEmpty()) {
                        if (start_driving) {
                            playAlarmSound();
                        }

                        // Devices disconnected
                        for (WifiP2pDevice device : disconnectedDevices) {
                            Log.d("ARG-TRACE", "          Disconnected device: " + device.deviceName);

                            // how many devices with the same name?
                            int matching_names = 0;
                            for (ConnectedSpotter tmp : connectedSpotters) {
                                if (tmp.name.equals(device.deviceName)) {
                                    matching_names++;
                                }
                            }
                            for (ConnectedSpotter tmp : connectedSpotters) {
                                if (tmp.name.equals(device.deviceName)) {
                                    if (matching_names > 1) {
                                        Log.d("ARG-TRACE", "          Duplicate names found, lets close them all.");
                                        serverClass.close_socket(tmp.socket_address);
                                        /*
                                        // Loop through current sockets to see if this device is still connected.
                                        // This way if multiple devices have the same name, we remove the right one.
                                        boolean remove = true;
                                        if (tmp.socket_address != null) {
                                            if (serverClass.is_socket_active(tmp.socket_address)) {
                                                remove = false;
                                                break;
                                            }
                                        }
                                        if (remove) {
                                            connectedSpotters.remove(tmp);
                                            break;
                                        }
                                        */
                                        connectedSpotters.remove(tmp);
                                    } else {
                                        Log.d("ARG-TRACE", "          No duplicate names, disconnecting device.");
                                        connectedSpotters.remove(tmp);
                                    }
                                }
                            }

                        }
                    }
                });
            }
        }
    };

    private void initialWork() {
        txtInfo = findViewById(R.id.txtInfo);
        simpleButton = findViewById(R.id.simpleButton);
        btnStart = findViewById(R.id.btnStart);
        btnJoin = findViewById(R.id.btnJoin);
        list = findViewById(R.id.list);
        simpleButton.setEnabled(false);
        simpleButton.setVisibility(View.GONE);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), channelListener);
        receiver = new myBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int currVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume_level = ((float) currVolume / (float) maxVolume);
        Log.d("ARG-TRACE", "Volume level: " + volume_level);
        if (volume_level > 0.5) {
            txtInfo.setText(R.string.driver_or_spotter);
        } else {
            txtInfo.setText(R.string.warning_volume_is_less_than_50);
        }
    }

    WifiP2pManager.ChannelListener channelListener = () -> Log.d("ARG-TRACE", "channelListener: onChannelDisconnected.");

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!host_started && client_started && !trying_to_connect && !is_connected) {
                Log.d("ARG-TRACE", "peerListListener:onPeersAvailable: Received the signal of a change.");

                peers.clear();
                peers.addAll(peerList.getDeviceList());

                List<String> groupOwners = new ArrayList<>();
                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;
                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    //Log.d("ARG-TRACE", "     CLIENT:" + device.deviceName + ":" + device.status+":"+(device.isGroupOwner()?"Host":"Client"));
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    if (device.isGroupOwner()) {
                        groupOwners.add(device.deviceName);
                    }
                    index++;
                }
                try {
                    int num_owners = groupOwners.size();
                    if (num_owners > 0) {
                        Log.d("ARG-TRACE", "          " + num_owners + " group owners found.");
                        deviceNameArray = groupOwners.toArray(new String[num_owners]);
                        int num_peers = peers.size();
                        deviceArray = peers.toArray(new WifiP2pDevice[num_peers]);

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.listview, deviceNameArray);
                        list.setAdapter(adapter);

                        for (int list_itr = 0; list_itr < num_owners; list_itr++) {
                            TextView list_item = (TextView) list.getChildAt(list_itr);
                            if (list_item != null) {
                                if (list_itr % 2 == 1) {
                                    list_item.setBackground(getDrawable(R.drawable.tan_list_item));
                                }
                            } else {
                                Log.d("ARG-TRACE", "          No group owner list item found at position: " + list_itr);
                            }
                        }
                    } else {
                        Log.d("ARG-TRACE", "          No group owners found.");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (peers.size() == 0) {
                    Log.d("ARG-TRACE", "peerListListener:onPeersAvailable: list size == 0.");
                    txtInfo.setText(R.string.no_others_found);
                }

            } else {
                // Why isn't this being called?
                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    Log.d("ARG-TRACE", "     CLIENT:" + device.deviceName + ":" + device.status + ":" + (device.isGroupOwner() ? "Host" : "Client") + ":" + device.deviceAddress);
                }
            }
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d("ARG-TRACE", "onDestroy: ENTRY");

            if (host_started) {
                shutdown_host();
            }
            if (client_started) {
                shutdown_client();
            }
            unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.d("ARG-TRACE", "onDestroy: Exception: " + e.getMessage());
        }
        super.onDestroy();
    }

    protected void requestPermission() {
        Log.d("ARG-TRACE", "requestPermission: ENTRY");
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ARG-TRACE", "          Need to get ACCESS_FINE_LOCATION");
            ActivityCompat.shouldShowRequestPermissionRationale(this, "In order to Join a spot safely group, you will need to grant location permissions.");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                Log.d("ARG-TRACE", "          Need to get NEARBY_WIFI_DEVICES");
                ActivityCompat.shouldShowRequestPermissionRationale(this, "In order to Join a spot safely group, you will need to grant nearby wifi permissions.");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        if (isWifiP2pEnabled) {
            if (!host_started && !client_started) {
                Log.d("ARG-TRACE", "setIsWifiP2pEnabled: set that WiFi is ON.");
                if (volume_level > 0.5) {
                    txtInfo.setText(R.string.driver_or_spotter);
                } else {
                    txtInfo.setText(R.string.warning_volume_is_less_than_50);
                }
                btnJoin.setEnabled(true);
                btnJoin.setVisibility(View.VISIBLE);
                btnStart.setEnabled(true);
                btnStart.setVisibility(View.VISIBLE);
            }
        } else {
            Log.d("ARG-TRACE", "setIsWifiP2pEnabled: set that WiFi is OFF.");
            txtInfo.setText(R.string.wifi_off);
            btnJoin.setEnabled(false);
            btnJoin.setVisibility(View.GONE);
            btnStart.setEnabled(false);
            btnStart.setVisibility(View.GONE);


            if (host_started) {
                // If we were driving, we should probably sound the alarm
                if (start_driving) {
                    playAlarmSound();
                }
                shutdown_host();
            }
            if (client_started) {
                shutdown_client();
            }
        }
    }

    public class ThreadHandler extends Thread {
        ServerSocket serverSocket;
        private ArrayList<ServerClass> serverThreads = new ArrayList<>();

        @Override
        public void run() {
            Log.d("ARG-TRACE", "ServerClass: creating server socket");
            try {
                serverSocket = new ServerSocket(8888);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            while (!isInterrupted() && !serverSocket.isClosed()) { //You should handle interrupts in some way, so that the thread won't keep on forever if you exit the app.
                try {
                    ServerClass thread = new ServerClass(serverSocket.accept());
                    serverThreads.add(thread);
                    thread.start();
                } catch (IOException e) {
                    Log.d("ARG-TRACE", "ThreadHandler: IOException: " + e.getMessage());
                }
            }
        }

        public void shutdown_sockets() {
            for (ServerClass serverThread : serverThreads) {
                serverThread.shutdown_socket();
            }
        }

        public boolean is_socket_active(String socket_address) {
            for (ServerClass connection : serverThreads) {
                if (connection.socket.getRemoteSocketAddress().toString().equals(socket_address)) {
                    if (connection.socket.isConnected()) {
                        Log.d("ARG-TRACE", "         Socket is still here and connected: " + socket_address);
                        return true;
                    } else {
                        Log.d("ARG-TRACE", "         Socket is still here and NOT connected: " + socket_address);
                        return false;
                    }
                }
            }
            Log.d("ARG-TRACE", "         Socket is NOT here: " + socket_address);
            return false;
        }

        public void close_socket(String socket_address) {
            for (ServerClass connection : serverThreads) {
                if (connection.socket.getRemoteSocketAddress().toString().equals(socket_address)) {
                    connection.shutdown_socket();
                }
            }
        }
    }

    public class ServerClass extends Thread {
        private Socket socket;
        private InputStream inputStream;

        public ServerClass(Socket socket) {
            this.socket = socket;
            Log.d("ARG-TRACE", "ServerClass: new socket: " + socket.getRemoteSocketAddress());
            Log.d("ARG-TRACE", "          " + socket);
        }

        public void shutdown_socket() {
            if (socket != null) {
                if (socket.isConnected()) {
                    Log.d("ARG-TRACE", "ServerClass: shutting down socket.");
                    try {
                        socket.shutdownInput();
                        socket.shutdownOutput();
                        socket.close();
                    } catch (IOException e) {
                        Log.d("ARG-TRACE", "ServerClass: shutting down socket: error: " + e.getMessage());
                    }
                } else {
                    Log.d("ARG-TRACE", "ServerClass: shutting down socket: socket was not null, but not connected?");
                    socket = null;
                }
            }
        }

        @Override
        public void run() {
            Log.d("ARG-TRACE", "ServerClass: run: starting the server class.");
            try {
                Log.d("ARG-TRACE", "ServerClass: getting inputStream");
                inputStream = socket.getInputStream();
            } catch (SocketException e) {
                // Socket closed
                Log.d("ARG-TRACE", "          SocketException: " + e.getMessage());
                return;
            } catch (IOException e) {
                Log.d("ARG-TRACE", "ServerClass: !!!!!!!!!!!!!!! Runtime exception starting server class");
                Log.d("ARG-TRACE", "ServerClass: " + e.getMessage());
                throw new RuntimeException(e);
            }

            Log.d("ARG-TRACE", "ServerClass: created the socket");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            Log.d("ARG-TRACE", "ServerClass: calling execute");

            executor.execute(() -> {
                byte[] buffer = new byte[1024];
                int bytes;
                Log.d("ARG-TRACE", "ServerClass: run: executor: run: starting the while loop to process incoming bytes.");

                try {
                    while (socket != null) {
                        bytes = inputStream.read(buffer);
                        if (bytes > 0) {
                            int finalBytes = bytes;
                            handler.post(() -> {
                                Log.d("ARG-TRACE", "****** Server class received input.");
                                String tempMSG = new String(buffer, 0, finalBytes);

                                boolean status = false;
                                if (tempMSG.equals("0") || tempMSG.equals("1")) {
                                    if (tempMSG.equals("0") && start_driving && !in_alarm) {
                                        playAlarmSound();
                                    }
                                    if (tempMSG.equals("1")) {
                                        status = true;
                                    }

                                    // Set the status for this device
                                    String clientAddress = socket.getRemoteSocketAddress().toString();

                                    for (ConnectedSpotter tmp : connectedSpotters) {
                                        Log.d("ARG-TRACE", "          Looking for match: " + tmp.socket_address + " == " + clientAddress);
                                        if (tmp.socket_address != null && tmp.socket_address.equals(clientAddress)) {
                                            Log.d("ARG-TRACE", "          Updating status for " + tmp.name);
                                            tmp.set_code(status);
                                            break;
                                        }
                                    }

                                    runOnUiThread(MainActivity.this::update_listView_colors);

                                } else {
                                    if (tempMSG.equals(shutdown_signal)) {
                                        // Received shutdown socket signal
                                        Log.d("ARG-TRACE", "          Received shutdown socket signal from: " + socket.getRemoteSocketAddress());
                                        shutdown_socket();
                                    } else {
                                        // Initial communication, set the IP address
                                        Log.d("ARG-TRACE", "          Received device Name: " + tempMSG);
                                        Log.d("ARG-TRACE", "          Received Name from socket: " + socket.getRemoteSocketAddress());
                                        String clientAddress = socket.getInetAddress().toString().substring(1);

                                        for (ConnectedSpotter tmp : connectedSpotters) {
                                            Log.d("ARG-TRACE", "          Looking for device name match: " + tmp.name + " == " + tempMSG);
                                            if (tmp.name != null && tmp.name.equals(tempMSG) && tmp.socket_address == null) {
                                                Log.d("ARG-TRACE", "          Updating socket address for " + tmp.name);
                                                tmp.set_socketAddress(socket.getRemoteSocketAddress().toString());
                                                break;
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    Log.d("ARG-TRACE", "ServerClass: run: executor: run: RUNTIME EXCEPTION.");
                    Log.d("ARG-TRACE", "ServerClass: " + e.getMessage());
                    shutdown_socket();
                    //throw new RuntimeException(e);
                }

                Log.d("ARG-TRACE",  "ServerClass: socket must be null, closing down");
                shutdown_socket();
            });
        }
    }

    public void update_listView_colors() {
        Log.d("ARG-TRACE", "Updating list Background colors.");
        Iterator<ConnectedSpotter> itr = connectedSpotters.iterator();
        int i = 0;

        while (itr.hasNext()) {
            ConnectedSpotter tmp = itr.next();
            if (!tmp.status) {
                list.getChildAt(i).setBackground(getDrawable(R.drawable.list_item_red));
                Log.d("ARG-TRACE", list.getItemAtPosition(i) + " is red due to status.");
            } else {
                if (tmp.last_comm < System.currentTimeMillis() - 1000) {
                    list.getChildAt(i).setBackground(getDrawable(R.drawable.list_item_red));

                    Log.d("ARG-TRACE", list.getItemAtPosition(i) + " is red due to no update in the last second.");
                } else {
                    list.getChildAt(i).setBackground(getDrawable(R.drawable.list_item_green));
                }
            }
            i++;
        }
    }

    public class ClientClass extends Thread {
        String hostAdd;
        private Socket socket;
        private OutputStream outputStream;

        public ClientClass(InetAddress hostAddress) {
            Log.d("ARG-TRACE", "ClientClass: constructor: initializing the client class.");
            if (hostAddress != null) {
                hostAdd = hostAddress.getHostAddress();
                socket = new Socket();
            } else {
                Log.d("ARG-TRACE", "          hostAddress was null???");
            }
        }

        public void write(byte[] bytes) {
            //Log.d("ARG-TRACE", "ClientClass: write: starting to write to output stream.");
            try {
                //Log.d("ARG-TRACE", "          client socket: " + socket.getLocalSocketAddress());
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.d("ARG-TRACE", "          Error: " + e.getMessage());
                //throw new RuntimeException(e);
                shutdown_client();
            }
        }

        public void shutdown_socket() {
            Log.d("ARG-TRACE", "ClientClass: shutdown_socket: ENTRY");
            if (socket != null) {
                if (socket.isConnected()) {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        // Send the signal to the server to shutdown the socket, otherwise it
                        // ends up staying open.
                        Log.d("ARG-TRACE", "          SHUTDOWN CLIENT THREAD: sending shutdown signal.");
                        write(shutdown_signal.getBytes());

                        Log.d("ARG-TRACE", "          SHUTDOWN CLIENT THREAD: shutting down socket.");
                        try {
                            socket.shutdownInput();
                            socket.shutdownOutput();  // Might have a race condition here with sending the shutdown signal
                            socket.close();
                            Log.d("ARG-TRACE", "          SHUTDOWN CLIENT THREAD: socket close was called, now going back to UI thread");

                            runOnUiThread(() -> {
                                Log.d("ARG-TRACE", "          Calling finalize_shutdown_client on UI thread.");
                                finalize_shutdown_client();
                            });
                        } catch (IOException e) {
                            Log.d("ARG-TRACE", "ClientClass: shutting down socket: error: " + e.getMessage());
                            runOnUiThread(() -> {
                                finalize_shutdown_client();
                            });
                        }

                    });
                } else {
                    Log.d("ARG-TRACE", "ClientClass: shutting down socket: socket was not null, but not connected?");
                    socket = null;
                    runOnUiThread(() -> {
                        finalize_shutdown_client();
                    });
                }
            } else {
                Log.d("ARG-TRACE", "ClientClass: shutting down socket: socket was null?");
                runOnUiThread(() -> {
                    finalize_shutdown_client();
                });
            }
        }

        @Override
        public void run() {
            Log.d("ARG-TRACE", "ClientClass: run: starting the client class.");
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 500);
                outputStream = socket.getOutputStream();
                Log.d("ARG-TRACE", "          ClientClass: client socket: " + socket.getLocalSocketAddress());
            } catch (IOException e) {
                Log.d("ARG-TRACE", "          ClientClass: Exception: " + e.getMessage());
                //throw new RuntimeException(e);
            }
        }

    }

    public void playAlarmSound() {
        Log.d("ARG-TRACE", "playAlarmSound ENTRY.");
        if (!in_alarm) {
            in_alarm = true;
            simpleButton.setText(R.string.stop_alarm);

            // Create a MediaPlayer object and specify the alarm sound file
            mediaPlayer = MediaPlayer.create(this, R.raw.emergency_alarm);

            // Set looping to true to play the alarm sound repeatedly
            mediaPlayer.setLooping(true);

            // Start playing the alarm sound
            mediaPlayer.start();
        }
    }

    public void stopAlarmSound() {
        Log.d("ARG-TRACE", "stopAlarmSound ENTRY.");

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d("ARG-TRACE", "          stopping alarm");
            // Stop the alarm sound and release the MediaPlayer resources
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;

            in_alarm = false;
            start_driving = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (host_started) {
                simpleButton.setText(R.string.start_driving);
            } else {
                simpleButton.setText("-");
                simpleButton.setEnabled(false);
                simpleButton.setVisibility(View.GONE);
            }
        } else {
            Log.d("ARG-TRACE", "          can't stop alarm");
        }
    }
}