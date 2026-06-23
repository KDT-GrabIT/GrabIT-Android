package com.example.grabitTest

class BeepFeedbackController {

    private val beepPlayer = BeepPlayer()

    fun init(): Boolean {
        return beepPlayer.init()
    }

    fun playListeningStart(onDone: () -> Unit) {
        beepPlayer.playBeep(onDone)
    }

    fun release() {
        beepPlayer.release()
    }
}