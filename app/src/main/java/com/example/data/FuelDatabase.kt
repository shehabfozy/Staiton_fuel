package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// 1. DATABASE ENTITIES (Materialized Models)
// ==========================================

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val purchasePrice: Double,
    val salePrice: Double
)

@Entity(tableName = "tanks")
data class Tank(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val productId: Int,
    val capacity: Double,
    val currentBalance: Double
)

@Entity(tableName = "pumps")
data class Pump(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pumpNumber: String,
    val productId: Int,
    val tankId: Int,
    val lastReading: Double,
    val isActive: Boolean = true
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val position: String, // Supervisor, Operator, Cashier, Accountant
    val isActive: Boolean = true
)

@Entity(tableName = "shift_sessions")
data class ShiftSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val shiftNumber: String,
    val startedByUserId: Int = 1,
    val closedByUserId: Int? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val status: String, // "Open", "Closed", "Settled", "Locked"
    val employeeId: Int,
    val pumpId: Int,
    val startReading: Double,
    val endReading: Double = 0.0,
    val qtySold: Double = 0.0,
    val unitPrice: Double,
    val totalSalesAmount: Double = 0.0,
    val actualCashReceived: Double = 0.0,
    val expenses: Double = 0.0,
    val variance: Double = 0.0,
    val expectedCash: Double = 0.0,
    val startCustody: Double = 0.0,
    val isSettled: Boolean = false,
    val settledAt: Long? = null,
    val notes: String = ""
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "Sale", "Expense", "Purchase"
    val amount: Double,
    val description: String,
    val timestamp: Long,
    val shiftId: Int? = null
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryNumber: String,
    val date: Long,
    val description: String,
    val debitAccount: String,
    val creditAccount: String,
    val amount: Double,
    val currency: String = "YER",
    val exchangeRate: Double = 1.0,
    val isPosted: Boolean = true
)

@Entity(tableName = "chart_of_accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val type: String, // Asset, Liability, Equity, Revenue, Expense
    val balance: Double
)

// ==========================================
// 2. DATA ACCESS OBJECTS (DAOs)
// ==========================================

@Dao
interface FuelDao {
    // Products
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    // Tanks
    @Query("SELECT * FROM tanks")
    fun getAllTanks(): Flow<List<Tank>>

    @Query("SELECT * FROM tanks WHERE id = :id")
    suspend fun getTankById(id: Int): Tank?

    @Update
    suspend fun updateTank(tank: Tank)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: Tank)

    // Pumps
    @Query("SELECT * FROM pumps")
    fun getAllPumps(): Flow<List<Pump>>

    @Query("SELECT * FROM pumps WHERE id = :id")
    suspend fun getPumpById(id: Int): Pump?

    @Update
    suspend fun updatePump(pump: Pump)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPump(pump: Pump)

    // Employees
    @Query("SELECT * FROM employees")
    fun getAllEmployees(): Flow<List<Employee>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee)

    // Shifts
    @Query("SELECT * FROM shift_sessions ORDER BY id DESC")
    fun getAllShifts(): Flow<List<ShiftSession>>

    @Query("SELECT * FROM shift_sessions WHERE id = :id")
    suspend fun getShiftById(id: Int): ShiftSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftSession): Long

    @Update
    suspend fun updateShift(shift: ShiftSession)

    // Transactions / Expenses
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    // Ledger / Journal Entries
    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(entry: JournalEntry)

    // Accounts
    @Query("SELECT * FROM chart_of_accounts ORDER BY code ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM chart_of_accounts WHERE name = :name")
    suspend fun getAccountByName(name: String): Account?

    @Update
    suspend fun updateAccount(account: Account)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)
}

// ==========================================
// 3. DATABASE CONTAINER HOLDER
// ==========================================

