package com.movielocal.client.data.api

import com.movielocal.client.data.models.ContentResponse
import com.movielocal.client.data.models.HealthResponse
import retrofit2.Response
import retrofit2.http.GET

interface MovieApi {
    
    @GET("/api/health")
    suspend fun checkHealth(): Response<HealthResponse>
    
    @GET("/api/content")
    suspend fun getContent(): Response<ContentResponse>
}
