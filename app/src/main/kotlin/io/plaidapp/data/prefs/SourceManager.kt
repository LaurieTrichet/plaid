package io.plaidapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import io.plaidapp.R
import io.plaidapp.data.Source
import io.plaidapp.util.edit

object SourceManager {

    val SOURCE_DESIGNER_NEWS_POPULAR = "SOURCE_DESIGNER_NEWS_POPULAR"
    val SOURCE_DESIGNER_NEWS_RECENT = "SOURCE_DESIGNER_NEWS_RECENT"
    val SOURCE_DRIBBBLE_POPULAR = "SOURCE_DRIBBBLE_POPULAR"
    val SOURCE_DRIBBBLE_FOLLOWING = "SOURCE_DRIBBBLE_FOLLOWING"
    val SOURCE_DRIBBBLE_USER_LIKES = "SOURCE_DRIBBBLE_USER_LIKES"
    val SOURCE_DRIBBBLE_USER_SHOTS = "SOURCE_DRIBBBLE_USER_SHOTS"
    val SOURCE_DRIBBBLE_RECENT = "SOURCE_DRIBBBLE_RECENT"
    val SOURCE_DRIBBBLE_DEBUTS = "SOURCE_DRIBBBLE_DEBUTS"
    val SOURCE_DRIBBBLE_ANIMATED = "SOURCE_DRIBBBLE_ANIMATED"
    val SOURCE_PRODUCT_HUNT = "SOURCE_PRODUCT_HUNT"
    private val SOURCES_PREF = "SOURCES_PREF"
    private val KEY_SOURCES = "KEY_SOURCES"

    fun getSources(context: Context): kotlin.List<io.plaidapp.data.Source> {
        val prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE)
        val sourceKeys = prefs.getStringSet(KEY_SOURCES, null)
        return sourceKeys?.let { getExistingSources(context, it, prefs) }
                ?: createDefaultSources(context, prefs)
    }

    private fun createDefaultSources(context: Context, prefs: SharedPreferences): List<Source> {
        setupDefaultSources(context, prefs)
        return defaultSources(context)
    }

    private fun getExistingSources(context: Context, sourceKeys: Set<String>, prefs: SharedPreferences): List<Source> {
        val res = sourceKeys.toList().map {
            when {
                it.startsWith(Source.DribbbleSearchSource.DRIBBBLE_QUERY_PREFIX) -> {
                    Source.DribbbleSearchSource(
                            it.replace(Source.DribbbleSearchSource.DRIBBBLE_QUERY_PREFIX, ""),
                            prefs.getBoolean(it, false))
                }
                it.startsWith(Source.DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX) -> {
                    Source.DesignerNewsSearchSource(
                            it.replace(Source.DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX, ""),
                            prefs.getBoolean(it, false))
                }
                else -> getSource(context, it, prefs.getBoolean(it, false))
            }
        }

        return res.filterNotNull().sortedBy { it.sortOrder }
    }

    fun addSource(toAdd: Source, context: Context) {
        val prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE)
        val sourceKeys = prefs.getStringSet(KEY_SOURCES, null)
        prefs.edit {
            putStringSet(KEY_SOURCES, sourceKeys + toAdd.key)
            putBoolean(toAdd.key, toAdd.active)
        }
    }

    fun updateSource(source: Source, context: Context) {
        context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE).edit {
            putBoolean(source.key, source.active)
        }
    }

    fun removeSource(source: Source, context: Context) {
        val prefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE)
        val sourceKeys = prefs.getStringSet(KEY_SOURCES, null)
        prefs.edit {
            putStringSet(KEY_SOURCES, sourceKeys - source.key)
            remove(source.key)
        }
    }

    private fun setupDefaultSources(context: Context, prefs: SharedPreferences) {
        prefs.edit(false) {
            val keys = defaultSources(context).map {
                putBoolean(it.key, it.active)
                it.key
            }
            putStringSet(KEY_SOURCES, keys.toSet())
        }
    }

    private fun getSource(context: Context, key: String, active: Boolean): Source? {
        val source = defaultSources(context).firstOrNull { it.key == key }
        source?.active = active
        return source
    }

    private fun defaultSources(context: Context) = listOf(
            Source.DesignerNewsSource(SOURCE_DESIGNER_NEWS_POPULAR, 100,
                    context.getString(R.string.source_designer_news_popular), true),
            Source.DesignerNewsSource(SOURCE_DESIGNER_NEWS_RECENT, 101,
                    context.getString(R.string.source_designer_news_recent), false),
            // 200 sort order range left for DN searches
            Source.DribbbleSource(SOURCE_DRIBBBLE_POPULAR, 300,
                    context.getString(R.string.source_dribbble_popular), true),
            Source.DribbbleSource(SOURCE_DRIBBBLE_FOLLOWING, 301,
                    context.getString(R.string.source_dribbble_following), false),
            Source.DribbbleSource(SOURCE_DRIBBBLE_USER_SHOTS, 302,
                    context.getString(R.string.source_dribbble_user_shots), false),
            Source.DribbbleSource(SOURCE_DRIBBBLE_USER_LIKES, 303,
                    context.getString(R.string.source_dribbble_user_likes), false),
            Source.DribbbleSource(SOURCE_DRIBBBLE_RECENT, 304,
                    context.getString(R.string.source_dribbble_recent), false),
            Source.DribbbleSource(SOURCE_DRIBBBLE_DEBUTS, 305,
                    context.getString(R.string.source_dribbble_debuts), false),
            Source.DribbbleSource(SOURCE_DRIBBBLE_ANIMATED, 306,
                    context.getString(R.string.source_dribbble_animated), false),
            Source.DribbbleSearchSource(
                    context.getString(R.string.source_dribbble_search_material_design), true),
            // 400 sort order range left for dribbble searches
            Source(SOURCE_PRODUCT_HUNT, 500, context.getString(R.string.source_product_hunt),
                    R.drawable.ic_product_hunt, false)
    )
}