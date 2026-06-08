package com.example.data.repository

import com.example.data.local.FinanceDao
import com.example.data.model.Budget
import com.example.data.model.SavingGoal
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val financeDao: FinanceDao) {

    val allTransactions: Flow<List<Transaction>> = financeDao.getAllTransactions()
    val allBudgets: Flow<List<Budget>> = financeDao.getAllBudgets()
    val allSavingGoals: Flow<List<SavingGoal>> = financeDao.getAllSavingGoals()

    suspend fun insertTransaction(transaction: Transaction) {
        financeDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        financeDao.deleteTransaction(transaction)
    }

    suspend fun clearAllTransactions() {
        financeDao.clearAllTransactions()
    }

    suspend fun insertBudget(budget: Budget) {
        financeDao.insertBudget(budget)
    }

    suspend fun deleteBudgetByCategory(category: String) {
        financeDao.deleteBudgetByCategory(category)
    }

    suspend fun insertSavingGoal(goal: SavingGoal) {
        financeDao.insertSavingGoal(goal)
    }

    suspend fun deleteSavingGoal(goal: SavingGoal) {
        financeDao.deleteSavingGoal(goal)
    }
}
