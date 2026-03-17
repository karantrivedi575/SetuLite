package com.paysetu.app.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReceivePaymentScreen(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Incoming Offline Payment")

        Text("Amount: ₹250")

        Row {
            Button(onClick = onAccept) {
                Text("Accept")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onReject) {
                Text("Reject")
            }
        }
    }
}
