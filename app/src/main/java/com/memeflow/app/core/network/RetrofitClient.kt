package com.memeflow.app.core.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.memeflow.app.core.common.AppException
import com.memeflow.app.core.common.ErrorKind
import com.memeflow.app.core.network.dto.ErrorResponseDto
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class TokenProvider(
    var accessToken: String? = null
) {
    fun hasToken(): Boolean = !accessToken.isNullOrBlank()
}

fun createRetrofitClient(
    baseUrl: String,
    tokenProvider: TokenProvider,
    onTokenExpired: () -> Unit
): MemeFlowApi {
    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = tokenProvider.accessToken
        val newRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    return retrofit.create(MemeFlowApi::class.java)
}

fun mapHttpError(throwable: Throwable): AppException {
    val response = (throwable as? retrofit2.HttpException)?.response()
    val errorBody = response?.errorBody()?.string()
    val errorDto = try {
        val gson = GsonBuilder().create()
        errorBody?.let { gson.fromJson(it, ErrorResponseDto::class.java) }
    } catch (_: Exception) {
        null
    }
    val message = errorDto?.message ?: throwable.message ?: "Ошибка соединения с сервером."
    return when (response?.code()) {
        401 -> AppException(ErrorKind.UNAUTHORIZED, message)
        403 -> AppException(ErrorKind.FORBIDDEN, message)
        404 -> AppException(ErrorKind.NOT_FOUND, message)
        409, 422 -> AppException(ErrorKind.VALIDATION, message)
        null -> AppException(ErrorKind.NETWORK, message)
        else -> AppException(ErrorKind.UNKNOWN, message)
    }
}
