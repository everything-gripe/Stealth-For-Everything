/*
 * Copyright 2018 Google LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership. The ASF licenses this
 * file to you under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.cosmos.unreddit.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.cosmos.unreddit.R
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

/**
 * A [FrameLayout] which responds to nested scrolls to create drag-dismissable layouts.
 * Applies an elasticity factor to reduce movement as you approach the given dismiss distance.
 * Optionally also scales down content during drag.
 */
class ElasticDragDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    // configurable attribs
    private var dragDismissDistance = Float.MAX_VALUE
    private var dragDismissFraction = -1f
    private var dragDismissScale = 1f
    private var shouldScale = false
    private var dragElacticity = 0.8f

    // state
    private var totalDrag = 0f
    private var draggingDown = false
    private var draggingUp = false
    private var mLastActionEvent = 0
    private val callbacks: MutableList<ElasticDragDismissCallback> = mutableListOf()
    private val fastOutSlowIn = AnimationUtils.loadInterpolator(
        context, android.R.interpolator.fast_out_slow_in
    )

    interface ElasticDragDismissCallback {
        /**
         * Called for each drag event.
         *
         * @param elasticOffset       Indicating the drag offset with elasticity applied i.e. may
         * exceed 1.
         * @param elasticOffsetPixels The elastically scaled drag distance in pixels.
         * @param rawOffset           Value from [0, 1] indicating the raw drag offset i.e.
         * without elasticity applied. A value of 1 indicates that the
         * dismiss distance has been reached.
         * @param rawOffsetPixels     The raw distance the user has dragged
         */
        fun onDrag(
            elasticOffset: Float,
            elasticOffsetPixels: Float,
            rawOffset: Float,
            rawOffsetPixels: Float
        )

        /**
         * Called when dragging is released and has exceeded the threshold dismiss distance.
         */
        fun onDragDismissed()
    }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ElasticDragDismissFrameLayout,
            0, 0
        ).apply {
            try {
                if (hasValue(R.styleable.ElasticDragDismissFrameLayout_dragDismissDistance)) {
                    dragDismissDistance = getDimensionPixelSize(
                        R.styleable.ElasticDragDismissFrameLayout_dragDismissDistance,
                        0
                    ).toFloat()
                } else if (hasValue(R.styleable.ElasticDragDismissFrameLayout_dragDismissFraction)) {
                    dragDismissFraction = getFloat(
                        R.styleable.ElasticDragDismissFrameLayout_dragDismissFraction,
                        dragDismissFraction
                    )
                }
                if (hasValue(R.styleable.ElasticDragDismissFrameLayout_dragDismissScale)) {
                    dragDismissScale = getFloat(
                        R.styleable.ElasticDragDismissFrameLayout_dragDismissScale,
                        dragDismissScale
                    )
                    shouldScale = dragDismissScale != 1f
                }
                if (hasValue(R.styleable.ElasticDragDismissFrameLayout_dragElasticity)) {
                    dragElacticity = getFloat(
                        R.styleable.ElasticDragDismissFrameLayout_dragElasticity,
                        dragElacticity
                    )
                }
            } finally {
                recycle()
            }
        }
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return nestedScrollAxes and SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // if we're in a drag gesture and the user reverses up the we should take those events
        if (draggingDown && dy > 0 || draggingUp && dy < 0) {
            dragScale(dy)
            consumed[1] = dy
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        dragScale(dyUnconsumed)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        mLastActionEvent = ev.action
        return super.onInterceptTouchEvent(ev)
    }

    override fun onStopNestedScroll(child: View) {
        if (totalDrag.times(-1) >= dragDismissDistance) {
            dispatchDismissCallback()
        } else { // settle back to natural position
            if (mLastActionEvent == MotionEvent.ACTION_DOWN) {
                // this is a 'defensive cleanup for new gestures',
                // don't animate here
                // see also https://github.com/nickbutcher/plaid/issues/185
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
            } else {
                animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200L)
                    .setInterpolator(fastOutSlowIn)
                    .setListener(null)
                    .start()
            }
            totalDrag = 0f
            draggingUp = false
            draggingDown = draggingUp
            dispatchDragCallback(0f, 0f, 0f, 0f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (dragDismissFraction > 0f) {
            dragDismissDistance = h * dragDismissFraction
        }
    }

    fun addListener(listener: ElasticDragDismissCallback) {
        callbacks.add(listener)
    }

    fun removeListener(listener: ElasticDragDismissCallback) {
        if (callbacks.size > 0) {
            callbacks.remove(listener)
        }
    }

    private fun dragScale(scroll: Int) {
        if (scroll == 0) return
        totalDrag += scroll.toFloat()

        // track the direction & set the pivot point for scaling
        // don't double track i.e. if start dragging down and then reverse, keep tracking as
        // dragging down until they reach the 'natural' position
        if (scroll < 0 && !draggingUp && !draggingDown) {
            draggingDown = true
            if (shouldScale) pivotY = height.toFloat()
        } else if (scroll > 0 && !draggingDown && !draggingUp) {
            draggingUp = true
            if (shouldScale) pivotY = 0f
        }
        // how far have we dragged relative to the distance to perform a dismiss
        // (0–1 where 1 = dismiss distance). Decreasing logarithmically as we approach the limit
        var dragFraction = log10((1 + abs(totalDrag) / dragDismissDistance).toDouble())
            .toFloat()

        // calculate the desired translation given the drag fraction
        var dragTo = dragFraction * dragDismissDistance * dragElacticity
        if (draggingUp) {
            // as we use the absolute magnitude when calculating the drag fraction, need to
            // re-apply the drag direction
            dragTo *= -1f
        }
        translationY = dragTo
        if (shouldScale) {
            val scale = 1 - (1 - dragDismissScale) * dragFraction
            scaleX = scale
            scaleY = scale
        }

        // if we've reversed direction and gone past the settle point then clear the flags to
        // allow the list to get the scroll events & reset any transforms
        if (draggingDown && totalDrag >= 0 ||
            draggingUp && totalDrag <= 0
        ) {
            dragFraction = 0f
            dragTo = dragFraction
            totalDrag = dragTo
            draggingUp = false
            draggingDown = draggingUp
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
        dispatchDragCallback(
            dragFraction, dragTo,
            min(1f, abs(totalDrag) / dragDismissDistance), totalDrag
        )
    }

    private fun dispatchDragCallback(
        elasticOffset: Float,
        elasticOffsetPixels: Float,
        rawOffset: Float,
        rawOffsetPixels: Float
    ) {
        if (callbacks.isNotEmpty()) {
            for (callback in callbacks) {
                callback.onDrag(
                    elasticOffset, elasticOffsetPixels,
                    rawOffset, rawOffsetPixels
                )
            }
        }
    }

    private fun dispatchDismissCallback() {
        if (callbacks.isNotEmpty()) {
            for (callback in callbacks) {
                callback.onDragDismissed()
            }
        }
    }
}
