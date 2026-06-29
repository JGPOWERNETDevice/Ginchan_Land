package net.jgpower.gichan_land.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ServerConfig {

    private const val LOCAL_HTTP_URL = "http://192.168.100.32:1880/"
    private const val PUBLIC_HTTP_URL = "http://58.77.65.29:1880/"

    private const val LOCAL_WS_URL = "ws://192.168.100.32:1880/ws/app"
    private const val PUBLIC_WS_URL = "ws://58.77.65.29:1880/ws/app"

    fun getBaseHttpUrl(context: Context): String {
        return if (isWifi(context)) {
            LOCAL_HTTP_URL
        } else {
            PUBLIC_HTTP_URL
        }
    }

    fun getLocalBaseHttpUrl(): String {
        return LOCAL_HTTP_URL
    }

    fun getPublicBaseHttpUrl(): String {
        return PUBLIC_HTTP_URL
    }

    fun getWebSocketUrl(context: Context): String {
        return if (isWifi(context)) {
            LOCAL_WS_URL
        } else {
            PUBLIC_WS_URL
        }
    }

    // 무전기 음성 통신은 내부망 Wi-Fi에서만 허용합니다.
    // LTE/5G 또는 Wi-Fi 미연결 상태에서는 공인 Node-RED 주소로 신호 서버가 연결되어도
    // UDP 음성 통신이 정상 동작하지 않으므로 무전기 기능을 차단합니다.
    fun isWalkieNetworkAvailable(context: Context): Boolean {
        return isWifi(context)
    }

    fun getWalkieBaseHttpUrl(context: Context): String? {
        return if (isWalkieNetworkAvailable(context)) LOCAL_HTTP_URL else null
    }

    fun walkieNetworkErrorMessage(): String {
        return "무전기는 내부 Wi-Fi 연결 상태에서만 사용할 수 있습니다. 네트워크를 확인하세요."
    }

    private fun isWifi(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
