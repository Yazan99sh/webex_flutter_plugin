package ae.altkamul.webex_flutter_plugin

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.ciscowebex.androidsdk.WebexError
import ae.altkamul.webex_flutter_plugin.databinding.FragmentCallControlsBinding
import ae.altkamul.webex_flutter_plugin.utils.CallObjectStorage
import ae.altkamul.webex_flutter_plugin.utils.showDialogWithMessage
import ae.altkamul.webex_flutter_plugin.utils.UIUtils
import ae.altkamul.webex_flutter_plugin.utils.GlobalExceptionHandler
import ae.altkamul.webex_flutter_plugin.calling.CallActivity
import ae.altkamul.webex_flutter_plugin.calling.CallObserverInterface
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.CallObserver
import com.ciscowebex.androidsdk.phone.MediaOption
import com.ciscowebex.androidsdk.phone.MediaRenderView
import com.ciscowebex.androidsdk.phone.MediaStreamChangeEventType
import com.ciscowebex.androidsdk.phone.MediaStreamChangeEventInfo
import com.ciscowebex.androidsdk.phone.MediaStreamType
import com.ciscowebex.androidsdk.phone.Phone
import com.ciscowebex.androidsdk.phone.CompanionMode
import org.koin.androidx.viewmodel.ext.android.viewModel

class CallControlsFragment : Fragment(), OnClickListener, CallObserverInterface {
    val webexViewModel: WebexViewModel by viewModel()

    private lateinit var binding: FragmentCallControlsBinding
    private var callFailed = false
    private var isInPipMode = false

    private val mHandler = Handler(Looper.getMainLooper())

    enum class NetworkStatus {
        PoorUplink, PoorDownlink, Good, NoNetwork
    }

