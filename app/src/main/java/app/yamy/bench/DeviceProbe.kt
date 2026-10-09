package app.yamy.bench

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug

/** Sondes matérielles sans permission : température de la batterie, mémoire native. */
class DeviceProbe(private val context: Context) {
    fun batteryTempC(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tenths < 0) Float.NaN else tenths / 10f
    }

    fun nativeHeapMb(): Long = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
}
