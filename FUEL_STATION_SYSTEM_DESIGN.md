# وثيقة التحليل البرمجي والتصميم المحاسبي المتكامل لنظام إدارة ومحاسبة محطات الوقود (اليمن)

هذه الوثيقة تمثل التصميم والتحليل الهيكلي الشامل لنظام إدارة ومحاسبة محطة وقود تعمل في البيئة اليمنية (تدعم الريال اليمني وتعدد أسعار الصرف للعملات الأجنبية كالسعودي والدولار)، مع تصميم هيكلي لقاعدة البيانات (PostgreSQL) وبنية برمجية باستخدام (Laravel 12 + Flutter Web).

---

## أولاً: تحليل النظام (System Analysis)

### 1. دراسة دورة العمل الكاملة (Operational Workflow)
تتكون دورة العمل في محطة الوقود من أربع حلقات مترابطة:
1. **دورة التوريد والشراء واللوجستيات:**
   - الاتفاق مع المورد (شركة النفط اليمنية أو الموردين التجاريين).
   - شراء الشحنة (بترول/ديزل) مع تحديد السعر بالريال اليمني أو العملات الأجنبية.
   - عمليات النقل (أجور القواطر)، والرسوم الرسمية (الجمارك، تحسين المدينة، رسوم النقاط والأمن).
   - تفريغ الشحنة في الخزانات المحددة وتوثيق الفاقد الطبيعي أثناء النقل.
2. **إدارة الخزانات والمخزون:**
   - قياس كمية الوقود في الخزانات (يدوياً بالمسطرة أو إلكترونياً عبر حساسات ATG).
   - تحديث المخزون الفعلي بناءً على عملية التفريغ وحركة المبيعات بالمضخات.
3. **دورة المبيعات والمضخات والمناوبات (الوردية):**
   - فتح المناوبة وتسليم العدادات للعمال المسؤولين عن المضخات.
   - تفويض المضخات للعمال وتسجيل قراءات عدادات البداية.
   - البيع المستمر (نقدي، آجل للشركات والجهات الحكومية، تحويلات بنكية، نقاط بيع).
   - إغلاق المناوبة وتسجيل قراءات عدادات النهاية وحساب الكمية ومقارنتها بالنقدية المستلمة.
4. **تسوية العهد (Clearance) وقفل المناوبة:**
   - احتساب العجز أو الزيادة لكل عامل.
   - توريد النقدية الفعلية إلى خزينة المحطة (الرئيسية) أو إيداعها مباشرة في حسابات البنوك.
   - قفل المناوبة نهائياً وتوليد القيود المحاسبية التلقائية.

### 2. تحليل العمليات التشغيلية (Operational Operations)
- **مراقبة الخزانات:** تتبع مستمر لمسوى الوقود. المعادلة الحاكمة:
  `الرصيد الحالي = الرصيد السابق + الوارد (المفرّغ) - المباع (مجموع قراءات العدادات)`
- **إدارة المضخات:** ربط كل مضخة بخزان محدد ومنتج واحد (بترول أو ديزل).
- **إدارة الورديات (Shifts):** دعم تشغيل المحطة 24 ساعة مقسمة على ورديتين أو ثلاث، مع مرونة تامة في نقل العامل من مضخة لأخرى.

### 3. تحليل العمليات المحاسبية (Accounting Workflow)
عند تفعيل النظام، يتم توليد القيود تلقائياً بناءً على الأحداث التشغيلية كالتالي:
- **عند شراء الوقود وتفريغه:**
  - القيد التلقائي لثمن الوقود والمصاريف:
    - *من حـ/ مخزن وقود (بترول/ديزل) (القيمة الإجمالية شاملة الشراء والنقل والرسوم)*
    - *إلى حـ/ المورد (شركة النفط أو مورد تجاري)*
    - *وإلى حـ/ الصندوق أو البنك (أجور النقل والرسوم إن دفعت نقداً)*
- **عند بيع الوقود وإغلاق المناوبة:**
  - يتم حساب القيمة الإجمالية للمبيعات: `الكمية المباعة × سعر البيع للتر`
  - القيد التلقائي لإغلاق الوردية وتوريد المستحقات:
    - *من مذكورين:*
      - *حـ/ عهدة العامل (النقدية الفعلية المسلمة)*
      - *حـ/ العملاء الآجلين (البيعات الآجلة الموثقة برقم العميل)*
      - *حـ/ البنك / شركة الصرافة (المبيعات بالتحويل أو نقاط البيع)*
      - *حـ/ مصاريف تشغيلية (المصروفات التي دفعها العامل من المبيعات)*
      - *حـ/ عجز عهد العمال (إن وجد عجز مالي محمل على العامل)*
    - *إلى مذكورين:*
      - *حـ/ مبيعات الوقود (بترول/ديزل)*
      - *حـ/ زيادة عهد العمال (إن وجدت زيادة نقدية غير مفسرة)*
