package com.escape.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.escape.app.R;
import com.escape.app.service.FocusSessionService;
import com.escape.app.utils.PreferencesManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//  Focus Session
public class FocusSessionActivity extends BaseActivity 
        implements FocusSessionService.OnTimerUpdateListener {

    private static final int REQUEST_DND_PERMISSION = 3001;
    private static final long ANIMATION_DURATION = 1500;
    
    // preference ključevi za focus statistike
    private static final String PREF_TOTAL_SESSIONS = "focus_total_sessions";
    private static final String PREF_TOTAL_FOCUS_MILLIS = "focus_total_millis";
    private static final String PREF_FOCUS_STREAK = "focus_streak";
    private static final String PREF_LAST_FOCUS_DATE = "focus_last_date";
    private static final String PREF_TODAY_FOCUS_MILLIS = "focus_today_millis";
    private static final String PREF_TODAY_FOCUS_DATE = "focus_today_date";
    private static final long DAILY_GOAL_MILLIS = 2 * 60 * 60 * 1000L; // 2 hours

    private FrameLayout rootLayout;
    private FrameLayout circleButton;
    private FrameLayout pulseRing;
    private View gradientOverlay;
    private TextView startTextView;
    private TextView startIconView;
    private TextView timerTextView;
    private TextView sessionStatusText;
    private TextView kairosBalanceTextView;
    private TextView clockTextView;
    private TextView tapHintText;
    private TextView totalSessionsText;
    private TextView totalTimeText;
    private TextView streakText;
    private TextView todayTimeText;
    private TextView todayPercentText;
    private TextView goalHoursText;
    private View todayProgressBar;
    private LinearLayout statsRow;
    private LinearLayout todaySessionsContainer;

    private FocusSessionService focusService;
    private PreferencesManager preferencesManager;
    private boolean serviceBound = false;
    private boolean sessionActive = false;
    private boolean endDialogVisible = false;
    private Dialog currentEndDialog = null;
    
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler pulseHandler = new Handler(Looper.getMainLooper());
    private ObjectAnimator pulseAnimator;
    private long sessionStartMillis = 0;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockDisplay();
            clockHandler.postDelayed(this, 1000);
        }
    };

    private int initialBackgroundColor;
    private int initialCircleColor;
    private int targetBackgroundColor;
    private int targetCircleColor;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FocusSessionService.FocusSessionBinder binder = 
                (FocusSessionService.FocusSessionBinder) service;
            focusService = binder.getService();
            serviceBound = true;
            focusService.setTimerUpdateListener(FocusSessionActivity.this);

            if (focusService.isSessionActive()) {
                sessionActive = true;
                showActiveSessionState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            focusService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus_session);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        preferencesManager = new PreferencesManager(this);
        
        initializeColors();
        initializeViews();
        loadFocusStats();
        setupClickListener();
        animateEntrance();
    }

    private void initializeColors() {
        initialBackgroundColor = ContextCompat.getColor(this, R.color.deep_blue);
        initialCircleColor = ContextCompat.getColor(this, R.color.focus_circle_initial);
        targetBackgroundColor = Color.parseColor("#000000");
        targetCircleColor = ContextCompat.getColor(this, R.color.focus_circle_active);
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.rootLayout);
        circleButton = findViewById(R.id.circleButton);
        pulseRing = findViewById(R.id.pulseRing);
        gradientOverlay = findViewById(R.id.gradientOverlay);
        startTextView = findViewById(R.id.startTextView);
        startIconView = findViewById(R.id.startIconView);
        timerTextView = findViewById(R.id.timerTextView);
        sessionStatusText = findViewById(R.id.sessionStatusText);
        kairosBalanceTextView = findViewById(R.id.kairosBalanceTextView);
        clockTextView = findViewById(R.id.clockTextView);
        tapHintText = findViewById(R.id.tapHintText);
        totalSessionsText = findViewById(R.id.totalSessionsText);
        totalTimeText = findViewById(R.id.totalTimeText);
        streakText = findViewById(R.id.streakText);
        todayTimeText = findViewById(R.id.todayTimeText);
        todayPercentText = findViewById(R.id.todayPercentText);
        goalHoursText = findViewById(R.id.goalHoursText);
        todayProgressBar = findViewById(R.id.todayProgressBar);
        statsRow = findViewById(R.id.statsRow);
        todaySessionsContainer = findViewById(R.id.todaySessionsContainer);

        updateKairosDisplay();
        updateClockDisplay();
        updateGoalDisplay();
        resetToInitialState();
    }

    private void updateKairosDisplay() {
        int balance = preferencesManager.getKairosBalance();
        kairosBalanceTextView.setText(balance + " ⧖");
    }

    private void updateGoalDisplay() {
        int goalHours = (int) (DAILY_GOAL_MILLIS / (1000 * 60 * 60));
        if (goalHoursText != null) {
            goalHoursText.setText(getString(R.string.goal_hours, goalHours));
        }
    }

    private void loadFocusStats() {
        SharedPreferences prefs = preferencesManager.getSharedPreferences();
        
        // ukupno sesija
        int totalSessions = prefs.getInt(PREF_TOTAL_SESSIONS, 0);
        totalSessionsText.setText(String.valueOf(totalSessions));
        
        // ukupno focus vremena
        long totalMillis = prefs.getLong(PREF_TOTAL_FOCUS_MILLIS, 0);
        totalTimeText.setText(formatDuration(totalMillis));
        
        // streak
        updateStreakDisplay();
        
        // današnji focus
        updateTodayDisplay();
    }

    private void updateStreakDisplay() {
        SharedPreferences prefs = preferencesManager.getSharedPreferences();
        int streak = prefs.getInt(PREF_FOCUS_STREAK, 0);
        streakText.setText(String.valueOf(streak));
    }

    private void updateTodayDisplay() {
        SharedPreferences prefs = preferencesManager.getSharedPreferences();
        
        // provjeravam da li treba da resetujem današnje statistike
        long todayStart = getTodayStartMillis();
        long lastDate = prefs.getLong(PREF_TODAY_FOCUS_DATE, 0);
        
        long todayMillis;
        if (lastDate < todayStart) {
            // novi dan – resetujem današnji focus
            todayMillis = 0;
            prefs.edit()
                .putLong(PREF_TODAY_FOCUS_MILLIS, 0)
                .putLong(PREF_TODAY_FOCUS_DATE, todayStart)
                .apply();
        } else {
            todayMillis = prefs.getLong(PREF_TODAY_FOCUS_MILLIS, 0);
        }
        
        todayTimeText.setText(formatDuration(todayMillis));
        
        int percent = (int) Math.min(100, (todayMillis * 100) / DAILY_GOAL_MILLIS);
        todayPercentText.setText(getString(R.string.percent_complete, percent));
        
        // animiram progress bar
        todayProgressBar.post(() -> {
            FrameLayout parent = (FrameLayout) todayProgressBar.getParent();
            int targetWidth = (int) (parent.getWidth() * percent / 100.0);
            
            ValueAnimator animator = ValueAnimator.ofInt(0, targetWidth);
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) todayProgressBar.getLayoutParams();
                params.width = (int) animation.getAnimatedValue();
                todayProgressBar.setLayoutParams(params);
            });
            animator.start();
        });
    }

    private long getTodayStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String formatDuration(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;
        
        if (hours > 0) {
            return hours + "." + (minutes * 10 / 60) + "h";
        } else {
            return minutes + "m";
        }
    }

    private void resetToInitialState() {
        rootLayout.setBackgroundColor(initialBackgroundColor);
        updateStatusBarColor(initialBackgroundColor);

        GradientDrawable circleDrawable = (GradientDrawable) circleButton.getBackground().mutate();
        circleDrawable.setColor(initialCircleColor);
        circleButton.setBackground(circleDrawable);

        startTextView.setAlpha(1f);
        startTextView.setVisibility(View.VISIBLE);
        startIconView.setAlpha(1f);
        startIconView.setVisibility(View.VISIBLE);
        timerTextView.setAlpha(0f);
        timerTextView.setVisibility(View.GONE);
        sessionStatusText.setAlpha(0f);
        sessionStatusText.setVisibility(View.GONE);

        circleButton.setClickable(true);
        if (clockTextView != null) {
            clockTextView.setAlpha(0f);
        }
        if (tapHintText != null) {
            tapHintText.setAlpha(1f);
        }
    }

    private void setupClickListener() {
        circleButton.setOnClickListener(v -> {
            if (!sessionActive) {
                animateButtonPress();
                checkDndPermissionAndStart();
            } else {
                showEndSessionConfirmation();
            }
        });
    }

    private void animateButtonPress() {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(circleButton, "scaleX", 1f, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(circleButton, "scaleY", 1f, 0.95f);
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(circleButton, "scaleX", 0.95f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(circleButton, "scaleY", 0.95f, 1f);
        
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);
        scaleDown.setDuration(100);
        
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);
        scaleUp.setDuration(100);
        
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(scaleDown, scaleUp);
        set.start();
    }

    private void animateEntrance() {
        // animiram stats kartice
        if (statsRow != null) {
            for (int i = 0; i < statsRow.getChildCount(); i++) {
                View child = statsRow.getChildAt(i);
                child.setAlpha(0f);
                child.setTranslationY(-20f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(100L * i)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
        }

        // animiram circle dugme
        if (circleButton != null) {
            circleButton.setScaleX(0.8f);
            circleButton.setScaleY(0.8f);
            circleButton.setAlpha(0f);
            circleButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(200)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }

        // animiram današnju karticu
        if (todaySessionsContainer != null) {
            todaySessionsContainer.setAlpha(0f);
            todaySessionsContainer.setTranslationY(30f);
            todaySessionsContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(400)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private void startPulseAnimation() {
        if (pulseRing == null) return;
        
        pulseAnimator = ObjectAnimator.ofFloat(pulseRing, "alpha", 0.3f, 0.8f, 0.3f);
        pulseAnimator.setDuration(2000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    private void showEndSessionConfirmation() {
        if (endDialogVisible && currentEndDialog != null && currentEndDialog.isShowing()) {
            return;
        }
        endDialogVisible = true;

        currentEndDialog = new Dialog(this, R.style.Theme_Escape_Dialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_end_session, null);
        currentEndDialog.setContentView(dialogView);
        currentEndDialog.setCancelable(false);
        currentEndDialog.setCanceledOnTouchOutside(false);
        
        // updateujem dialog viewove za latin ako treba
        updateDialogViewsForLatin(dialogView);

        currentEndDialog.findViewById(R.id.buttonYes).setOnClickListener(v -> {
            currentEndDialog.dismiss();
            endDialogVisible = false;
            endSessionAndNavigateBack();
        });

        currentEndDialog.findViewById(R.id.buttonNo).setOnClickListener(v -> {
            currentEndDialog.dismiss();
            endDialogVisible = false;
        });

        currentEndDialog.setOnDismissListener(d -> {
            endDialogVisible = false;
            currentEndDialog = null;
        });
        currentEndDialog.show();
    }

    private void checkDndPermissionAndStart() {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.dnd_permission_message)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivityForResult(intent, REQUEST_DND_PERMISSION);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    startFocusSession();
                })
                .show();
        } else {
            startFocusSession();
        }
    }

    private void startFocusSession() {
        sessionActive = true;
        sessionStartMillis = System.currentTimeMillis();

        Intent serviceIntent = new Intent(this, FocusSessionService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        runStartAnimations();
        startPulseAnimation();
    }

    private void runStartAnimations() {
        circleButton.setClickable(false);

        // sakrivam stats i today sekciju
        if (statsRow != null) {
            statsRow.animate().alpha(0f).setDuration(300).start();
        }
        if (todaySessionsContainer != null) {
            todaySessionsContainer.animate().alpha(0f).translationY(50f).setDuration(300).start();
        }
        if (tapHintText != null) {
            tapHintText.animate().alpha(0f).setDuration(200).start();
        }

        // background color animacija
        ValueAnimator backgroundAnimator = ValueAnimator.ofObject(
            new ArgbEvaluator(), initialBackgroundColor, targetBackgroundColor);
        backgroundAnimator.setDuration(ANIMATION_DURATION);
        backgroundAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            rootLayout.setBackgroundColor(color);
            updateStatusBarColor(color);
        });

        // gradient overlay fade out
        if (gradientOverlay != null) {
            gradientOverlay.animate().alpha(0f).setDuration(ANIMATION_DURATION).start();
        }

        // circle color animacija
        GradientDrawable circleDrawable = (GradientDrawable) circleButton.getBackground();
        ValueAnimator circleAnimator = ValueAnimator.ofObject(
            new ArgbEvaluator(), initialCircleColor, targetCircleColor);
        circleAnimator.setDuration(ANIMATION_DURATION);
        circleAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            circleDrawable.setColor(color);
        });

        // start text i icon fade out
        ObjectAnimator textFadeOut = ObjectAnimator.ofFloat(startTextView, "alpha", 1f, 0f);
        textFadeOut.setDuration(ANIMATION_DURATION / 3);
        ObjectAnimator iconFadeOut = ObjectAnimator.ofFloat(startIconView, "alpha", 1f, 0f);
        iconFadeOut.setDuration(ANIMATION_DURATION / 3);

        // timer i status fade in
        ObjectAnimator timerFadeIn = ObjectAnimator.ofFloat(timerTextView, "alpha", 0f, 1f);
        timerFadeIn.setDuration(ANIMATION_DURATION / 2);
        timerFadeIn.setStartDelay(ANIMATION_DURATION / 2);

        ObjectAnimator statusFadeIn = ObjectAnimator.ofFloat(sessionStatusText, "alpha", 0f, 1f);
        statusFadeIn.setDuration(ANIMATION_DURATION / 2);
        statusFadeIn.setStartDelay(ANIMATION_DURATION / 2);

        ObjectAnimator clockFadeIn = ObjectAnimator.ofFloat(clockTextView, "alpha", 0f, 1f);
        clockFadeIn.setDuration(ANIMATION_DURATION / 2);
        clockFadeIn.setStartDelay(ANIMATION_DURATION / 2);

        textFadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startTextView.setVisibility(View.GONE);
                startIconView.setVisibility(View.GONE);
                timerTextView.setVisibility(View.VISIBLE);
                sessionStatusText.setVisibility(View.VISIBLE);
            }
        });

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimator, circleAnimator, textFadeOut, iconFadeOut, 
            timerFadeIn, statusFadeIn, clockFadeIn);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (serviceBound && focusService != null) {
                    focusService.startSession();
                }
                circleButton.setClickable(true);
            }
        });
        animatorSet.start();
    }

    private void showActiveSessionState() {
        rootLayout.setBackgroundColor(targetBackgroundColor);
        updateStatusBarColor(targetBackgroundColor);
        
        if (gradientOverlay != null) {
            gradientOverlay.setAlpha(0f);
        }
        
        GradientDrawable circleDrawable = (GradientDrawable) circleButton.getBackground().mutate();
        circleDrawable.setColor(targetCircleColor);
        circleButton.setBackground(circleDrawable);
        
        startTextView.setVisibility(View.GONE);
        startIconView.setVisibility(View.GONE);
        timerTextView.setVisibility(View.VISIBLE);
        timerTextView.setAlpha(1f);
        sessionStatusText.setVisibility(View.VISIBLE);
        sessionStatusText.setAlpha(1f);
        
        if (clockTextView != null) {
            clockTextView.setAlpha(1f);
        }
        if (tapHintText != null) {
            tapHintText.setAlpha(0f);
        }
        if (statsRow != null) {
            statsRow.setAlpha(0f);
        }
        if (todaySessionsContainer != null) {
            todaySessionsContainer.setAlpha(0f);
        }
        
        circleButton.setClickable(true);
        startPulseAnimation();

        if (focusService != null) {
            updateTimerDisplay(focusService.getElapsedMillis());
        }
    }

    private void updateStatusBarColor(int color) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
    }

    @Override
    public void onTimerUpdate(long elapsedMillis) {
        runOnUiThread(() -> {
            updateTimerDisplay(elapsedMillis);
            updateSessionStatus(elapsedMillis);
        });
    }

    @Override
    public void onKairosEarned(int totalKairosThisSession) {
        runOnUiThread(() -> {
            preferencesManager.addKairos(1);
            updateKairosDisplay();
            Toast.makeText(this, getString(R.string.kairos_earned_message), Toast.LENGTH_LONG).show();
        });
    }

    private void updateTimerDisplay(long elapsedMillis) {
        long seconds = (elapsedMillis / 1000) % 60;
        long minutes = (elapsedMillis / (1000 * 60)) % 60;
        long hours = elapsedMillis / (1000 * 60 * 60);

        String timeString = String.format(Locale.getDefault(), 
            "%02d:%02d:%02d", hours, minutes, seconds);
        timerTextView.setText(timeString);
    }

    private void updateSessionStatus(long elapsedMillis) {
        long minutes = elapsedMillis / (1000 * 60);
        
        if (minutes < 5) {
            sessionStatusText.setText(getString(R.string.getting_started));
        } else if (minutes < 15) {
            sessionStatusText.setText(getString(R.string.building_momentum));
        } else if (minutes < 30) {
            sessionStatusText.setText(getString(R.string.in_the_zone));
        } else if (minutes < 60) {
            sessionStatusText.setText(getString(R.string.deep_focus_achieved));
        } else {
            sessionStatusText.setText(getString(R.string.focus_master));
        }
    }

    private void saveSessionStats(long sessionMillis) {
        SharedPreferences prefs = preferencesManager.getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        
        // povećavam ukupno sesija
        int totalSessions = prefs.getInt(PREF_TOTAL_SESSIONS, 0) + 1;
        editor.putInt(PREF_TOTAL_SESSIONS, totalSessions);
        
        // dodajem u ukupno focus vrijeme
        long totalMillis = prefs.getLong(PREF_TOTAL_FOCUS_MILLIS, 0) + sessionMillis;
        editor.putLong(PREF_TOTAL_FOCUS_MILLIS, totalMillis);
        
        // dodajem u današnje focus vrijeme
        long todayStart = getTodayStartMillis();
        long lastDate = prefs.getLong(PREF_TODAY_FOCUS_DATE, 0);
        long todayMillis;
        
        if (lastDate < todayStart) {
            todayMillis = sessionMillis;
        } else {
            todayMillis = prefs.getLong(PREF_TODAY_FOCUS_MILLIS, 0) + sessionMillis;
        }
        editor.putLong(PREF_TODAY_FOCUS_MILLIS, todayMillis);
        editor.putLong(PREF_TODAY_FOCUS_DATE, todayStart);
        
        // updateujem streak
        long lastFocusDate = prefs.getLong(PREF_LAST_FOCUS_DATE, 0);
        int streak = prefs.getInt(PREF_FOCUS_STREAK, 0);
        
        if (lastFocusDate < todayStart) {
            // provjeravam da li je jučer
            if (lastFocusDate >= todayStart - 86400000L) {
                streak++;
            } else {
                streak = 1;
            }
        }
        editor.putInt(PREF_FOCUS_STREAK, streak);
        editor.putLong(PREF_LAST_FOCUS_DATE, todayStart);
        
        editor.apply();
    }

    @Override
    public void onBackPressed() {
        if (sessionActive) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (sessionActive) {
            promptEndSessionAndReturnToFront();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (sessionActive && !isFinishing() && !isChangingConfigurations()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (sessionActive && !isFinishing()) {
                    promptEndSessionAndReturnToFront();
                }
            }, 100);
        }
    }

    private void promptEndSessionAndReturnToFront() {
        if (!endDialogVisible) {
            showEndSessionConfirmation();
        }
        Intent intent = new Intent(this, FocusSessionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void endSessionAndNavigateBack() {
        sessionActive = false;
        stopPulseAnimation();

        long sessionMillis = 0;
        if (serviceBound && focusService != null) {
            sessionMillis = focusService.getElapsedMillis();
            focusService.stopSession();
        }

        // čuvam statistike ako je sesija bila bar 1 minut
        if (sessionMillis >= 60000) {
            saveSessionStats(sessionMillis);
        }
        
        // poslije završetka sesije, vraćam se na glavni ekran
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (sessionActive && !endDialogVisible) {
            showEndSessionConfirmation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceBound && focusService != null) {
            focusService.setTimerUpdateListener(this);
        }
        clockHandler.postDelayed(clockRunnable, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulseAnimation();
        if (serviceBound) {
            if (focusService != null) {
                focusService.removeTimerUpdateListener();
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
        clockHandler.removeCallbacks(clockRunnable);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DND_PERMISSION) {
            startFocusSession();
        }
    }

    private void updateClockDisplay() {
        if (clockTextView == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        clockTextView.setText(sdf.format(new Date()));
    }
}
