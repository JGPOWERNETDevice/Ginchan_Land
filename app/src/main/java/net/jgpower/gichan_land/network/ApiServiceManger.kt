package net.jgpower.gichan_land.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiServiceManager {

    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    lateinit var apiService: ApiService
        private set


    fun init(context: Context) {
        val baseUrl = ServerConfig.getBaseHttpUrl(context)

        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
        }
    }
}
