package com.example.weatherapp.util.managers

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import com.example.weatherapp.data.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task

class LocationManager(
    private val context: Context,
    private val locationPermissionRequest: ActivityResultLauncher<Array<String>>
) {

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Task<android.location.Location>? {
        if (!PermissionManager(context, locationPermissionRequest).arePermissionsAllowed(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            PermissionManager(context, locationPermissionRequest).askForPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            return null
        } else {
            return LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { l ->
                    l.let {
                        Location(it.longitude.toInt(), it.latitude.toInt())
                    }
                }
        }
    }
}