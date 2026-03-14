package com.appojellyapp.feature.streaming.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appojellyapp.feature.streaming.moonlight.connection.PairingManager
import com.appojellyapp.feature.streaming.moonlight.connection.PairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingManager: PairingManager,
) : ViewModel() {

    val pairingStatus: StateFlow<PairingStatus> = pairingManager.status

    fun checkPairingStatus() {
        viewModelScope.launch {
            pairingManager.checkPairingStatus()
        }
    }

    fun requestPairing() {
        pairingManager.requestPairing()
    }

    fun completePairing(pin: String) {
        viewModelScope.launch {
            pairingManager.completePairing(pin)
        }
    }

    fun unpair() {
        viewModelScope.launch {
            pairingManager.unpair()
        }
    }
}