    var currentNetworkStatus = NetworkStatus.Good

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return DataBindingUtil.inflate<FragmentCallControlsBinding>(
            LayoutInflater.from(context),
            R.layout.fragment_call_controls, container, false
        ).also { binding = it }.apply {
            setUpViews(arguments)
            observerCallLiveData()
        }.root
    }

    override fun onResume() {
        super.onResume()
        webexViewModel.currentCallId?.let { onVideoStreamingChanged(it) }
        webexViewModel.enableStreams()
    }

    private fun getMediaOption(): MediaOption {
        val mediaOption: MediaOption =
            if (webexViewModel.callCapability == WebexRepository.CallCap.Audio_Only) {
                MediaOption.audioOnly()
            } else {
                MediaOption.audioVideoSharing(
                    android.util.Pair(binding.localView, binding.remoteView),
                    binding.screenShareView
                )
            }
        return mediaOption
    }

    fun dialOutgoingCall(callerId: String) {
        webexViewModel.dial(callerId, getMediaOption())
    }

    // === CallObserverInterface ===

    override fun onConnected(call: Call?) {
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler())
        onCallConnected(call?.getCallId().orEmpty())
        webexViewModel.setShareMaxCaptureFPSSetting(30)
    }

    override fun onStartRinging(call: Call?, ringerType: Call.RingerType) {}
    override fun onStopRinging(call: Call?, ringerType: Call.RingerType) {}
    override fun onWaiting(call: Call?) {}

    override fun onDisconnected(call: Call?, event: CallObserver.CallDisconnectedEvent?) {
        Thread.setDefaultUncaughtExceptionHandler(null)
        var callFailed = false
        var callEnded = false
        var failedError: WebexError<Any>? = null

        event?.let { _event ->
            when (_event) {
                is CallObserver.OtherConnected -> callEnded = true
                is CallObserver.CallErrorEvent -> {
                    failedError = _event.getError()
                    callFailed = true
                }
                is CallObserver.CallEnded -> callEnded = true
            }
        }

        when {
            callFailed -> onCallFailed(call?.getCallId().orEmpty(), failedError)
            callEnded -> onCallTerminated(call?.getCallId().orEmpty())
        }
    }

    override fun onInfoChanged(call: Call?) {}

    override fun onMediaChanged(call: Call?, event: CallObserver.MediaChangedEvent?) {
        event?.let { _event ->
            val eventCall = _event.getCall()
            when (_event) {
                is CallObserver.RemoteSendingVideoEvent -> {
                    webexViewModel.isRemoteVideoMuted = !_event.isSending()
                    onVideoStreamingChanged(eventCall?.getCallId().orEmpty())
                }
                is CallObserver.SendingVideo -> {
                    webexViewModel.isLocalVideoMuted = !_event.isSending()
                    onVideoStreamingChanged(eventCall?.getCallId().orEmpty())
                }
                is CallObserver.ReceivingVideo -> {
                    webexViewModel.isRemoteVideoMuted = !_event.isReceiving()
                    onVideoStreamingChanged(eventCall?.getCallId().orEmpty())
                }
                is CallObserver.MediaStreamAvailabilityEvent -> {
                    onMediaStreamAvailabilityEvent(eventCall?.getCallId().orEmpty(), _event)
                }
            }
        }
    }

    private fun onMediaStreamAvailabilityEvent(
        callId: String, event: CallObserver.MediaStreamAvailabilityEvent
    ) {
        if (webexViewModel.currentCallId != callId) return

        mHandler.post {
            if (event.isAvailable()) {
                if (event.getStream()?.getStreamType() == MediaStreamType.Stream1) {
                    onVideoStreamingChanged(webexViewModel.currentCallId.toString())
                    setRemoteVideoInformation(
                        event.getStream()?.getPerson()?.getDisplayName().orEmpty(),
                        !(event.getStream()?.getPerson()?.isSendingAudio() ?: true)
                    )
                }
            }

            event.getStream()?.setOnMediaStreamInfoChanged { type, info ->
                mediaStreamInfoChangedListener(type, info)
            }
        }
    }

    private fun mediaStreamInfoChangedListener(
        type: MediaStreamChangeEventType, info: MediaStreamChangeEventInfo
    ) {
        mHandler.post {
            when (type) {
                MediaStreamChangeEventType.Video -> {
                    // Handle video stream changes for main stream
                }
                MediaStreamChangeEventType.Audio -> {
                    if (info.getStream().getStreamType() == MediaStreamType.Stream1) {
                        setRemoteVideoInformation(
                            info.getStream().getPerson()?.getDisplayName().orEmpty(),
                            !(info.getStream().getPerson()?.isSendingAudio() ?: true)
                        )
                    }
                }
                MediaStreamChangeEventType.Membership -> {
                    val membership = info.getStream().getPerson()
                    membership?.let { member ->
                        if (info.getStream().getStreamType() == MediaStreamType.Stream1) {
                            setRemoteVideoInformation(
                                member.getDisplayName().orEmpty(),
                                !(member.isSendingAudio())
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }

    override fun onMediaQualityInfoChanged(mediaQualityInfo: Call.MediaQualityInfo) {
        updateNetworkStatusChange(mediaQualityInfo)
    }

    // === UI Setup & Observers ===

    @SuppressLint("NotifyDataSetChanged")
    private fun observerCallLiveData() {
        webexViewModel.setCompositeLayoutLiveData.observe(viewLifecycleOwner, Observer { result ->
            result?.let {
                if (it.first) {
                    webexViewModel.compositedVideoLayout = webexViewModel.compositedLayoutState
                }
            }
        })

        webexViewModel.callingLiveData.observe(viewLifecycleOwner, Observer {
            it?.let {
                when (it.event) {
                    WebexRepository.CallEvent.DialCompleted -> {
                        onCallJoined(it.call)
                    }
                    WebexRepository.CallEvent.DialFailed,
                    WebexRepository.CallEvent.WrongApiCalled,
                    WebexRepository.CallEvent.CannotStartInstantMeeting -> {
                        showErrorState(it.errorMessage ?: it.event.name)
                    }
                    else -> {}
                }
            }
        })

        webexViewModel.authLiveData.observe(viewLifecycleOwner, Observer {
            if (it != null && it == Constants.Callbacks.RE_LOGIN_REQUIRED) {
                val callActivity = activity as? CallActivity
                callActivity?.finishWithResult(Constants.Result.STATUS_AUTH_FAILED, "Re-login required")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webexViewModel.currentCallId?.let { webexViewModel.setVideoRenderViews(it) }
        webexViewModel.cleanup()
        webexViewModel.callObserverInterface = null
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun setUpViews(bundle: Bundle?) {
        videoViewState(true)
        webexViewModel.callObserverInterface = this

        webexViewModel.enableBackgroundStream(webexViewModel.enableBgStreamtoggle)
        webexViewModel.enableAudioBNR(true)
        webexViewModel.setAudioBNRMode(Phone.AudioBRNMode.HP)
        webexViewModel.setDefaultFacingMode(Phone.FacingMode.USER)
        webexViewModel.setVideoMaxTxFPSSetting(30)
        webexViewModel.setVideoEnableCamera2Setting(true)
        webexViewModel.setVideoEnableDecoderMosaicSetting(true)
        webexViewModel.setSharingMaxRxBandwidth(Phone.DefaultBandwidth.MAX_BANDWIDTH_SESSION.getValue())
        webexViewModel.setAudioMaxRxBandwidth(Phone.DefaultBandwidth.MAX_BANDWIDTH_AUDIO.getValue())
        webexViewModel.setVideoStreamMode(webexViewModel.streamMode)

        binding.callingHeader.text = getString(R.string.calling)
        val callerId = bundle?.getString(Constants.Intent.OUTGOING_CALL_CALLER_ID)
        binding.tvName.text = callerId

        binding.ivCancelCall.setOnClickListener(this)
        binding.mainContentLayout.setOnClickListener(this)
        binding.ivNetworkSignal.setOnClickListener(this)
        binding.ivNetworkSignal.visibility = View.GONE

        binding.goBackButton.setOnClickListener {
            val callActivity = activity as? CallActivity
            callActivity?.finishWithResult(Constants.Result.STATUS_CALL_FAILED, "Call failed")
        }
        binding.goBackButton.visibility = View.GONE
    }

    override fun onClick(v: View?) {
        webexViewModel.currentCallId?.let { callId ->
            when (v) {
                binding.ivCancelCall -> endCall()
                binding.ivNetworkSignal -> {
                    val text = "Network: ${currentNetworkStatus.name}"
                    android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    fun onBackPressed() {
        endCall()
    }

    private fun endCall() {
        webexViewModel.currentCallId?.let {
            webexViewModel.hangup(it)
            val callActivity = activity as? CallActivity
            callActivity?.finishWithResult(Constants.Result.STATUS_CALL_ENDED)
        } ?: run {
            val callActivity = activity as? CallActivity
            callActivity?.finishWithResult(Constants.Result.STATUS_CALL_ENDED)
        }
    }

    fun showErrorState(message: String) {
        mHandler.post {
            if (isAdded) {
                binding.callingHeader.text = getString(R.string.call_failed)
                binding.callingHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red))
                binding.tvName.text = message
                binding.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                binding.goBackButton.visibility = View.VISIBLE
                binding.ivCancelCall.visibility = View.GONE
            }
        }
    }

    // === Video View Management ===

    private fun videoViewTextColorState(hidden: Boolean) {
        var hide = hidden
        if (hide && webexViewModel.isRemoteScreenShareON) hide = false

        if (hide) {
            binding.callingHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            binding.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        } else {
            val status = isMainStageRemoteVideoUnMuted()
            if (status) {
                binding.callingHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                binding.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }
    }

    private fun localVideoViewState(toHide: Boolean) {
        if (toHide) {
            binding.localViewLayout.visibility = View.GONE
        } else {
            binding.localViewLayout.visibility = View.VISIBLE
            binding.localView.setZOrderOnTop(true)
        }
    }

    private fun resizeRemoteVideoView() {
        if (webexViewModel.isRemoteScreenShareON) {
            val width = resources.getDimension(R.dimen.remote_video_view_width).toInt()
            val height = resources.getDimension(R.dimen.remote_video_view_height).toInt()
            val params = ConstraintLayout.LayoutParams(width, height)
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.marginStart = resources.getDimension(R.dimen.remote_video_view_margin_start).toInt()
            params.bottomMargin = resources.getDimension(R.dimen.remote_video_view_margin_Bottom).toInt()
            binding.remoteViewLayout.layoutParams = params
            binding.remoteViewLayout.background = ContextCompat.getDrawable(requireActivity(), R.drawable.surfaceview_border)
            binding.remoteView.setZOrderOnTop(true)
        } else {
            val params = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            binding.remoteViewLayout.layoutParams = params
            binding.remoteViewLayout.background = ContextCompat.getDrawable(requireActivity(), R.drawable.surfaceview_transparent_border)
            binding.remoteView.setZOrderOnTop(false)
        }
    }

    private fun videoViewState(toHide: Boolean) {
        localVideoViewState(toHide)
        if (toHide) {
            binding.remoteViewLayout.visibility = View.GONE
        } else {
            val status = isMainStageRemoteVideoUnMuted()
            if (status) binding.remoteViewLayout.visibility = View.VISIBLE
        }
        videoViewTextColorState(toHide)
    }

    private fun onCallConnected(callId: String) {
        mHandler.post {
            val layout = webexViewModel.getCompositedLayout()
            binding.ivNetworkSignal.visibility = View.VISIBLE
            webexViewModel.setCompositedLayout(layout)

            if (callId == webexViewModel.currentCallId) {
                val callInfo = webexViewModel.getCall(callId)
                var isSelfVideoMuted = true
                callInfo?.let { _callInfo ->
                    isSelfVideoMuted = !_callInfo.isSendingVideo()
                    webexViewModel.isRemoteVideoMuted = !_callInfo.isReceivingVideo()
                }
                binding.videoCallLayout.visibility = View.VISIBLE

                webexViewModel.isLocalVideoMuted = isSelfVideoMuted
                onVideoStreamingChanged(callId)

                localVideoViewState(webexViewModel.isLocalVideoMuted)

                if (webexViewModel.isRemoteVideoMuted) {
                    binding.remoteViewLayout.visibility = View.GONE
                } else {
                    val status = isMainStageRemoteVideoUnMuted()
                    if (status) binding.remoteViewLayout.visibility = View.VISIBLE
                }
                videoViewTextColorState(webexViewModel.isRemoteVideoMuted)
            }
        }
    }

    private fun setRemoteVideoInformation(name: String, audioMuted: Boolean) {
        binding.tvRemoteUserName.text = name
        if (audioMuted) {
            binding.ivRemoteAudioState.setImageResource(R.drawable.ic_microphone_muted_bold)
        } else {
            binding.ivRemoteAudioState.setImageResource(R.drawable.ic_microphone_36)
        }
    }

    private fun onVideoStreamingChanged(callId: String) {
        if (webexViewModel.currentCallId == null) return

        mHandler.post {
            if (isAdded) {
                if (webexViewModel.isLocalVideoMuted) {
                    localVideoViewState(true)
                } else {
                    localVideoViewState(false)
                    val pair = webexViewModel.getVideoRenderViews(callId)
                    if (pair.first == null) {
                        webexViewModel.setVideoRenderViews(callId, binding.localView, binding.remoteView)
                    }
                }

                if (webexViewModel.isRemoteVideoMuted) {
                    binding.remoteViewLayout.visibility = View.GONE
                    binding.ivRemoteAudioState.visibility = View.GONE
                    binding.tvRemoteUserName.visibility = View.GONE
                } else {
                    if (webexViewModel.isRemoteScreenShareON) resizeRemoteVideoView()
                    val status = isMainStageRemoteVideoUnMuted()
                    if (status) {
                        binding.remoteViewLayout.visibility = View.VISIBLE
                        val pair = webexViewModel.getVideoRenderViews(callId)
                        if (pair.second == null && webexViewModel.callCapability != WebexRepository.CallCap.Audio_Only) {
                            webexViewModel.setVideoRenderViews(callId, binding.localView, binding.remoteView)
                        }
                        if (webexViewModel.streamMode != Phone.VideoStreamMode.COMPOSITED) {
                            binding.ivRemoteAudioState.visibility = View.VISIBLE
                            binding.tvRemoteUserName.visibility = View.VISIBLE
                        } else {
                            binding.ivRemoteAudioState.visibility = View.GONE
                            binding.tvRemoteUserName.visibility = View.GONE
                        }
                    }
                }

                videoViewTextColorState(webexViewModel.isRemoteVideoMuted)

                if (isInPipMode && !webexViewModel.isRemoteVideoMuted) {
                    localVideoViewState(true)
                    binding.ivRemoteAudioState.visibility = View.GONE
                    binding.tvRemoteUserName.visibility = View.GONE
                }
            }
        }
    }

    private fun isMainStageRemoteVideoUnMuted(): Boolean {
        if (!webexViewModel.isRemoteVideoMuted) {
            val streams = webexViewModel.getMediaStreams()
            streams?.let { streamList ->
                val stream = streamList.find { it.getStreamType() == MediaStreamType.Stream1 }
                stream?.let { return it.getPerson()?.isSendingVideo() ?: false }
            }
        }
        return false
    }

    // === Call State Handling ===

    private fun onCallJoined(call: Call?) {
        mHandler.post {
            if (call?.getCallId().orEmpty() == webexViewModel.currentCallId) {
                binding.callingHeader.text = getString(R.string.onCall)
            }
        }
    }

    private fun onCallFailed(callId: String, failedError: WebexError<Any>?) {
        mHandler.post {
            callFailed = true
            showErrorState(failedError?.errorMessage ?: getString(R.string.call_failed))
        }
    }

    private fun onCallTerminated(callId: String) {
        webexViewModel.clearCallObservers(callId)
        CallObjectStorage.removeCallObject(callId)

        mHandler.post {
            if (!callFailed) {
                val callActivity = activity as? CallActivity
                callActivity?.finishWithResult(Constants.Result.STATUS_CALL_ENDED)
            }
        }
    }

    private fun updateNetworkStatusChange(mediaQualityInfo: Call.MediaQualityInfo) {
        mHandler.post {
            if (!isAdded) return@post
            when (mediaQualityInfo) {
                Call.MediaQualityInfo.NetworkLost -> {
                    binding.ivNetworkSignal.setImageResource(R.drawable.ic_no_network)
                    currentNetworkStatus = NetworkStatus.NoNetwork
                }
                Call.MediaQualityInfo.Good -> {
                    binding.ivNetworkSignal.setImageResource(R.drawable.ic_good_network)
                    currentNetworkStatus = NetworkStatus.Good
                }
                Call.MediaQualityInfo.PoorUplink -> {
                    binding.ivNetworkSignal.setImageResource(R.drawable.ic_poor_network)
                    currentNetworkStatus = NetworkStatus.PoorUplink
                }
                Call.MediaQualityInfo.PoorDownlink -> {
                    binding.ivNetworkSignal.setImageResource(R.drawable.ic_poor_network)
                    currentNetworkStatus = NetworkStatus.PoorDownlink
                }
                Call.MediaQualityInfo.HighCpuUsage -> {
                    showDialogWithMessage(requireContext(), R.string.warning, getString(R.string.high_cpu_usage))
                }
                Call.MediaQualityInfo.DeviceLimitation -> {
                    showDialogWithMessage(requireContext(), R.string.warning, getString(R.string.device_limitation))
                }
            }
        }
    }

    // === PIP & Configuration ===

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.localViewLayout.layoutParams.height =
                requireActivity().resources.getDimension(R.dimen.local_video_view_width).toInt()
            binding.localViewLayout.layoutParams.width =
                requireActivity().resources.getDimension(R.dimen.local_video_view_height).toInt()
        } else {
            binding.localViewLayout.layoutParams.height =
                requireActivity().resources.getDimension(R.dimen.local_video_view_height).toInt()
            binding.localViewLayout.layoutParams.width =
                requireActivity().resources.getDimension(R.dimen.local_video_view_width).toInt()
        }
        binding.localViewLayout.requestLayout()
    }

    fun pipVisibility(currentView: Int, inPipMode: Boolean) {
        isInPipMode = inPipMode
        if (currentView == View.GONE) {
            binding.videoCallLayout.layoutParams.height = 400
            if (binding.screenShareView.isVisible) {
                binding.remoteViewLayout.visibility = currentView
            }
        } else {
            binding.videoCallLayout.layoutParams.height =
                resources.getDimension(R.dimen.video_view_height).toInt()
            binding.remoteViewLayout.visibility = currentView
        }
        binding.localViewLayout.visibility = currentView
        binding.ivNetworkSignal.visibility = currentView
        binding.tvRemoteUserName.visibility = currentView
        binding.ivRemoteAudioState.visibility = currentView
        binding.callingHeader.visibility = currentView
        binding.tvName.visibility = currentView
        binding.ivCancelCall.visibility = currentView
    }

    fun aspectRatio(): Rational {
        val width = binding.videoCallLayout.width
        val height = binding.videoCallLayout.height
        return if (width > 0 && height > 0) {
            getCoercedRational(width, height)
        } else if (UIUtils.isPortraitMode(requireContext())) {
            Rational(9, 16)
        } else {
            Rational(16, 9)
        }
    }

    private fun getCoercedRational(width: Int, height: Int): Rational {
        return when {
            width.toFloat() / height.toFloat() > 2.1f -> Rational(21, 10)
            width.toFloat() / height.toFloat() < 1 / 2.1f -> Rational(10, 21)
            else -> Rational(width, height)
        }
    }

    private val View.isVisible: Boolean get() = visibility == View.VISIBLE

    companion object {
        private const val tag = "CallControlsFragment"
    }
}
