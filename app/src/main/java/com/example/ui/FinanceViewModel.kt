package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Budget
import com.example.data.model.SavingGoal
import com.example.data.model.Transaction
import com.example.data.repository.FinanceRepository
import com.example.parser.SmsParser
import com.example.network.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    // --- Database Streams ---
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBudgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSavingGoals: StateFlow<List<SavingGoal>> = repository.allSavingGoals
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- SMS Inbox Scanning ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<String?>(null)
    val scanResult: StateFlow<String?> = _scanResult.asStateFlow()

    // --- Gemini Chatbot ---
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                message = "Hello! I am SpendWise AI, your household finance advisor. Try scanning your bank SMS or asking me things like 'How much did I spend?' or 'How can I save Rs 5000?'",
                isUser = false
            )
        )
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    // --- SMS Loader Action ---
    fun scanSmsInbox(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanResult.value = null
            var importedCount = 0
            
            try {
                val cursor = context.contentResolver.query(
                    Uri.parse("content://sms/inbox"),
                    arrayOf("address", "body", "date"),
                    null,
                    null,
                    "date DESC"
                )

                cursor?.use { c ->
                    val addressIdx = c.getColumnIndexOrThrow("address")
                    val bodyIdx = c.getColumnIndexOrThrow("body")
                    val dateIdx = c.getColumnIndexOrThrow("date")

                    val existingList = repository.allTransactions.firstOrNull() ?: emptyList()
                    val existingSignatures = existingList.map { 
                        "${it.amount}_${it.type}_${it.timestamp}" 
                    }.toSet()

                    while (c.moveToNext()) {
                        val sender = c.getString(addressIdx)
                        val body = c.getString(bodyIdx)
                        val dateMillis = c.getLong(dateIdx)

                        val parsed = SmsParser.parse(body, sender)
                        if (parsed != null) {
                            val signature = "${parsed.amount}_${parsed.type}_$dateMillis"
                            if (!existingSignatures.contains(signature)) {
                                val category = Transaction.assignCategory(parsed.merchant, parsed.type)
                                val transaction = Transaction(
                                    amount = parsed.amount,
                                    type = parsed.type,
                                    category = category,
                                    merchant = parsed.merchant,
                                    isUpi = parsed.isUpi,
                                    senderOrAccount = parsed.senderOrAccount,
                                    smsBody = body,
                                    timestamp = dateMillis,
                                    paymentType = parsed.paymentType
                                )
                                repository.insertTransaction(transaction)
                                importedCount++
                            }
                        }
                    }
                }
                _scanResult.value = if (importedCount > 0) {
                    "Success! Automatically imported $importedCount past bank transactions."
                } else {
                    "Scan completed: No new transactions found in SMS inbox."
                }
            } catch (e: Exception) {
                Log.e("FinanceViewModel", "Error scanning SMS", e)
                _scanResult.value = "Error scanning: Check SMS reading permission."
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    // --- CRUD API ---
    fun addTransaction(amount: Double, type: String, merchant: String, category: String, isUpi: Boolean = false, sender: String = "Manual", paymentType: String = "BANK") {
        viewModelScope.launch {
            val tx = Transaction(
                amount = amount,
                type = type,
                merchant = merchant,
                category = category,
                isUpi = isUpi,
                senderOrAccount = sender,
                timestamp = System.currentTimeMillis(),
                paymentType = paymentType
            )
            repository.insertTransaction(tx)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun setBudget(category: String, amount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(category, amount))
        }
    }

    fun deleteBudget(category: String) {
        viewModelScope.launch {
            repository.deleteBudgetByCategory(category)
        }
    }

    fun addSavingGoal(name: String, targetAmount: Double, currentAmount: Double, deadline: String) {
        viewModelScope.launch {
            val goal = SavingGoal(
                name = name,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                deadline = deadline
            )
            repository.insertSavingGoal(goal)
        }
    }

    fun deleteSavingGoal(goal: SavingGoal) {
        viewModelScope.launch {
            repository.deleteSavingGoal(goal)
        }
    }

    fun updateSavingGoalProgress(goal: SavingGoal, addAmount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(currentAmount = (goal.currentAmount + addAmount).coerceIn(0.0, goal.targetAmount))
            repository.insertSavingGoal(updated)
        }
    }

    // --- Gemini Question Answering ---
    fun askAi(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _chatHistory.value = _chatHistory.value + ChatMessage(query, isUser = true)
            _aiLoading.value = true

            val transactions = allTransactions.value
            val budgets = allBudgets.value
            val goals = allSavingGoals.value

            val totalDebit = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
            val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
            val netSavings = totalCredit - totalDebit

            val categorySpends = transactions.filter { it.type == "DEBIT" }
                .groupBy { it.category }
                .mapValues { it.value.sumOf { it.amount } }

            val contextBuilder = StringBuilder()
            contextBuilder.append("You are SpendWise AI, an expert household finance advisor. Answer user queries briefly (under 4-5 sentences) and warmly. ")
            contextBuilder.append("Directly use their current data to provide exact figures. Frame suggestions to let them save.\n\n")
            
            contextBuilder.append("User's Dashboard Data:\n")
            contextBuilder.append("- Total Expenses (Debits): Rs. $totalDebit\n")
            contextBuilder.append("- Total Income (Credits): Rs. $totalCredit\n")
            contextBuilder.append("- Remaining Net Savings: Rs. $netSavings\n\n")

            contextBuilder.append("Categories of Spending:\n")
            categorySpends.forEach { (cat, amt) ->
                contextBuilder.append("  * $cat: Rs. $amt")
                val budget = budgets.find { it.category.equals(cat, true) }
                if (budget != null) {
                    contextBuilder.append(" (Budget set to: Rs. ${budget.limitAmount}. Status: ${if (amt > budget.limitAmount) "OVERBUDGET!" else "Within limit"})")
                }
                contextBuilder.append("\n")
            }

            contextBuilder.append("\nActive Saving Goals:\n")
            if (goals.isEmpty()) {
                contextBuilder.append("  * None set.\n")
            } else {
                goals.forEach { g ->
                    contextBuilder.append("  * ${g.name}: Target Rs. ${g.targetAmount}, Saved: Rs. ${g.currentAmount} (Deadline: ${g.deadline})\n")
                }
            }

            contextBuilder.append("\nRecent 10 UPI/Bank transactions:\n")
            transactions.take(10).forEach { t ->
                val typeStr = if (t.type == "DEBIT") "Spent" else "Received"
                contextBuilder.append("  * Rs. ${t.amount} - $typeStr at ${t.merchant} (${t.category}) via ${t.senderOrAccount}\n")
            }

            val systemPrompt = contextBuilder.toString()
            val answer = GeminiClient.queryGemini(query, systemPrompt)

            _chatHistory.value = _chatHistory.value + ChatMessage(answer, isUser = false)
            _aiLoading.value = false
        }
    }

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis: StateFlow<String?> = _aiAnalysis.asStateFlow()

    private val _aiAnalysisLoading = MutableStateFlow(false)
    val aiAnalysisLoading: StateFlow<Boolean> = _aiAnalysisLoading.asStateFlow()

    fun runFinancialAnalysis() {
        viewModelScope.launch {
            _aiAnalysisLoading.value = true
            _aiAnalysis.value = null

            val transactions = allTransactions.value
            val budgets = allBudgets.value
            val goals = allSavingGoals.value

            val totalDebit = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
            val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
            val netSavings = totalCredit - totalDebit

            val categorySpends = transactions.filter { it.type == "DEBIT" }
                .groupBy { it.category }
                .mapValues { it.value.sumOf { it.amount } }

            val prompt = StringBuilder()
            prompt.append("Core Finance Analysis Prompt:\n")
            prompt.append("You are a professional financial planner and household reserves advisor. ")
            prompt.append("Analyze the client's current spending, earnings, and goals carefully. ")
            prompt.append("Provide a highly constructive, encouraging, and actionable response with 4 clear specific financial recommendations, each formatted with a short bold title.\n\n")
            prompt.append("Current Portfolio Data:\n")
            prompt.append("- Total Income (Credit): Rs. $totalCredit\n")
            prompt.append("- Total Spend (Debit): Rs. $totalDebit\n")
            prompt.append("- Net Monthly Surplus/Savings: Rs. $netSavings\n\n")

            prompt.append("Category Spend vs Budgets:\n")
            if (categorySpends.isEmpty()) {
                prompt.append("- No category spends recorded yet.\n")
            }
            categorySpends.forEach { (cat, amt) ->
                prompt.append("* $cat: Spent Rs. $amt")
                val budget = budgets.find { it.category.equals(cat, true) }
                if (budget != null) {
                    prompt.append(" (Limit: Rs. ${budget.limitAmount})")
                }
                prompt.append("\n")
            }

            prompt.append("\nActive Saving Goals:\n")
            if (goals.isEmpty()) {
                prompt.append("- No saving targets registered.\n")
            } else {
                goals.forEach { g ->
                    prompt.append("* ${g.name}: Target Rs. ${g.targetAmount}, Current progress: Rs. ${g.currentAmount} (Deadline: ${g.deadline})\n")
                }
            }

            val systemPrompt = "You are a friendly, direct, and structured financial planner. Present your recommendations directly as 4 concise advice suggestions with emojis and numbered headers. Keep complete response under 1000 characters."
            val answer = GeminiClient.queryGemini(prompt.toString(), systemPrompt)

            _aiAnalysis.value = answer
            _aiAnalysisLoading.value = false
        }
    }

    fun clearChat() {
        _chatHistory.value = listOf(
            ChatMessage(
                message = "Chat history cleared. How can I help you analyze your household budget today?",
                isUser = false
            )
        )
    }
}

// Factory to pass dynamic dependencies to ViewModel
class FinanceViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
