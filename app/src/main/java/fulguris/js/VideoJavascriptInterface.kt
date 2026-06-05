package fulguris.js

import android.webkit.JavascriptInterface
import fulguris.view.WebPageTab

class VideoJavascriptInterface(private val tab: WebPageTab) {

    /** Called by the new detection script (all stream types). */
    @JavascriptInterface
    fun onVideoDetected(url: String, qualitiesJson: String?, resolution: String?, streamType: String) {
        tab.onVideoDetected(url, qualitiesJson, resolution, streamType)
    }

    /** Legacy shim — new script calls onVideoDetected instead. */
    @JavascriptInterface
    fun onVideoPlaying(url: String, qualitiesJson: String?, resolution: String?) {
        // no-op: superseded by onVideoDetected
    }
}
