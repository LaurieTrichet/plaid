package io.plaidapp.data

import android.support.annotation.DrawableRes
import io.plaidapp.R
import java.util.*

open class Source(
        val key: String,
        val sortOrder: Int,
        val name: String,
        @DrawableRes val iconRes: Int,
        var active: Boolean) {


    open fun isSwipeDismissable() = false

    open class DribbbleSource(key: String, sortOrder: Int, name: String, active: Boolean) :
            Source(key, sortOrder, name, R.drawable.ic_dribbble, active)

    class DribbbleSearchSource(val query: String, active: Boolean) :
            DribbbleSource(DribbbleSearchSource.DRIBBBLE_QUERY_PREFIX + query,
                    DribbbleSearchSource.SEARCH_SORT_ORDER, "“$query”", active) {

        companion object {
            val DRIBBBLE_QUERY_PREFIX = "DRIBBBLE_QUERY_"
            val SEARCH_SORT_ORDER = 400
        }

        override fun isSwipeDismissable() = true
    }

    open class DesignerNewsSource(key: String, sortOrder: Int, name: String, active: Boolean) :
            Source(key, sortOrder, name, R.drawable.ic_designer_news, active)

    class DesignerNewsSearchSource(val query: String, active: Boolean) :
            DribbbleSource(DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX + query,
                    DesignerNewsSearchSource.SEARCH_SORT_ORDER, "“$query”", active) {

        companion object {
            val DESIGNER_NEWS_QUERY_PREFIX = "DESIGNER_NEWS_QUERY_"
            val SEARCH_SORT_ORDER = 200
        }

        override fun isSwipeDismissable() = true
    }

    class SourceComparator : Comparator<Source> {
        override fun compare(lhs: Source, rhs: Source) = lhs.sortOrder - rhs.sortOrder
    }
}