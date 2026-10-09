package com.flykpicks.android.api

import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Makes every Retrofit call fail with an [ApiError] instead of `HttpException` / `IOException` /
 * Moshi exceptions, so services only ever catch one error family. Also lets `Unit` endpoints
 * succeed on empty (204) bodies and turns an unexpected empty body into [ApiError.Decoding].
 */
class ApiErrorCallAdapterFactory(private val mapper: ApiErrorMapper) : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) return null
        val responseType = getParameterUpperBound(0, returnType as ParameterizedType)
        val isUnit = responseType == Unit::class.java
        return object : CallAdapter<Any, Call<Any>> {
            override fun responseType(): Type = responseType
            override fun adapt(call: Call<Any>): Call<Any> = ApiErrorCall(call, mapper, isUnit)
        }
    }
}

private class ApiErrorCall<T : Any>(
    private val delegate: Call<T>,
    private val mapper: ApiErrorMapper,
    private val isUnit: Boolean,
) : Call<T> {

    override fun enqueue(callback: Callback<T>) {
        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                val translated = try {
                    translate(response)
                } catch (t: Throwable) {
                    callback.onFailure(this@ApiErrorCall, t)
                    return
                }
                callback.onResponse(this@ApiErrorCall, translated)
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                callback.onFailure(this@ApiErrorCall, mapper.mapFailure(t))
            }
        })
    }

    override fun execute(): Response<T> =
        try {
            translate(delegate.execute())
        } catch (t: Throwable) {
            throw mapper.mapFailure(t)
        }

    @Suppress("UNCHECKED_CAST")
    private fun translate(response: Response<T>): Response<T> {
        if (!response.isSuccessful) {
            val body = response.errorBody()?.use { it.string() }
            throw mapper.httpError(response.code(), body, response.headers()["Retry-After"])
        }
        if (response.body() != null) return response
        if (isUnit) return Response.success(Unit as T, response.raw())
        throw ApiError.Decoding("Empty response body for ${request().url.encodedPath}")
    }

    override fun clone(): Call<T> = ApiErrorCall(delegate.clone(), mapper, isUnit)
    override fun isExecuted(): Boolean = delegate.isExecuted
    override fun cancel() = delegate.cancel()
    override fun isCanceled(): Boolean = delegate.isCanceled
    override fun request(): Request = delegate.request()
    override fun timeout(): Timeout = delegate.timeout()
}
