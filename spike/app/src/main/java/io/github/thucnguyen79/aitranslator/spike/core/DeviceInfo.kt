package io.github.thucnguyen79.aitranslator.spike.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfo {
    fun json(context: Context): JSONObject {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("soc", if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("cpuCores", Runtime.getRuntime().availableProcessors())
            .put("ramGb", "%.1f".format(Locale.US, mem.totalMem / 1e9))
    }

    fun summary(context: Context): String {
        val d = json(context)
        return "${d.getString("manufacturer")} ${d.getString("model")} · ${d.getString("soc")} · " +
            "Android ${d.getString("android")} · ${d.getInt("cpuCores")} nhân · RAM ${d.getString("ramGb")} GB"
    }

    /** Proportional set size of this process in MB (includes native model memory). */
    fun pssMb(): Double = Debug.getPss() / 1024.0

    fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun writeResult(dir: File, prefix: String, json: JSONObject): File {
        dir.mkdirs()
        return File(dir, "$prefix-${stamp()}.json").also { it.writeText(json.toString(2)) }
    }
}
