package ae.altkamul.webex_flutter_plugin.auth

import ae.altkamul.webex_flutter_plugin.Constants
import ae.altkamul.webex_flutter_plugin.R
import ae.altkamul.webex_flutter_plugin.WebexViewModel
import ae.altkamul.webex_flutter_plugin.calling.CallActivity
import ae.altkamul.webex_flutter_plugin.databinding.ActivityLoginWithTokenBinding
import ae.altkamul.webex_flutter_plugin.webexModule
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.ciscowebex.androidsdk.utils.AppConfiguration
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.context.loadKoinModules

class JWTLoginActivity : AppCompatActivity() {

    lateinit var binding: ActivityLoginWithTokenBinding

    private val loginViewModel: LoginViewModel by viewModel()
    private val webexViewModel: WebexViewModel by viewModel()

    companion object {
        private var modulesLoaded = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sentToken = intent.getStringExtra(Constants.Intent.JWTToken) ?: ""

        loadModulesIfNeeded()

        DataBindingUtil.setContentView<ActivityLoginWithTokenBinding>(
            this, R.layout.activity_login_with_token
        ).also { binding = it }.apply {

            // Show loading state immediately
            progressLayout.visibility = View.VISIBLE
            loginFailedTextView.visibility = View.GONE
            goBackButton.visibility = View.GONE

            loginViewModel.isAuthorized.observe(
                this@JWTLoginActivity,
                Observer { isAuthorized ->
                    progressLayout.visibility = View.GONE
                    isAuthorized?.let {
                        if (it) {
                            onLoggedIn()
                        } else {
                            onLoginFailed(getString(R.string.jwt_login_failed))
                        }
                    }
                })

            loginViewModel.isAuthorizedCached.observe(
                this@JWTLoginActivity,
                Observer { isAuthorizedCached ->
                    isAuthorizedCached?.let {
                        if (it) {
                            onLoggedIn()
                        } else {
                            // Not cached, do fresh login
                            loginViewModel.loginWithJWT(sentToken)
                        }
                    }
                })

            loginViewModel.errorData.observe(
                this@JWTLoginActivity,
                Observer { errorMessage ->
                    progressLayout.visibility = View.GONE
                    onLoginFailed(errorMessage ?: getString(R.string.jwt_login_failed))
                })

            goBackButton.setOnClickListener {
                finishWithResult(Constants.Result.STATUS_AUTH_FAILED, getString(R.string.jwt_login_failed))
            }

            loginViewModel.initialize()
        }
    }

    private fun loadModulesIfNeeded() {
        if (!modulesLoaded) {
            try {
                loadKoinModules(listOf(webexModule, loginModule, JWTWebexModule))
                modulesLoaded = true
            } catch (e: Exception) {
                // Modules already loaded
            }
        }
        AppConfiguration.setContext(applicationContext)
        webexViewModel.enableBackgroundConnection(webexViewModel.enableBgConnectiontoggle)
    }

    override fun onBackPressed() {
        finishWithResult(Constants.Result.STATUS_CANCELLED, "User cancelled")
        super.onBackPressed()
    }

    private fun onLoggedIn() {
        val callerId = intent.getStringExtra(Constants.Intent.OUTGOING_CALL_CALLER_ID) ?: "-"
        val callIntent = CallActivity.getOutgoingIntent(this, callerId)
        startActivityForResult(callIntent, CallActivity.REQUEST_CODE_CALL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CallActivity.REQUEST_CODE_CALL) {
            // Forward the result from CallActivity back to Flutter
            val status = data?.getStringExtra("status") ?: Constants.Result.STATUS_CALL_ENDED
            val message = data?.getStringExtra("message") ?: ""
            finishWithResult(status, message)
        }
    }

    private fun onLoginFailed(failureMessage: String) {
        binding.progressLayout.visibility = View.GONE
        binding.loginFailedTextView.visibility = View.VISIBLE
        binding.loginFailedTextView.text = failureMessage
        binding.goBackButton.visibility = View.VISIBLE
    }

    private fun finishWithResult(status: String, message: String) {
        val resultIntent = Intent().apply {
            putExtra("status", status)
            putExtra("message", message)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
