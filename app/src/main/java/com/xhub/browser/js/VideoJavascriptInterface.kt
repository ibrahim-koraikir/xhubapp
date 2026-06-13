package com.xhub.browser.js

import android.webkit.JavascriptInterface
import com.xhub.browser.view.WebPageTab

class VideoJavascriptInterface(private val tab: WebPageTab) {

    /** Called by the detection script (all stream types). */
    @JavascriptInterface
    fun onVideoDetected(url: String, qualitiesJson: String?, resolution: String?, streamType: String) {
        tab.onVideoDetected(url, qualitiesJson, resolution, streamType)
    }
}
