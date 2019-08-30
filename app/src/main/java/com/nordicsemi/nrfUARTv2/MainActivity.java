
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.nordicsemi.nrfUARTv2;


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener, ServiceConnection, View.OnClickListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int CONNECT_LAST_DEVICE = 3;
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static int currentStatus = UART_PROFILE_DISCONNECTED;
    public final String TAG = this.getClass().getSimpleName();
    private UartService uartService = null;
    private BluetoothDevice btDevice = null;
    private BluetoothAdapter btAdapter = null;
    private ArrayAdapter<String> listAdapter;
    private String lastDeviceAddress = null; // stores last-connected BT device address
    private boolean mUserDisconnect = false; // flag for user vs unexpected BT disconnect
    private Button btnConnectDisconnect, btnSend;
    private EditText edtMessage;
    private ListView messageListView;


    // this is used to keep track of BT connection state for tryConnectBT
    public static boolean isBTConnected() {
        return currentStatus == UART_PROFILE_CONNECTED;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(BleEvent event) {
        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
        switch (event.getCurrentEvent()) {
            case BleEvent.ACTION_GATT_CONNECTED:
                Log.d(TAG, "UART_CONNECT_MSG");
                btnConnectDisconnect.setText("Disconnect");
                edtMessage.setEnabled(true);
                btnSend.setEnabled(true);
                ((TextView) findViewById(R.id.deviceName)).setText(btDevice.getName() + " - ready");
                listAdapter.add("[" + currentDateTimeString + "] Connected to: " + btDevice.getName());
                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                currentStatus = UART_PROFILE_CONNECTED;
                break;
            case BleEvent.ACTION_GATT_DISCONNECTED:
                Log.d(TAG, "UART_DISCONNECT_MSG");
                btnConnectDisconnect.setText("Connect");
                edtMessage.setEnabled(false);
                btnSend.setEnabled(false);
                ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                listAdapter.add("[" + currentDateTimeString + "] Disconnected to: " + btDevice.getName());
                currentStatus = UART_PROFILE_DISCONNECTED;
                uartService.close();
                tryConnectBT(); // try to reconnect, if needed
                //setUiState();
                break;
            case BleEvent.ACTION_GATT_SERVICES_DISCOVERED:
                uartService.enableTXNotification();
                break;
            case BleEvent.ACTION_DATA_AVAILABLE:
                try {
                    String text = new String(event.getValue(), StandardCharsets.UTF_8);
                    listAdapter.add("[" + currentDateTimeString + "] RX: " + text);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    commitToFile("[" + currentDateTimeString + "] RX: " + text);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                break;
            case BleEvent.DEVICE_DOES_NOT_SUPPORT_UART:
                showMessage("Device doesn't support UART. Disconnecting");
                uartService.disconnect();
                break;
            default:
                break;

        }

    }

    // bluetooth autoconnect to last device and reconnect service
    public void tryConnectBT() {
        if (mUserDisconnect) {
            mUserDisconnect = false;
            return; // this was an intentional disconnect, do nothing
        }
        if (lastDeviceAddress == null) {
            return; // we have nothing to connect to..
        }

        final Handler handler = new Handler();
        final Runnable runner = new Runnable() {
            @Override public void run() {
                Log.d(TAG, "checking BT connection onStart");
                if (uartService == null) {
                    return;
                }
                if (!isBTConnected()) {
                    uartService.connect(lastDeviceAddress);
                }
                if (!isBTConnected()) {
                    Log.d(TAG, "Failed to connect, try again later..");
                    handler.postDelayed(this, 10000);
                }
            }
        };
        handler.postDelayed(runner, 100);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        EventBus.getDefault().register(this);
        messageListView = findViewById(R.id.listMessage);
        btnConnectDisconnect = findViewById(R.id.btn_select);
        btnSend = findViewById(R.id.sendButton);
        edtMessage = findViewById(R.id.sendText);
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        listAdapter = new ArrayAdapter<>(this, R.layout.message_detail);
        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);
        initService();
        // retrieve last connected BT device, this needs to be set up for quick autoconnect
        lastDeviceAddress = DevicePreferences.getLastDevice(getApplicationContext());
        Log.d(TAG, "..retrieved lastDeviceAddress= " + lastDeviceAddress);
        if (lastDeviceAddress != null && btAdapter.isEnabled()) {
            btDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(lastDeviceAddress);
        }
        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(this);
        // Handle Send button
        btnSend.setOnClickListener(this);
        // Set initial UI state
    }

    private void commitToFile(String loggedText) throws IOException {
        FileOutputStream fOut = openFileOutput("savedData.txt",
                MODE_APPEND);
        OutputStreamWriter osw = new OutputStreamWriter(fOut);
        osw.write(loggedText);
        osw.flush();
        osw.close();
    }

    private void initService() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        tryConnectBT();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        unbindService(this);
        uartService.stopSelf();
        uartService = null;

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!btAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECT_LAST_DEVICE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "..connecting to lastDeviceAddress= " + lastDeviceAddress);
                    uartService.connect(lastDeviceAddress);
                }
                break;
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    btDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + btDevice + "mserviceValue" + uartService);
                    ((TextView) findViewById(R.id.deviceName)).setText(btDevice.getName() + " - connecting");
                    // store connected device
                    Log.d(TAG, "..storing lastDeviceAddress= " + deviceAddress);
                    DevicePreferences.setLastDevice(getApplicationContext(), deviceAddress);
                    uartService.connect(lastDeviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (currentStatus == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain); // this only starts new task if not already running
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        } else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("系统消息！")
                    .setMessage("确定退出嘛？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    @Override public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        uartService = ((UartService.LocalBinder) iBinder).getService();
        Log.d(TAG, "onServiceConnected uartService= " + uartService);
        if (!uartService.initialize()) {
            Log.e(TAG, "Unable to initialize Bluetooth");
            finish();
        }
    }

    @Override public void onServiceDisconnected(ComponentName componentName) {
        //uartService.disconnect(btDevice);
        uartService = null;
    }

    @Override public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_select:
                if (!btAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (btnConnectDisconnect.getText().equals("Connect")) {
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (btDevice != null) {
                            mUserDisconnect = true;
                            uartService.disconnect();
                        }
                    }
                }
                break;
            case R.id.sendButton:
                EditText editText = findViewById(R.id.sendText);
                String message = editText.getText().toString();
                byte[] value;
                //send data to service
                value = message.getBytes(StandardCharsets.UTF_8);
                uartService.writeRXCharacteristic(value);
                //Update the log with time stamp
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                listAdapter.add("[" + currentDateTimeString + "] TX: " + message);
                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                edtMessage.setText("");
                break;
            default:
                break;
        }
    }
}
