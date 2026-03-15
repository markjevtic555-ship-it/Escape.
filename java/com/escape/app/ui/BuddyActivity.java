package com.escape.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.model.BuddyAppStats;
import com.escape.app.model.BuddyUser;
import com.escape.app.model.UsageStats;
import com.escape.app.utils.FirebaseBuddyManager;

// buddy sistem - povezivanje sa drugim korisnicima
public class BuddyActivity extends BaseActivity implements FirebaseBuddyManager.BuddyUpdateListener {

    private View backButton;
    private LinearLayout connectionStatusBadge;
    private View statusDot;
    private TextView statusText;
    private LinearLayout buddyCodeCard;
    private TextView buddyCodeText;
    private LinearLayout copyCodeButton;
    private LinearLayout connectSection;
    private EditText buddyCodeInput;
    private TextView connectErrorText;
    private LinearLayout connectButton;
    private TextView connectButtonText;
    private ProgressBar connectProgress;
    private LinearLayout connectedSection;
    private TextView buddyInitial;
    private TextView buddyNameText;
    private TextView buddyEmailText;
    private View liveDot;
    private ImageView buddyCurrentAppIcon;
    private TextView buddyCurrentAppName;
    private TextView buddyCurrentAppDuration;
    private LinearLayout buddyAppsContainer;
    private LinearLayout penaltyCard;
    private TextView penaltyReasonText;
    private LinearLayout disconnectButton;

