package com.escape.app.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.escape.app.R;
import com.escape.app.model.AppRestriction;
import com.escape.app.model.FocusSession;
import com.escape.app.model.UsageStats;
import com.escape.app.model.BuddyUser;
import com.escape.app.service.AppMonitoringService;
import com.escape.app.utils.AppNotificationManager;
import com.escape.app.utils.FirebaseBuddyManager;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.AppUsageStatsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// glavna aktivnost - početni ekran aplikacije
public class MainActivity extends BaseActivity {
    private static final int REQUEST_USAGE_STATS = 1001;
    private static final int REQUEST_OVERLAY = 1002;
    private static final int REQUEST_NOTIFICATIONS = 1003;

    private LinearLayout selectAppsButton;
    private LinearLayout focusSessionButton;
    private LinearLayout statisticsButton;
    private TextView settingsButton;
    private TextView accountButton;
    private LinearLayout buddyButton;
    private View buddyStatusIndicator;
    private TextView buddyStatusText;
    private TextView statusTextView;
    private TextView statusSubtext;
    private View statusIndicator;
    private TextView appsTrackedCount;
    private TextView todayUsageText;
    private TextView quoteText;
    private TextView quoteAuthor;

    private AppUsageStatsManager usageStatsManager;
    private PreferencesManager preferencesManager;
    private AppNotificationManager notificationManager;
    private FirebaseBuddyManager buddyManager;

    private boolean serviceEnabled = false;

