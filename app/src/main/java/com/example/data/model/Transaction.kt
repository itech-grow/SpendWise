package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "DEBIT" or "CREDIT"
    val category: String, // e.g., "Food", "Groceries", "Utilities", "Shopping", "Entertainment", "Salary", "Investment", "Others"
    val merchant: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUpi: Boolean = false,
    val senderOrAccount: String = "Manual",
    val smsBody: String? = null,
    val paymentType: String = "BANK" // "BANK" or "CARD"
) {
    companion object {
        fun assignCategory(merchantName: String, type: String): String {
            if (type == "CREDIT") {
                val lower = merchantName.lowercase()
                return when {
                    lower.contains("salary") || lower.contains("pay slip") || lower.contains("payroll") -> "Salary"
                    lower.contains("dividend") || lower.contains("interest") || lower.contains("invest") -> "Investment"
                    lower.contains("cashback") || lower.contains("refund") -> "Refunds"
                    else -> "Income"
                }
            }

            val lower = merchantName.lowercase()
            return when {
                lower.contains("swiggy") || lower.contains("zomato") || lower.contains("rest") || 
                lower.contains("cafe") || lower.contains("hotel") || lower.contains("food") || lower.contains("chai") || lower.contains("tea") -> "Food & Dining"
                
                lower.contains("grocer") || lower.contains("blinkit") || lower.contains("zepto") || 
                lower.contains("mart") || lower.contains("dmart") || lower.contains("supermarket") || lower.contains("milk") -> "Groceries"
                
                lower.contains("ebill") || lower.contains("elec") || lower.contains("power") || 
                lower.contains("broadband") || lower.contains("mobile") || lower.contains("jio") || 
                lower.contains("airtel") || lower.contains("recharge") || lower.contains("water") || 
                lower.contains("gas") || lower.contains("bescom") -> "Utilities"
                
                lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || 
                lower.contains("shop") || lower.contains("mall") || lower.contains("fashion") -> "Shopping"
                
                lower.contains("netflix") || lower.contains("prime") || lower.contains("hotstar") || 
                lower.contains("movie") || lower.contains("pvr") || lower.contains("bookmyshow") || 
                lower.contains("spotify") || lower.contains("game") -> "Entertainment"
                
                lower.contains("uber") || lower.contains("ola") || lower.contains("rapido") || 
                lower.contains("petrol") || lower.contains("shell") || lower.contains("fuel") || 
                lower.contains("auto") || lower.contains("cab") || lower.contains("irctc") || lower.contains("train") -> "Transport"
                
                lower.contains("mutual") || lower.contains("fund") || lower.contains("shares") || 
                lower.contains("sip") || lower.contains("zerodha") || lower.contains("groww") || 
                lower.contains("stock") -> "Investment"
                
                lower.contains("medic") || lower.contains("hosp") || lower.contains("pharm") || 
                lower.contains("apollo") || lower.contains("doctor") || lower.contains("clinic") -> "Medical"
                
                else -> "Others"
            }
        }
    }
}
