package ae.altkamul.webex_flutter_plugin

import ae.altkamul.webex_flutter_plugin.utils.CallObjectStorage
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.ciscowebex.androidsdk.Webex
import com.ciscowebex.androidsdk.WebexUCLoginDelegate
import com.ciscowebex.androidsdk.WebexAuthDelegate
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.CallObserver
import com.ciscowebex.androidsdk.phone.MediaOption
import com.ciscowebex.androidsdk.phone.Phone

class WebexRepository(val webex: Webex) : WebexUCLoginDelegate, WebexAuthDelegate {
    private val tag = "WebexRepository"

    enum class CallCap {
        Audio_Only,
        Audio_Video
    }

    enum class CallEvent {
        DialCompleted,
        DialFailed,
        WrongApiCalled,
        CannotStartInstantMeeting
    }

    data class CallLiveData(
        val event: CallEvent,
        val call: Call? = null,
        val sharingLabel: String? = null,
        val errorMessage: String? = null
    )

    var isAddedCall = false
    var currentCallId: String? = null
    var oldCallId: String? = null
    var isSendingAudio = true
    var isLocalVideoMuted = true
    var isRemoteVideoMuted = true
    var isRemoteScreenShareON = false
    var enableBgStreamtoggle = true
    var enableBgConnectiontoggle = true

    var callCapability: CallCap = CallCap.Audio_Video
    var compositedVideoLayout: MediaOption.CompositedVideoLayout = MediaOption.CompositedVideoLayout.FILMSTRIP
    var streamMode: Phone.VideoStreamMode = Phone.VideoStreamMode.AUXILIARY

    var _authLiveDataList: MutableList<MutableLiveData<String>?> = mutableListOf()
    var _callObservers: HashMap<String, MutableList<CallObserver>> = HashMap()

    init {
        webex.delegate = this
        webex.authDelegate = this
    }

    fun clearCallData() {
        isAddedCall = false
        currentCallId = null
        oldCallId = null
        isSendingAudio = true
        isLocalVideoMuted = true
        isRemoteScreenShareON = false
        isRemoteVideoMuted = true
    }

    fun getCall(callId: String): Call? {
        return CallObjectStorage.getCallObject(callId)
    }

    @Synchronized
    fun setCallObserver(call: Call, callObserver: CallObserver) {
        val callId = call.getCallId() ?: return
        var observers = _callObservers[callId]
        var registerFirstTime = false
        if (observers == null) {
            registerFirstTime = true
            observers = mutableListOf()
        }
        if (!observers.contains(callObserver)) {
            observers.add(callObserver)
        }
        _callObservers[callId] = observers
        if (registerFirstTime)
            registerCallObserver(call)
    }

    inner class WxCallObserver(private val _callId: String) : CallObserver {
        override fun onWaiting(call: Call?, reason: Call.WaitReason?) {
            _callObservers[_callId]?.forEach { it.onWaiting(call, reason) }
        }

        override fun onRinging(call: Call?) {
            _callObservers[_callId]?.forEach { it.onRinging(call) }
        }

        override fun onStartRinging(call: Call?, ringerType: Call.RingerType) {
            _callObservers[_callId]?.forEach { it.onStartRinging(call, ringerType) }
        }

        override fun onStopRinging(call: Call?, ringerType: Call.RingerType) {
            _callObservers[_callId]?.forEach { it.onStopRinging(call, ringerType) }
        }

        override fun onConnected(call: Call?) {
            _callObservers[_callId]?.forEach { it.onConnected(call) }
        }

        override fun onDisconnected(event: CallObserver.CallDisconnectedEvent?) {
            _callObservers[_callId]?.forEach { it.onDisconnected(event) }
            CallObjectStorage.removeCallObject(_callId)
        }

        override fun onInfoChanged(call: Call?) {
            _callObservers[_callId]?.forEach { it.onInfoChanged(call) }
        }

        override fun onCallMembershipChanged(event: CallObserver.CallMembershipChangedEvent?) {
            _callObservers[_callId]?.forEach { it.onCallMembershipChanged(event) }
        }

        override fun onMediaChanged(event: CallObserver.MediaChangedEvent?) {
            _callObservers[_callId]?.forEach { it.onMediaChanged(event) }
        }

        override fun onScheduleChanged(call: Call?) {
            _callObservers[_callId]?.forEach { it.onScheduleChanged(call) }
        }

        override fun onMediaQualityInfoChanged(mediaQualityInfo: Call.MediaQualityInfo) {
            _callObservers[_callId]?.forEach { it.onMediaQualityInfoChanged(mediaQualityInfo) }
        }
    }

    private fun registerCallObserver(call: Call) {
        call.getCallId()?.let {
            call.setObserver(WxCallObserver(it))
        }
    }

    fun removeCallObserver(callId: String, observer: CallObserver) {
        val observers = _callObservers[callId]
        observers?.let {
            it.remove(observer)
            if (it.size == 0)
                _callObservers.remove(callId)
        }
    }

    fun clearCallObservers(callId: String) {
        _callObservers.remove(callId)
    }
}
