package com.example.expense_tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "expense_tracker.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_TRANSACTIONS = "transactions";
    public static final String COL_ID = "id";
    public static final String COL_USER_EMAIL = "user_email";
    public static final String COL_TYPE = "type"; // 'expense' or 'income'
    public static final String COL_AMOUNT = "amount";
    public static final String COL_CATEGORY = "category";
    public static final String COL_DATE = "date"; // YYYY-MM-DD
    public static final String COL_NOTE = "note";
    public static final String COL_RECEIPT_IMAGE = "receipt_image"; // Base64 thumbnail string
    public static final String COL_SYNCED = "synced"; // 0 for false, 1 for true
    public static final String COL_CREATED_AT = "created_at";

    // Saving Goals Table & Columns
    public static final String TABLE_SAVING_GOALS = "saving_goals";
    public static final String COL_GOAL_ID = "id";
    public static final String COL_GOAL_USER_EMAIL = "user_email";
    public static final String COL_GOAL_NAME = "name";
    public static final String COL_GOAL_TARGET = "target_amount";
    public static final String COL_GOAL_CURRENT = "current_amount";

    // Recurring Payments Table & Columns
    public static final String TABLE_RECURRING = "recurring_payments";
    public static final String COL_REC_ID = "id";
    public static final String COL_REC_USER_EMAIL = "user_email";
    public static final String COL_REC_NAME = "name";
    public static final String COL_REC_AMOUNT = "amount";
    public static final String COL_REC_FREQUENCY = "frequency";
    public static final String COL_REC_DUE_DATE = "due_date";

    // Category Budgets Table & Columns
    public static final String TABLE_CAT_BUDGETS = "category_budgets";
    public static final String COL_CB_ID = "id";
    public static final String COL_CB_USER_EMAIL = "user_email";
    public static final String COL_CB_CATEGORY = "category";
    public static final String COL_CB_LIMIT = "budget_limit";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTransactionsTable = "CREATE TABLE " + TABLE_TRANSACTIONS + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_USER_EMAIL + " TEXT, " +
                COL_TYPE + " TEXT, " +
                COL_AMOUNT + " REAL, " +
                COL_CATEGORY + " TEXT, " +
                COL_DATE + " TEXT, " +
                COL_NOTE + " TEXT, " +
                COL_RECEIPT_IMAGE + " TEXT, " +
                COL_SYNCED + " INTEGER DEFAULT 0, " +
                COL_CREATED_AT + " TEXT DEFAULT CURRENT_TIMESTAMP)";
        db.execSQL(createTransactionsTable);

        String createGoalsTable = "CREATE TABLE " + TABLE_SAVING_GOALS + " (" +
                COL_GOAL_ID + " TEXT PRIMARY KEY, " +
                COL_GOAL_USER_EMAIL + " TEXT, " +
                COL_GOAL_NAME + " TEXT, " +
                COL_GOAL_TARGET + " REAL, " +
                COL_GOAL_CURRENT + " REAL)";
        db.execSQL(createGoalsTable);

        String createRecurringTable = "CREATE TABLE " + TABLE_RECURRING + " (" +
                COL_REC_ID + " TEXT PRIMARY KEY, " +
                COL_REC_USER_EMAIL + " TEXT, " +
                COL_REC_NAME + " TEXT, " +
                COL_REC_AMOUNT + " REAL, " +
                COL_REC_FREQUENCY + " TEXT, " +
                COL_REC_DUE_DATE + " TEXT)";
        db.execSQL(createRecurringTable);

        String createCatBudgetsTable = "CREATE TABLE " + TABLE_CAT_BUDGETS + " (" +
                COL_CB_ID + " TEXT PRIMARY KEY, " +
                COL_CB_USER_EMAIL + " TEXT, " +
                COL_CB_CATEGORY + " TEXT, " +
                COL_CB_LIMIT + " REAL)";
        db.execSQL(createCatBudgetsTable);

        // Create indices on queried fields (user_email and date) for rapid querying
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON " + TABLE_TRANSACTIONS + 
                   " (" + COL_USER_EMAIL + ", " + COL_DATE + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goals_user ON " + TABLE_SAVING_GOALS + 
                   " (" + COL_GOAL_USER_EMAIL + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rec_user ON " + TABLE_RECURRING + 
                   " (" + COL_REC_USER_EMAIL + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cb_user ON " + TABLE_CAT_BUDGETS + 
                   " (" + COL_CB_USER_EMAIL + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SAVING_GOALS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECURRING);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CAT_BUDGETS);
        onCreate(db);
    }

    // Transaction Model representation for easy transfer
    public static class Transaction {
        public String id;
        public String userEmail;
        public String type;
        public double amount;
        public String category;
        public String date;
        public String note;
        public String receiptImage;
        public int synced;

        public Transaction() {}

        public Transaction(String id, String userEmail, String type, double amount, String category, String date, String note, String receiptImage, int synced) {
            this.id = id;
            this.userEmail = userEmail;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.date = date;
            this.note = note;
            this.receiptImage = receiptImage;
            this.synced = synced;
        }
    }

    // Insert a transaction
    public boolean insertTransaction(Transaction tx) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, tx.id);
        cv.put(COL_USER_EMAIL, tx.userEmail);
        cv.put(COL_TYPE, tx.type);
        cv.put(COL_AMOUNT, tx.amount);
        cv.put(COL_CATEGORY, tx.category);
        cv.put(COL_DATE, tx.date);
        cv.put(COL_NOTE, tx.note);
        cv.put(COL_RECEIPT_IMAGE, tx.receiptImage);
        cv.put(COL_SYNCED, tx.synced);

        long result = db.insert(TABLE_TRANSACTIONS, null, cv);
        return result != -1;
    }

    // Delete a transaction
    public boolean deleteTransaction(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_TRANSACTIONS, COL_ID + "=?", new String[]{id});
        return result > 0;
    }

    // Fetch all transactions for a specific user, sorted by date (newest first)
    public List<Transaction> getTransactions(String userEmail) {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null,
                COL_USER_EMAIL + "=?", new String[]{userEmail},
                null, null, COL_DATE + " DESC, " + COL_CREATED_AT + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                Transaction tx = new Transaction();
                tx.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                tx.userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_EMAIL));
                tx.type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));
                tx.amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT));
                tx.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
                tx.date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                tx.note = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE));
                tx.receiptImage = cursor.getString(cursor.getColumnIndexOrThrow(COL_RECEIPT_IMAGE));
                tx.synced = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SYNCED));
                list.add(tx);
            }
            cursor.close();
        }
        return list;
    }

    // Get unsynced transactions (for syncing to Supabase)
    public List<Transaction> getUnsyncedTransactions(String userEmail) {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null,
                COL_USER_EMAIL + "=? AND " + COL_SYNCED + "=0", new String[]{userEmail},
                null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                Transaction tx = new Transaction();
                tx.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                tx.userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_EMAIL));
                tx.type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));
                tx.amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT));
                tx.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
                tx.date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                tx.note = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE));
                tx.receiptImage = cursor.getString(cursor.getColumnIndexOrThrow(COL_RECEIPT_IMAGE));
                tx.synced = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SYNCED));
                list.add(tx);
            }
            cursor.close();
        }
        return list;
    }

    // Mark a transaction as synced
    public void markAsSynced(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNCED, 1);
        db.update(TABLE_TRANSACTIONS, cv, COL_ID + "=?", new String[]{id});
    }

    // Get Balance Summary: returns total income and total expense as a Map
    public Map<String, Double> getBalanceSummary(String userEmail) {
        Map<String, Double> summary = new HashMap<>();
        summary.put("income", 0.0);
        summary.put("expense", 0.0);

        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COL_TYPE + ", SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COL_USER_EMAIL + "=? GROUP BY " + COL_TYPE;
        Cursor cursor = db.rawQuery(query, new String[]{userEmail});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String type = cursor.getString(0);
                double sum = cursor.getDouble(1);
                if ("income".equalsIgnoreCase(type) || "expense".equalsIgnoreCase(type)) {
                    summary.put(type.toLowerCase(), sum);
                }
            }
            cursor.close();
        }
        return summary;
    }

    // Get sum of expense for dates >= targetDate
    public double getExpenseSumSince(String userEmail, String targetDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COL_USER_EMAIL + "=? AND " + COL_TYPE + "='expense' AND " + COL_DATE + ">=?";
        Cursor cursor = db.rawQuery(query, new String[]{userEmail, targetDate});
        double sum = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                sum = cursor.getDouble(0);
            }
            cursor.close();
        }
        return sum;
    }

    // Get exact sum of expense for a specific single date
    public double getExpenseSumForDate(String userEmail, String date) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COL_USER_EMAIL + "=? AND " + COL_TYPE + "='expense' AND " + COL_DATE + "=?";
        Cursor cursor = db.rawQuery(query, new String[]{userEmail, date});
        double sum = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                sum = cursor.getDouble(0);
            }
            cursor.close();
        }
        return sum;
    }

    // Get group spending by category
    public Map<String, Double> getCategorySpending(String userEmail) {
        Map<String, Double> breakdown = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COL_CATEGORY + ", SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COL_USER_EMAIL + "=? AND " + COL_TYPE + "='expense' GROUP BY " + COL_CATEGORY;
        Cursor cursor = db.rawQuery(query, new String[]{userEmail});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String cat = cursor.getString(0);
                double sum = cursor.getDouble(1);
                breakdown.put(cat, sum);
            }
            cursor.close();
        }
        return breakdown;
    }

    // --- SAVING GOALS MODEL & CRUD ---
    public static class SavingGoal {
        public String id;
        public String userEmail;
        public String name;
        public double targetAmount;
        public double currentAmount;

        public SavingGoal() {}
        public SavingGoal(String id, String userEmail, String name, double targetAmount, double currentAmount) {
            this.id = id;
            this.userEmail = userEmail;
            this.name = name;
            this.targetAmount = targetAmount;
            this.currentAmount = currentAmount;
        }
    }

    public boolean insertSavingGoal(SavingGoal goal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_GOAL_ID, goal.id);
        cv.put(COL_GOAL_USER_EMAIL, goal.userEmail);
        cv.put(COL_GOAL_NAME, goal.name);
        cv.put(COL_GOAL_TARGET, goal.targetAmount);
        cv.put(COL_GOAL_CURRENT, goal.currentAmount);
        long result = db.insert(TABLE_SAVING_GOALS, null, cv);
        return result != -1;
    }

    public boolean updateSavingGoal(SavingGoal goal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_GOAL_NAME, goal.name);
        cv.put(COL_GOAL_TARGET, goal.targetAmount);
        cv.put(COL_GOAL_CURRENT, goal.currentAmount);
        int result = db.update(TABLE_SAVING_GOALS, cv, COL_GOAL_ID + "=?", new String[]{goal.id});
        return result > 0;
    }

    public boolean deleteSavingGoal(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_SAVING_GOALS, COL_GOAL_ID + "=?", new String[]{id});
        return result > 0;
    }

    public List<SavingGoal> getSavingGoals(String userEmail) {
        List<SavingGoal> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SAVING_GOALS, null, COL_GOAL_USER_EMAIL + "=?", new String[]{userEmail}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                SavingGoal g = new SavingGoal();
                g.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_GOAL_ID));
                g.userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COL_GOAL_USER_EMAIL));
                g.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_GOAL_NAME));
                g.targetAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_TARGET));
                g.currentAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_CURRENT));
                list.add(g);
            }
            cursor.close();
        }
        return list;
    }

    // --- RECURRING PAYMENTS MODEL & CRUD ---
    public static class RecurringPayment {
        public String id;
        public String userEmail;
        public String name;
        public double amount;
        public String frequency;
        public String dueDate;

        public RecurringPayment() {}
        public RecurringPayment(String id, String userEmail, String name, double amount, String frequency, String dueDate) {
            this.id = id;
            this.userEmail = userEmail;
            this.name = name;
            this.amount = amount;
            this.frequency = frequency;
            this.dueDate = dueDate;
        }
    }

    public boolean insertRecurringPayment(RecurringPayment rec) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_REC_ID, rec.id);
        cv.put(COL_REC_USER_EMAIL, rec.userEmail);
        cv.put(COL_REC_NAME, rec.name);
        cv.put(COL_REC_AMOUNT, rec.amount);
        cv.put(COL_REC_FREQUENCY, rec.frequency);
        cv.put(COL_REC_DUE_DATE, rec.dueDate);
        long result = db.insert(TABLE_RECURRING, null, cv);
        return result != -1;
    }

    public boolean updateRecurringPayment(RecurringPayment rec) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_REC_NAME, rec.name);
        cv.put(COL_REC_AMOUNT, rec.amount);
        cv.put(COL_REC_FREQUENCY, rec.frequency);
        cv.put(COL_REC_DUE_DATE, rec.dueDate);
        int result = db.update(TABLE_RECURRING, cv, COL_REC_ID + "=?", new String[]{rec.id});
        return result > 0;
    }

    public boolean deleteRecurringPayment(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_RECURRING, COL_REC_ID + "=?", new String[]{id});
        return result > 0;
    }

    public List<RecurringPayment> getRecurringPayments(String userEmail) {
        List<RecurringPayment> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECURRING, null, COL_REC_USER_EMAIL + "=?", new String[]{userEmail}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                RecurringPayment r = new RecurringPayment();
                r.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_ID));
                r.userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_USER_EMAIL));
                r.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_NAME));
                r.amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REC_AMOUNT));
                r.frequency = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_FREQUENCY));
                r.dueDate = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_DUE_DATE));
                list.add(r);
            }
            cursor.close();
        }
        return list;
    }

    // Category Budget Model Representation
    public static class CategoryBudget {
        public String id;
        public String userEmail;
        public String category;
        public double budgetLimit;

        public CategoryBudget() {}

        public CategoryBudget(String id, String userEmail, String category, double budgetLimit) {
            this.id = id;
            this.userEmail = userEmail;
            this.category = category;
            this.budgetLimit = budgetLimit;
        }
    }

    public boolean insertCategoryBudget(CategoryBudget cb) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CB_ID, cb.id);
        cv.put(COL_CB_USER_EMAIL, cb.userEmail);
        cv.put(COL_CB_CATEGORY, cb.category);
        cv.put(COL_CB_LIMIT, cb.budgetLimit);
        long result = db.insert(TABLE_CAT_BUDGETS, null, cv);
        return result != -1;
    }

    public boolean updateCategoryBudget(CategoryBudget cb) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CB_CATEGORY, cb.category);
        cv.put(COL_CB_LIMIT, cb.budgetLimit);
        int result = db.update(TABLE_CAT_BUDGETS, cv, COL_CB_ID + "=?", new String[]{cb.id});
        return result > 0;
    }

    public boolean deleteCategoryBudget(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_CAT_BUDGETS, COL_CB_ID + "=?", new String[]{id});
        return result > 0;
    }

    public List<CategoryBudget> getCategoryBudgets(String userEmail) {
        List<CategoryBudget> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CAT_BUDGETS, null, COL_CB_USER_EMAIL + "=?", new String[]{userEmail}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                CategoryBudget cb = new CategoryBudget();
                cb.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_CB_ID));
                cb.userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COL_CB_USER_EMAIL));
                cb.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CB_CATEGORY));
                cb.budgetLimit = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_CB_LIMIT));
                list.add(cb);
            }
            cursor.close();
        }
        return list;
    }

    public boolean doesTransactionExist(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSACTIONS, new String[]{COL_ID}, COL_ID + "=?", new String[]{id}, null, null, null);
        boolean exists = false;
        if (cursor != null) {
            exists = cursor.getCount() > 0;
            cursor.close();
        }
        return exists;
    }

    public void clearUserData(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty() || "No user email".equals(userEmail)) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, COL_USER_EMAIL + "=?", new String[]{userEmail});
        db.delete(TABLE_SAVING_GOALS, COL_GOAL_USER_EMAIL + "=?", new String[]{userEmail});
        db.delete(TABLE_RECURRING, COL_REC_USER_EMAIL + "=?", new String[]{userEmail});
        db.delete(TABLE_CAT_BUDGETS, COL_CB_USER_EMAIL + "=?", new String[]{userEmail});
    }

    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, null, null);
        db.delete(TABLE_SAVING_GOALS, null, null);
        db.delete(TABLE_RECURRING, null, null);
        db.delete(TABLE_CAT_BUDGETS, null, null);
    }
}
