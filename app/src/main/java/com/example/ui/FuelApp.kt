package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Global Colors
val primaryBg = Color(0xFF101924)    // Slate Deep Navy
val secondaryCard = Color(0xFF1B2E46)  // Tech Blue Panel
val accentYem = Color(0xFFFF9F1C)     // Warm Amber/Gold
val textLight = Color(0xFFF1F5F9)
val textMuted = Color(0xFF94A3B8)
val greenSuccess = Color(0xFF2EC4B6)

// ==========================================
// VIEW VIEWMODEL FOR UNIFIED LOCAL FLUX
// ==========================================

class FuelViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FuelDatabase.getDatabase(application)
    private val repo = FuelRepository(db.dao())

    val products: StateFlow<List<Product>> = repo.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tanks: StateFlow<List<Tank>> = repo.tanks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pumps: StateFlow<List<Pump>> = repo.pumps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val employees: StateFlow<List<Employee>> = repo.employees.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shifts: StateFlow<List<ShiftSession>> = repo.shifts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions: StateFlow<List<Transaction>> = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val journalEntries: StateFlow<List<JournalEntry>> = repo.journalEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<Account>> = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    var selectedRole by mutableStateOf("Admin") // "Admin" (Manager), "Accountant", "Supervisor", "Cashier", "Auditor"
    var activeTab by mutableStateOf(0) // 12 tabs total

    init {
        viewModelScope.launch {
            repo.checkAndPrepopulate()
        }
    }

    // Role Capabilities Verification
    fun isTabDisabledForRole(tabIndex: Int, role: String): Boolean {
        return when (role) {
            "Supervisor" -> tabIndex in listOf(1, 4, 10, 11) // Cannot see Procurement, Clearance, GL/Accounts, Financials
            "Cashier" -> tabIndex in listOf(1, 5, 10, 11) // Cannot see Procurement, Pump Desk, General Ledger, Financials
            "Accountant" -> tabIndex in listOf(5, 6) // Cannot see physical pump assignment or layout changes
            "Auditor" -> false // Can view everything (No restriction, but can't edit - edit operations will check role)
            else -> false // Manager / Admin has full power
        }
    }

    // Purchase Action
    fun executePurchase(tankId: Int, productId: Int, liters: Double, rawCost: Double, freight: Double, customs: Double, description: String) {
        if (selectedRole == "Auditor" || isTabDisabledForRole(1, selectedRole)) return
        viewModelScope.launch {
            val tank = repo.getTankById(tankId) ?: return@launch
            val totalCost = rawCost + freight + customs
            
            // Adjust Tank Stock
            repo.updateTank(tank.copy(currentBalance = tank.currentBalance + liters))
            
            // Adjust cash ledger (debit tank stock, credit supplier/cash)
            val tankAccount = if (productId == 1) "مخزن البترول الممتاز" else "مخزن الديزل"
            repo.adjustAccountBalance(tankAccount, totalCost)
            repo.adjustAccountBalance("الصندوق الرئيسي للمحطة", -totalCost)

            // Log Transaction
            repo.insertTransaction(Transaction(0, "Purchase", totalCost, "مشتريات $liters لتر: $description", System.currentTimeMillis()))
            
            // Auto Journal Entry
            repo.insertJournal(JournalEntry(
                id = 0,
                entryNumber = "JE-PUR-${System.currentTimeMillis() % 10000}",
                date = System.currentTimeMillis(),
                description = "توريد $liters لتر وقود خزان ${tank.name}",
                debitAccount = tankAccount,
                creditAccount = "الصندوق الرئيسي للمحطة",
                amount = totalCost
            ))
        }
    }

    // Open Shift Action
    fun openShift(employeeId: Int, pumpId: Int, unitPrice: Double, startCustody: Double) {
        if (selectedRole == "Auditor") return
        viewModelScope.launch {
            val pump = repo.getPumpById(pumpId) ?: return@launch
            val lastNozzle = pump.lastReading
            val shiftNum = "SS-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-${(10..99).random()}"
            
            val newShift = ShiftSession(
                id = 0,
                shiftNumber = shiftNum,
                startTime = System.currentTimeMillis(),
                status = "Open",
                employeeId = employeeId,
                pumpId = pumpId,
                startReading = lastNozzle,
                unitPrice = unitPrice,
                startCustody = startCustody
            )
            repo.insertShift(newShift)
        }
    }

    // Close Shift Action
    fun closeShift(id: Int, endReading: Double, actualCash: Double, expenses: Double, notes: String) {
        if (selectedRole == "Auditor") return
        viewModelScope.launch {
            val shift = repo.getShiftById(id) ?: return@launch
            val pump = repo.getPumpById(shift.pumpId) ?: return@launch
            
            if (endReading < shift.startReading) return@launch // Invalid nozzle entries
            
            val litersSold = endReading - shift.startReading
            val totalSales = litersSold * shift.unitPrice
            val expectedCash = totalSales - expenses
            val variance = actualCash - expectedCash

            val closedShift = shift.copy(
                status = "Closed",
                endTime = System.currentTimeMillis(),
                endReading = endReading,
                qtySold = litersSold,
                totalSalesAmount = totalSales,
                actualCashReceived = actualCash,
                expenses = expenses,
                expectedCash = expectedCash,
                variance = variance,
                notes = notes
            )
            
            repo.updateShift(closedShift)
            
            // Adjust physical pump registry nozzle count
            repo.updatePump(pump.copy(lastReading = endReading))
            
            // Adjust Tank Stock
            val tank = repo.getTankById(pump.tankId)
            if (tank != null) {
                val newBal = (tank.currentBalance - litersSold).coerceAtLeast(0.0)
                repo.updateTank(tank.copy(currentBalance = newBal))
            }
        }
    }

    // Settle/Clear Shift Custody Finalization (Cashier / Accountant locks shift)
    fun settleShift(shiftId: Int) {
        if (selectedRole == "Auditor" || selectedRole == "Supervisor") return
        viewModelScope.launch {
            val shift = repo.getShiftById(shiftId) ?: return@launch
            if (shift.status != "Closed") return@launch
            
            // 1. Mark status as Locked (No further editing allowed)
            val lockedShift = shift.copy(status = "Locked", isSettled = true, settledAt = System.currentTimeMillis())
            repo.updateShift(lockedShift)

            // 2. Clear Operator's Temporary Account, Transfer to Main Safe
            val worker = repo.employees.stateIn(viewModelScope).value.find { it.id == shift.employeeId }?.name ?: "العامل"
            repo.adjustAccountBalance("الصندوق الرئيسي للمحطة", shift.actualCashReceived)
            repo.adjustAccountBalance("إيرادات مبيعات الوقود", shift.totalSalesAmount)
            
            if (shift.expenses > 0) {
                repo.adjustAccountBalance("مصاريف عمومية وإدارية ونثريات", shift.expenses)
            }
            
            val fuelType = if (shift.pumpId == 3) "مخزن الديزل" else "مخزن البترول ممتاز"
            val costL_ref = if (shift.pumpId == 3) 800.0 else 700.0
            repo.adjustAccountBalance(fuelType, -(shift.qtySold * costL_ref))
            repo.adjustAccountBalance("تكلفة مبيعات الوقود (COGS)", shift.qtySold * costL_ref)

            // Insert Accounting Journal Entries
            repo.insertJournal(JournalEntry(
                id = 0,
                entryNumber = "JE-SET-${shift.id}",
                date = System.currentTimeMillis(),
                description = "تصفية مبيعات مناوبة ${shift.shiftNumber} - العامل $worker",
                debitAccount = "الصندوق الرئيسي للمحطة",
                creditAccount = "إيرادات مبيعات الوقود",
                amount = shift.totalSalesAmount
            ))
            
            repo.insertTransaction(Transaction(0, "Sale", shift.totalSalesAmount, "تسوية مبيعات وردية ${shift.shiftNumber}", System.currentTimeMillis(), shift.id))
        }
    }

    // Direct Quick Manual Sales (POS, Bank, Credit or Cash)
    fun makeManualSale(customerId: Int?, type: String, amount: Double, pumpId: Int, qtyLiters: Double) {
        if (selectedRole == "Auditor") return
        viewModelScope.launch {
            val label = "مبيعات صالة فورية ($type)"
            repo.insertTransaction(Transaction(0, "Sale", amount, "$label: بقيمة $amount ر.ي", System.currentTimeMillis()))
            
            // Accounting entry
            val debitAcc = when (type) {
                "Credit" -> "ذمم العملاء - سحوبات آجلة"
                "Bank" -> "حساب بنك الكريمي الإسلامي"
                else -> "الصندوق الرئيسي للمحطة" // Cash
            }
            
            repo.adjustAccountBalance(debitAcc, amount)
            repo.adjustAccountBalance("إيرادات مبيعات الوقود", amount)

            repo.insertJournal(JournalEntry(
                id = 0,
                entryNumber = "JE-SAL-${System.currentTimeMillis() % 10000}",
                date = System.currentTimeMillis(),
                description = "بيع وقود مباشر بنمط $type",
                debitAccount = debitAcc,
                creditAccount = "إيرادات مبيعات الوقود",
                amount = amount
            ))
        }
    }

    // Direct Expense Registration
    fun makeExpense(amount: Double, description: String, sourceAccount: String) {
        if (selectedRole == "Auditor" || isTabDisabledForRole(9, selectedRole)) return
        viewModelScope.launch {
            repo.insertTransaction(Transaction(0, "Expense", amount, "مصروف تشغيلي: $description", System.currentTimeMillis()))
            
            // Adjust Ledger accounts
            repo.adjustAccountBalance(sourceAccount, -amount)
            repo.adjustAccountBalance("مصاريف عمومية وإدارية ونثريات", amount)

            repo.insertJournal(JournalEntry(
                id = 0,
                entryNumber = "JE-EXP-${System.currentTimeMillis() % 10000}",
                date = System.currentTimeMillis(),
                description = "صرف نقدية ومصاريف: $description",
                debitAccount = "مصاريف عمومية وإدارية ونثريات",
                creditAccount = sourceAccount,
                amount = amount
            ))
        }
    }
}

