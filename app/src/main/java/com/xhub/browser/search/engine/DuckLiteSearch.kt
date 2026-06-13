/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.xhub.browser.search.engine

import com.xhub.browser.R

/**
 * The DuckDuckGo Lite search engine.
 */
class DuckLiteSearch : BaseSearchEngine(
    "file:///android_asset/duckduckgo.webp",
    "https://duckduckgo.com/lite/?t=fulguris&q=",
    R.string.search_engine_duckduckgo_lite
)
