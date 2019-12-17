package com.rqd.hm10term;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
// import android.support.v7.app.AppCompatActivity;
import android.annotation.TargetApi;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanSettings;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
//import android.support.v4.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private String LOG_TAG = MainActivity.class.getName();
    private enum ScanState { NONE, LESCAN, DISCOVERY, DISCOVERY_FINISHED }
    private ScanState scanState = ScanState.NONE;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_MY_PERMISSION = 1;
    // Stops scanning after 5 seconds.
    private static final long SCAN_PERIOD = 5000;
    private boolean mScanning;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private ListView mLVDevices;
    private ProgressBar mPBScan;
    // Сканнеры. Старый и новый.
    private OldLeScanCallback mOldLeScanCallback;
    private NewLeScanCallback mNewLeScanCallback;

    /** Обратный вызов для устаревших версий Андроида 4.3 -//- 4.4.
     * Работает медленно и иногда "теряет" устройства.
     * Иногда приходится сканировать устройства несколько раз
     */
    private class OldLeScanCallback implements BluetoothAdapter.LeScanCallback {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,  byte[] scanRecord) {
            // https://developer.android.com/reference/java/lang/Runnable
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.w("LeScan", "LeScan");
                    if(device != null) {
                        Log.w(LOG_TAG, "Find device " + device.getName() + "(" + device.getAddress()+ ")");
                        mLeDeviceListAdapter.addDevice(device);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }

    /** Это обновлённая версия сканнера BLE-устройств, начиная с 5.0
     * LOLLIPOP.
     * https://developer.android.com/reference/android/bluetooth/le/ScanCallback
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class NewLeScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            // int rssi = result.getRssi();
            // ScanRecord scanRecord = result.getScanRecord();
            // byte[] record = scanRecord.getBytes();
            Log.w(LOG_TAG, "Find device " + device.getName() + "(" + device.getAddress()+ ")");
            mLeDeviceListAdapter.addDevice(device);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.w("onBatchScanResults", "Вызов");
            for(ScanResult result : results) {
                BluetoothDevice btDevice = result.getDevice();
                // int rssi = result.getRssi();
                // ScanRecord scanRecord = result.getScanRecord();
                // byte[] record = scanRecord.getBytes();
                mLeDeviceListAdapter.addDevice(btDevice);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
            // Вызываем родительский метод
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(LOG_TAG, "Error scan BLE: " + errorCode);
            super.onScanFailed(errorCode);
        }
    }

    /** Функция включает Location
     * На новых телефонах без неё не будет работать BLE-поиск
     * Начиная с Android 6.0 блютуз сканнер требует ACCESS_COARSE_LOCATION. Проблема в том, что
     * в https://developer.android.com/guide/topics/connectivity/bluetooth-le
     * ничего нет про то, что надо ещё подключить Location. Почему и зачем нужно
     * включить Location -- неведомо. Однако, её надо хотя бы запросить. Иначе,
     * некоторые модели телефонов наотрез отказываются сканировать BLE-устройства
     * Причём, под Location понимается не только GPS, как «услугой определения местоположения»,
     * но, сетевое обнаружение местоположения. Предполагается, что если не удастся включить
     * Location, будем вызывать старый способ сканирования (4.1 -//- 4.3)
     * Пока, не реализовано.
     * Заимствовано из примера
     * https://www.javatips.net/api/intro-to-ble-master/android_ble/app/src/main/java/com/yeokm1/bleintro/BLEHandler.java
     */
    private void enableLocation()
    {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);// getActivity().getSystemService(Context.LOCATION_SERVICE);
        boolean  locationEnabled = false;
        try {
            locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ignored) {}
        try {
            locationEnabled |= locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception ignored) {}
        if(!locationEnabled)
            scanState = ScanState.DISCOVERY;
    }

    /** сканироване BLE устройств
     * с учётом, того, какая версия Android установлена "на борту" телефона
     */
    private void scanLeDevice(boolean enable) {
        Log.w(LOG_TAG, "ScanLeDevices");
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        checkPermission(Manifest.permission.BLUETOOTH);
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN);
        /* Вызываем эту функцию, просто, чтобы активировать Позиционирование
         * Если этого не сделать, некоторые модели телефонов откажутся сканировать
         * BLE-устройства
         */
        enableLocation();
        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        /* Определяем, какая версия Андроида стоит "на борту". Если старше
         * 5.1 -- LOLLIPOP, вызываем "новую" библиотеку сканирования BLE
         * Суть в том, что мы формируем Создаём объект ScanCallback, а потом вызываем при помощи
         * mBluetoothAdapter.getBluetoothLeScanner()
         * bluetoothLeScanner.stopScan(mNewLeScanCallback); или
         * bluetoothLeScanner.startScan(mNewLeScanCallback);
         * Не забываем создать объекты mOldLeScanCallBack или mNewLeScanCallBack
         */
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (enable) {
                /* Если mNewLeScanCallback не определён, создаём объект */
                if(mNewLeScanCallback == null) {
                    mNewLeScanCallback = new NewLeScanCallback();
                }

                /* Если процесс сканирования был запущен, останавливаем его*/
                if (mScanning) {
                    bluetoothLeScanner.stopScan(mNewLeScanCallback);
                    mScanning = false;
                }

                /*
                 * Определяем список фильтров сканирования. Пока, он только один --
                 * UUID Bluetooth cc2541
                 * http://www.ti.com/lit/ds/symlink/cc2541.pdf
                 * https://developer.android.com/reference/android/bluetooth/le/ScanFilter
                 */
                List<ScanFilter> filters = new ArrayList<ScanFilter>();
                ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString(BLENameResolver.SERVICE_HM10))).build();
                filters.add(filter);
                // https://developer.android.com/reference/android/bluetooth/le/ScanSettings
                // ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT).build();
                ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                // Остановить сканирование по истечении SCAN_PERIOD. Время "сна" не учитывается
                // https://developer.android.com/reference/java/lang/Runnable
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        bluetoothLeScanner.stopScan(mNewLeScanCallback);
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                // https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner
                bluetoothLeScanner.startScan(filters, settings, mNewLeScanCallback);
            } else {
                // Прекратить сканирование, если mScanning установлено в значение false
                bluetoothLeScanner.stopScan(mNewLeScanCallback);
                mScanning = false;
            }
        } else {
            if(mOldLeScanCallback == null) {
                mOldLeScanCallback = new OldLeScanCallback();
            }
            UUID [] uuids = new UUID[1];
            uuids [0] = UUID.fromString(BLENameResolver.SERVICE_HM10);
            if(enable) {
                // Если сканирование уже запущено, останавливаем его
                if(mScanning) {
                    mBluetoothAdapter.stopLeScan(mOldLeScanCallback);
                }
                // Вызов со своеобразным "фильтром"
                mBluetoothAdapter.startLeScan(uuids, mOldLeScanCallback);
                mScanning = true;
            } else {
                mBluetoothAdapter.stopLeScan(mOldLeScanCallback);
                mScanning = false;
            }
        }

        /* Обновляем меню: если запущен процесс сканирования, убираем меню scan
         * Если процесс сканирования остановлен, убираем меню stop и включаем stop
         * Этот вызов обрабатывается в onCreateOptionsMenu()
         */
        invalidateOptionsMenu();
    }

    /** Запросить нужные разрешения для работы BLE-Сканнера
     *
     * TODO: Исправить краш при отказе от предоставления разрешений на использование Bluetooth
     */
    private boolean checkPermission(String permission)
    {
        Log.w(LOG_TAG, "Permission = " + permission);
        if ( ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED )
        {
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{permission},
                    ACCESS_MY_PERMISSION
            );

            return (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED);
        }

        return true;
    }

    /** Адаптер ListView.
     * http://startandroid.ru/ru/uroki/vse-uroki-spiskom/113-urok-54-kastomizatsija-spiska-sozdaem-svoj-adapter.html
     * Штука удобная -- его можно заполнять различными activity. Главное, переопределить набор
     * обязательных методов. В этом же классе храним список добавленных устройств.
     * Громоздко, но так проще...
     * См. https://developer.android.com/reference/android/widget/BaseAdapter
     */
    class LeDeviceListAdapter extends BaseAdapter {
        public ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;
        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(mLeDevices.contains(device)) {
                Log.w(LOG_TAG, "Device already exists: " + device.getAddress() + " " + device.getName() + ", " + mLeDevices.size());
                if(mLeDevices.size() > 0) {
                    Log.w("mLeDevices", mLeDevices.get(0).getAddress() + " " + mLeDevices.get(0).getName());
                }
            } else {
                mLeDevices.add(device);
                Log.w("LeDeviceListAdapter", "Add " + device.getName() + " " + device.getAddress());
            }
        }

        public void clear() { mLeDevices.clear(); }

        @Override
        public int getCount() { return mLeDevices.size(); }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        /** Здесь и происходит "привязывание" соответствующего XML-описания
         * активности. В данном случае, это device_item (см. ./res/layout/device_item
         * Из структуры вызова видно, что можно заполнять список разными шаблонами
         * отображения, что может быть очень удобно.
         * @param i
         * @param cView
         * @param viewGroup
         * @return
         */
        @Override
        public View getView(int i, View cView, ViewGroup viewGroup) {
            View view = cView;
            if (view == null) {
                view = mInflator.inflate(R.layout.device_item, viewGroup, false);
            }

            BluetoothDevice device = mLeDevices.get(i);
            TextView tvDeviceName = (TextView) view.findViewById(R.id.TVDeviceName);
            TextView tvDeviceAddress = (TextView) view.findViewById(R.id.TVDeviceAddress);
            String strDeviceName = device.getName();
            if(strDeviceName != null && strDeviceName.length() > 0)
                tvDeviceName.setText(device.getName());
            else
                tvDeviceName.setText(R.string.unknown_device);
            tvDeviceAddress.setText(device.getAddress());

            return view;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        menu.findItem(R.id.menu_scan).setVisible(!mScanning);
        menu.findItem(R.id.menu_stop).setVisible(mScanning);
        mPBScan.setVisibility(mScanning ? View.VISIBLE : View.INVISIBLE);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Без вызовов onPause() & onResume() при повороте экрана или "засыпании"
     * В процессе сканирования вы будете получать ошибку обращения к нулевому
     * объекту.
     */
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    /** Без вызовов onPause() & onResume() при повороте экрана или "засыпании"
     * В процессе сканирования вы будете получать ошибку обращения к нулевому
     * объекту.
     */
    @Override
    protected void onResume() {
        mScanning = true;
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        mLVDevices.setAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    /* "Слушаем" клик по элементу в списке. Если кто-то кликнут, переходим в следующую
     * активность и пытаемся подключиться к выбранному устройству
     * https://stackoverflow.com/questions/4709870/setonitemclicklistener-on-custom-listview
     * http://startandroid.ru/ru/uroki/vse-uroki-spiskom/85-urok-44-sobytija-v-listview.html
     */
    private ListView.OnItemClickListener mLVItemClick = new ListView.OnItemClickListener() {
        @Override
        public void onItemClick (AdapterView< ? > adapter, View view, int position, long arg){
            BluetoothDevice device = (BluetoothDevice) mLeDeviceListAdapter.getItem(position);
            final Intent intent = new Intent(MainActivity.this, TermActivity.class);
            /* Запоминаем имя и адрес выбранного устройства в предстоящий intent
             * при помощи метода putExtra()
             * https://developer.android.com/reference/android/content/Intent
             */
            intent.putExtra("device_name", device.getName());
            intent.putExtra("device_address", device.getAddress());
            if (mScanning) {
                scanLeDevice(false);
            }
            // http://startandroid.ru/ru/uroki/vse-uroki-spiskom/67-urok-28-extras-peredaem-dannye-s-pomoschju-intent.html
            Log.w("Frequency: ", "Device: " + device.getName() + ", Address: " + device.getAddress());
            // Всё. Запускаем следующую активность и пробуем подключиться к устройству
            startActivity(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        checkPermission(Manifest.permission.BLUETOOTH);
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        mLVDevices = findViewById(R.id.LVDevices);
        mLVDevices.setOnItemClickListener(mLVItemClick);
        mPBScan = findViewById(R.id.PBScan);
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        // https://developer.android.com/guide/components/intents-filters?hl=ru
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }
}
