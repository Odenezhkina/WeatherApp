package com.example.weatherapp.util.managers

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.weatherapp.exceptions.LocationPermissionDeniedException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task

class LocationPermissionManager(private val application : Application) {

    private val fusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    fun getLocation(): Result<Task<Location>> {
        if (ActivityCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(LocationPermissionDeniedException("LocationPermissionDenied"))
        }
        return Result.success(fusedLocationProviderClient.lastLocation)
    }
}