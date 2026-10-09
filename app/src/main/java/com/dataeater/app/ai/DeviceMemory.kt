package com.dataeater.app.ai

import android.app.ActivityManager
import android.content.Context

/**
 * Works out whether a given model can safely be loaded on this phone.
 *
 * DataEater does not hardcode any model name. Any `.litertlm` file dropped
 * into the models folder can be loaded. That freedom needs a safety net,
 * because choosing a model that is far too large for the phone does not
 * produce a small error message - it produces a crash, or the whole system
 * killing the app to free memory.
 *
 * So before loading anything we compare the model size with the memory the
 * phone actually has free, and tell the user plainly.
 */
object DeviceMemory {

    /** What the phone can tell us about its memory right now. */
    data class Info(
        val totalMegabytes: Int,
        val availableMegabytes: Int,
        val isLowMemory: Boolean,
    )

    /** The three answers a model size can get. */
    enum class Verdict {
        /** Plenty of room. Load it. */
        COMFORTABLE,

        /** It should work, but the phone will be busy. Other apps may slow down. */
        TIGHT,

        /** Not enough memory. Loading this would very likely fail. */
        TOO_LARGE,
    }

    /**
     * Memory a model needs *on top of* its own file size, for the runtime,
     * the working buffers and the cache.
     */
    const val OVERHEAD_MEGABYTES = 700

    /**
     * The largest share of ALL a phone's memory that one model may claim.
     * Android itself needs a large part of it, and so does the user.
     */
    const val MAX_SHARE_OF_TOTAL_PERCENT = 75

    /** Asks Android what the memory situation is. */
    fun read(context: Context): Info {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)

        val megabyte = 1024L * 1024L
        return Info(
            totalMegabytes = (info.totalMem / megabyte).toInt(),
            availableMegabytes = (info.availMem / megabyte).toInt(),
            isLowMemory = info.lowMemory,
        )
    }

    /**
     * Decides whether a model of [fileSizeMegabytes] can be loaded.
     *
     * This is a pure calculation, so it can be tested on the computer
     * without a phone. That matters: getting it wrong means a crash.
     */
    fun verdict(fileSizeMegabytes: Int, device: Info): Verdict {
        val needed = fileSizeMegabytes + OVERHEAD_MEGABYTES

        // A phone that is already low on memory cannot take a large model,
        // however much memory it has in total.
        if (device.isLowMemory && fileSizeMegabytes > 300) return Verdict.TOO_LARGE

        // Total memory is shared with Android itself and with every other app.
        // A model that needs more than three quarters of ALL the RAM would
        // never fit, even on a phone with nothing else running.
        if (needed > device.totalMegabytes * MAX_SHARE_OF_TOTAL_PERCENT / 100) {
            return Verdict.TOO_LARGE
        }

        return when {
            device.availableMegabytes >= needed -> Verdict.COMFORTABLE
            device.availableMegabytes * 10 >= needed * 7 -> Verdict.TIGHT
            else -> Verdict.TOO_LARGE
        }
    }

    /** A short explanation for the user, in their language. */
    fun explain(verdict: Verdict, fileSizeMegabytes: Int, device: Info): String = when (verdict) {
        Verdict.COMFORTABLE -> "needs about ${fileSizeMegabytes + OVERHEAD_MEGABYTES} MB"

        Verdict.TIGHT ->
            "tight: needs about ${fileSizeMegabytes + OVERHEAD_MEGABYTES} MB, " +
                "only ${device.availableMegabytes} MB free. Close other apps first."

        Verdict.TOO_LARGE ->
            "too large: needs about ${fileSizeMegabytes + OVERHEAD_MEGABYTES} MB but " +
                "this phone has ${device.totalMegabytes} MB in total. " +
                "Choose a smaller .litertlm file."
    }
}