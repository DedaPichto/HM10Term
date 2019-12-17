# HM10Term
Терминал HM10
=============
Пример терминала для отправки/приёма комманд через Bluetooth Terminal cc2541 (HM-10, BT05, e.t.c.).
Сканируемый UUID's устройство(а) можно добавлять в BLENameResolve, в виде констант, а потом дописывать
в фильтр.

Сейчас добавлен единственный UIID 0000ffe0-0000-1000-8000-00805f9b34fb (HM-10). Он же заложен в фильтр поиска, чтобы ускорить обнаружение устройств HM-10

Терминал сделан для обмена данными с DYI-устройствами на основе Arduino

Особенность программы -- запрашивается доступ к Location. Даже не запрашивается, а спрашивается,
есть ли доступ? Если этого не сделать, у ряда устройств не будет доступа к поиску BLE устройств
и надо будет включать старую систему скана (startLeScan)

Не знаю, есть ли ещё какое-то решение. Пока, это -- единственное.

    private void enableLocation()
    {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
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

За решение спасибо https://github.com/kai-morich, с его лучшим терминалом https://github.com/kai-morich/SimpleBluetoothLeTerminal

В частности, у новых Samsung'ов и LG. (см. EnableLocation и эту дискуссию https://github.com/Polidea/RxAndroidBle/issues/106)
 * Motorola Nexus 6, Android 7.0 - не в порядке
 * LGE Nexus 5X, Android 7.0 - не в порядке
 * Samsung S9 SM-G960F - не в порядке

и т.д.

Поддерживается Android с его 4.1-4.3startLeScan()

