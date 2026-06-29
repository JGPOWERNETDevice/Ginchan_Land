package net.jgpower.gichan_land.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiServiceManager {

    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var localRetrofit: Retrofit? = null
    private var publicRetrofit: Retrofit? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    lateinit var apiService: ApiService
        private set

    lateinit var localApiService: ApiService
        private set

    lateinit var publicApiService: ApiService
        private set

    fun init(context: Context) {
        val baseUrl = ServerConfig.getBaseHttpUrl(context)

        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl

            retrofit = createRetrofit(baseUrl)
            apiService = retrofit!!.create(ApiService::class.java)
        }

        if (localRetrofit == null) {
            localRetrofit = createRetrofit(ServerConfig.getLocalBaseHttpUrl())
            localApiService = localRetrofit!!.create(ApiService::class.java)
        }

        if (publicRetrofit == null) {
            publicRetrofit = createRetrofit(ServerConfig.getPublicBaseHttpUrl())
            publicApiService = publicRetrofit!!.create(ApiService::class.java)
        }
    }

    fun getCurrentBaseUrl(): String? {
        return currentBaseUrl
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
