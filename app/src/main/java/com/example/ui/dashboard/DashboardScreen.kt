package com.example.ui.dashboard

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Budget
import com.example.data.model.SavingGoal
import com.example.data.model.Transaction
import com.example.ui.ChatMessage
import com.example.ui.FinanceViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val budgets by viewModel.allBudgets.collectAsStateWithLifecycle()
    val savingGoals by viewModel.allSavingGoals.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiAnalysis by viewModel.aiAnalysis.collectAsStateWithLifecycle()
    val aiAnalysisLoading by viewModel.aiAnalysisLoading.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("dashboard") } // "dashboard", "transactions", "chat"
    var showAddTxDialog by remember { mutableStateOf(false) }
    var addTxDefaultType by remember { mutableStateOf("DEBIT") }
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Display SMS Scan Results Toast
    LaunchedEffect(scanResult) {
        scanResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearScanResult()
        }
    }

    // Handle SMS Permissions Launcher
    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (readGranted && receiveGranted) {
            Toast.makeText(context, "Permissions granted! Scanning inbox...", Toast.LENGTH_SHORT).show()
            viewModel.scanSmsInbox(context)
        } else {
            Toast.makeText(context, "Permissions denied. Cannot automatically scan UPI SMS.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FinanceBottomNavigation(
                activeTab = activeTab,
                onTabSelect = { activeTab = it }
            )
        },
        floatingActionButton = {
            if (activeTab != "chat") {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == "dashboard") {
                            showAddGoalDialog = true
                        } else {
                            showAddTxDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add New Entry"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // High-fidelity Emerald-themed top header
            HeaderBanner(
                transactions = transactions,
                onScanClicked = {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    } else {
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    }
                    smsPermissionsLauncher.launch(permissions)
                },
                isScanning = isScanning
            )

            // Dynamic view based on chosen state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeTab) {
                    "dashboard" -> {
                        DashboardTab(
                            transactions = transactions,
                            budgets = budgets,
                            savingGoals = savingGoals,
                            onAddBudgetClick = { showAddBudgetDialog = true },
                            onDeleteBudgetClick = { viewModel.deleteBudget(it) },
                            onGoalTopup = { goal, amt -> viewModel.updateSavingGoalProgress(goal, amt) },
                            onGoalDelete = { viewModel.deleteSavingGoal(it) },
                            aiAnalysis = aiAnalysis,
                            aiAnalysisLoading = aiAnalysisLoading,
                            onRunAnalysis = { viewModel.runFinancialAnalysis() },
                            onAddSpendClick = {
                                addTxDefaultType = "DEBIT"
                                showAddTxDialog = true
                            },
                            onAddEarningClick = {
                                addTxDefaultType = "CREDIT"
                                showAddTxDialog = true
                            },
                            onAddGoalClick = { showAddGoalDialog = true },
                            onLoadMaySampleData = { viewModel.generateMaySampleData() }
                        )
                    }
                    "transactions" -> {
                        TransactionsTab(
                            transactions = transactions,
                            onDeleteTransaction = { viewModel.deleteTransaction(it) },
                            onEditTransaction = { editingTransaction = it }
                        )
                    }
                    "chat" -> {
                        AiAssistantTab(
                            chatHistory = chatHistory,
                            aiLoading = aiLoading,
                            onSendMessage = { viewModel.askAi(it) },
                            onClearChat = { viewModel.clearChat() }
                        )
                    }
                }
            }
        }
    }

    // Add Manual Transaction Dialog
    if (showAddTxDialog) {
        AddTxDialog(
            onDismiss = { showAddTxDialog = false },
            onSave = { amt, type, merchant, cat, isUpi, sender, paymentType ->
                viewModel.addTransaction(amt, type, merchant, cat, isUpi, sender, paymentType)
                showAddTxDialog = false
            },
            defaultType = addTxDefaultType
        )
    }

    // Edit Transaction Dialog
    editingTransaction?.let { tx ->
        EditTxDialog(
            transaction = tx,
            onDismiss = { editingTransaction = null },
            onSave = { updated ->
                viewModel.updateTransaction(updated)
                editingTransaction = null
            }
        )
    }

    // Add Goal Dialog
    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onSave = { name, target, initial, deadline ->
                viewModel.addSavingGoal(name, target, initial, deadline)
                showAddGoalDialog = false
            }
        )
    }

    // Add Budget Dialog
    if (showAddBudgetDialog) {
        AddBudgetDialog(
            onDismiss = { showAddBudgetDialog = false },
            onSave = { cat, amt ->
                viewModel.setBudget(cat, amt)
                showAddBudgetDialog = false
            }
        )
    }
}

