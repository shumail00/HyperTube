package com.github.libretube.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    const val DEFAULT_PIPED_BASE_URL = "https://pipedapi.kavin.rocks/"

    val PIPED_API_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.private.coffee",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.drgns.space",
        "https://pipedapi.smnz.de",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.moomoo.me",
        "https://pipedapi.syncpundit.io",
        "https://pipedapi-libre.kavin.rocks"
    )

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val kotlinxConverterFactory = JsonHelper.json
        .asConverterFactory("application/json".toMediaType())

    val pipedApi: ExternalApi by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_PIPED_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(kotlinxConverterFactory)
            .build()
            .create<ExternalApi>()
    }
}
