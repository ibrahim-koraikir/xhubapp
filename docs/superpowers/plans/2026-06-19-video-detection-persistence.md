# Video Detection Persistence Implementation Plan

**Goal:** Ensure that video detection works reliably across multiple video plays, page refreshes, subsequent site navigations, and dynamic video content changes (SPAs/lazy loading).
**Architecture:** 
1. Make `clearVideoDetectedState()` public and call it on `onPageStarted` to clear previous states on fresh page loads/refreshes.
2. Centralize video sniffer script injection in a new `injectVideoSniffer()` method in `WebPageTab` which is guarded by `window._vdInit` in JS.
3. Inject the sniffer from both `onPageFinished` and `onProgressChanged` (at 100%) to ensure it is always loaded.
4. Enhance the JS sniffer to observe `src` attribute changes, handle extra HTML5 events (`play`, `loadeddata`, `canplay`), and add a 2-second fallback interval scan.
**Tech Stack:** Kotlin, Android WebView (evaluateJavascript, MutationObserver, DOM events)

---

### Task 1: WebPageTab Refactor and Unit Test

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/view/WebPageTab.kt`
- Modify: `app/src/test/java/com/xhub/browser/view/WebPageTabTest.kt`

- [ ] **Step 1: Write the failing test**
  Add the following test case inside `WebPageTabTest.kt` to verify that `clearVideoDetectedState()` resets the video detection properties and that it's accessible.
  ```kotlin
      @Test
      fun `WebPageTab clearVideoDetectedState resets video parameters`() {
          val activity = mock(TestActivity::class.java)
          `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
          `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
          `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
          `when`(activity.resources).thenReturn(realActivity.resources)
          `when`(activity.packageName).thenReturn(realActivity.packageName)
          `when`(activity.theme).thenReturn(realActivity.theme)
          `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
          `when`(activity.getString(anyInt())).thenAnswer { invocation ->
              realActivity.getString(invocation.getArgument(0))
          }
          `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
              realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
          }
          `when`(activity.application).thenReturn(realActivity.application)

          val mockTabInitializer = mock(TabInitializer::class.java)
          `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

          val tab = WebPageTab(
              activity = activity,
              tabInitializer = mockTabInitializer,
              isIncognito = false,
              homePageInitializer = mock(HomePageInitializer::class.java),
              incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
              bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
              downloadPageInitializer = mock(DownloadPageInitializer::class.java),
              historyPageInitializer = mock(HistoryPageInitializer::class.java)
          )

          // Simulate video detected
          tab.onVideoDetected("https://test.com/video.mp4", null, "720p", "direct")
          assertThat(tab.isVideoDetected).isTrue()
          assertThat(tab.detectedVideoUrl).isEqualTo("https://test.com/video.mp4")

          // Clear it
          tab.clearVideoDetectedState()
          assertThat(tab.isVideoDetected).isFalse()
          assertThat(tab.detectedVideoUrl).isNull()
      }
  ```
- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "com.xhub.browser.view.WebPageTabTest"`
  Expected: FAIL (because `clearVideoDetectedState` is private and cannot be resolved)
- [ ] **Step 3: Make clearVideoDetectedState public**
  In `WebPageTab.kt`, change:
  ```kotlin
      private fun clearVideoDetectedState() {
  ```
  to:
  ```kotlin
      fun clearVideoDetectedState() {
  ```
- [ ] **Step 4: Run test to verify it passes**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "com.xhub.browser.view.WebPageTabTest"`
  Expected: PASS
- [ ] **Step 5: Build and verify**
  Run: `.\gradlew.bat assembleXhubFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

---

