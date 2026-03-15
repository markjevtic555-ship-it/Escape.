package com.escape.app.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.escape.app.R;
import com.escape.app.adapter.AppListAdapter;
import com.escape.app.model.AppInfo;
import com.escape.app.model.AppRestriction;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.AppUsageStatsManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// izbor aplikacija i postavljanje limita
public class AppSelectionActivity extends BaseActivity implements AppListAdapter.OnAppClickListener {

    private RecyclerView appsRecyclerView;
    private Button saveButton;
    private TextView kairosBalanceTextView;
    private TextView appsCountText;
    private TextView restrictedCountText;
    private android.widget.EditText searchEditText;

    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private List<AppInfo> filteredAppList;
    private PreferencesManager preferencesManager;
    private AppUsageStatsManager usageStatsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        // sakrivam action bar za čistiji izgled
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeUtilities();
        initializeViews();
        setupRecyclerView();
        loadAppList();
        updateKairosBalance();
        updateCounts();
    }

    // inicijalizujem utility klase
    private void initializeUtilities() {
        preferencesManager = new PreferencesManager(this);
        usageStatsManager = new AppUsageStatsManager(this);
    }

    // inicijalizujem UI komponente
    private void initializeViews() {
        appsRecyclerView = findViewById(R.id.appsRecyclerView);
        saveButton = findViewById(R.id.saveButton);
        kairosBalanceTextView = findViewById(R.id.kairosBalanceTextView);
        appsCountText = findViewById(R.id.appsCountText);
        restrictedCountText = findViewById(R.id.restrictedCountText);
        searchEditText = findViewById(R.id.searchEditText);

        // postavljam click listenere
        saveButton.setOnClickListener(v -> finish());

        // postavljam search funkcionalnost
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // postavljam RecyclerView
    private void setupRecyclerView() {
        appsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        appList = new ArrayList<>();
        filteredAppList = new ArrayList<>();
        adapter = new AppListAdapter(filteredAppList, preferencesManager, this);
        appsRecyclerView.setAdapter(adapter);
    }

    // učitavam listu instaliranih aplikacija
    private void loadAppList() {
        appList.clear();

        // uzimam samo korisnički instalabilne aplikacije (isključuje sistemske i one bez launchera)
        List<AppInfo> userApps = usageStatsManager.getUserApps();

        // sortiram po imenu aplikacije
        userApps.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

        appList.addAll(userApps);
        filteredAppList.clear();
        filteredAppList.addAll(appList);
        adapter.notifyDataSetChanged();
    }

    /**
     * Filter apps based on search query
     */
    private void filterApps(String query) {
        filteredAppList.clear();
        
        if (TextUtils.isEmpty(query)) {
            filteredAppList.addAll(appList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : appList) {
                if (app.getAppName().toLowerCase().contains(lowerQuery) ||
                    app.getPackageName().toLowerCase().contains(lowerQuery)) {
                    filteredAppList.add(app);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
        updateCounts();
    }

    /**
     * Handle app click - show dialog to set time limit
     */
    @Override
    public void onAppClicked(AppInfo app) {
        showSetLimitDialog(app);
    }

    /**
     * Show premium dialog to set time limit for an app with wheel picker
     */
    private void showSetLimitDialog(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_set_limit, null);
        builder.setView(dialogView);

        // uzimam viewove
        ImageView dialogAppIcon = dialogView.findViewById(R.id.dialogAppIcon);
        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialogView.findViewById(R.id.dialogMessage);
        NumberPicker hoursPicker = dialogView.findViewById(R.id.hoursPicker);
        NumberPicker minutesPicker = dialogView.findViewById(R.id.minutesPicker);
        TextView quickSelect15 = dialogView.findViewById(R.id.quickSelect15);
        TextView quickSelect30 = dialogView.findViewById(R.id.quickSelect30);
        TextView quickSelect1h = dialogView.findViewById(R.id.quickSelect1h);
        TextView quickSelect2h = dialogView.findViewById(R.id.quickSelect2h);
        Button buttonCancel = dialogView.findViewById(R.id.buttonCancel);
        Button buttonSave = dialogView.findViewById(R.id.buttonSave);
        Button buttonRemoveLimit = dialogView.findViewById(R.id.buttonRemoveLimit);

        // postavljam informacije o aplikaciji
        dialogAppIcon.setImageDrawable(app.getIcon());
        dialogTitle.setText(app.getAppName());
        dialogMessage.setText(R.string.set_limit_message);

        // konfiguriram hours picker (0-23)
        hoursPicker.setMinValue(0);
        hoursPicker.setMaxValue(23);
        hoursPicker.setWrapSelectorWheel(true);
        styleNumberPicker(hoursPicker);

        // konfiguriram minutes picker (0, 5, 10, 15, ... 55)
        String[] minuteValues = new String[12];
        for (int i = 0; i < 12; i++) {
            minuteValues[i] = String.format("%02d", i * 5);
        }
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(11);
        minutesPicker.setDisplayedValues(minuteValues);
        minutesPicker.setWrapSelectorWheel(true);
        styleNumberPicker(minutesPicker);

        // pre-populiram sa postojećim limitom ako postoji
        AppRestriction existingRestriction = preferencesManager.getAppRestriction(app.getPackageName());
        if (existingRestriction != null && existingRestriction.getDailyLimitMinutes() > 0) {
            int totalMinutes = existingRestriction.getDailyLimitMinutes();
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            hoursPicker.setValue(hours);
            minutesPicker.setValue(minutes / 5); // konvertujem u index
            buttonRemoveLimit.setVisibility(View.VISIBLE);
        } else {
            // default na 1 sat
            hoursPicker.setValue(1);
            minutesPicker.setValue(0);
            buttonRemoveLimit.setVisibility(View.GONE);
        }

        // updateujem dialog viewove za latin ako treba
        updateDialogViewsForLatin(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // pravim dialog window background transparent da se vide rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // quick select dugmad
        View.OnClickListener quickSelectListener = v -> {
            int id = v.getId();
            if (id == R.id.quickSelect15) {
                hoursPicker.setValue(0);
                minutesPicker.setValue(3); // 15 minutes = index 3
            } else if (id == R.id.quickSelect30) {
                hoursPicker.setValue(0);
                minutesPicker.setValue(6); // 30 minutes = index 6
            } else if (id == R.id.quickSelect1h) {
                hoursPicker.setValue(1);
                minutesPicker.setValue(0);
            } else if (id == R.id.quickSelect2h) {
                hoursPicker.setValue(2);
                minutesPicker.setValue(0);
            }
        };
        quickSelect15.setOnClickListener(quickSelectListener);
        quickSelect30.setOnClickListener(quickSelectListener);
        quickSelect1h.setOnClickListener(quickSelectListener);
        quickSelect2h.setOnClickListener(quickSelectListener);

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonSave.setOnClickListener(v -> {
            int hours = hoursPicker.getValue();
            int minutesIndex = minutesPicker.getValue();
            int minutes = minutesIndex * 5;
            int totalMinutes = hours * 60 + minutes;

            if (totalMinutes <= 0) {
                Toast.makeText(this, R.string.set_limit_error, Toast.LENGTH_SHORT).show();
                return;
            }

            long currentUsageMillis = 0;
            if (existingRestriction != null) {
                currentUsageMillis = existingRestriction.getTotalTimeUsedToday();
            } else {
                currentUsageMillis = usageStatsManager.getAppUsageTimeToday(app.getPackageName());
            }

            AppRestriction restriction;
            if (existingRestriction != null) {
                restriction = existingRestriction;
            } else {
                restriction = new AppRestriction(app.getPackageName(), app.getAppName());
            }

            restriction.setDailyLimitMinutes(totalMinutes);
            restriction.setTotalTimeUsedToday(currentUsageMillis);
            restriction.setEnabled(true);

            preferencesManager.saveAppRestriction(restriction);

            // Refresh adapter to show updated limit
            adapter.notifyDataSetChanged();
            updateKairosBalance();
            updateCounts();

            dialog.dismiss();
        });

        buttonRemoveLimit.setOnClickListener(v -> {
            preferencesManager.removeAppRestriction(app.getPackageName());
            adapter.notifyDataSetChanged();
            updateCounts();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void styleNumberPicker(NumberPicker picker) {
        try {
            Field selectionDivider = NumberPicker.class.getDeclaredField("mSelectionDivider");
            selectionDivider.setAccessible(true);
            selectionDivider.set(picker, new ColorDrawable(0xFF1A2A4A));

            Field selectionDividerHeight = NumberPicker.class.getDeclaredField("mSelectionDividerHeight");
            selectionDividerHeight.setAccessible(true);
            selectionDividerHeight.set(picker, 2);
        } catch (Exception e) {
        }
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof android.widget.EditText) {
                ((android.widget.EditText) child).setTextColor(Color.WHITE);
                ((android.widget.EditText) child).setTextSize(24);
            }
        }
    }

    /**
     * Update the Kairos balance display
     */
    private void updateKairosBalance() {
        int kairosBalance = preferencesManager.getKairosBalance();
        kairosBalanceTextView.setText(kairosBalance + " ⧖");
    }

    /**
     * Update the app counts display
     */
    private void updateCounts() {
        appsCountText.setText(getString(R.string.apps_available) + " (" + filteredAppList.size() + ")");
        
        int restrictedCount = preferencesManager.loadAppRestrictions().size();
        if (restrictedCount > 0) {
            restrictedCountText.setText(getString(R.string.restricted_count, restrictedCount));
            restrictedCountText.setVisibility(View.VISIBLE);
        } else {
            restrictedCountText.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKairosBalance();
        updateCounts();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
