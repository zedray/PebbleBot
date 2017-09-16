package com.zedray.pebblebotandroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT32;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "Pebble";
    private final static String DEVICE_NAME = "keg";
    private final static String DEVICE_ADDRESS = "D0:39:72:C9:1E:15";

    private final static String SERVICE = "a495ff20-c5b1-4b44-b512-1370f02d74de";
    private final static String SCRATCH1 = "a495ff21-c5b1-4b44-b512-1370f02d74de";
    private final static String SCRATCH2 = "a495ff22-c5b1-4b44-b512-1370f02d74de";

    private final static byte[] ROTATE_FORWARDS = hexStringToByteArray("00");
    private final static byte[] ROTATE_STOP = hexStringToByteArray("5a");
    private final static byte[] ROTATE_BACKWARDS = hexStringToByteArray("b4");

    private final static int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattService mService;
    private BluetoothGattCharacteristic mC1;
    private BluetoothGattCharacteristic mC2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();


        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        scanLeDevice(true);

        findViewById(R.id.button_left_forwards).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        Log.w(TAG, "button_left_forwards ACTION_UP");
                        set(mC1, ROTATE_STOP);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        Log.w(TAG, "button_left_forwards ACTION_DOWN");
                        set(mC1, ROTATE_BACKWARDS);
                        break;
                }
                return false;
            }
        });
        findViewById(R.id.button_right_forwards).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        Log.w(TAG, "button_right_forwards ACTION_UP");
                        set(mC2, ROTATE_STOP);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        Log.w(TAG, "button_right_forwards ACTION_DOWN");
                        set(mC2, ROTATE_FORWARDS);
                        break;
                }
                return false;
            }
        });

        findViewById(R.id.button_left_backwards).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        Log.w(TAG, "button_left_backwards ACTION_UP");
                        set(mC1, ROTATE_STOP);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        Log.w(TAG, "button_left_backwards ACTION_DOWN");
                        set(mC1, ROTATE_FORWARDS);
                        break;
                }
                return false;
            }
        });
        findViewById(R.id.button_right_backwards).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        Log.w(TAG, "button_right_backwards ACTION_UP");
                        set(mC2, ROTATE_STOP);
                        break;
                    case MotionEvent.ACTION_DOWN:
                        Log.w(TAG, "button_right_backwards ACTION_DOWN");
                        set(mC2, ROTATE_BACKWARDS);
                        break;
                }
                return false;
            }
        });
    }


    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }

    private void set(BluetoothGattCharacteristic c, byte[] rotate) {
        Log.w(TAG, "set " + c + "  " + rotate);
        if (c != null) {
            c.setValue(rotate);
            mBluetoothGatt.writeCharacteristic(c);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    private boolean mScanning;
    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 10000;

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.w(TAG, "device: " + device.getName() + " " + device.getAddress());

                            if (device.getName() != null && device.getName().equalsIgnoreCase(DEVICE_NAME)) {

                                mScanning = false;
                                mBluetoothAdapter.stopLeScan(mLeScanCallback);

                                connectToDevice(device);
                            }
                        }
                    });
                }
            };

    BluetoothGatt mBluetoothGatt;
    int mConnectionState;

    private void connectToDevice(BluetoothDevice device) {
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
    }

    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mConnectionState = STATE_CONNECTED;
                        Log.i(TAG, "Connected to GATT server.");
                        Log.i(TAG, "Attempting to start service discovery:" +
                                mBluetoothGatt.discoverServices());

                    } else if (newState == STATE_DISCONNECTED) {
                        mConnectionState = STATE_DISCONNECTED;
                        Log.i(TAG, "Disconnected from GATT server.");
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "onServicesDiscovered GATT_SUCCESS");
                        for (BluetoothGattService service : gatt.getServices()) {
                            Log.w(TAG, "service: " + service.getUuid());
                            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                Log.w(TAG, "characteristic: " + characteristic.getUuid() + "  " + characteristic.getInstanceId());
                                if (characteristic.getValue() == null) {
                                    //Log.w(TAG, "value is null");
                                } else {
                                    int format = -1;
                                    final int value = characteristic.getIntValue(format, 1);
                                    Log.w(TAG, "value: " + value);
                                }
                            }
                        }

                        mService = gatt.getService(UUID.fromString(SERVICE));
                        Log.w(TAG, "mService: " + mService.getUuid());
                        mC1 = mService.getCharacteristic(UUID.fromString(SCRATCH1));
                        Log.w(TAG, "mC1: " + mC1.getUuid());
                        mC2 = mService.getCharacteristic(UUID.fromString(SCRATCH2));
                        Log.w(TAG, "mC2: " + mC2.getUuid());
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    Log.w(TAG, "onCharacteristicRead status: " + status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "onCharacteristicRead received: " + characteristic);
                        //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.d("onCharacteristicWrite", "Failed write, retrying");
                        gatt.writeCharacteristic(characteristic);
                    }
                    Log.w(TAG, "onCharacteristicWrite characteristic: " + characteristic.getUuid() + " " + status);
                }
            };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning) {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }
}