// Format Numbers in Yemen Riyals (YER)
fun formatYem(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    formatter.currency = Currency.getInstance("YER")
    return formatter.format(amount).replace("YER", "ر.ي").replace("$", "ر.ي")
}

// Convert timestamp to human date
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
    return sdf.format(Date(timestamp))
}

// ==========================================
// CENTRAL COMPOSE INTERFACE ENTRY POINT
// ==========================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FuelApp(viewModel: FuelViewModel = viewModel()) {
    val localContext = LocalContext.current
    val products by viewModel.products.collectAsState()
    val tanks by viewModel.tanks.collectAsState()
    val pumps by viewModel.pumps.collectAsState()
    val employees by viewModel.employees.collectAsState()
    val shifts by viewModel.shifts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val journalEntries by viewModel.journalEntries.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    // Ensure Arabic texts formatting
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = accentYem, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("نظام محاسبية وإدارة محروقات اليمن", color = textLight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    actions = {
                        // Role Picker Panel
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("الوظيفة النشطة: ", color = textMuted, fontSize = 13.sp)
                            var expandedRole by remember { mutableStateOf(false) }
                            Box {
                                Button(
                                    onClick = { expandedRole = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = secondaryCard),
                                    modifier = Modifier.testTag("role_switcher_button")
                                ) {
                                    Text(
                                        text = when (viewModel.selectedRole) {
                                            "Admin" -> "المدير العام 👑"
                                            "Accountant" -> "المحاسب المالي 💼"
                                            "Supervisor" -> "مشرف الصالة ⛽"
                                            "Cashier" -> "أمين الصندوق 💰"
                                            "Auditor" -> "المراقب المالي 📑"
                                            else -> "غير معروف"
                                        },
                                        color = accentYem,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedRole,
                                    onDismissRequest = { expandedRole = false },
                                    modifier = Modifier.background(secondaryCard)
                                ) {
                                    listOf("Admin", "Accountant", "Supervisor", "Cashier", "Auditor").forEach { role ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = when (role) {
                                                        "Admin" -> "المدير العام (صلاحيات كاملة) 👑"
                                                        "Accountant" -> "المحاسب (الأمور المالية والحسابات) 💼"
                                                        "Supervisor" -> "مشرف المحطة (المضخات والورديات) ⛽"
                                                        "Cashier" -> "أمين الصندوق (القبض والقرطاسية) 💰"
                                                        "Auditor" -> "مراقب خارجي (عرض دون تعديل) 📑"
                                                        else -> ""
                                                    }, color = textLight
                                                )
                                            },
                                            onClick = {
                                                viewModel.selectedRole = role
                                                expandedRole = false
                                                Toast.makeText(localContext, "تم تبديل الدور إلى ${role}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryBg)
                )
            },
            containerColor = primaryBg
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Adaptive Lateral Navigation (Web style desktop LAN layout)
                NavigationSidebar(
                    selectedTab = viewModel.activeTab,
                    onTabSelected = { tab ->
                        if (viewModel.isTabDisabledForRole(tab, viewModel.selectedRole)) {
                            Toast.makeText(localContext, "🚫 ليس لديك صلاحية للدخول إلى هذا القسم!", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.activeTab = tab
                        }
                    },
                    viewModel = viewModel,
                    activeRole = viewModel.selectedRole
                )

                // Central Active Working Area
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(primaryBg)
                        .padding(16.dp)
                ) {
                    when (viewModel.activeTab) {
                        0 -> DashboardTab(tanks, pumps, shifts, accounts, transactions)
                        1 -> ProcurementTab(tanks, products, onPurchase = { tk, pr, lt, raw, fr, cs, desc ->
                            viewModel.executePurchase(tk, pr, lt, raw, fr, cs, desc)
                            Toast.makeText(localContext, "✅ تم تسجيل الفاتورة وتوريد $lt لتر للمخزن بنجاح!", Toast.LENGTH_SHORT).show()
                        }, viewModel = viewModel)
                        2 -> SalesTab(pumps, employees, onManualSale = { cust, type, amt, pump, liters ->
                            viewModel.makeManualSale(cust, type, amt, pump, liters)
                            Toast.makeText(localContext, "💰 تم تقييد مبيعات الصالة بنجاح!", Toast.LENGTH_SHORT).show()
                        }, viewModel = viewModel)
                        3 -> ShiftSessionsTab(shifts, employees, pumps, onOpenShift = { emp, pmp, prc, cst ->
                            viewModel.openShift(emp, pmp, prc, cst)
                            Toast.makeText(localContext, "🚀 تم بدء المناوبة الوردية بنجاح!", Toast.LENGTH_SHORT).show()
                        }, onCloseShift = { id, endNum, actualCash, exp, note ->
                            viewModel.closeShift(id, endNum, actualCash, exp, note)
                            Toast.makeText(localContext, "🏁 تم إرسال إغلاق المناوبة العدادات، بانتظار التسوية المالية!", Toast.LENGTH_SHORT).show()
                        }, viewModel = viewModel)
                        4 -> ClearanceTab(shifts, employees, onSettle = { id ->
                            viewModel.settleShift(id)
                            Toast.makeText(localContext, "🔒 تم توريد عهدة الوردية وأفلت بشكل نهائي!", Toast.LENGTH_SHORT).show()
                        }, viewModel = viewModel)
                        5 -> PumpsTab(pumps, employees, tanks)
                        6 -> TanksTab(tanks, products)
                        7 -> CustomersTab()
                        8 -> SuppliersTab()
                        9 -> ExpensesTab(accounts, onAddExpense = { amt, desc, src ->
                            viewModel.makeExpense(amt, desc, src)
                            Toast.makeText(localContext, "✔ تم تسجيل وتقييد المصروف اليومي!", Toast.LENGTH_SHORT).show()
                        }, viewModel = viewModel)
                        10 -> GLLedgerTab(accounts, journalEntries)
                        11 -> ReportsTab(transactions, shifts, accounts, journalEntries)
                    }
                }
            }
        }
    }
}

