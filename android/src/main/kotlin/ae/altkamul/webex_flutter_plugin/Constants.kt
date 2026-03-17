package ae.altkamul.webex_flutter_plugin

class Constants {
    object Intent {
        const val OUTGOING_CALL_CALLER_ID = "OUTGOING_CALL_CALLER_ID"
        const val JWTToken = "JWT_Token"
        const val CALL_ID = "callid"
    }

    object Callbacks {
        const val RE_LOGIN_REQUIRED = "RE_LOGIN_REQUIRED"
    }

    object Result {
        const val STATUS_CALL_ENDED = "call_ended"
        const val STATUS_AUTH_FAILED = "auth_failed"
        const val STATUS_CALL_FAILED = "call_failed"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_ERROR = "error"
    }
}
