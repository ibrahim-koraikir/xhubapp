/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.xhub.browser.search.engine

import com.xhub.browser.R

/**
 * The Brave search engine.
 */
class BraveSearch : BaseSearchEngine(
    "file:///android_asset/brave.webp",
    "https://search.brave.com/search?q=",
    R.string.search_engine_brave
)