// ==========================================
// SYSTEM UI NAVIGATION SIDEBAR COMPONENT
// ==========================================

@Composable
fun NavigationSidebar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    viewModel: FuelViewModel,
    activeRole: String
) {
    val tabsList = listOf(
        Pair("الرئيسية المراقبة", Icons.Filled.Dashboard),
        Pair("مشتريات وتفريغ", Icons.Filled.CloudDownload),
        Pair("مبيعات مباشرة", Icons.Filled.PointOfSale),
        Pair("وردية ومناوبات", Icons.Filled.HourglassEmpty),
        Pair("إخلاء عهد وتسويات", Icons.Filled.Payments),
        Pair("المضخات والخرطوم", Icons.Filled.Power),
        Pair("عيارات الخزانات", Icons.Filled.DynamicFeed),
        Pair("العملاء والشركات", Icons.Filled.SupervisorAccount),
        Pair("الموردون والشحن", Icons.Filled.ContactPage),
        Pair("المصروفات النثرية", Icons.Filled.PriceCheck),
        Pair("المحاسبة العامة COA", Icons.Filled.Receipt),
        Pair("القوائم والتقارير المالية", Icons.Filled.Assessment)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(Color(0xFF0F172A)) // Dark Sidebar
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "القائمة التشغيلية",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        tabsList.forEachIndexed { index, pair ->
            val isDisabled = viewModel.isTabDisabledForRole(index, activeRole)
            val isSelected = selectedTab == index

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp, horizontal = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> Color(0xFFFF9F1C).copy(alpha = 0.15f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(enabled = !isDisabled) { onTabSelected(index) }
                    .padding(12.dp)
                    .testTag("nav_item_${index}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = pair.second,
                    contentDescription = null,
                    tint = when {
                        isDisabled -> Color(0xFF334155)
                        isSelected -> Color(0xFFFF9F1C)
                        else -> Color(0xFF94A3B8)
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = pair.first,
                    color = when {
                        isDisabled -> Color(0xFF334155)
                        isSelected -> Color(0xFFFF9F1C)
                        else -> Color(0xFFE2E8F0)
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD COMPONENT (الرئيسية)
// ==========================================

@Composable
fun DashboardTab(
    tanks: List<Tank>,
    pumps: List<Pump>,
    shifts: List<ShiftSession>,
    accounts: List<Account>,
    transactions: List<Transaction>
) {
    val totalCashValue = accounts.find { it.name == "الصندوق الرئيسي للمحطة" }?.balance ?: 0.0
    val bankValue = accounts.find { it.name == "حساب بنك الكريمي الإسلامي" }?.balance ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("لوحة قيادة محطة المحروقات", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text("مراقبة المخزون، السيولة، والورديات اللحظية لليمن", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }

        // Highlights Metrics
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("صندوق النقدية (الرئيسي)", formatYem(totalCashValue), Icons.Filled.AccountBalanceWallet, Color(0xFF2EC4B6), modifier = Modifier.weight(1f))
                MetricCard("رصيد البنك (الكريمي)", formatYem(bankValue), Icons.Filled.AccountBalance, Color(0xFFFF9F1C), modifier = Modifier.weight(1f))
                MetricCard("المناوبات الجارية", shifts.filter { it.status == "Open" }.size.toString() + " ورديات جارية", Icons.Filled.Timelapse, Color(0xFFE71D36), modifier = Modifier.weight(1f))
            }
        }

        // Fuel Tank visual levels
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("عيارات ومخزون خزانات المحطة اللحظي", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        tanks.take(3).forEach { tank ->
                            val pct = (tank.currentBalance / tank.capacity).toFloat()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                                    Canvas(modifier = Modifier.size(100.dp)) {
                                        drawArc(
                                            color = Color(0xFF334155),
                                            startAngle = 135f,
                                            sweepAngle = 270f,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = if (pct < 0.2f) Color(0xFFE71D36) else Color(0xFFFF9F1C),
                                            startAngle = 135f,
                                            sweepAngle = 270f * pct,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${(pct * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("ممتلئ", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(tank.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${tank.currentBalance.toInt()} / ${tank.capacity.toInt()} لتر", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }

        // Active shift or recent log
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("آخر المناوبات وقفل العدادات", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (shifts.isEmpty()) {
                        Text("لا يوجد ورديات مسجلة بعد", color = Color(0xFF94A3B8), modifier = Modifier.padding(8.dp))
                    } else {
                        shifts.take(3).forEach { sh ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("كود المناوبة: ${sh.shiftNumber}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("البداية: ${formatDate(sh.startTime)}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (sh.status) {
                                            "Open" -> "قيد العمل ⏳"
                                            "Closed" -> "مغلق/بانتظار المراجعة"
                                            "Locked" -> "مسوى ومغلق بقفل 🔒"
                                            else -> sh.status
                                        },
                                        fontSize = 11.sp,
                                        color = if (sh.status == "Open") Color(0xFFFF9F1C) else Color(0xFF2EC4B6),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(formatYem(sh.totalSalesAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            HorizontalDivider(color = Color(0xFF334155))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, valStr: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(valStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ==========================================
// 2. PROCUREMENT MODULE (مشتريات وتفريغ)
// ==========================================

@Composable
fun ProcurementTab(
    tanks: List<Tank>,
    products: List<Product>,
    onPurchase: (Int, Int, Double, Double, Double, Double, String) -> Unit,
    viewModel: FuelViewModel
) {
    if (viewModel.selectedRole == "Supervisor" || viewModel.selectedRole == "Cashier") {
        PermissionDeniedScreen()
        return
    }

    var selectedTankId by remember { mutableStateOf(tanks.firstOrNull()?.id ?: 1) }
    var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: 1) }
    var rawLiters by remember { mutableStateOf("15000") }
    var baseCost by remember { mutableStateOf("10500000") } // YER
    var freightCost by remember { mutableStateOf("50000") }
    var customsTotal by remember { mutableStateOf("30000") }
    var description by remember { mutableStateOf("شحنة تفريغ وقود من شركة النفط") }

    val totalCost = (baseCost.toDoubleOrNull() ?: 0.0) + (freightCost.toDoubleOrNull() ?: 0.0) + (customsTotal.toDoubleOrNull() ?: 0.0)
    val perLiter = if ((rawLiters.toDoubleOrNull() ?: 0.0) > 0.0) totalCost / (rawLiters.toDoubleOrNull() ?: 1.0) else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("شاشة توريد وشراء الوقود السائل (القواطر)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("تسجيل الفواتير وتوزيع التكاليف لإقامة السعر المرجعي الفعلي للمخزون", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Tank selection
                    Text("اختر الخزان لتوجيه الوقود المفرغ:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tanks.forEach { tk ->
                            FilterChip(
                                selected = selectedTankId == tk.id,
                                onClick = { selectedTankId = tk.id },
                                label = { Text(tk.name) }
                            )
                        }
                    }

                    // Product selection
                    Text("اختر المادة السائلة:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.forEach { pr ->
                            FilterChip(
                                selected = selectedProductId == pr.id,
                                onClick = { selectedProductId = pr.id; selectedTankId = if (pr.id == 1) 1 else 3 },
                                label = { Text(pr.name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rawLiters,
                        onValueChange = { rawLiters = it },
                        label = { Text("الكمية الفعلية المستلمة (اللتر)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proc_liters_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = baseCost,
                        onValueChange = { baseCost = it },
                        label = { Text("القيمة الأساسية للشراء (ريال يمني)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = freightCost,
                        onValueChange = { freightCost = it },
                        label = { Text("أجور نقل وشحن قطار النقل للبرميل/القاطرة") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = customsTotal,
                        onValueChange = { customsTotal = it },
                        label = { Text("رسوم جمارك وتحسين منافذ بلدية الوردية") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("ملاحظات تفصيلية") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    // Summary Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("إجمالي تكلفة الشحنة بالوصول:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(formatYem(totalCost), color = Color(0xFFFF9F1C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("سعر تكلفة اللتر الواحد الواصل:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("${perLiter.toInt()} ريال يمني / لتر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val lt = rawLiters.toDoubleOrNull() ?: 0.0
                            val cost = baseCost.toDoubleOrNull() ?: 0.0
                            val fr = freightCost.toDoubleOrNull() ?: 0.0
                            val cust = customsTotal.toDoubleOrNull() ?: 0.0
                            if (lt > 0 && cost > 0) {
                                onPurchase(selectedTankId, selectedProductId, lt, cost, fr, cust, description)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("procurement_submit_button")
                    ) {
                        Text("اعتماد قيد التوريد والتنزيل للخزان دفترياً ومحاسبياً ✔", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. SALES MODULE (مبيعات مباشرة)
// ==========================================

@Composable
fun SalesTab(
    pumps: List<Pump>,
    employees: List<Employee>,
    onManualSale: (Int?, String, Double, Int, Double) -> Unit,
    viewModel: FuelViewModel
) {
    var amountText by remember { mutableStateOf("15000") }
    var qtyLitersText by remember { mutableStateOf("15.7") }
    var saleType by remember { mutableStateOf("Cash") } // Cash, Credit, Bank
    var selectedPumpId by remember { mutableStateOf(pumps.firstOrNull()?.id ?: 1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("شاشة الصالة والمبيعات الفورية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("تسجيل سحوبات الآجل للشركات المتعاقدة أو تسديدات التحويل الإلكتروني الفوري في اليمن برقم المرجع", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("طريقة السحب المالي:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Pair("Cash", "دفع فوري نقدي 💵"),
                            Pair("Credit", "مبيعات آجل شركات 📑"),
                            Pair("Bank", "تحويل مالي (الكريمي) 📱")
                        ).forEach { t ->
                            FilterChip(
                                selected = saleType == t.first,
                                onClick = { saleType = t.first },
                                label = { Text(t.second) }
                            )
                        }
                    }

                    Text("حدد الطرمبة لخصم المستودع التلقائي:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pumps.forEach { p ->
                            FilterChip(
                                selected = selectedPumpId == p.id,
                                onClick = { selectedPumpId = p.id },
                                label = { Text(p.pumpNumber) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ الإجمالي بالريال اليمني") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = qtyLitersText,
                        onValueChange = { qtyLitersText = it },
                        label = { Text("الكمية التقديرية باللتر") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            val lit = qtyLitersText.toDoubleOrNull() ?: 0.0
                            if (amt > 0 && lit > 0) {
                                onManualSale(null, saleType, amt, selectedPumpId, lit)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("تسجيل في يومية المبيعات والترحيل الفوري 💰", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. SHIFT MANAGEMENT MODULE (تسيير الوردية)
// ==========================================

@Composable
fun ShiftSessionsTab(
    shifts: List<ShiftSession>,
    employees: List<Employee>,
    pumps: List<Pump>,
    onOpenShift: (Int, Int, Double, Double) -> Unit,
    onCloseShift: (Int, Double, Double, Double, String) -> Unit,
    viewModel: FuelViewModel
) {
    val localContext = LocalContext.current
    var selectedEmployeeId by remember { mutableStateOf(employees.firstOrNull()?.id ?: 1) }
    var selectedPumpId by remember { mutableStateOf(pumps.firstOrNull()?.id ?: 1) }
    var unitPrice by remember { mutableStateOf("950") } // YER price
    var startCustody by remember { mutableStateOf("0") }

    // Close screen variables
    var endNozzleText by remember { mutableStateOf("") }
    var actualCashText by remember { mutableStateOf("") }
    var expenseShiftText by remember { mutableStateOf("0") }
    var closeNotesText by remember { mutableStateOf("تم إقفال الدورة وقيد المصاريف للوردية.") }

    val activeShift = shifts.find { it.status == "Open" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("نظام إدارة ومكافحة عجز المناوبات (Shift Wizard)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("فتح ووردية العمل، استلام العداد ومطابقة السيولة النقدية مع العجز والزيادة بالتسعير الحالي", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        if (activeShift == null) {
            // Screen state to OPEN a new shift
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🚀 فتح وردية عمل جديدة لتسجيل الصرافة:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)

                        Text("حدد العامل المسؤول على الخرطوم:", color = Color.White, fontSize = 12.sp)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            employees.forEach { emp ->
                                FilterChip(
                                    selected = selectedEmployeeId == emp.id,
                                    onClick = { selectedEmployeeId = emp.id },
                                    label = { Text(emp.name) }
                                )
                            }
                        }

                        Text("حدد المضخة/العداد:", color = Color.White, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pumps.forEach { p ->
                                FilterChip(
                                    selected = selectedPumpId == p.id,
                                    onClick = { selectedPumpId = p.id; unitPrice = if (p.productId == 1) "950" else "1050" },
                                    label = { Text(p.pumpNumber) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = unitPrice,
                            onValueChange = { unitPrice = it },
                            label = { Text("سعر بيع البترول/الديزل الفعلي للتر الوردية") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        OutlinedTextField(
                            value = startCustody,
                            onValueChange = { startCustody = it },
                            label = { Text("عهدة البداية النقدية (الفكة)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        Button(
                            onClick = {
                                onOpenShift(
                                    selectedEmployeeId,
                                    selectedPumpId,
                                    unitPrice.toDoubleOrNull() ?: 950.0,
                                    startCustody.toDoubleOrNull() ?: 0.0
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_shift_submit")
                        ) {
                            Text("البدء وسحب قراءة العداد الأخير في الخادم 🏁", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        } else {
            // Open shift exists, enable CLOSE screen
            item {
                val workerName = employees.find { it.id == activeShift.employeeId }?.name ?: "غير معروف"
                val pumpNo = pumps.find { it.id == activeShift.pumpId }?.pumpNumber ?: "خرطوم"

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("⏳ الوردية الجارية النشطة حالياً:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("الحالة: جارية بالبث المباشر", color = Color(0xFFFF9F1C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Column {
                                Text("اسم مسؤول الوردية: $workerName", color = Color.White, fontSize = 13.sp)
                                Text("رقم المضخة الملتزم بها: $pumpNo", color = Color.White, fontSize = 13.sp)
                                Text("قراءة عداد الابتداء للخرطوم: ${activeShift.startReading} لتر", color = textMuted, fontSize = 13.sp)
                                Text("مستوى سعر الصرف المعاير: ${activeShift.unitPrice} ريال/لتر", color = textMuted, fontSize = 12.sp)
                            }
                        }

                        Text("إجراءات تصفية العدادات والإغلاق:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                        OutlinedTextField(
                            value = endNozzleText,
                            onValueChange = { endNozzleText = it },
                            label = { Text("أدخل قراءة عداد النهاية (مأخوذة من الطرمبة)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("close_end_reading_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        OutlinedTextField(
                            value = actualCashText,
                            onValueChange = { actualCashText = it },
                            label = { Text("النقد المستلم الفعلي (في الدرج)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("close_actual_cash_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        OutlinedTextField(
                            value = expenseShiftText,
                            onValueChange = { expenseShiftText = it },
                            label = { Text("المصروفات النثرية التي صُرِفت بموقع الوردية") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        OutlinedTextField(
                            value = closeNotesText,
                            onValueChange = { closeNotesText = it },
                            label = { Text("تفسيرات إضافية لأي فروقات") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                        )

                        Button(
                            onClick = {
                                val endRead = endNozzleText.toDoubleOrNull() ?: 0.0
                                val actCash = actualCashText.toDoubleOrNull() ?: 0.0
                                val expVal = expenseShiftText.toDoubleOrNull() ?: 0.0
                                if (endRead >= activeShift.startReading) {
                                    onCloseShift(activeShift.id, endRead, actCash, expVal, closeNotesText)
                                } else {
                                    Toast.makeText(localContext, "🚫 قراءة العداد النهائية غير صالحة ولا يمكن أن تكون أقل من قراءة الابتداء!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE71D36)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("close_shift_submit")
                        ) {
                            Text("قفل العداد وحفظ الوردية بنجاح ✔", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // List shift logs
        item {
            Text("سجلات وورديات المحطة المسجلة (التاريخية)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        items(shifts) { sh ->
            val wrk = employees.find { it.id == sh.employeeId }?.name ?: "العامل"
            val pmp = pumps.find { it.id == sh.pumpId }?.pumpNumber ?: "خرطوم"

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("مناوبة #: ${sh.shiftNumber}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text(
                            text = when (sh.status) {
                                "Open" -> "قيد العمل ⏳"
                                "Closed" -> "مغلقة/بانتظار الإخلاء 📑"
                                "Locked" -> "مسلمة ومقفلة نهائياً 🔒"
                                else -> sh.status
                            },
                            fontSize = 11.sp,
                            color = if (sh.status == "Open") Color(0xFFFF9F1C) else Color(0xFF2EC4B6),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("مسؤول الوردية: $wrk | الماكينة: $pmp", fontSize = 12.sp, color = textMuted)
                    
                    if (sh.status != "Open") {
                        Text("الكمية المباعة: ${sh.qtySold} لتر | المبيعات الدفترية: ${formatYem(sh.totalSalesAmount)}", fontSize = 12.sp, color = textLight)
                        Text(
                            text = "العجز أو الزيادة: " + if (sh.variance < 0) "عجز ${formatYem(sh.variance)}" else "زيادة ${formatYem(sh.variance)}",
                            fontSize = 12.sp,
                            color = if (sh.variance < 0) Color(0xFFE71D36) else Color(0xFF2EC4B6),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. CLEARANCE MODULE (تخليص عهود العمال)
// ==========================================

@Composable
fun ClearanceTab(
    shifts: List<ShiftSession>,
    employees: List<Employee>,
    onSettle: (Int) -> Unit,
    viewModel: FuelViewModel
) {
    if (viewModel.selectedRole == "Supervisor" || viewModel.selectedRole == "Cashier") {
        PermissionDeniedScreen()
        return
    }

    val closedShifts = shifts.filter { it.status == "Closed" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("قسم تدقيق وتصفية المستحقات المالية (إخلاء العهد)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("خاص بأمناء الخزن والمحاسبين: توريد المبالغ النقدية للصندوق المالي الرئيسي وإصدار إشعار القيد التلقائي للوردية", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        if (closedShifts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد مناوبات مغلقة وجاهزة لتسوية النقدية حالياً لليوم.", color = textMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(closedShifts) { sh ->
                val worker = employees.find { it.id == sh.employeeId }?.name ?: "العامل"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("طلب إخلاء وتوريد عهد الوردية رقم: ${sh.shiftNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("اسم العامل بالصالة:", color = textMuted, fontSize = 12.sp)
                                    Text(worker, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("مبيعات اللترات المحسوبة فورا:", color = textMuted, fontSize = 12.sp)
                                    Text("${sh.qtySold} لتر", color = Color.White, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("المبيعات الصافية المطلوبة دفترياً:", color = textMuted, fontSize = 12.sp)
                                    Text(formatYem(sh.totalSalesAmount), color = Color.White, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("النقدية المحسوبة بالمطابقة:", color = textMuted, fontSize = 12.sp)
                                    Text(formatYem(sh.actualCashReceived), color = Color.White, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("عجـز أو فائـض الوردية للموظف:", color = textMuted, fontSize = 12.sp)
                                    Text(
                                        text = (if (sh.variance < 0) "عجز " else "زيادة ") + formatYem(sh.variance),
                                        color = if (sh.variance < 0) Color(0xFFE71D36) else Color(0xFF2EC4B6),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onSettle(sh.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EC4B6)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clearance_settle_button")
                        ) {
                            Text("توليد سند توريد الخزينة وقفل عهد العامل نهائياً 🔒", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. PUMPS PANEL VIEW (عدادات الوردية)
// ==========================================

@Composable
fun PumpsTab(
    pumps: List<Pump>,
    employees: List<Employee>,
    tanks: List<Tank>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("شاشة تهيئة ومراقبة العدادات والمضخات مادياً", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("تعيين الماكينات وربط العدادات التراكمية بالخزانات المحددة", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        items(pumps) { pump ->
            val tankName = tanks.find { it.id == pump.tankId }?.name ?: "خزان خارجي"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pump.pumpNumber, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (pump.productId == 1) Color(0xFFFF9F1C).copy(alpha = 0.2f) else Color(0xFF2EC4B6).copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (pump.productId == 1) "بترول" else "ديزل",
                                    fontSize = 10.sp,
                                    color = if (pump.productId == 1) Color(0xFFFF9F1C) else Color(0xFF2EC4B6),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الخزان المرتبط المغذي: $tankName", fontSize = 12.sp, color = textMuted)
                        Text("القراءة التراكمية الحالية للخرطوم: ${pump.lastReading} لتر", fontSize = 12.sp, color = Color.White)
                    }

                    Icon(
                        Icons.Filled.LocalGasStation,
                        contentDescription = null,
                        tint = if (pump.productId == 1) Color(0xFFFF9F1C) else Color(0xFF2EC4B6),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 7. TANKS PANEL VIEW (الخزانات)
// ==========================================

@Composable
fun TanksTab(tanks: List<Tank>, products: List<Product>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("مستودعات وخزانات المحروقات السائلة بالمحطة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("حساب وتعديل كميات المخزون لمراقبة العيار والتبخر الطبيعي", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        items(tanks) { tank ->
            val pName = products.find { it.id == tank.productId }?.name ?: "مادة"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(tank.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("مكثف الوقود: $pName", fontSize = 11.sp, color = textMuted)
                        }
                        Text("${((tank.currentBalance / tank.capacity) * 100).toInt()}% ممتلئ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F1C))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated bar
                    LinearProgressIndicator(
                        progress = (tank.currentBalance / tank.capacity).toFloat(),
                        color = Color(0xFFFF9F1C),
                        trackColor = Color(0xFF334155),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("الرصيد الدفتري: ${tank.currentBalance.toInt()} لتر", fontSize = 12.sp, color = Color.White)
                        Text("السعة المادية: ${tank.capacity.toInt()} لتر", fontSize = 12.sp, color = textMuted)
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. CUSTOMERS PANEL (العملاء المتفقين)
// ==========================================

@Composable
fun CustomersTab() {
    val custs = listOf(
        Triple("مؤسسة هائل سعيد التجارية سحوبات", "5500,000 ر.ي", "حد مسموح: 8 مليون ريال"),
        Triple("آليات الأشغال العامة الحكومية", "1310,000 ر.ي", "حد مسموح: 4 مليون ريال"),
        Triple("مجموعة باعبيد للمقاولات والطرق", "0 ر.ي", "حد مسموح: 3 مليون ريال")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("ذمم العملاء وسحوبات الشركات التجارية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("إدارة المبيعات الآجلة برقم السحوبات وحد السقف الائتماني المسموح به باليمن", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        items(custs) { (name, bal, limit) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(limit, color = textMuted, fontSize = 11.sp)
                    }
                    Text(bal, color = Color(0xFFFF9F1C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// 9. SUPPLIERS PANEL (شركات التوريد الشريكة)
// ==========================================

@Composable
fun SuppliersTab() {
    val suppliers = listOf(
        Pair("شركة النفط اليمنية - فرع صنعاء", "رصيد دائن مستحق: 12500,000 ر.ي"),
        Pair("مؤسسة باهديل للاستيراد النفطي", "رصيد دائن مستحق: 0 ر.ي")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("موردي ناقلات الوقود السائل", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("ربط مستحقات المصانع والشراء بالتوريد والتوزيع الدفتري", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        items(suppliers) { (name, credit) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text("الريال اليمني", color = textMuted, fontSize = 11.sp)
                    }
                    Text(credit, color = Color(0xFFE71D36), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// 10. EXPENSES BOOK (دفتر الصروفات)
// ==========================================

@Composable
fun ExpensesTab(
    accounts: List<Account>,
    onAddExpense: (Double, String, String) -> Unit,
    viewModel: FuelViewModel
) {
    if (viewModel.selectedRole == "Supervisor" || viewModel.selectedRole == "Auditor") {
        PermissionDeniedScreen()
        return
    }

    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("صيانة دورية لطرمبة ومسدس الماكينة") }
    var selectedSource by remember { mutableStateOf("الصندوق الرئيسي للمحطة") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("مصروفات ونثريات المحطة اليومية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("قيد المصروفات المتنوعية (رواتب، كهرباء صيانة طرمبات، فاقد عيار)", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("صيغة سحب النقدية والتسوية المحاسبية من:", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("الصندوق الرئيسي للمحطة", "حساب بنك الكريمي الإسلامي").forEach { src ->
                            FilterChip(
                                selected = selectedSource == src,
                                onClick = { selectedSource = src },
                                label = { Text(src) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("المبلغ التقديري بالريال اليمني (YER)") },
                        modifier = Modifier.fillMaxWidth().testTag("expense_amt_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("تفاصيل وتبريرات الصرف") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Color(0xFFFF9F1C), focusedBorderColor = Color(0xFFFF9F1C))
                    )

                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onAddExpense(amt, note, selectedSource)
                                amount = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("expense_submit_button")
                    ) {
                        Text("قيد وصرف مستند النثرية اليوم ✔", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                }
            }
        }
    }
}

// ==========================================
// 11. GENERAL LEDGER COA VIEW (المحاسبة والدفاتر)
// ==========================================

@Composable
fun GLLedgerTab(accounts: List<Account>, journalEntries: List<JournalEntry>) {
    var viewJournalStyle by remember { mutableStateOf(false) } // false = COA, true = Journal entries

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (viewJournalStyle) "القيود اليومية التلقائية المركبة" else "دليل شجرة الحسابات (COA)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = { viewJournalStyle = !viewJournalStyle },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2E46))
                ) {
                    Text(if (viewJournalStyle) "عرض شجرة الحسابات" else "عرض قيود اليومية المركبة", color = Color(0xFFFF9F1C))
                }
            }
        }

        if (!viewJournalStyle) {
            // Render COA with current balances
            items(accounts) { ac ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(ac.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("كود الحساب: ${ac.code} | صنف: ${ac.type}", color = textMuted, fontSize = 11.sp)
                        }
                        Text(formatYem(ac.balance), color = if (ac.balance >= 0) Color(0xFF2EC4B6) else Color(0xFFE71D36), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Render journal entries
            items(journalEntries) { je ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("قيد اليومية #: ${je.entryNumber}", fontWeight = FontWeight.Bold, color = Color(0xFFFF9F1C), fontSize = 12.sp)
                            Text(formatDate(je.date), color = textMuted, fontSize = 10.sp)
                        }
                        Text(je.description, fontSize = 12.sp, color = Color.White)
                        
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(4.dp)).padding(8.dp)) {
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("من (مدين +): ${je.debitAccount}", color = Color(0xFF2EC4B6), fontSize = 11.sp)
                                    Text(formatYem(je.amount), color = Color(0xFF2EC4B6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("إلى (دائن -): ${je.creditAccount}", color = Color.White, fontSize = 11.sp)
                                    Text(formatYem(je.amount), color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 12. REPORTS PAGE (التقارير والمخرجات المالية)
// ==========================================

@Composable
fun ReportsTab(
    transactions: List<Transaction>,
    shifts: List<ShiftSession>,
    accounts: List<Account>,
    journalEntries: List<JournalEntry>
) {
    var showPrintReceipt by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("المخرجات والتقارير المالية والتحليلية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(
                    onClick = { showPrintReceipt = !showPrintReceipt },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C))
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, tint = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showPrintReceipt) "إخفاء لوحة الطباعة" else "معاينة الميزانية والطباعة 📑", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showPrintReceipt) {
            // Custom printer emulation box mock
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .border(1.dp, Color.Black)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "جمهورية اليمن\nمحطة التضامن النموذجية للمحروقات\nصنعاء - شارع الستين",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(color = Color.Black)
                        
                        Text(
                            text = "ميزان المراجعة والأرصدة الختامية لليوم " + SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        accounts.forEach { ac ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(ac.name, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(formatYem(ac.balance), color = Color.Black, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = Color.Black)
                        
                        // Fake profit statement
                        val revSales = accounts.find { it.code == "3101" }?.balance ?: 0.0
                        val cogs = accounts.find { it.code == "4101" }?.balance ?: 0.0
                        val exp = accounts.find { it.code == "4203" }?.balance ?: 0.0
                        val netProf = revSales - cogs - exp

                        Text(
                            text = "تقرير استخلاص الربحية (صافي الأرباح): " + formatYem(netProf),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        
                        Text(
                            text = "نقل محلي وآمن - ترحيل آلي بنسبة نجاح 100٪",
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            // General grid statistics items
            val totalRevenuesVal = accounts.find { it.code == "3101" }?.balance ?: 0.0
            val totalCostsVal = accounts.find { it.code == "4101" }?.balance ?: 0.0

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("إيرادات المبيعات المحققة", formatYem(totalRevenuesVal), Icons.Filled.AddCard, Color(0xFF2EC4B6), modifier = Modifier.weight(1f))
                    MetricCard("تكلفة المشتريات المفرغة", formatYem(totalCostsVal), Icons.Filled.Output, Color(0xFFFF9F1C), modifier = Modifier.weight(1f))
                    MetricCard("توازن الخزائن العام", formatYem(totalRevenuesVal - totalCostsVal), Icons.Filled.LockClock, Color(0xFFE71D36), modifier = Modifier.weight(1f))
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E46)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ملخص العجز ومكاسب عمال الورديات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val deficitsSum = shifts.filter { it.variance < 0 }.sumOf { it.variance }
                        val surplusSum = shifts.filter { it.variance > 0 }.sumOf { it.variance }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي غرامات العجز المالي للعمال (مدين):", color = textMuted, fontSize = 13.sp)
                            Text(formatYem(deficitsSum), color = Color(0xFFE71D36), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي فوائض مبيعات المناوبات (دائن الأرباح):", color = textMuted, fontSize = 13.sp)
                            Text(formatYem(surplusSum), color = Color(0xFF2EC4B6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// FALLBACK SCREEN - ACCESS RIGHTS REJECTION
// ==========================================

@Composable
fun PermissionDeniedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101924)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFE71D36), modifier = Modifier.size(56.dp))
            Text("المحتوى مغلق لأسباب أمنية الصلاحيات 🔒", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("ليست لديك الأذونات الكافية لتنفيذ هذا الإجراء المالي أو استعراض الشاشة.", fontSize = 12.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
        }
    }
}
