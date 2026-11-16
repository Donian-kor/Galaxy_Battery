package com.donian.galaxy_battery.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * SAF 폴더 접근 권한과 마지막으로 고른 폴더 URI를 한 곳에서 관리한다.
 *
 * 앱은 폴더 하나만 읽는다. 그래서 "저장된 URI", "그 URI의 권한이 아직 있는가",
 * "쓸모없어진 권한 정리"가 언제나 함께 움직인다. 세 가지를 한 클래스에 모아 두면
 * 권한 확인과 정리 규칙이 화면 로직과 섞이지 않는다.
 */
class FolderAccessManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private companion object {
        const val KEY_FOLDER_URI = "folder_uri"
        const val READ_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    /** 마지막으로 고른 폴더 URI. 저장된 값이 없으면 null. */
    fun savedFolderUri(): Uri? = prefs.getString(KEY_FOLDER_URI, null)?.toUri()

    /** 폴더 URI를 저장한다. null 이면 저장된 값을 지운다. */
    fun saveFolderUri(uri: Uri?) {
        prefs.edit {
            if (uri == null) remove(KEY_FOLDER_URI) else putString(KEY_FOLDER_URI, uri.toString())
        }
    }

    /**
     * 동기 권한 확인.
     *
     * 메모리 목록 조회라 메인 스레드에서 호출해도 안전하다. 마지막 실행 이후
     * 권한이 회수됐는지 판단할 때 쓴다.
     */
    fun hasPersistedReadPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == uri.toString() && it.isReadPermission }

    /** 폴더 선택 결과로 받은 읽기 권한을 영구 보관한다. */
    fun takePersistableReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, READ_FLAGS)
        } catch (_: SecurityException) {}
    }

    /**
     * 앱은 언제나 폴더 하나만 읽으므로, 다른 권한 부여는 거절되었거나 교체된 선택의 잔여물이다.
     * Android는 앱이 더 이상 쓰지 않아도 해제하기 전까지 유지한다.
     */
    fun releaseUnusedGrants(keep: Uri?) {
        val resolver = context.contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri != keep }
            .forEach {
                try {
                    resolver.releasePersistableUriPermission(it.uri, READ_FLAGS)
                } catch (_: SecurityException) {}
            }
    }
}
