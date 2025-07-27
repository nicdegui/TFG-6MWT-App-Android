package com.example.app6mwt.bluetooth

import java.util.UUID

object BluetoothConstants {
    // Reemplaza estos UUIDs con los correctos para tu BM1000
    val BM1000_SERVICE_UUID: UUID = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    val BM1000_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