- **عند ترحيل وإخلاء العهدة:**
  - *من حـ/ الصندوق الرئيسي (الخزينة)*
  - *إلى حـ/ عهدة العامل (إغلاق عهدة العامل وتصفيرها)*

### 4. تحديد الكيانات والعلاقات (Entities & Relationships)
- **المستخدمون والوظائف:** علاقة متعدد لمتعدد (Many-to-Many) بين المستخدمين والوظائف والصلاحيات.
- **الخزانات والمضخات:** خزان واحد يغذي مضخة أو أكثر (One-to-Many). ومضخة واحدة ترتبط بخزان واحد.
- **العامل والمضخة:** علاقة تعيين متغيرة عبر جدول الوسيط `pump_assignments` لتتبع التغييرات التاريخية.
- **المناوبة والعمليات:** المناوبة الواحدة `shift_sessions` تحتوي على عمليات مبيعات، مصروفات، وحركة مخزنية متعددة.

### 5. صلاحيات المستخدمين (Role-Based Access Control)
1. **المدير العام (Admin):** صلاحيات مطلقة وتعديل البيانات المقفلة والمناوبات وحذف القيود.
2. **المحاسب (Accountant):** إدارة الدليل المحاسبي، القيود، لمساندة العمليات المالية، والتقارير المالية دون إمكانية تعديل إعدادات المضخات المادية.
3. **مسؤول المحطة (Station Supervisor):** فتح وإغلاق المناوبات، جرد الخزانات، قراءة العدادات، وتعديل تعيينات العمال في المضخات.
4. **أمين الصندوق (Cashier):** استلام النقدية المحولة من عهد العمال، عمل سندات القبض والصرف.
5. **المراقب (Auditor/Observer):** الاطلاع على شاشات الجرد والقراءات والتقارير دون تعديل أو إضافة.

---

## ثانياً: تصميم قاعدة البيانات (Database Schema - Postgres SQL)

مخطط الجداول يوضح البنية المتكاملة لقاعدة البيانات مع القيود والمفاتيح الأجنبية والمؤشرات (Indexes).

