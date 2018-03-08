package com.simplemobiletools.clock.fragments

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.clock.R
import com.simplemobiletools.clock.activities.SimpleActivity
import com.simplemobiletools.clock.adapters.StopwatchAdapter
import com.simplemobiletools.clock.extensions.config
import com.simplemobiletools.clock.extensions.formatStopwatchTime
import com.simplemobiletools.clock.helpers.SORT_BY_LAP
import com.simplemobiletools.clock.helpers.SORT_BY_LAP_TIME
import com.simplemobiletools.clock.helpers.SORT_BY_TOTAL_TIME
import com.simplemobiletools.clock.models.Lap
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import kotlinx.android.synthetic.main.fragment_stopwatch.view.*

class StopwatchFragment : Fragment() {
    private val UPDATE_INTERVAL = 10L

    private val updateHandler = Handler()
    private val mainLooper = Looper.getMainLooper()
    private var uptimeAtStart = 0L
    private var totalTicks = 0
    private var currentTicks = 0    // ticks that reset at pause
    private var lapTicks = 0
    private var currentLap = 1
    private var isRunning = false
    private var sorting = SORT_BY_LAP or SORT_DESCENDING
    private var laps = ArrayList<Lap>()

    lateinit var stopwatchAdapter: StopwatchAdapter
    lateinit var view: ViewGroup

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        view = (inflater.inflate(R.layout.fragment_stopwatch, container, false) as ViewGroup).apply {
            stopwatch_time.setOnClickListener {
                togglePlayPause()
                checkHaptic(this)
            }

            stopwatch_play_pause.setOnClickListener {
                togglePlayPause()
                checkHaptic(this)
            }

            stopwatch_reset.setOnClickListener {
                resetStopwatch()
                checkHaptic(this)
            }

            stopwatch_sorting_indicator_1.setOnClickListener {
                changeSorting(SORT_BY_LAP)
                checkHaptic(this)
            }

            stopwatch_sorting_indicator_2.setOnClickListener {
                changeSorting(SORT_BY_LAP_TIME)
                checkHaptic(this)
            }

            stopwatch_sorting_indicator_3.setOnClickListener {
                changeSorting(SORT_BY_TOTAL_TIME)
                checkHaptic(this)
            }

            stopwatch_lap.setOnClickListener {
                stopwatch_sorting_indicators_holder.beVisible()
                val lap = Lap(currentLap++, lapTicks * UPDATE_INTERVAL, totalTicks * UPDATE_INTERVAL)
                laps.add(0, lap)
                lapTicks = 0
                updateLaps()
                checkHaptic(this)
            }

            stopwatchAdapter = StopwatchAdapter(activity as SimpleActivity, ArrayList(), stopwatch_list) {
                if (it is Int) {
                    changeSorting(it)
                }
            }
            Lap.sorting = sorting
            stopwatch_list.adapter = stopwatchAdapter
        }

        updateSortingIndicators()
        return view
    }

    override fun onResume() {
        super.onResume()
        setupStopwatch()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun setupStopwatch() {
        setupViews()
    }

    private fun setupViews() {
        val adjustedPrimaryColor = context!!.getAdjustedPrimaryColor()
        view.apply {
            context!!.updateTextColors(stopwatch_fragment)
            stopwatch_play_pause.background = resources.getColoredDrawableWithColor(R.drawable.circle_background_filled, adjustedPrimaryColor)
            stopwatch_reset.applyColorFilter(context!!.config.textColor)
        }

        updateIcons()
        updateDisplayedText()
    }

    private fun updateIcons() {
        val drawableId = if (isRunning) R.drawable.ic_pause else R.drawable.ic_play
        val iconColor = if (context!!.getAdjustedPrimaryColor() == Color.WHITE) Color.BLACK else context!!.config.textColor
        view.stopwatch_play_pause.setImageDrawable(resources.getColoredDrawableWithColor(drawableId, iconColor))
    }

    private fun togglePlayPause() {
        isRunning = !isRunning
        updateIcons()
        view.stopwatch_lap.beVisibleIf(isRunning)

        if (isRunning) {
            updateHandler.post(updateRunnable)
            uptimeAtStart = SystemClock.uptimeMillis()
            view.stopwatch_reset.beVisible()
        } else {
            val prevSessionsMS = (totalTicks - currentTicks) * UPDATE_INTERVAL
            val totalDuration = SystemClock.uptimeMillis() - uptimeAtStart + prevSessionsMS
            updateHandler.removeCallbacksAndMessages(null)
            view.stopwatch_time.text = totalDuration.formatStopwatchTime(true)
            currentTicks = 0
            totalTicks--
        }
    }

    private fun updateDisplayedText() {
        view.stopwatch_time.text = (totalTicks * UPDATE_INTERVAL).formatStopwatchTime(false)
        if (currentLap > 1) {
            stopwatchAdapter.updateLastField(lapTicks * UPDATE_INTERVAL, totalTicks * UPDATE_INTERVAL)
        }
    }

    private fun resetStopwatch() {
        updateHandler.removeCallbacksAndMessages(null)
        isRunning = false
        currentTicks = 0
        totalTicks = 0
        currentLap = 1
        lapTicks = 0
        laps.clear()
        updateIcons()

        view.apply {
            stopwatch_reset.beGone()
            stopwatch_lap.beGone()
            stopwatch_time.text = 0L.formatStopwatchTime(false)
            stopwatch_sorting_indicators_holder.beInvisible()
        }
    }

    private fun changeSorting(clickedValue: Int) {
        sorting = if (sorting and clickedValue != 0) {
            sorting.flipBit(SORT_DESCENDING)
        } else {
            clickedValue or SORT_DESCENDING
        }

        updateSortingIndicators()
        Lap.sorting = sorting
        updateLaps()
    }

    private fun updateSortingIndicators() {
        var bitmap = context!!.resources.getColoredBitmap(R.drawable.ic_sorting_triangle, context!!.getAdjustedPrimaryColor())
        view.apply {
            stopwatch_sorting_indicator_1.beInvisibleIf(sorting and SORT_BY_LAP == 0)
            stopwatch_sorting_indicator_2.beInvisibleIf(sorting and SORT_BY_LAP_TIME == 0)
            stopwatch_sorting_indicator_3.beInvisibleIf(sorting and SORT_BY_TOTAL_TIME == 0)

            val activeIndicator = when {
                sorting and SORT_BY_LAP != 0 -> stopwatch_sorting_indicator_1
                sorting and SORT_BY_LAP_TIME != 0 -> stopwatch_sorting_indicator_2
                else -> stopwatch_sorting_indicator_3
            }

            if (sorting and SORT_DESCENDING == 0) {
                val matrix = Matrix()
                matrix.postScale(1f, -1f)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            activeIndicator.setImageBitmap(bitmap)
        }
    }

    private fun updateLaps() {
        laps.sort()
        stopwatchAdapter.updateItems(laps)
    }

    private fun checkHaptic(view: View) {
        if (context!!.config.vibrateOnButtonPress) {
            view.performHapticFeedback()
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                if (totalTicks % 10 == 0) {
                    mainLooper.run {
                        updateDisplayedText()
                    }
                }
                totalTicks++
                currentTicks++
                lapTicks++
                updateHandler.postAtTime(this, uptimeAtStart + currentTicks * UPDATE_INTERVAL)
            }
        }
    }
}