    private String[][] getQuotes() {
        return new String[][]{
            {getString(R.string.quote_0_text), getString(R.string.quote_0_author)},
            {getString(R.string.quote_1_text), getString(R.string.quote_1_author)},
            {getString(R.string.quote_2_text), getString(R.string.quote_2_author)},
            {getString(R.string.quote_3_text), getString(R.string.quote_3_author)},
            {getString(R.string.quote_4_text), getString(R.string.quote_4_author)},
            {getString(R.string.quote_5_text), getString(R.string.quote_5_author)},
            {getString(R.string.quote_6_text), getString(R.string.quote_6_author)},
            {getString(R.string.quote_7_text), getString(R.string.quote_7_author)}
        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);
            initializeUtilities();
            initializeViews();
            setupClickListeners();
            checkPermissionsAndStart();
            animateEntrance();
            setRandomQuote();

            Log.d("MainActivity", "onCreate completed successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "CRITICAL ERROR during activity initialization", e);
            Toast.makeText(this, getString(R.string.error_initializing_app, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        checkServiceStatus();
        updateQuickStats();
        updateBuddyStatus();
    }

    private void initializeUtilities() {
        // inicijalizujem sve potrebne menadžere
        usageStatsManager = new AppUsageStatsManager(this);
        preferencesManager = new PreferencesManager(this);
        notificationManager = new AppNotificationManager(this);
        buddyManager = new FirebaseBuddyManager(this);
        preferencesManager.checkAndPerformWeeklyKairosReset();
    }

    private void initializeViews() {
        selectAppsButton = findViewById(R.id.selectAppsButton);
        focusSessionButton = findViewById(R.id.focusSessionButton);
        statisticsButton = findViewById(R.id.statisticsButton);
        settingsButton = findViewById(R.id.settingsButton);
        accountButton = findViewById(R.id.accountButton);
        buddyButton = findViewById(R.id.buddyButton);
        buddyStatusIndicator = findViewById(R.id.buddyStatusIndicator);
        buddyStatusText = findViewById(R.id.buddyStatusText);
        statusTextView = findViewById(R.id.statusTextView);
        statusSubtext = findViewById(R.id.statusSubtext);
        statusIndicator = findViewById(R.id.statusIndicator);
        appsTrackedCount = findViewById(R.id.appsTrackedCount);
        todayUsageText = findViewById(R.id.todayUsageText);
        quoteText = findViewById(R.id.quoteText);
        quoteAuthor = findViewById(R.id.quoteAuthor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void setupClickListeners() {
        selectAppsButton.setOnClickListener(v -> openAppSelection());
        focusSessionButton.setOnClickListener(v -> toggleFocusSession());
        statisticsButton.setOnClickListener(v -> openStatistics());
        settingsButton.setOnClickListener(v -> openSettings());
        if (accountButton != null) {
            accountButton.setOnClickListener(v -> openAccount());
        }
        buddyButton.setOnClickListener(v -> openBuddySystem());

        View statusCard = findViewById(R.id.statusCard);
        if (statusCard != null) {
            statusCard.setOnClickListener(v -> {
                if (serviceEnabled) {
                    stopMonitoringService();
                } else {
                    checkPermissionsAndStart();
                }
            });
        }
    }

    private void animateEntrance() {
        View headerSection = findViewById(R.id.headerSection);
        if (headerSection != null) {
            headerSection.setAlpha(0f);
            headerSection.setTranslationY(-50f);
            headerSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
        }

        View statusCard = findViewById(R.id.statusCard);
        if (statusCard != null) {
            statusCard.setAlpha(0f);
            statusCard.setScaleX(0.9f);
            statusCard.setScaleY(0.9f);
            statusCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay(200)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        }

        View quickStatsRow = findViewById(R.id.quickStatsRow);
        if (quickStatsRow != null) {
            quickStatsRow.setAlpha(0f);
            quickStatsRow.setTranslationX(-30f);
            quickStatsRow.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }

        View[] actionCards = {selectAppsButton, focusSessionButton, statisticsButton, settingsButton, buddyButton};
        for (int i = 0; i < actionCards.length; i++) {
            if (actionCards[i] != null) {
                actionCards[i].setAlpha(0f);
                actionCards[i].setTranslationY(40f);
                actionCards[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(400 + (i * 80L))
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
            }
        }

        View quoteSection = findViewById(R.id.quoteSection);
        if (quoteSection != null) {
            quoteSection.setAlpha(0f);
            quoteSection.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(800)
                .start();
        }
    }

    private void setRandomQuote() {
        Random random = new Random();
        String[][] quotes = getQuotes();
        String[] quote = quotes[random.nextInt(quotes.length)];
        if (quoteText != null) {
            quoteText.setText("\"" + quote[0] + "\"");
        }
        if (quoteAuthor != null) {
            quoteAuthor.setText("— " + quote[1]);
        }
    }

    private void updateQuickStats() {
        // ažuriram brze statistike na početnom ekranu
        List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();
        if (appsTrackedCount != null) {
            appsTrackedCount.setText(String.valueOf(restrictions.size()));
        }

        long totalScreenTime = preferencesManager.getTotalScreenTimeToday();
        if (todayUsageText != null) {
            todayUsageText.setText(UsageStats.formatTime(totalScreenTime));
        }
    }

    private void checkPermissionsAndStart() {
        try {
            if (!hasUsageStatsPermission()) {
                requestUsageStatsPermission();
            } else if (!hasOverlayPermission()) {
                requestOverlayPermission();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                requestNotificationPermission();
            } else {
                startMonitoringService();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in checkPermissionsAndStart", e);
            Toast.makeText(this, getString(R.string.error_checking_permissions, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } else {
            return usageStatsManager.hasUsageStatsPermission();
        }
    }

    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestUsageStatsPermission() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.usage_stats_permission_message)
            .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivityForResult(intent, REQUEST_USAGE_STATS);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void requestOverlayPermission() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
    }

    private void startMonitoringService() {
        try {
            serviceEnabled = true;
            preferencesManager.setServiceEnabled(true);

            Intent serviceIntent = new Intent(this, AppMonitoringService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
            
            updateUI();
            Toast.makeText(this, getString(R.string.monitoring_started), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("MainActivity", "Error starting service", e);
            Toast.makeText(this, getString(R.string.error_starting_service), Toast.LENGTH_SHORT).show();
            serviceEnabled = false;
            preferencesManager.setServiceEnabled(false);
        }
    }

    private void stopMonitoringService() {
        serviceEnabled = false;
        preferencesManager.setServiceEnabled(false);

        Intent serviceIntent = new Intent(this, AppMonitoringService.class);
        stopService(serviceIntent);

        updateUI();
        Toast.makeText(this, getString(R.string.monitoring_stopped), Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        try {
            if (serviceEnabled) {
                statusTextView.setText(getString(R.string.service_active));
                statusSubtext.setText(getString(R.string.monitoring_apps));
                statusIndicator.setBackgroundResource(R.drawable.main_status_dot_active);
            } else {
                statusTextView.setText(getString(R.string.service_inactive));
                statusSubtext.setText(getString(R.string.tap_to_enable));
                statusIndicator.setBackgroundResource(R.drawable.main_status_dot_inactive);
            }
        } catch (Exception e) {
            Log.e("MainActivity", getString(R.string.error_updating_ui), e);
        }
    }

    private void checkServiceStatus() {
        serviceEnabled = preferencesManager.isServiceEnabled();
    }

    private void openAppSelection() {
        Intent intent = new Intent(this, AppSelectionActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void toggleFocusSession() {
        Intent intent = new Intent(this, FocusSessionActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openStatistics() {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openAccount() {
        Intent intent = new Intent(this, AccountActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openBuddySystem() {
        // proveravam da li je korisnik ulogovan, ako nije ide na login
        if (!buddyManager.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, BuddyActivity.class);
            startActivity(intent);
        }
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void updateBuddyStatus() {
        if (buddyManager.isLoggedIn()) {
            buddyManager.getCurrentUserData(new FirebaseBuddyManager.UserDataCallback() {
                @Override
                public void onUserLoaded(BuddyUser user) {
                    runOnUiThread(() -> {
                        if (user.hasBuddy()) {
                            buddyStatusIndicator.setVisibility(View.VISIBLE);
                            buddyStatusText.setText(getString(R.string.connected));
                            buddyStatusText.setTextColor(0xFF4CAF50);
                        } else {
                            buddyStatusIndicator.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        buddyStatusIndicator.setVisibility(View.GONE);
                    });
                }
            });
        } else {
            buddyStatusIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_USAGE_STATS) {
            if (hasUsageStatsPermission()) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this, getString(R.string.usage_stats_permission_required), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            if (hasOverlayPermission()) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this, getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show();
            }
        }
    }
}