```sql
-- 1. جدول الأدوار (roles)
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. جدول الصلاحيات (permissions)
CREATE TABLE permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    module VARCHAR(50) NOT NULL, -- Core, Accounting, Inventory, Pumps, etc.
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. جدول الربط بين الأدوار والصلاحيات (role_permissions)
CREATE TABLE role_permissions (
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INTEGER REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 4. جدول الموظفين (employees)
CREATE TABLE employees (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    identity_card VARCHAR(50),
    position VARCHAR(50) NOT NULL, -- Operator, Cashier, Admin, etc.
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. جدول المستخدمين (users)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    employee_id INTEGER REFERENCES employees(id) ON DELETE SET NULL,
    role_id INTEGER REFERENCES roles(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. جدول المنتجات (products)
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL, -- بترول ممتاز، ديزل
    unit VARCHAR(20) DEFAULT 'Liter',
    purchase_price_ref NUMERIC(15, 2) NOT NULL, -- السعر المرجعي للشراء
    sale_price_ref NUMERIC(15, 2) NOT NULL, -- السعر المرجعي للبيع الحالي
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. جدول خزانات الوقود (tanks)
CREATE TABLE tanks (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL, -- خزان بترول رقم 1
    product_id INTEGER NOT NULL REFERENCES products(id),
    capacity NUMERIC(15, 2) NOT NULL, -- السعة القصوى باللتر
    minimum_limit NUMERIC(15, 2) NOT NULL DEFAULT 500.00, -- الحد الأدنى للتنبيه
    current_balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- الرصيد الفعلي الحالي للوقود
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balance_capacity CHECK (current_balance <= capacity)
);

-- 8. جدول مضخات الوقود (pumps)
CREATE TABLE pumps (
    id SERIAL PRIMARY KEY,
    pump_number VARCHAR(10) UNIQUE NOT NULL, -- مضخة غاطس رقم 1
    product_id INTEGER NOT NULL REFERENCES products(id),
    tank_id INTEGER NOT NULL REFERENCES tanks(id),
    last_reading NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- آخر قراءة تراكمية للعداد
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. جدول تعيين العمال التاريخي للمضخات (pump_assignments)
CREATE TABLE pump_assignments (
    id SERIAL PRIMARY KEY,
    pump_id INTEGER NOT NULL REFERENCES pumps(id) ON DELETE CASCADE,
    employee_id INTEGER NOT NULL REFERENCES employees(id),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    is_current BOOLEAN DEFAULT TRUE
);

-- 10. جدول الموردين (suppliers)
CREATE TABLE suppliers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    address TEXT,
    account_id INTEGER UNIQUE, -- ارتباط دليل الحسابات
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. جدول العملاء (customers)
CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    customer_type VARCHAR(20) DEFAULT 'Cash', -- Cash, Credit, Corporate
    credit_limit NUMERIC(15, 2) DEFAULT 0.00,
    account_id INTEGER UNIQUE, -- ارتباط دليل الحسابات
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 12. جدول الصناديق والخواص المالية (cashboxes)
CREATE TABLE cashboxes (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL, -- صندوق المحطة الرئيسي، صندوق الوردية
    account_id INTEGER UNIQUE, -- ارتباط دليل الحسابات
    balance NUMERIC(15, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. جدول الحسابات البنكية ومحفظة النقد (banks)
CREATE TABLE banks (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL, -- بنك التضامن، بنك كريمي، إلخ
    account_number VARCHAR(50),
    account_id INTEGER UNIQUE, -- ارتباط دليل الحسابات
    balance NUMERIC(15, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14. جدول جرويات المناوبات والورديات (shift_sessions)
CREATE TABLE shift_sessions (
    id SERIAL PRIMARY KEY,
    shift_number VARCHAR(20) UNIQUE NOT NULL, -- SS-20260606-01
    started_by_user_id INTEGER NOT NULL REFERENCES users(id),
    closed_by_user_id INTEGER REFERENCES users(id),
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'Open', -- Open, Closed, Settled, Locked
    notes TEXT
);

-- 15. جدول تفاصيل قراءة العدادات لكل مضخة داخل المناوبة (shift_pump_readings)
CREATE TABLE shift_pump_readings (
    id SERIAL PRIMARY KEY,
    shift_session_id INTEGER NOT NULL REFERENCES shift_sessions(id) ON DELETE CASCADE,
    pump_id INTEGER NOT NULL REFERENCES pumps(id),
    employee_id INTEGER NOT NULL REFERENCES employees(id),
    start_reading NUMERIC(15, 2) NOT NULL, -- قراءة البداية
    end_reading NUMERIC(15, 2), -- قراءة النهاية (تعبـأ عند الإغلاق)
    qty_sold NUMERIC(15, 2) GENERATED ALWAYS AS (end_reading - start_reading) STORED, -- الكمية المباعة تلقائياً
    unit_price NUMERIC(15, 2) NOT NULL, -- سعر اللتر في هذه المناوبة
    total_sales_amount NUMERIC(15, 2) GENERATED ALWAYS AS ((end_reading - start_reading) * unit_price) STORED, -- قيمة المبيعات المستهدفة
    actual_cash_received NUMERIC(15, 2) DEFAULT 0.00, -- النقدية الفعلية المسلمة من العامل
    expenses NUMERIC(15, 2) DEFAULT 0.00, -- مصروفات تشغيلية خصمها العامل بالموقع بطلب مسبق
    variance NUMERIC(15, 2) DEFAULT 0.00, -- العجز المالي (سالب) أو الزيادة (موجب)
    is_settled BOOLEAN DEFAULT FALSE,
    settled_at TIMESTAMP
);

-- 16. جدول المشتريات والتوريدات (purchases)
CREATE TABLE purchases (
    id SERIAL PRIMARY KEY,
    invoice_no VARCHAR(50) UNIQUE NOT NULL,
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    purchase_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal_cost NUMERIC(15, 2) NOT NULL, -- قيمة الوقود الخام
    transport_fees NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- أجور النقل
    custom_fees NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- رسوم جمارك ومكاتب التحسين
    other_expenses NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- مصاريف أخرى
    total_cost NUMERIC(15, 2) NOT NULL, -- التكلفة الكلية الموزعة للمخزون
    is_draft BOOLEAN DEFAULT TRUE,
    created_by INTEGER REFERENCES users(id)
);

-- 17. تفاصيل أصناف الشراء والتفريغ (purchase_items)
CREATE TABLE purchase_items (
    id SERIAL PRIMARY KEY,
    purchase_id INTEGER NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(id),
    tank_id INTEGER NOT NULL REFERENCES tanks(id), -- الخزان المستهدف للتفريغ
    qty_liters NUMERIC(15, 2) NOT NULL, -- الكمية المشتراة باللتر
    price_per_liter NUMERIC(15, 2) NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL
);

-- 18. جدول المبيعات المباشرة وغير المباشرة (sales)
CREATE TABLE sales (
    id SERIAL PRIMARY KEY,
    invoice_no VARCHAR(50) UNIQUE NOT NULL,
    shift_session_id INTEGER NOT NULL REFERENCES shift_sessions(id) ON DELETE CASCADE,
    customer_id INTEGER REFERENCES customers(id), -- نقدياً أو آجلاً
    sale_type VARCHAR(20) NOT NULL, -- Cash, Credit, BankTransfer, POS
    payment_destination_id INTEGER, -- معرف الصندوق أو البنك المستلم
    sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(15, 2) NOT NULL,
    created_by INTEGER REFERENCES users(id)
);

-- 19. تفاصيل مبيعات الوقود (sale_items)
CREATE TABLE sale_items (
    id SERIAL PRIMARY KEY,
    sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(id),
    qty_liters NUMERIC(15, 2) NOT NULL,
    price_per_liter NUMERIC(15, 2) NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL
);

-- 20. جدول المصروفات التشغيلية والنثرية (expenses)
CREATE TABLE expenses (
    id SERIAL PRIMARY KEY,
    expense_no VARCHAR(50) UNIQUE NOT NULL,
    shift_session_id INTEGER REFERENCES shift_sessions(id) ON DELETE SET NULL, -- للربط بالمناوبات
    expense_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details TEXT NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    account_id INTEGER NOT NULL, -- الحساب التفصيلي المخصوم بالدليبل المصاريف
    payment_source VARCHAR(20) NOT NULL, -- Cashbox, Bank, OperatorCustody
    payment_source_id INTEGER, -- معرف الصندوق أو البنك الدائن
    created_by INTEGER REFERENCES users(id)
);

-- 21. جدول حركة المخزون التاريخية (inventory_movements)
CREATE TABLE inventory_movements (
    id SERIAL PRIMARY KEY,
    tank_id INTEGER NOT NULL REFERENCES tanks(id),
    movement_type VARCHAR(20) NOT NULL, -- Purchase, Sale, Adjustment, Loss
    qty_liters NUMERIC(15, 2) NOT NULL, -- بالموجب للزيادة والسالب للنقصان
    reference_id INTEGER, -- ID الشراء أو فواتير المبيعات
    reference_type VARCHAR(50), -- 'purchase', 'sale_shift', 'adjustment'
    vessel_balance NUMERIC(15, 2) NOT NULL, -- الرصيد المتبقي بالخزان بعد الحركة مباشرة
    movement_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 22. جدول اليومية العامة المحاسبية (journal_entries)
CREATE TABLE journal_entries (
    id SERIAL PRIMARY KEY,
    entry_number VARCHAR(50) UNIQUE NOT NULL, -- JE-20260606-001
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    description TEXT,
    reference_id INTEGER, -- ID العملية المنشئة (شراء، تسوية وردية، إلخ)
    reference_type VARCHAR(50), 
    is_posted BOOLEAN DEFAULT FALSE, -- هل تم ترحيلها بشكل نهائي للأستاذ العام
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 23. تفاصيل قيد اليومية العامة - الجوانب المدينة والدائنة (journal_details)
CREATE TABLE journal_details (
    id SERIAL PRIMARY KEY,
    journal_entry_id INTEGER NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL, -- رقم الحساب المتأثر من دليل الحسابات
    debit NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- الجنب المدين (+)
    credit NUMERIC(15, 2) NOT NULL DEFAULT 0.00, -- الجنب الدائن (-)
    currency VARCHAR(10) DEFAULT 'YER', -- العملة المستخدمة
    exchange_rate NUMERIC(10, 4) DEFAULT 1.0000, -- سعر الصرف للريال اليمني
    notes TEXT,
    CONSTRAINT chk_debit_credit_double CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0))
);

-- 24. جدول دليل الحسابات (chart_of_accounts)
CREATE TABLE chart_of_accounts (
    id SERIAL PRIMARY KEY,
    account_code VARCHAR(30) UNIQUE NOT NULL, -- رمز الحساب مثال: 1101001
    name_ar VARCHAR(150) NOT NULL, -- اسم الحساب بالعربية
    name_en VARCHAR(150),
    parent_id INTEGER REFERENCES chart_of_accounts(id), -- الحساب الرئيسي الأب
    account_type VARCHAR(50) NOT NULL, -- Asset, Liability, Equity, Revenue, Expense
    is_sub_account BOOLEAN DEFAULT TRUE, -- هل هو حساب فرعي يقبل القيود
    balance NUMERIC(15, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 25. جدول سجلات التدقيق والمراقبة (audit_logs)
CREATE TABLE audit_logs (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL, -- LOGIN, UPDATE_PUMP, INSERT_SALE, LOCK_SHIFT
    affected_table VARCHAR(50) NOT NULL,
    record_id INTEGER,
    previous_values TEXT, -- بصيغة JSON
    new_values TEXT, -- بصيغة JSON
    ip_address VARCHAR(45),
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 26. إعدادات النظام وتحديث الأسعار (settings)
CREATE TABLE settings (
    id SERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- إنشاء الفهارس لرفع الأداء وسرعة الاستعلام (Performance Indexes)
CREATE INDEX idx_tanks_opt ON tanks(product_id);
CREATE INDEX idx_pumps_opt ON pumps(pump_number, is_active);
CREATE INDEX idx_journal_details_acc ON journal_details(account_id);
CREATE INDEX idx_shift_sessions_status ON shift_sessions(status);
CREATE INDEX idx_inventory_movements_tank ON inventory_movements(tank_id, movement_time);
```

