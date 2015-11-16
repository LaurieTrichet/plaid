package io.plaidapp.data

import android.content.Context
import com.google.gson.GsonBuilder
import io.plaidapp.BuildConfig
import io.plaidapp.data.api.AuthInterceptor
import io.plaidapp.data.api.ClientAuthInterceptor
import io.plaidapp.data.api.designernews.DesignerNewsService
import io.plaidapp.data.api.dribbble.DribbbleService
import io.plaidapp.data.api.producthunt.ProductHuntService
import io.plaidapp.data.prefs.DesignerNewsPrefs
import io.plaidapp.data.prefs.DribbblePrefs
import retrofit.RestAdapter
import retrofit.converter.GsonConverter

abstract class BaseDataManager(context: Context) : DribbblePrefs.DribbbleLoginStatusListener,
        DesignerNewsPrefs.DesignerNewsLoginStatusListener {

    val designerNewsPrefs = DesignerNewsPrefs.get(context)

    var designerNewsApi = createDesignerNewsApi()
        private set

    val dribbblePrefs = DribbblePrefs.get(context)

    var dribbbleApi = createDribbbleApi()
        private set

    val productHuntApi = RestAdapter.Builder()
            .setEndpoint(ProductHuntService.ENDPOINT)
            .setRequestInterceptor(AuthInterceptor(BuildConfig.PROCUCT_HUNT_DEVELOPER_TOKEN))
            .build()
            .create(ProductHuntService::class.java)

    abstract fun onDataLoaded(data: List<PlaidItem>)

    protected fun setPage(items: List<PlaidItem>, page: Int) {
        items.forEach { it.page = page }
    }

    protected fun setDataSource(items: List<PlaidItem>, dataSource: String) {
        items.forEach { it.dataSource = dataSource }
    }

    private fun createDesignerNewsApi(): DesignerNewsService {
        return RestAdapter.Builder()
                .setEndpoint(DesignerNewsService.ENDPOINT)
                .setRequestInterceptor(ClientAuthInterceptor(designerNewsPrefs.accessToken,
                        BuildConfig.DESIGNER_NEWS_CLIENT_ID))
                .build()
                .create(DesignerNewsService::class.java)
    }

    private fun createDribbbleApi(): DribbbleService {
        return RestAdapter.Builder()
                .setEndpoint(DribbbleService.ENDPOINT)
                .setConverter(GsonConverter(GsonBuilder()
                        .setDateFormat(DribbbleService.DATE_FORMAT)
                        .create()))
                .setRequestInterceptor(AuthInterceptor(dribbblePrefs.accessToken))
                .build()
                .create(DribbbleService::class.java)
    }

    override fun onDribbbleLogin() {
        dribbbleApi = createDribbbleApi() // capture the auth token
    }

    override fun onDribbbleLogout() {
        dribbbleApi = createDribbbleApi() // clear the auth token
    }

    override fun onDesignerNewsLogin() {
        designerNewsApi = createDesignerNewsApi() // capture the auth token
    }

    override fun onDesignerNewsLogout() {
        designerNewsApi = createDesignerNewsApi() // clear the auth token
    }
}