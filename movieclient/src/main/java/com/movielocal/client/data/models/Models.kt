package com.movielocal.client.data.models

import com.google.gson.annotations.SerializedName

data class Movie(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("year")
    val year: Int,
    
    @SerializedName("genre")
    val genre: String,
    
    @SerializedName("rating")
    val rating: Float,
    
    @SerializedName("duration")
    val duration: Int,
    
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String,
    
    @SerializedName("videoUrl")
    val videoUrl: String,
    
    @SerializedName("type")
    val type: String = "movie"
)

data class Series(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("year")
    val year: Int,
    
    @SerializedName("genre")
    val genre: String,
    
    @SerializedName("rating")
    val rating: Float,
    
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String,
    
    @SerializedName("type")
    val type: String = "series",
    
    @SerializedName("seasons")
    val seasons: List<Season>
)

data class Season(
    @SerializedName("seasonNumber")
    val seasonNumber: Int,
    
    @SerializedName("episodes")
    val episodes: List<Episode>
)

data class Episode(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("episodeNumber")
    val episodeNumber: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("duration")
    val duration: Int,
    
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String,
    
    @SerializedName("videoUrl")
    val videoUrl: String
)

data class ContentResponse(
    @SerializedName("movies")
    val movies: List<Movie>,
    
    @SerializedName("series")
    val series: List<Series>
)

data class HealthResponse(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("serverTime")
    val serverTime: Long
)