---

## ثالثاً: إدارة المخزون والخزانات (Tank Management)

تعتبر الخزانات المورد الحقيقي لمحطة الوقود. يدعم النظام إدارة التفاصيل التالية:
1. **تسجيل الأرصدة الافتتاحية:** تعبئة السعة الكلية والأرصدة الموجودة عند تفعيل النظام.
2. **بروتوكول تفريغ الشحنات (التوريد):** عند تفريغ كميات جديدة، يقوم النظام بتعديل مخزون الخزان الموجه إليه من جدول المشتريات.
3. **الجرد الفعلي المستمر وعمل تصفية فاقد مخزني:**
   تسجيل قياس مستوى الخزان بالمتر والمسطرة لمقارنة المخزون الدفتري والمخزون الفعلي.
   - *معادلة المخزون النقية:*
     `الرصيد الفعلي الحالي = الرصيد الافتتاحي + مجموع كميات الوارد المفرغة - مجموع الكميات المباعة بالمضخات المرتبطة`
   - *معادلة الفاقد المخزني الدفتري الفعلي:*
     `الفاقد / الزيادة = الجرد الفعلي بالمسطرة - الرصيد الدفتري الحالي`
     إذا كان الفاقد سالباً وضمن الحدود المسموح بها (أقل من 0.5% بموجب قوانين شركة النفط اليمنية نتيجة التمدد الحراري والتبخر)، يسجل كمصروف فاقد طبيعي. إذا تجاوز يتم ترحيله للتحقيق.

