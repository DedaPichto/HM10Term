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

/**
 * Created by denis on 25.03.18.
 */

public class LeService extends Service {
    private String TAG = LeService.class.getName();

    private int mBallFrequency;
    private int mBallFill;
    private int mBallSleep;
    private boolean mBallState;

    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    BluetoothGattCharacteristic mHM10characteristicRXTX;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.rqd.denis.ball.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.rqd.denis.ball.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.rqd.denis.ball.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.rqd.denis.ball.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.rqd.denis.ball.EXTRA_DATA";
    public final static String ACTION_FILL =
            "com.rqd.denis.ball.ACTION_FILL";
    public final static String PARAM_NAME =
            "com.rqd.denis.ball.PARAM_NAME";
    public final static String PARAM_VALUE =
            "com.rqd.denis.ball.PARAM_VALUE";

    public class LocalBinder extends Binder {
        LeService getService() {
            return LeService.this;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.w(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.w(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.w(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService serviceHM10 = gatt.getService(UUID.fromString(LeServicesNameResolver.SERVICE_HM10));
                if(serviceHM10 != null) {
                    mHM10characteristicRXTX = serviceHM10.getCharacteristic(UUID.fromString(LeServicesNameResolver.CHARACTERISTIC_HM10_RXTX));
                    if(mHM10characteristicRXTX != null) {
                        BluetoothGattDescriptor descriptorConfig = mHM10characteristicRXTX.getDescriptor(UUID.fromString(LeServicesNameResolver.DESCRIPTOR_HM10_CLIENT_CONFIG));
                        if(descriptorConfig != null) {
                            descriptorConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            gatt.writeDescriptor(descriptorConfig);
                        }
                        BluetoothGattDescriptor descriptorUser = mHM10characteristicRXTX.getDescriptor(UUID.fromString(LeServicesNameResolver.DESCRIPTOR_HM10_USER_DESCRIPTION));
                        if(descriptorUser != null) {
                            descriptorUser.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            gatt.writeDescriptor(descriptorUser);
                        }
                        gatt.setCharacteristicNotification(mHM10characteristicRXTX, true);
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                // https://stackoverflow.com/questions/24990061/android-ble-notifications-for-glucose
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
            registerNotifications(gatt);
            Log.w(TAG, "BluetothLeService().onServicesDiscovered()");
        }

        /*
         * Регистрируем уведомления на нужные сервисы HM-10
         */
        protected void registerNotifications(BluetoothGatt gatt)
        {
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services)
            {
                if(service.getUuid().equals(UUID.fromString(LeServicesNameResolver.SERVICE_HM10))) {
                    Log.w("HM-10 Service", service.getUuid().toString() + " " + LeServicesNameResolver.lookupService(service.getUuid().toString(), "Не опознан"));
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        if(characteristic.getUuid().equals(UUID.fromString(LeServicesNameResolver.CHARACTERISTIC_HM10_RXTX))) {
                            Log.w("HM-10 Characteristic", characteristic.getUuid().toString() + " " + LeServicesNameResolver.lookupCharacteristic(characteristic.getUuid().toString(), "Не опознана"));
                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                descriptor.setValue( BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                                Log.w("HM-10 Descriptor", descriptor.getUuid().toString() + " " + LeServicesNameResolver.lookupDescriptor(descriptor.getUuid().toString(), "Не опознан"));
                            }
                            gatt.setCharacteristicNotification(characteristic, true);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onCharacteristicRead()" + characteristic.getStringValue(0));
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else
            {
                Log.w(TAG, "Error (onCharacteristicRead()): " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // Log.w(TAG, "onCharacteristicChanged()");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
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
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        broadcastMessage("connected", 1);
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
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        broadcastMessage("connected", 0);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // Log.w("broadcastUpdate()", LeServicesNameResolver.lookupCharacteristic(characteristic.getUuid().toString(), "Не найдена характеристика"));
        if(characteristic.getUuid().equals(UUID.fromString(LeServicesNameResolver.CHARACTERISTIC_HM10_RXTX)))
        {
            int flag = characteristic.getProperties();
            String strData = characteristic.getStringValue(0);
            readBackParams(strData);
        }

        sendBroadcast(intent);
    }

    private void parseBallCommand(String strCommand) {
        boolean bBackward = false;
        if(strCommand.charAt(0) == 'b') {
            strCommand = strCommand.substring(1);
            bBackward = true;
        }
        String strAction = new String();

        switch (strCommand.charAt(0)) {
            case 'f':
                strAction = bBackward ? "back_fill" : "fill";
                break;
            case 'q':
                strAction = bBackward ? "back_frequency" : "frequency";
                break;
            case 's':
                strAction = bBackward ? "back_sleep" : "sleep";
                break;
            case 'a':
                strAction = bBackward ? "back_state" : "state";
                break;
            default:
                break;
        }

        if(!strAction.isEmpty()) {
            String strParam = strCommand.substring(1);
            int param = Integer.parseInt(strParam);
            broadcastMessage(strAction, param);
        }
    }

    /*
     * Парсим параметры
     */
    private void readBackParams(String strData) {
        // Log.w(TAG, strData + "(" + strData.length() + ")");
        Log.w(TAG, strData);
        String strCommands[] = strData.split("\\s+");
        boolean bBackward = false;

        for(String strCommand : strCommands) {
            // parseBallCommand(strCommand);
            broadcastMessage("reply", strCommand);
        }
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
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
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
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        Log.w(TAG, "readCharacteristic()");
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
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        Log.w(TAG, "setCharacteristicNotification(" + characteristic.getUuid() + ")");
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
        Log.w(TAG, "Trying send characteristic");
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
            // Log.w(TAG, "sendMessage(" + strMSG + ")");
            return mBluetoothGatt.writeCharacteristic(mHM10characteristicRXTX);
        }

        return false;
    }

    /*
     *
     */
    public int getBallFrequency()
    {
        return mBallFrequency;
    }

    /*
     *
     */
    public  int getBallFill()
    {
        return mBallFill;
    }

    public  int getBallSleep()
    {
        return mBallSleep;
    }

    public boolean getBallState()
    {
        return mBallState;
    }
}