    private FirebaseBuddyManager buddyManager;
    private boolean isLoading = false;
    private BuddyUser currentUser;
    private ValueAnimator liveDotAnimator;
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private BuddyUser lastBuddyData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buddy);

        buddyManager = new FirebaseBuddyManager(this);

        // ako korisnik nije ulogovan, šaljem ga na login
        if (!buddyManager.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        loadUserData();
        animateEntrance();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        connectionStatusBadge = findViewById(R.id.connectionStatusBadge);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        
        buddyCodeCard = findViewById(R.id.buddyCodeCard);
        buddyCodeText = findViewById(R.id.buddyCodeText);
        copyCodeButton = findViewById(R.id.copyCodeButton);
        
        connectSection = findViewById(R.id.connectSection);
        buddyCodeInput = findViewById(R.id.buddyCodeInput);
        connectErrorText = findViewById(R.id.connectErrorText);
        connectButton = findViewById(R.id.connectButton);
        connectButtonText = findViewById(R.id.connectButtonText);
        connectProgress = findViewById(R.id.connectProgress);
        
        connectedSection = findViewById(R.id.connectedSection);
        buddyInitial = findViewById(R.id.buddyInitial);
        buddyNameText = findViewById(R.id.buddyNameText);
        buddyEmailText = findViewById(R.id.buddyEmailText);
        liveDot = findViewById(R.id.liveDot);
        buddyCurrentAppIcon = findViewById(R.id.buddyCurrentAppIcon);
        buddyCurrentAppName = findViewById(R.id.buddyCurrentAppName);
        buddyCurrentAppDuration = findViewById(R.id.buddyCurrentAppDuration);
        buddyAppsContainer = findViewById(R.id.buddyAppsContainer);
        penaltyCard = findViewById(R.id.penaltyCard);
        penaltyReasonText = findViewById(R.id.penaltyReasonText);
        disconnectButton = findViewById(R.id.disconnectButton);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        copyCodeButton.setOnClickListener(v -> copyBuddyCode());
        
        connectButton.setOnClickListener(v -> attemptConnect());
        
        disconnectButton.setOnClickListener(v -> confirmDisconnect());
    }

    private void loadUserData() {
        // učitavam podatke korisnika i generišem kod ako treba
        String userId = buddyManager.getCurrentUserId();
        if (userId != null && !userId.isEmpty()) {
            String immediateCode = generateBuddyCodeFromId(userId);
            buddyCodeText.setText(immediateCode);
        } else {
            buddyCodeText.setText(getString(R.string.loading));
        }
        
        buddyManager.getCurrentUserData(new FirebaseBuddyManager.UserDataCallback() {
            @Override
            public void onUserLoaded(BuddyUser user) {
                runOnUiThread(() -> {
                    if (user == null) {
                        buddyCodeText.setText(getString(R.string.error));
                        Toast.makeText(BuddyActivity.this, 
                            "Failed to load user data", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    currentUser = user;
                    
                    if (user.buddyCode == null || user.buddyCode.isEmpty()) {
                        String userId = user.odorId != null ? user.odorId : 
                            (buddyManager.getCurrentUserId() != null ? buddyManager.getCurrentUserId() : "");
                        if (!userId.isEmpty()) {
                            String code = generateBuddyCodeFromId(userId);
                            user.buddyCode = code;
                        } else {
                            user.buddyCode = "ERROR";
                        }
                    }
                    
                    updateUI(user);
                    
                    if (user.hasBuddy()) {
                        buddyManager.startBuddyListener(BuddyActivity.this);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    String userId = buddyManager.getCurrentUserId();
                    if (userId != null && !userId.isEmpty()) {
                        String code = generateBuddyCodeFromId(userId);
                        buddyCodeText.setText(code);
                        Toast.makeText(BuddyActivity.this, 
                            "Using generated code (database error: " + error + ")", Toast.LENGTH_SHORT).show();
                    } else {
                        buddyCodeText.setText(getString(R.string.error));
                        Toast.makeText(BuddyActivity.this, 
                            "Error loading data: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private String generateBuddyCodeFromId(String userId) {
        if (userId == null || userId.length() < 8) {
            return "ESCAPE00";
        }
        StringBuilder code = new StringBuilder();
        code.append(userId.substring(0, 2).toUpperCase());
        code.append(userId.substring(userId.length() / 2, userId.length() / 2 + 2).toUpperCase());
        code.append(userId.substring(userId.length() - 4).toUpperCase());
        
        String result = code.toString().replaceAll("[^A-Z0-9]", "X");
        
        if (result.length() < 8) {
            result = result + "00000000".substring(0, 8 - result.length());
        } else if (result.length() > 8) {
            result = result.substring(0, 8);
        }
        
        return result;
    }

    private void updateUI(BuddyUser user) {
        buddyCodeText.setText(user.buddyCode);

        if (user.hasBuddy()) {
            showConnectedState(user);
        } else {
            showDisconnectedState();
        }
    }

    private void showConnectedState(BuddyUser user) {
        connectionStatusBadge.setBackgroundResource(R.drawable.buddy_status_badge_connected);
        statusDot.setBackgroundResource(R.drawable.status_dot_green);
        statusText.setText(getString(R.string.connected));
        statusText.setTextColor(0xFF4CAF50);

        buddyCodeCard.setVisibility(View.GONE);
        connectSection.setVisibility(View.GONE);
        connectedSection.setVisibility(View.VISIBLE);

        if (user.buddyEmail != null && !user.buddyEmail.isEmpty()) {
            String initial = user.buddyEmail.substring(0, 1).toUpperCase();
            buddyInitial.setText(initial);
            
            String name = user.buddyEmail.split("@")[0];
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            buddyNameText.setText(name);
            buddyEmailText.setText(user.buddyEmail);
        }

        startLiveDotAnimation();

        if (user.buddyId != null) {
            buddyManager.getBuddyData(user.buddyId, new FirebaseBuddyManager.UserDataCallback() {
                @Override
                public void onUserLoaded(BuddyUser buddy) {
                    runOnUiThread(() -> updateBuddyUsageUI(buddy));
                }

                @Override
                public void onError(String error) {
                }
            });
        }
    }

    private void showDisconnectedState() {
        connectionStatusBadge.setBackgroundResource(R.drawable.buddy_status_badge_disconnected);
        statusDot.setBackgroundResource(R.drawable.status_dot_red);
        statusText.setText(getString(R.string.not_connected));
        statusText.setTextColor(0xFFFF5252);

        buddyCodeCard.setVisibility(View.VISIBLE);
        connectSection.setVisibility(View.VISIBLE);
        connectedSection.setVisibility(View.GONE);

        stopLiveDotAnimation();
    }

    private void updateBuddyUsageUI(BuddyUser buddy) {
        lastBuddyData = buddy;

        if (buddy.currentAppPackage != null && !buddy.currentAppPackage.isEmpty() &&
            buddy.currentAppName != null && !buddy.currentAppName.isEmpty()) {
            buddyCurrentAppName.setText(buddy.currentAppName);
            buddyCurrentAppIcon.setVisibility(View.VISIBLE);
            loadAppIcon(buddy.currentAppPackage, buddyCurrentAppIcon);
        } else {
            buddyCurrentAppName.setText(getString(R.string.no_app_in_use));
            buddyCurrentAppIcon.setVisibility(View.GONE);
        }

        updateCurrentAppDuration(buddy);

        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastBuddyData != null) {
                    updateCurrentAppDuration(lastBuddyData);
                    updateHandler.postDelayed(this, 1000);
                }
            }
        };
        updateHandler.postDelayed(updateRunnable, 1000);

        if (buddyAppsContainer != null) {
            buddyAppsContainer.removeAllViews();

            if (buddy.restrictedApps == null || buddy.restrictedApps.isEmpty()) {
                TextView emptyView = new TextView(this);
                emptyView.setText(getString(R.string.no_restricted_apps));
                emptyView.setTextColor(0xFFAAAAAA);
                emptyView.setTextSize(13);
                emptyView.setGravity(Gravity.CENTER_HORIZONTAL);
                buddyAppsContainer.addView(emptyView);
            } else {
                int index = 0;
                for (BuddyAppStats stats : buddy.restrictedApps.values()) {
                    addBuddyAppCard(stats, index++);
                }
            }
        }

        if (buddy.penaltyActive) {
            penaltyCard.setVisibility(View.VISIBLE);
            String lockedAppName = buddyManager != null ? buddyManager.getLockedPenaltyAppName() : null;
            if (lockedAppName != null && !lockedAppName.isEmpty()) {
                penaltyReasonText.setText(
                    getString(R.string.buddy_penalty_locked_app, lockedAppName)
                );
            } else {
                penaltyReasonText.setText(getString(R.string.buddy_exceeded_one_limit));
            }
        } else {
            penaltyCard.setVisibility(View.GONE);
        }
    }
    
    private void loadAppIcon(String packageName, ImageView targetView) {
        if (packageName == null || packageName.isEmpty()) {
            targetView.setVisibility(View.GONE);
            return;
        }

        targetView.setVisibility(View.VISIBLE);

        PackageManager pm = getPackageManager();

        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            targetView.setImageDrawable(icon);
            return;
        } catch (PackageManager.NameNotFoundException e) {
        } catch (Exception e) {
        }

        try {
            android.graphics.drawable.GradientDrawable placeholder =
                new android.graphics.drawable.GradientDrawable();
            placeholder.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            placeholder.setColor(0xFF64B5F6);
            targetView.setImageDrawable(placeholder);
        } catch (Exception ex) {
        }
    }

    private void updateCurrentAppDuration(BuddyUser buddy) {
        if (buddy != null && buddy.currentAppPackage != null && !buddy.currentAppPackage.isEmpty() &&
            buddy.currentAppStartTime > 0) {
            long durationMs = System.currentTimeMillis() - buddy.currentAppStartTime;
            if (durationMs < 0) {
                durationMs = 0;
            }
            long totalSeconds = durationMs / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                buddyCurrentAppDuration.setText(String.format("%dh %dm", hours, minutes));
            } else if (minutes > 0) {
                buddyCurrentAppDuration.setText(String.format("%dm %ds", minutes, seconds));
            } else {
                buddyCurrentAppDuration.setText(String.format("%ds", seconds));
            }
        } else {
            buddyCurrentAppDuration.setText("0m");
        }
    }

    private void addBuddyAppCard(BuddyAppStats stats, int index) {
        if (stats == null) return;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.stats_app_card_premium);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dpToPx(12));

        FrameLayout iconContainer = new FrameLayout(this);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
            dpToPx(48), dpToPx(48));
        iconContainer.setLayoutParams(iconContainerParams);

        ImageView iconView = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
            dpToPx(48), dpToPx(48));
        iconParams.gravity = Gravity.CENTER;
        iconView.setLayoutParams(iconParams);
        iconContainer.addView(iconView);

        loadAppIcon(stats.packageName, iconView);

        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(dpToPx(14), 0, dpToPx(8), 0);
        infoLayout.setLayoutParams(infoParams);

        TextView appName = new TextView(this);
        appName.setText(stats.appName != null ? stats.appName : stats.packageName);
        appName.setTextColor(Color.WHITE);
        appName.setTextSize(16);
        appName.setTypeface(null, android.graphics.Typeface.BOLD);
        appName.setMaxLines(1);
        appName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        long limitMs = stats.dailyLimitMinutes * 60L * 1000L;
        double percentage = limitMs > 0 ? (stats.usageTodayMs / (double) limitMs) * 100 : 0;

        TextView usageText = new TextView(this);
        usageText.setText(UsageStats.formatTime(stats.usageTodayMs) + " / " +
            stats.dailyLimitMinutes + " min");
        usageText.setTextColor(Color.parseColor("#A0A0B8"));
        usageText.setTextSize(13);
        usageText.setPadding(0, dpToPx(2), 0, 0);

        infoLayout.addView(appName);
        infoLayout.addView(usageText);

        topRow.addView(iconContainer);
        topRow.addView(infoLayout);

        // progress bar sekcija
        LinearLayout progressSection = new LinearLayout(this);
        progressSection.setOrientation(LinearLayout.VERTICAL);

        // progress bar pozadina
        FrameLayout progressContainer = new FrameLayout(this);
        LinearLayout.LayoutParams progressContainerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6));
        progressContainer.setLayoutParams(progressContainerParams);

        GradientDrawable progressBg = new GradientDrawable();
        progressBg.setShape(GradientDrawable.RECTANGLE);
        progressBg.setColor(Color.parseColor("#1A1A2E"));
        progressBg.setCornerRadius(dpToPx(3));
        progressContainer.setBackground(progressBg);

        // progress bar fill
        View progressFill = new View(this);
        float progressRatio = Math.min(1f, (float) percentage / 100f);
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(
            0, FrameLayout.LayoutParams.MATCH_PARENT);
        progressFill.setLayoutParams(fillParams);

        GradientDrawable fillDrawable = new GradientDrawable();
        fillDrawable.setShape(GradientDrawable.RECTANGLE);
        fillDrawable.setCornerRadius(dpToPx(3));

        if (percentage >= 100) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FF6B6B"),
                Color.parseColor("#EE5A5A")
            });
        } else if (percentage >= 80) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FFA94D"),
                Color.parseColor("#FF922B")
            });
        } else if (percentage >= 50) {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#FFE066"),
                Color.parseColor("#FFD43B")
            });
        } else {
            fillDrawable.setColors(new int[]{
                Color.parseColor("#69DB7C"),
                Color.parseColor("#51CF66")
            });
        }
        fillDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        progressFill.setBackground(fillDrawable);

        progressContainer.addView(progressFill);

        // percentage label
        LinearLayout percentageRow = new LinearLayout(this);
        percentageRow.setOrientation(LinearLayout.HORIZONTAL);
        percentageRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams percentRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        percentRowParams.setMargins(0, dpToPx(6), 0, 0);
        percentageRow.setLayoutParams(percentRowParams);

        TextView percentageText = new TextView(this);
        percentageText.setText(String.format(java.util.Locale.getDefault(), "%.0f%% used", Math.min(percentage, 999)));
        percentageText.setTextSize(11);

        if (percentage >= 100) {
            percentageText.setTextColor(Color.parseColor("#FF6B6B"));
        } else if (percentage >= 80) {
            percentageText.setTextColor(Color.parseColor("#FFA94D"));
        } else if (percentage >= 50) {
            percentageText.setTextColor(Color.parseColor("#FFE066"));
        } else {
            percentageText.setTextColor(Color.parseColor("#69DB7C"));
        }

        percentageRow.addView(percentageText);

        progressSection.addView(progressContainer);
        progressSection.addView(percentageRow);

        card.addView(topRow);
        card.addView(progressSection);

        buddyAppsContainer.addView(card);

        // animiram progress bar fill
        card.post(() -> {
            int targetWidth = (int) (progressContainer.getWidth() * progressRatio);
            ValueAnimator animator = ValueAnimator.ofInt(0, targetWidth);
            animator.setDuration(800);
            animator.setStartDelay(index * 100L + 300);
            animator.setInterpolator(new DecelerateInterpolator(2f));
            animator.addUpdateListener(animation -> {
                fillParams.width = (int) animation.getAnimatedValue();
                progressFill.setLayoutParams(fillParams);
            });
            animator.start();
        });

        // entrance animacija
        card.setAlpha(0f);
        card.setTranslationY(dpToPx(20));
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(index * 80L + 200)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();
    }

    private void copyBuddyCode() {
        String codeToCopy = null;
        
        if (currentUser != null && currentUser.buddyCode != null && !currentUser.buddyCode.isEmpty()) {
            codeToCopy = currentUser.buddyCode;
        } else {
            String userId = buddyManager.getCurrentUserId();
            if (userId != null && !userId.isEmpty()) {
                codeToCopy = generateBuddyCodeFromId(userId);
            }
        }
        
        if (codeToCopy == null || codeToCopy.isEmpty()) {
            Toast.makeText(this, "Unable to get buddy code", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Buddy Code", codeToCopy);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Code copied to clipboard!", Toast.LENGTH_SHORT).show();

        // animiram dugme
        copyCodeButton.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction(() -> 
                copyCodeButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            )
            .start();
    }

    private void attemptConnect() {
        if (isLoading) return;

        connectErrorText.setVisibility(View.GONE);

        String code = buddyCodeInput.getText().toString().trim().toUpperCase();

        if (code.isEmpty()) {
            showConnectError("Please enter a buddy code");
            shakeView(buddyCodeInput);
            return;
        }

        if (code.length() != 8) {
            showConnectError("Buddy code must be 8 characters");
            shakeView(buddyCodeInput);
            return;
        }

        // provjeravam da li pokušava da koristi svoj kod
        if (currentUser != null && code.equals(currentUser.buddyCode)) {
            showConnectError("You cannot connect to yourself!");
            shakeView(buddyCodeInput);
            return;
        }

        setConnectLoading(true);
        
        // dodajem timeout da sprečim infinite loading
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            if (isLoading) {
                setConnectLoading(false);
                showConnectError("Connection timed out. Please check your internet and try again.");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 15000); // 15 sekundi timeout

        buddyManager.connectToBuddy(code, new FirebaseBuddyManager.BuddyCallback() {
            @Override
            public void onSuccess() {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                runOnUiThread(() -> {
                    setConnectLoading(false);
                    Toast.makeText(BuddyActivity.this, 
                        "Connected successfully!", Toast.LENGTH_SHORT).show();
                    buddyCodeInput.setText("");
                    
                    // ponovo učitavam podatke korisnika da ažuriram ekran
                    loadUserData();
                });
            }

            @Override
            public void onError(String error) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                runOnUiThread(() -> {
                    setConnectLoading(false);
                    android.util.Log.e("BuddyActivity", "Connection error: " + error);
                    
                    showConnectError(error);
                });
            }
        });
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.disconnect_buddy)
            .setMessage(R.string.disconnect_buddy_message)
            .setPositiveButton(R.string.disconnect, (dialog, which) -> performDisconnect())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void performDisconnect() {
        buddyManager.disconnectBuddy(new FirebaseBuddyManager.BuddyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(BuddyActivity.this, 
                        R.string.disconnected_from_buddy, Toast.LENGTH_SHORT).show();
                    showDisconnectedState();
                    currentUser.buddyId = null;
                    currentUser.buddyEmail = null;
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(BuddyActivity.this, 
                        "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setConnectLoading(boolean loading) {
        isLoading = loading;
        connectButtonText.setVisibility(loading ? View.GONE : View.VISIBLE);
        connectProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        connectButton.setAlpha(loading ? 0.7f : 1.0f);
        buddyCodeInput.setEnabled(!loading);
    }

    private void showConnectError(String message) {
        connectErrorText.setText(message);
        connectErrorText.setVisibility(View.VISIBLE);
        
        connectErrorText.setAlpha(0f);
        connectErrorText.animate()
            .alpha(1f)
            .setDuration(200)
            .start();
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 
            0, 10, -10, 10, -10, 5, -5, 0);
        shake.setDuration(400);
        shake.start();
    }

    private void startLiveDotAnimation() {
        if (liveDotAnimator != null && liveDotAnimator.isRunning()) return;

        liveDotAnimator = ValueAnimator.ofFloat(1f, 0.3f, 1f);
        liveDotAnimator.setDuration(1500);
        liveDotAnimator.setRepeatCount(ValueAnimator.INFINITE);
        liveDotAnimator.setInterpolator(new LinearInterpolator());
        liveDotAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            liveDot.setAlpha(alpha);
        });
        liveDotAnimator.start();
    }

    private void stopLiveDotAnimation() {
        if (liveDotAnimator != null) {
            liveDotAnimator.cancel();
            liveDotAnimator = null;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void animateEntrance() {
        View content = findViewById(android.R.id.content);
        content.setAlpha(0f);
        content.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    @Override
    public void onBuddyDataChanged(BuddyUser buddy) {
        runOnUiThread(() -> {
            // logujem za debugging
            android.util.Log.d("BuddyActivity", "Buddy data changed - App: " + 
                (buddy.currentAppName != null ? buddy.currentAppName : "null") + 
                ", Package: " + (buddy.currentAppPackage != null ? buddy.currentAppPackage : "null") +
                ", StartTime: " + buddy.currentAppStartTime);
            updateBuddyUsageUI(buddy);
        });
    }

    @Override
    public void onBuddyDisconnected() {
        runOnUiThread(() -> {
            showDisconnectedState();
            Toast.makeText(this, "Your buddy disconnected", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPenaltyTriggered(String reason) {
        runOnUiThread(() -> {
            penaltyCard.setVisibility(View.VISIBLE);
            penaltyReasonText.setText(reason);
            
            Toast.makeText(this, 
                getString(R.string.penalty_activated, reason), Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onOneHourNotification(String appName, String message) {
        runOnUiThread(() -> {
            // prikazujem notifikaciju
            android.app.NotificationManager notificationManager = 
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "buddy_notifications", getString(R.string.buddy_notifications_channel), 
                    android.app.NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(channel);
            }
            
            // kreiram cropped platypus icon za notifikaciju – agresivno cropujem da popunim krug
            android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.ic_platypus);
            android.graphics.Bitmap largeIcon = null;
            if (original != null) {
                try {
                    int width = original.getWidth();
                    int height = original.getHeight();
                    // agresivno cropujem: koristim 85% da zumiram na platypusa, uklanjam padding
                    int cropSize = (int) (Math.min(width, height) * 0.85f);
                    int x = (width - cropSize) / 2;
                    int y = (height - cropSize) / 2;
                    android.graphics.Bitmap cropped = android.graphics.Bitmap.createBitmap(original, x, y, cropSize, cropSize);
                    largeIcon = android.graphics.Bitmap.createScaledBitmap(cropped, 256, 256, true);
                    if (cropped != original) {
                        cropped.recycle();
                    }
                    if (original != largeIcon) {
                        original.recycle();
                    }
                } catch (Exception e) {
                    largeIcon = original; // fallback na original
                }
            }
            
            android.app.Notification notification = new android.app.Notification.Builder(this, "buddy_notifications")
                .setContentTitle(getString(R.string.buddy_alert))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_platypus)
                .setLargeIcon(largeIcon)
                .setAutoCancel(true)
                .build();
            
            notificationManager.notify(1001, notification);
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null && currentUser.hasBuddy()) {
            buddyManager.startBuddyListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLiveDotAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        buddyManager.removeListeners();
        stopLiveDotAnimation();
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}

