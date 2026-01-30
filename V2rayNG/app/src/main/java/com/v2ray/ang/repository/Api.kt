package com.v2ray.ang.repository

import com.google.gson.GsonBuilder
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import android.util.Log

interface Api {

    @GET
    fun getConfigsList(@Url url: String): Single<ResponseBody>

    companion object {
        private val interceptor = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BODY)
        }
        private val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        private val gson = GsonBuilder()
            .setLenient()
            .create()

        private val retrofit = Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()

        operator fun invoke(): Api = retrofit.create(Api::class.java)

        fun fetchAllSubscriptions(): Single<List<String>> {
            return Single.fromCallable {
                val subscriptions = MmkvManager.decodeSubscriptions()
                val configsList = mutableListOf<String>()
                if (subscriptions.isEmpty()) {
                    Log.e(AppConfig.TAG, "No subscriptions found in MmkvManager")
                }
                subscriptions.forEach { (id, sub) ->
                    if (sub.enabled && !sub.url.isNullOrEmpty()) {
                        try {
                            val config = invoke().getConfigsList(sub.url).blockingGet()
                            val configString = config.string()
                            if (configString.isNotEmpty()) {
                                configsList.add(configString)
                                Log.d(AppConfig.TAG, "Subscription $id updated successfully: ${sub.url}")
                            } else {
                                Log.w(AppConfig.TAG, "Empty config received for subscription $id: ${sub.url}")
                            }
                        } catch (e: Exception) {
                            Log.e(AppConfig.TAG, "Error updating subscription $id (${sub.url}): ${e.message}", e)
                        }
                    } else {
                        Log.w(AppConfig.TAG, "Subscription $id is disabled or has no URL")
                    }
                }
                if (configsList.isEmpty()) {
                    Log.e(AppConfig.TAG, "No configs were fetched from any subscription")
                } else {
                    Log.d(AppConfig.TAG, "Fetched ${configsList.size} subscription configs")
                }
                configsList
            }
        }
    }
}
