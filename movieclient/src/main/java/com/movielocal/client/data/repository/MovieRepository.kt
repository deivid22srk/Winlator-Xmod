package com.movielocal.client.data.repository

import com.movielocal.client.data.api.MovieApi
import com.movielocal.client.data.api.RetrofitClient
import com.movielocal.client.data.models.ContentResponse
import com.movielocal.client.data.models.HealthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MovieRepository {
    
    private var api: MovieApi? = null
    
    fun setServerUrl(url: String) {
        api = RetrofitClient.getMovieApi(url)
    }
    
    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api?.checkHealth()
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Health check failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getContent(): Result<ContentResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api?.getContent()
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch content"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
