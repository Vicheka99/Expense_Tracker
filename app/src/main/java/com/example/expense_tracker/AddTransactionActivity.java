package com.example.expense_tracker;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class AddTransactionActivity extends AppCompatActivity {

    private boolean isExpense = true;
    private String selectedCategory = "";
    private String selectedDate = ""; // format: YYYY-MM-DD

    // Views
    private TextView btnExpenseToggle;
    private TextView btnIncomeToggle;
    private EditText etAmount;
    private GridLayout glCategories;
    private MaterialCardView cardDatePicker;
    private TextView tvSelectedDate;
    private EditText etDescription;
    private MaterialButton btnSaveTransaction;

    private Calendar calendar;

    // Category Class
    private static class CategoryItem {
        String name;
        int iconRes;

        CategoryItem(String name, int iconRes) {
            this.name = name;
            this.iconRes = iconRes;
        }
    }

    private final CategoryItem[] expenseCategories = new CategoryItem[]{
            new CategoryItem("Food", R.drawable.ic_fork_knife),
            new CategoryItem("Travel", R.drawable.ic_car),
            new CategoryItem("Shop", R.drawable.ic_shopping_bag),
            new CategoryItem("Health", R.drawable.ic_health),
            new CategoryItem("Bills", R.drawable.ic_bills),
            new CategoryItem("Fun", R.drawable.ic_fun),
            new CategoryItem("Study", R.drawable.ic_study),
            new CategoryItem("Other", R.drawable.ic_other)
    };

    private final CategoryItem[] incomeCategories = new CategoryItem[]{
            new CategoryItem("Salary", R.drawable.ic_wallet),
            new CategoryItem("Bonus", R.drawable.ic_lightbulb),
            new CategoryItem("Freelance", R.drawable.ic_laptop),
            new CategoryItem("Investment", R.drawable.ic_trend_prediction),
            new CategoryItem("Savings", R.drawable.ic_exchange),
            new CategoryItem("Other", R.drawable.ic_other)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_add_transaction);

        // Bind views
        ImageView btnBack = findViewById(R.id.btnBack);
        btnExpenseToggle = findViewById(R.id.btnExpenseToggle);
        btnIncomeToggle = findViewById(R.id.btnIncomeToggle);
        etAmount = findViewById(R.id.etAmount);
        glCategories = findViewById(R.id.glCategories);
        cardDatePicker = findViewById(R.id.cardDatePicker);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        etDescription = findViewById(R.id.etDescription);
        btnSaveTransaction = findViewById(R.id.btnSaveTransaction);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Toggle buttons logic
        btnExpenseToggle.setOnClickListener(v -> setTransactionType(true));
        btnIncomeToggle.setOnClickListener(v -> setTransactionType(false));

        // Date Picker setup
        calendar = Calendar.getInstance();
        updateDateDisplay();

        cardDatePicker.setOnClickListener(v -> showDatePicker());

        // Save logic
        btnSaveTransaction.setOnClickListener(v -> saveTransaction());

        // Initial setup
        setTransactionType(true);
    }

    private void setTransactionType(boolean isExpenseType) {
        this.isExpense = isExpenseType;
        selectedCategory = ""; // reset selected category on toggle

        if (isExpense) {
            btnExpenseToggle.setBackgroundResource(R.drawable.bg_pill_active);
            btnExpenseToggle.setTextColor(Color.WHITE);
            btnIncomeToggle.setBackgroundResource(android.R.color.transparent);
            btnIncomeToggle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            btnIncomeToggle.setBackgroundResource(R.drawable.bg_pill_active);
            btnIncomeToggle.setTextColor(Color.WHITE);
            btnExpenseToggle.setBackgroundResource(android.R.color.transparent);
            btnExpenseToggle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }

        populateCategoriesGrid();
    }

    private void populateCategoriesGrid() {
        glCategories.removeAllViews();
        CategoryItem[] categories = isExpense ? expenseCategories : incomeCategories;

        for (int i = 0; i < categories.length; i++) {
            CategoryItem item = categories[i];
            View cellView = LayoutInflater.from(this).inflate(R.layout.item_category_grid, glCategories, false);

            MaterialCardView cardCategory = cellView.findViewById(R.id.cardCategory);
            ImageView ivCategoryIcon = cellView.findViewById(R.id.ivCategoryIcon);
            TextView tvCategoryName = cellView.findViewById(R.id.tvCategoryName);

            tvCategoryName.setText(item.name);
            ivCategoryIcon.setImageResource(item.iconRes);

            // Set tags to identify category name
            cardCategory.setTag(item.name);

            cardCategory.setOnClickListener(v -> {
                selectedCategory = (String) v.getTag();
                updateCategoryHighlights();
            });

            glCategories.addView(cellView);
        }
    }

    private void updateCategoryHighlights() {
        for (int i = 0; i < glCategories.getChildCount(); i++) {
            View cell = glCategories.getChildAt(i);
            MaterialCardView cardCategory = cell.findViewById(R.id.cardCategory);
            ImageView ivCategoryIcon = cell.findViewById(R.id.ivCategoryIcon);
            TextView tvCategoryName = cell.findViewById(R.id.tvCategoryName);

            String catName = (String) cardCategory.getTag();
            if (catName != null && catName.equals(selectedCategory)) {
                // Selected look
                cardCategory.setCardBackgroundColor(ContextCompat.getColor(this, R.color.dark_blue));
                ivCategoryIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.white));
                tvCategoryName.setTextColor(Color.WHITE);
            } else {
                // Default unselected look
                cardCategory.setCardBackgroundColor(Color.TRANSPARENT);
                ivCategoryIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
                tvCategoryName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateDisplay();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        SimpleDateFormat displayFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        tvSelectedDate.setText(displayFormat.format(calendar.getTime()));
        selectedDate = dbFormat.format(calendar.getTime());
    }

    private void saveTransaction() {
        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = etDescription.getText().toString().trim();
        if (note.isEmpty()) {
            note = selectedCategory; // Fallback to category name if empty
        }

        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(this);
        String userEmail = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

        // Create transaction object
        String txId = UUID.randomUUID().toString();
        DatabaseHelper.Transaction tx = new DatabaseHelper.Transaction(
                txId,
                userEmail,
                isExpense ? "expense" : "income",
                amount,
                selectedCategory,
                selectedDate,
                note,
                "", // No receipt photo
                0 // 0 = unsynced
        );

        // Save locally
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        boolean localSaved = dbHelper.insertTransaction(tx);

        if (localSaved) {
            Toast.makeText(this, "Transaction saved locally", Toast.LENGTH_SHORT).show();

            // Trigger background sync to Supabase if logged in
            if (authManager.isLoggedIn()) {
                authManager.syncTransaction(tx, new Runnable() {
                    @Override
                    public void run() {
                        // Success: mark as synced in local DB
                        dbHelper.markAsSynced(txId);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        // Failure: do nothing, will sync next time
                    }
                });
            }

            // Return to home
            finish();
        } else {
            Toast.makeText(this, "Failed to save transaction locally", Toast.LENGTH_LONG).show();
        }
    }
}