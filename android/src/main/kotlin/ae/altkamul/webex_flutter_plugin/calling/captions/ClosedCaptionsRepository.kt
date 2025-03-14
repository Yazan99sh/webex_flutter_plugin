package ae.altkamul.webex_flutter_plugin.calling.captions

import io.reactivex.subjects.BehaviorSubject

class ClosedCaptionsRepository {
    val dataStream: BehaviorSubject<CaptionData> = BehaviorSubject.create()

    fun setCaption(caption: CaptionData) {
        dataStream.onNext(caption)

    }
}