---

## رابعاً: إدارة المضخات (Pump Management)

يتحكم النظام بالمضخات ويربط كل مضخة بخزان مادي، لضمان سحب دقيق:
1. **التعيين التلقائي للمضخات وعمال الورديات:**
   - لكل مضخة مسار مبيعات ومسؤول (عامل مضخة) معين في وردية محددة.
   - يتيح النظام لمسؤول المحطة تغيير العامل المسؤول على نفس المضخة في منتصف المناوبة (مثال: انتهاء فترة عمل العامل أو ظروف صحية)، حيث يتم أخذ قراءة مصفقة للعداد وحفظها للجدولة التاريخية في `pump_assignments`.
2. **السجل التاريخي لليوميات والمضخات:**
   - يوثق جدول `pump_assignments` تاريخ ووقت تولي العامل للمضخة وتاريخ تسليمها وعداد البدء والانتهاء لتوزيع المسؤولية المالية بشكل دقيق، مما يضمن منع التداخل أو التهرب من تبرير العجز المالي.

---

## خامساً: إدارة المناوبات والورديات (Shift Management)

تعتبر شاشة إدارة الورديات والمناوبات الشاشة الحيوية والأهم في دورة حياة النظام كالتالي:

```
[فتح المناوبة] ────> [مبيعات مستمرة] ────> [إغلاق المناوبة والعدادات] ────> [احتساب الفروقات والعجز] ────> [إخلاء وتسوية العهدة وقفل المناوبة بالصندوق العام]
```

### 1. بروتوكول فتح المناوبة
- يبحث النظام تلقائياً عن آخر قراءة تراكمية مسجلة لكل مضخة في قاعدة البيانات (في جدول `pumps`).
- يتثبت من هوية العمال القائمين وتخصيص عهدة مالية نقدية بدائية معهم إن وجد كعهدة بداية (مثلا صرافة فكة).
- يتم تغيير حالة المناوبة في قاعدة البيانات إلى `Open`.

