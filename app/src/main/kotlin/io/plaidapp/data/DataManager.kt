package io.plaidapp.data

import android.content.Context
import io.plaidapp.data.api.dribbble.DribbbleSearch
import io.plaidapp.data.api.dribbble.DribbbleService
import io.plaidapp.data.prefs.SourceManager
import io.plaidapp.ui.FilterAdapter
import org.jetbrains.anko.async
import org.jetbrains.anko.uiThread
import retrofit.Callback
import retrofit.RetrofitError
import retrofit.client.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Responsible for loading data from the various sources. Instantiating classes are responsible for
 * providing the {code onDataLoaded} method to do something with the data.
 */
abstract class DataManager(context: Context, private val filterAdapter: FilterAdapter) :
        BaseDataManager(context), FilterAdapter.FiltersChangedListener, DataLoadingSubject {

    private val loadingCount = AtomicInteger(0)
    private var pageIndexes = filterAdapter.filters.map { it.key to 0 }.toMap()

    fun loadAllDataSources() = filterAdapter.filters.forEach { loadSource(it) }

    override fun isDataLoading() = loadingCount.get() > 0

    override fun onFiltersChanged(changedFilter: Source) {
        if (changedFilter.active) {
            loadSource(changedFilter)
        } else {
            pageIndexes += changedFilter.key to 0
        }
    }

    override fun onFilterRemoved(removed: Source?) {
        // no-op
    }

    private fun loadSource(source: Source) {
        if (source.active) {
            loadingCount.incrementAndGet()
            val page = getNextPageIndex(source.key)
            when (source.key) {
                SourceManager.SOURCE_DESIGNER_NEWS_POPULAR -> loadDesignerNewsTopStories(page)
                SourceManager.SOURCE_DESIGNER_NEWS_RECENT -> loadDesignerNewsRecent(page)
                SourceManager.SOURCE_DRIBBBLE_POPULAR -> loadDribbblePopular(page)
                SourceManager.SOURCE_DRIBBBLE_FOLLOWING -> loadDribbbleFollowing(page)
                SourceManager.SOURCE_DRIBBBLE_USER_LIKES -> loadDribbbleUserLikes(page)
                SourceManager.SOURCE_DRIBBBLE_USER_SHOTS -> loadDribbbleUserShots(page)
                SourceManager.SOURCE_DRIBBBLE_RECENT -> loadDribbbleRecent(page)
                SourceManager.SOURCE_DRIBBBLE_DEBUTS -> loadDribbbleDebuts(page)
                SourceManager.SOURCE_DRIBBBLE_ANIMATED -> loadDribbbleAnimated(page)
                SourceManager.SOURCE_PRODUCT_HUNT -> loadProductHunt(page)
                else -> when (source) {
                    is Source.DribbbleSearchSource -> loadDribbbleSearch(source, page)
                    is Source.DesignerNewsSearchSource -> loadDesignerNewsSearch(source, page)

                }
            }
        }
    }

    private fun getNextPageIndex(dataSource: String): Int {
        val nextPage = 1 + pageIndexes.getOrElse(dataSource) { 0 }
        pageIndexes += dataSource to nextPage
        return nextPage
    }

    private fun sourceIsEnabled(key: String) = pageIndexes[key] != 0

    private fun loadDesignerNewsTopStories(page: Int) {
        designerNewsApi.getTopStories(page,
                callback(page, SourceManager.SOURCE_DESIGNER_NEWS_POPULAR) { it.stories })
    }

    private fun loadDesignerNewsRecent(page: Int) {
        designerNewsApi.getRecentStories(page,
                callback(page, SourceManager.SOURCE_DESIGNER_NEWS_RECENT) { it.stories })
    }

    private fun loadDesignerNewsSearch(source: Source.DesignerNewsSearchSource, page: Int) {
        designerNewsApi.search(source.query, page,
                callback(page, source.key) { it.stories })
    }

    private fun loadDribbblePopular(page: Int) {
        dribbbleApi.getPopular(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_POPULAR) { it })
    }

    private fun loadDribbbleDebuts(page: Int) {
        dribbbleApi.getDebuts(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_DEBUTS) { it })
    }

    private fun loadDribbbleAnimated(page: Int) {
        dribbbleApi.getAnimated(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_ANIMATED) { it })
    }

    private fun loadDribbbleRecent(page: Int) {
        dribbbleApi.getRecent(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_RECENT) { it })
    }

    private fun loadDribbbleFollowing(page: Int) = dribbbleLogged {
        dribbbleApi.getFollowing(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_FOLLOWING) { it })
    }

    private fun loadDribbbleUserLikes(page: Int) = dribbbleLogged {
        dribbbleApi.getUserLikes(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_USER_LIKES) {
                    // API returns Likes but we just want the Shots
                    // these will be sorted like any other shot (popularity per page)
                    // TODO figure out a more appropriate sorting strategy for likes
                    it.map { it.shot }
                })
    }

    private fun loadDribbbleUserShots(page: Int) = dribbbleLogged {
        val user = dribbblePrefs.user
        dribbbleApi.getUserShots(page, DribbbleService.PER_PAGE_DEFAULT,
                callback(page, SourceManager.SOURCE_DRIBBBLE_USER_SHOTS) {
                    // this api call doesn't populate the shot user field but we need it
                    it.apply { forEach { it.user = user } }
                })
    }

    private fun loadDribbbleSearch(source: Source.DribbbleSearchSource, page: Int) = async {
        val items = DribbbleSearch.search(source.query, DribbbleSearch.SORT_RECENT, page)
        uiThread {
            if (items != null && items.size > 0) {
                setPage(items, page)
                setDataSource(items, source.key)
                onDataLoaded(items)
            }
            loadingCount.decrementAndGet();
        }
    }

    private fun loadProductHunt(page: Int) {
        // this API's paging is 0 based but this class (& sorting) is 1 based so adjust locally
        productHuntApi.getPosts(page - 1,
                callback(page, SourceManager.SOURCE_PRODUCT_HUNT) { it.posts })
    }

    private inline fun dribbbleLogged(code: () -> Unit) {
        if (dribbblePrefs.isLoggedIn) {
            code()
        } else {
            loadingCount.decrementAndGet()
        }
    }

    private inline fun <T> callback(page: Int, sourceKey: String,
                                    crossinline extract: (T) -> List<PlaidItem>)
            = retrofitCallback<T> { result, response ->
        if (sourceIsEnabled(sourceKey)) {
            val items = extract(result)
            setPage(items, page)
            setDataSource(items, sourceKey)
            onDataLoaded(items)
        }
    }

    private inline fun <T> retrofitCallback(crossinline code: (T, Response) -> Unit): Callback<T>
            = object : Callback<T> {
        override fun success(t: T?, response: Response) {
            t?.let { code(it, response) }
            loadingCount.decrementAndGet()
        }

        override fun failure(error: RetrofitError) {
            loadingCount.decrementAndGet()
        }
    }
}