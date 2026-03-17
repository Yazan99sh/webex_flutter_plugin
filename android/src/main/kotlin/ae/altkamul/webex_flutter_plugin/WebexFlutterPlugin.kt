package ae.altkamul.webex_flutter_plugin

import ae.altkamul.webex_flutter_plugin.auth.JWTLoginActivity
import android.app.Activity
import android.content.Intent
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class WebexFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var pendingResult: Result? = null

    companion object {
        const val REQUEST_CODE_WEBEX_CALL = 9001
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(
            flutterPluginBinding.binaryMessenger,
            "webex_flutter_plugin"
        )
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "startWebexCalling") {
            val currentActivity = activity
            if (currentActivity == null) {
                result.error("NO_ACTIVITY", "No activity available", null)
                return
            }

            // Store pending result to resolve when call activity finishes
            pendingResult = result

            val intent = Intent(currentActivity, JWTLoginActivity::class.java).apply {
                putExtra(Constants.Intent.OUTGOING_CALL_CALLER_ID, call.argument<String>("caller_id"))
                putExtra(Constants.Intent.JWTToken, call.argument<String>("jwt_token"))
            }
            currentActivity.startActivityForResult(intent, REQUEST_CODE_WEBEX_CALL)
        } else {
            result.notImplemented()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_WEBEX_CALL) {
            val status = data?.getStringExtra("status") ?: "unknown"
            val message = data?.getStringExtra("message") ?: ""

            pendingResult?.success(mapOf("status" to status, "message" to message))
            pendingResult = null
            return true
        }
        return false
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // ActivityAware
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
