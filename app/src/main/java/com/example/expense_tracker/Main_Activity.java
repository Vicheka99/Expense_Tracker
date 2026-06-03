package com.example.expense_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class Main_Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.main_activity);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_analysis) {
                selectedFragment = new AnalysisFragment();
            } else if (id == R.id.nav_wallet) {
                // CHANGED: Now uses your custom BudgetFragment layout
                selectedFragment = new BudgetFragment();
            } else if (id == R.id.nav_profile) {
                selectedFragment = new PlaceholderFragment.ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Main_Activity.this, AddTransactionActivity.class);
            startActivity(intent);
        });
    }

    // Home Fragment Class
    public static class HomeFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_home, container, false);
        }
    }

    // Budget Fragment Class (Spending Limits)
    public static class BudgetFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            // This now successfully links to your spending limit layout
            return inflater.inflate(R.layout.fragment_budget, container, false);
        }
    }

    // Analysis Fragment Class
    public static class AnalysisFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_analysis, container, false);
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
                return inflater.inflate(R.layout.fragment_profile, container, false);
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_placeholder, container, false);
            TextView textView = view.findViewById(R.id.page_title);
            if (getArguments() != null) {
                textView.setText(getArguments().getString(ARG_TITLE));
            }
            return view;
        }
    }
}
