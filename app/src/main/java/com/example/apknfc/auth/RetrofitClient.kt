package com.example.apknfc.auth

import com.example.apknfc.qr.PresenceApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Ponto único de configuração do Retrofit.
 *
 * TROQUE a BASE_URL pelo endereço real do seu backend assim
 * que ele existir. Durante o desenvolvimento local, se o
 * backend rodar no seu PC e você testar no celular físico,
 * use o IP da sua máquina na rede local (não "localhost",
 * que dentro do celular aponta pro próprio celular).
 * Ex: "http://192.168.0.10:8000/"
 */
object RetrofitClient {

    private const val BASE_URL = "http://192.168.0.10:8000/"

    // Um único Retrofit, usado por todas as APIs do app
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }

    // Presença por QR code (pacote qr)
    val presenceApi: PresenceApi by lazy { retrofit.create(PresenceApi::class.java) }
}