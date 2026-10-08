package com.example.audiary.spotify

object AudiaryLog {
    const val TAG = "AudiaryAuth"

    fun d(message: String) {
        try {
            android.util.Log.d(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG] DEBUG: $message")
        }
    }

    fun i(message: String) {
        try {
            android.util.Log.i(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG] INFO: $message")
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        try {
            android.util.Log.w(TAG, message, throwable)
        } catch (_: Throwable) {
            println("[$TAG] WARN: $message ${throwable?.message.orEmpty()}")
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        try {
            android.util.Log.e(TAG, message, throwable)
        } catch (_: Throwable) {
            println("[$TAG] ERROR: $message ${throwable?.message.orEmpty()}")
        }
    }
}

