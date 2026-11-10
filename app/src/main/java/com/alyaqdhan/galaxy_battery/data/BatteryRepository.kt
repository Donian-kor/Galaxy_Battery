package com.alyaqdhan.galaxy_battery.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.alyaqdhan.galaxy_battery.BatteryCache
import com.alyaqdhan.galaxy_battery.BatteryInfo
import com.alyaqdhan.galaxy_battery.BatteryParser
import com.alyaqdhan.galaxy_battery.DocEntry

/**
 * Owns all Android/SAF/SharedPreferences interaction for battery-log data.
 *
 * [BatteryParser] stays a pure parser (no Context/SAF). This repository is the
 * single place that talks to the content resolver and the prefs store, so the
 * parser can be unit-tested without an Android environment.
 */
class BatteryRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    // ── Folder / file helpers ─────────────────────────────────────────────────

    fun hasPersistedPermission(folderUri: Uri): Boolean {
        val uriStr = folderUri.toString()
        if (context.contentResolver.persistedUriPermissions
                .any { it.uri.toString() == uriStr && it.isReadPermission }) return true
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.count >= 0 } ?: false
        } catch (_: Exception) { false }
    }

    fun listChildren(folderUri: Uri, parentId: String): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<DocEntry>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: ""
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val lastMod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    result += DocEntry(id, name, mime, lastMod)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun findLatestLogEntry(folderUri: Uri): DocEntry? {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel = listChildren(folderUri, treeDocId)
        val dirMime = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { BatteryParser.isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id).filter { BatteryParser.isDumpStateLog(it.name) })
            }
        }
        return allLogs.maxByOrNull {
            BatteryParser.extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified
        }
    }

    fun listAllLogs(folderUri: Uri): List<DocEntry> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel = listChildren(folderUri, treeDocId)
        val dirMime = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { BatteryParser.isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id).filter { BatteryParser.isDumpStateLog(it.name) })
            }
        }
        return allLogs.sortedByDescending {
            BatteryParser.extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified
        }
    }

    // ── Log parsing ───────────────────────────────────────────────────────────

    fun parseLogEntry(folderUri: Uri, entry: DocEntry): BatteryInfo {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                BatteryParser.parseInputStream(stream, entry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName = entry.name,
                errorMessage = "Could not open ${entry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName = entry.name,
                errorMessage = "Read error: ${e.localizedMessage}"
            )
        }
    }

    fun parseLatestLog(folderUri: Uri): BatteryInfo {
        val logEntry = findLatestLogEntry(folderUri)
            ?: return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, logEntry.id)
        val info = try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                BatteryParser.parseInputStream(stream, logEntry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName = logEntry.name,
                errorMessage = "Could not open ${logEntry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName = logEntry.name,
                errorMessage = "Read error: ${e.localizedMessage}"
            )
        }

        BatteryCache.cacheBatteryInfo(prefs, info)
        return info
    }

    fun smartScan(folderUri: Uri): BatteryInfo {
        val latestEntry = findLatestLogEntry(folderUri)
        if (latestEntry == null) {
            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
            if (cached != null) return cached
            return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")
        }
        val cached = BatteryCache.loadCachedBatteryInfo(prefs)
        if (cached != null && cached.logFileName == latestEntry.name) return cached
        return parseLatestLog(folderUri)
    }

    fun extractBatterySection(folderUri: Uri, entry: DocEntry): String {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                BatteryParser.readBatterySections(stream) ?: context.getString(com.alyaqdhan.galaxy_battery.R.string.raw_no_sections)
            } ?: context.getString(com.alyaqdhan.galaxy_battery.R.string.raw_open_failed)
        } catch (e: Exception) {
            context.getString(com.alyaqdhan.galaxy_battery.R.string.raw_read_error, e.localizedMessage)
        }
    }

    // ── Cache passthrough ─────────────────────────────────────────────────────

    fun loadCachedBatteryInfo(): BatteryInfo? = BatteryCache.loadCachedBatteryInfo(prefs)

    fun cacheBatteryInfo(info: BatteryInfo) = BatteryCache.cacheBatteryInfo(prefs, info)

    fun clearBatteryInfoCache() = BatteryCache.clearBatteryInfoCache(prefs)
}