package io.plaidapp.ui

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AnimationUtils
import android.widget.ActionMenuView
import android.widget.ImageView
import android.widget.TextView
import io.plaidapp.BuildConfig
import io.plaidapp.R
import io.plaidapp.data.DataManager
import io.plaidapp.data.PlaidItem
import io.plaidapp.data.Source
import io.plaidapp.data.api.ClientAuthInterceptor
import io.plaidapp.data.api.designernews.DesignerNewsService
import io.plaidapp.data.api.designernews.model.NewStoryRequest
import io.plaidapp.data.api.designernews.model.StoriesResponse
import io.plaidapp.data.pocket.PocketUtils
import io.plaidapp.data.prefs.DesignerNewsPrefs
import io.plaidapp.data.prefs.DribbblePrefs
import io.plaidapp.data.prefs.SourceManager
import io.plaidapp.ui.recyclerview.FilterTouchHelperCallback
import io.plaidapp.ui.recyclerview.InfiniteScrollListener
import io.plaidapp.ui.transitions.FabDialogMorphSetup
import io.plaidapp.util.AnimUtils
import io.plaidapp.util.ViewUtils
import io.plaidapp.util.addOnScrollListener
import io.plaidapp.util.setSpanSizeLookup
import kotlinx.android.synthetic.activity_home.*
import org.jetbrains.anko.*
import retrofit.Callback
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Response
import java.security.InvalidParameterException

class HomeActivity : Activity() {

    companion object {
        private val RC_SEARCH = 0
        private val RC_AUTH_DRIBBBLE_FOLLOWING = 1
        private val RC_AUTH_DRIBBBLE_USER_LIKES = 2
        private val RC_AUTH_DRIBBBLE_USER_SHOTS = 3
        private val RC_NEW_DESIGNER_NEWS_STORY = 4
        private val RC_NEW_DESIGNER_NEWS_LOGIN = 5
    }

