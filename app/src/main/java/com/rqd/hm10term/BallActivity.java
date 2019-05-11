package com.rqd.hm10term;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BallActivity extends AppCompatActivity {
    // Тэг для отметки вывода отладки
    protected String LOG_TAG = BallActivity.class.getName();
    private static final int ACCESS_MY_PERMISSION = 1;
    // Имя выбранного устройства
    private String mDeviceName = new String();
    // Адрес выбранного устройства
    private String mDeviceAddress = new String();
    // Экземпляр класса com.rqd.denis.ballrqd.BluetoothLeService
    private LeService mBluetoothLeService;
    // Характеристика для передачи данных на устройство
    private BluetoothGattCharacteristic mCharacteristicTX;
    // Состояние подключения
    private boolean mConnected;
    // Включён шарик или выключен
    private boolean mEnabled;
    // Название устройства
    private TextView tvDeviceName = null;
    // Адрес устройства
    private TextView tvDeviceAddress = null;
    // Строка к отправке
    private EditText etSend = null;
    // Кнопка отправки данных
    private Button btnSend = null;
    // Лог обмена данными
    private EditText etLog = null;
    // Уровень зарядки
    private TextView tvPower = null;
    // Слой с SeekBar'ом, отображающим процесс подключения к устройству
    private LinearLayout llConnect = null;

    // Код, управляющий жизненным циклом Сервиса
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.w(LOG_TAG, "Получаем mBluetoothLeService");
            mBluetoothLeService = ((LeService.LocalBinder) service).getService();
            if(mBluetoothLeService != null) {
                if (!mBluetoothLeService.initialize()) {
                    Log.e(LOG_TAG, "Не могу инициализировать сервис Bluetooth");
                    finish();
                }
                // Автоматически подключаемся к устройству, если инициализация прошла успешно
                Log.w(LOG_TAG, "Подключаемся к " + mDeviceAddress);
                mBluetoothLeService.connect(mDeviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Инициализация глобальных переменных
    private void initGlobalVariables() {
        Intent intent = getIntent();
        mDeviceName = getString(R.string.default_device_name);
        mDeviceAddress = getString(R.string.default_device_address);
        mDeviceName = intent.getStringExtra("device_name");
        mDeviceAddress = intent.getStringExtra("device_address");
        mConnected = false;
        mEnabled = false;
        tvDeviceName = findViewById(R.id.tvDeviceName);
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress);
        etSend = findViewById(R.id.etSend);
        btnSend = findViewById(R.id.btnSend);
        etLog = findViewById(R.id.etLog);
        llConnect = findViewById(R.id.llConnect);
        tvPower = findViewById(R.id.tvPower);

        /** Отправляем команду из строки etSend
         *
         */
        Button.OnClickListener onSend = new Button.OnClickListener() {
            public void onClick(View btnView) {
                String strCommand = etSend.getText().toString();
                if(strCommand != null && !strCommand.isEmpty()) {
                    if(mBluetoothLeService.sendMessage(strCommand + "\r\n")) {
                        Log.w(LOG_TAG, String.format("Send command: %s", strCommand));
//                      Pattern p = Pattern.compile("p(\d+)");
//                      Matcher m = p.matcher(strCommand);
                        etLog.append(strCommand + "\n");
                        etSend.setText("");
                    }
                }
            }
        };
        btnSend.setOnClickListener(onSend);
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // Log.w("BroadCastReceiver ",  action);
            // Log.w(LOG_TAG, "BroadcastReceiver(): " + action);
            if (LeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState();
                invalidateOptionsMenu();
                Log.w(LOG_TAG, "Connected to " + mDeviceAddress );
                etSend.setEnabled(true);
                btnSend.setEnabled(true);
                llConnect.setVisibility(View.INVISIBLE);
            } else if (LeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState();
                invalidateOptionsMenu();
                etSend.setEnabled(false);
                btnSend.setEnabled(false);
                llConnect.setVisibility(View.VISIBLE);
            } else if (LeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                // displayGattServices(mBluetoothLeService.getSupportedGattServices());
                // getParams();
                Log.w(LOG_TAG, "Сервисы прочитаны");
                requestCurrentActions();
            } else if (LeService.ACTION_DATA_AVAILABLE.equals(action)) {
                // Log.w(LOG_TAG"DATA_AVAILABLE " + intent.getStringExtra(com.rqd.denis.ballrqd.BluetoothLeService.EXTRA_DATA));
                // displayData(intent.getStringExtra(com.rqd.denis.ballrqd.BluetoothLeService.EXTRA_DATA));

            } else if (LeService.EXTRA_DATA.equals(action))
            {
                // Log.w(LOG_TAG"EXTRA_DATA");
                String name = intent.getStringExtra(LeService.PARAM_NAME);
                // int value = -1;
                // value = intent.getIntExtra(BluetoothLeService.PARAM_VALUE, -1);
                String value = intent.getStringExtra(LeService.PARAM_VALUE);
                Log.w("BroadcastReceiver", "Name: " + name + ", Value: " + value);
                if(name.equals("reply")) {
                    if(value.charAt(0) == 'p') {
                        tvPower.setText(value.substring(1));
                    } else {
                        etLog.append(value + "\n");
                    }
                }
//                if(name.equals("frequency")) {
//                    // mSBFreq.setProgress(value);
//                } else if(name.equals("fill")) {
//                    // mSBFill.setProgress(value);
//                } else if(name.equals("sleep")) {
//                    // mSBSleep.setProgress(value);
//                } else if(name.equals("state")) {
//                    mEnabled = value == 0 ? false : true;
//                    changeBtnState();
//                } else if(name.equals("connected")) {
//                    Log.w("BroadCastReceiver", "Name: " + name + ", Value: " + value);
//                    // mDeviceConnected = (value == 0) ? false : true;
//                    updateConnectionState();
//                }
            }
        }
    };


    public void sendRequest(View view) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 100ms
                if(mBluetoothLeService != null) {
                    if(mBluetoothLeService.sendMessage("gq\r\n")) {
                        // etLog.append();
                    }
                }
            }
        }, 1000);
    }

    /*
     *
     */
    public void updateConnectionState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mConnected) {
                    tvDeviceName.setText(mDeviceName);
                    tvDeviceAddress.setText(mDeviceAddress);
                    tvDeviceName.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorGreen));
                    tvDeviceAddress.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorGreen));
                } else {
                    tvDeviceName.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDark));
                    tvDeviceAddress.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDark));
                }
            }
        });
    }

    /*
     * http://developer.alexanderklimov.ru/android/views/seekbar.php
     */
    private SeekBar.OnSeekBarChangeListener sbFreqChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.w("Frequency: ", "progress: " + progress);
            if(mBluetoothLeService != null) {
                mBluetoothLeService.sendMessage("sq" + progress + "\n");
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };


    private void changeBtnState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mEnabled) {
                } else {
                }
            }
        });
    }

    /*
     *   Запрашиваем нужные разрешения для работы BLE-Сканнера
     */
    private boolean checkPermission(String permission)
    {
        Log.w(LOG_TAG, "Permission = " + permission);
        if ( ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED )
        {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permission},
                    ACCESS_MY_PERMISSION
            );

            return (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED);
        }

        return true;
    }

    /*
     * Запрашиваем у шарика текущие состояния переменных
     */
    public void requestCurrentActions() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 100ms
                if(mBluetoothLeService != null) {
                    mBluetoothLeService.sendMessage("gf\r\n");
                }
            }
        }, 1000);
    }

    /*
     * Устанавливаем фильтр
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(LeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(LeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(LeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(LeService.ACTION_FILL);
        intentFilter.addAction(LeService.EXTRA_DATA);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ball);
        // Запрашиваем нужные разрешения доступа к BLUETOOTH и Геопозиционированию
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        checkPermission(Manifest.permission.BLUETOOTH);
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN);
        initGlobalVariables();
        Intent gattServiceIntent = new Intent(this, LeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Запрашиваем нужные разрешения доступа к BLUETOOTH и Геопозиционированию
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        checkPermission(Manifest.permission.BLUETOOTH);
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN);

        Intent intent = registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(LOG_TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_ball, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                Log.w("Connecting", "Пробуем подключиться к " + mDeviceAddress + " " + mDeviceName);
                if(mBluetoothLeService != null) {
                    mBluetoothLeService.connect(mDeviceAddress);
                }
                return true;
            case R.id.menu_disconnect:
                if(mBluetoothLeService != null) {
                    mBluetoothLeService.disconnect();
                }
                return true;
            case R.id.menu_scan:
            {
                final Intent intent = new Intent(BallActivity.this, MainActivity.class);
                {
                    mBluetoothLeService.disconnect();
                    unbindService(mServiceConnection);
                    mBluetoothLeService = null;
                }
                startActivity(intent);
            }
            return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
