package com.example.weatherapp.app.presentation.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.domain.exceptions.InvalidCityException
import com.example.domain.exceptions.LocationRequestFailedException
import com.example.domain.model.WeatherForecast
import com.example.domain.repository.WeatherRepository
import com.example.weatherapp.app.MainActivity
import com.example.weatherapp.app.presentation.utils.LocationPermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val sp: SharedPreferences,
    application: Application
) : AndroidViewModel(application) {

    private val _weatherForecast = MutableLiveData<Result<WeatherForecast>>()
    val weatherForecast: LiveData<Result<WeatherForecast>> = _weatherForecast

    private val locationPermissionManager = LocationPermissionManager(application)


    init {
        loadData()
    }

    @kotlin.jvm.Throws(InvalidCityException::class)
    fun loadData() {
        if (sp.getString(MainActivity.PREF_IS_AUTO, "true").toBoolean()) {
            loadDataAuto()
        } else {
            val city: String? = sp.getString(MainActivity.PREF_CITY_KEY, "")
            if (!city.isNullOrEmpty() && city.isNotBlank()) {
                loadDataManually(city)
            } else {
                _weatherForecast.value = Result.failure(
                    InvalidCityException()
                )
            }
        }
    }

    private fun loadDataManually(cityName: String) {
        viewModelScope.launch {
            val result = repository.getWeatherForecast(
                cityName
            )
            _weatherForecast.value = Result.success(result)
        }
    }

    @kotlin.jvm.Throws(LocationRequestFailedException::class)
    private fun loadDataAuto() {
        val locationRes = locationPermissionManager.getLocation()
        locationRes.fold(
            onSuccess = { task ->
                task.addOnSuccessListener {
                    if (it == null) {
                        _weatherForecast.value = Result.failure(
                            LocationRequestFailedException()
                        )
                    } else {
                        updateWeatherForecastAuto(it)
                    }
                }
            },
            onFailure = {
                _weatherForecast.value = Result.failure(it)
            })
    }

    private fun updateWeatherForecastAuto(it: Location) {
        viewModelScope.launch {
            val result = repository.getWeatherForecast(
                latitude = it.latitude,
                longitude = it.longitude
            )
            _weatherForecast.value = Result.success(result)
        }
    }
}