### 2. بروتوكول إغلاق المناوبة
عند نهاية اليوم أو فترة الوردية، يقوم مسؤول المحطة بالتالي:
- تسجيل **قراءات عدادات المضخات الحالية** (النهاية).
- النظام يحسب آلياً:
  `الكمية المباعة (اللتر) = قراءة العداد النهائية - قراءة العداد الابتدائية`
- يتم ضرب الكمية بسعر اللتر المحدد للمناوبة لحساب **المبيعات المتوقعة**:
  `القيمة الإجمالية للمبيعات المفترضة (YER) = الكمية المباعة × سعر البيع الفعلي`
- تسجيل المصروفات التي قام العامل بدفعها من درج المبيعات (مثل: شراء ماء، أجر تنظيف، تبرع للبلدية) مشروحة بوثائق رسمية.
- تسجيل الفاصل المالي الفعلي (النقد المسلم، والتحويلات للعملاء الآجلين، وعملاء الدفع الإلكتروني والبطائق).
- **الاحتساب المالي للعجز والزيادة:**
  `النقدية المتوقعة = عهدة البداية (الفكة) + المبيعات الإجمالية - المصروفات المعتمدة - المبيعات الآجلة - مبيعات التحويلات`
  `العجز أو الزيادة = النقدية الفعلية المسلمة من العامل - النقدية المتوقعة`
  - إذا كان الناتج سالباً: يسجل كعجز عهدة مالي على العامل ويدرج في الحساب المدين لعهد العامل.
  - إذا كان الناتج موجباً: يسجل كزيادة عهد غير مفسرة ويودع في حساب الأرباح الأخرى تحت بند تسويات مبيعات المحطة.

---

## سادساً: إخلاء العهد وتوريد الصندوق الرئيسي

بعد قفل المناوبة وحساب العجز والزيادة من مسؤول المحطة، تأتي مرحلة **إخلاء العهدة** عن طريق المحاسب أو مدير المالية:
1. يقوم النظام بتوليد **تقرير عهد المناوبة** لإقرار البيانات.
2. يدخل أمين الصندوق الرئيسي لتوليد **سند توريد عهد الصندوق** برقم الوردية لإثبات دخول الأموال فعلياً إلى التجويف المالي للشركة.
3. بمجرد ضغط "إخلاء وقفل نهائي"، تتغير حالة المناوبة إلى `Locked`.
4. **الحماية الصارمة من التعديل:** يتم تجميد جميع سجلات المناوبة ولا يمكن تعديلها أو حذفها نهائياً منعاً للتلاعب في القراءات وتجفيف العجز الدفتري، إلا بترخيص خطي وصلاحيات استثنائية من المدير العام.

---

## سابعاً: المبيعات ونوافذ تفويض الوقود

يدعم النظام قنوات توزيع مختلفة لمرونة التطبيق في البيئة اليمنية:
1. **البيع النقدي (Cash Sales):** يتم الدفع المباشر للعامل ومطابقتها بالدرج.
2. **البيع الآجل (Credit Sales):** يتم إصدار المبيعات وتوجيه الحساب المدين لحساب العميل التجاري (مثل: مبيعات ناقلات مصانع الطوب، أو مركبات الجهات الحكومية).
3. **البيع بالتحويل المالي والصرافة (Transfers):** تسجيل وتوجيه القيمة لحساب البنوك أو حساب ومحفظة صرافة محلية (مثل: الكريمي للتمويل الأصغر، النجم، إلخ) مع إلزامي إدخال رقم مرجع الحوالة في قاعدة البيانات.
4. **البيع بنقاط البيع (POS):** الدفع بالبطاقات البنكية والائتمانية وربط الحساب الدائري بالصناديق المرتبطة بنقاط البيع.

---

## ثامناً: التوريد واللوجستيات والمخازن

عند شراء شحنة وقود (وقود خام في ناقلات القواطر):
1. يسجل النظام رقم مستند فاتورة الشراء وتفاصيل الناقلة.
2. يتجمع في شاشة التوريد حقول أجور النقل (أجر السائق والقاطرة) ورسوم نقاط مكاتب الخدمات ورسوم التحسين وجمارك التحصيل.
3. يقوم النظام بإنشاء **توزيع مرجعي للتكلفة الإجمالية** لتسعير مخزون الوقود باللتر الفعلي الواصل.
   `تكلفة اللتر الواصل = (سعر الشراء الإجمالي + أجور النقل + الرسوم الجمركية + الرسوم الإضافية) / عدد اللترات المفرغة بالخزانات`
4. تولد الشاشة قيداً محاسبياً متوازناً وتزيد مباشرة مخزون الخزان المعني وتنشئ كشف حركة المستودع الفريد في جدول `inventory_movements`.