    private var noFilterEmptyInflated = false
    private val noFilterEmptyText by lazy {
        // create the no filters empty text
        (stub_no_filters.inflate() as TextView).apply {
            val emptyText = getString(R.string.no_filters_selected)
            val filterPlaceholderStart = emptyText.indexOf('\u08B4')
            val altMethodStart = filterPlaceholderStart + 3
            text = SpannableStringBuilder(emptyText).apply {
                // show an image of the filter icon
                setSpan(ImageSpan(ctx, R.drawable.ic_filter_small, ImageSpan.ALIGN_BASELINE),
                        filterPlaceholderStart,
                        filterPlaceholderStart + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // make the alt method (swipe from right) less prominent and italic
                setSpan(ForegroundColorSpan(
                        ContextCompat.getColor(ctx, R.color.text_secondary_light)),
                        altMethodStart,
                        emptyText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.ITALIC),
                        altMethodStart,
                        emptyText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            onClick { drawer.openDrawer(GravityCompat.END) }
        }
    }

    // Data
    private val dribbblePrefs by lazy { DribbblePrefs.get(ctx) }
    private val designerNewsPrefs by lazy { DesignerNewsPrefs.get(ctx) }

    private val filtersAdapter by lazy {
        FilterAdapter(ctx, SourceManager.getSources(ctx)) { sharedElement, forSource ->
            val login = intentFor<DribbbleLogin>(FabDialogMorphSetup.EXTRA_SHARED_ELEMENT_START_COLOR to
                    ContextCompat.getColor(ctx, R.color.background_dark))
            val options = ActivityOptions.makeSceneTransitionAnimation(this,
                    sharedElement, getString(R.string.transition_dribbble_login))
            startActivityForResult(login, getAuthSourceRequestCode(forSource), options.toBundle())
        }
    }

    // Scroll
    private var gridScrollY = 0

    private val feedAdapter: FeedAdapter by lazy { FeedAdapter(this, dataManager, PocketUtils.isPocketInstalled(ctx)) }

    private val dataManager by lazy {
        object : DataManager(this, filtersAdapter) {
            override fun onDataLoaded(data: List<PlaidItem>) {
                feedAdapter.addAndResort(data)
                checkEmptyState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        drawer.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setActionBar(toolbar)
        if (savedInstanceState == null) {
            animateToolbar()
        }

        stories_grid.apply {
            adapter = feedAdapter
            val columns = resources.getInteger(R.integer.num_columns)
            val gridManager = GridLayoutManager(ctx, columns).apply {
                setSpanSizeLookup { pos -> if (pos == feedAdapter.dataItemCount) columns else 1 }
            }
            layoutManager = gridManager
            addOnScrollListener { recycler, dx, dy ->
                gridScrollY += dy
                if (gridScrollY > 0 && toolbar.translationZ != -1f) {
                    toolbar.translationZ = -1f
                } else if (gridScrollY == 0 && toolbar.translationZ != 0f) {
                    toolbar.translationZ = 0f
                }
            }
            addOnScrollListener(object : InfiniteScrollListener(gridManager, dataManager) {
                override fun onLoadMore() = dataManager.loadAllDataSources()
            })
            setHasFixedSize(true)
        }

        // drawer layout treats fitsSystemWindows specially so we have to handle insets ourselves
        drawer.setOnApplyWindowInsetsListener { v, insets ->
            // inset the toolbar down by the status bar height
            toolbar.layoutParams = (toolbar.layoutParams as MarginLayoutParams).apply {
                topMargin += insets.systemWindowInsetTop
                rightMargin += insets.systemWindowInsetRight
            }

            // inset the grid top by statusbar+toolbar & the bottom by the navbar (don't clip)
            stories_grid.apply {
                setPadding(paddingLeft,
                        insets.systemWindowInsetTop + ViewUtils.getActionBarSize(ctx),
                        paddingRight + insets.systemWindowInsetRight,
                        paddingBottom)
            }

            // inset the fab for the navbar
            fab.layoutParams = (fab.layoutParams as MarginLayoutParams).apply {
                topMargin += insets.systemWindowInsetTop
                rightMargin += insets.systemWindowInsetRight
            }

            // we place a background behind the status bar to combine with it's semi-transparent
            // color to get the desired appearance.  Set it's height to the status bar height
            status_bar_background.layoutParams = (status_bar_background.layoutParams).apply {
                height = insets.systemWindowInsetTop
            }

            // inset the filters list for the status bar / navbar
            // need to set the padding end for landscape case
            filters.apply {
                val ltr = layoutDirection == View.LAYOUT_DIRECTION_LTR
                setPaddingRelative(paddingStart,
                        paddingTop + insets.systemWindowInsetTop,
                        paddingEnd + (if (ltr) insets.systemWindowInsetRight else 0),
                        paddingBottom + insets.systemWindowInsetBottom)
            }

            // clear this listener so insets aren't re-applied
            drawer.setOnApplyWindowInsetsListener(null)

            insets.consumeSystemWindowInsets()
        }

        setupTaskDescription()

        filters.adapter = filtersAdapter
        filtersAdapter.addFilterChangedListener(filtersChangedListener)
        filtersAdapter.addFilterChangedListener(dataManager)
        dataManager.loadAllDataSources()
        val callback = FilterTouchHelperCallback(filtersAdapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(filters)
        checkEmptyState()
        checkConnectivity()

        fab.onClick { fabClick() }
    }

    private val filtersChangedListener = object : FilterAdapter.FiltersChangedListener {
        override fun onFiltersChanged(changedFilter: Source) {
            if (!changedFilter.active) {
                feedAdapter.removeDataSource(changedFilter.key)
            }
            checkEmptyState()
        }

        override fun onFilterRemoved(removed: Source) {
            feedAdapter.removeDataSource(removed.key)
            checkEmptyState()
        }

    }

    private fun fabClick() {
        if (designerNewsPrefs.isLoggedIn) {
            val intent = intentFor<PostNewDesignerNewsStory>(FabDialogMorphSetup.EXTRA_SHARED_ELEMENT_START_COLOR
                    to ContextCompat.getColor(ctx, R.color.accent))
            val options = ActivityOptions.makeSceneTransitionAnimation(this, fab,
                    getString(R.string.transition_new_designer_news_post))
            startActivityForResult(intent, RC_NEW_DESIGNER_NEWS_STORY, options.toBundle())
        } else {
            val intent = intentFor<DesignerNewsLogin>(FabDialogMorphSetup.EXTRA_SHARED_ELEMENT_START_COLOR to
                    ContextCompat.getColor(ctx, R.color.accent))
            val options = ActivityOptions.makeSceneTransitionAnimation(this, fab,
                    getString(R.string.transition_designer_news_login))
            startActivityForResult(intent, RC_NEW_DESIGNER_NEWS_LOGIN, options.toBundle())
        }
    }

    private fun checkEmptyState() {
        if (feedAdapter.dataItemCount == 0) {
            if (filtersAdapter.enabledSourcesCount > 0) {
                loading.visibility = View.VISIBLE
                setNoFiltersEmptyTextVisibility(View.GONE)
            } else {
                loading.visibility = View.GONE
                setNoFiltersEmptyTextVisibility(View.VISIBLE)
            }
            gridScrollY = 0
            toolbar.translationZ = 0f
        } else {
            loading.visibility = View.GONE
            setNoFiltersEmptyTextVisibility(View.GONE)
        }
    }

    private fun setNoFiltersEmptyTextVisibility(visibility: Int) {
        if (visibility == View.VISIBLE || noFilterEmptyInflated) {
            noFilterEmptyText.visibility = visibility
            noFilterEmptyInflated = true
        }
    }

    private fun setupTaskDescription() {
        // set a silhouette icon in overview as the launcher icon is a bit busy
        // and looks bad on top of colorPrimary
        //Bitmap overviewIcon = ImageUtils.vectorToBitmap(this, R.drawable.ic_launcher_silhouette);
        // TODO replace launcher icon with a monochrome version from RN.
        val overviewIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name),
                overviewIcon,
                ContextCompat.getColor(ctx, R.color.primary)))
        overviewIcon.recycle()
    }

    override fun onResume() {
        super.onResume()
        dribbblePrefs.addLoginStatusListener(dataManager)
        dribbblePrefs.addLoginStatusListener(filtersAdapter)
    }

    override fun onPause() {
        dribbblePrefs.removeLoginStatusListener(dataManager)
        dribbblePrefs.removeLoginStatusListener(filtersAdapter)
        super.onPause()
    }

    private fun animateToolbar() {
        // this is gross but toolbar doesn't expose it's children to animate them :(
        val title = toolbar.getChildAt(0) as? TextView
        title?.apply {

            // fade in and space out the title.  Animating the letterSpacing performs horribly so
            // fake it by setting the desired letterSpacing then animating the scaleX ¯\_(ツ)_/¯
            alpha = 0f
            scaleX = 0.8f

            animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .setStartDelay(300)
                    .setDuration(900)
                    .setInterpolator(AnimUtils.getMaterialInterpolator(ctx))
        }

        val amv = toolbar.getChildAt(1) as? ActionMenuView
        amv?.apply {
            popAnim(getChildAt(0), 500, 200)
        }
    }

    private fun popAnim(view: View?, startDelay: Long, duration: Long) = view?.apply {
        alpha = 0f
        scaleX = 0f
        scaleY = 0f

        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(startDelay)
                .setDuration(duration)
                .setInterpolator(AnimationUtils.loadInterpolator(ctx,
                        android.R.interpolator.overshoot))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_dribbble_login)?.apply {
            setTitle(if (dribbblePrefs.isLoggedIn) R.string.dribbble_log_out else R.string.dribbble_login)
        }
        menu.findItem(R.id.menu_designer_news_login)?.apply {
            setTitle(if (designerNewsPrefs.isLoggedIn) R.string.designer_news_log_out else R.string.designer_news_login)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_filter -> {
            drawer.openDrawer(GravityCompat.END);
            true
        }
        R.id.menu_search -> {
            // get the icon's location on screen to pass through to the search screen
            val searchMenuView = toolbar.findViewById(R.id.menu_search)
            var loc = IntArray(2)
            searchMenuView.getLocationOnScreen(loc)
            startActivityForResult(SearchActivity.createStartIntent(this, loc[0], loc[0] +
                    (searchMenuView.width / 2)), RC_SEARCH, ActivityOptions
                    .makeSceneTransitionAnimation(this).toBundle())
            searchMenuView.alpha = 0f
            true
        }
        R.id.menu_dribbble_login -> {
            if (!dribbblePrefs.isLoggedIn) {
                dribbblePrefs.login(ctx)
            } else {
                dribbblePrefs.logout()
                toast(R.string.dribbble_logged_out)
            }
            true
        }
        R.id.menu_designer_news_login -> {
            if (!designerNewsPrefs.isLoggedIn) {
                startActivity<DesignerNewsLogin>()
            } else {
                designerNewsPrefs.logout()
                toast(R.string.designer_news_logged_out)
            }
            true
        }
        R.id.menu_about -> {
            startActivity<AboutActivity>()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_SEARCH -> {
                // reset the search icon which we hid
                val searchMenuView = toolbar.findViewById(R.id.menu_search)
                searchMenuView?.alpha = 1f

                if (resultCode == SearchActivity.RESULT_CODE_SAVE && data != null) {
                    val query = data.getStringExtra(SearchActivity.EXTRA_QUERY)
                    if (TextUtils.isEmpty(query)) return
                    var dribbleSearch: Source? = null
                    var designerNewsSearch: Source? = null
                    var newSource = false
                    if (data.getBooleanExtra(SearchActivity.EXTRA_SAVE_DRIBBBLE, false)) {
                        dribbleSearch = Source.DribbbleSearchSource(query, true)
                        newSource = filtersAdapter.addFilter(dribbleSearch)
                    }
                    if (data.getBooleanExtra(SearchActivity.EXTRA_SAVE_DESIGNER_NEWS, false)) {
                        designerNewsSearch = Source.DesignerNewsSearchSource(query, true)
                        newSource = newSource || filtersAdapter.addFilter(dribbleSearch)
                    }
                    if (newSource && (dribbleSearch != null || designerNewsSearch != null)) {
                        highlightNewSources(dribbleSearch, designerNewsSearch)
                    }
                }
            }
            RC_NEW_DESIGNER_NEWS_STORY -> {
                if (resultCode == PostNewDesignerNewsStory.RESULT_DRAG_DISMISSED) {
                    // need to reshow the FAB as there's no shared element transition
                    showFab()
                } else if (resultCode == PostNewDesignerNewsStory.RESULT_POST && data != null) {
                    val title = data.getStringExtra(PostNewDesignerNewsStory.EXTRA_STORY_TITLE)
                    val url = data.getStringExtra(PostNewDesignerNewsStory.EXTRA_STORY_URL)
                    val comment = data.getStringExtra(PostNewDesignerNewsStory.EXTRA_STORY_COMMENT)
                    if (!TextUtils.isEmpty(title)) {
                        var storyToPost: NewStoryRequest? = null
                        if (!TextUtils.isEmpty(url)) {
                            storyToPost = NewStoryRequest.createWithUrl(title, url)
                        } else if (!TextUtils.isEmpty(comment)) {
                            storyToPost = NewStoryRequest.createWithComment(title, comment)
                        }
                        storyToPost?.let {
                            // TODO: move this to a service in follow up CL?
                            val designerNewsApi = RestAdapter.Builder()
                                    .setEndpoint(DesignerNewsService.ENDPOINT)
                                    .setRequestInterceptor(ClientAuthInterceptor(
                                            designerNewsPrefs.accessToken,
                                            BuildConfig.DESIGNER_NEWS_CLIENT_ID))
                                    .build()
                                    .create(DesignerNewsService::class.java)
                            designerNewsApi.postStory(storyToPost, object : Callback<StoriesResponse> {
                                override fun success(story: StoriesResponse?, response: Response?) {
                                    val id = story?.stories?.get(0)?.id
                                }

                                override fun failure(error: RetrofitError?) {
                                    Log.e("HomeActivity", "Failed posting story", error)
                                }

                            })
                        }
                    }
                }
            }

            RC_NEW_DESIGNER_NEWS_LOGIN -> {
                if (resultCode == RESULT_OK) showFab()
            }
            RC_AUTH_DRIBBBLE_FOLLOWING -> {
                if (resultCode == RESULT_OK) {
                    filtersAdapter.enableFilterByKey(SourceManager.SOURCE_DRIBBBLE_FOLLOWING, ctx)
                }
            }
            RC_AUTH_DRIBBBLE_USER_LIKES -> {
                if (resultCode == RESULT_OK) {
                    filtersAdapter.enableFilterByKey(
                            SourceManager.SOURCE_DRIBBBLE_USER_LIKES, ctx)
                }
            }
            RC_AUTH_DRIBBBLE_USER_SHOTS -> {
                if (resultCode == Activity.RESULT_OK) {
                    filtersAdapter.enableFilterByKey(
                            SourceManager.SOURCE_DRIBBBLE_USER_SHOTS, this)
                }
            }
            else -> {
            }
        }
    }

    private fun showFab() = with(fab) {
        alpha = 0f
        scaleX = 0f
        scaleY = 0f
        translationY
        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(300L)
                .setInterpolator(AnimationUtils.loadInterpolator(ctx,
                        android.R.interpolator.linear_out_slow_in))
                .start()
    }

    /**
     * Highlight the new item by:
     *      1. opening the drawer
     *      2. scrolling it into view
     *      3. flashing it's background
     *      4. closing the drawer
     */
    private fun highlightNewSources(vararg sources: Source?) {
        val closeDrawerRunnable = { drawer.closeDrawer(GravityCompat.END) }

        drawer.setDrawerListener(object : DrawerLayout.SimpleDrawerListener() {

            val filtersTouch = { v: View, event: MotionEvent ->
                drawer.removeCallbacks(closeDrawerRunnable)
            }

            override fun onDrawerOpened(drawerView: View?) {
                // scroll to the new item(s) and highlight them
                val filterPositions = sources.filterNotNull()
                        .map { filtersAdapter.getFilterPosition(it) }
                val scrollTo = filterPositions.max()!!
                filters.smoothScrollToPosition(scrollTo)
                filterPositions.map {
                    filters.findViewHolderForAdapterPosition(it) as? FilterAdapter.FilterViewHolder
                }.forEach {
                    // this is failing for the first saved search, then working for subsequent calls
                    // TODO work out why!
                    it?.highlightFilter()
                }
                filters.setOnTouchListener(filtersTouch)
            }

            override fun onDrawerClosed(drawerView: View?) {
                // reset
                filters.setOnTouchListener(null)
            }

            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_DRAGGING) {
                    drawer.removeCallbacks(closeDrawerRunnable)
                }
            }
        })
        drawer.openDrawer(GravityCompat.END)
        drawer.postDelayed(closeDrawerRunnable, 2000)
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    private fun checkConnectivity() = with(connectivityManager) {
        val connected = activeNetworkInfo?.isConnected ?: false
        if (!connected) {
            loading.visibility = View.GONE
            with(stub_no_connection.inflate() as ImageView) {
                val avd = getDrawable(R.drawable.avd_no_connection) as AnimatedVectorDrawable
                setImageDrawable(avd)
                avd.start()
            }
        }
    }

    private fun getAuthSourceRequestCode(filter: Source) = when (filter.key) {
        SourceManager.SOURCE_DRIBBBLE_FOLLOWING -> RC_AUTH_DRIBBBLE_FOLLOWING
        SourceManager.SOURCE_DRIBBBLE_USER_LIKES -> RC_AUTH_DRIBBBLE_USER_LIKES
        SourceManager.SOURCE_DRIBBBLE_USER_SHOTS -> RC_AUTH_DRIBBBLE_USER_SHOTS
        else -> throw InvalidParameterException()
    }
}