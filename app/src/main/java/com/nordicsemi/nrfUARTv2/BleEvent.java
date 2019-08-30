package com.nordicsemi.nrfUARTv2;

/**
 * Project :  Android-nRF-UART-rajtrilo-master.
 * Package name: com.nordicsemi.nrfUARTv2
 * Created by :  dabin.
 * Created time: 2019/8/31 0:14
 * Changed by :  dabin.
 * Changed time: 2019/8/31 0:14
 * Class description:
 */
public class BleEvent {
    public final static String ACTION_GATT_CONNECTED =
            "CN.DABIN.BLE.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "CN.DABIN.BLE.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "CN.DABIN.BLE.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "CN.DABIN.BLE.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "CN.DABIN.BLE.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "CN.DABIN.BLE.DEVICE_DOES_NOT_SUPPORT_UART";
    private String currentEvent;
    private byte[] value;

    public BleEvent(String currentEvent) {
        this.currentEvent = currentEvent;
    }

    public BleEvent(String currentEvent, byte[] value) {
        this.currentEvent = currentEvent;
        this.value = value;
    }

    public String getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(String currentEvent) {
        this.currentEvent = currentEvent;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }
}
