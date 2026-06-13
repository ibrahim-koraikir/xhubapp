package com.xhub.browser.search

import com.xhub.browser.di.SuggestionsClient
import com.xhub.browser.settings.preferences.UserPreferences
import android.app.Application
import dagger.Reusable
import com.xhub.browser.search.engine.AskSearch
import com.xhub.browser.search.engine.BaiduSearch
import com.xhub.browser.search.engine.BaseSearchEngine
import com.xhub.browser.search.engine.BingSearch
import com.xhub.browser.search.engine.BraveSearch
import com.xhub.browser.search.engine.CustomSearch
import com.xhub.browser.search.engine.DuckLiteNoJSSearch
import com.xhub.browser.search.engine.DuckLiteSearch
import com.xhub.browser.search.engine.DuckNoJSSearch
import com.xhub.browser.search.engine.DuckSearch
import com.xhub.browser.search.engine.EcosiaSearch
import com.xhub.browser.search.engine.EkoruSearch
import com.xhub.browser.search.engine.GoogleSearch
import com.xhub.browser.search.engine.MojeekSearch
import com.xhub.browser.search.engine.NaverSearch
import com.xhub.browser.search.engine.QwantLiteSearch
import com.xhub.browser.search.engine.QwantSearch
import com.xhub.browser.search.engine.SearxSearch
import com.xhub.browser.search.engine.StartPageMobileSearch
import com.xhub.browser.search.engine.StartPageSearch
import com.xhub.browser.search.engine.YahooNoJSSearch
import com.xhub.browser.search.engine.YahooSearch
import com.xhub.browser.search.engine.YandexSearch
import com.xhub.browser.search.suggestions.BaiduSuggestionsModel
import com.xhub.browser.search.suggestions.DuckSuggestionsModel
import com.xhub.browser.search.suggestions.GoogleSuggestionsModel
import com.xhub.browser.search.suggestions.NaverSuggestionsModel
import com.xhub.browser.search.suggestions.NoOpSuggestionsRepository
import com.xhub.browser.search.suggestions.RequestFactory
import com.xhub.browser.search.suggestions.SuggestionsRepository
import io.reactivex.Single
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * The model that provides the search engine based
 * on the user's preference.
 */
@Reusable
class SearchEngineProvider @Inject constructor(
    private val userPreferences: UserPreferences,
    @SuggestionsClient private val okHttpClient: Single<OkHttpClient>,
    private val requestFactory: RequestFactory,
    private val application: Application
) {

    /**
     * Provide the [SuggestionsRepository] that maps to the user's current preference.
     */
    fun provideSearchSuggestions(): SuggestionsRepository =
        when (userPreferences.searchSuggestionChoice) {
            0 -> NoOpSuggestionsRepository()
            1 -> GoogleSuggestionsModel(okHttpClient, requestFactory, application, userPreferences)
            2 -> DuckSuggestionsModel(okHttpClient, requestFactory, application, userPreferences)
            3 -> BaiduSuggestionsModel(okHttpClient, requestFactory, application, userPreferences)
            4 -> NaverSuggestionsModel(okHttpClient, requestFactory, application, userPreferences)
            else -> GoogleSuggestionsModel(okHttpClient, requestFactory, application, userPreferences)
        }

    /**
     * Provide the [BaseSearchEngine] that maps to the user's current preference.
     */
    fun provideSearchEngine(): BaseSearchEngine =
        when (userPreferences.searchChoice) {
            0 -> CustomSearch(userPreferences.searchUrl, userPreferences)
            1 -> GoogleSearch()
            2 -> AskSearch()
            3 -> BaiduSearch()
            4 -> BingSearch()
            5 -> BraveSearch()
            6 -> DuckSearch()
            7 -> DuckNoJSSearch()
            8 -> DuckLiteSearch()
            9 -> DuckLiteNoJSSearch()
            10 -> EcosiaSearch()
            11 -> EkoruSearch()
            12 -> MojeekSearch()
            13 -> NaverSearch()
            14 -> QwantSearch()
            15 -> QwantLiteSearch()
            16 -> SearxSearch()
            17 -> StartPageSearch()
            18 -> StartPageMobileSearch()
            19 -> YahooSearch()
            20 -> YahooNoJSSearch()
            21 -> YandexSearch()
            else -> GoogleSearch()
        }

    /**
     * Return the serializable index of of the provided [BaseSearchEngine].
     */
    fun mapSearchEngineToPreferenceIndex(searchEngine: BaseSearchEngine): Int =
        when (searchEngine) {
            is CustomSearch -> 0
            is GoogleSearch -> 1
            is AskSearch -> 2
            is BaiduSearch -> 3
            is BingSearch -> 4
            is BraveSearch -> 5
            is DuckSearch -> 6
            is DuckNoJSSearch -> 7
            is DuckLiteSearch -> 8
            is DuckLiteNoJSSearch -> 9
            is EcosiaSearch -> 10
            is EkoruSearch -> 11
            is MojeekSearch -> 12
            is NaverSearch -> 13
            is QwantSearch -> 14
            is QwantLiteSearch -> 15
            is SearxSearch -> 16
            is StartPageSearch -> 17
            is StartPageMobileSearch -> 18
            is YahooSearch -> 19
            is YahooNoJSSearch -> 20
            is YandexSearch -> 21
            else -> throw UnsupportedOperationException("Unknown search engine provided: " + searchEngine.javaClass)
        }

    /**
     * Provide a list of all supported search engines.
     */
    fun provideAllSearchEngines(): List<BaseSearchEngine> = listOf(
        CustomSearch(userPreferences.searchUrl, userPreferences),
        GoogleSearch(),
        AskSearch(),
        BaiduSearch(),
        BingSearch(),
        BraveSearch(),
        DuckSearch(),
        DuckNoJSSearch(),
        DuckLiteSearch(),
        DuckLiteNoJSSearch(),
        EcosiaSearch(),
        EkoruSearch(),
        MojeekSearch(),
        NaverSearch(),
        QwantSearch(),
        QwantLiteSearch(),
        SearxSearch(),
        StartPageSearch(),
        StartPageMobileSearch(),
        YahooSearch(),
        YahooNoJSSearch(),
        YandexSearch()
    )

}
