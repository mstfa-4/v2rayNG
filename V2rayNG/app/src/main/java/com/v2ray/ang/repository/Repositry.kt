package com.v2ray.ang.repository

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers

class Repositry {
    object CustomResponse {
        fun <T : Any> Requst(api: Single<T>, response: (T) -> Unit, error: (String) -> Unit) {
            ResponseSingleton.com().add(
                api
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(object : DisposableSingleObserver<T>() {
                        override fun onSuccess(t: T) {
                            if (t.toString().isNotEmpty()) {
                                response.invoke(t)
                            }
                        }

                        override fun onError(e: Throwable) {
                        }
                    })
            )
        }
    }
}
