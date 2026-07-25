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

/**
 * Central store for the home-screen shortcut groups.
 *
 * ## Three data sources, merged deterministically
 *
 * The list the user actually sees is derived from three layers:
 *
 *  1. **Remote** — a `shortcuts.json` we host on GitHub and serve through the jsDelivr CDN
 *     (see [RemoteShortcutsFetcher]). Downloaded in the background and cached raw in
 *     [KEY_REMOTE_CACHE]. This is how we push new sites to everyone WITHOUT shipping an app update.
 *  2. **Defaults** — [defaultGroups], compiled into the APK. Used as the offline fallback the very
 *     first time before any remote fetch has succeeded.
 *  3. **User overlay** — [KEY_OVERLAY]. This holds ONLY the user's *deltas* (which sites/groups they
 *     removed, added, renamed, and their preferred ordering) — never a full snapshot.
 *
 * [loadGroups] returns `merge(base, overlay)` where `base = remote ?: defaults`. Because the user's
 * edits live in a separate overlay keyed by URL, they are never clobbered when we push a new remote
 * list: a site the user deleted stays deleted (tombstone), a site they renamed keeps its custom
 * name, and brand-new remote sites simply appear appended to the relevant group.
 *
 * [saveGroups] keeps its original signature so callers (e.g. ManageShortcutsActivity) don't change:
 * it diffs the edited list against the current base and persists the resulting overlay.
 */
object ShortcutRepository {

    private const val PREFS_NAME = "home_shortcuts_prefs"

    /** Legacy key: full serialized group list from before the overlay model. Migrated then removed. */
    private const val KEY_GROUPS = "shortcut_groups"

    /** Raw JSON downloaded from the remote CDN (the [RemoteShortcutsFetcher] output). */
    private const val KEY_REMOTE_CACHE = "remote_groups_json"

    /** The user's overlay of deltas (tombstones / adds / renames / ordering). */
    private const val KEY_OVERLAY = "user_overlay_json"

    /**
     * The user's manually-starred favorite sites, stored as a self-contained `[{name,url}]` array.
     *
     * Favorites are a separate layer from the overlay on purpose: they hold the FULL site record
     * (name + url), not just a reference into a group. That way a starred site keeps appearing in
     * the Favorites row even if the user later removes it from its group or a remote push drops it.
     * Because they live outside the base/overlay merge, remote updates never touch them.
     */
    private const val KEY_FAVORITES = "favorite_sites_json"

    /** Monotonic version, bumped whenever the effective data changes (overlay save OR remote fetch). */
    private const val KEY_VERSION = "shortcut_groups_version"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Monotonically increasing version counter, bumped every time the effective shortcut data
     * changes — either the user saves edits ([saveGroups]) or a fresh remote list is cached
     * ([cacheRemoteGroups]). Callers (e.g. the home screen) compare this to the version they last
     * rendered so they can skip redundant — and destructive — rebuilds.
     */
    fun dataVersion(context: Context): Int =
        prefs(context).getInt(KEY_VERSION, 0)

    private fun bumpVersion(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        editor.putInt(KEY_VERSION, prefs.getInt(KEY_VERSION, 0) + 1)
    }

    /**
     * Identity key for a site. Names can be edited freely, so the URL is the natural key. We
     * normalize lightly (trim + drop a single trailing slash + lowercase) so trivially different
     * spellings of the same URL are treated as the same site for remove/rename matching.
     */
    private fun urlKey(url: String): String =
        url.trim().removeSuffix("/").lowercase()

