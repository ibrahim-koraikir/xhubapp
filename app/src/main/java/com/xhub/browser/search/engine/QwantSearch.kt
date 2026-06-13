/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.xhub.browser.search.engine

import com.xhub.browser.R

/**
 * The Qwant search engine.
 */
class QwantSearch : BaseSearchEngine(
    "file:///android_asset/qwant.webp",
    "https://www.qwant.com/?q=",
    R.string.search_engine_qwant
)
