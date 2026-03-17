import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.domain.usecase.ReceivePaymentUseCase
import kotlinx.coroutines.launch

class ReceivePaymentViewModel(
    private val receivePaymentUseCase: ReceivePaymentUseCase
) : ViewModel() {

}
