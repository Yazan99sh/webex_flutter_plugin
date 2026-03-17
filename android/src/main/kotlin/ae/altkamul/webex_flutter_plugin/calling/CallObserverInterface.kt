package ae.altkamul.webex_flutter_plugin.calling

import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.CallObserver

interface CallObserverInterface {
    fun onConnected(call: Call?) {}
    fun onRinging(call: Call?) {}
    fun onStartRinging(call: Call?, ringerType: Call.RingerType) {}
    fun onStopRinging(call: Call?, ringerType: Call.RingerType) {}
    fun onWaiting(call: Call?) {}
    fun onDisconnected(call: Call?, event: CallObserver.CallDisconnectedEvent?) {}
    fun onInfoChanged(call: Call?) {}
    fun onMediaChanged(call: Call?, event: CallObserver.MediaChangedEvent?) {}
    fun onCallMembershipChanged(call: Call?, event: CallObserver.CallMembershipChangedEvent?) {}
    fun onScheduleChanged(call: Call?) {}
    fun onMediaQualityInfoChanged(mediaQualityInfo: Call.MediaQualityInfo) {}
}
