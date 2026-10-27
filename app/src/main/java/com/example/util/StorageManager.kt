package com.example.util

import android.content.Context
import com.example.ai.tier1.AiModelManager
import java.io.File

data class StorageReport(
    val baseAppMb: Double,
    val databaseMb: Double,
    val modelsMb: Double,
    val cacheMb: Double,
    val totalUsedMb: Double,
    val maxBudgetMb: Double = 500.0,
    val freeBudgetMb: Double
)

object StorageManager {

    fun getStorageReport(context: Context): StorageReport {
        // Base APK size
        val apkFile = File(context.applicationInfo.sourceDir)
        val apkBytes = if (apkFile.exists()) apkFile.length() else 20L * 1024 * 1024
        val apkMb = apkBytes.toDouble() / (1024 * 1024)

        // Database size in data/data/
        var dbBytes = 0L
        val dbFile = context.getDatabasePath("korvi_dictionary_db")
        if (dbFile.exists()) {
            dbBytes += dbFile.length()
        }
        dbFile.parentFile?.let { parent ->
            val dbWal = File(parent, "korvi_dictionary_db-wal")
            if (dbWal.exists()) dbBytes += dbWal.length()
            val dbShm = File(parent, "korvi_dictionary_db-shm")
            if (dbShm.exists()) dbBytes += dbShm.length()
        }

        // If not initialized yet, account for assets DB size (~19.4MB)
        if (dbBytes < 1024 * 1024) {
            dbBytes = 20L * 1024 * 1024
        }
        val dbMb = dbBytes.toDouble() / (1024 * 1024)

        // On-device AI Models size in files/models/
        val modelsMb = AiModelManager.getTotalModelsSizeMb(context)

        // Cache size
        val cacheBytes = getFolderSize(context.cacheDir)
        val cacheMb = cacheBytes.toDouble() / (1024 * 1024)

        val totalUsedMb = apkMb + dbMb + modelsMb + cacheMb
        val freeBudgetMb = maxOf(0.0, 500.0 - totalUsedMb)

        return StorageReport(
            baseAppMb = (apkMb * 10).toInt() / 10.0,
            databaseMb = (dbMb * 10).toInt() / 10.0,
            modelsMb = modelsMb,
            cacheMb = (cacheMb * 10).toInt() / 10.0,
            totalUsedMb = (totalUsedMb * 10).toInt() / 10.0,
            maxBudgetMb = 500.0,
            freeBudgetMb = (freeBudgetMb * 10).toInt() / 10.0
        )
    }

    fun cleanCache(context: Context): Long {
        var deletedBytes = 0L
        val cacheDir = context.cacheDir
        if (cacheDir.isDirectory) {
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    deletedBytes += file.length()
                    file.deleteRecursively()
                }
            }
        }
        return deletedBytes
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) {
                    size += if (f.isDirectory) getFolderSize(f) else f.length()
                }
            }
        } else {
            size = dir.length()
        }
        return size
    }
}
