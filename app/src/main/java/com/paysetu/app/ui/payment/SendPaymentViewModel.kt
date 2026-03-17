package com.paysetu.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.domain.usecase.SendPaymentUseCase
import kotlinx.coroutines.launch

class SendPaymentViewModel(
    private val sendPaymentUseCase: SendPaymentUseCase
) : ViewModel() {

    fun sendPayment(amount: Long) {
        viewModelScope.launch {
            sendPaymentUseCase.execute(amount)
        }
    }
}
