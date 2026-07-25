package com.xhub.browser

import android.app.Application
import java.lang.reflect.Field

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The global `app: App` lateinit var is used by DomainPreferences.fileName()
        // to resolve shared_prefs paths. In tests, we initialize it via reflection
        // since TestApplication is not a subclass of App (which requires Hilt).
        // Only Application-level APIs are called on `app` in the test code path,
        // so this is safe.
        try {
            val appKtClass = Class.forName("com.xhub.browser.AppKt")
            val field: Field = appKtClass.getDeclaredField("app")
            field.isAccessible = true
            field.set(null, this)
        } catch (e: Exception) {
            // Best-effort; tests that don't need DomainPreferences will still run
        }
    }
}