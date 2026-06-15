package net.jgpower.gichan_land.network

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiServiceManager {

    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null

    lateinit var apiService: ApiService
        private set

    fun init(context: Context) {
        val baseUrl = ServerConfig.getBaseHttpUrl(context)

        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
        }
    }
}
