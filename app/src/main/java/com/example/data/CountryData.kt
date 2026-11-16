package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.CountryDialInfo
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object CountryData {
  val countries: List<CountryDialInfo> = listOf(
    CountryDialInfo("CI", "Côte d'Ivoire", "+225", "🇨🇮"),
    CountryDialInfo("FR", "France", "+33", "🇫🇷"),
    CountryDialInfo("SN", "Sénégal", "+221", "🇸🇳"),
    CountryDialInfo("CM", "Cameroun", "+237", "🇨🇲"),
    CountryDialInfo("ML", "Mali", "+223", "🇲🇱"),
    CountryDialInfo("BF", "Burkina Faso", "+226", "🇧🇫"),
    CountryDialInfo("TG", "Togo", "+228", "🇹🇬"),
    CountryDialInfo("BJ", "Bénin", "+229", "🇧🇯"),
    CountryDialInfo("GN", "Guinée", "+224", "🇬🇳"),
    CountryDialInfo("GA", "Gabon", "+241", "🇬🇦"),
    CountryDialInfo("CG", "Congo", "+242", "🇨🇬"),
    CountryDialInfo("CD", "RD Congo", "+243", "🇨🇩"),
    CountryDialInfo("MA", "Maroc", "+212", "🇲🇦"),
    CountryDialInfo("DZ", "Algérie", "+213", "🇩🇿"),
    CountryDialInfo("TN", "Tunisie", "+216", "🇹🇳"),
    CountryDialInfo("NG", "Nigeria", "+234", "🇳🇬"),
    CountryDialInfo("GH", "Ghana", "+233", "🇬🇭"),
    CountryDialInfo("BE", "Belgique", "+32", "🇧🇪"),
    CountryDialInfo("CH", "Suisse", "+41", "🇨🇭"),
    CountryDialInfo("CA", "Canada", "+1", "🇨🇦"),
    CountryDialInfo("US", "États-Unis", "+1", "🇺🇸"),
    CountryDialInfo("GB", "Royaume-Uni", "+44", "🇬🇧"),
    CountryDialInfo("DE", "Allemagne", "+49", "🇩🇪"),
    CountryDialInfo("ES", "Espagne", "+34", "🇪🇸"),
    CountryDialInfo("IT", "Italie", "+39", "🇮🇹"),
    CountryDialInfo("PT", "Portugal", "+351", "🇵🇹"),
    CountryDialInfo("BR", "Brésil", "+55", "🇧🇷"),
  )

  fun findByIso(iso: String?): CountryDialInfo {
    val clean = iso?.uppercase(Locale.ROOT)?.trim() ?: ""
    return countries.firstOrNull { it.isoCode == clean }
      ?: CountryDialInfo("CI", "Côte d'Ivoire", "+225", "🇨🇮")
  }

  fun detectFromDeviceLocale(context: Context): CountryDialInfo {
    val configLocale =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
      } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
      }
    val iso = configLocale?.country?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
    return findByIso(iso)
  }

  @SuppressLint("MissingPermission")
  suspend fun detectCountryViaGpsOrLocale(context: Context): Pair<CountryDialInfo, Boolean> {
    val hasFine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) {
      return detectFromDeviceLocale(context) to false
    }

    val gpsIso: String? =
      withTimeoutOrNull(4500L) {
        runCatching {
          val fusedClient = LocationServices.getFusedLocationProviderClient(context)
          val lastLoc = fusedClient.lastLocation.await()
          val location =
            lastLoc
              ?: run {
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                  .await()
              }
          if (location != null && Geocoder.isPresent()) {
            reverseGeocodeCountryIso(context, location.latitude, location.longitude)
          } else {
            null
          }
        }.getOrNull()
      }

    return if (!gpsIso.isNullOrBlank()) {
      findByIso(gpsIso) to true
    } else {
      detectFromDeviceLocale(context) to false
    }
  }

  private suspend fun reverseGeocodeCountryIso(
    context: Context,
    lat: Double,
    lon: Double,
  ): String? = withContext(Dispatchers.IO) {
    val geocoder = Geocoder(context, Locale.getDefault())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(lat, lon, 1) { addresses ->
          if (cont.isActive) cont.resume(addresses.firstOrNull()?.countryCode)
        }
      }
    } else {
      @Suppress("DEPRECATION")
      geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode
    }
  }
}