---

## تاسعاً: المحاسبة العامة ودليل الحسابات (Accounting Principles)

تعتمد البنية المالية للمحطة على دليل حسابات (Chart of Accounts) متخصص ومبسط يتوافق مع طبيعة النشاط:

### 1. شجرة دليل الحسابات المقترح (نموذج مبسط):
- **1- الأصول (Assets):**
  - **11- الأصول المتداولة (Current Assets):**
    - **1101- الصناديق والخزائن (Cashboxes):**
      - `1101001` الصندوق الرئيسي للمحطة
      - `1101002` صندوق ورديات المحطة
    - **1102- البنوك والشركات المصرفية (Banks):**
      - `1102001` حساب بنك كريمي الإسلامي
      - `1102002` حساب كاش يمن (محفظة صرافة)
    - **1103- ذمم مدنية - عملاء آجليون (Customers Account):**
      - `1103001` حساب شركة هائل سعيد أنعم وآجل شركائهم
      - `1103002` حساب طقم الخدمات العسكرية والنقاط
    - **1104- حسابات وعهد العمال والموظفين (Staff Custodies):**
      - `1104001` حساب عهدة العامل أحمد الكبوس
      - `1104002` حساب عهدة العامل خالد المعلم
    - **1105- المخزون السلعي (Inventory):**
      - `1105001` مخزون خزان البترول (المنتج رقم 1)
      - `1105002` مخزون خزان الديزل (المنتج رقم 2)
- **2- الالتزامات وحقوق الملكية (Liabilities & Equity):**
  - **21- الالتزامات المتداولة (Current Liabilities):**
    - **2101- ذمم دائنة - الموردون (Suppliers):**
      - `2101001` شركة النفط اليمنية - صنعاء/عدن
      - `2101002` حساب المورد التجاري باعبيد والشراكة
- **3- الإيرادات والمبيعات (Revenues):**
  - `3101001` إيرادات مبيعات البترول الممتاز
  - `3101002` إيرادات مبيعات الديزل
  - `3102001` إيرادات تسويات مبيعات (زيادة العهد)
- **4- المصروفات والتكاليف (Expenses & Costs):**
  - **41- تكلفة المبيعات (Cost of Goods Sold):**
    - `4101001` تكلفة وقود البترول المباع
    - `4101002` تكلفة وقود الديزل المباع
  - **42- المصروفات التشغيلية والخدمية (Operating Expenses):**
    - `4201001` مصاريف صيانة وغسيل طرمبات ومضخات الوقود
    - `4201002` مصاريف أجور نقل وتفريغ لوجستية
    - `4201003` مصاريف رسوم تحسين وبلديات ونقاط تفتيش اليمن
    - `4201004` مصاريف عجز مالي عهود عمال (إن تحملته المحطة)
    - `4201005` مصاريف الرواتب والأجور

### 2. توليد وعرض القوائم المالية
يقوم النظام بعمل ترحيل لحظي (Auto-Posting) للقيود المركبة عند المبيعات والتوريد وإغلاق الوجبات لتوليد:
- **ميزان المراجعة (Trial Balance):** للتحقق من تطابق الأرصدة المدينة والدائنة لكافة الصناديق والعهود والمبيعات.
- **قائمة الدخل (Income Statement):** لاستخراج مجمل وصافي الأرباح:
  `صافي الأرباح = إيرادات المبيعات - تكلفة المبيعات المباشرة - المصاريف التشغيلية`
- **الميزانية العمومية (Balance Sheet):** تظهر موقف أصول المحطة (النقدية في الخزينة والبنك والعهد وقيمة الوقود بالخزانات) ضد الالتزامات والمورديين المتبقيين والتمويل المالي للشركاء.

---

## عاشراً: التقارير والتحليلات البيانية الذكية (Reports Module)

يتضمن النظام محرك تقارير متقدم يخدّم مستويات التشغيل والمحاسبة والمراقبة:

1. **التقارير التشغيلية الميدانية:**
   - **تقرير حركة المبيعات اليومية والشهرية:** تبيان كمية المبيعات باللتر وقيمتها النقدية وحساب المتوسط اليومي لكل منتج (بترول/ديزل).
   - **تقرير إنتاجية المضخات:** قياس الكميات المباعة بكل مضخة لتحديد الطرمبات الأكثر نشاطاً في المحطة لتشجيع الصيانة الدورية لها.
   - **تقرير إنتاجية عمال الورديات وعجز الورديات:** تتبع تاريخي دقيق لمعدلات العجز المالي والزيادة لتقييم أمانة ومهارة موظفي الصيانة بالدرج.
   - **تقرير الخزانات ومسار الجرد الفعلي:** توضيح الفاقد والمكتسب اليومي لتجنب عمليات تسريب الوقود تحت الأرض أو تبخر المواد بسرعة.
