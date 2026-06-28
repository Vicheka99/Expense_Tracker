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
    private static final int DATABASE_VERSION = 1;

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
        String createTableQuery = "CREATE TABLE " + TABLE_TRANSACTIONS + " (" +
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
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
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
}
