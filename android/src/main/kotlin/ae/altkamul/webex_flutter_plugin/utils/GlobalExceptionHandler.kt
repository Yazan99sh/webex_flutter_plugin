package ae.altkamul.webex_flutter_plugin.utils

import android.util.Log
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.CallObserver

internal class GlobalExceptionHandler : Thread.UncaughtExceptionHandler {
    private val tag = "GlobalExceptionHandler"
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(tag, "Uncaught exception during call", throwable)

        // Try to hang up any active call gracefully
        try {
            for (i in 0 until CallObjectStorage.size()) {
                val callObj = CallObjectStorage.getCallObjectFromIndex(i)
                if (callObj?.getStatus() == Call.CallStatus.CONNECTED) {
                    Log.d(tag, "Hanging up active call: ${callObj.getCallId()}")
                    callObj.hangup { result ->
                        Log.d(tag, "Emergency hangup result: ${result.isSuccessful}")
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during emergency hangup", e)
        }

        CallObjectStorage.clearStorage()

        // Delegate to default handler which will restart the app
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
