package com.xhub.browser.shortcuts

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ShortcutSite(
    val name: String,
    val url: String
)

data class ShortcutGroup(
    val name: String,
    val sites: MutableList<ShortcutSite> = mutableListOf()
)

object ShortcutRepository {

    private const val PREFS_NAME = "home_shortcuts_prefs"
    private const val KEY_GROUPS  = "shortcut_groups"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------
    fun defaultGroups(): MutableList<ShortcutGroup> = mutableListOf(
        ShortcutGroup("Free OnlyFans", mutableListOf(
            ShortcutSite("PimpBunny", "https://pimpbunny.com"),
            ShortcutSite("EroThots", "https://erotthots.com"),
            ShortcutSite("ThotHub", "https://thothub.to"),
            ShortcutSite("PornTN", "https://porntn.com"),
            ShortcutSite("LeakGallery", "https://leakgallery.com"),
            ShortcutSite("Influencers Gone Wild", "https://influencersgonewild.com"),
            ShortcutSite("HornyLeak", "https://hornyleak.com"),
            ShortcutSite("NotFans", "https://notfans.com"),
            ShortcutSite("GotAnyNudes", "https://gotanynudes.com"),
            ShortcutSite("ShareAnyNudes", "https://shareanynudes.com"),
            ShortcutSite("Hot Leak", "https://hotleak.vip"),
            ShortcutSite("NudoStar", "https://nudostar.com"),
            ShortcutSite("DirtyShip", "https://dirtyship.com"),
            ShortcutSite("LewdStars", "https://lewdstars.com")
        )),
        ShortcutGroup("AI Porn", mutableListOf()),
        ShortcutGroup("Live Sex Cam", mutableListOf()),
        ShortcutGroup("Top Premium", mutableListOf()),
        ShortcutGroup("AI Porn Generator", mutableListOf()),
        ShortcutGroup("TikTok Porn", mutableListOf(
            ShortcutSite("FikFap", "https://fikfap.com"),
            ShortcutSite("Tik Porn", "https://tikporn.tv"),
            ShortcutSite("FYPTT", "https://fyptt.to"),
            ShortcutSite("Kwiky", "https://kwiky.net"),
            ShortcutSite("PornHub Shorties", "https://pornhubshorties.com"),
            ShortcutSite("SlushyXXX", "https://slushyxxx.com"),
            ShortcutSite("TikXXX", "https://tikxxx.com"),
            ShortcutSite("Followx", "https://followx.net"),
            ShortcutSite("FreeOnlyTik", "https://freeonlytik.com"),
            ShortcutSite("NSFWSwipe", "https://nsfwswipe.com"),
            ShortcutSite("Tik.cx", "https://tik.cx"),
            ShortcutSite("FreakTok", "https://freaktok.com"),
            ShortcutSite("SwipeFap", "https://swipefap.com"),
            ShortcutSite("TitFap", "https://titfap.com"),
            ShortcutSite("OGFAP", "https://ogfap.com"),
            ShortcutSite("Waptap", "https://waptap.com"),
            ShortcutSite("PinPorn", "https://pinporn.com"),
            ShortcutSite("SexReels", "https://sexreels.com"),
            ShortcutSite("OnlyScroll", "https://onlyscroll.com")
        )),
        ShortcutGroup("Arabic", mutableListOf()),
        ShortcutGroup("movies", mutableListOf(
            ShortcutSite("AEBN", "https://m.aebn.net/?theaterId=80365&genreId=101&locale=en"),
            ShortcutSite("Pornwatch", "https://pornwatch.ws/"),
            ShortcutSite("Speedporn", "https://speedporn.net/the-perfect-sister-in-law/"),
            ShortcutSite("xtapes", "https://xtapes.me/the-fiery-maid/"),
            ShortcutSite("Pornkino", "https://pornkino.cc/"),
            ShortcutSite("WatchFreeXXX", "https://watchfreexxx.net/"),
            ShortcutSite("Mangoporn", "https://mangoporn.net/"),
            ShortcutSite("Pandamovies", "https://pandamovies.pw/"),
            ShortcutSite("Freeomovie", "https://freeomovie.info/"),
            ShortcutSite("Mangoporn Movies", "https://mangoporn.net/movies/40-year-old-size-queens-4/")
        ))
    )

    // ------------------------------------------------------------------
    // Load
    // ------------------------------------------------------------------
    fun loadGroups(context: Context): MutableList<ShortcutGroup> {
        val json = prefs(context).getString(KEY_GROUPS, null) ?: return defaultGroups()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ShortcutGroup>()
            for (i in 0 until arr.length()) {
                val gObj = arr.getJSONObject(i)
                val sitesArr = gObj.getJSONArray("sites")
                val sites = mutableListOf<ShortcutSite>()
                for (j in 0 until sitesArr.length()) {
                    val sObj = sitesArr.getJSONObject(j)
                    sites.add(ShortcutSite(sObj.getString("name"), sObj.getString("url")))
                }
                list.add(ShortcutGroup(gObj.getString("name"), sites))
            }
            list
        } catch (e: Exception) {
            defaultGroups()
        }
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------
    fun saveGroups(context: Context, groups: List<ShortcutGroup>) {
        val arr = JSONArray()
        groups.forEach { group ->
            val gObj = JSONObject()
            gObj.put("name", group.name)
            val sitesArr = JSONArray()
            group.sites.forEach { site ->
                val sObj = JSONObject()
                sObj.put("name", site.name)
                sObj.put("url", site.url)
                sitesArr.put(sObj)
            }
            gObj.put("sites", sitesArr)
            arr.put(gObj)
        }
        prefs(context).edit().putString(KEY_GROUPS, arr.toString()).apply()
    }
}