// --- Common UI Components ---

@Composable
fun HeaderBanner(
    transactions: List<Transaction>,
    onScanClicked: () -> Unit,
    isScanning: Boolean
) {
    val totalDebit = remember(transactions) {
        transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    }
    val totalCredit = remember(transactions) {
        transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    }
    val netBalance = totalCredit - totalDebit

    val format = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val isDark = false

    val headerGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primaryContainer
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                Color(0xFF1E293B) // Premium Dark Slate Navy
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(headerGradient)
            .padding(top = 20.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Household Reserve",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = format.format(netBalance),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("net_balance_text")
                    )
                }

                FilledTonalButton(
                    onClick = onScanClicked,
                    enabled = !isScanning,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading SMS...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Incoming/Credits Indicator
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isDark) FinanceIncomeGreenDark else FinanceIncomeGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Income icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Total Income", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text(
                                format.format(totalCredit),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Outgoing/Debits Indicator
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isDark) FinanceExpenseRedDark else FinanceExpenseRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Spent icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Total Outflow", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text(
                                format.format(totalDebit),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceBottomNavigation(
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    NavigationBar(
        tonalElevation = 6.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = activeTab == "dashboard",
            onClick = { onTabSelect("dashboard") },
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            modifier = Modifier.testTag("tab_dashboard")
        )
        NavigationBarItem(
            selected = activeTab == "transactions",
            onClick = { onTabSelect("transactions") },
            icon = { Icon(Icons.Default.List, contentDescription = "History") },
            label = { Text("Statements") },
            modifier = Modifier.testTag("tab_transactions")
        )
        NavigationBarItem(
            selected = activeTab == "chat",
            onClick = { onTabSelect("chat") },
            icon = { Icon(Icons.Default.Star, contentDescription = "Chat with AI") },
            label = { Text("AI Advisor") },
            modifier = Modifier.testTag("tab_chat")
        )
    }
}

// --- Dashboard Tab ---

