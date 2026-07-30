package com.vatradar.app.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.vatradar.app.di.NetworkModule

/**
 * VATSIM Connect 로그인 (앱 쪽).
 *
 * 앱은 OAuth를 직접 하지 않습니다. VATSIM Connect가 아직 PKCE를 지원하지 않아
 * client_secret이 필요한데, APK에 넣은 시크릿은 누구나 꺼낼 수 있어 시크릿이
 * 아니기 때문입니다. 그래서 Worker가 대신 OAuth 클라이언트가 되고,
 * 앱은 서버가 발급한 불투명 토큰만 받아 둡니다.
 *
 * 앱이 하는 일은 두 가지뿐입니다:
 *   1. 브라우저로 서버의 /auth/start 를 연다
 *   2. vatradar://auth?token=...&cid=... 로 돌아온 값을 저장한다
 */
object VatsimConnect {

    /**
     * 로그인 화면을 엽니다.
     *
     * WebView가 아니라 Custom Tab을 쓰는 이유:
     * WebView 안에서 남의 로그인 폼을 띄우면 앱이 비밀번호를 가로챌 수 있는 구조가 되고,
     * 사용자는 주소창이 없어 진짜 VATSIM인지 확인할 방법이 없습니다.
     * OAuth 명세(RFC 8252)도 같은 이유로 WebView를 금지합니다.
     */
    fun launch(context: Context) {
        val url = Uri.parse(NetworkModule.WATCH_URL).buildUpon()
            .appendEncodedPath("auth/start")
            .build()

        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, url)
        } catch (e: ActivityNotFoundException) {
            // Custom Tab을 지원하는 브라우저가 없으면 아무 브라우저로 넘깁니다.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, url))
            }.onFailure { Log.w("VATRadar", "브라우저를 열 수 없습니다", e) }
        }
    }

    /** 로그인 성공으로 돌아온 딥링크에서 CID와 토큰을 꺼냅니다. */
    fun parseCallback(uri: Uri?): Link? {
        if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
        val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
        val cid = uri.getQueryParameter("cid")?.takeIf { it.isNotBlank() } ?: return null
        return Link(cid = cid, token = token)
    }

    data class Link(val cid: String, val token: String)

    private const val SCHEME = "vatradar"
    private const val HOST = "auth"
}
