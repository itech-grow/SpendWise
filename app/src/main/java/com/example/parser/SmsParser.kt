package com.example.parser

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val merchant: String,
    val senderOrAccount: String,
    val isUpi: Boolean,
    val paymentType: String // "BANK" or "CARD"
)

object SmsParser {
    private const val TAG = "SmsParser"
    
    // Match Rs, Rs. Rs ., INR, or amt followed by numeric value (handles commas and decimals)
    private val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|amt|val|vpa)\\s*([0-9,]+(?:\\.[0-9]{2})?)")
    
    private val debitKeywords = listOf("debited", "sent", "paid", "transfer", "spent", "dr", "withdraw")
    private val creditKeywords = listOf("credited", "received", "credited with", "added", "cr")

    fun parse(smsBody: String, sender: String?): ParsedTransaction? {
        val bodyLower = smsBody.lowercase(Locale.ROOT)
        
        // Ensure this is a relevant SMS by checking standard financial terms
        val isFinancial = bodyLower.contains("rs") || bodyLower.contains("inr") || 
                          bodyLower.contains("debited") || bodyLower.contains("credited") || 
                          bodyLower.contains("spent") || bodyLower.contains("received")
        if (!isFinancial) return null
        
        // Parse Amount
        val matcher = amountPattern.matcher(smsBody)
        var amount: Double? = null
        if (matcher.find()) {
            val amtStr = matcher.group(1)?.replace(",", "")
            amount = amtStr?.toDoubleOrNull()
        }
        
        if (amount == null || amount <= 0.0) {
            return null
        }
        
        // Determine Credit vs Debit
        var type = "DEBIT"
        var debitIndex = -1
        var creditIndex = -1
        
        for (kw in debitKeywords) {
            val idx = bodyLower.indexOf(kw)
            if (idx != -1 && (debitIndex == -1 || idx < debitIndex)) {
                debitIndex = idx
            }
        }
        
        for (kw in creditKeywords) {
            val idx = bodyLower.indexOf(kw)
            if (idx != -1 && (creditIndex == -1 || idx < creditIndex)) {
                creditIndex = idx
            }
        }
        
        if (creditIndex != -1 && (debitIndex == -1 || creditIndex < debitIndex)) {
            type = "CREDIT"
        }
        
        // Determine whether UPI transaction
        val isUpi = bodyLower.contains("upi") || bodyLower.contains("vpa") || bodyLower.contains("gpay") || 
                    bodyLower.contains("phonepe") || bodyLower.contains("paytm") || bodyLower.contains("cred") ||
                    bodyLower.contains("@")
        
        // Clean sender name (e.g., AD-HDFCBK -> HDFC Bank, VM-SBIN -> SBI Bank)
        val senderClean = when {
            sender == null -> "Unknown Account"
            sender.contains("HDFCBK", ignoreCase = true) || sender.contains("HDFC", ignoreCase = true) -> "HDFC Bank"
            sender.contains("SBIN", ignoreCase = true) || sender.contains("SBI", ignoreCase = true) -> "SBI Bank"
            sender.contains("ICICI", ignoreCase = true) -> "ICICI Bank"
            sender.contains("AXIS", ignoreCase = true) -> "Axis Bank"
            sender.contains("KOTAK", ignoreCase = true) -> "Kotak Bank"
            sender.contains("PNB", ignoreCase = true) -> "PNB Bank"
            sender.contains("PAYTM", ignoreCase = true) -> "Paytm Bank"
            sender.contains("BOB", ignoreCase = true) -> "Bank of Baroda"
            else -> {
                // Remove SMS channel headers like "AD-", "VK-"
                val parts = sender.split("-")
                val base = if (parts.size > 1) parts[1] else parts[0]
                base.uppercase(Locale.ROOT)
            }
        }
        
        // Extract merchant or counter-party
        var merchant = ""
        val toPatterns = listOf(
            Pattern.compile("(?i)to\\s+vpa\\s+([^\\s,]+)"),
            Pattern.compile("(?i)via\\s+UPI\\s+to\\s+([^\\s,]+(?:\\s+[^\\s,]+){0,2})"),
            Pattern.compile("(?i)paid\\s+to\\s+([^\\s,]+(?:\\s+[^\\s,]+){0,2})"),
            Pattern.compile("(?i)transfer\\s+to\\s+([^\\s,]+(?:\\s+[^\\s,]+){0,2})"),
            Pattern.compile("(?i)to\\s+([^\\s,]{2,15}(?:\\s+[^\\s,]{2,15}){0,1})")
        )
        
        for (p in toPatterns) {
            val toMatcher = p.matcher(smsBody)
            if (toMatcher.find()) {
                val found = toMatcher.group(1)?.trim()
                if (!found.isNullOrBlank() && 
                    !found.equals("upi", true) && 
                    !found.equals("vpa", true) && 
                    !found.contains("Ref", true) && 
                    !found.contains("A/C", true)) {
                    merchant = found.split("Ref")[0].split("on")[0].split("via")[0].trim()
                    break
                }
            }
        }
        
        // Clean up merchant name
        merchant = merchant.replace(Regex("[^a-zA-Z0-9\\s\\.\\-\\@\\_]"), "").trim()
        if (merchant.length > 25) {
            merchant = merchant.take(25) + "..."
        }
        if (merchant.isBlank()) {
            merchant = if (type == "CREDIT") "Deposit / Income" else "Merchant Purchase"
        }
        
        // Ensure accurate Card vs Bank classification based on financial alerts
        val isCard = bodyLower.contains("card") || 
                     bodyLower.contains("credit card") || 
                     bodyLower.contains("debit card") || 
                     bodyLower.contains("spent on cc") || 
                     bodyLower.contains("spent on credit card") || 
                     bodyLower.contains("using card") || 
                     bodyLower.contains("txn of visa") || 
                     bodyLower.contains("visa card") || 
                     bodyLower.contains("mastercard") || 
                     bodyLower.contains("rupay") || 
                     bodyLower.contains("diners") || 
                     bodyLower.contains("amex") || 
                     bodyLower.contains("creditcard") ||
                     bodyLower.contains("debitcard")
        
        val paymentType = if (isCard) "CARD" else "BANK"
        
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant,
            senderOrAccount = senderClean,
            isUpi = isUpi,
            paymentType = paymentType
        )
    }
}
