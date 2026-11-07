package com.github.libretube.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

object RetrofitInstance {
    private const val BASE_URL = "https://www.youtube.com/"

    private val kotlinxConverterFactory = JsonHelper.json
        .asConverterFactory("application/json".toMediaType())

    val externalApi: ExternalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(kotlinxConverterFactory)
            .build()
            .create<ExternalApi>()
    }
}
