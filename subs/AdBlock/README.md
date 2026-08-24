# Standalone AdBlock SDK for Android 🛡️

A fast, lightweight, standalone AdBlock library for Android applications. It parses EasyList, AdGuard, and ABP filter rules and blocks ads, banners, popups, and tracking scripts in **any WebView**.

---

## 🚀 Quick Setup (3 Steps)

### 1. Add to your `settings.gradle`
```groovy
include ':AdBlock'
project(':AdBlock').projectDir = new File(rootDir, 'subs/AdBlock/lib')
```

### 2. Add to your app's `build.gradle`
```groovy
dependencies {
    implementation project(':AdBlock')
}
```

### 3. Attach to ANY WebView in your app
```kotlin
import com.xhub.browser.adblock.AdBlockEngine

class MainActivity : AppCompatActivity() {

    private lateinit var adBlocker: AdBlockEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdBlock Engine
        adBlocker = AdBlockEngine.getInstance(this)

        val webView: WebView = findViewById(R.id.webView)
        
        // Attach to WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // Intercepts & blocks ad domains, popups, tracking scripts
                val blockedResponse = adBlocker.shouldBlock(request, view.url ?: "")
                return blockedResponse ?: super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl("https://example.com")
    }
}
```

---

## 📦 How to Export as an `.aar` Library

If you want to use this AdBlocker in another Android Studio project without copying source code:

Run this command in terminal:
```powershell
.\gradlew.bat :AdBlock:assembleRelease
```

The compiled **`.aar` library file** will be generated at:
`subs/AdBlock/lib/build/outputs/aar/lib-release.aar`

You can drop this `.aar` file into the `libs/` folder of any Android project!

---

## ✨ Features
- 🚫 Blocks EasyList & AdGuard filter rules
- ⚡ High Performance LRU byte-aware cache
- 🌐 Reusable across any WebView in any Android app
- 🔄 Zero dependencies on external browser frames
