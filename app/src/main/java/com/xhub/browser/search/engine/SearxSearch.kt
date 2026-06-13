/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.xhub.browser.search.engine

import com.xhub.browser.R

/**
 * The Searx search engine.
 */
class SearxSearch : BaseSearchEngine(
    "file:///android_asset/searx.webp",
    "https://searx.prvcy.eu/search?q=",
    R.string.search_engine_searx
)
