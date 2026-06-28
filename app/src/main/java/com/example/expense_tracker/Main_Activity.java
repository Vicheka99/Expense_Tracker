package com.example.expense_tracker;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main_Activity extends AppCompatActivity {

    public static String formatAmount(android.content.Context context, double amountInUSD) {
        return formatAmount(context, amountInUSD, false);
    }

    public static String formatAmount(android.content.Context context, double amountInUSD, boolean forceNoDecimal) {
        if (context == null) return String.format(Locale.US, forceNoDecimal ? "$%,.0f" : "$%,.2f", amountInUSD);
        android.content.SharedPreferences prefs = context.getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        String currency = prefs.getString("selected_currency", "USD");
        if ("KHR".equals(currency)) {
            double rate = prefs.getFloat("exchange_rate", 4100.0f);
            double amountInKHR = amountInUSD * rate;
            return String.format(Locale.US, "%,.0f ៛", amountInKHR);
        } else {
            return String.format(Locale.US, forceNoDecimal ? "$%,.0f" : "$%,.2f", amountInUSD);
        }
    }

    public static void checkBudgetAndNotify(android.content.Context context) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        boolean notifEnabled = prefs.getBoolean("notif_enabled", true);
        if (!notifEnabled) return;

        double monthLimit = prefs.getFloat("monthly_budget_limit", 1000.00f);
        if (monthLimit <= 0) return;

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(context);
        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        double monthSpend = dbHelper.getExpenseSumSince(email, dbFormat.format(cal.getTime()));

        if (monthSpend >= 0.85 * monthLimit) {
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            String channelId = "budget_alerts_channel";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    "Budget Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                );
                notificationManager.createNotificationChannel(channel);
            }

            String spendStr = formatAmount(context, monthSpend);
            String limitStr = formatAmount(context, monthLimit);

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Budget Alert!")
                .setContentText(String.format(Locale.US, "Your monthly spending is low! Spent %s of %s limit.", spendStr, limitStr))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

            notificationManager.notify(101, builder.build());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBudgetAndNotify(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.main_activity);

        // Request runtime notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }

        // Auto-seed database if empty to showcase metrics and graphs
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(this);
        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
        if (dbHelper.getTransactions(email).isEmpty()) {
            seedSampleData(dbHelper, email);
        }

        View navHome = findViewById(R.id.nav_home);
        View navAnalysis = findViewById(R.id.nav_analysis);
        View navBudget = findViewById(R.id.nav_budget);
        View navProfile = findViewById(R.id.nav_profile);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);

        // Custom Navigation item listeners
        navHome.setOnClickListener(v -> selectFragment(new HomeFragment(), navHome, navHome, navAnalysis, navBudget, navProfile));
        navAnalysis.setOnClickListener(v -> selectFragment(new AnalysisFragment(), navAnalysis, navHome, navAnalysis, navBudget, navProfile));
        navBudget.setOnClickListener(v -> selectFragment(new BudgetFragment(), navBudget, navHome, navAnalysis, navBudget, navProfile));
        navProfile.setOnClickListener(v -> selectFragment(new PlaceholderFragment.ProfileFragment(), navProfile, navHome, navAnalysis, navBudget, navProfile));

        // Initial setup - select Home
        if (savedInstanceState == null) {
            selectFragment(new HomeFragment(), navHome, navHome, navAnalysis, navBudget, navProfile);
        }

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Main_Activity.this, AddTransactionActivity.class);
            startActivity(intent);
        });
    }

    private void selectFragment(Fragment fragment, View selectedNav, View... navs) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();

        // Dynamically update tints of bottom navigation icons
        for (View nav : navs) {
            if (nav instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) nav;
                if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof ImageView) {
                    ImageView img = (ImageView) vg.getChildAt(0);
                    if (nav == selectedNav) {
                        img.setImageTintList(ColorStateList.valueOf(getColor(R.color.primary_blue)));
                    } else {
                        img.setImageTintList(ColorStateList.valueOf(getColor(R.color.text_main)));
                    }
                }
            }
        }
    }

    // Home Fragment Class
    public static class HomeFragment extends Fragment {
        private View rootView;
        private String searchQuery = "";

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_home, container, false);
            setupStaticGreeting(rootView);

            TextView tvHomeManageCategories = rootView.findViewById(R.id.tvHomeManageCategories);
            if (tvHomeManageCategories != null) {
                tvHomeManageCategories.setOnClickListener(v -> showManageCategoriesDialog());
            }

            TextView tvHomeViewAllTransactions = rootView.findViewById(R.id.tvHomeViewAllTransactions);
            if (tvHomeViewAllTransactions != null) {
                tvHomeViewAllTransactions.setOnClickListener(v -> showAllTransactionsDialog());
            }

            android.widget.EditText etHomeSearch = rootView.findViewById(R.id.etHomeSearch);
            if (etHomeSearch != null) {
                etHomeSearch.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        searchQuery = s.toString();
                        refreshData(rootView);
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            }

            return rootView;
        }

        private void showManageCategoriesDialog() {
            if (getActivity() == null) return;
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("selected_language", "EN");
            boolean isKhmer = "KH".equals(lang);

            String title = isKhmer ? "គ្រប់គ្រងប្រភេទចំណាយ" : "Manage Categories";
            String message = isKhmer ? "ប្រភេទចំណាយត្រូវបានដំណើរការដោយស្វ័យប្រវត្តិ និងធ្វើសមកាលកម្មរួចរាល់!\n\nបច្ចុប្បន្នអ្នកមាន៨ប្រភេទសកម្ម៖\n• អាហារ, ធ្វើដំណើរ, ទិញឥវ៉ាន់, សុខភាព\n• វិក្កយបត្រ, កម្សាន្ត, សិក្សា, ផ្សេងៗ\n\nការបន្ថែមប្រភេទផ្ទាល់ខ្លួននឹងមាននៅក្នុងការអាប់ដេតបន្ទាប់។"
                                     : "Categories are fully automated and synchronized!\n\nYou currently have 8 active categories:\n• Food, Travel, Shop, Health\n• Bills, Fun, Study, Other\n\nCustom category additions will be available in a future update.";
            String button = isKhmer ? "យល់ព្រម" : "Got it";

            new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(button, null)
                    .show();
        }

        private void showAllTransactionsDialog() {
            if (getActivity() == null) return;
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("selected_language", "EN");
            boolean isKhmer = "KH".equals(lang);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LinearLayout mainContainer = new LinearLayout(getActivity());
            mainContainer.setOrientation(LinearLayout.VERTICAL);
            mainContainer.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

            TextView title = new TextView(getActivity());
            title.setText(isKhmer ? "ប្រតិបត្តិការទាំងអស់" : "All Transactions");
            title.setTextSize(20);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(0xFF1A1C24);
            title.setPadding(0, 0, 0, dpToPx(16));
            mainContainer.addView(title);

            android.widget.ScrollView scrollView = new android.widget.ScrollView(getActivity());
            LinearLayout itemsContainer = new LinearLayout(getActivity());
            itemsContainer.setOrientation(LinearLayout.VERTICAL);

            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);

            if (transactions.isEmpty()) {
                TextView tvEmpty = new TextView(getActivity());
                tvEmpty.setText(isKhmer ? "មិនទាន់មានប្រតិបត្តិការណាមួយឡើយ។" : "No transactions recorded yet.");
                tvEmpty.setTextColor(0xFF9DA3B4);
                tvEmpty.setGravity(Gravity.CENTER);
                tvEmpty.setPadding(0, dpToPx(24), 0, dpToPx(24));
                itemsContainer.addView(tvEmpty);
            } else {
                for (final DatabaseHelper.Transaction tx : transactions) {
                    View txView = LayoutInflater.from(getActivity()).inflate(R.layout.item_transaction, itemsContainer, false);

                    MaterialCardView cardTxIconBg = txView.findViewById(R.id.cardTxIconBg);
                    ImageView ivTxIcon = txView.findViewById(R.id.ivTxIcon);
                    TextView tvTxTitle = txView.findViewById(R.id.tvTxTitle);
                    TextView tvTxDate = txView.findViewById(R.id.tvTxDate);
                    TextView tvTxAmount = txView.findViewById(R.id.tvTxAmount);
                    TextView tvTxCategory = txView.findViewById(R.id.tvTxCategory);

                    tvTxTitle.setText(tx.note);
                    tvTxCategory.setText(translateText(tx.category, isKhmer));
                    tvTxDate.setText(tx.date);

                    cardTxIconBg.setCardBackgroundColor(getCategoryBgColor(tx.category));
                    ivTxIcon.setImageResource(getCategoryIcon(tx.category));
                    ivTxIcon.setImageTintList(ColorStateList.valueOf(getCategoryIconTint(tx.category)));

                    if ("expense".equalsIgnoreCase(tx.type)) {
                        tvTxAmount.setText("-" + formatAmount(getActivity(), tx.amount));
                        tvTxAmount.setTextColor(0xFFF44336);
                    } else {
                        tvTxAmount.setText("+" + formatAmount(getActivity(), tx.amount));
                        tvTxAmount.setTextColor(0xFF4CAF50);
                    }

                    txView.setOnClickListener(v -> showTransactionDetails(tx));

                    itemsContainer.addView(txView);
                }
            }

            scrollView.addView(itemsContainer);
            mainContainer.addView(scrollView);
            builder.setView(mainContainer);
            builder.setPositiveButton(isKhmer ? "បិទ" : "Close", null);

            builder.show();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (rootView != null) {
                refreshData(rootView);
            }
        }

        private void setupStaticGreeting(View view) {
            TextView tvHomeGreeting = view.findViewById(R.id.tv_home_greeting);
            if (tvHomeGreeting != null && getActivity() != null) {
                SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
                String lang = prefs.getString("selected_language", "EN");
                boolean isKhmer = "KH".equals(lang);

                String name = authManager.getUserFullName();
                if (name == null || name.trim().isEmpty()) {
                    String email = authManager.getUserEmail();
                    name = isKhmer ? "អ្នកប្រើប្រាស់" : "User";
                    if (email != null && email.contains("@")) {
                        name = email.split("@")[0];
                        if (name.length() > 0) {
                            name = name.substring(0, 1).toUpperCase() + name.substring(1);
                        }
                    }
                }
                
                // Dynamic time of day greeting
                java.util.Calendar c = java.util.Calendar.getInstance();
                int timeOfDay = c.get(java.util.Calendar.HOUR_OF_DAY);
                String greetingPrefix;
                if (timeOfDay < 12) {
                    greetingPrefix = "Good Morning";
                } else if (timeOfDay < 16) {
                    greetingPrefix = "Good Afternoon";
                } else if (timeOfDay < 21) {
                    greetingPrefix = "Good Evening";
                } else {
                    greetingPrefix = "Good Night";
                }
                
                String transPrefix = translateText(greetingPrefix, isKhmer);
                tvHomeGreeting.setText(transPrefix + (isKhmer ? " " : ", ") + name);

                // Set dynamic locale-aware date header
                TextView tvHomeDate = view.findViewById(R.id.tv_home_date);
                if (tvHomeDate != null) {
                    SimpleDateFormat sdf;
                    if (isKhmer) {
                        sdf = new SimpleDateFormat("EEEE d MMMM", new Locale("km", "KH"));
                    } else {
                        sdf = new SimpleDateFormat("EEEE, MMMM d", Locale.US);
                    }
                    tvHomeDate.setText(sdf.format(new java.util.Date()));
                }
                
                // Load avatar
                ImageView ivHomeAvatar = view.findViewById(R.id.iv_home_profile_avatar);
                if (ivHomeAvatar != null) {
                    PlaceholderFragment.loadAvatar(authManager.getUserAvatarUrl(), ivHomeAvatar);
                }
            }
        }

        private void refreshData(View view) {
            if (getContext() == null) return;

            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getContext());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());

            // 1. Balance summary
            Map<String, Double> summary = dbHelper.getBalanceSummary(email);
            double totalIncome = summary.containsKey("income") ? summary.get("income") : 0.0;
            double totalExpense = summary.containsKey("expense") ? summary.get("expense") : 0.0;
            double balance = totalIncome - totalExpense;

            TextView tvHomeBalance = view.findViewById(R.id.tvHomeBalance);
            TextView tvHomeIncome = view.findViewById(R.id.tvHomeIncome);
            TextView tvHomeExpense = view.findViewById(R.id.tvHomeExpense);

            if (tvHomeBalance != null) {
                if (balance < 0) {
                    tvHomeBalance.setText("-" + formatAmount(getContext(), Math.abs(balance)));
                } else {
                    tvHomeBalance.setText(formatAmount(getContext(), balance));
                }
            }
            if (tvHomeIncome != null) {
                tvHomeIncome.setText(formatAmount(getContext(), totalIncome, true));
            }
            if (tvHomeExpense != null) {
                tvHomeExpense.setText(formatAmount(getContext(), totalExpense, true));
            }

            // 2. Stats calculation
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String todayStr = dbFormat.format(new java.util.Date());
            double todaySpend = dbHelper.getExpenseSumForDate(email, todayStr);

            Calendar cal = Calendar.getInstance();
            // Start of week (Monday/Sunday depending on locale, standardizing to Sunday-based week start)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
            String startOfWeekStr = dbFormat.format(cal.getTime());
            double weekSpend = dbHelper.getExpenseSumSince(email, startOfWeekStr);

            cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String startOfMonthStr = dbFormat.format(cal.getTime());
            double monthSpend = dbHelper.getExpenseSumSince(email, startOfMonthStr);

            double savingsRate = 0.0;
            if (totalIncome > 0) {
                savingsRate = ((totalIncome - totalExpense) / totalIncome) * 100.0;
                if (savingsRate < 0) savingsRate = 0.0; // clamp negative savings rate to 0
            }

            TextView tvHomeTodaySpend = view.findViewById(R.id.tvHomeTodaySpend);
            TextView tvHomeWeekSpend = view.findViewById(R.id.tvHomeWeekSpend);
            TextView tvHomeMonthSpend = view.findViewById(R.id.tvHomeMonthSpend);
            TextView tvHomeSavingsRate = view.findViewById(R.id.tvHomeSavingsRate);

            if (tvHomeTodaySpend != null) tvHomeTodaySpend.setText(formatAmount(getContext(), todaySpend));
            if (tvHomeWeekSpend != null) tvHomeWeekSpend.setText(formatAmount(getContext(), weekSpend));
            if (tvHomeMonthSpend != null) tvHomeMonthSpend.setText(formatAmount(getContext(), monthSpend));
            if (tvHomeSavingsRate != null) tvHomeSavingsRate.setText(String.format(Locale.US, "%.1f%%", savingsRate));

            // 3. Category overview
            LinearLayout layoutHomeCategories = view.findViewById(R.id.layoutHomeCategories);
            if (layoutHomeCategories != null) {
                layoutHomeCategories.removeAllViews();
                String[] allCategories = new String[]{"Food", "Travel", "Shop", "Health", "Bills", "Fun", "Study", "Other"};
                for (String cat : allCategories) {
                    addCategoryView(layoutHomeCategories, cat);
                }
            }

            // 4. Recent Transactions List
            LinearLayout layoutRecentTransactions = view.findViewById(R.id.layoutRecentTransactions);
            if (layoutRecentTransactions != null) {
                layoutRecentTransactions.removeAllViews();
                List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);

                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    List<DatabaseHelper.Transaction> filtered = new java.util.ArrayList<>();
                    String lowerQuery = searchQuery.toLowerCase().trim();
                    for (DatabaseHelper.Transaction tx : transactions) {
                        if ((tx.note != null && tx.note.toLowerCase().contains(lowerQuery)) ||
                            (tx.category != null && tx.category.toLowerCase().contains(lowerQuery))) {
                            filtered.add(tx);
                        }
                    }
                    transactions = filtered;
                }

                if (transactions.isEmpty()) {
                    TextView tvEmpty = new TextView(getContext());
                    tvEmpty.setText("No transactions recorded yet.");
                    tvEmpty.setTextColor(0xFF9DA3B4);
                    tvEmpty.setGravity(Gravity.CENTER);
                    tvEmpty.setTextSize(14);
                    tvEmpty.setPadding(0, dpToPx(24), 0, dpToPx(24));
                    layoutRecentTransactions.addView(tvEmpty);
                } else {
                    // Show up to 10 transactions
                    int count = Math.min(transactions.size(), 10);
                    for (int i = 0; i < count; i++) {
                        final DatabaseHelper.Transaction tx = transactions.get(i);
                        View txView = LayoutInflater.from(getContext()).inflate(R.layout.item_transaction, layoutRecentTransactions, false);

                        MaterialCardView cardTxIconBg = txView.findViewById(R.id.cardTxIconBg);
                        ImageView ivTxIcon = txView.findViewById(R.id.ivTxIcon);
                        TextView tvTxTitle = txView.findViewById(R.id.tvTxTitle);
                        TextView tvTxDate = txView.findViewById(R.id.tvTxDate);
                        TextView tvTxAmount = txView.findViewById(R.id.tvTxAmount);
                        TextView tvTxCategory = txView.findViewById(R.id.tvTxCategory);
                        tvTxTitle.setText(tx.note);
                        tvTxCategory.setText(tx.category);
                        tvTxDate.setText(tx.date);

                        // Layout styling based on category
                        cardTxIconBg.setCardBackgroundColor(getCategoryBgColor(tx.category));
                        ivTxIcon.setImageResource(getCategoryIcon(tx.category));
                        ivTxIcon.setImageTintList(ColorStateList.valueOf(getCategoryIconTint(tx.category)));

                        // Amount styling
                        if ("expense".equalsIgnoreCase(tx.type)) {
                            tvTxAmount.setText("-" + formatAmount(getContext(), tx.amount));
                            tvTxAmount.setTextColor(0xFFF44336); // Red
                        } else {
                            tvTxAmount.setText("+" + formatAmount(getContext(), tx.amount));
                            tvTxAmount.setTextColor(0xFF4CAF50); // Green
                        }

                        // Setup details dialog
                        txView.setOnClickListener(v -> showTransactionDetails(tx));

                        layoutRecentTransactions.addView(txView);
                    }
                }
            }

            // 5. Background sync for unsynced transactions
            if (authManager.isLoggedIn()) {
                List<DatabaseHelper.Transaction> unsynced = dbHelper.getUnsyncedTransactions(email);
                for (final DatabaseHelper.Transaction tx : unsynced) {
                    authManager.syncTransaction(tx, () -> dbHelper.markAsSynced(tx.id), null);
                }
            }
            translateView(getContext(), view);
        }

        private void addCategoryView(LinearLayout container, String cat) {
            if (getContext() == null) return;

            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(0, 0, dpToPx(20), 0);
            layout.setLayoutParams(layoutParams);

            MaterialCardView card = new MaterialCardView(getContext());
            card.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(56), dpToPx(56)));
            card.setRadius(dpToPx(16));
            card.setCardElevation(0);
            card.setCardBackgroundColor(getCategoryBgColor(cat));
            card.setStrokeWidth(0);

            ImageView img = new ImageView(getContext());
            FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(dpToPx(24), dpToPx(24));
            imgParams.gravity = Gravity.CENTER;
            img.setLayoutParams(imgParams);
            img.setImageResource(getCategoryIcon(cat));
            img.setImageTintList(ColorStateList.valueOf(getCategoryIconTint(cat)));
            card.addView(img);

            TextView text = new TextView(getContext());
            text.setText(cat);
            text.setTextSize(11);
            text.setGravity(Gravity.CENTER);
            text.setTextColor(0xFF9DA3B4);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMargins(0, dpToPx(8), 0, 0);
            text.setLayoutParams(textParams);

            layout.addView(card);
            layout.addView(text);
            container.addView(layout);
        }

        private void showTransactionDetails(final DatabaseHelper.Transaction tx) {
            if (getActivity() == null) return;
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("selected_language", "EN");
            boolean isKhmer = "KH".equals(lang);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            android.widget.ScrollView scrollView = new android.widget.ScrollView(getActivity());
            LinearLayout container = new LinearLayout(getActivity());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

            // Title Note
            TextView tvTitle = new TextView(getActivity());
            tvTitle.setText(tx.note);
            tvTitle.setTextSize(20);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(0xFF1A1C24);
            container.addView(tvTitle);

            // Category & Type Details
            TextView tvDetails = new TextView(getActivity());
            String sign = "expense".equalsIgnoreCase(tx.type) ? "-" : "+";
            int amountColor = "expense".equalsIgnoreCase(tx.type) ? 0xFFF44336 : 0xFF4CAF50;

            String typeStr = isKhmer ? ("expense".equalsIgnoreCase(tx.type) ? "ចំណាយ" : "ចំណូល") : tx.type.toUpperCase();
            String catStr = translateText(tx.category, isKhmer);

            String detailsTemplate = isKhmer ? "ប្រភេទ៖ %s\nប្រភេទចំណាយ៖ %s\nកាលបរិច្ឆេទ៖ %s" 
                                             : "Type: %s\nCategory: %s\nDate: %s";

            tvDetails.setText(String.format(Locale.US, detailsTemplate, typeStr, catStr, tx.date));
            tvDetails.setTextSize(14);
            tvDetails.setTextColor(0xFF9DA3B4);
            tvDetails.setLineSpacing(4, 1);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailsParams.setMargins(0, dpToPx(12), 0, 0);
            tvDetails.setLayoutParams(detailsParams);
            container.addView(tvDetails);

            // Amount
            TextView tvAmount = new TextView(getActivity());
            tvAmount.setText(sign + formatAmount(getActivity(), tx.amount));
            tvAmount.setTextSize(26);
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmount.setTextColor(amountColor);
            LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            amountParams.setMargins(0, dpToPx(16), 0, 0);
            tvAmount.setLayoutParams(amountParams);
            container.addView(tvAmount);

            scrollView.addView(container);
            builder.setView(scrollView);

            builder.setPositiveButton(isKhmer ? "បិទ" : "Close", null);
            builder.setNegativeButton(isKhmer ? "លុប" : "Delete", null);

            final AlertDialog dialog = builder.create();

            dialog.setOnShowListener(dialogInterface -> {
                // Delete transaction listener
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(isKhmer ? "លុបប្រតិបត្តិការ" : "Delete Transaction")
                            .setMessage(isKhmer ? "តើអ្នកប្រាកដជាចង់លុបប្រតិបត្តិការនេះមែនទេ?" : "Are you sure you want to delete this transaction?")
                            .setPositiveButton(isKhmer ? "បាទ/ចាស" : "Yes", (d, w) -> {
                                DatabaseHelper.getInstance(getActivity()).deleteTransaction(tx.id);
                                Toast.makeText(getActivity(), isKhmer ? "បានលុបប្រតិបត្តិការ" : "Transaction deleted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                if (rootView != null) {
                                    refreshData(rootView);
                                }
                            })
                            .setNegativeButton(isKhmer ? "ទេ" : "No", null)
                            .show();
                });
            });

            dialog.show();
        }

        public static int getCategoryIcon(String cat) {
            switch (cat) {
                case "Food": return R.drawable.ic_fork_knife;
                case "Travel": return R.drawable.ic_car;
                case "Shop": return R.drawable.ic_shopping_bag;
                case "Health": return R.drawable.ic_health;
                case "Bills": return R.drawable.ic_bills;
                case "Fun": return R.drawable.ic_fun;
                case "Study": return R.drawable.ic_study;
                case "Salary": return R.drawable.ic_wallet;
                case "Bonus": return R.drawable.ic_lightbulb;
                case "Freelance": return R.drawable.ic_laptop;
                case "Investment": return R.drawable.ic_trend_prediction;
                case "Savings": return R.drawable.ic_exchange;
                default: return R.drawable.ic_other;
            }
        }

        public static int getCategoryBgColor(String cat) {
            switch (cat) {
                case "Food": return 0xFFFFF3E0; // light_orange
                case "Travel": return 0xFFE3F2FD; // light_blue
                case "Shop": return 0xFFF3E5F5; // light_purple
                case "Health": return 0xFFFFEBEE; // light_red
                case "Bills": return 0xFFFFFDE7; // light_yellow
                case "Fun": return 0xFFE8F5E9; // light_green
                case "Study": return 0xFFE0F7FA; // light_cyan
                case "Salary": return 0xFFE3F2FD; // light_blue_icon_bg
                case "Bonus": return 0xFFFFFDE7; // light_yellow
                case "Freelance": return 0xFFE0F7FA; // light_cyan
                case "Investment": return 0xFFFFF3E0; // light_orange
                case "Savings": return 0xFFE8F5E9; // light_green
                default: return 0xFFEFEBE9; // light_brown
            }
        }

        public static int getCategoryIconTint(String cat) {
            switch (cat) {
                case "Food": return 0xFFEF6C00;
                case "Travel": return 0xFF0D47A1;
                case "Shop": return 0xFF7B1FA2;
                case "Health": return 0xFFC62828;
                case "Bills": return 0xFFF57F17;
                case "Fun": return 0xFF2E7D32;
                case "Study": return 0xFF00838F;
                case "Salary": return 0xFF0D47A1;
                case "Bonus": return 0xFFF57F17;
                case "Freelance": return 0xFF00838F;
                case "Investment": return 0xFFEF6C00;
                case "Savings": return 0xFF2E7D32;
                default: return 0xFF4E342E;
            }
        }

        private int dpToPx(int dp) {
            if (getContext() == null) return dp;
            return (int) (dp * getContext().getResources().getDisplayMetrics().density);
        }
    }

    // Budget Fragment Class (Spending Limits)
    // Budget Fragment Class (Spending Limits)
    public static class BudgetFragment extends Fragment {
        private View rootView;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_budget, container, false);
            
            rootView.findViewById(R.id.cardMonthlyProgress).setOnClickListener(v -> showSetBudgetDialog("monthly_budget_limit", "Monthly Budget", 1000.00f));
            rootView.findViewById(R.id.cardFoodBudget).setOnClickListener(v -> showSetBudgetDialog("food_budget_limit", "Food Category Budget", 300.00f));
            rootView.findViewById(R.id.cardShopBudget).setOnClickListener(v -> showSetBudgetDialog("shop_budget_limit", "Shopping Category Budget", 200.00f));

            return rootView;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (rootView != null) {
                refreshData(rootView);
            }
        }

        private void showSetBudgetDialog(final String prefKey, String title, float defaultValue) {
            if (getContext() == null) return;
            final android.content.SharedPreferences prefs = getContext().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            float currentLimit = prefs.getFloat(prefKey, defaultValue);

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Set " + title);

            LinearLayout container = new LinearLayout(getContext());
            container.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            container.setPadding(padding, padding, padding, padding);

            final android.widget.EditText input = new android.widget.EditText(getContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(Locale.US, "%.2f", currentLimit));
            input.setSelection(input.getText().length());
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String val = input.getText().toString().trim();
                try {
                    float newLimit = Float.parseFloat(val);
                    if (newLimit >= 0) {
                        prefs.edit().putFloat(prefKey, newLimit).apply();
                        refreshData(rootView);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        private void refreshData(View view) {
            if (getContext() == null) return;

            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getContext());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());

            // 1. Profile Avatar
            ImageView ivAvatar = view.findViewById(R.id.ivBudgetProfileAvatar);
            if (ivAvatar != null) {
                PlaceholderFragment.loadAvatar(authManager.getUserAvatarUrl(), ivAvatar);
            }

            // 2. Welcome Back Header
            TextView tvWelcome = view.findViewById(R.id.tvBudgetWelcome);
            if (tvWelcome != null) {
                String name = authManager.getUserFullName();
                if (name == null || name.trim().isEmpty()) {
                    String uEmail = authManager.getUserEmail();
                    name = "User";
                    if (uEmail != null && uEmail.contains("@")) {
                        name = uEmail.split("@")[0];
                        if (name.length() > 0) {
                            name = name.substring(0, 1).toUpperCase() + name.substring(1);
                        }
                    }
                }
                tvWelcome.setText("Hello, " + name);
            }

            // 3. Monthly Progress Card
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            double monthLimit = prefs.getFloat("monthly_budget_limit", 1000.00f);
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String startOfMonthStr = dbFormat.format(cal.getTime());
            double monthSpend = dbHelper.getExpenseSumSince(email, startOfMonthStr);

            TextView tvMonthlySpend = view.findViewById(R.id.tvBudgetMonthlySpend);
            TextView tvMonthlyLimit = view.findViewById(R.id.tvBudgetMonthlyLimit);
            TextView tvMonthlyLeft = view.findViewById(R.id.tvBudgetMonthlyLeft);
            View vProgressFill = view.findViewById(R.id.vBudgetMonthlyProgressFill);

            if (tvMonthlySpend != null) tvMonthlySpend.setText(formatAmount(getContext(), monthSpend, true));
            if (tvMonthlyLimit != null) tvMonthlyLimit.setText(" / " + formatAmount(getContext(), monthLimit, true));
            
            double left = monthLimit - monthSpend;
            if (tvMonthlyLeft != null) {
                if (left >= 0) {
                    tvMonthlyLeft.setText(formatAmount(getContext(), left, true) + " Left");
                } else {
                    tvMonthlyLeft.setText(formatAmount(getContext(), Math.abs(left), true) + " Over");
                }
            }

            if (vProgressFill != null) {
                int pct = monthLimit > 0 ? (int) Math.round((monthSpend / monthLimit) * 100.0) : 0;
                if (pct > 100) pct = 100;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vProgressFill.getLayoutParams();
                params.weight = pct;
                vProgressFill.setLayoutParams(params);
            }

            // 4. Category Budgets & Recurring Payments calculation
            List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);
            String monthPrefix = startOfMonthStr.substring(0, 7); // e.g. "2026-06"

            double foodSpend = 0.0;
            double shopSpend = 0.0;
            DatabaseHelper.Transaction lastRent = null;
            DatabaseHelper.Transaction lastNetflix = null;
            DatabaseHelper.Transaction otherBill = null;

            for (DatabaseHelper.Transaction tx : transactions) {
                if (tx.date.startsWith(monthPrefix)) {
                    if ("expense".equalsIgnoreCase(tx.type)) {
                        if ("Food".equals(tx.category)) {
                            foodSpend += tx.amount;
                        } else if ("Shop".equals(tx.category)) {
                            shopSpend += tx.amount;
                        }
                    }
                }

                // Analyze recurring payments
                if ("expense".equalsIgnoreCase(tx.type)) {
                    if (tx.note.toLowerCase().contains("rent")) {
                        if (lastRent == null || tx.date.compareTo(lastRent.date) > 0) {
                            lastRent = tx;
                        }
                    } else if (tx.note.toLowerCase().contains("netflix")) {
                        if (lastNetflix == null || tx.date.compareTo(lastNetflix.date) > 0) {
                            lastNetflix = tx;
                        }
                    } else if ("Bills".equals(tx.category)) {
                        if (otherBill == null || tx.date.compareTo(otherBill.date) > 0) {
                            otherBill = tx;
                        }
                    }
                }
            }

            // Render Food Category Card
            double foodLimit = prefs.getFloat("food_budget_limit", 300.00f);
            TextView tvFoodSpend = view.findViewById(R.id.tvBudgetFoodSpend);
            TextView tvFoodPct = view.findViewById(R.id.tvBudgetFoodPct);
            View vFoodFill = view.findViewById(R.id.vBudgetFoodProgressFill);
            if (tvFoodSpend != null) tvFoodSpend.setText(formatAmount(getContext(), foodSpend, true) + " / " + formatAmount(getContext(), foodLimit, true));
            int foodPct = foodLimit > 0 ? (int) Math.round((foodSpend / foodLimit) * 100.0) : 0;
            if (foodPct > 100) foodPct = 100;
            if (tvFoodPct != null) tvFoodPct.setText(foodPct + "%");
            if (vFoodFill != null) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vFoodFill.getLayoutParams();
                params.weight = foodPct;
                vFoodFill.setLayoutParams(params);
            }

            // Render Shop Category Card
            double shopLimit = prefs.getFloat("shop_budget_limit", 200.00f);
            TextView tvShopSpend = view.findViewById(R.id.tvBudgetShopSpend);
            TextView tvShopPct = view.findViewById(R.id.tvBudgetShopPct);
            View vShopFill = view.findViewById(R.id.vBudgetShopProgressFill);
            if (tvShopSpend != null) tvShopSpend.setText(formatAmount(getContext(), shopSpend, true) + " / " + formatAmount(getContext(), shopLimit, true));
            int shopPct = shopLimit > 0 ? (int) Math.round((shopSpend / shopLimit) * 100.0) : 0;
            if (shopPct > 100) shopPct = 100;
            if (tvShopPct != null) tvShopPct.setText(shopPct + "%");
            if (vShopFill != null) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vShopFill.getLayoutParams();
                params.weight = shopPct;
                vShopFill.setLayoutParams(params);
            }

            // 5. Laptop Saving Goals calculations
            // Net savings goal (target $1,000)
            Map<String, Double> balSummary = dbHelper.getBalanceSummary(email);
            double income = balSummary.containsKey("income") ? balSummary.get("income") : 0.0;
            double expense = balSummary.containsKey("expense") ? balSummary.get("expense") : 0.0;
            double accumulatedSavings = income - expense;
            if (accumulatedSavings < 0) accumulatedSavings = 0.0;

            double goalLimit = 1000.00;
            TextView tvGoalSpend = view.findViewById(R.id.tvBudgetGoalSpend);
            TextView tvGoalPct = view.findViewById(R.id.tvBudgetGoalPct);
            View vGoalFill = view.findViewById(R.id.vBudgetGoalProgressFill);

            if (tvGoalSpend != null) tvGoalSpend.setText(formatAmount(getContext(), accumulatedSavings, true) + " of " + formatAmount(getContext(), goalLimit, true));
            int goalPct = (int) Math.round((accumulatedSavings / goalLimit) * 100.0);
            if (goalPct > 100) goalPct = 100;
            if (tvGoalPct != null) tvGoalPct.setText(goalPct + "%");
            if (vGoalFill != null) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vGoalFill.getLayoutParams();
                params.weight = goalPct;
                vGoalFill.setLayoutParams(params);
            }

            // 6. Render Recurring Payments Card 1 (Rent)
            TextView tvRec1Title = view.findViewById(R.id.tvRec1Title);
            TextView tvRec1Sub = view.findViewById(R.id.tvRec1Sub);
            TextView tvRec1Amount = view.findViewById(R.id.tvRec1Amount);
            if (lastRent != null) {
                if (tvRec1Title != null) tvRec1Title.setText(lastRent.note);
                if (tvRec1Sub != null) tvRec1Sub.setText("LATEST • " + lastRent.date);
                if (tvRec1Amount != null) tvRec1Amount.setText("-" + formatAmount(getContext(), lastRent.amount));
            } else {
                if (tvRec1Title != null) tvRec1Title.setText("Rent");
                if (tvRec1Sub != null) tvRec1Sub.setText("MONTHLY • 1ST OF MONTH");
                if (tvRec1Amount != null) tvRec1Amount.setText("-" + formatAmount(getContext(), 1200.00));
            }

            // Render Recurring Payments Card 2 (Netflix or other bills)
            TextView tvRec2Title = view.findViewById(R.id.tvRec2Title);
            TextView tvRec2Sub = view.findViewById(R.id.tvRec2Sub);
            TextView tvRec2Amount = view.findViewById(R.id.tvRec2Amount);
            if (lastNetflix != null) {
                if (tvRec2Title != null) tvRec2Title.setText(lastNetflix.note);
                if (tvRec2Sub != null) tvRec2Sub.setText("LATEST • " + lastNetflix.date);
                if (tvRec2Amount != null) tvRec2Amount.setText("-" + formatAmount(getContext(), lastNetflix.amount));
            } else if (otherBill != null) {
                if (tvRec2Title != null) tvRec2Title.setText(otherBill.note);
                if (tvRec2Sub != null) tvRec2Sub.setText("LATEST • " + otherBill.date);
                if (tvRec2Amount != null) tvRec2Amount.setText("-" + formatAmount(getContext(), otherBill.amount));
            } else {
                if (tvRec2Title != null) tvRec2Title.setText("Netflix");
                if (tvRec2Sub != null) tvRec2Sub.setText("MONTHLY • 12TH OF MONTH");
                if (tvRec2Amount != null) tvRec2Amount.setText("-" + formatAmount(getContext(), 15.99));
            }
            translateView(getContext(), view);
        }
    }

    // Analysis Fragment Class
    public static class AnalysisFragment extends Fragment {
        private View rootView;
        private String currentFilter = "Monthly";

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_analysis, container, false);

            rootView.findViewById(R.id.btnAnalysisDaily).setOnClickListener(v -> setFilter("Daily"));
            rootView.findViewById(R.id.btnAnalysisWeekly).setOnClickListener(v -> setFilter("Weekly"));
            rootView.findViewById(R.id.btnAnalysisMonthly).setOnClickListener(v -> setFilter("Monthly"));
            rootView.findViewById(R.id.btnAnalysisYearly).setOnClickListener(v -> setFilter("Yearly"));

            return rootView;
        }

        private void setFilter(String filter) {
            currentFilter = filter;
            if (rootView != null) {
                updateTimeFilters(rootView);
                refreshData(rootView);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (rootView != null) {
                updateTimeFilters(rootView);
                refreshData(rootView);
            }
        }

        private void updateTimeFilters(View view) {
            TextView btnDaily = view.findViewById(R.id.btnAnalysisDaily);
            TextView btnWeekly = view.findViewById(R.id.btnAnalysisWeekly);
            TextView btnMonthly = view.findViewById(R.id.btnAnalysisMonthly);
            TextView btnYearly = view.findViewById(R.id.btnAnalysisYearly);

            if (btnDaily == null || btnWeekly == null || btnMonthly == null || btnYearly == null) return;

            btnDaily.setBackgroundResource("Daily".equals(currentFilter) ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            btnDaily.setTextColor("Daily".equals(currentFilter) ? 0xFFFFFFFF : 0xFF9DA3B4);

            btnWeekly.setBackgroundResource("Weekly".equals(currentFilter) ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            btnWeekly.setTextColor("Weekly".equals(currentFilter) ? 0xFFFFFFFF : 0xFF9DA3B4);

            btnMonthly.setBackgroundResource("Monthly".equals(currentFilter) ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            btnMonthly.setTextColor("Monthly".equals(currentFilter) ? 0xFFFFFFFF : 0xFF9DA3B4);

            btnYearly.setBackgroundResource("Yearly".equals(currentFilter) ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            btnYearly.setTextColor("Yearly".equals(currentFilter) ? 0xFFFFFFFF : 0xFF9DA3B4);
        }

        private void refreshData(View view) {
            if (getContext() == null) return;

            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getContext());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());

            // 1. Profile Avatar
            ImageView ivAvatar = view.findViewById(R.id.ivAnalysisProfileAvatar);
            if (ivAvatar != null) {
                PlaceholderFragment.loadAvatar(authManager.getUserAvatarUrl(), ivAvatar);
            }

            // 2. Set Timeframe Start Date
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar calendar = Calendar.getInstance();
            String todayStr = dbFormat.format(calendar.getTime());

            String thresholdDate = todayStr;
            int elapsedDays = 1;

            if ("Daily".equals(currentFilter)) {
                thresholdDate = todayStr;
                elapsedDays = 1;
            } else if ("Weekly".equals(currentFilter)) {
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                thresholdDate = dbFormat.format(calendar.getTime());
                Calendar now = Calendar.getInstance();
                elapsedDays = now.get(Calendar.DAY_OF_WEEK);
            } else if ("Monthly".equals(currentFilter)) {
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                thresholdDate = dbFormat.format(calendar.getTime());
                elapsedDays = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            } else if ("Yearly".equals(currentFilter)) {
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                thresholdDate = dbFormat.format(calendar.getTime());
                elapsedDays = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            }

            // 3. Filter Transactions & Compute Day/Category Spending
            List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);
            double filterTotalExpense = 0.0;
            double filterTotalIncome = 0.0;
            Map<String, Double> timeframeCatSpending = new java.util.HashMap<>();
            double[] daySpends = new double[8]; // index 1 = Sunday, ..., 7 = Saturday

            for (DatabaseHelper.Transaction tx : transactions) {
                if (tx.date.compareTo(thresholdDate) >= 0) {
                    if ("expense".equalsIgnoreCase(tx.type)) {
                        filterTotalExpense += tx.amount;
                        timeframeCatSpending.put(tx.category, timeframeCatSpending.getOrDefault(tx.category, 0.0) + tx.amount);

                        try {
                            java.util.Date dateObj = dbFormat.parse(tx.date);
                            if (dateObj != null) {
                                Calendar txCal = Calendar.getInstance();
                                txCal.setTime(dateObj);
                                int dayOfWeek = txCal.get(Calendar.DAY_OF_WEEK);
                                daySpends[dayOfWeek] += tx.amount;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        filterTotalIncome += tx.amount;
                    }
                }
            }

            // 4. Trend Sum
            double trendSum = filterTotalIncome - filterTotalExpense;
            TextView tvTrendSum = view.findViewById(R.id.tvAnalysisTrendSum);
            if (tvTrendSum != null) {
                if (trendSum >= 0) {
                    tvTrendSum.setText("+" + formatAmount(getContext(), trendSum));
                    tvTrendSum.setTextColor(0xFF4CAF50);
                } else {
                    tvTrendSum.setText("-" + formatAmount(getContext(), Math.abs(trendSum)));
                    tvTrendSum.setTextColor(0xFFF44336);
                }
            }

            // 5. Avg Daily Spend
            double avgDailySpend = elapsedDays > 0 ? (filterTotalExpense / elapsedDays) : 0.0;
            TextView tvAvgDailySpend = view.findViewById(R.id.tvAnalysisAvgDailySpend);
            if (tvAvgDailySpend != null) {
                tvAvgDailySpend.setText(formatAmount(getContext(), avgDailySpend));
            }

            // 6. Most Active Day Calculation
            int maxDayIndex = -1;
            double maxDaySum = 0.0;
            for (int i = 1; i <= 7; i++) {
                if (daySpends[i] > maxDaySum) {
                    maxDaySum = daySpends[i];
                    maxDayIndex = i;
                }
            }

            String[] weekDays = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            TextView tvActiveDay = view.findViewById(R.id.tvAnalysisActiveDay);
            TextView tvActiveDaySub = view.findViewById(R.id.tvAnalysisActiveDaySubtitle);
            if (tvActiveDay != null && tvActiveDaySub != null) {
                if (maxDayIndex != -1 && filterTotalExpense > 0) {
                    tvActiveDay.setText(weekDays[maxDayIndex]);
                    int pct = (int) Math.round((maxDaySum / filterTotalExpense) * 100.0);
                    tvActiveDaySub.setText(String.format(Locale.US, "%d%% of total spend", pct));
                } else {
                    tvActiveDay.setText("None");
                    tvActiveDaySub.setText("0% of total spend");
                }
            }

            // 7. Top Categories Calculations
            java.util.List<Map.Entry<String, Double>> sortedCategories = new java.util.ArrayList<>(timeframeCatSpending.entrySet());
            java.util.Collections.sort(sortedCategories, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

            // Find views for top 3 categories
            TextView tvCat1Title = view.findViewById(R.id.cat1_title);
            TextView tvCat2Title = view.findViewById(R.id.cat2_title);
            TextView tvCat3Title = view.findViewById(R.id.cat3_title);

            TextView tvCat1Amount = view.findViewById(R.id.tvCat1Amount);
            TextView tvCat2Amount = view.findViewById(R.id.tvCat2Amount);
            TextView tvCat3Amount = view.findViewById(R.id.tvCat3Amount);

            View vCat1ProgressFill = view.findViewById(R.id.vCat1ProgressFill);
            View vCat2ProgressFill = view.findViewById(R.id.vCat2ProgressFill);
            View vCat3ProgressFill = view.findViewById(R.id.vCat3ProgressFill);

            TextView tvDonutPct = view.findViewById(R.id.tvAnalysisDonutPct);
            TextView tvDonutCat = view.findViewById(R.id.tvAnalysisDonutCategory);

            TextView tvLegend1 = view.findViewById(R.id.tvAnalysisLegend1);
            TextView tvLegend2 = view.findViewById(R.id.tvAnalysisLegend2);
            TextView tvLegend3 = view.findViewById(R.id.tvAnalysisLegend3);

            int pct1 = 0, pct2 = 0, pct3 = 0;

            // Populate Category 1
            if (sortedCategories.size() > 0 && filterTotalExpense > 0) {
                Map.Entry<String, Double> entry1 = sortedCategories.get(0);
                pct1 = (int) Math.round((entry1.getValue() / filterTotalExpense) * 100);

                if (tvCat1Title != null) tvCat1Title.setText(entry1.getKey());
                if (tvCat1Amount != null) tvCat1Amount.setText(formatAmount(getContext(), entry1.getValue()));
                if (vCat1ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat1ProgressFill.getLayoutParams();
                    params.weight = pct1;
                    vCat1ProgressFill.setLayoutParams(params);
                }
                if (tvDonutPct != null) tvDonutPct.setText(pct1 + "%");
                if (tvDonutCat != null) tvDonutCat.setText(entry1.getKey());
                if (tvLegend1 != null) tvLegend1.setText(String.format(Locale.US, "%s (%d%%)", entry1.getKey(), pct1));
            } else {
                if (tvCat1Title != null) tvCat1Title.setText("No Data");
                if (tvCat1Amount != null) tvCat1Amount.setText(formatAmount(getContext(), 0.0));
                if (vCat1ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat1ProgressFill.getLayoutParams();
                    params.weight = 0;
                    vCat1ProgressFill.setLayoutParams(params);
                }
                if (tvDonutPct != null) tvDonutPct.setText("0%");
                if (tvDonutCat != null) tvDonutCat.setText("No Data");
                if (tvLegend1 != null) tvLegend1.setText("None (0%)");
            }

            // Populate Category 2
            if (sortedCategories.size() > 1 && filterTotalExpense > 0) {
                Map.Entry<String, Double> entry2 = sortedCategories.get(1);
                pct2 = (int) Math.round((entry2.getValue() / filterTotalExpense) * 100);

                if (tvCat2Title != null) tvCat2Title.setText(entry2.getKey());
                if (tvCat2Amount != null) tvCat2Amount.setText(formatAmount(getContext(), entry2.getValue()));
                if (vCat2ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat2ProgressFill.getLayoutParams();
                    params.weight = pct2;
                    vCat2ProgressFill.setLayoutParams(params);
                }
                if (tvLegend2 != null) tvLegend2.setText(String.format(Locale.US, "%s (%d%%)", entry2.getKey(), pct2));
            } else {
                if (tvCat2Title != null) tvCat2Title.setText("No Data");
                if (tvCat2Amount != null) tvCat2Amount.setText(formatAmount(getContext(), 0.0));
                if (vCat2ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat2ProgressFill.getLayoutParams();
                    params.weight = 0;
                    vCat2ProgressFill.setLayoutParams(params);
                }
                if (tvLegend2 != null) tvLegend2.setText("None (0%)");
            }

            // Populate Category 3
            if (sortedCategories.size() > 2 && filterTotalExpense > 0) {
                Map.Entry<String, Double> entry3 = sortedCategories.get(2);
                pct3 = (int) Math.round((entry3.getValue() / filterTotalExpense) * 100);

                if (tvCat3Title != null) tvCat3Title.setText(entry3.getKey());
                if (tvCat3Amount != null) tvCat3Amount.setText(formatAmount(getContext(), entry3.getValue()));
                if (vCat3ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat3ProgressFill.getLayoutParams();
                    params.weight = pct3;
                    vCat3ProgressFill.setLayoutParams(params);
                }
                if (tvLegend3 != null) tvLegend3.setText(String.format(Locale.US, "%s (%d%%)", entry3.getKey(), pct3));
            } else {
                if (tvCat3Title != null) tvCat3Title.setText("No Data");
                if (tvCat3Amount != null) tvCat3Amount.setText(formatAmount(getContext(), 0.0));
                if (vCat3ProgressFill != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vCat3ProgressFill.getLayoutParams();
                    params.weight = 0;
                    vCat3ProgressFill.setLayoutParams(params);
                }
                if (tvLegend3 != null) tvLegend3.setText("None (0%)");
            }

            // Update Donut Chart View slices
            DonutChartView donutChartView = view.findViewById(R.id.donutChartView);
            if (donutChartView != null) {
                java.util.List<DonutChartView.Slice> slices = new java.util.ArrayList<>();
                if (filterTotalExpense > 0) {
                    float sumPct = 0;
                    if (sortedCategories.size() > 0) {
                        float p1 = (float) ((sortedCategories.get(0).getValue() / filterTotalExpense) * 100f);
                        slices.add(new DonutChartView.Slice(p1, HomeFragment.getCategoryIconTint(sortedCategories.get(0).getKey())));
                        sumPct += p1;
                    }
                    if (sortedCategories.size() > 1) {
                        float p2 = (float) ((sortedCategories.get(1).getValue() / filterTotalExpense) * 100f);
                        slices.add(new DonutChartView.Slice(p2, HomeFragment.getCategoryIconTint(sortedCategories.get(1).getKey())));
                        sumPct += p2;
                    }
                    if (sortedCategories.size() > 2) {
                        float p3 = (float) ((sortedCategories.get(2).getValue() / filterTotalExpense) * 100f);
                        slices.add(new DonutChartView.Slice(p3, HomeFragment.getCategoryIconTint(sortedCategories.get(2).getKey())));
                        sumPct += p3;
                    }
                    if (100f - sumPct > 1f) {
                        slices.add(new DonutChartView.Slice(100f - sumPct, 0xFF4E342E));
                    }
                }
                donutChartView.setSlices(slices);
            }
            translateView(getContext(), view);
        }
    }

    public static class PlaceholderFragment extends Fragment {
        private static final String ARG_TITLE = "title";

        public static PlaceholderFragment newInstance(String title) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, title);
            fragment.setArguments(args);
            return fragment;
        }
        public static class ProfileFragment extends Fragment {
            @Nullable
            @Override
            public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
                View view = inflater.inflate(R.layout.fragment_profile, container, false);
                
                if (getActivity() != null) {
                    SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                    android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);

                    TextView tvProfileEmail = view.findViewById(R.id.tv_profile_email);
                    if (tvProfileEmail != null) {
                        tvProfileEmail.setText(authManager.getUserEmail());
                    }
                    
                    TextView tvProfileName = view.findViewById(R.id.tv_profile_name);
                    if (tvProfileName != null) {
                        String name = authManager.getUserFullName();
                        if (name == null || name.trim().isEmpty()) {
                            String email = authManager.getUserEmail();
                            if (email != null && email.contains("@")) {
                                name = email.split("@")[0];
                                if (name.length() > 0) {
                                    name = name.substring(0, 1).toUpperCase() + name.substring(1);
                                }
                            } else {
                                name = "User";
                            }
                        }
                        tvProfileName.setText(name);
                    }
                    
                    // Load avatars
                    ImageView ivTopAvatar = view.findViewById(R.id.iv_top_profile_avatar);
                    if (ivTopAvatar != null) {
                        PlaceholderFragment.loadAvatar(authManager.getUserAvatarUrl(), ivTopAvatar);
                    }
                    ImageView ivMainAvatar = view.findViewById(R.id.iv_main_profile_avatar);
                    if (ivMainAvatar != null) {
                        PlaceholderFragment.loadAvatar(authManager.getUserAvatarUrl(), ivMainAvatar);
                    }

                    // --- Dynamic Settings Bindings ---
                    // 1. Currency Listeners
                    View cardKHR = view.findViewById(R.id.cardCurrencyKHR);
                    if (cardKHR != null) {
                        cardKHR.setOnClickListener(v -> {
                            prefs.edit().putString("selected_currency", "KHR").apply();
                            updateProfileUISettings(view);
                        });
                    }
                    View cardUSD = view.findViewById(R.id.cardCurrencyUSD);
                    if (cardUSD != null) {
                        cardUSD.setOnClickListener(v -> {
                            prefs.edit().putString("selected_currency", "USD").apply();
                            updateProfileUISettings(view);
                        });
                    }

                    // 2. Exchange Rate Row
                    View layoutExchangeRate = view.findViewById(R.id.layoutExchangeRate);
                    if (layoutExchangeRate != null) {
                        layoutExchangeRate.setOnClickListener(v -> {
                            android.widget.LinearLayout layout = new android.widget.LinearLayout(getActivity());
                            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                            layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));

                            android.widget.TextView label = new android.widget.TextView(getActivity());
                            label.setText("1 USD is equal to:");
                            label.setTextColor(0xFF9DA3B4);
                            label.setTextSize(14);
                            label.setPadding(0, 0, 0, dpToPx(8));
                            layout.addView(label);

                            android.widget.LinearLayout row = new android.widget.LinearLayout(getActivity());
                            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                            android.widget.TextView prefix = new android.widget.TextView(getActivity());
                            prefix.setText("$1 USD = ");
                            prefix.setTextColor(0xFF1A1C24);
                            prefix.setTextSize(18);
                            prefix.setTypeface(null, android.graphics.Typeface.BOLD);
                            row.addView(prefix);

                            android.widget.EditText input = new android.widget.EditText(getActivity());
                            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                            float currentRate = prefs.getFloat("exchange_rate", 4100.0f);
                            input.setText(String.format(Locale.US, "%.0f", currentRate));
                            input.setTextSize(18);
                            input.setTypeface(null, android.graphics.Typeface.BOLD);
                            input.setTextColor(0xFF0A5296);
                            android.widget.LinearLayout.LayoutParams editParams = new android.widget.LinearLayout.LayoutParams(
                                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                            input.setLayoutParams(editParams);
                            row.addView(input);

                            android.widget.TextView suffix = new android.widget.TextView(getActivity());
                            suffix.setText(" KHR");
                            suffix.setTextColor(0xFF1A1C24);
                            suffix.setTextSize(18);
                            suffix.setTypeface(null, android.graphics.Typeface.BOLD);
                            row.addView(suffix);

                            layout.addView(row);

                            new AlertDialog.Builder(getActivity())
                                    .setTitle("Set Exchange Rate")
                                    .setView(layout)
                                    .setPositiveButton("Save", (dialog, which) -> {
                                        try {
                                            float newRate = Float.parseFloat(input.getText().toString());
                                            if (newRate > 0) {
                                                prefs.edit().putFloat("exchange_rate", newRate).apply();
                                                updateProfileUISettings(view);
                                            }
                                        } catch (Exception e) {}
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        });
                    }

                    // 3. Language Listeners
                    View cardLangEN = view.findViewById(R.id.cardLangEN);
                    if (cardLangEN != null) {
                        cardLangEN.setOnClickListener(v -> {
                            prefs.edit().putString("selected_language", "EN").apply();
                            updateProfileUISettings(view);
                        });
                    }
                    View cardLangKH = view.findViewById(R.id.cardLangKH);
                    if (cardLangKH != null) {
                        cardLangKH.setOnClickListener(v -> {
                            prefs.edit().putString("selected_language", "KH").apply();
                            updateProfileUISettings(view);
                        });
                    }

                    // 4. Notification Switch Listener
                    View layoutNotifToggle = view.findViewById(R.id.layoutNotifToggle);
                    if (layoutNotifToggle != null) {
                        layoutNotifToggle.setOnClickListener(v -> {
                            boolean current = prefs.getBoolean("notif_enabled", true);
                            prefs.edit().putBoolean("notif_enabled", !current).apply();
                            updateProfileUISettings(view);
                            checkBudgetAndNotify(getActivity());
                        });
                    }

                    // Initial UI State drawing
                    updateProfileUISettings(view);
                }

                View btnSeedData = view.findViewById(R.id.btn_seed_data);
                if (btnSeedData != null) {
                    btnSeedData.setOnClickListener(v -> {
                        if (getActivity() != null) {
                            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
                            String lang = prefs.getString("selected_language", "EN");
                            boolean isKhmer = "KH".equals(lang);

                            new AlertDialog.Builder(getActivity())
                                    .setTitle(isKhmer ? "ទិន្នន័យគំរូ" : "Seed Sample Data")
                                    .setMessage(isKhmer ? "តើអ្នកចង់លុបទិន្នន័យចាស់ និងបង្កើតទិន្នន័យគំរូមែនទេ?" : "Would you like to clear existing transactions and seed sample data?")
                                    .setPositiveButton(isKhmer ? "បាទ/ចាស" : "Yes", (dialog, which) -> {
                                        DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
                                        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                                        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
                                        
                                        dbHelper.getWritableDatabase().delete(
                                                DatabaseHelper.TABLE_TRANSACTIONS,
                                                DatabaseHelper.COL_USER_EMAIL + "=?",
                                                new String[]{email}
                                        );

                                        ((Main_Activity) getActivity()).seedSampleData(dbHelper, email);
                                        
                                        Toast.makeText(getActivity(), isKhmer ? "បានបង្កើតទិន្នន័យគំរូរួចរាល់" : "Sample data seeded successfully", Toast.LENGTH_SHORT).show();
                                        
                                        updateProfileUISettings(view);
                                        checkBudgetAndNotify(getActivity());
                                    })
                                    .setNegativeButton(isKhmer ? "ទេ" : "No", null)
                                    .show();
                        }
                    });
                }

                View btnLogout = view.findViewById(R.id.btn_logout);
                if (btnLogout != null) {
                    btnLogout.setOnClickListener(v -> {
                        if (getActivity() != null) {
                            SupabaseAuthManager.getInstance(getActivity()).logout();
                            Intent intent = new Intent(getActivity(), LoginActivity.class);
                            startActivity(intent);
                            getActivity().finish();
                        }
                    });
                }
                
                return view;
            }

            private void updateProfileUISettings(View view) {
                if (getContext() == null) return;
                android.content.SharedPreferences prefs = getContext().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
                float density = getResources().getDisplayMetrics().density;

                // 1. Currency Display
                String currency = prefs.getString("selected_currency", "USD");
                com.google.android.material.card.MaterialCardView cardKHR = view.findViewById(R.id.cardCurrencyKHR);
                com.google.android.material.card.MaterialCardView cardUSD = view.findViewById(R.id.cardCurrencyUSD);
                TextView tvKHR = view.findViewById(R.id.tvCurrencyKHR);
                TextView tvUSD = view.findViewById(R.id.tvCurrencyUSD);

                if (cardKHR != null && cardUSD != null && tvKHR != null && tvUSD != null) {
                    if ("KHR".equals(currency)) {
                        cardKHR.setCardBackgroundColor(0xFFFFFFFF);
                        cardKHR.setCardElevation(1 * density);
                        tvKHR.setTextColor(0xFF0A5296);
                        
                        cardUSD.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cardUSD.setCardElevation(0);
                        tvUSD.setTextColor(0xFF9DA3B4);
                    } else {
                        cardUSD.setCardBackgroundColor(0xFFFFFFFF);
                        cardUSD.setCardElevation(1 * density);
                        tvUSD.setTextColor(0xFF0A5296);
                        
                        cardKHR.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cardKHR.setCardElevation(0);
                        tvKHR.setTextColor(0xFF9DA3B4);
                    }
                }

                // 2. Exchange Rate Display
                float rate = prefs.getFloat("exchange_rate", 4100.0f);
                TextView tvExchangeRateVal = view.findViewById(R.id.tvExchangeRateVal);
                if (tvExchangeRateVal != null) {
                    tvExchangeRateVal.setText(String.format(Locale.US, "1 USD = %,.0f KHR", rate));
                }

                // 3. Language Display
                String lang = prefs.getString("selected_language", "EN");
                com.google.android.material.card.MaterialCardView cardEN = view.findViewById(R.id.cardLangEN);
                com.google.android.material.card.MaterialCardView cardKH = view.findViewById(R.id.cardLangKH);
                TextView tvLangEN = view.findViewById(R.id.tvLangEN);
                TextView tvLangKH = view.findViewById(R.id.tvLangKH);
                TextView tvProfileLanguageVal = view.findViewById(R.id.tvProfileLanguageVal);

                if (cardEN != null && cardKH != null && tvLangEN != null && tvLangKH != null) {
                    if ("KH".equals(lang)) {
                        cardKH.setCardBackgroundColor(0xFFFFFFFF);
                        cardKH.setCardElevation(1 * density);
                        tvLangKH.setTextColor(0xFF0A5296);
                        
                        cardEN.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cardEN.setCardElevation(0);
                        tvLangEN.setTextColor(0xFF9DA3B4);
                        if (tvProfileLanguageVal != null) tvProfileLanguageVal.setText("Khmer");
                    } else {
                        cardEN.setCardBackgroundColor(0xFFFFFFFF);
                        cardEN.setCardElevation(1 * density);
                        tvLangEN.setTextColor(0xFF0A5296);
                        
                        cardKH.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cardKH.setCardElevation(0);
                        tvLangKH.setTextColor(0xFF9DA3B4);
                        if (tvProfileLanguageVal != null) tvProfileLanguageVal.setText("English");
                    }
                }

                // 4. Notifications Display
                boolean notifEnabled = prefs.getBoolean("notif_enabled", true);
                View switchNotifBg = view.findViewById(R.id.layoutNotifToggle);
                View switchNotifThumb = view.findViewById(R.id.switchNotifThumb);
                View switchNotifThumbIcon = view.findViewById(R.id.switchNotifThumbIcon);

                if (switchNotifBg != null && switchNotifThumb != null) {
                    if (notifEnabled) {
                        switchNotifBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0A5296));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchNotifThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        switchNotifThumb.setLayoutParams(params);
                        if (switchNotifThumbIcon != null) switchNotifThumbIcon.setVisibility(View.VISIBLE);
                    } else {
                        switchNotifBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD0D5DD));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchNotifThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                        params.addRule(RelativeLayout.ALIGN_PARENT_START);
                        switchNotifThumb.setLayoutParams(params);
                        if (switchNotifThumbIcon != null) switchNotifThumbIcon.setVisibility(View.INVISIBLE);
                    }
                }
                translateView(getContext(), view);
            }

            private int dpToPx(int dp) {
                if (getContext() == null) return dp;
                return (int) (dp * getResources().getDisplayMetrics().density);
            }
        }

    public static void loadAvatar(String url, ImageView imageView) {
        if (url == null || url.trim().isEmpty() || imageView == null) return;
        java.util.concurrent.Executor executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                java.io.InputStream in = new java.net.URL(url).openStream();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                handler.post(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            TextView textView = new TextView(getContext());
            if (getArguments() != null) {
                textView.setText(getArguments().getString(ARG_TITLE));
            }
            return textView;
        }
    }

    public static void translateView(android.content.Context context, View view) {
        if (view == null || context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        String lang = prefs.getString("selected_language", "EN");
        boolean isKhmer = "KH".equals(lang);

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv instanceof android.widget.EditText) {
                android.widget.EditText et = (android.widget.EditText) tv;
                if (et.getHint() != null) {
                    String originalHint = et.getHint().toString();
                    String translatedHint = translateText(originalHint, isKhmer);
                    et.setHint(translatedHint);
                }
            } else {
                String originalText = tv.getText().toString();
                String translatedText = translateText(originalText, isKhmer);
                tv.setText(translatedText);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                translateView(context, vg.getChildAt(i));
            }
        }
    }

    private static String translateText(String original, boolean isKhmer) {
        if (original == null) return "";
        String clean = original.trim();
        if (isKhmer) {
            switch (clean) {
                // Home Screen & Categories
                case "CURRENT BALANCE": return "សមតុល្យបច្ចុប្បន្ន";
                case "Income": return "ចំណូល";
                case "Expense": return "ចំណាយ";
                case "TODAY": return "ថ្ងៃនេះ";
                case "THIS WEEK": return "សប្តាហ៍នេះ";
                case "THIS MONTH": return "ខែនេះ";
                case "SAVINGS RATE": return "អត្រាសន្សំ";
                case "Categories": return "ប្រភេទ";
                case "MANAGE": return "គ្រប់គ្រង";
                case "Recent Transactions": return "ប្រតិបត្តិការថ្មីៗ";
                case "VIEW ALL": return "មើលទាំងអស់";
                case "Search transactions...": return "ស្វែងរកប្រតិបត្តិការ...";
                case "No transactions recorded yet.": return "មិនទាន់មានប្រតិបត្តិការណាមួយឡើយ។";
                
                // Categories Translation
                case "Food": return "អាហារ";
                case "Travel": return "ការធ្វើដំណើរ";
                case "Shop": return "ទិញឥវ៉ាន់";
                case "Health": return "សុខភាព";
                case "Bills": return "វិក្កយបត្រ";
                case "Fun": return "ការកម្សាន្ត";
                case "Study": return "ការសិក្សា";
                case "Salary": return "ប្រាក់ខែ";
                case "Bonus": return "ប្រាក់លើកទឹកចិត្ត";
                case "Freelance": return "ការងារក្រៅម៉ោង";
                case "Investment": return "ការវិនិយោគ";
                case "Savings": return "ប្រាក់សន្សំ";
                case "Other": return "ផ្សេងៗ";
                case "Others": return "ផ្សេងៗ";

                // Greetings
                case "Good Morning": return "អរុណសួស្តី";
                case "Good Afternoon": return "ទិវាសួស្តី";
                case "Good Evening": return "សាយ័នសួស្តី";
                case "Good Night": return "រាត្រីសួស្តី";
                
                // Analysis Screen
                case "Analysis": return "ការវិភាគ";
                case "Daily": return "ប្រចាំថ្ងៃ";
                case "Weekly": return "ប្រចាំសប្តាហ៍";
                case "Monthly": return "ប្រចាំខែ";
                case "Yearly": return "ប្រចាំឆ្នាំ";
                case "TOTAL SPENDING": return "ការចំណាយសរុប";
                case "AVERAGE DAILY SPEND": return "ចំណាយមធ្យមប្រចាំថ្ងៃ";
                case "MOST ACTIVE DAY": return "ថ្ងៃចំណាយច្រើនបំផុត";
                case "Category Breakdown": return "ការបែងចែកប្រភេទចំណាយ";
                case "No Data": return "គ្មានទិន្នន័យ";
                
                // Budget Screen & Saving goals
                case "Budgets": return "កញ្ចប់ថវិកា";
                case "Monthly Progress": return "វឌ្ឍនភាពប្រចាំខែ";
                case "Left": return "នៅសល់";
                case "Over": return "លើស";
                case "Category Budgets": return "កញ្ចប់ថវិកាប្រភេទចំណាយ";
                case "Savings Goals": return "គោលដៅសន្សំ";
                case "Recurring Payments": return "ការទូទាត់ប្រចាំកាលកំណត់";
                case "Rent": return "ថ្លៃជួលផ្ទះ";
                case "Netflix": return "កម្មវិធី Netflix";
                case "Laptop Saving": return "ការសន្សំទិញឡេបថប";
                
                // Recurring indicators
                case "LATEST": return "ថ្មីៗបំផុត";
                case "MONTHLY": return "ប្រចាំខែ";
                case "1ST OF MONTH": return "ថ្ងៃទី១ នៃខែ";
                case "12TH OF MONTH": return "ថ្ងៃទី១២ នៃខែ";
                case "MONTHLY • 1ST OF MONTH": return "ប្រចាំខែ • ថ្ងៃទី១ នៃខែ";
                case "MONTHLY • 12TH OF MONTH": return "ប្រចាំខែ • ថ្ងៃទី១២ នៃខែ";
                
                // Profile Screen
                case "Profile & Settings": return "ប្រវត្តិរូប និង ការកំណត់";
                case "Default Currency": return "រូបិយប័ណ្ណលំនាំដើម";
                case "Exchange Rate": return "អត្រាប្តូរប្រាក់";
                case "Language": return "ភាសា";
                case "Budget Alerts": return "ការជូនដំណឹងកញ្ចប់ថវិកា";
                case "When approaching limits": return "នៅពេលជិតអស់កំណត់";
                case "Logout": return "ចាកចេញ";
                case "Set Exchange Rate": return "កំណត់អត្រាប្តូរប្រាក់";
                case "1 USD is equal to:": return "១ដុល្លារគឺស្មើនឹង៖";
            }
        } else {
            switch (clean) {
                // Home Screen & Categories
                case "សមតុល្យបច្ចុប្បន្ន": return "CURRENT BALANCE";
                case "ចំណូល": return "Income";
                case "ចំណាយ": return "Expense";
                case "ថ្ងៃនេះ": return "TODAY";
                case "សប្តាហ៍នេះ": return "THIS WEEK";
                case "ខែនេះ": return "THIS MONTH";
                case "អត្រាសន្សំ": return "SAVINGS RATE";
                case "ប្រភេទ": return "Categories";
                case "គ្រប់គ្រង": return "MANAGE";
                case "ប្រតិបត្តិការថ្មីៗ": return "Recent Transactions";
                case "មើលទាំងអស់": return "VIEW ALL";
                case "ស្វែងរកប្រតិបត្តិការ...": return "Search transactions...";
                case "មិនទាន់មានប្រតិបត្តិការណាមួយឡើយ។": return "No transactions recorded yet.";
                
                // Categories
                case "អាហារ": return "Food";
                case "ការធ្វើដំណើរ": return "Travel";
                case "ទិញឥវ៉ាន់": return "Shop";
                case "សុខភាព": return "Health";
                case "វិក្កយបត្រ": return "Bills";
                case "ការកម្សាន្ត": return "Fun";
                case "ការសិក្សា": return "Study";
                case "ប្រាក់ខែ": return "Salary";
                case "ប្រាក់លើកទឹកចិត្ត": return "Bonus";
                case "ការងារក្រៅម៉ោង": return "Freelance";
                case "ការវិនិយោគ": return "Investment";
                case "ប្រាក់សន្សំ": return "Savings";
                case "ផ្សេងៗ": return "Other";

                // Greetings
                case "អរុណសួស្តី": return "Good Morning";
                case "ទិវាសួស្តី": return "Good Afternoon";
                case "សាយ័នសួស្តី": return "Good Evening";
                case "រាត្រីសួស្តី": return "Good Night";
                
                // Analysis Screen
                case "ការវិភាគ": return "Analysis";
                case "ប្រចាំថ្ងៃ": return "Daily";
                case "ប្រចាំសប្តាហ៍": return "Weekly";
                case "ប្រចាំខែ": return "Monthly";
                case "ប្រចាំឆ្នាំ": return "Yearly";
                case "ការចំណាយសរុប": return "TOTAL SPENDING";
                case "ចំណាយមធ្យមប្រចាំថ្ងៃ": return "AVERAGE DAILY SPEND";
                case "ថ្ងៃចំណាយច្រើនបំផុត": return "MOST ACTIVE DAY";
                case "ការបែងចែកប្រភេទចំណាយ": return "Category Breakdown";
                case "គ្មានទិន្នន័យ": return "No Data";
                
                // Budget Screen & Saving goals
                case "កញ្ចប់ថវិកា": return "Budgets";
                case "វឌ្ឍនភាពប្រចាំខែ": return "Monthly Progress";
                case "នៅសល់": return "Left";
                case "លើស": return "Over";
                case "កញ្ចប់ថវិកាប្រភេទចំណាយ": return "Category Budgets";
                case "គោលដៅសន្សំ": return "Savings Goals";
                case "ការទូទាត់ប្រចាំកាលកំណត់": return "Recurring Payments";
                case "ថ្លៃជួលផ្ទះ": return "Rent";
                case "កម្មវិធី Netflix": return "Netflix";
                case "ការសន្សំទិញឡេបថប": return "Laptop Saving";
                
                // Recurring indicators
                case "ថ្មីៗបំផុត": return "LATEST";
                case "ថ្ងៃទី១ នៃខែ": return "1ST OF MONTH";
                case "ថ្ងៃទី១២ នៃខែ": return "12TH OF MONTH";
                case "ប្រចាំខែ • ថ្ងៃទី១ នៃខែ": return "MONTHLY • 1ST OF MONTH";
                case "ប្រចាំខែ • ថ្ងៃទី១២ នៃខែ": return "MONTHLY • 12TH OF MONTH";
                
                // Profile Screen
                case "ប្រវត្តិរូប និង ការកំណត់": return "Profile & Settings";
                case "រូបិយប័ណ្ណលំនាំដើម": return "Default Currency";
                case "អត្រាប្តូរប្រាក់": return "Exchange Rate";
                case "ភាសា": return "Language";
                case "ការជូនដំណឹងកញ្ចប់ថវិកា": return "Budget Alerts";
                case "នៅពេលជិតអស់កំណត់": return "When approaching limits";
                case "ចាកចេញ": return "Logout";
                case "កំណត់អត្រាប្តូរប្រាក់": return "Set Exchange Rate";
                case "១ដុល្លារគឺស្មើនឹង៖": return "1 USD is equal to:";
            }
        }
        return original;
    }

    private void seedSampleData(DatabaseHelper dbHelper, String email) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        // Current date string
        String today = sdf.format(cal.getTime());

        // Yesterday
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = sdf.format(cal.getTime());

        // 3 days ago
        cal.add(Calendar.DAY_OF_YEAR, -2);
        String threeDaysAgo = sdf.format(cal.getTime());

        // 5 days ago
        cal.add(Calendar.DAY_OF_YEAR, -2);
        String fiveDaysAgo = sdf.format(cal.getTime());

        // 10 days ago
        cal.add(Calendar.DAY_OF_YEAR, -5);
        String tenDaysAgo = sdf.format(cal.getTime());

        // 1st of month
        Calendar firstOfMonthCal = Calendar.getInstance();
        firstOfMonthCal.set(Calendar.DAY_OF_MONTH, 1);
        String firstOfMonth = sdf.format(firstOfMonthCal.getTime());

        // 12th of month
        Calendar twelfthOfMonthCal = Calendar.getInstance();
        twelfthOfMonthCal.set(Calendar.DAY_OF_MONTH, 12);
        String twelfthOfMonth = sdf.format(twelfthOfMonthCal.getTime());

        // 1. Income: Monthly Salary ($2500)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "income",
            2500.00,
            "Salary",
            firstOfMonth,
            "Monthly Salary",
            null,
            0
        ));

        // 2. Expense: Rent ($600)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            600.00,
            "Bills",
            firstOfMonth,
            "Apartment Rent",
            null,
            0
        ));

        // 3. Expense: Netflix ($15.99)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            15.99,
            "Fun",
            twelfthOfMonth,
            "Netflix Subscription",
            null,
            0
        ));

        // 4. Expense: Grocery shopping ($85.50)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            85.50,
            "Food",
            tenDaysAgo,
            "Whole Foods Groceries",
            null,
            0
        ));

        // 5. Expense: Gasoline fuel ($45.00)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            45.00,
            "Travel",
            fiveDaysAgo,
            "Shell Petrol Station",
            null,
            0
        ));

        // 6. Expense: Dinner with friends ($120.00)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            120.00,
            "Fun",
            threeDaysAgo,
            "Barbecue Dinner",
            null,
            0
        ));

        // 7. Expense: Clothes shopping ($150.00)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            150.00,
            "Shop",
            yesterday,
            "Zara Shopping mall",
            null,
            0
        ));

        // 8. Expense: Morning coffee ($4.50)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "expense",
            4.50,
            "Food",
            today,
            "Starbucks Coffee",
            null,
            0
        ));

        // 9. Income: Freelance Project ($350.00)
        dbHelper.insertTransaction(new DatabaseHelper.Transaction(
            java.util.UUID.randomUUID().toString(),
            email,
            "income",
            350.00,
            "Freelance",
            today,
            "Mobile App UI Design",
            null,
            0
        ));
    }
}
