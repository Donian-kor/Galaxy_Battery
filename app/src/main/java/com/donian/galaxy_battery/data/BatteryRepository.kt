package com.donian.galaxy_battery.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.donian.galaxy_battery.BatteryCache
import com.donian.galaxy_battery.BatteryInfo
import com.donian.galaxy_battery.BatteryParser
import com.donian.galaxy_battery.DocEntry

/**
 * SAF/SharedPreferences 및 배터리 로그 데이터 접근을 총괄하는 리포지토리.
 *
 * [BatteryParser]는 Android 의존성 없는 순수 파서로 두고,
 * ContentResolver 및 SharedPreferences 상호작용을 이 리포지토리가 한 곳에서 처리한다.
 */
class BatteryRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private companion object {
        const val TAG = "BatteryRepository"
    }

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
        } catch (e: Exception) {
            Log.w(TAG, "persisted permission check failed for $folderUri", e)
            false
        }
    }

    /**
     * 선택한 폴더 이름이나 URI 경로에 "log" 또는 "sysdump" 단어가 포함되어 있는지 확인한다.
     * 초비자가 엉뚱한 일반 폴더(Download, DCIM 등)를 눌렀을 때 조기 안내하기 위한 스마트 검증용이다.
     */
    fun isLogFolderUri(folderUri: Uri): Boolean {
        val treeDocId = try {
            DocumentsContract.getTreeDocumentId(folderUri)
        } catch (_: Exception) {
            ""
        }
        if (treeDocId.contains("log", ignoreCase = true) || treeDocId.contains("sysdump", ignoreCase = true)) {
            return true
        }

        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: ""
                    name.contains("log", ignoreCase = true) || name.contains("sysdump", ignoreCase = true)
                } else false
            } ?: false
        } catch (_: Exception) {
            false
        }
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
        } catch (e: Exception) {
            Log.w(TAG, "listChildren failed for $parentId in $folderUri", e)
        }
        return result
    }

    fun findLatestLogEntry(folderUri: Uri): DocEntry? {
        val treeDocId = try { DocumentsContract.getTreeDocumentId(folderUri) } catch (_: Exception) { "" }
        val topLevel = listChildren(folderUri, treeDocId)
        val dirMime = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { BatteryParser.isDumpStateLog(it.name) }.toMutableList()

        // 스마트 폴더 검증: 폴더 이름이 'log'가 아니고 최상위에 dumpState_*.log도 없으면
        // 일반 폴더(Download 등)로 판단해 하위 디렉토리를 쓸데없이 탐색하지 않고 null 반환
        val isLogFolder = isLogFolderUri(folderUri)
        if (allLogs.isEmpty() && !isLogFolder) {
            return null
        }

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
        val treeDocId = try { DocumentsContract.getTreeDocumentId(folderUri) } catch (_: Exception) { "" }
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
                healthPercent = null,
                cycleCount = null,
                logFileName = entry.name,
                readSuccess = false,
                errorMessage = "파일을 열 수 없습니다."
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseLogEntry failed for ${entry.name}", e)
            BatteryInfo(
                healthPercent = null,
                cycleCount = null,
                logFileName = entry.name,
                readSuccess = false,
                errorMessage = "파싱 오류: ${e.localizedMessage ?: "알 수 없는 오류"}"
            )
        }
    }

    fun parseLatestLog(folderUri: Uri, latestEntry: DocEntry): BatteryInfo {
        val parsed = parseLogEntry(folderUri, latestEntry)
        if (parsed.readSuccess) cacheBatteryInfo(parsed)
        return parsed
    }

    fun extractBatterySection(folderUri: Uri, entry: DocEntry): String {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                BatteryParser.readBatterySections(stream) ?: "배터리 섹션을 찾을 수 없습니다."
            } ?: "파일을 열 수 없습니다."
        } catch (e: Exception) {
            Log.w(TAG, "extractBatterySection failed for ${entry.name}", e)
            "파일 읽기 오류: ${e.localizedMessage ?: "알 수 없는 오류"}"
        }
    }

    fun smartScan(folderUri: Uri, latestEntry: DocEntry): BatteryInfo =
        parseLatestLog(folderUri, latestEntry)

    // ── Cache operations ──────────────────────────────────────────────────────

    fun cacheBatteryInfo(info: BatteryInfo) {
        BatteryCache.cacheBatteryInfo(prefs, info)
    }

    fun loadCachedBatteryInfo(): BatteryInfo? {
        return BatteryCache.loadCachedBatteryInfo(prefs)
    }

    fun clearBatteryInfoCache() {
        BatteryCache.clearBatteryInfoCache(prefs)
    }
}