### Task 2: Implement injectVideoSniffer in WebPageTab

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/view/WebPageTab.kt`

- [ ] **Step 1: Write the failing test**
  Add a test to verify `injectVideoSniffer()` works.
  ```kotlin
      @Test
      fun `WebPageTab injectVideoSniffer executes javascript on webView when enabled`() {
          `when`(userPreferences.videoDetectionEnabled).thenReturn(true)
          val activity = mock(TestActivity::class.java)
          `when`(activity.applicationInfo).thenReturn(realActivity.applicationInfo)
          `when`(activity.applicationContext).thenReturn(realActivity.applicationContext)
          `when`(activity.layoutInflater).thenReturn(realActivity.layoutInflater)
          `when`(activity.resources).thenReturn(realActivity.resources)
          `when`(activity.packageName).thenReturn(realActivity.packageName)
          `when`(activity.theme).thenReturn(realActivity.theme)
          `when`(activity.getDir(any(), anyInt())).thenReturn(realActivity.getDir("test", 0))
          `when`(activity.getString(anyInt())).thenAnswer { invocation ->
              realActivity.getString(invocation.getArgument(0))
          }
          `when`(activity.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
              realActivity.getSharedPreferences(invocation.getArgument(0), invocation.getArgument(1))
          }
          `when`(activity.application).thenReturn(realActivity.application)

          val mockTabInitializer = mock(TabInitializer::class.java)
          `when`(mockTabInitializer.url()).thenReturn(Uris.FulgurisHome)

          val tab = WebPageTab(
              activity = activity,
              tabInitializer = mockTabInitializer,
              isIncognito = false,
              homePageInitializer = mock(HomePageInitializer::class.java),
              incognitoPageInitializer = mock(IncognitoPageInitializer::class.java),
              bookmarkPageInitializer = mock(BookmarkPageInitializer::class.java),
              downloadPageInitializer = mock(DownloadPageInitializer::class.java),
              historyPageInitializer = mock(HistoryPageInitializer::class.java)
          )

          // Since webView is created, let's spy on it or call injectVideoSniffer
          tab.injectVideoSniffer()
          // Should not crash and successfully check settings
          verify(userPreferences).videoDetectionEnabled
      }
  ```
- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "com.xhub.browser.view.WebPageTabTest"`
  Expected: FAIL (because `injectVideoSniffer` does not exist on `WebPageTab`)
