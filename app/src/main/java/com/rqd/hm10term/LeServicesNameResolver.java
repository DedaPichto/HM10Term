package com.rqd.hm10term;

import java.util.HashMap;

/**
 * Created by denis on 25.03.18.
 */

public class LeServicesNameResolver {
    /*
     *   HM-10 Service: 0000ffe0-0000-1000-8000-00805f9b34fb HM-10
     *   HM-10 Characteristic: 0000ffe1-0000-1000-8000-00805f9b34fb HM RX/TX Service
     *   HM-10 Descriptor: 00002902-0000-1000-8000-00805f9b34fb Client Characteristic Configuration
     *   HM-10 Descriptor: 00002901-0000-1000-8000-00805f9b34fb Characteristic User Description
     */
    public static String SERVICE_HM10 = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static String CHARACTERISTIC_HM10_RXTX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String DESCRIPTOR_HM10_CLIENT_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String DESCRIPTOR_HM10_USER_DESCRIPTION = "00002901-0000-1000-8000-00805f9b34fb";

    private static HashMap<String, String> services = new HashMap();
    private static HashMap<String, String> characteristics = new HashMap();
    private static HashMap<String, String> descriptors = new HashMap();

    static {
        services.put("00001800-0000-1000-8000-00805f9b34fb", "Generic Access");
        services.put("00001801-0000-1000-8000-00805f9b34fb", "Generic Attribute");
        services.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information");
        services.put("0000ffe0-0000-1000-8000-00805f9b34fb", "HM-10");

        characteristics.put("00002a05-0000-1000-8000-00805f9b34fb", "Service Changed");
        characteristics.put("00002a23-0000-1000-8000-00805f9b34fb", "System ID");
        characteristics.put("00002a24-0000-1000-8000-00805f9b34fb", "Model Number String");
        characteristics.put("00002a25-0000-1000-8000-00805f9b34fb", "Serial Number String");
        characteristics.put("00002a26-0000-1000-8000-00805f9b34fb", "Firmware Revision String");
        characteristics.put("00002a27-0000-1000-8000-00805f9b34fb", "Hardware Revision String");
        characteristics.put("00002a28-0000-1000-8000-00805f9b34fb", "Software Revision String");
        characteristics.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        characteristics.put("00002a2a-0000-1000-8000-00805f9b34fb", "IEEE 11073-20601 Regulatory Certification Data List");
        characteristics.put("00002a50-0000-1000-8000-00805f9b34fb", "PnP ID");
        characteristics.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Name");
        characteristics.put("00002a01-0000-1000-8000-00805f9b34fb", "Appearance");
        characteristics.put("00002a02-0000-1000-8000-00805f9b34fb", "Peripheral Privacy Flag");
        characteristics.put("00002a03-0000-1000-8000-00805f9b34fb", "Reconnection Address");
        characteristics.put("00002a04-0000-1000-8000-00805f9b34fb", "Peripheral Preferred Connection Parameters");
        characteristics.put("0000ffe1-0000-1000-8000-00805f9b34fb", "HM RX/TX Service");


        descriptors.put("00002902-0000-1000-8000-00805f9b34fb", "Client Characteristic Configuration");
        descriptors.put("00002901-0000-1000-8000-00805f9b34fb", "Characteristic User Description");
        descriptors.put("00002902-0000-1000-8000-00805f9b34fb", "Client Characteristic Configuration");
    }

    //	Find Service
    public static String lookupService(String uuid, String defaultName) {
        String name = services.get(uuid);
        return name == null ? defaultName : name;
    }

    //	Find Characteristic
    public static String lookupCharacteristic(String uuid, String defaultName) {
        String name = characteristics.get(uuid);
        return name == null ? defaultName : name;
    }

    //	Find Descriptor
    public static String lookupDescriptor(String uuid, String defaultName) {
        String name = descriptors.get(uuid);
        return name == null ? defaultName : name;
    }

    // Find in all attributes
    public  static String lookup(String uuid, String defaultName, String type)
    {
        String name = defaultName;
        name = services.get(uuid);
        if(name == null)
        {
            name = characteristics.get(uuid);
            if(name == null)
            {
                name = descriptors.get(uuid);
                if(name == null)
                {
                    return defaultName;
                } else {
                    type = "descriptor";
                    return name;
                }
            } else {
                type = "characteristic";
                return name;
            }
        } else {
            type = "service";
            return name;
        }
    }

}
