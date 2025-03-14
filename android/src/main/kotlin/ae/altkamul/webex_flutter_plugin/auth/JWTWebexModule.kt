package ae.altkamul.webex_flutter_plugin.auth

import com.ciscowebex.androidsdk.Webex
import com.ciscowebex.androidsdk.auth.JWTAuthenticator
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

val JWTWebexModule = module {


    factory {
        Webex(androidApplication(), JWTAuthenticator())
    }
}