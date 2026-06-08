package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Transaction
import com.example.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val db = AppDatabase.getDatabase(context)
            val dao = db.financeDao()

            for (msg in messages) {
                val body = msg.messageBody ?: continue
                val sender = msg.originatingAddress
                
                val parsed = SmsParser.parse(body, sender)
                if (parsed != null) {
                    val transaction = Transaction(
                        amount = parsed.amount,
                        type = parsed.type,
                        category = Transaction.assignCategory(parsed.merchant, parsed.type),
                        merchant = parsed.merchant,
                        isUpi = parsed.isUpi,
                        senderOrAccount = parsed.senderOrAccount,
                        smsBody = body,
                        timestamp = msg.timestampMillis,
                        paymentType = parsed.paymentType
                    )
                    
                    scope.launch {
                        try {
                            dao.insertTransaction(transaction)
                            Log.d("SmsReceiver", "Successfully tracked background transaction: $transaction")
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Failed to insert tracked transaction: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }
}
