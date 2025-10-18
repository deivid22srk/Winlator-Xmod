package com.movielocal.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movielocal.client.data.models.Movie
import com.movielocal.client.data.models.Series
import com.movielocal.client.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class Success(val movies: List<Movie>, val series: List<Series>) : UiState()
    data class Error(val message: String) : UiState()
}

data class ConnectionState(
    val isConnected: Boolean = false,
    val serverUrl: String = ""
)

class MovieViewModel : ViewModel() {
    
    private val repository = MovieRepository()
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    fun setServerUrl(url: String) {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        
        repository.setServerUrl(formattedUrl)
        _connectionState.value = ConnectionState(serverUrl = formattedUrl)
        checkConnection()
    }
    
    fun checkConnection() {
        viewModelScope.launch {
            val result = repository.checkHealth()
            _connectionState.value = _connectionState.value.copy(
                isConnected = result.isSuccess
            )
            
            if (result.isSuccess) {
                loadContent()
            }
        }
    }
    
    fun loadContent() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val result = repository.getContent()
            
            _uiState.value = if (result.isSuccess) {
                val content = result.getOrNull()!!
                UiState.Success(movies = content.movies, series = content.series)
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Erro ao carregar conteúdo")
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun getFilteredMovies(movies: List<Movie>): List<Movie> {
        if (_searchQuery.value.isEmpty()) return movies
        return movies.filter { 
            it.title.contains(_searchQuery.value, ignoreCase = true) ||
            it.genre.contains(_searchQuery.value, ignoreCase = true)
        }
    }
    
    fun getFilteredSeries(series: List<Series>): List<Series> {
        if (_searchQuery.value.isEmpty()) return series
        return series.filter { 
            it.title.contains(_searchQuery.value, ignoreCase = true) ||
            it.genre.contains(_searchQuery.value, ignoreCase = true)
        }
    }
}
