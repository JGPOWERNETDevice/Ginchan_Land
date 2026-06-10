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

    fun getWebSocketUrl(context: Context): String {
        return if (isWifi(context)) {
            LOCAL_WS_URL
        } else {
            PUBLIC_WS_URL
        }
    }

    private fun isWifi(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}