    // ------------------------------------------------------------------
    // Defaults (offline fallback baseline)
    // ------------------------------------------------------------------
    fun defaultGroups(): MutableList<ShortcutGroup> = mutableListOf(
        ShortcutGroup("Free OnlyFans", mutableListOf(
            ShortcutSite("PimpBunny", "https://pimpbunny.com"),
            ShortcutSite("borntobefuck", "https://fr.borntobefuck.com/categories"),
            ShortcutSite("leakyourporn", "https://leakyourporn.com/"),
            ShortcutSite("ultrahqporn", "https://ultrahqporn.com/"),
            ShortcutSite("prothots", "https://prothots.com/"),
            ShortcutSite("fapptime", "https://fapptime.com/"),
            ShortcutSite("theleaksbay", "https://theleaksbay.com/homepage/"),
            ShortcutSite("thotslife", "https://thotslife.com/"),
            ShortcutSite("ThotHub VIP", "https://thothub.vip/"),
            ShortcutSite("NSFW247", "https://nsfw247.to/homepage20260626/"),
            ShortcutSite("ThotHub", "https://thothub.to"),
            ShortcutSite("thotchicks", "https://thotchicks.com/"),
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
        ShortcutGroup("Free Stream", mutableListOf(
            ShortcutSite("sheeshfans", "https://sheeshfans.com/"),
            ShortcutSite("justporn", "https://www.justporn.com/"),
            ShortcutSite("letsporn", "https://letsporn.com/"),
            ShortcutSite("yesporn", "https://yesporn.vip/"),
            ShortcutSite("zzxxtra", "https://zzxxtra.com/"),
            ShortcutSite("allpornstream", "https://allpornstream.com/"),
            ShortcutSite("beeg", "https://beeg.com/"),
            ShortcutSite("pmvhaven", "https://pmvhaven.com/"),
            ShortcutSite("bellesa", "https://www.bellesa.co/"),
            ShortcutSite("hello.porn", "https://hello.porn/best/"),
            ShortcutSite("pornhd3x", "https://www9.pornhd3x.tv/"),
            ShortcutSite("hobby.porn", "https://hobby.porn/"),
            ShortcutSite("max.porn", "https://max.porn/"),
            ShortcutSite("pornhat", "https://www.pornhat.com/"),
            ShortcutSite("porngrey", "https://www.porngrey.net/"),
            ShortcutSite("ooxxx", "https://ooxxx.com/"),
            ShortcutSite("zzztube", "https://zzztube.com/hot/"),
            ShortcutSite("tubeorigin", "https://www.tubeorigin.com/"),
            ShortcutSite("pornhat.one", "https://www.pornhat.one/?ad_sub=336"),
            ShortcutSite("pornl", "https://pornl.com/"),
            ShortcutSite("wow.xxx", "https://www.wow.xxx/"),
            ShortcutSite("pornobae", "https://pornobae.com/"),
            ShortcutSite("1porn", "https://www.1porn.tv/")
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
        ShortcutGroup("Arabic", mutableListOf(
            ShortcutSite("xxxarabsex", "https://xxxarabsex.to/"),
            ShortcutSite("koselarab", "https://koselarab.com/"),
            ShortcutSite("arabsexi", "https://arabsexi.info/"),
            ShortcutSite("braiiz", "https://braiiz.com/"),
            ShortcutSite("3rabxn", "https://3rabxn.com/"),
            ShortcutSite("sexaraby", "https://www.sexaraby.to/"),
            ShortcutSite("sexmaharim", "https://sexmaharim.com/"),
            ShortcutSite("sexarabii", "https://sexarabii.com/"),
            ShortcutSite("darkegy", "https://darkegy.cam/"),
            ShortcutSite("freesexalarab", "https://freesexalarab.com/"),
            ShortcutSite("naralsex", "https://www.naralsex.com/"),
            ShortcutSite("arabx", "https://www.arabx.cam/"),
            ShortcutSite("elneswangy", "https://www.elneswangy.com/"),
            ShortcutSite("eljooker", "https://eljooker.com/"),
            ShortcutSite("anametnaka", "https://anametnaka.com/"),
            ShortcutSite("arabnok", "https://arabnok.com/"),
            ShortcutSite("sexalarab", "https://sexalarab.com/"),
            ShortcutSite("nafakarab", "https://nafakarab.com/"),
            ShortcutSite("shrmha", "https://shrmha.com/"),
            ShortcutSite("عرب.chat", "https://xn--ygba1c.chat/"),
            ShortcutSite("x-arxx", "https://x-arxx.com/"),
            ShortcutSite("arabxnxx", "https://arabxnxx.to/"),
            ShortcutSite("abozeb", "https://www.abozeb.com/"),
            ShortcutSite("sexnyk", "https://www.sexnyk.com/"),
            ShortcutSite("arbada", "https://arbada.net/"),
            ShortcutSite("arabsex", "https://www.arabsex.xxx/"),
            ShortcutSite("arabhotx", "https://arabhotx.com/"),
            ShortcutSite("sexeti", "https://sexeti.com/"),
            ShortcutSite("egy69", "https://www.egy69.com/")
        )),
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
    // Favorites (independent, self-contained layer)
    // ------------------------------------------------------------------
    /**
     * The user's starred favorite sites, in the order they were added. Empty if none have been
     * starred yet or the stored payload is missing/corrupt (never throws).
     */
    fun favoriteSites(context: Context): MutableList<ShortcutSite> {
        val json = prefs(context).getString(KEY_FAVORITES, null) ?: return mutableListOf()
        return parseSitesJson(json) ?: mutableListOf()
    }

    /** True if a site with the same normalized URL is currently favorited. */
    fun isFavorite(context: Context, url: String): Boolean {
        val key = urlKey(url)
        return favoriteSites(context).any { urlKey(it.url) == key }
    }

    /**
     * Toggle a site's favorite status: add it (appended to the end) if not present, remove it (by
     * normalized URL) if present. Persists the new list and bumps [dataVersion] so the home screen
     * rebuilds. Safe to call off the main thread.
     *
     * @return the new state — true if the site is now favorited, false if it was just un-favorited.
     */
    fun toggleFavorite(context: Context, site: ShortcutSite): Boolean {
        val current = favoriteSites(context)
        val key = urlKey(site.url)
        val existingIndex = current.indexOfFirst { urlKey(it.url) == key }
        val nowFavorited: Boolean
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            nowFavorited = false
        } else {
            current.add(site)
            nowFavorited = true
        }
        val p = prefs(context)
        val editor = p.edit().putString(KEY_FAVORITES, sitesToJson(current))
        bumpVersion(p, editor)
        editor.apply()
        return nowFavorited
    }

    /** Parse a bare `[{name,url}]` array of sites. Returns null on malformed input. */
    private fun parseSitesJson(json: String?): MutableList<ShortcutSite>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            val sites = mutableListOf<ShortcutSite>()
            for (i in 0 until arr.length()) {
                val sObj = arr.getJSONObject(i)
                sites.add(ShortcutSite(sObj.getString("name"), sObj.getString("url")))
            }
            sites
        } catch (e: Exception) {
            null
        }
    }

    /** Serialize a list of sites to a bare `[{name,url}]` array. */
    private fun sitesToJson(sites: List<ShortcutSite>): String {
        val arr = JSONArray()
        sites.forEach { site ->
            arr.put(JSONObject().put("name", site.name).put("url", site.url))
        }
        return arr.toString()
    }

    // ------------------------------------------------------------------
    // JSON (de)serialization of a plain group list
    // ------------------------------------------------------------------
    /**
     * Parse the shortcut groups from JSON. Two shapes are accepted:
     *
     *  1. A bare array — `[{name, sites:[{name,url}]}]` (legacy overlay/cache format).
     *  2. An object wrapping the array — `{ "version": N, "groups": [ ... ] }`. This is the format
     *     of the hosted `shortcuts.json` (the exported file) so the remote fetch can consume it
     *     directly. The `version` field is currently informational only.
     *
     * Returns null on any malformed input.
     */
    fun parseGroupsJson(json: String?): MutableList<ShortcutGroup>? {
        if (json.isNullOrBlank()) return null
        return try {
            // Accept either a top-level array or a { version, groups: [...] } object.
            val arr = run {
                val trimmed = json.trimStart()
                if (trimmed.startsWith("{")) {
                    JSONObject(json).getJSONArray("groups")
                } else {
                    JSONArray(json)
                }
            }
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
            null
        }
    }

    private fun groupsToJson(groups: List<ShortcutGroup>): String {
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
        return arr.toString()
    }

    // ------------------------------------------------------------------
    // Base = remote cache, else defaults
    // ------------------------------------------------------------------
    /**
     * The authoritative base list before the user overlay is applied: the last successfully cached
     * remote list if present and valid, otherwise the compiled-in [defaultGroups].
     */
    fun baseGroups(context: Context): MutableList<ShortcutGroup> {
        val cached = prefs(context).getString(KEY_REMOTE_CACHE, null)
        return parseGroupsJson(cached) ?: defaultGroups()
    }

    // ------------------------------------------------------------------
    // Overlay model
    // ------------------------------------------------------------------
    /**
     * The user's deltas relative to [baseGroups]. All identity is by [urlKey] for sites and by
     * name for groups. Everything here is additive/subtractive so it can be replayed on top of any
     * future base list.
     */
    data class Overlay(
        /** URL keys of base sites the user removed (tombstones — survive remote re-adds). */
        val removedSiteKeys: MutableSet<String> = mutableSetOf(),
        /** Names of base groups the user removed. */
        val removedGroups: MutableSet<String> = mutableSetOf(),
        /** Group name -> user-added sites not present in the base. */
        val addedSites: MutableMap<String, MutableList<ShortcutSite>> = mutableMapOf(),
        /** Names of groups the user created that don't exist in the base. */
        val addedGroups: MutableList<String> = mutableListOf(),
        /** URL key -> custom display name for base sites the user renamed. */
        val renamedSites: MutableMap<String, String> = mutableMapOf(),
        /** User's preferred group ordering (by name). New base groups append after these. */
        val groupOrder: MutableList<String> = mutableListOf(),
        /** Group name -> user's preferred site ordering (by URL key). New sites append after. */
        val siteOrder: MutableMap<String, MutableList<String>> = mutableMapOf()
    )

    private fun overlayToJson(o: Overlay): String {
        val root = JSONObject()
        root.put("removedSiteKeys", JSONArray(o.removedSiteKeys.toList()))
        root.put("removedGroups", JSONArray(o.removedGroups.toList()))
        root.put("addedGroups", JSONArray(o.addedGroups))
        root.put("groupOrder", JSONArray(o.groupOrder))

        val added = JSONObject()
        o.addedSites.forEach { (group, sites) ->
            val arr = JSONArray()
            sites.forEach { s ->
                arr.put(JSONObject().put("name", s.name).put("url", s.url))
            }
            added.put(group, arr)
        }
        root.put("addedSites", added)

        val renamed = JSONObject()
        o.renamedSites.forEach { (k, v) -> renamed.put(k, v) }
        root.put("renamedSites", renamed)

        val order = JSONObject()
        o.siteOrder.forEach { (group, keys) -> order.put(group, JSONArray(keys)) }
        root.put("siteOrder", order)

        return root.toString()
    }

    private fun parseOverlay(json: String?): Overlay {
        val o = Overlay()
        if (json.isNullOrBlank()) return o
        return try {
            val root = JSONObject(json)
            root.optJSONArray("removedSiteKeys")?.let { a -> for (i in 0 until a.length()) o.removedSiteKeys.add(a.getString(i)) }
            root.optJSONArray("removedGroups")?.let { a -> for (i in 0 until a.length()) o.removedGroups.add(a.getString(i)) }
            root.optJSONArray("addedGroups")?.let { a -> for (i in 0 until a.length()) o.addedGroups.add(a.getString(i)) }
            root.optJSONArray("groupOrder")?.let { a -> for (i in 0 until a.length()) o.groupOrder.add(a.getString(i)) }

            root.optJSONObject("addedSites")?.let { obj ->
                obj.keys().forEach { group ->
                    val arr = obj.getJSONArray(group)
                    val sites = mutableListOf<ShortcutSite>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        sites.add(ShortcutSite(s.getString("name"), s.getString("url")))
                    }
                    o.addedSites[group] = sites
                }
            }
            root.optJSONObject("renamedSites")?.let { obj ->
                obj.keys().forEach { k -> o.renamedSites[k] = obj.getString(k) }
            }
            root.optJSONObject("siteOrder")?.let { obj ->
                obj.keys().forEach { group ->
                    val arr = obj.getJSONArray(group)
                    val keys = mutableListOf<String>()
                    for (i in 0 until arr.length()) keys.add(arr.getString(i))
                    o.siteOrder[group] = keys
                }
            }
            o
        } catch (e: Exception) {
            Overlay()
        }
    }

    // ------------------------------------------------------------------
    // Merge: base ⊕ overlay
    // ------------------------------------------------------------------
    /**
     * Produce the effective list the user sees by replaying [overlay] on top of [base].
     * Pure function (no I/O) so it is directly unit-testable.
     */
    fun merge(base: List<ShortcutGroup>, overlay: Overlay): MutableList<ShortcutGroup> {
        val result = mutableListOf<ShortcutGroup>()

        // 1. Start from base groups the user hasn't tombstoned.
        base.forEach { baseGroup ->
            if (overlay.removedGroups.contains(baseGroup.name)) return@forEach

            val sites = mutableListOf<ShortcutSite>()
            baseGroup.sites.forEach { site ->
                val key = urlKey(site.url)
                if (overlay.removedSiteKeys.contains(key)) return@forEach
                // Apply rename if present.
                val name = overlay.renamedSites[key] ?: site.name
                sites.add(ShortcutSite(name, site.url))
            }
            // Inject user-added sites for this group.
            overlay.addedSites[baseGroup.name]?.let { added ->
                added.forEach { a ->
                    if (!overlay.removedSiteKeys.contains(urlKey(a.url))) sites.add(a)
                }
            }
            // Reorder sites per user's preference; unknown/new sites keep relative order at the end.
            reorderSites(sites, overlay.siteOrder[baseGroup.name])
            result.add(ShortcutGroup(baseGroup.name, sites))
        }

        // 2. Append user-created groups that don't exist in the base.
        val baseNames = base.map { it.name }.toSet()
        overlay.addedGroups.forEach { groupName ->
            if (baseNames.contains(groupName)) return@forEach
            if (overlay.removedGroups.contains(groupName)) return@forEach
            val sites = mutableListOf<ShortcutSite>()
            overlay.addedSites[groupName]?.forEach { a ->
                if (!overlay.removedSiteKeys.contains(urlKey(a.url))) sites.add(a)
            }
            reorderSites(sites, overlay.siteOrder[groupName])
            result.add(ShortcutGroup(groupName, sites))
        }

        // 3. Reorder groups per the user's preference; new/unknown groups keep order at the end.
        reorderGroups(result, overlay.groupOrder)
        return result
    }

    private fun reorderSites(sites: MutableList<ShortcutSite>, order: List<String>?) {
        if (order.isNullOrEmpty()) return
        val rank = order.withIndex().associate { (i, k) -> k to i }
        // Stable sort: known items by their rank, unknown items pushed to the end preserving order.
        val sorted = sites.sortedWith(compareBy { rank[urlKey(it.url)] ?: Int.MAX_VALUE })
        sites.clear()
        sites.addAll(sorted)
    }

    private fun reorderGroups(groups: MutableList<ShortcutGroup>, order: List<String>?) {
        if (order.isNullOrEmpty()) return
        val rank = order.withIndex().associate { (i, name) -> name to i }
        val sorted = groups.sortedWith(compareBy { rank[it.name] ?: Int.MAX_VALUE })
        groups.clear()
        groups.addAll(sorted)
    }

    // ------------------------------------------------------------------
    // Diff: edited list -> overlay (relative to base)
    // ------------------------------------------------------------------
    /**
     * Compute the [Overlay] that, when merged onto [base], reproduces [edited]. This is how we turn
     * a user's full edited list (from the manage screen) back into a compact set of deltas.
     */
    fun computeOverlay(base: List<ShortcutGroup>, edited: List<ShortcutGroup>): Overlay {
        val overlay = Overlay()

        val baseByName = base.associateBy { it.name }
        val editedByName = edited.associateBy { it.name }

        // Group-level tombstones and additions.
        base.forEach { if (!editedByName.containsKey(it.name)) overlay.removedGroups.add(it.name) }
        edited.forEach { if (!baseByName.containsKey(it.name)) overlay.addedGroups.add(it.name) }

        // Preserve the user's group ordering.
        overlay.groupOrder.addAll(edited.map { it.name })

        edited.forEach { editedGroup ->
            val baseGroup = baseByName[editedGroup.name]
            val baseSiteKeys = baseGroup?.sites?.map { urlKey(it.url) }?.toSet() ?: emptySet()
            val baseSiteByKey = baseGroup?.sites?.associateBy { urlKey(it.url) } ?: emptyMap()
            val editedKeys = editedGroup.sites.map { urlKey(it.url) }.toSet()

            // Site tombstones: base sites for this group missing from the edited group.
            baseGroup?.sites?.forEach { s ->
                if (!editedKeys.contains(urlKey(s.url))) overlay.removedSiteKeys.add(urlKey(s.url))
            }

            // Added sites (not in base) and renames (in base but different display name).
            val added = mutableListOf<ShortcutSite>()
            editedGroup.sites.forEach { s ->
                val key = urlKey(s.url)
                if (!baseSiteKeys.contains(key)) {
                    added.add(s)
                } else {
                    val baseSite = baseSiteByKey[key]
                    if (baseSite != null && baseSite.name != s.name) overlay.renamedSites[key] = s.name
                }
            }
            if (added.isNotEmpty()) overlay.addedSites[editedGroup.name] = added

            // Preserve per-group site ordering.
            overlay.siteOrder[editedGroup.name] = editedGroup.sites.map { urlKey(it.url) }.toMutableList()
        }

        return overlay
    }

    // ------------------------------------------------------------------
    // Public API (signatures unchanged for existing callers)
    // ------------------------------------------------------------------
    /**
     * The effective, merged list of groups the user should see:
     * `merge(remoteOrDefaults, userOverlay)`. Performs a one-time migration of any legacy full-list
     * storage into the new overlay model.
     */
    fun loadGroups(context: Context): MutableList<ShortcutGroup> {
        migrateLegacyIfNeeded(context)
        val base = baseGroups(context)
        val overlay = parseOverlay(prefs(context).getString(KEY_OVERLAY, null))
        return merge(base, overlay)
    }

    /**
     * Persist the user's edits. Instead of storing the whole list (which would clobber future
     * remote updates), we diff [groups] against the current base and store only the resulting
     * overlay. Bumps [dataVersion] so the home screen rebuilds.
     */
    fun saveGroups(context: Context, groups: List<ShortcutGroup>) {
        val base = baseGroups(context)
        val overlay = computeOverlay(base, groups)
        val p = prefs(context)
        val editor = p.edit().putString(KEY_OVERLAY, overlayToJson(overlay))
        bumpVersion(p, editor)
        editor.apply()
    }

    /**
     * Store a freshly fetched, already-validated remote list (raw JSON of a `[{name,sites}]` array).
     * Called by [RemoteShortcutsFetcher] on a background thread. Bumps [dataVersion] only when the
     * cached payload actually changed, so we don't trigger needless home-screen rebuilds.
     *
     * @return true if the cache was updated (i.e. the remote content differed from what we had).
     */
    fun cacheRemoteGroups(context: Context, rawJson: String): Boolean {
        // Validate by attempting to parse into our model; reject junk so a bad deploy can't wipe
        // everyone's home screen.
        if (parseGroupsJson(rawJson) == null) return false
        val p = prefs(context)
        if (p.getString(KEY_REMOTE_CACHE, null) == rawJson) return false
        val editor = p.edit().putString(KEY_REMOTE_CACHE, rawJson)
        bumpVersion(p, editor)
        editor.apply()
        return true
    }

    /**
     * One-time migration: users from before the overlay model have their full edited list stored in
     * [KEY_GROUPS]. Convert it to an overlay (diffed against the current base) so their edits are
     * preserved, then delete the legacy key so we never migrate twice.
     */
    private fun migrateLegacyIfNeeded(context: Context) {
        val p = prefs(context)
        val legacy = p.getString(KEY_GROUPS, null) ?: return
        // If an overlay already exists, we've already migrated — just drop the stale legacy blob.
        if (p.getString(KEY_OVERLAY, null) != null) {
            p.edit().remove(KEY_GROUPS).apply()
            return
        }
        val legacyGroups = parseGroupsJson(legacy)
        if (legacyGroups == null) {
            p.edit().remove(KEY_GROUPS).apply()
            return
        }
        val overlay = computeOverlay(baseGroups(context), legacyGroups)
        p.edit()
            .putString(KEY_OVERLAY, overlayToJson(overlay))
            .remove(KEY_GROUPS)
            .apply()
    }
}
