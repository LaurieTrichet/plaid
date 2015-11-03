package io.plaidapp.util

import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView

fun GridLayoutManager.setSpanSizeLookup(code: (Int) -> Int){
    val lookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = code(position)
    }
    spanSizeLookup = lookup
}

fun RecyclerView.addOnScrollListener(code: (RecyclerView, Int, Int) -> Unit) {
    val listener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = code(recyclerView, dx, dy)
    }
    addOnScrollListener(listener)
}