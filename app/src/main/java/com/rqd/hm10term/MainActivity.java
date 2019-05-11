package com.rqd.hm10term;

import android.support.v7.app.AppCompatActivity;
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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

public class MainActivity extends AppCompatActivity {
    private String TAG = MainActivity.class.getName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_MY_PERMISSION = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private boolean mScanning;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private ListView mLVDevices;
    private LinearLayout mLLDevices;
    private LinearLayout mLLScan;
    private ProgressBar mPBScan;
    private String mDeviceName;
    private String mDeviceAddress;

    // Обратный вызов сканирования устройств.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    // addDevice(result);
                    // Date dNow = new Date();
                    BluetoothDevice btDevice = result.getDevice();
                    mLeDeviceListAdapter.addDevice(btDevice);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                    // Log.w("onBatchScanResult", result.getDevice().getName() + " " + result.getRssi());
                    // Log.w("onBatchScanResult", String.format("%d",dNow.getTime() - mMoment.getTime()));
                    // mPBSearch.setProgress((int)(dNow.getTime() - mMoment.getTime()) / 100);
                    super.onScanResult(callbackType, result);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.w("onBatchScanResults", "Вызов");
                    // Date dNow = new Date();
                    for(ScanResult result : results) {
                        BluetoothDevice btDevice = result.getDevice();
                        mLeDeviceListAdapter.addDevice(btDevice);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                    // mPBSearch.setProgress((int)(dNow.getTime() - mMoment.getTime()) / 100);
                    // Вызываем родительский метод
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w("onScanFailed", "Error: " + errorCode);
                    super.onScanFailed(errorCode);
                }

            };

    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            // Остановить сканирование по истечении SCAN_PERIOD
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(mLeScanCallback);
                    invalidateOptionsMenu();
                    mLLScan.setVisibility(View.INVISIBLE);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothLeScanner.startScan(mLeScanCallback);
            // mMoment = new Date();

        } else {
            // Прекратить сканирование, если mScanning установлено в значение false
            mScanning = false;
            bluetoothLeScanner.stopScan(mLeScanCallback);
        }

        invalidateOptionsMenu();
    }

    /*
     *   Запросить нужные разрешения для работы BLE-Сканнера
     */
    private boolean checkPermission(String permission)
    {
        Log.w(TAG, "Permission = " + permission);
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

    /*
     * http://startandroid.ru/ru/uroki/vse-uroki-spiskom/113-urok-54-kastomizatsija-spiska-sozdaem-svoj-adapter.html
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
                Log.w("LeDeviceListAdapter", "fail add device " + device.getAddress() + " " + device.getName() + ", " + mLeDevices.size());
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

        @Override
        public View getView(int i, View cView, ViewGroup viewGroup) {
            View view = cView;
            if (view == null) {
                view = mInflator.inflate(R.layout.device_item, viewGroup, false);
            }

            BluetoothDevice device = mLeDevices.get(i);
            TextView tvDeviceName = (TextView) view.findViewById(R.id.tvLabelDeviceName);
            TextView tvDeviceAddress = (TextView) view.findViewById(R.id.tvDeviceAddress);
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
        if (mScanning) {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        } else {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                if(mScanning) {
                    mScanning = false;
                    scanLeDevice(false);
                } else {
                    scanLeDevice(true);
                }
                mLLScan.setVisibility(View.VISIBLE);
                return super.onOptionsItemSelected(item);
            case R.id.menu_stop:
                if(mScanning) {
                    mScanning = false;
                    scanLeDevice(false);
                    mLLScan.setVisibility(View.INVISIBLE);
                }
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }

        // return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onResume() {
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

    /*
     * Слушаем выбор элементов
     * https://stackoverflow.com/questions/4709870/setonitemclicklistener-on-custom-listview
     * http://startandroid.ru/ru/uroki/vse-uroki-spiskom/85-urok-44-sobytija-v-listview.html
     */
    private ListView.OnItemClickListener mLVItemClick = new ListView.OnItemClickListener() {
        @Override
        public void onItemClick (AdapterView< ? > adapter, View view, int position, long arg){
            BluetoothDevice device = (BluetoothDevice) mLeDeviceListAdapter.getItem(position);
            final Intent intent = new Intent(MainActivity.this, BallActivity.class);
            intent.putExtra("device_name", device.getName());
            intent.putExtra("device_address", device.getAddress());
            if (mScanning) {
                // mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanning = false;
            }
            // http://startandroid.ru/ru/uroki/vse-uroki-spiskom/67-urok-28-extras-peredaem-dannye-s-pomoschju-intent.html
            mDeviceName = intent.getStringExtra("device_name");
            mDeviceAddress = intent.getStringExtra("device_address");
            Log.w("Frequency: ", "Device: " + device.getName() + ", Address: " + device.getAddress());
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
        mLVDevices = findViewById(R.id.lvDeviceList);
        mLVDevices.setOnItemClickListener(mLVItemClick);
        mLLDevices = findViewById(R.id.llDevicesList);
        mLLScan = findViewById(R.id.llProgressBar);
        mPBScan = findViewById(R.id.pbScan);
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

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }
}
