package com.v2ray.ang.repository

import io.reactivex.disposables.CompositeDisposable

object ResponseSingleton {
    var INSTANCE: CompositeDisposable? = null
    fun com(): CompositeDisposable {
        if (INSTANCE==null) {
            INSTANCE = CompositeDisposable()
        }
        return INSTANCE as CompositeDisposable
    }
}