- [ ] **Step 3: Implement injectVideoSniffer with upgraded JS**
  In `WebPageTab.kt`, implement the method:
  ```kotlin
      fun injectVideoSniffer() {
          if (!userPreferences.videoDetectionEnabled) return
          val view = webView ?: return
          
          val videoScript = """
              (function() {
                  if (window._vdInit) return;
                  window._vdInit = true;

                  var anchorQualities = null;

                  function classifyUrl(url) {
                      if (!url) return 'unknown';
                      if (url.startsWith('blob:')) return 'blob';
                      if (url.indexOf('.m3u8') !== -1) return 'hls';
                      if (url.indexOf('.mpd') !== -1) return 'dash';
                      if (/^https?:\/\//i.test(url)) return 'direct';
                      return 'unknown';
                  }

                  function scanAnchorsOnce() {
                      if (anchorQualities !== null) return anchorQualities;
                      anchorQualities = {};
                      var anchors = document.querySelectorAll('a[href]');
                      for (var j = 0; j < anchors.length; j++) {
                          var href = anchors[j].href || '';
                          if (/\.(mp4|webm|m4v|ogv|mkv)(\?|${'$'})/i.test(href)) {
                              var aLabel = anchors[j].getAttribute('data-res')
                                  || anchors[j].getAttribute('label')
                                  || anchors[j].textContent.trim().substring(0, 30)
                                  || 'Download ' + (j + 1);
                              anchorQualities[aLabel] = href;
                          }
                      }
                      return anchorQualities;
                  }

                  function buildQualities(video) {
                      var qualities = {};
                      var sources = video.querySelectorAll('source');
                      for (var i = 0; i < sources.length; i++) {
                          var s = sources[i];
                          var sUrl = s.src || s.getAttribute('src') || '';
                          if (!sUrl) continue;
                          var label = s.getAttribute('label')
                              || s.getAttribute('title')
                              || s.getAttribute('data-res')
                              || s.getAttribute('res')
                              || s.getAttribute('size')
                              || (video.videoHeight > 0 ? video.videoHeight + 'p' : null)
                              || ('Source ' + (i + 1));
                          qualities[label] = sUrl;
                      }
                      var anchorLinks = scanAnchorsOnce();
                      for (var key in anchorLinks) {
                          if (anchorLinks.hasOwnProperty(key)) {
                              qualities[key] = anchorLinks[key];
                          }
                      }
                      return qualities;
                  }

                  function reportVideo(video) {
                      var url = video.currentSrc || video.src || '';
                      if (!url) return;
                      
                      video._vdLast = url;
                      
                      var streamType = classifyUrl(url);
                      var qualities = buildQualities(video);
                      if (Object.keys(qualities).length === 0) {
                          qualities['Default'] = url;
                      }
                      var resolution = (video.videoHeight > 0) ? video.videoHeight + 'p' : '';
                      if (window.VideoSniffer) {
                          window.VideoSniffer.onVideoDetected(
                              url,
                              JSON.stringify(qualities),
                              resolution,
                              streamType
                          );
                      }
                  }

                  function scanAllVideos() {
                      var videos = document.querySelectorAll('video');
                      if (videos.length === 0) return;
                      
                      for (var i = 0; i < videos.length; i++) {
                          var v = videos[i];
                          
                          if (!v._vdSet) {
                              v._vdSet = true;
                              v.addEventListener('loadedmetadata', function() { reportVideo(this); });
                              v.addEventListener('playing', function() { reportVideo(this); });
                              v.addEventListener('play', function() { reportVideo(this); });
                              v.addEventListener('loadeddata', function() { reportVideo(this); });
                              v.addEventListener('canplay', function() { reportVideo(this); });
                              if (v.readyState >= 1 && (v.currentSrc || v.src)) {
                                  reportVideo(v);
                              }
                          }
                          
                          var cur = v.currentSrc || v.src;
                          if (cur && cur !== v._vdLast) {
                              reportVideo(v);
                          }
                      }
                  }

                  var navHandler = function() {
                      anchorQualities = null;
                      setTimeout(scanAllVideos, 300);
                  };
                  window._vdNav = navHandler;
                  window.addEventListener('popstate', navHandler);
                  window.addEventListener('hashchange', navHandler);

                  scanAllVideos();

                  var debounceTimer = null;
                  var observer = new MutationObserver(function() {
                      clearTimeout(debounceTimer);
                      debounceTimer = setTimeout(scanAllVideos, 500);
                  });
                  observer.observe(document.documentElement, {
                      childList: true,
                      subtree: true,
                      attributes: true,
                      attributeFilter: ['src']
                  });
                  window._vdObs = observer;

                  // Periodic backup scan
                  setInterval(scanAllVideos, 2000);
              })();
          """.trimIndent()
          view.evaluateJavascript(videoScript, null)
      }
  ```
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Build and verify**
  Run: `.\gradlew.bat assembleXhubFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

---

### Task 3: Integrate with WebPageClient & WebPageChromeClient Lifecycles

**Files:**
- Modify: `app/src/main/java/com/xhub/browser/view/WebPageClient.kt`
- Modify: `app/src/main/java/com/xhub/browser/view/WebPageChromeClient.kt`

- [ ] **Step 1: Reset state on Page Start**
  In `WebPageClient.kt` `onPageStarted`, clear video detected state:
  ```kotlin
      override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
          Timber.i("$ihs : onPageStarted - $url")
          webPageTab.clearVideoDetectedState()
          onPageFinishedDone = false
          ...
  ```
- [ ] **Step 2: Inject in onPageFinished**
  In `WebPageClient.kt` `onPageFinished`, replace the old sniffer injection block with `webPageTab.injectVideoSniffer()`.
- [ ] **Step 3: Inject in onProgressChanged**
  In `WebPageChromeClient.kt` `onProgressChanged`, when `newProgress == 100`, call `webPageTab.injectVideoSniffer()`:
  ```kotlin
      override fun onProgressChanged(view: WebView, newProgress: Int) {
          Timber.v("onProgressChanged: $newProgress")

          webBrowser.onProgressChanged(webPageTab, newProgress)
          
          if (newProgress == 100 && userPreferences.videoDetectionEnabled) {
              webPageTab.injectVideoSniffer()
          }
          ...
  ```
- [ ] **Step 4: Build and verify**
  Run: `.\gradlew.bat clean assembleXhubFullDownloadDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 5: Run unit tests**
  Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest`
  Expected: BUILD SUCCESSFUL