@Composable
fun DashboardTab(
    transactions: List<Transaction>,
    budgets: List<Budget>,
    savingGoals: List<SavingGoal>,
    onAddBudgetClick: () -> Unit,
    onDeleteBudgetClick: (String) -> Unit,
    onGoalTopup: (SavingGoal, Double) -> Unit,
    onGoalDelete: (SavingGoal) -> Unit,
    aiAnalysis: String?,
    aiAnalysisLoading: Boolean,
    onRunAnalysis: () -> Unit,
    onAddSpendClick: () -> Unit,
    onAddEarningClick: () -> Unit,
    onAddGoalClick: () -> Unit,
    onLoadMaySampleData: () -> Unit
) {
    val rps = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val isDark = false

    val parsedSpends = remember(transactions) {
        transactions.filter { it.type == "DEBIT" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { it.amount } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Quick Actions Hub
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Reserve Inputs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Manual Spend Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAddSpendClick() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF3B1E1E) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isDark) Color(0xFFEF4444) else Color(0xFFE57373), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Spend Icon",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Add Spend", 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFC62828)
                                )
                            }
                        }

                        // Manual Earning Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAddEarningClick() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF143225) else Color(0xFFE8F5E9)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isDark) Color(0xFF10B981) else Color(0xFF81C784), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Earn Icon",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Add Earning", 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF2E7D32)
                                )
                            }
                        }

                        // Savings Goal Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAddGoalClick() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF382D16) else Color(0xFFFFF8E1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isDark) Color(0xFFF59E0B) else Color(0xFFFFD54F), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Goal Icon",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Add Savings", 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if (isDark) Color(0xFFFDE047) else Color(0xFFF57F17)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onLoadMaySampleData,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "May Sample Data",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Populate May Month Sample Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. AI Suggestions & Financial Analyzer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "AI Analyzer",
                            tint = Color(0xFFE2B022), // Beautiful Gold
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spend & Earning Analyzer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Examine your manual spends or auto-tracked SMS ledger, find savings gaps, and generate customized financial recommendations.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (aiAnalysisLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                "Analyzing earnings, spends, & reserve targets...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (aiAnalysis != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Latest Financial Suggestions:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    text = aiAnalysis,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRunAnalysis,
                        enabled = !aiAnalysisLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Run Analysis icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (aiAnalysis != null) "Re-Analyze My Ledger" else "Run Portfolio Analytics",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Savings Goals Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saving Goals",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${savingGoals.size} Active",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (savingGoals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "No goal set",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No savings goals configured.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Tap the + button to map a new reserve target.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(savingGoals, key = { it.id }) { goal ->
                GoalItemCard(goal, onTopup = { onGoalTopup(goal, it) }, onDelete = { onGoalDelete(goal) })
            }
        }

        // Category Budgets Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category Budgets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onAddBudgetClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add Budget", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set Limit", fontSize = 13.sp)
                }
            }
        }

        if (budgets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No budget limits set",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No budget limits set.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Establish target boundaries to avoid overspending on utilities, dining, or shopping.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(budgets, key = { it.category }) { budget ->
                val spent = parsedSpends[budget.category] ?: 0.0
                BudgetItemCard(
                    budget = budget,
                    spent = spent,
                    onDelete = { onDeleteBudgetClick(budget.category) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun GoalItemCard(
    goal: SavingGoal,
    onTopup: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val rps = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f
    val percentText = "${(progress * 100).toInt()}%"
    val isDark = false

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Goal Target",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = goal.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (goal.deadline.isNotBlank()) {
                            Text(
                                text = "By ${goal.deadline}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Goal",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Reserve Saved", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = rps.format(goal.currentAmount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Target target", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = rps.format(goal.targetAmount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Smooth linear progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (progress >= 1f) {
                    if (isDark) FinanceIncomeGreenDark else FinanceIncomeGreen
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Target: $percentText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 1f) {
                        if (isDark) FinanceIncomeGreenDark else FinanceIncomeGreen
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                // Quick saving increment triggers
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GoalTopUpChip(label = "+ ₹500", onClick = { onTopup(500.0) })
                    GoalTopUpChip(label = "+ ₹2,000", onClick = { onTopup(2000.0) })
                }
            }
        }
    }
}

@Composable
fun GoalTopUpChip(
    label: String,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun BudgetItemCard(
    budget: Budget,
    spent: Double,
    onDelete: () -> Unit
) {
    val rps = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val progress = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
    val ratioText = "${rps.format(spent)} / ${rps.format(budget.limitAmount)}"

    val isOverBudget = spent > budget.limitAmount

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val emoji = when (budget.category) {
                        "Food & Dining" -> "🍔"
                        "Groceries" -> "🛒"
                        "Utilities" -> "💡"
                        "Shopping" -> "🛍️"
                        "Entertainment" -> "🎬"
                        "Transport" -> "🚗"
                        "Investment" -> "📈"
                        "Medical" -> "🏥"
                        else -> "📁"
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = budget.category,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOverBudget) "OVER LIMIT BOUNDARY" else "Within monthly threshold",
                            fontSize = 11.sp,
                            color = if (isOverBudget) MaterialTheme.colorScheme.error else Color(0xFF1B6E4A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Budget",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spending status",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = ratioText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// --- Transactions Tab ---

@Composable
fun TransactionsTab(
    transactions: List<Transaction>,
    onDeleteTransaction: (Transaction) -> Unit,
    onEditTransaction: (Transaction) -> Unit
) {
    val rps = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("All") }
    var selectedFilterPaymentType by remember { mutableStateOf("All") } // "All", "BANK", "CARD"

    val filterCategories = listOf("All", "Food & Dining", "Groceries", "Utilities", "Shopping", "Entertainment", "Transport", "Investment", "Others")

    val processedTransactions = remember(transactions, searchQuery, selectedFilterCategory, selectedFilterPaymentType) {
        transactions.filter {
            val matchesQuery = it.merchant.contains(searchQuery, ignoreCase = true) ||
                    it.senderOrAccount.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedFilterCategory == "All" || it.category == selectedFilterCategory
            val matchesPaymentType = selectedFilterPaymentType == "All" || it.paymentType == selectedFilterPaymentType
            matchesQuery && matchesCategory && matchesPaymentType
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Filter Panel
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search transactions, banks, merchants...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("transaction_search_input"),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Payment type filter badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All Methods" to "All", "Bank Payments" to "BANK", "Card Payments" to "CARD").forEach { (label, value) ->
                val isSelected = selectedFilterPaymentType == value
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilterPaymentType = value },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Horizontal filter tags
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filterCategories.forEach { cat ->
                            val isSelected = selectedFilterCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (processedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No statements found matching search.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Try triggering 'Auto-Track' in the header to import from your SMS statement alerts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("transactions_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(processedTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        tx = tx,
                        onDelete = { onDeleteTransaction(tx) },
                        onEdit = { onEditTransaction(tx) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItemCard(
    tx: Transaction,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val rps = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(tx.timestamp) { sdf.format(Date(tx.timestamp)) }
    val isDark = false

    val emoji = when (tx.category) {
        "Food & Dining" -> "🍔"
        "Groceries" -> "🛒"
        "Utilities" -> "💡"
        "Shopping" -> "🛍️"
        "Entertainment" -> "🎬"
        "Transport" -> "🚗"
        "Investment" -> "📈"
        "Medical" -> "🏥"
        "Salary" -> "💼"
        "Refunds" -> "🎁"
        "Income" -> "💵"
        else -> "📦"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_card_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category emoji circular tag
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (tx.type == "CREDIT") {
                                if (isDark) FinanceIncomeGreenBgDark else FinanceIncomeGreenBg
                            } else {
                                if (isDark) FinanceExpenseRedBgDark else FinanceExpenseRedBg
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.merchant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        if (tx.isUpi) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDark) Color(0xFF132D30) else Color(0xFFE0F7FA))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "UPI",
                                    fontSize = 8.sp,
                                    color = if (isDark) Color(0xFF2DD4BF) else Color(0xFF006064),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Display Card or Bank badge
                        Spacer(modifier = Modifier.width(6.dp))
                        val isCard = tx.paymentType == "CARD"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isCard) {
                                        if (isDark) Color(0xFF332A15) else Color(0xFFFEF3C7)
                                    } else {
                                        if (isDark) Color(0xFF1E2D3D) else Color(0xFFE0F2FE)
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isCard) "CARD" else "BANK",
                                fontSize = 8.sp,
                                color = if (isCard) {
                                    if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
                                } else {
                                    if (isDark) Color(0xFF60A5FA) else Color(0xFF0369A1)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.senderOrAccount,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedDate,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (tx.type == "DEBIT") "-" else "+"} ${rps.format(tx.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (tx.type == "DEBIT") {
                        if (isDark) FinanceExpenseRedDark else FinanceExpenseRed
                    } else {
                        if (isDark) FinanceIncomeGreenDark else FinanceIncomeGreen
                    },
                    modifier = Modifier.testTag("tx_amount_${tx.id}")
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Statement",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Delete Statement",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- AI Assistant Tab ---

@Composable
fun AiAssistantTab(
    chatHistory: List<ChatMessage>,
    aiLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Slide down to newest chat on entry/update
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    val suggestionQuestions = listOf(
        "Summarize my budget details",
        "How can I save Rs 5000 next month?",
        "Where am I overspending raw details?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
    ) {
        // Chat Header Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B6E4A))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "SpendWise Gemini Advisor",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }

            TextButton(onClick = onClearChat) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Advisor Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Conversational Dialogue Container
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatHistory) { msg ->
                val bubbleShape = if (msg.isUser) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                }

                val containerColor = if (msg.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }

                val textColor = if (msg.isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .shadow(if (msg.isUser) 1.dp else 2.dp, shape = bubbleShape)
                            .background(containerColor, shape = bubbleShape)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = msg.message,
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (aiLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                            modifier = Modifier.shadow(2.dp, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Analyzing household reserve statements...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Suggestion prompt tabs
        if (chatHistory.size <= 1 && !aiLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Suggested questions:",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                suggestionQuestions.forEach { prompt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { onSendMessage(prompt) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(prompt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Chat Send Keyboard Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = { Text("Ask Gemini Finance Advisor...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (queryText.isNotBlank()) {
                            onSendMessage(queryText)
                            queryText = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("send_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- POPUP INPUT DIALOGS ---

@Composable
fun AddTxDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String, String, String, Boolean, String, String) -> Unit,
    defaultType: String = "DEBIT"
) {
    var amountValue by remember { mutableStateOf("") }
    var merchantValue by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(defaultType) } // "DEBIT" or "CREDIT"
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var senderValue by remember { mutableStateOf("Cash") }
    var isUpiChecked by remember { mutableStateOf(false) }
    var selectedPaymentType by remember { mutableStateOf("BANK") } // "BANK" or "CARD"

    val categories = listOf("Food & Dining", "Groceries", "Utilities", "Shopping", "Entertainment", "Transport", "Investment", "Others")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Insert Statement Entry",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Debit vs Credit selection trigger
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { transactionType = "DEBIT" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transactionType == "DEBIT") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Expense Out", color = if (transactionType == "DEBIT") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { transactionType = "CREDIT" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transactionType == "CREDIT") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Income In", color = if (transactionType == "CREDIT") Color(0xFF1B6E4A) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Amount Text Field
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = it },
                    label = { Text("Amount (Rs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_tx_amount"),
                    singleLine = true
                )

                // Merchant Text Field
                OutlinedTextField(
                    value = merchantValue,
                    onValueChange = { merchantValue = it },
                    label = { Text("Merchant / Counter-party") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("add_tx_merchant"),
                    singleLine = true
                )

                // Sender / Account Tag
                OutlinedTextField(
                    value = senderValue,
                    onValueChange = { senderValue = it },
                    label = { Text("Source (e.g. UPI, SBI, Wallet)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Card vs Bank selection
                Text("Payment Method Type", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentType = "BANK" }
                    ) {
                        RadioButton(
                            selected = selectedPaymentType == "BANK",
                            onClick = { selectedPaymentType = "BANK" }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bank Payment", fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentType = "CARD" }
                    ) {
                        RadioButton(
                            selected = selectedPaymentType == "CARD",
                            onClick = { selectedPaymentType = "CARD" }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Card Payment", fontSize = 13.sp)
                    }
                }

                // Is UPI Payment Boolean Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("UPI Payment", fontSize = 14.sp)
                    Switch(
                        checked = isUpiChecked,
                        onCheckedChange = { isUpiChecked = it }
                    )
                }

                // Categorization List
                Text("Select Category", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat, fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountValue.toDoubleOrNull()
                            if (amt != null && amt > 0.0 && merchantValue.isNotBlank()) {
                                onSave(amt, transactionType, merchantValue, selectedCategory, isUpiChecked, senderValue, selectedPaymentType)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034C3C))
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun EditTxDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var amountValue by remember { mutableStateOf(transaction.amount.toString()) }
    var merchantValue by remember { mutableStateOf(transaction.merchant) }
    var transactionType by remember { mutableStateOf(transaction.type) } // "DEBIT" or "CREDIT"
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var senderValue by remember { mutableStateOf(transaction.senderOrAccount) }
    var isUpiChecked by remember { mutableStateOf(transaction.isUpi) }
    var selectedPaymentType by remember { mutableStateOf(transaction.paymentType) } // "BANK" or "CARD"

    val categories = listOf("Food & Dining", "Groceries", "Utilities", "Shopping", "Entertainment", "Transport", "Investment", "Others")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Edit Transaction Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Debit vs Credit Selection
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { transactionType = "DEBIT" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transactionType == "DEBIT") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Expense Out", color = if (transactionType == "DEBIT") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { transactionType = "CREDIT" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transactionType == "CREDIT") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Income In", color = if (transactionType == "CREDIT") Color(0xFF1B6E4A) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Merchant/Payment Details Name
                OutlinedTextField(
                    value = merchantValue,
                    onValueChange = { merchantValue = it },
                    label = { Text("Payment/Merchant Name") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("edit_tx_merchant"),
                    singleLine = true
                )

                // Amount Text Field
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = it },
                    label = { Text("Amount (Rs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_tx_amount"),
                    singleLine = true
                )

                // Sender/Source Field
                OutlinedTextField(
                    value = senderValue,
                    onValueChange = { senderValue = it },
                    label = { Text("Account/Source (e.g. UPI, SBI, Visa)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Card vs Bank Payment Method selection
                Text("Payment Method Type", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentType = "BANK" }
                    ) {
                        RadioButton(
                            selected = selectedPaymentType == "BANK",
                            onClick = { selectedPaymentType = "BANK" }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bank Payment", fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentType = "CARD" }
                    ) {
                        RadioButton(
                            selected = selectedPaymentType == "CARD",
                            onClick = { selectedPaymentType = "CARD" }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Card Payment", fontSize = 13.sp)
                    }
                }

                // Is UPI toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("UPI Payment", fontSize = 14.sp)
                    Switch(
                        checked = isUpiChecked,
                        onCheckedChange = { isUpiChecked = it }
                    )
                }

                // Categorization List
                Text("Select Category", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat, fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountValue.toDoubleOrNull()
                            if (amt != null && amt > 0.0 && merchantValue.isNotBlank()) {
                                onSave(
                                    transaction.copy(
                                        amount = amt,
                                        type = transactionType,
                                        merchant = merchantValue,
                                        category = selectedCategory,
                                        senderOrAccount = senderValue,
                                        isUpi = isUpiChecked,
                                        paymentType = selectedPaymentType
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034C3C))
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String) -> Unit
) {
    var goalName by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
    var initialSaved by remember { mutableStateOf("") }
    var deadLineStr by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "New Savings Goal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    label = { Text("Goal Name (e.g. Summer Vacation)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetAmt,
                    onValueChange = { targetAmt = it },
                    label = { Text("Target Amount (Rs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = initialSaved,
                    onValueChange = { initialSaved = it },
                    label = { Text("Initial Saved Amount (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = deadLineStr,
                    onValueChange = { deadLineStr = it },
                    label = { Text("Target Deadline (e.g. Dec 2026)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val target = targetAmt.toDoubleOrNull() ?: 0.0
                            val initial = initialSaved.toDoubleOrNull() ?: 0.0
                            if (goalName.isNotBlank() && target > 0.0) {
                                onSave(goalName, target, initial, deadLineStr)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034C3C))
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Food & Dining") }
    var limitAmt by remember { mutableStateOf("") }

    val categories = listOf("Food & Dining", "Groceries", "Utilities", "Shopping", "Entertainment", "Transport", "Investment", "Others")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Set Monthly Limit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = limitAmt,
                    onValueChange = { limitAmt = it },
                    label = { Text("Amount Limit (Rs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Category Boundaries", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val limit = limitAmt.toDoubleOrNull()
                            if (limit != null && limit > 0.0) {
                                onSave(selectedCategory, limit)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF034C3C))
                    ) {
                        Text("Establish")
                    }
                }
            }
        }
    }
}
