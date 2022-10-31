package com.example.weatherapp.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherapp.MainActivity
import com.example.weatherapp.R
import com.example.weatherapp.data.WeatherRepository
import com.example.weatherapp.data.response.MainInformationAboutDay
import com.example.weatherapp.data.response.WeatherForecast
import com.example.weatherapp.databinding.FragmentMainBinding
import com.example.weatherapp.exceptions.InvalidCityException
import com.example.weatherapp.exceptions.LocationPermissionDeniedException
import com.example.weatherapp.extensions.setWeatherIcon
import com.example.weatherapp.util.WeatherForecastAdapter
import com.example.weatherapp.util.managers.ConvertingManager
import com.example.weatherapp.viewmodel.MainViewModel
import com.example.weatherapp.viewmodel.MainViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    private val adapter = WeatherForecastAdapter()

    @Inject
    lateinit var repository: WeatherRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

        showProgressBar()
        val factory = MainViewModelFactory(
            repository,
            application = requireActivity().application,
            sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        initObserves()

        return binding.root
    }

    private fun initObserves() {
        viewModel.weatherForecast.observe(viewLifecycleOwner) { resWeatherForecast ->
            resWeatherForecast.fold(
                onSuccess = {
                    hideProgressBar()
                    setWeatherForecastToUi(it)
                    showActionBarTitle(it.city.name, it.city.country)
                    saveToPreferences(it.city.name, it.city.country)

                },
                onFailure =
                {
                    // todo request permissions
                    when (it) {
                        is LocationPermissionDeniedException -> {
                            requestLocationPermissions()
                        }
                        is InvalidCityException -> {
                            Toast.makeText(context, R.string.error_invalid_city, Toast.LENGTH_SHORT)
                                .show()
                        }
                        else -> {
                            Toast.makeText(context, R.string.error_message, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                })
        }
    }

    private fun showActionBarTitle(city: String, country: String) {
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(
                R.string.city_country_format,
                city,
                country
            )
    }

    private fun requestLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED -> {
                // Everything is ok
                viewModel.loadData()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_no_location_access_granted),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                // todo check result
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 0
                )
            }
        }
    }

    private fun showProgressBar() {
        for (view in binding.mainCl.children) {
            view.visibility = View.INVISIBLE
            if (view.id == R.id.progress_bar) {
                (view as ContentLoadingProgressBar).show()
            }
        }
    }

    private fun hideProgressBar() {
        for (view in binding.mainCl.children) {
            view.visibility = View.VISIBLE
            if (view.id == R.id.progress_bar) {
                (view as ContentLoadingProgressBar).hide()
            }
        }
    }

    private fun setWeatherForecastToUi(
        weatherForecast: WeatherForecast,
    ) {
        var temperatureUnit: String
        var timeFormat: String

        PreferenceManager.getDefaultSharedPreferences(requireActivity()).run {
            temperatureUnit = getString(
                MainActivity.PREF_TEMPERATURE_UNIT_KEY,
                MainActivity.DEFAULT_TEMPERATURE_UNIT
            ).toString()
            timeFormat = getString(
                MainActivity.PREF_TIME_FORMAT_KEY,
                MainActivity.DEFAULT_TIMEFORMAT
            ).toString()
        }

        with(binding) {
            val converter = ConvertingManager(resources)
            weatherForecast.list[0].run {
                main.run {
                    tvTemperature.text = getString(
                        R.string.temperature_unit,
                        converter.convertTemp(temperatureUnit, temp),
                        temperatureUnit
                    )
                    tvPressure.text = getString(R.string.pressure_unit, pressure)
                    tvHumidity.text = getString(R.string.humidity_unit, humidity)
                    tvFeelsLike.text =
                        getString(
                            R.string.feels_like,
                            converter.convertTemp(temperatureUnit, temp),
                            temperatureUnit
                        )
                }
                tvVisibility.text = converter.convertVisibility(visibility)
                tvWind.text = getString(R.string.wind_unit, converter.formatDouble(wind.speed))
                ivWeather.setWeatherIcon(weather[0].id, weather[0].icon)
            }
            weatherForecast.city.run {
                tvSunrise.text =
                    converter.convertTimeToString(timeFormat.split(", ")[1], sunrise.toLong())
                tvSunset.text =
                    converter.convertTimeToString(timeFormat.split(", ")[1], sunset.toLong())
            }
            textClock.format24Hour = timeFormat

            setUpAdapter(timeFormat, temperatureUnit, weatherForecast.list.drop(1))
            setUpRecycler()
        }
    }

    private fun setUpAdapter(
        timeFormat: String,
        temperatureUnit: String,
        list: List<MainInformationAboutDay>
    ) {
        adapter.setData(list)
        adapter.timeformat = timeFormat
        adapter.temperatureUnit = temperatureUnit
    }

    private fun setUpRecycler(){
        with(binding) {
            rvWeather.addItemDecoration(
                DividerItemDecoration(
                    context,
                    RecyclerView.HORIZONTAL
                )
            )
            rvWeather.setHasFixedSize(true)
            rvWeather.adapter = adapter
        }
    }

    private fun saveToPreferences(city: String, country: String) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit().run {
                putString(MainActivity.PREF_CITY_KEY, city)
                putString(MainActivity.PREF_COUNTRY_KEY, country)
                apply()
            }
    }

}