2. **التقارير المالية والمحاسبية لليومية:**
   - **كشف حساب عميل موحد وعملاء التحت كشف:** تبيان فواتير السحب الآجل ومستندات الدفع والقبض وكسور المديونية المتبقية وتصديرها كملفات PDF وتليجرام جاهزة.
   - **كشف حساب مورد شامل السحوبات والكميات المستلمة.**
   - **حركة حركة الصندوق والسيولة النقدية والتحويلات اللحظية.**
   - **ميزان المراجعة بالأرصدة والمجاميع، وقائمة الدخل والأستاذ العام.**

---

## الحادي عشر: الجدار التقني والبنية الهيكلية والبرمجية (Architecture System)

### 1. الهيكل المعماري البرمجي للويب والشبكة المحلية (Architecture Diagram)
النظام يدعم نمط الهجين المزدوج (Local-First Deployment with Optional Cloud Sync).
يعمل مباشرة في شبكة المحطة المحلية (LAN) دون اشتراط توفر إنترنت، مع ميزة التحول التلقائي للسحابي (SaaS Cloud Node) عند توفر الخدمة لمزامنة فروع محطات متعددة للشركة الأم.

```
       ┌────────────────────────────────────────────────────────┐
       │                       Flutter Web                      │
       │                   (واجهة المستخدم في المتصفح)             │
       └───────────────────────────┬────────────────────────────┘
                                   │
                                   │ طلبات REST API / JWT Auth
                                   ▼
       ┌────────────────────────────────────────────────────────┐
       │                       Laravel 12                       │
       │                      (API Backend)                     │
       └───────────────────────────┬────────────────────────────┘
                                   │
                                   │ محرك الاستعلامات والعمليات
                                   ▼
       ┌────────────────────────────────────────────────────────┐
       │                   PostgreSQL Database                  │
       │           (قواعد البيانات وقفل العمليات بالتزامن)         │
       └────────────────────────────────────────────────────────┘
```

- **Backend Context:** بناء هيكل API مريح ونظام مروّج للحدث (Event-Driven Architecture) مع معمارية الخدمات المستقلة للمحاسبة والمخازن.
- **Frontend Context:** واجهة ويب ممتازة ومبهرجة مبنية بـ Flutter Web تتوافق مع الأجهزة اللوحية والحواسيب وتدعم الاستجابة السريعة (Responsive Design) مع واجهة مستخدم ثنائية الاتجاه (عربي/إنجليزي) ووضع الداكن.
- **Database Context (PostgreSQL):** استخدام المعاملات المعزولة (Database Transactions Level Serializable) لحماية عدادات المضخات وسحوبات الخزانات من تضارب القيد المتزامن (Race Conditions).

### 2. خطة الأمان وحماية البيانات والنسخ الاحتياطي (Security & Backup Strategy)
1. **المصادقة والتسجيل (Authentication & JWT Token):**
   - حماية مسارات الـ API بالكامل ببروتوكول حماية الجلسة والتوكن JWT ذي الهوية المشفرة.
   - التحقق الصارم من صحة المدخلات وخلو سجلات التغذية من نصوص التمرير الخبيث SQL Injection والقرصنة XSS.
2. **سجل التدقيق والمراقبة الذاتي (Audit Log):**
   - كما موضح في جدول `audit_logs`؛ كل عملية تعديل أو قراءة مادية أو إنشاء لليوجيات تسجل بالرمز والاسم والفوارق والمقارنات وجهاز الفرز للتتبع السريع للأمن السيبراني والداخلي للشركة.
3. **خوارزميات واستراتيجية النسخ الاحتياطي المؤتمت (Backup Engine):**
   - تنفيذ سكربت جوب مؤتمت (Laravel Scheduler CRON) يقوم بالتقاط نسخة قواعد البيانات الهيكلية والبيانية تكرارياً كل 6 ساعات محلياً.
   - المزامنة الاحتياطية المشفرة للسحابة والخواص لقرص التخزين الخارجي أو سحابات حماية AWS S3 / Google Cloud Drive لتأمين البيانات بنسبة 99.99% ضد حوادث التلف والحرائق والكوارث المادية للمحطة الميدانية.

---

وثيقة التحليل والبرمجة هذه توضح النضج التنظيمي المحاسبي والتقني لتأسيس نظام وقود يمني متين وصالح للإنتاج والتشغيل في أصعب الظروف الإقليمية.
