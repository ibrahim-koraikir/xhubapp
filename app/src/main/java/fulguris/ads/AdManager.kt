package fulguris.ads

import android.content.SharedPreferences
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fulguris.di.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @UserPrefs private val sharedPreferences: SharedPreferences,
    @ApplicationContext private val context: Context
) {

    companion object {
        const val KEY_ACTION_COUNT = "ad_action_count"
        const val KEY_TARGET_THRESHOLD = "ad_target_threshold"
        const val KEY_AD_NETWORKS = "ad_networks_set"
        const val CONFIG_URL = "https://raw.githubusercontent.com/ibrahim-koraikir/AhmedHytworker-AdsConfig/main/ad_networks.json"
        
        val DEFAULT_NETWORKS = setOf(
            "https://www.effectivegatecpm.com/hypsia868?key=d55fe3c96beb154d635fe6ee82094511",
            "https://mpanyinadiingsinsp.com?e5hLQ=1258633",
            "https://villainoussession.com/AsFuJy"
        )
    }

    private val client = OkHttpClient()

    init {
        // Initialize threshold if not set
        if (!sharedPreferences.contains(KEY_TARGET_THRESHOLD)) {
            generateNewThreshold()
        }
        
        // Fetch latest networks from GitHub in the background
        fetchAdNetworks()
        registerNetworkListener()
    }

    private fun registerNetworkListener() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    // Fetch networks when internet becomes available
                    fetchAdNetworks()
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }

    private fun fetchAdNetworks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(CONFIG_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val responseBody = response.body?.string() ?: return@use
                    
                    val jsonObject = JSONObject(responseBody)
                    val networksArray = jsonObject.getJSONArray("networks")
                    val urls = mutableSetOf<String>()
                    
                    for (i in 0 until networksArray.length()) {
                        val network = networksArray.getJSONObject(i)
                        urls.add(network.getString("url"))
                    }
                    
                    if (urls.isNotEmpty()) {
                        sharedPreferences.edit().putStringSet(KEY_AD_NETWORKS, urls).apply()
                        Timber.d("Successfully updated ad networks: ${urls.size} networks loaded.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch dynamic ad networks")
            }
        }
    }

    /**
     * Tracks an action and returns true if an ad should be shown.
     */
    fun trackAction(): Boolean {
        var currentCount = sharedPreferences.getInt(KEY_ACTION_COUNT, 0)
        val targetThreshold = sharedPreferences.getInt(KEY_TARGET_THRESHOLD, 8)

        currentCount++

        if (currentCount >= targetThreshold) {
            // Threshold reached, reset count and generate new threshold
            sharedPreferences.edit()
                .putInt(KEY_ACTION_COUNT, 0)
                .putInt(KEY_TARGET_THRESHOLD, getRandomThreshold())
                .apply()
            return true
        } else {
            // Update current count
            sharedPreferences.edit()
                .putInt(KEY_ACTION_COUNT, currentCount)
                .apply()
            return false
        }
    }

    /**
     * Returns a random ad URL.
     */
    fun getAdUrl(): String {
        val networks = sharedPreferences.getStringSet(KEY_AD_NETWORKS, DEFAULT_NETWORKS) ?: DEFAULT_NETWORKS
        val list = if (networks.isEmpty()) DEFAULT_NETWORKS.toList() else networks.toList()
        return list.random()
    }

    /**
     * Checks if a given url string matches any of the known ad domains to bypass adblockers.
     */
    fun isAdUrl(url: String): Boolean {
        val networks = sharedPreferences.getStringSet(KEY_AD_NETWORKS, DEFAULT_NETWORKS) ?: DEFAULT_NETWORKS
        val list = if (networks.isEmpty()) DEFAULT_NETWORKS.toList() else networks.toList()
        
        for (adUrl in list) {
            try {
                val adDomain = Uri.parse(adUrl).host ?: continue
                if (url.contains(adDomain)) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        return false
    }

    private fun generateNewThreshold() {
        sharedPreferences.edit()
            .putInt(KEY_TARGET_THRESHOLD, getRandomThreshold())
            .apply()
    }

    private fun getRandomThreshold(): Int {
        // Randomly returns 6 to 10
        return (6..10).random()
    }
}
