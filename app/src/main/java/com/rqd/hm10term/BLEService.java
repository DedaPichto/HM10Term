package com.rqd.hm10term;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by denis on 25.03.18.
 */

public class BLEService extends Service {
    private List<String> filters;
    private String LOG_TAG = BLEService.class.getName();

    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    BluetoothGattCharacteristic mHM10characteristicRXTX;

    public final static int STATE_DISCONNECTED = 0;
    public final static int STATE_CONNECTING = -1;
    public final static int STATE_CONNECTED = -2;
    public final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
    public final static int STATE_DISCONNECTING = -4;
    public final static int STATE_CLOSED = -5;
    private int mConnectionState = STATE_DISCONNECTED;

    public final static String ACTION_GATT_CONNECTED =
            "com.rqd.electrycat.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_CONNECTING =
            "com.rqd.electrycat.ACTION_GATT_CONNECTING";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.rqd.electrycat.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_DISCONNECTING =
            "com.rqd.electrycat.ACTION_GATT_DISCONNECTING";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.rqd.electrycat.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.rqd.electrycat.ACTION_DATA_AVAILABLE";
    public final static String ACTION_GET_STATE =
            "com.rqd.electrycat.ACTION_GET_STATE";
    public final static String ACTION_GET_FREQUENCY =
            "com.rqd.electrycat.ACTION_GET_FREQUENCY";
    public final static String ACTION_GET_FILL =
            "com.rqd.electrycat.ACTION_GET_FILL";
    public final static String ACTION_GET_CHARGE =
            "com.rqd.electrycat.ACTION_GET_CHARGE";
    public final static String ACTION_GET_SLEEP =
            "com.rqd.electrycat.ACTION_GET_SLEEP";
    public final static String EXTRA_DATA =
            "com.rqd.electrycat.EXTRA_DATA";
    public final static String PARAM_NAME =
            "com.rqd.electrycat.PARAM_NAME";
    public final static String PARAM_VALUE =
            "com.rqd.electrycat.PARAM_VALUE";

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Enables or disables notification on a give characteristic.
     * https://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(
            BluetoothGatt gatt,
            BluetoothGattDescriptor descriptor,
            BluetoothGattCharacteristic characteristic,
            boolean enabled) {
        if(gatt != null && descriptor != null && characteristic != null) {
            int readProperties = characteristic.getProperties();
            if ((readProperties & mHM10characteristicRXTX.PROPERTY_INDICATE) != 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            } else if ((readProperties & mHM10characteristicRXTX.PROPERTY_NOTIFY) != 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            gatt.writeDescriptor(descriptor);
        }
    }

    /** Регистрируем уведомления сервисов HM-10
     * https://stackoverflow.com/questions/25865587/android-4-3-bluetooth-ble-dont-called-oncharacteristicread/25866874
     */
    protected void registerHM10ServicesNotifications(BluetoothGatt gatt) {
        BluetoothGattService serviceHM10 = gatt.getService(UUID.fromString(BLENameResolver.SERVICE_HM10));
        if(serviceHM10 != null) {
            mHM10characteristicRXTX = serviceHM10.getCharacteristic(UUID.fromString(BLENameResolver.CHARACTERISTIC_HM10_RXTX));
            if(mHM10characteristicRXTX != null) {

                BluetoothGattDescriptor descriptorConfig =
                        mHM10characteristicRXTX.getDescriptor(UUID.fromString(BLENameResolver.DESCRIPTOR_HM10_CLIENT_CONFIG));
                setCharacteristicNotification(gatt, descriptorConfig, mHM10characteristicRXTX, true);

                BluetoothGattDescriptor descriptorUser =
                        mHM10characteristicRXTX.getDescriptor(UUID.fromString(BLENameResolver.DESCRIPTOR_HM10_USER_DESCRIPTION));
                setCharacteristicNotification(gatt, descriptorUser, mHM10characteristicRXTX, true);

                gatt.setCharacteristicNotification(mHM10characteristicRXTX, true);
            }
        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction = "Unknown Action";
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.w(LOG_TAG, "STATE_CONNECTED");
                    mConnectionState = STATE_CONNECTED;
                    intentAction = ACTION_GATT_CONNECTED;
                    if(!mBluetoothGatt.discoverServices()) {
                        Log.w(LOG_TAG, String.format("Error Discover Service"));
                    }
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    Log.w(LOG_TAG, "STATE_CONNECTING");
                    mConnectionState = STATE_CONNECTING;
                    intentAction = ACTION_GATT_CONNECTING;
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.w(LOG_TAG, "STATE_DISCONNECTED");
                    mConnectionState = STATE_DISCONNECTED;
                    intentAction = ACTION_GATT_DISCONNECTED;
                    break;
                case BluetoothProfile.STATE_DISCONNECTING:
                    Log.w(LOG_TAG, "STATE_DISCONNECTING");
                    mConnectionState = STATE_DISCONNECTING;
                    intentAction = ACTION_GATT_DISCONNECTING;
                    break;
                default:
                    Log.w(LOG_TAG, "Unknown State");
                    break;
            }
            broadcastUpdate(intentAction);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                // https://stackoverflow.com/questions/24990061/android-ble-notifications-for-glucose
            } else {
                Log.w(LOG_TAG, "onServicesDiscovered received: " + status);
            }
            registerHM10ServicesNotifications(gatt);
            Log.w(LOG_TAG, "BluetothLeService().onServicesDiscovered()");
        }

        private void parseMessage(String message) {
            final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
            String[] messages = message.trim().split("\\s+");
            for(String msg : messages) {
                if(filters == null) {
                    broadcastMessage("reply", msg.trim());
                } else {
                    for(String filter : filters) {
                        // for ex "p\d+$"
                        Pattern p = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(msg);
                        if (!m.matches()) {
                            broadcastMessage("reply", msg.trim());
                            sendBroadcast(intent);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            // super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                int flag = characteristic.getProperties();
                Log.w("onCharacteristicRead()", String.format("Read message %s", characteristic.getStringValue(0).toString()));
                // parseMessage(characteristic.getStringValue(0));
            } else
            {
                Log.w(LOG_TAG, "Error (onCharacteristicRead()): " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // https://stackoverflow.com/questions/36659340/android-ble-oncharacteristicread-and-oncharacteristicchanged-never-called
            Log.w("onCharacteristicChanged", String.format("value = %s", characteristic.getStringValue(0)));
            super.onCharacteristicChanged(gatt, characteristic);
            gatt.readCharacteristic(characteristic);
            int flag = characteristic.getProperties();
            parseMessage(characteristic.getStringValue(0));
        }
    };

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     * Подключаемся к GATT-серверу, размещённому на устройстве BLE
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(LOG_TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(LOG_TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(LOG_TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(LOG_TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        // broadcastMessage("connected", 1);
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        // broadcastMessage("connected", 0);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public void broadcastMessage(String strAction, int param) {
        Intent intent = new Intent(EXTRA_DATA);
        intent.putExtra(PARAM_NAME, strAction);
        intent.putExtra(PARAM_VALUE, param);
        sendBroadcast(intent);
    }

    public void broadcastMessage(String strAction, String param) {
        Intent intent = new Intent(EXTRA_DATA);
        intent.putExtra(PARAM_NAME, strAction);
        intent.putExtra(PARAM_VALUE, param);
        sendBroadcast(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(LOG_TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(LOG_TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }

        Log.w(LOG_TAG, "readCharacteristic()");
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     * https://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        Log.w(LOG_TAG, "setCharacteristicNotification(" + characteristic.getUuid() + ")");
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /*
     *  Отсылаем характеристику
     */
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        Log.w(LOG_TAG, "Trying send characteristic");
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /** Отправляет сообщение через характеристику HM10
     *
     * TODO Разобраться, почему иногда возвращается false, хотя, данные отлично отправлены
     */
    public boolean sendMessage(String strMSG)
    {
        if(mHM10characteristicRXTX != null) {
            mHM10characteristicRXTX.setValue(strMSG);
            // Log.w(LOG_TAG, "sendMessage(" + strMSG + ")");
            return mBluetoothGatt.writeCharacteristic(mHM10characteristicRXTX);
        }

        return false;
    }
}
