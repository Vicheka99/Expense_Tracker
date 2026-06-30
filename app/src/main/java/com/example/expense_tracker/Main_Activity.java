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
        checkBillsAndNotify(this);
    }

    public static void checkBillsAndNotify(android.content.Context context) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        boolean remindersEnabled = prefs.getBoolean("bill_reminders_enabled", true);
        if (!remindersEnabled) return;

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(context);
        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

        List<DatabaseHelper.RecurringPayment> recs = dbHelper.getRecurringPayments(email);
        if (recs.isEmpty()) return;

        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = dbFormat.format(new java.util.Date());

        int dueCount = 0;
        for (DatabaseHelper.RecurringPayment rec : recs) {
            if (rec.dueDate != null && rec.dueDate.compareTo(todayStr) <= 0) {
                dueCount++;
            }
        }

        if (dueCount > 0) {
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            String channelId = "bill_reminders_channel";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    "Bill Reminders",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                );
                notificationManager.createNotificationChannel(channel);
            }

            boolean isKhmer = "KH".equals(prefs.getString("selected_language", "EN"));
            String title = isKhmer ? "ការរំលឹកវិក្កយបត្រ!" : "Bill Reminder!";
            String text = isKhmer 
                ? "អ្នកមានវិក្កយបត្រចំនួន " + dueCount + " ត្រូវបង់ ឬហួសកាលកំណត់!"
                : "You have " + dueCount + " bill(s) due today or overdue!";

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

            notificationManager.notify(102, builder.build());
        }
    }

    // --- Category helper methods (accessible from activity-level methods) ---
    public static int getCategoryIcon(String category) {
        switch (category) {
            case "Food": return R.drawable.ic_fork_knife;
            case "Shop": return R.drawable.ic_shopping_bag;
            case "Travel": return R.drawable.ic_car;
            case "Health": return R.drawable.ic_health;
            case "Bills": return R.drawable.ic_bills;
            case "Fun": return R.drawable.ic_fun;
            case "Study": return R.drawable.ic_study;
            default: return R.drawable.ic_other;
        }
    }

    public static int getCategoryColor(String category) {
        switch (category) {
            case "Food": return 0xFFFFF3E0;
            case "Shop": return 0xFFE3F2FD;
            case "Travel": return 0xFFE8F5E9;
            case "Health": return 0xFFFCE4EC;
            case "Bills": return 0xFFF3E5F5;
            case "Fun": return 0xFFFFFDE7;
            case "Study": return 0xFFE0F7FA;
            default: return 0xFFF5F5F5;
        }
    }

    public static int getCategoryIconTint(String category) {
        switch (category) {
            case "Food": return 0xFFFF9800;
            case "Shop": return 0xFF1976D2;
            case "Travel": return 0xFF4CAF50;
            case "Health": return 0xFFE91E63;
            case "Bills": return 0xFF9C27B0;
            case "Fun": return 0xFFFFC107;
            case "Study": return 0xFF00BCD4;
            default: return 0xFF9E9E9E;
        }
    }

    public void updateNotificationBadge(View rootView) {
        if (rootView == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(this);
        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String todayStr = dbFormat.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String startOfMonthStr = dbFormat.format(cal.getTime());
        String monthPrefix = startOfMonthStr.substring(0, 7);

        double monthLimit = prefs.getFloat("monthly_budget_limit", 1000.00f);
        double monthSpend = dbHelper.getExpenseSumSince(email, startOfMonthStr);

        boolean hasMonthAlert = monthLimit > 0 && monthSpend >= 0.80 * monthLimit;
        
        List<DatabaseHelper.CategoryBudget> cbList = dbHelper.getCategoryBudgets(email);
        List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);
        List<DatabaseHelper.RecurringPayment> recs = dbHelper.getRecurringPayments(email);

        int totalAlerts = (hasMonthAlert ? 1 : 0) + getCategoryAlertsCount(cbList, transactions, monthPrefix);
        int totalBills = recs.size();
        int grandTotal = totalAlerts + totalBills;

        int lastSeenCount = prefs.getInt("last_seen_notif_count", 0);
        
        View badgeCard = rootView.findViewById(R.id.badgeNotificationCountCard);
        TextView tvCount = rootView.findViewById(R.id.tvNotificationCount);
        
        if (badgeCard != null && tvCount != null) {
            if (grandTotal > 0 && grandTotal != lastSeenCount) {
                tvCount.setText(String.valueOf(grandTotal));
                badgeCard.setVisibility(View.VISIBLE);
            } else {
                badgeCard.setVisibility(View.GONE);
            }
        }
    }

    private int getCategoryAlertsCount(List<DatabaseHelper.CategoryBudget> cbList, List<DatabaseHelper.Transaction> transactions, String monthPrefix) {
        int count = 0;
        for (DatabaseHelper.CategoryBudget cb : cbList) {
            if (cb.budgetLimit <= 0) continue;
            double catSpend = 0;
            for (DatabaseHelper.Transaction tx : transactions) {
                if (tx.date.startsWith(monthPrefix) && "expense".equalsIgnoreCase(tx.type)) {
                    if (cb.category.equalsIgnoreCase(tx.category)) catSpend += tx.amount;
                    else if ("Shop".equalsIgnoreCase(cb.category) && "Shopping".equalsIgnoreCase(tx.category)) catSpend += tx.amount;
                }
            }
            if (catSpend / cb.budgetLimit >= 0.80) {
                count++;
            }
        }
        return count;
    }

    public void showNotificationsDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
        String lang = prefs.getString("selected_language", "EN");
        boolean isKhmer = "KH".equals(lang);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(this);
        String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String todayStr = dbFormat.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String startOfMonthStr = dbFormat.format(cal.getTime());
        String monthPrefix = startOfMonthStr.substring(0, 7);

        double monthLimit = prefs.getFloat("monthly_budget_limit", 1000.00f);
        double monthSpend = dbHelper.getExpenseSumSince(email, startOfMonthStr);

        List<DatabaseHelper.CategoryBudget> cbList = dbHelper.getCategoryBudgets(email);
        List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);
        List<DatabaseHelper.RecurringPayment> recs = dbHelper.getRecurringPayments(email);

        java.util.List<DatabaseHelper.Transaction> history = new java.util.ArrayList<>();
        for (DatabaseHelper.Transaction tx : transactions) {
            if (tx.note != null && tx.note.contains("(Subscription)")) {
                history.add(tx);
            }
        }
        java.util.Collections.sort(history, (a, b) -> b.date.compareTo(a.date));

        // Per-category budget alerts (≥80%)
        java.util.List<String[]> catAlerts = new java.util.ArrayList<>();
        for (DatabaseHelper.CategoryBudget cb : cbList) {
            if (cb.budgetLimit <= 0) continue;
            double catSpend = 0;
            for (DatabaseHelper.Transaction tx : transactions) {
                if (tx.date.startsWith(monthPrefix) && "expense".equalsIgnoreCase(tx.type)) {
                    if (cb.category.equalsIgnoreCase(tx.category)) catSpend += tx.amount;
                    else if ("Shop".equalsIgnoreCase(cb.category) && "Shopping".equalsIgnoreCase(tx.category)) catSpend += tx.amount;
                }
            }
            double pct = catSpend / cb.budgetLimit;
            if (pct >= 0.80) {
                int pctInt = (int) Math.min(Math.round(pct * 100), 100);
                catAlerts.add(new String[]{ cb.category, formatAmount(this, catSpend),
                    formatAmount(this, cb.budgetLimit), String.valueOf(pctInt), pct >= 1.0 ? "1" : "0" });
            }
        }

        boolean hasMonthAlert = monthLimit > 0 && monthSpend >= 0.80 * monthLimit;

        // All recurring — classify urgency
        Calendar deadline7 = Calendar.getInstance();
        deadline7.add(Calendar.DAY_OF_YEAR, 7);
        String deadline7Str = dbFormat.format(deadline7.getTime());

        float d = getResources().getDisplayMetrics().density;
        int p16 = (int)(16*d), p12 = (int)(12*d);

        // Root scroll
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);

        android.graphics.drawable.GradientDrawable dialogBg = new android.graphics.drawable.GradientDrawable();
        dialogBg.setColor(0xFFF5F8FC);
        dialogBg.setCornerRadius(20 * d);
        scrollView.setBackground(dialogBg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ══════════════════════════════════
        // HEADER — gradient dark blue
        // ══════════════════════════════════
        android.graphics.drawable.GradientDrawable headerBg = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ 0xFF0D47A1, 0xFF1565C0 }
        );
        headerBg.setCornerRadii(new float[]{ 20*d, 20*d, 20*d, 20*d, 0, 0, 0, 0 }); // round top corners matching dialog shape

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(p16, (int)(22*d), p16, (int)(18*d));
        header.setBackground(headerBg);

        // Top row: bell + title + badge
        LinearLayout hTop = new LinearLayout(this);
        hTop.setOrientation(LinearLayout.HORIZONTAL);
        hTop.setGravity(Gravity.CENTER_VERTICAL);

        ImageView bellIv = new ImageView(this);
        LinearLayout.LayoutParams bellP = new LinearLayout.LayoutParams((int)(24*d),(int)(24*d));
        bellP.rightMargin = (int)(10*d);
        bellIv.setLayoutParams(bellP);
        bellIv.setImageResource(R.drawable.ic_notification);
        bellIv.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        hTop.addView(bellIv);

        TextView tvHTitle = new TextView(this);
        tvHTitle.setText(isKhmer ? "ការជូនដំណឹង" : "Notifications");
        tvHTitle.setTextColor(0xFFFFFFFF);
        tvHTitle.setTextSize(18);
        tvHTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hTitleP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvHTitle.setLayoutParams(hTitleP);
        hTop.addView(tvHTitle);

        // Total badge
        int totalAlerts = (hasMonthAlert ? 1 : 0) + catAlerts.size();
        int totalBills = recs.size();
        int grandTotal = totalAlerts + totalBills;
        prefs.edit().putInt("last_seen_notif_count", grandTotal).apply();
        View activeBadge = findViewById(R.id.badgeNotificationCountCard);
        if (activeBadge != null) {
            activeBadge.setVisibility(View.GONE);
        }
        if (grandTotal > 0) {
            android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
            badgeBg.setColor(0xFFE53935); badgeBg.setCornerRadius(20*d);
            TextView badgeTv = new TextView(this);
            badgeTv.setText(String.valueOf(grandTotal));
            badgeTv.setTextColor(0xFFFFFFFF);
            badgeTv.setTextSize(11);
            badgeTv.setTypeface(null, android.graphics.Typeface.BOLD);
            badgeTv.setPadding((int)(9*d),(int)(3*d),(int)(9*d),(int)(3*d));
            badgeTv.setGravity(Gravity.CENTER);
            badgeTv.setBackground(badgeBg);
            hTop.addView(badgeTv);
        }
        header.addView(hTop);

        // Subtitle
        TextView tvSub = new TextView(this);
        tvSub.setPadding((int)(34*d), (int)(4*d), 0, 0);
        tvSub.setTextColor(0xAAFFFFFF);
        tvSub.setTextSize(12);
        tvSub.setText(isKhmer
            ? totalAlerts + " ការព្រមាន  •  " + totalBills + " វិក្កយបត្រ  •  " + history.size() + " បានបង់"
            : totalAlerts + " alert(s)  •  " + totalBills + " bill(s)  •  " + history.size() + " paid");
        header.addView(tvSub);
        root.addView(header);

        // ══════════════════════════════════
        // BODY — white background
        // ══════════════════════════════════
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p16, p12, p16, (int)(24*d));
        android.graphics.drawable.GradientDrawable bodyBg = new android.graphics.drawable.GradientDrawable();
        bodyBg.setColor(0xFFF5F8FC);
        bodyBg.setCornerRadii(new float[]{ 0, 0, 0, 0, 20*d, 20*d, 20*d, 20*d }); // round bottom corners matching dialog shape
        body.setBackground(bodyBg);

        // ── Budget Alerts ──
        if (hasMonthAlert || !catAlerts.isEmpty()) {
            addNotifSectionHeader(body, isKhmer ? "ការព្រមានថវិការ" : "Budget Alerts",
                (hasMonthAlert ? 1 : 0) + catAlerts.size(), 0xFFE53935, d);

            // Monthly budget card
            if (hasMonthAlert) {
                int pctVal = (int) Math.min(Math.round((monthSpend / monthLimit) * 100), 100);
                boolean isOver = monthSpend >= monthLimit;
                addBudgetAlertCard(body,
                    isOver ? (isKhmer ? "ហួសដែនកំណត់ថវិការ" : "Monthly Budget Exceeded")
                           : (isKhmer ? "ជិតដល់ដែនកំណត់" : "Approaching Budget Limit"),
                    isKhmer
                        ? "ចំណាយ " + formatAmount(this, monthSpend) + " / " + formatAmount(this, monthLimit)
                        : "Spent " + formatAmount(this, monthSpend) + " of " + formatAmount(this, monthLimit),
                    pctVal, isOver, 0, null, null, d);
            }

            // Category budget cards
            for (String[] ca : catAlerts) {
                boolean isOver = "1".equals(ca[4]);
                int pctInt = Integer.parseInt(ca[3]);
                addBudgetAlertCard(body,
                    ca[0] + (isOver ? (isKhmer ? " — ហួសដែនកំណត់!" : " — Exceeded!") : (isKhmer ? " — ជិតដល់" : " — Near limit")),
                    isKhmer ? "ចំណាយ " + ca[1] + " / " + ca[2] : "Spent " + ca[1] + " of " + ca[2],
                    pctInt, isOver, getCategoryIcon(ca[0]), getCategoryColor(ca[0]), getCategoryIconTint(ca[0]), d);
            }
        }

        // ── Bill Reminders — ALL recurring payments ──
        if (!recs.isEmpty()) {
            addNotifSectionHeader(body, isKhmer ? "ការរំលឹកវិក្កយបត្រ" : "Bill Reminders",
                recs.size(), 0xFF1565C0, d);

            for (DatabaseHelper.RecurringPayment rec : recs) {
                boolean isOverdue = rec.dueDate != null && rec.dueDate.compareTo(todayStr) < 0;
                boolean isDueToday = rec.dueDate != null && rec.dueDate.equals(todayStr);
                boolean isDueSoon = rec.dueDate != null && !isOverdue && !isDueToday && rec.dueDate.compareTo(deadline7Str) <= 0;

                int daysLeft = 0;
                try {
                    java.util.Date dueD = dbFormat.parse(rec.dueDate);
                    java.util.Date todayD = dbFormat.parse(todayStr);
                    daysLeft = (int)((dueD.getTime() - todayD.getTime()) / (1000 * 60 * 60 * 24));
                } catch (Exception ignored) {}

                // Status pill label + color
                String pillText;
                int pillColor, cardBg, accentColor;
                if (isOverdue) {
                    pillText = isKhmer ? "យឺត" : "OVERDUE";
                    pillColor = 0xFFE53935; cardBg = 0xFFFFF5F5; accentColor = 0xFFE53935;
                } else if (isDueToday) {
                    pillText = isKhmer ? "ថ្ងៃនេះ" : "DUE TODAY";
                    pillColor = 0xFFFF6F00; cardBg = 0xFFFFFBF0; accentColor = 0xFFFF6F00;
                } else if (isDueSoon) {
                    pillText = isKhmer ? "ឆាប់ៗ" : "SOON";
                    pillColor = 0xFF1976D2; cardBg = 0xFFF0F6FF; accentColor = 0xFF1976D2;
                } else {
                    pillText = isKhmer ? "កំណត់" : "SCHEDULED";
                    pillColor = 0xFF546E7A; cardBg = 0xFFF8F9FA; accentColor = 0xFF546E7A;
                }

                String dueLabel;
                if (isOverdue) {
                    dueLabel = isKhmer ? "យឺតហើយ " + Math.abs(daysLeft) + " ថ្ងៃ" : "Overdue by " + Math.abs(daysLeft) + " day(s)";
                } else if (isDueToday) {
                    dueLabel = isKhmer ? "ដល់ថ្ងៃនេះ!" : "Due Today!";
                } else {
                    dueLabel = isKhmer ? "ក្នុង " + daysLeft + " ថ្ងៃ · " + rec.dueDate : "In " + daysLeft + " days · " + rec.dueDate;
                }

                // Card
                com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardP.bottomMargin = (int)(10*d);
                card.setLayoutParams(cardP);
                card.setRadius(14*d); card.setCardElevation(2*d); card.setStrokeWidth(0);
                card.setCardBackgroundColor(ColorStateList.valueOf(cardBg));

                LinearLayout cardInner = new LinearLayout(this);
                cardInner.setOrientation(LinearLayout.HORIZONTAL);
                cardInner.setGravity(Gravity.CENTER_VERTICAL);
                cardInner.setPadding(p12, p12, p12, p12);

                // Left accent stripe
                android.graphics.drawable.GradientDrawable stripe = new android.graphics.drawable.GradientDrawable();
                stripe.setColor(accentColor);
                stripe.setCornerRadii(new float[]{4*d,4*d,0,0,0,0,4*d,4*d});
                View stripeV = new View(this);
                LinearLayout.LayoutParams stripeP = new LinearLayout.LayoutParams((int)(4*d), LinearLayout.LayoutParams.MATCH_PARENT);
                stripeP.rightMargin = p12;
                stripeP.setMarginStart(0);
                stripeV.setLayoutParams(stripeP);
                stripeV.setBackground(stripe);
                cardInner.addView(stripeV);

                // Icon circle
                com.google.android.material.card.MaterialCardView iconC = new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams icP = new LinearLayout.LayoutParams((int)(44*d),(int)(44*d));
                icP.rightMargin = p12;
                iconC.setLayoutParams(icP);
                iconC.setRadius(22*d); iconC.setCardElevation(0); iconC.setStrokeWidth(0);
                android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
                iconBg.setColor(accentColor & 0x1AFFFFFF | 0x1A000000); // 10% alpha version
                iconBg.setColor(isOverdue ? 0xFFFFCDD2 : isDueToday ? 0xFFFFE0B2 : isDueSoon ? 0xFFBBDEFB : 0xFFECEFF1);
                iconC.setCardBackgroundColor(ColorStateList.valueOf(
                    isOverdue ? 0xFFFFCDD2 : isDueToday ? 0xFFFFE0B2 : isDueSoon ? 0xFFBBDEFB : 0xFFECEFF1));
                ImageView billIv = new ImageView(this);
                android.widget.FrameLayout.LayoutParams bivP = new android.widget.FrameLayout.LayoutParams((int)(22*d),(int)(22*d));
                bivP.gravity = Gravity.CENTER;
                billIv.setLayoutParams(bivP);
                billIv.setImageResource(R.drawable.ic_bills);
                billIv.setImageTintList(ColorStateList.valueOf(accentColor));
                iconC.addView(billIv);
                cardInner.addView(iconC);

                // Text column
                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tcP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                textCol.setLayoutParams(tcP);

                // Name row + pill
                LinearLayout nameRow = new LinearLayout(this);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setText(rec.name);
                tvName.setTextColor(0xFF1A1C24);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvName.setLayoutParams(nameP);
                nameRow.addView(tvName);

                // Pill badge
                android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
                pillBg.setColor(pillColor); pillBg.setCornerRadius(20*d);
                TextView pillTv = new TextView(this);
                pillTv.setText(pillText);
                pillTv.setTextColor(0xFFFFFFFF);
                pillTv.setTextSize(9);
                pillTv.setTypeface(null, android.graphics.Typeface.BOLD);
                pillTv.setPadding((int)(7*d),(int)(2*d),(int)(7*d),(int)(2*d));
                pillTv.setBackground(pillBg);
                nameRow.addView(pillTv);
                textCol.addView(nameRow);

                // Due label
                TextView tvDue = new TextView(this);
                tvDue.setText(dueLabel);
                tvDue.setTextColor(accentColor);
                tvDue.setTextSize(12);
                LinearLayout.LayoutParams dueP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dueP.topMargin = (int)(2*d);
                tvDue.setLayoutParams(dueP);
                textCol.addView(tvDue);

                // Freq + amount
                TextView tvFreq = new TextView(this);
                tvFreq.setText(rec.frequency + "  ·  " + formatAmount(this, rec.amount));
                tvFreq.setTextColor(0xFF9E9E9E);
                tvFreq.setTextSize(11);
                textCol.addView(tvFreq);
                cardInner.addView(textCol);

                // Amount on right
                TextView amtTv = new TextView(this);
                amtTv.setText(formatAmount(this, rec.amount));
                amtTv.setTextColor(accentColor);
                amtTv.setTextSize(15);
                amtTv.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams amtP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                amtP.leftMargin = (int)(10*d);
                amtTv.setLayoutParams(amtP);
                cardInner.addView(amtTv);

                card.addView(cardInner);
                body.addView(card);
            }
        }

        // ── Payment History (Processed Subscriptions) ──

        if (!history.isEmpty()) {
            addNotifSectionHeader(body, isKhmer ? "ប្រវត្តិនៃការបង់ប្រាក់" : "Payment History",
                history.size(), 0xFF4CAF50, d);

            for (DatabaseHelper.Transaction tx : history) {
                com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardP.bottomMargin = (int)(10*d);
                card.setLayoutParams(cardP);
                card.setRadius(14*d); card.setCardElevation(2*d); card.setStrokeWidth(0);
                card.setCardBackgroundColor(ColorStateList.valueOf(0xFFF1F8E9));

                LinearLayout cardInner = new LinearLayout(this);
                cardInner.setOrientation(LinearLayout.HORIZONTAL);
                cardInner.setGravity(Gravity.CENTER_VERTICAL);
                cardInner.setPadding(p12, p12, p12, p12);

                android.graphics.drawable.GradientDrawable stripe = new android.graphics.drawable.GradientDrawable();
                stripe.setColor(0xFF4CAF50);
                stripe.setCornerRadii(new float[]{4*d,4*d,0,0,0,0,4*d,4*d});
                View stripeV = new View(this);
                LinearLayout.LayoutParams stripeP = new LinearLayout.LayoutParams((int)(4*d), LinearLayout.LayoutParams.MATCH_PARENT);
                stripeP.rightMargin = p12;
                stripeP.setMarginStart(0);
                stripeV.setLayoutParams(stripeP);
                stripeV.setBackground(stripe);
                cardInner.addView(stripeV);

                com.google.android.material.card.MaterialCardView iconC = new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams icP = new LinearLayout.LayoutParams((int)(44*d),(int)(44*d));
                icP.rightMargin = p12;
                iconC.setLayoutParams(icP);
                iconC.setRadius(22*d); iconC.setCardElevation(0); iconC.setStrokeWidth(0);
                iconC.setCardBackgroundColor(ColorStateList.valueOf(0xFFDCEDC8));
                ImageView billIv = new ImageView(this);
                android.widget.FrameLayout.LayoutParams bivP = new android.widget.FrameLayout.LayoutParams((int)(22*d),(int)(22*d));
                bivP.gravity = Gravity.CENTER;
                billIv.setLayoutParams(bivP);
                billIv.setImageResource(R.drawable.ic_bills);
                billIv.setImageTintList(ColorStateList.valueOf(0xFF4CAF50));
                iconC.addView(billIv);
                cardInner.addView(iconC);

                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tcP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                textCol.setLayoutParams(tcP);

                LinearLayout nameRow = new LinearLayout(this);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setText(tx.note.replace(" (Subscription)", ""));
                tvName.setTextColor(0xFF1A1C24);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvName.setLayoutParams(nameP);
                nameRow.addView(tvName);

                android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
                pillBg.setColor(0xFF4CAF50); pillBg.setCornerRadius(20*d);
                TextView pillTv = new TextView(this);
                pillTv.setText(isKhmer ? "បានបង់" : "PAID");
                pillTv.setTextColor(0xFFFFFFFF);
                pillTv.setTextSize(9);
                pillTv.setTypeface(null, android.graphics.Typeface.BOLD);
                pillTv.setPadding((int)(7*d),(int)(2*d),(int)(7*d),(int)(2*d));
                pillTv.setBackground(pillBg);
                nameRow.addView(pillTv);
                textCol.addView(nameRow);

                TextView tvDue = new TextView(this);
                tvDue.setText((isKhmer ? "បានបង់នៅថ្ងៃទី " : "Paid on ") + tx.date);
                tvDue.setTextColor(0xFF4CAF50);
                tvDue.setTextSize(12);
                LinearLayout.LayoutParams dueP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dueP.topMargin = (int)(2*d);
                tvDue.setLayoutParams(dueP);
                textCol.addView(tvDue);

                TextView tvFreq = new TextView(this);
                tvFreq.setText(isKhmer ? "ប្រភេទ៖ វិក្កយបត្រ" : "Category: Bills");
                tvFreq.setTextColor(0xFF9E9E9E);
                tvFreq.setTextSize(11);
                textCol.addView(tvFreq);
                cardInner.addView(textCol);

                TextView amtTv = new TextView(this);
                amtTv.setText("-" + formatAmount(this, tx.amount));
                amtTv.setTextColor(0xFFE53935);
                amtTv.setTextSize(15);
                amtTv.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams amtP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                amtP.leftMargin = (int)(10*d);
                amtTv.setLayoutParams(amtP);
                cardInner.addView(amtTv);

                card.addView(cardInner);
                body.addView(card);
            }
        }

        // ── Empty state ──
        if (!hasMonthAlert && catAlerts.isEmpty() && recs.isEmpty() && history.isEmpty()) {
            LinearLayout emptyL = new LinearLayout(this);
            emptyL.setOrientation(LinearLayout.VERTICAL);
            emptyL.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.topMargin = (int)(32*d); elp.bottomMargin = (int)(24*d);
            emptyL.setLayoutParams(elp);

            ImageView emptyIv = new ImageView(this);
            LinearLayout.LayoutParams eip = new LinearLayout.LayoutParams((int)(56*d),(int)(56*d));
            eip.bottomMargin = (int)(12*d);
            eip.gravity = Gravity.CENTER_HORIZONTAL;
            emptyIv.setLayoutParams(eip);
            emptyIv.setImageResource(R.drawable.ic_notification);
            emptyIv.setImageTintList(ColorStateList.valueOf(0xFFD0D5E8));
            emptyL.addView(emptyIv);

            TextView tvE = new TextView(this);
            tvE.setText(isKhmer ? "គ្មានការជូនដំណឹងថ្មីៗ" : "All clear! Nothing to show.");
            tvE.setTextColor(0xFFB0B7C3);
            tvE.setTextSize(14);
            tvE.setGravity(Gravity.CENTER);
            emptyL.addView(tvE);
            body.addView(emptyL);
        }

        root.addView(body);
        scrollView.addView(root);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedDialog);
        builder.setView(scrollView);
        builder.setPositiveButton(isKhmer ? "បិទ" : "Done", null);
        builder.show();
    }

    /** Section header with count chip */
    private void addNotifSectionHeader(LinearLayout parent, String text, int count, int color, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = (int)(14*d); rp.bottomMargin = (int)(8*d);
        row.setLayoutParams(rp);

        // Accent bar
        View bar = new View(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int)(4*d),(int)(16*d));
        bp.rightMargin = (int)(8*d);
        bar.setLayoutParams(bp);
        android.graphics.drawable.GradientDrawable barBg = new android.graphics.drawable.GradientDrawable();
        barBg.setColor(color); barBg.setCornerRadius(4*d);
        bar.setBackground(barBg);
        row.addView(bar);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF444444);
        tv.setTextSize(12);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams tvP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvP);
        row.addView(tv);

        // Count chip
        android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
        chipBg.setColor(color & 0x22FFFFFF | 0x22000000);
        chipBg.setColor(color);
        chipBg.setCornerRadius(20*d);
        chipBg.setAlpha(220);
        TextView chip = new TextView(this);
        chip.setText(String.valueOf(count));
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(10);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding((int)(8*d),(int)(2*d),(int)(8*d),(int)(2*d));
        chip.setBackground(chipBg);
        row.addView(chip);

        parent.addView(row);
    }

    /** Budget alert card with progress bar */
    private void addBudgetAlertCard(LinearLayout parent, String title, String sub, int pct,
                                    boolean isOver, int iconRes, Integer iconBgColor, Integer iconTint, float d) {
        int p12 = (int)(12*d), p16 = (int)(16*d);
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = (int)(10*d);
        card.setLayoutParams(cp);
        card.setRadius(14*d); card.setCardElevation(2*d); card.setStrokeWidth(0);
        card.setCardBackgroundColor(ColorStateList.valueOf(isOver ? 0xFFFFF5F5 : 0xFFFFFBF0));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(p12, p12, p12, p12);

        // Left colored stripe
        android.graphics.drawable.GradientDrawable stripe = new android.graphics.drawable.GradientDrawable();
        stripe.setColor(isOver ? 0xFFE53935 : 0xFFFF9800);
        stripe.setCornerRadii(new float[]{4*d,4*d,0,0,0,0,4*d,4*d});
        View stripeV = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams((int)(4*d), LinearLayout.LayoutParams.MATCH_PARENT);
        sp.rightMargin = p12;
        stripeV.setLayoutParams(sp);
        stripeV.setBackground(stripe);
        inner.addView(stripeV);

        // Icon
        if (iconRes != 0 && iconBgColor != null) {
            com.google.android.material.card.MaterialCardView ic = new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams((int)(40*d),(int)(40*d));
            icp.rightMargin = p12;
            ic.setLayoutParams(icp);
            ic.setRadius(20*d); ic.setCardElevation(0); ic.setStrokeWidth(0);
            ic.setCardBackgroundColor(ColorStateList.valueOf(iconBgColor));
            ImageView iv = new ImageView(this);
            android.widget.FrameLayout.LayoutParams ivP = new android.widget.FrameLayout.LayoutParams((int)(20*d),(int)(20*d));
            ivP.gravity = Gravity.CENTER;
            iv.setLayoutParams(ivP);
            iv.setImageResource(iconRes);
            iv.setImageTintList(ColorStateList.valueOf(iconTint != null ? iconTint : 0xFF666666));
            ic.addView(iv);
            inner.addView(ic);
        } else {
            // Warning icon
            ImageView warnIv = new ImageView(this);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams((int)(24*d),(int)(24*d));
            wp.rightMargin = p12;
            warnIv.setLayoutParams(wp);
            warnIv.setImageResource(R.drawable.ic_warning);
            warnIv.setImageTintList(ColorStateList.valueOf(isOver ? 0xFFE53935 : 0xFFFF9800));
            inner.addView(warnIv);
        }

        // Text + progress
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tcp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(isOver ? 0xFFB71C1C : 0xFFE65100);
        tvTitle.setTextSize(13); tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(sub);
        tvSub.setTextColor(0xFF757575); tvSub.setTextSize(11);
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subP.topMargin = (int)(2*d); subP.bottomMargin = (int)(8*d);
        tvSub.setLayoutParams(subP);
        textCol.addView(tvSub);

        // Progress bar
        LinearLayout track = new LinearLayout(this);
        LinearLayout.LayoutParams trackP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int)(6*d));
        track.setLayoutParams(trackP);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundResource(R.drawable.bg_progress_track);
        View fill = new View(this);
        LinearLayout.LayoutParams fillP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct);
        fill.setLayoutParams(fillP);
        fill.setBackgroundResource(isOver ? R.drawable.bg_progress_fill_orange : R.drawable.bg_progress_fill_dark_blue);
        track.addView(fill);
        View rem = new View(this);
        rem.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100 - pct));
        track.addView(rem);
        textCol.addView(track);

        inner.addView(textCol);

        // Percentage on right
        TextView pctTv = new TextView(this);
        pctTv.setText(pct + "%");
        pctTv.setTextColor(isOver ? 0xFFE53935 : 0xFFFF9800);
        pctTv.setTextSize(16); pctTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams pctP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pctP.leftMargin = p12;
        pctTv.setLayoutParams(pctP);
        inner.addView(pctTv);

        card.addView(inner);
        parent.addView(card);
    }

    /** @deprecated kept for any remaining callers */
    @Deprecated
    private void addSectionLabel(LinearLayout parent, String text, int color, float d) {
        addNotifSectionHeader(parent, text, 0, color, d);
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
            
            View btnNotif = rootView.findViewById(R.id.btnHeaderNotification);
            if (btnNotif != null) {
                btnNotif.setOnClickListener(v -> {
                    if (getActivity() instanceof Main_Activity) {
                        ((Main_Activity) getActivity()).showNotificationsDialog();
                    }
                });
            }

            refreshData(rootView);
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
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setHorizontalScrollBarEnabled(false);
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

            final SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getContext());
            final String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            final DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());

            // Optimistic instant local render
            updateUIHome(view, dbHelper, email, authManager);

            // Fetch server updates via GET REST API
            if (authManager.isLoggedIn()) {
                authManager.fetchTransactionsFromServer(new SupabaseAuthManager.FetchTransactionsCallback() {
                    @Override
                    public void onSuccess(final List<DatabaseHelper.Transaction> transactions) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            boolean hasNew = false;
                            for (DatabaseHelper.Transaction tx : transactions) {
                                if (!dbHelper.doesTransactionExist(tx.id)) {
                                    dbHelper.insertTransaction(tx);
                                    hasNew = true;
                                }
                            }
                            if (hasNew && rootView != null) {
                                updateUIHome(rootView, dbHelper, email, authManager);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // Silent fail - offline mode retains local data
                    }
                });
            }
        }

        private void updateUIHome(View view, DatabaseHelper dbHelper, String email, SupabaseAuthManager authManager) {
            if (getContext() == null) return;

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
                if (savingsRate < 0) savingsRate = 0.0;
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

            // 4. Recent Transactions List (RecyclerView)
            androidx.recyclerview.widget.RecyclerView rvRecentTransactions = view.findViewById(R.id.rvRecentTransactions);
            TextView tvHomeNoTransactions = view.findViewById(R.id.tvHomeNoTransactions);

            if (rvRecentTransactions != null && tvHomeNoTransactions != null) {
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
                    tvHomeNoTransactions.setVisibility(View.VISIBLE);
                    rvRecentTransactions.setVisibility(View.GONE);
                } else {
                    tvHomeNoTransactions.setVisibility(View.GONE);
                    rvRecentTransactions.setVisibility(View.VISIBLE);

                    // Show up to 10 transactions
                    int count = Math.min(transactions.size(), 10);
                    List<DatabaseHelper.Transaction> recentList = transactions.subList(0, count);

                    rvRecentTransactions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
                    TransactionAdapter adapter = new TransactionAdapter(getContext(), recentList, tx -> showTransactionDetails(tx));
                    rvRecentTransactions.setAdapter(adapter);
                }
            }

            // 5. Background sync for unsynced transactions (Push to Supabase REST API)
            if (authManager.isLoggedIn()) {
                List<DatabaseHelper.Transaction> unsynced = dbHelper.getUnsyncedTransactions(email);
                for (final DatabaseHelper.Transaction tx : unsynced) {
                    authManager.syncTransaction(tx, () -> dbHelper.markAsSynced(tx.id), null);
                }
            }
            if (getActivity() instanceof Main_Activity) {
                ((Main_Activity) getActivity()).updateNotificationBadge(view);
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

            View btnAddGoal = rootView.findViewById(R.id.btn_add_saving_goal);
            if (btnAddGoal != null) {
                btnAddGoal.setOnClickListener(v -> showAddEditSavingGoalDialog(null));
            }
            View btnAddRec = rootView.findViewById(R.id.btn_add_recurring);
            if (btnAddRec != null) {
                btnAddRec.setOnClickListener(v -> showAddEditRecurringPaymentDialog(null));
            }
            View btnAddCat = rootView.findViewById(R.id.btn_add_category_budget);
            if (btnAddCat != null) {
                btnAddCat.setOnClickListener(v -> showAddEditCategoryBudgetDialog(null));
            }
            
            View btnNotif = rootView.findViewById(R.id.btnHeaderNotification);
            if (btnNotif != null) {
                btnNotif.setOnClickListener(v -> {
                    if (getActivity() instanceof Main_Activity) {
                        ((Main_Activity) getActivity()).showNotificationsDialog();
                    }
                });
            }

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

        private int dpToPx(int dp) {
            if (getContext() == null) return dp;
            return (int) (dp * getContext().getResources().getDisplayMetrics().density);
        }

        private void showAllCategoryBudgetsDialog() {
            if (getActivity() == null) return;
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("selected_language", "EN");
            boolean isKhmer = "KH".equals(lang);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            
            android.widget.ScrollView scrollView = new android.widget.ScrollView(getActivity());
            scrollView.setVerticalScrollBarEnabled(false);
            
            LinearLayout container = new LinearLayout(getActivity());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

            TextView title = new TextView(getActivity());
            title.setText(isKhmer ? "កម្រិតថវិកាតាមប្រភេទចំណាយ" : "Category Budgets");
            title.setTextSize(20);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(0xFF1A1C24);
            title.setPadding(0, 0, 0, dpToPx(16));
            container.addView(title);

            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);

            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String monthPrefix = dbFormat.format(cal.getTime()).substring(0, 7);

            String[] categories = {"Food", "Travel", "Shop", "Health", "Bills", "Fun", "Study", "Other"};
            float[] defaultBudgets = {300f, 100f, 200f, 150f, 250f, 100f, 100f, 100f};
            String[] prefKeys = {"food_budget_limit", "travel_budget_limit", "shop_budget_limit", "health_budget_limit", "bills_budget_limit", "fun_budget_limit", "study_budget_limit", "other_budget_limit"};
            
            Map<String, Double> categorySpending = new java.util.HashMap<>();
            for (String cat : categories) {
                categorySpending.put(cat, 0.0);
            }

            for (DatabaseHelper.Transaction tx : transactions) {
                if (tx.date.startsWith(monthPrefix) && "expense".equalsIgnoreCase(tx.type)) {
                    if (categorySpending.containsKey(tx.category)) {
                        categorySpending.put(tx.category, categorySpending.get(tx.category) + tx.amount);
                    } else {
                        categorySpending.put("Other", categorySpending.getOrDefault("Other", 0.0) + tx.amount);
                    }
                }
            }

            float density = getResources().getDisplayMetrics().density;

            for (int i = 0; i < categories.length; i++) {
                final String catName = categories[i];
                final String prefKey = prefKeys[i];
                final float defaultLimit = defaultBudgets[i];
                final double spend = categorySpending.getOrDefault(catName, 0.0);
                final float limit = prefs.getFloat(prefKey, defaultLimit);

                com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(getActivity());
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.bottomMargin = (int)(12 * density);
                card.setLayoutParams(cardParams);
                card.setRadius(16 * density);
                card.setCardElevation(2 * density);
                card.setStrokeWidth(0);
                card.setCardBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF));
                card.setClickable(true);
                card.setFocusable(true);

                LinearLayout inner = new LinearLayout(getActivity());
                inner.setOrientation(LinearLayout.VERTICAL);
                inner.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));

                RelativeLayout header = new RelativeLayout(getActivity());
                header.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView tvName = new TextView(getActivity());
                tvName.setText(translateText(catName, isKhmer));
                tvName.setTextColor(0xFF1A1C24);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                RelativeLayout.LayoutParams lpName = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                lpName.addRule(RelativeLayout.ALIGN_PARENT_START);
                tvName.setLayoutParams(lpName);
                header.addView(tvName);

                TextView tvSp = new TextView(getActivity());
                tvSp.setText(formatAmount(getActivity(), spend, true) + " / " + formatAmount(getActivity(), limit, true));
                tvSp.setTextColor(0xFF9DA3B4);
                tvSp.setTextSize(11);
                RelativeLayout.LayoutParams lpSp = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                lpSp.addRule(RelativeLayout.ALIGN_PARENT_END);
                tvSp.setLayoutParams(lpSp);
                header.addView(tvSp);

                inner.addView(header);

                int pct = limit > 0 ? (int) Math.round((spend / limit) * 100.0) : 0;
                if (pct > 100) pct = 100;

                LinearLayout track = new LinearLayout(getActivity());
                LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int)(6*density));
                trackParams.topMargin = (int)(8 * density);
                track.setLayoutParams(trackParams);
                track.setBackgroundResource(R.drawable.bg_progress_track);
                track.setOrientation(LinearLayout.HORIZONTAL);

                View fill = new View(getActivity());
                LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct);
                fill.setLayoutParams(fillParams);
                fill.setBackgroundResource(R.drawable.bg_progress_fill_light_blue);
                track.addView(fill);

                View remaining = new View(getActivity());
                LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100 - pct);
                remaining.setLayoutParams(remainingParams);
                track.addView(remaining);

                inner.addView(track);
                card.addView(inner);

                card.setOnClickListener(v -> {
                    showSetBudgetDialog(prefKey, catName + " Category Budget", defaultLimit);
                });

                container.addView(card);
            }

            scrollView.addView(container);
            builder.setView(scrollView);
            builder.setPositiveButton(isKhmer ? "បិទ" : "Close", null);
            builder.show();
        }

        private void processRecurringPayments(DatabaseHelper dbHelper, String userEmail) {
            List<DatabaseHelper.RecurringPayment> recs = dbHelper.getRecurringPayments(userEmail);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String todayStr = sdf.format(new java.util.Date());

            for (DatabaseHelper.RecurringPayment rec : recs) {
                String dueDateStr = rec.dueDate;
                
                // If it's a legacy non-date string, migrate it to today
                if (dueDateStr == null || !dueDateStr.contains("-")) {
                    dueDateStr = todayStr;
                }

                try {
                    // While the due date is in the past or today, we charge/cut money!
                    while (dueDateStr.compareTo(todayStr) <= 0) {
                        // Create transaction (deduct money)
                        DatabaseHelper.Transaction tx = new DatabaseHelper.Transaction(
                                java.util.UUID.randomUUID().toString(),
                                userEmail,
                                "expense",
                                rec.amount,
                                "Bills",
                                dueDateStr,
                                rec.name + " (Subscription)",
                                null,
                                0
                        );
                        dbHelper.insertTransaction(tx);

                        // Calculate next due date
                        java.util.Date dueDateObj = sdf.parse(dueDateStr);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(dueDateObj);

                        if ("WEEKLY".equalsIgnoreCase(rec.frequency)) {
                            cal.add(Calendar.WEEK_OF_YEAR, 1);
                        } else {
                            cal.add(Calendar.MONTH, 1);
                        }

                        dueDateStr = sdf.format(cal.getTime());
                        rec.dueDate = dueDateStr;
                        dbHelper.updateRecurringPayment(rec);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void refreshData(View view) {
            if (getContext() == null) return;

            SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getContext());
            String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getContext());

            // Process auto-deductions first so they reflect immediately in calculation totals
            processRecurringPayments(dbHelper, email);

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

            // 4. Category Budgets & Dynamic calculations
            boolean isKhmer = "KH".equals(prefs.getString("selected_language", "EN"));
            List<DatabaseHelper.Transaction> transactions = dbHelper.getTransactions(email);
            String monthPrefix = startOfMonthStr.substring(0, 7); // e.g. "2026-06"

            LinearLayout layoutCategoryBudgets = view.findViewById(R.id.layoutCategoryBudgets);
            if (layoutCategoryBudgets != null) {
                layoutCategoryBudgets.removeAllViews();

                List<DatabaseHelper.CategoryBudget> cbList = dbHelper.getCategoryBudgets(email);
                boolean seededCb = prefs.getBoolean("seeded_category_budgets", false);
                if (cbList.isEmpty() && !seededCb) {
                    // Seed defaults if empty
                    dbHelper.insertCategoryBudget(new DatabaseHelper.CategoryBudget(
                            java.util.UUID.randomUUID().toString(), email, "Food", 300.00
                    ));
                    dbHelper.insertCategoryBudget(new DatabaseHelper.CategoryBudget(
                            java.util.UUID.randomUUID().toString(), email, "Shop", 200.00
                    ));
                    prefs.edit().putBoolean("seeded_category_budgets", true).apply();
                    cbList = dbHelper.getCategoryBudgets(email);
                }

                float density = getResources().getDisplayMetrics().density;
                int cardWidth = (int)(160 * density);

                for (int i = 0; i < cbList.size(); i++) {
                    final DatabaseHelper.CategoryBudget cb = cbList.get(i);

                    // Compute current month spending for this category
                    double catSpend = 0.0;
                    for (DatabaseHelper.Transaction tx : transactions) {
                        if (tx.date.startsWith(monthPrefix) && "expense".equalsIgnoreCase(tx.type)) {
                            if (cb.category.equalsIgnoreCase(tx.category)) {
                                catSpend += tx.amount;
                            } else if ("Shop".equalsIgnoreCase(cb.category) && "Shopping".equalsIgnoreCase(tx.category)) {
                                catSpend += tx.amount;
                            }
                        }
                    }

                    // Card Container — fixed width for horizontal scrolling
                    com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(getContext());
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.rightMargin = (int)(12 * density);
                    card.setLayoutParams(cardParams);
                    card.setRadius(20 * density);
                    card.setCardElevation(2 * density);
                    card.setStrokeWidth(0);
                    card.setCardBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF));
                    card.setClickable(true);
                    card.setFocusable(true);

                    // Inner Layout
                    LinearLayout inner = new LinearLayout(getContext());
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));

                    // Top Row Layout (Icon + Percentage)
                    RelativeLayout topRow = new RelativeLayout(getContext());
                    topRow.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ));

                    // Circle Icon Card
                    com.google.android.material.card.MaterialCardView iconCard = new com.google.android.material.card.MaterialCardView(getContext());
                    RelativeLayout.LayoutParams iconCardParams = new RelativeLayout.LayoutParams(
                            (int)(36 * density), (int)(36 * density)
                    );
                    iconCardParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                    iconCard.setLayoutParams(iconCardParams);
                    iconCard.setRadius(18 * density);
                    iconCard.setCardElevation(0);
                    iconCard.setStrokeWidth(0);
                    iconCard.setCardBackgroundColor(ColorStateList.valueOf(getCategoryColor(cb.category)));

                    ImageView ivIcon = new ImageView(getContext());
                    FrameLayout.LayoutParams ivIconParams = new FrameLayout.LayoutParams(
                            (int)(18 * density), (int)(18 * density)
                    );
                    ivIconParams.gravity = Gravity.CENTER;
                    ivIcon.setLayoutParams(ivIconParams);
                    ivIcon.setImageResource(getCategoryIcon(cb.category));
                    ivIcon.setImageTintList(ColorStateList.valueOf(getCategoryIconTint(cb.category)));
                    iconCard.addView(ivIcon);
                    topRow.addView(iconCard);

                    // Percentage Text
                    TextView tvPct = new TextView(getContext());
                    RelativeLayout.LayoutParams tvPctParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    tvPctParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                    tvPctParams.addRule(RelativeLayout.CENTER_VERTICAL);
                    tvPct.setLayoutParams(tvPctParams);
                    int pctVal = cb.budgetLimit > 0 ? (int) Math.round((catSpend / cb.budgetLimit) * 100.0) : 0;
                    if (pctVal > 100) pctVal = 100;
                    tvPct.setText(pctVal + "%");
                    tvPct.setTextColor(0xFF9DA3B4);
                    tvPct.setTextSize(12);
                    topRow.addView(tvPct);

                    inner.addView(topRow);

                    // Category Name
                    TextView tvCatName = new TextView(getContext());
                    LinearLayout.LayoutParams tvCatNameParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    tvCatNameParams.topMargin = (int)(12 * density);
                    tvCatName.setLayoutParams(tvCatNameParams);
                    tvCatName.setText(translateText(cb.category, isKhmer));
                    tvCatName.setTextColor(0xFF1A1C24);
                    tvCatName.setTextSize(14);
                    tvCatName.setTypeface(null, android.graphics.Typeface.BOLD);
                    inner.addView(tvCatName);

                    // Spend Text
                    TextView tvSpendText = new TextView(getContext());
                    LinearLayout.LayoutParams tvSpendParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    tvSpendParams.topMargin = (int)(4 * density);
                    tvSpendText.setLayoutParams(tvSpendParams);
                    tvSpendText.setText(formatAmount(getContext(), catSpend, true) + " / " + formatAmount(getContext(), cb.budgetLimit, true));
                    tvSpendText.setTextColor(0xFF9DA3B4);
                    tvSpendText.setTextSize(10);
                    tvSpendText.setSingleLine(true);
                    tvSpendText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    inner.addView(tvSpendText);

                    // Progress Bar Layout
                    LinearLayout progressTrack = new LinearLayout(getContext());
                    LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, (int)(6 * density)
                    );
                    trackParams.topMargin = (int)(12 * density);
                    progressTrack.setLayoutParams(trackParams);
                    progressTrack.setOrientation(LinearLayout.HORIZONTAL);
                    progressTrack.setBackgroundResource(R.drawable.bg_progress_track);

                    View fill = new View(getContext());
                    LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT, pctVal
                    );
                    fill.setLayoutParams(fillParams);
                    fill.setBackgroundResource(cb.category.equalsIgnoreCase("Food") ? R.drawable.bg_progress_fill_orange : R.drawable.bg_progress_fill_light_blue);
                    progressTrack.addView(fill);

                    View remaining = new View(getContext());
                    LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT, 100 - pctVal
                    );
                    remaining.setLayoutParams(remainingParams);
                    progressTrack.addView(remaining);

                    inner.addView(progressTrack);
                    card.addView(inner);

                    card.setOnClickListener(v -> showAddEditCategoryBudgetDialog(cb));
                    layoutCategoryBudgets.addView(card);
                }
            }

            // 5. Saving Goals Rendering
            LinearLayout layoutSavingGoals = view.findViewById(R.id.layoutSavingGoals);
            if (layoutSavingGoals != null) {
                layoutSavingGoals.removeAllViews();
                List<DatabaseHelper.SavingGoal> goals = dbHelper.getSavingGoals(email);
                boolean seededGoals = prefs.getBoolean("seeded_saving_goals", false);
                if (goals.isEmpty() && !seededGoals) {
                    DatabaseHelper.SavingGoal defaultGoal = new DatabaseHelper.SavingGoal(
                            java.util.UUID.randomUUID().toString(), email, "New Laptop", 1000.00, 250.00
                    );
                    dbHelper.insertSavingGoal(defaultGoal);
                    prefs.edit().putBoolean("seeded_saving_goals", true).apply();
                    goals = dbHelper.getSavingGoals(email);
                }

                android.content.Context ctx = getContext();
                float density = getResources().getDisplayMetrics().density;

                for (final DatabaseHelper.SavingGoal goal : goals) {
                    com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(ctx);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.bottomMargin = (int) (16 * density);
                    card.setLayoutParams(cardParams);
                    card.setRadius(24 * density);
                    card.setCardElevation(2 * density);
                    card.setStrokeWidth(0);
                    card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));

                    LinearLayout inner = new LinearLayout(ctx);
                    inner.setOrientation(LinearLayout.HORIZONTAL);
                    inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    inner.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));

                    com.google.android.material.card.MaterialCardView iconCard = new com.google.android.material.card.MaterialCardView(ctx);
                    LinearLayout.LayoutParams iconCardParams = new LinearLayout.LayoutParams((int) (70 * density), (int) (70 * density));
                    iconCard.setLayoutParams(iconCardParams);
                    iconCard.setRadius(16 * density);
                    iconCard.setCardElevation(0);
                    iconCard.setStrokeWidth(0);
                    iconCard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF5F8FC));

                    ImageView iv = new ImageView(ctx);
                    android.widget.FrameLayout.LayoutParams ivParams = new android.widget.FrameLayout.LayoutParams((int) (36 * density), (int) (36 * density));
                    ivParams.gravity = android.view.Gravity.CENTER;
                    iv.setLayoutParams(ivParams);
                    if (goal.name.toLowerCase().contains("laptop")) {
                        iv.setImageResource(R.drawable.ic_laptop);
                    } else {
                        iv.setImageResource(R.drawable.ic_trend_prediction);
                    }
                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF0A5296));
                    iconCard.addView(iv);
                    inner.addView(iconCard);

                    LinearLayout middle = new LinearLayout(ctx);
                    middle.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    middleParams.leftMargin = (int) (16 * density);

                    middle.setLayoutParams(middleParams);

                    RelativeLayout row = new RelativeLayout(ctx);
                    row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    TextView tvTitle = new TextView(ctx);
                    tvTitle.setText(goal.name);
                    tvTitle.setTextColor(0xFF1A1C24);
                    tvTitle.setTextSize(15);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    titleParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                    tvTitle.setLayoutParams(titleParams);
                    row.addView(tvTitle);

                    TextView tvPct = new TextView(ctx);
                    int goalPct = goal.targetAmount > 0 ? (int) Math.round((goal.currentAmount / goal.targetAmount) * 100.0) : 0;
                    if (goalPct > 100) goalPct = 100;
                    tvPct.setText(goalPct + "%");
                    tvPct.setTextColor(0xFF0A5296);
                    tvPct.setTextSize(14);
                    tvPct.setTypeface(null, android.graphics.Typeface.BOLD);
                    RelativeLayout.LayoutParams pctParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    pctParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                    tvPct.setLayoutParams(pctParams);
                    row.addView(tvPct);
                    middle.addView(row);

                    LinearLayout track = new LinearLayout(ctx);
                    LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (8 * density));
                    trackParams.topMargin = (int) (8 * density);
                    track.setLayoutParams(trackParams);
                    track.setBackgroundResource(R.drawable.bg_progress_track);
                    track.setOrientation(LinearLayout.HORIZONTAL);

                    View fill = new View(ctx);
                    LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, goalPct);
                    fill.setLayoutParams(fillParams);
                    fill.setBackgroundResource(R.drawable.bg_progress_fill_dark_blue);
                    track.addView(fill);

                    View remaining = new View(ctx);
                    LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100 - goalPct);
                    remaining.setLayoutParams(remainingParams);
                    track.addView(remaining);
                    middle.addView(track);

                    TextView tvSpend = new TextView(ctx);
                    tvSpend.setText(formatAmount(ctx, goal.currentAmount, true) + " of " + formatAmount(ctx, goal.targetAmount, true));
                    tvSpend.setTextColor(0xFF9DA3B4);
                    tvSpend.setTextSize(12);
                    LinearLayout.LayoutParams spendParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    spendParams.topMargin = (int) (8 * density);
                    tvSpend.setLayoutParams(spendParams);
                    middle.addView(tvSpend);

                    inner.addView(middle);
                    card.addView(inner);

                    card.setOnClickListener(v -> showSavingGoalOptionsDialog(goal));
                    layoutSavingGoals.addView(card);
                }
            }

            // 6. Recurring Payments Rendering
            LinearLayout layoutRecurringPayments = view.findViewById(R.id.layoutRecurringPayments);
            if (layoutRecurringPayments != null) {
                layoutRecurringPayments.removeAllViews();
                List<DatabaseHelper.RecurringPayment> recs = dbHelper.getRecurringPayments(email);
                boolean seededRecs = prefs.getBoolean("seeded_recurring_payments", false);
                if (recs.isEmpty() && !seededRecs) {
                    DatabaseHelper.RecurringPayment rec1 = new DatabaseHelper.RecurringPayment(
                            java.util.UUID.randomUUID().toString(), email, "Rent", 1200.00, "MONTHLY", "2026-07-01"
                    );
                    DatabaseHelper.RecurringPayment rec2 = new DatabaseHelper.RecurringPayment(
                            java.util.UUID.randomUUID().toString(), email, "Netflix", 15.99, "MONTHLY", "2026-07-12"
                    );
                    dbHelper.insertRecurringPayment(rec1);
                    dbHelper.insertRecurringPayment(rec2);
                    prefs.edit().putBoolean("seeded_recurring_payments", true).apply();
                    recs = dbHelper.getRecurringPayments(email);
                }

                android.content.Context ctx = getContext();
                float density = getResources().getDisplayMetrics().density;

                for (final DatabaseHelper.RecurringPayment rec : recs) {
                    com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(ctx);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.bottomMargin = (int) (12 * density);
                    card.setLayoutParams(cardParams);
                    card.setRadius(20 * density);
                    card.setCardElevation(2 * density);
                    card.setStrokeWidth(0);
                    card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));

                    RelativeLayout inner = new RelativeLayout(ctx);
                    inner.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
                    inner.setLayoutParams(new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

                    com.google.android.material.card.MaterialCardView iconCard = new com.google.android.material.card.MaterialCardView(ctx);
                    iconCard.setId(View.generateViewId());
                    RelativeLayout.LayoutParams iconCardParams = new RelativeLayout.LayoutParams((int) (40 * density), (int) (40 * density));
                    iconCardParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                    iconCardParams.addRule(RelativeLayout.CENTER_VERTICAL);
                    iconCard.setLayoutParams(iconCardParams);
                    iconCard.setRadius(20 * density);
                    iconCard.setCardElevation(0);
                    iconCard.setStrokeWidth(0);
                    iconCard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF5F8FC));

                    ImageView iv = new ImageView(ctx);
                    android.widget.FrameLayout.LayoutParams ivParams = new android.widget.FrameLayout.LayoutParams((int) (20 * density), (int) (20 * density));
                    ivParams.gravity = android.view.Gravity.CENTER;
                    iv.setLayoutParams(ivParams);
                    if (rec.name.toLowerCase().contains("netflix") || rec.name.toLowerCase().contains("video")) {
                        iv.setImageResource(R.drawable.ic_video);
                    } else {
                        iv.setImageResource(R.drawable.ic_bills);
                    }
                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF9DA3B4));
                    iconCard.addView(iv);
                    inner.addView(iconCard);

                    LinearLayout middle = new LinearLayout(ctx);
                    middle.setOrientation(LinearLayout.VERTICAL);
                    RelativeLayout.LayoutParams middleParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    middleParams.addRule(RelativeLayout.END_OF, iconCard.getId());
                    middleParams.addRule(RelativeLayout.CENTER_VERTICAL);
                    middleParams.leftMargin = (int) (12 * density);

                    middle.setLayoutParams(middleParams);

                    TextView tvTitle = new TextView(ctx);
                    tvTitle.setText(rec.name);
                    tvTitle.setTextColor(0xFF1A1C24);
                    tvTitle.setTextSize(14);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    middle.addView(tvTitle);

                    TextView tvSub = new TextView(ctx);
                    tvSub.setText(rec.frequency.toUpperCase() + " • " + rec.dueDate.toUpperCase());
                    tvSub.setTextColor(0xFF9DA3B4);
                    tvSub.setTextSize(9);
                    tvSub.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    subParams.topMargin = (int) (2 * density);
                    tvSub.setLayoutParams(subParams);
                    middle.addView(tvSub);
                    inner.addView(middle);

                    TextView tvAmount = new TextView(ctx);
                    tvAmount.setText("-" + formatAmount(ctx, rec.amount));
                    tvAmount.setTextColor(0xFF1A1C24);
                    tvAmount.setTextSize(14);
                    tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
                    RelativeLayout.LayoutParams amountParams = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
                    );
                    amountParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                    amountParams.addRule(RelativeLayout.CENTER_VERTICAL);
                    tvAmount.setLayoutParams(amountParams);
                    inner.addView(tvAmount);

                    card.addView(inner);
                    card.setOnClickListener(v -> showRecurringPaymentOptionsDialog(rec));
                    layoutRecurringPayments.addView(card);
                }
            }
            if (getActivity() instanceof Main_Activity) {
                ((Main_Activity) getActivity()).updateNotificationBadge(view);
            }
            translateView(getContext(), view);
        }

        private void showSavingGoalOptionsDialog(final DatabaseHelper.SavingGoal goal) {
            if (getActivity() == null) return;
            String[] options = {"Edit Goal", "Delete Goal"};
            new AlertDialog.Builder(getActivity())
                    .setTitle(goal.name)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showAddEditSavingGoalDialog(goal);
                        } else if (which == 1) {
                            new AlertDialog.Builder(getActivity())
                                    .setTitle("Delete Goal")
                                    .setMessage("Are you sure you want to delete this saving goal?")
                                    .setPositiveButton("Yes", (d, w) -> {
                                        DatabaseHelper.getInstance(getActivity()).deleteSavingGoal(goal.id);
                                        refreshData(rootView);
                                    })
                                    .setNegativeButton("No", null)
                                    .show();
                        }
                    })
                    .show();
        }

        private void showAddEditSavingGoalDialog(final DatabaseHelper.SavingGoal goal) {
            if (getActivity() == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(goal == null ? "Add Saving Goal" : "Edit Saving Goal");

            View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_saving_goal, null);
            final android.widget.EditText etName = dialogView.findViewById(R.id.etGoalName);
            final android.widget.EditText etTarget = dialogView.findViewById(R.id.etGoalTarget);
            final android.widget.EditText etCurrent = dialogView.findViewById(R.id.etGoalCurrent);

            if (goal != null) {
                etName.setText(goal.name);
                etTarget.setText(String.format(Locale.US, "%.2f", goal.targetAmount));
                etCurrent.setText(String.format(Locale.US, "%.2f", goal.currentAmount));
            }

            builder.setView(dialogView);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String targetStr = etTarget.getText().toString().trim();
                String currentStr = etCurrent.getText().toString().trim();

                if (name.isEmpty() || targetStr.isEmpty() || currentStr.isEmpty()) {
                    Toast.makeText(getActivity(), "All fields are required", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double target = Double.parseDouble(targetStr);
                    double current = Double.parseDouble(currentStr);

                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
                    SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                    String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

                    if (goal == null) {
                        DatabaseHelper.SavingGoal newGoal = new DatabaseHelper.SavingGoal(
                                java.util.UUID.randomUUID().toString(), email, name, target, current
                        );
                        dbHelper.insertSavingGoal(newGoal);
                    } else {
                        goal.name = name;
                        goal.targetAmount = target;
                        goal.currentAmount = current;
                        dbHelper.updateSavingGoal(goal);
                    }
                    refreshData(rootView);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "Invalid input format", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        private void showRecurringPaymentOptionsDialog(final DatabaseHelper.RecurringPayment rec) {
            if (getActivity() == null) return;
            String[] options = {"Edit Payment", "Delete Payment"};
            new AlertDialog.Builder(getActivity())
                    .setTitle(rec.name)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showAddEditRecurringPaymentDialog(rec);
                        } else if (which == 1) {
                            new AlertDialog.Builder(getActivity())
                                    .setTitle("Delete Recurring Payment")
                                    .setMessage("Are you sure you want to delete this payment?")
                                    .setPositiveButton("Yes", (d, w) -> {
                                        DatabaseHelper.getInstance(getActivity()).deleteRecurringPayment(rec.id);
                                        refreshData(rootView);
                                    })
                                    .setNegativeButton("No", null)
                                    .show();
                        }
                    })
                    .show();
        }

        private void showAddEditRecurringPaymentDialog(final DatabaseHelper.RecurringPayment rec) {
            if (getActivity() == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(rec == null ? "Add Recurring Payment" : "Edit Recurring Payment");

            View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_recurring_payment, null);
            final android.widget.EditText etName = dialogView.findViewById(R.id.etRecName);
            final android.widget.EditText etAmount = dialogView.findViewById(R.id.etRecAmount);
            final android.widget.Spinner spFreq = dialogView.findViewById(R.id.spRecFreq);
            final android.widget.EditText etDue = dialogView.findViewById(R.id.etRecDue);

            // Populate Spinner options
            String[] freqOptions = {"MONTHLY", "WEEKLY"};
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    getActivity(), android.R.layout.simple_spinner_item, freqOptions
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spFreq.setAdapter(adapter);

            // Bind DatePickerDialog to etDue
            etDue.setFocusable(false);
            etDue.setClickable(true);
            etDue.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                String currentVal = etDue.getText().toString().trim();
                if (!currentVal.isEmpty() && currentVal.contains("-")) {
                    try {
                        String[] parts = currentVal.split("-");
                        year = Integer.parseInt(parts[0]);
                        month = Integer.parseInt(parts[1]) - 1;
                        day = Integer.parseInt(parts[2]);
                    } catch (Exception ignored) {}
                }

                android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(
                        getActivity(),
                        (view1, y, m, d) -> etDue.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                        year, month, day
                );
                dpd.show();
            });

            if (rec != null) {
                etName.setText(rec.name);
                etAmount.setText(String.format(Locale.US, "%.2f", rec.amount));
                etDue.setText(rec.dueDate);
                if ("WEEKLY".equalsIgnoreCase(rec.frequency)) {
                    spFreq.setSelection(1);
                } else {
                    spFreq.setSelection(0);
                }
            } else {
                spFreq.setSelection(0);
                // Pre-populate due date to today
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                etDue.setText(sdf.format(new java.util.Date()));
            }

            builder.setView(dialogView);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String amountStr = etAmount.getText().toString().trim();
                String freq = spFreq.getSelectedItem().toString();
                String due = etDue.getText().toString().trim();

                if (name.isEmpty() || amountStr.isEmpty() || due.isEmpty()) {
                    Toast.makeText(getActivity(), "All fields are required", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double amount = Double.parseDouble(amountStr);

                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
                    SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                    String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

                    if (rec == null) {
                        DatabaseHelper.RecurringPayment newRec = new DatabaseHelper.RecurringPayment(
                                java.util.UUID.randomUUID().toString(), email, name, amount, freq, due
                        );
                        dbHelper.insertRecurringPayment(newRec);
                    } else {
                        rec.name = name;
                        rec.amount = amount;
                        rec.frequency = freq;
                        rec.dueDate = due;
                        dbHelper.updateRecurringPayment(rec);
                    }
                    refreshData(rootView);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "Invalid amount format", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        private static int getCategoryIcon(String category) {
            return Main_Activity.getCategoryIcon(category);
        }

        private static int getCategoryColor(String category) {
            return Main_Activity.getCategoryColor(category);
        }

        private static int getCategoryIconTint(String category) {
            return Main_Activity.getCategoryIconTint(category);
        }

        private void showAddEditCategoryBudgetDialog(final DatabaseHelper.CategoryBudget cb) {
            if (getActivity() == null) return;
            android.content.SharedPreferences prefs = getActivity().getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE);
            String lang = prefs.getString("selected_language", "EN");
            boolean isKhmer = "KH".equals(lang);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(cb == null ? (isKhmer ? "បន្ថែមថវិកាប្រភេទចំណាយ" : "Add Category Budget") : (isKhmer ? "កែសម្រួលថវិកាប្រភេទចំណាយ" : "Edit Category Budget"));

            View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_category_budget, null);
            final android.widget.Spinner spCategory = dialogView.findViewById(R.id.spCbCategory);
            final android.widget.EditText etLimit = dialogView.findViewById(R.id.etCbLimit);

            String[] categories = {"Food", "Travel", "Shop", "Health", "Bills", "Fun", "Study", "Other"};
            String[] displayCategories = new String[categories.length];
            for (int i = 0; i < categories.length; i++) {
                displayCategories[i] = translateText(categories[i], isKhmer);
            }

            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    getActivity(), android.R.layout.simple_spinner_item, displayCategories
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spCategory.setAdapter(adapter);

            if (cb != null) {
                int selectIdx = 0;
                for (int i = 0; i < categories.length; i++) {
                    if (categories[i].equalsIgnoreCase(cb.category)) {
                        selectIdx = i;
                        break;
                    }
                }
                spCategory.setSelection(selectIdx);
                spCategory.setEnabled(false);
                etLimit.setText(String.format(Locale.US, "%.2f", cb.budgetLimit));
            }

            builder.setView(dialogView);

            builder.setPositiveButton(isKhmer ? "រក្សាទុក" : "Save", (dialog, which) -> {
                int selectedIndex = spCategory.getSelectedItemPosition();
                String category = categories[selectedIndex];
                String limitStr = etLimit.getText().toString().trim();

                if (limitStr.isEmpty()) {
                    Toast.makeText(getActivity(), isKhmer ? "សូមបញ្ចូលចំនួនទឹកប្រាក់" : "Limit is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double limit = Double.parseDouble(limitStr);

                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(getActivity());
                    SupabaseAuthManager authManager = SupabaseAuthManager.getInstance(getActivity());
                    String email = authManager.isLoggedIn() ? authManager.getUserEmail() : "guest";

                    if (cb == null) {
                        List<DatabaseHelper.CategoryBudget> existing = dbHelper.getCategoryBudgets(email);
                        for (DatabaseHelper.CategoryBudget existingCb : existing) {
                            if (existingCb.category.equalsIgnoreCase(category)) {
                                Toast.makeText(getActivity(), isKhmer ? "ប្រភេទចំណាយនេះមានថវិការួចហើយ" : "Budget for this category already exists", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        DatabaseHelper.CategoryBudget newCb = new DatabaseHelper.CategoryBudget(
                                java.util.UUID.randomUUID().toString(), email, category, limit
                        );
                        dbHelper.insertCategoryBudget(newCb);
                    } else {
                        cb.budgetLimit = limit;
                        dbHelper.updateCategoryBudget(cb);
                    }
                    refreshData(rootView);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), isKhmer ? "ទម្រង់មិនត្រឹមត្រូវ" : "Invalid limit format", Toast.LENGTH_SHORT).show();
                }
            });

            if (cb != null) {
                builder.setNeutralButton(isKhmer ? "លុប" : "Delete", (dialog, which) -> {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(isKhmer ? "លុបកម្រិតថវិកា" : "Delete Category Budget")
                            .setMessage(isKhmer ? "តើអ្នកប្រាកដជាចង់លុបថវិកានេះមែនទេ?" : "Are you sure you want to delete this category budget?")
                            .setPositiveButton(isKhmer ? "បាទ/ចាស" : "Yes", (d, w) -> {
                                DatabaseHelper.getInstance(getActivity()).deleteCategoryBudget(cb.id);
                                refreshData(rootView);
                            })
                            .setNegativeButton(isKhmer ? "ទេ" : "No", null)
                            .show();
                });
            }

            builder.setNegativeButton(isKhmer ? "បោះបង់" : "Cancel", null);
            builder.show();
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

            View btnNotif = rootView.findViewById(R.id.btnHeaderNotification);
            if (btnNotif != null) {
                btnNotif.setOnClickListener(v -> {
                    if (getActivity() instanceof Main_Activity) {
                        ((Main_Activity) getActivity()).showNotificationsDialog();
                    }
                });
            }

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
            if (getActivity() instanceof Main_Activity) {
                ((Main_Activity) getActivity()).updateNotificationBadge(view);
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

                    View btnNotif = view.findViewById(R.id.btnHeaderNotification);
                    if (btnNotif != null) {
                        btnNotif.setOnClickListener(v -> {
                            if (getActivity() instanceof Main_Activity) {
                                ((Main_Activity) getActivity()).showNotificationsDialog();
                            }
                        });
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

                    // 5. Bill Reminders Switch Listener
                    View layoutBillRemindersToggle = view.findViewById(R.id.layoutBillRemindersToggle);
                    if (layoutBillRemindersToggle != null) {
                        layoutBillRemindersToggle.setOnClickListener(v -> {
                            boolean current = prefs.getBoolean("bill_reminders_enabled", true);
                            prefs.edit().putBoolean("bill_reminders_enabled", !current).apply();
                            updateProfileUISettings(view);
                        });
                    }

                    // Initial UI State drawing
                    updateProfileUISettings(view);
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

                if (switchNotifBg != null && switchNotifThumb != null) {
                    if (notifEnabled) {
                        switchNotifBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0A5296));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchNotifThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        switchNotifThumb.setLayoutParams(params);
                    } else {
                        switchNotifBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD0D5DD));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchNotifThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                        params.addRule(RelativeLayout.ALIGN_PARENT_START);
                        switchNotifThumb.setLayoutParams(params);
                    }
                }

                // 5. Bill Reminders Switch Display
                boolean billRemindersEnabled = prefs.getBoolean("bill_reminders_enabled", true);
                View switchBillBg = view.findViewById(R.id.layoutBillRemindersToggle);
                View switchBillThumb = view.findViewById(R.id.switchBillRemindersThumb);

                if (switchBillBg != null && switchBillThumb != null) {
                    if (billRemindersEnabled) {
                        switchBillBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0A5296));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchBillThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                        params.addRule(RelativeLayout.ALIGN_PARENT_END);
                        switchBillThumb.setLayoutParams(params);
                    } else {
                        switchBillBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD0D5DD));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchBillThumb.getLayoutParams();
                        params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                        params.addRule(RelativeLayout.ALIGN_PARENT_START);
                        switchBillThumb.setLayoutParams(params);
                    }
                }

                if (getActivity() instanceof Main_Activity) {
                    ((Main_Activity) getActivity()).updateNotificationBadge(view);
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

        // Technical / brand words that must never change regardless of language
        switch (clean) {
            case "Netflix": return "Netflix";
            case "USD": return "USD";
            case "KHR": return "KHR";
            case "Supabase": return "Supabase";
        }

        if (isKhmer) {
            switch (clean) {
                // ---- Home Screen ----
                case "CURRENT BALANCE": return "សមតុល្យសរុប";
                case "Income": return "ចំណូល";
                case "Expense": return "ចំណាយ";
                case "TODAY": return "ថ្ងៃនេះ";
                case "THIS WEEK": return "សប្តាហ៍នេះ";
                case "THIS MONTH": return "ខែនេះ";
                case "SAVINGS RATE": return "ការសន្សំប្រចាំខែ";
                case "Categories": return "ប្រភេទ";
                case "MANAGE": return "គ្រប់គ្រង";
                case "Recent Transactions": return "ប្រតិបត្តិការចុងក្រោយ";
                case "VIEW ALL": return "មើលទាំងអស់";
                case "Search transactions...": return "ស្វែងរកប្រតិបត្តិការ...";
                case "No transactions recorded yet.": return "មិនទាន់មានការចំណាយណាមួយឡើយ។";
                case "Close": return "បិទ";
                case "Welcome Back": return "សូមស្វាគមន៍";

                // ---- Categories ----
                case "Food": return "អាហារ";
                case "Travel": return "ការធ្វើដំណើរ";
                case "Shop": return "ទំនិញ";
                case "Shopping": return "ការទិញទំនិញ";
                case "Health": return "សុខភាព";
                case "Bills": return "វិក្កយបត្រ";
                case "Fun": return "ការកម្សាន្ត";
                case "Study": return "ការសិក្សា";
                case "Salary": return "ប្រាក់ខែ";
                case "Bonus": return "ប្រាក់រង្វាន់";
                case "Freelance": return "ការងារឯករាជ្យ";
                case "Investment": return "ការវិនិយោគ";
                case "Savings": return "ប្រាក់សន្សំ";
                case "Other": return "ផ្សេងៗ";
                case "Others": return "ផ្សេងៗ";
                case "Transport": return "ការធ្វើដំណើរ";
                case "Food & Dining": return "អាហារ និង ភោជនីយដ្ឋាន";

                // ---- Greetings ----
                case "Good Morning": return "អរុណសួស្ដី";
                case "Good Afternoon": return "ទិវាសួស្ដី";
                case "Good Evening": return "សាយ័ណ្ហសួស្ដី";
                case "Good Night": return "រាត្រីសួស្ដី";

                // ---- Analysis Screen ----
                case "Analysis": return "ការវិភាគ";
                case "Daily": return "ប្រចាំថ្ងៃ";
                case "Weekly": return "ប្រចាំសប្ដាហ៍";
                case "Monthly": return "ប្រចាំខែ";
                case "Yearly": return "ប្រចាំឆ្នាំ";
                case "TOTAL SPENDING": return "ចំណាយសរុប";
                case "AVERAGE DAILY SPEND": return "ចំណាយជាមធ្យមប្រចាំថ្ងៃ";
                case "MOST ACTIVE DAY": return "ថ្ងៃចំណាយច្រើនជាងគេ";
                case "Category Breakdown": return "ការបែងចែកតាមប្រភេទ";
                case "No Data": return "គ្មានទិន្នន័យ";
                case "Spending Trend": return "និន្នាការចំណាយ";
                case "Weekly Breakdown": return "ការបែងចែកប្រចាំសប្ដាហ៍";
                case "Income vs Expense": return "ចំណូល និង ចំណាយ";
                case "Spending Behavior": return "ទំលាប់ការចំណាយ";
                case "Top Categories": return "ប្រភេទដែលចំណាយច្រើន";
                case "Most Active Day": return "ថ្ងៃដែលចំណាយច្រើន";
                case "Avg Daily Spend": return "ចំណាយជាមធ្យមប្រចាំថ្ងៃ";
                case "None": return "មិនមាន";
                case "0% of total spent": return "0% នៃចំណាយសរុប";
                case "Down 4% from last week": return "ថយចុះ 4% ពីសប្ដាហ៍មុន";
                // Day abbreviations
                case "Mon": return "ច";
                case "Tue": return "អ";
                case "Wed": return "ព";
                case "Thu": return "ព្រ";
                case "Fri": return "សុ";
                case "Sat": return "ស";
                case "Sun": return "អា";
                // Week labels
                case "w 1": return "ស 1";
                case "w 2": return "ស 2";
                case "w 3": return "ស 3";
                case "w 4": return "ស 4";
                // Month abbreviations (chart labels)
                case "Sept": return "កញ";
                case "Oct": return "តុ";
                case "Nov": return "វិ";

                // ---- Budget Screen & Saving Goals ----
                case "Budgets": return "ថវិការ";
                case "MONTHLY PROGRESS": return "វឌ្ឍនភាពខែនេះ";
                case "Monthly Progress": return "វឌ្ឍនភាពខែនេះ";
                case "Left": return "នៅសល់";
                case "Over": return "លើស";
                case "Category Budgets": return "ថវិការតាមប្រភេទ";
                case "Saving Goals": return "គោលដៅសន្សំ";
                case "Savings Goals": return "គោលដៅសន្សំ";
                case "Recurring Payments": return "ការបង់ប្រចាំ";
                case "Rent": return "ថ្លៃជួល";
                case "Laptop Saving": return "សន្សំសម្រាប់ Laptop";
                case "New Laptop": return "Laptop ថ្មី";

                // ---- Recurring indicators ----
                case "LATEST": return "ចុងក្រោយ";
                case "MONTHLY": return "ប្រចាំខែ";
                case "1ST OF MONTH": return "ថ្ងៃទី ១ ខែ";
                case "12TH OF MONTH": return "ថ្ងៃទី ១២ ខែ";
                case "MONTHLY • 1ST OF MONTH": return "ប្រចាំខែ • ថ្ងៃទី ១";
                case "MONTHLY • 12TH OF MONTH": return "ប្រចាំខែ • ថ្ងៃទី ១២";

                // ---- Profile & Settings ----
                case "Profile & Settings": return "ព័ត៌មានគណនី";
                case "PREFERENCES": return "ការកំណត់";
                case "Default Currency": return "រូបិយប័ណ្ណតំណាង";
                case "Exchange Rate": return "អត្រាប្ដូរប្រាក់";
                case "Language": return "ភាសា";
                case "Budget Alerts": return "ការជូនដំណឹងថវិការ";
                case "When approaching limits": return "ពេលជិតដល់ដែនកំណត់";
                case "Bill Reminders": return "រំលឹកវិក្កយបត្រ";
                case "Upcoming recurring expenses": return "ចំណាយដែលនឹងមកដល់";
                case "Logout": return "ចេញពីគណនី";
                case "Set Exchange Rate": return "កំណត់អត្រាប្ដូរប្រាក់";
                case "1 USD is equal to:": return "១ USD ស្មើនឹង៖";
                case "Manual rate set for local transactions.": return "អត្រាដែលកំណត់ដោយដៃ";
                case "English": return "ភាសាអង់គ្លេស";
                case "Khmer": return "ភាសាខ្មែរ";
            }
        } else {
            switch (clean) {
                // ---- Home Screen ----
                case "សមតុល្យសរុប": return "CURRENT BALANCE";
                case "ចំណូល": return "Income";
                case "ចំណាយ": return "Expense";
                case "ថ្ងៃនេះ": return "TODAY";
                case "សប្តាហ៍នេះ": return "THIS WEEK";
                case "ខែនេះ": return "THIS MONTH";
                case "ការសន្សំប្រចាំខែ": return "SAVINGS RATE";
                case "ប្រភេទ": return "Categories";
                case "គ្រប់គ្រង": return "MANAGE";
                case "ប្រតិបត្តិការចុងក្រោយ": return "Recent Transactions";
                case "មើលទាំងអស់": return "VIEW ALL";
                case "ស្វែងរកប្រតិបត្តិការ...": return "Search transactions...";
                case "មិនទាន់មានការចំណាយណាមួយឡើយ។": return "No transactions recorded yet.";
                case "បិទ": return "Close";
                case "សូមស្វាគមន៍": return "Welcome Back";

                // ---- Categories ----
                case "អាហារ": return "Food";
                case "ការធ្វើដំណើរ": return "Travel";
                case "ទំនិញ": return "Shop";
                case "ការទិញទំនិញ": return "Shopping";
                case "សុខភាព": return "Health";
                case "វិក្កយបត្រ": return "Bills";
                case "ការកម្សាន្ត": return "Fun";
                case "ការសិក្សា": return "Study";
                case "ប្រាក់ខែ": return "Salary";
                case "ប្រាក់រង្វាន់": return "Bonus";
                case "ការងារឯករាជ្យ": return "Freelance";
                case "ការវិនិយោគ": return "Investment";
                case "ប្រាក់សន្សំ": return "Savings";
                case "ផ្សេងៗ": return "Other";
                case "អាហារ និង ភោជនីយដ្ឋាន": return "Food & Dining";

                // ---- Greetings ----
                case "អរុណសួស្ដី": return "Good Morning";
                case "ទិវាសួស្ដី": return "Good Afternoon";
                case "សាយ័ណ្ហសួស្ដី": return "Good Evening";
                case "រាត្រីសួស្ដី": return "Good Night";

                // ---- Analysis Screen ----
                case "ការវិភាគ": return "Analysis";
                case "ប្រចាំថ្ងៃ": return "Daily";
                case "ប្រចាំសប្ដាហ៍": return "Weekly";
                case "ប្រចាំខែ": return "Monthly";
                case "ប្រចាំឆ្នាំ": return "Yearly";
                case "ចំណាយសរុប": return "TOTAL SPENDING";
                case "ចំណាយជាមធ្យមប្រចាំថ្ងៃ": return "AVERAGE DAILY SPEND";
                case "ថ្ងៃចំណាយច្រើនជាងគេ": return "MOST ACTIVE DAY";
                case "ការបែងចែកតាមប្រភេទ": return "Category Breakdown";
                case "គ្មានទិន្នន័យ": return "No Data";

                // ---- Budget Screen & Saving Goals ----
                case "ថវិការ": return "Budgets";
                case "វឌ្ឍនភាពខែនេះ": return "Monthly Progress";
                case "នៅសល់": return "Left";
                case "លើស": return "Over";
                case "ថវិការតាមប្រភេទ": return "Category Budgets";
                case "គោលដៅសន្សំ": return "Savings Goals";
                case "ការបង់ប្រចាំ": return "Recurring Payments";
                case "ថ្លៃជួល": return "Rent";
                case "សន្សំសម្រាប់ Laptop": return "Laptop Saving";

                // ---- Recurring indicators ----
                case "ចុងក្រោយ": return "LATEST";
                case "ថ្ងៃទី ១ ខែ": return "1ST OF MONTH";
                case "ថ្ងៃទី ១២ ខែ": return "12TH OF MONTH";
                case "ប្រចាំខែ • ថ្ងៃទី ១": return "MONTHLY • 1ST OF MONTH";
                case "ប្រចាំខែ • ថ្ងៃទី ១២": return "MONTHLY • 12TH OF MONTH";

                // ---- Analysis Screen (reverse) ----
                case "និន្នាការចំណាយ": return "Spending Trend";
                case "ការបែងចែកប្រចាំសប្ដាហ៍": return "Weekly Breakdown";
                case "ចំណូល និង ចំណាយ": return "Income vs Expense";
                case "ទំលាប់ការចំណាយ": return "Spending Behavior";
                case "ប្រភេទដែលចំណាយច្រើន": return "Top Categories";
                case "ថ្ងៃដែលចំណាយច្រើន": return "Most Active Day";
                case "មិនមាន": return "None";
                // Day abbreviations (reverse)
                case "ច": return "Mon";
                case "អ": return "Tue";
                case "ព": return "Wed";
                case "ព្រ": return "Thu";
                case "សុ": return "Fri";
                case "ស": return "Sat";
                case "អា": return "Sun";
                // Week labels (reverse)
                case "ស 1": return "w 1";
                case "ស 2": return "w 2";
                case "ស 3": return "w 3";
                case "ស 4": return "w 4";
                // Month labels (reverse)
                case "កញ": return "Sept";
                case "តុ": return "Oct";
                case "វិ": return "Nov";
                // Budget extras (reverse)
                case "Laptop ថ្មី": return "New Laptop";

                // ---- Profile & Settings (reverse) ----
                case "ព័ត៌មានគណនី": return "Profile & Settings";
                case "ការកំណត់": return "PREFERENCES";
                case "រូបិយប័ណ្ណតំណាង": return "Default Currency";
                case "អត្រាប្ដូរប្រាក់": return "Exchange Rate";
                case "ភាសា": return "Language";
                case "ការជូនដំណឹងថវិការ": return "Budget Alerts";
                case "ពេលជិតដល់ដែនកំណត់": return "When approaching limits";
                case "រំលឹកវិក្កយបត្រ": return "Bill Reminders";
                case "ចំណាយដែលនឹងមកដល់": return "Upcoming recurring expenses";
                case "ចេញពីគណនី": return "Logout";
                case "កំណត់អត្រាប្ដូរប្រាក់": return "Set Exchange Rate";
                case "១ USD ស្មើនឹង៖": return "1 USD is equal to:";
                case "អត្រាដែលកំណត់ដោយដៃ": return "Manual rate set for local transactions.";
                case "ភាសាអង់គ្លេស": return "English";
                case "ភាសាខ្មែរ": return "Khmer";
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

    public static class TransactionAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
        private final android.content.Context context;
        private final List<DatabaseHelper.Transaction> transactions;
        private final OnTransactionClickListener clickListener;

        public interface OnTransactionClickListener {
            void onTransactionClick(DatabaseHelper.Transaction tx);
        }

        public TransactionAdapter(android.content.Context context, List<DatabaseHelper.Transaction> transactions, OnTransactionClickListener clickListener) {
            this.context = context;
            this.transactions = transactions;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_transaction, parent, false);
            return new TransactionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
            final DatabaseHelper.Transaction tx = transactions.get(position);
            holder.tvTxTitle.setText(tx.note);
            holder.tvTxCategory.setText(translateText(tx.category, "KH".equals(context.getSharedPreferences("ExpenseTrackerPrefs", android.content.Context.MODE_PRIVATE).getString("selected_language", "EN"))));
            holder.tvTxDate.setText(tx.date);

            // Category styling using parent class helpers
            holder.cardTxIconBg.setCardBackgroundColor(HomeFragment.getCategoryBgColor(tx.category));
            holder.ivTxIcon.setImageResource(HomeFragment.getCategoryIcon(tx.category));
            holder.ivTxIcon.setImageTintList(ColorStateList.valueOf(HomeFragment.getCategoryIconTint(tx.category)));

            // Amount styling using activity-level static formatAmount
            if ("expense".equalsIgnoreCase(tx.type)) {
                holder.tvTxAmount.setText("-" + Main_Activity.formatAmount(context, tx.amount));
                holder.tvTxAmount.setTextColor(0xFFF44336); // Red
            } else {
                holder.tvTxAmount.setText("+" + Main_Activity.formatAmount(context, tx.amount));
                holder.tvTxAmount.setTextColor(0xFF4CAF50); // Green
            }

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTransactionClick(tx);
                }
            });
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        public static class TransactionViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final com.google.android.material.card.MaterialCardView cardTxIconBg;
            final ImageView ivTxIcon;
            final TextView tvTxTitle;
            final TextView tvTxDate;
            final TextView tvTxAmount;
            final TextView tvTxCategory;

            public TransactionViewHolder(@NonNull View itemView) {
                super(itemView);
                cardTxIconBg = itemView.findViewById(R.id.cardTxIconBg);
                ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
                tvTxTitle = itemView.findViewById(R.id.tvTxTitle);
                tvTxDate = itemView.findViewById(R.id.tvTxDate);
                tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                tvTxCategory = itemView.findViewById(R.id.tvTxCategory);
            }
        }
    }
}