@Database(
    entities = [
        Product::class, Tank::class, Pump::class, Employee::class,
        ShiftSession::class, Transaction::class, JournalEntry::class, Account::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FuelDatabase : RoomDatabase() {
    abstract fun dao(): FuelDao

    companion object {
        @Volatile
        private var INSTANCE: FuelDatabase? = null

        fun getDatabase(context: android.content.Context): FuelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FuelDatabase::class.java,
                    "yemen_fuel_station_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 4. CENTRAL DATA REPOSITORY WITH PREPOPULATION
// ==========================================

class FuelRepository(private val dao: FuelDao) {
    val products: Flow<List<Product>> = dao.getAllProducts()
    val tanks: Flow<List<Tank>> = dao.getAllTanks()
    val pumps: Flow<List<Pump>> = dao.getAllPumps()
    val employees: Flow<List<Employee>> = dao.getAllEmployees()
    val shifts: Flow<List<ShiftSession>> = dao.getAllShifts()
    val transactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val journalEntries: Flow<List<JournalEntry>> = dao.getAllJournalEntries()
    val accounts: Flow<List<Account>> = dao.getAllAccounts()

    suspend fun insertProduct(product: Product) = dao.insertProduct(product)
    suspend fun updateTank(tank: Tank) = dao.updateTank(tank)
    suspend fun getTankById(id: Int) = dao.getTankById(id)
    suspend fun updatePump(pump: Pump) = dao.updatePump(pump)
    suspend fun getPumpById(id: Int) = dao.getPumpById(id)
    suspend fun insertEmployee(employee: Employee) = dao.insertEmployee(employee)
    suspend fun insertShift(shift: ShiftSession) = dao.insertShift(shift)
    suspend fun updateShift(shift: ShiftSession) = dao.updateShift(shift)
    suspend fun getShiftById(id: Int) = dao.getShiftById(id)
    suspend fun insertTransaction(transaction: Transaction) = dao.insertTransaction(transaction)
    suspend fun insertJournal(entry: JournalEntry) = dao.insertJournal(entry)

    suspend fun adjustAccountBalance(accountName: String, amountDelta: Double) {
        val account = dao.getAccountByName(accountName)
        if (account != null) {
            dao.updateAccount(account.copy(balance = account.balance + amountDelta))
        }
    }

    // Triggered on first app load to create default records
    suspend fun checkAndPrepopulate() {
        val count = dao.getPumpById(1)
        if (count == null) {
            // First time initialization! Use Yemen realities (Liters, YER, actual fuel items)

            // 1. Products
            val p1 = Product(1, "بترول ممتاز", 700.0, 950.0) // 950 YER / Liter sale
            val p2 = Product(2, "ديزل", 800.0, 1050.0) // 1050 YER / Liter sale
            dao.insertProduct(p1)
            dao.insertProduct(p2)

            // 2. Tanks
            val t1 = Tank(1, "خزان بترول رئيسي 1", 1, 50000.0, 24350.0)
            val t2 = Tank(2, "خزان بترول احتياطي 2", 1, 30000.0, 12000.0)
            val t3 = Tank(3, "خزان ديزل رئيسي 1", 2, 45000.0, 18700.0)
            dao.insertTank(t1)
            dao.insertTank(t2)
            dao.insertTank(t3)

            // 3. Pumps
            val pm1 = Pump(1, "عداد بترول 1", 1, 1, 154200.0)
            val pm2 = Pump(2, "عداد بترول 2", 1, 1, 89450.0)
            val pm3 = Pump(3, "عداد ديزل 1", 2, 3, 241100.0)
            dao.insertPump(pm1)
            dao.insertPump(pm2)
            dao.insertPump(pm3)

            // 4. Employees
            val emp1 = Employee(1, "أحمد الكبوس", "771234567", "Supervisor")
            val emp2 = Employee(2, "خالد المعلم", "733445566", "Operator")
            val emp3 = Employee(3, "عمر اليافعي", "711223344", "Operator")
            val emp4 = Employee(4, "أبو بكر الصنعاني", "777889900", "Cashier")
            dao.insertEmployee(emp1)
            dao.insertEmployee(emp2)
            dao.insertEmployee(emp3)
            dao.insertEmployee(emp4)

            // 5. Chart of Accounts
            val acs = listOf(
                Account(1, "1101", "الصندوق الرئيسي للمحطة", "Asset", 10500000.0),
                Account(2, "1102", "صندوق المبيعات والعهود", "Asset", 0.0),
                Account(3, "1103", "حساب بنك الكريمي الإسلامي", "Asset", 25000000.0),
                Account(4, "1104", "مخزن البترول الممتاز", "Asset", 23132500.0),
                Account(5, "1105", "مخزن الديزل", "Asset", 19635000.0),
                Account(6, "1106", "أحمد الكبوس - ذمة عهدة", "Asset", 0.0),
                Account(7, "1107", "خالد المعلم - ذمة عهدة", "Asset", 0.0),
                Account(8, "1201", "ذمم العملاء - سحوبات آجلة", "Asset", 4500000.0),
                Account(9, "2101", "شركة النفط اليمنية - مورد دائن", "Liability", 12500000.0),
                Account(10, "3101", "إيرادات مبيعات الوقود", "Revenue", 0.0),
                Account(11, "4101", "تكلفة مبيعات الوقود (COGS)", "Expense", 0.0),
                Account(12, "4201", "مصاريف نقل وتفريغ لوجستية", "Expense", 0.0),
                Account(13, "4202", "مصاريف جمارك وتحسين بلدية", "Expense", 0.0),
                Account(14, "4203", "مصاريف عمومية وإدارية ونثريات", "Expense", 0.0)
            )
            for (ac in acs) {
                dao.insertAccount(ac)
            }

            // 6. Prepopulate some historical shifts & transactions to make charts amazing
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L

            for (i in 5 downTo 1) {
                val sTime = now - i * dayMs
                val eTime = sTime + 8 * 60 * 60 * 1000L // 8 hours shift

                val litersSold = 1200.0 + i * 150.0
                val startRead = 154200.0 - (5-i)*1800 - 1500
                val endRead = startRead + litersSold
                val salesVal = litersSold * 950.0 // Unit price 950

                val expVal = 15000.0 + i * 2000.0
                val recCash = salesVal - expVal - 2000.0 // With 2000 YER variance-deficit

                val label = "SS-2026-06-0${5-i}-W"
                val sh = ShiftSession(
                    id = 0,
                    shiftNumber = label,
                    startedByUserId = 1,
                    closedByUserId = 4,
                    startTime = sTime,
                    endTime = eTime,
                    status = "Locked",
                    employeeId = (i % 3) + 1,
                    pumpId = (i % 2) + 1,
                    startReading = startRead,
                    endReading = endRead,
                    qtySold = litersSold,
                    unitPrice = 950.0,
                    totalSalesAmount = salesVal,
                    actualCashReceived = recCash,
                    expenses = expVal,
                    variance = -2000.0,
                    expectedCash = salesVal - expVal,
                    startCustody = 0.0,
                    isSettled = true,
                    settledAt = eTime + 1000,
                    notes = "تحتوي على تسويات نقدية وعجز طبيعي لمروحة التبخر والحرارة."
                )
                val newShiftId = dao.insertShift(sh).toInt()

                // Add to transactions
                dao.insertTransaction(Transaction(0, "Sale", salesVal, "مبيعات وردية $label", eTime, newShiftId))
                dao.insertTransaction(Transaction(0, "Expense", expVal, "مصروفات مسحوبة بوردية $label", eTime, newShiftId))

                // Log accounts adjustments
                adjustAccountBalance("إيرادات مبيعات الوقود", salesVal)
                adjustAccountBalance("مخزن البترول ممتاز", -litersSold * 700.0) // COGS credit
                adjustAccountBalance("تكلفة مبيعات الوقود (COGS)", litersSold * 700.0) // COGS debit
                adjustAccountBalance("الصندوق الرئيسي للمحطة", recCash) // Money entered Main Box

                // Write a neat journal entry
                dao.insertJournal(
                    JournalEntry(
                        0,
                        "JE-2026-06-0${5-i}-01",
                        eTime,
                        "إغلاق وإخلاء عهدة مناوبة $label تلقائياً لعداد بترول",
                        debitAccount = "الصندوق الرئيسي للمحطة",
                        creditAccount = "إيرادات مبيعات الوقود",
                        amount = salesVal
                    )
                )
            }
        }
    }
}
