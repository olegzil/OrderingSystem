package css.com.cloudkitchens.utilities

import android.util.Log
import css.com.cloudkitchens.BuildConfig

const val TAG="Smithsonian"
class ImmutableList<T>(private val inner: List<T>) : List<T> by inner

fun <T> List<T>.toImmutableList(): List<T> {
    return if (this is ImmutableList<T>) {
        this
    } else {
        ImmutableList(this)
    }
}

fun printOrCrash(msg: String, e: Throwable? = null) {
    e?.run {
        when (BuildConfig.BUILD_TYPE) {
            "debug" -> throw(this)
            "release" -> printLog(msg)
            else -> {
                printLog("unrecognizable ${BuildConfig.BUILD_TYPE}")
            }
        }
    } ?: run {
        printLog(msg)
    }
}

fun printLog(msg: String) {
    val decorator = "=-=-=-=-=-=-="
    if (BuildConfig.DEBUG)
        Log.d(TAG, "$decorator $msg $decorator")
}

fun printLogWithStack(msg: String) {
    val decorator = "=-=-=-=-=-=-="
//    if (Log.isLoggable(TAG, Log.DEBUG))
    Log.d(TAG, "$decorator begin stack trace $decorator")
    Log.d(TAG, "$decorator $msg $decorator")
    Log.d(TAG, " ${buildStackTraceString(Thread.currentThread().stackTrace)}")
    Log.d(TAG, "$decorator end stack trace $decorator")
}

fun buildStackTraceString(elements: Array<StackTraceElement>?): String {
    val sb = StringBuilder()
    if (elements != null && elements.isNotEmpty()) {
        for (element in elements) {
            sb.append(element.toString())
            sb.append("\n")
        }
    }
    return sb.toString()
}

