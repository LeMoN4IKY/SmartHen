package com.smarthen.app

import androidx.lifecycle.ViewModel

/** Survives Activity recreation alongside account/houses VMs that use this client's engine. */
class ApiResourcesViewModel : ViewModel() {
    val client = platformClient()
    override fun onCleared() { client.close() }
}
