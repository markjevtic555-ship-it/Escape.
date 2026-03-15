package com.escape.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.escape.app.R;
import com.escape.app.model.AppRestriction;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.FirebaseBuddyManager;
import com.escape.app.utils.AppUsageStatsManager;
import com.escape.app.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;

import java.util.Calendar;
import java.util.Random;

// full-screen blocking aktivnost - prikazuje se kad je aplikacija blokirana
public class BlockingActivity extends BaseActivity {
    // citati za random izbor
    private static final int[] QUOTE_RESOURCE_IDS = {
        R.string.quote_0_text,
        R.string.quote_1_text,
        R.string.quote_2_text,
        R.string.quote_3_text,
        R.string.quote_4_text,
        R.string.quote_5_text,
        R.string.quote_6_text,
        R.string.quote_7_text
    };

    // tipovi igara za random izbor
    private static final int GAME_SUDOKU = 0;
    private static final int GAME_WORDLE = 1;
    private static final int GAME_CONNECT_FOUR = 2;
    private static final int GAME_2048 = 3;
    private static final int TOTAL_GAMES = 4;

    private TextView quoteTextView;
    private TextView timeRemainingTextView;
    private TextView appNameTextView;
    private TextView buddyPenaltyBanner;
    private MaterialButton exitButton;
    private MaterialButton playGameButton;
    private MaterialButton kairosPurchaseButton;
    private FrameLayout blockedIconContainer;

    private PreferencesManager preferencesManager;
    private AppUsageStatsManager usageStatsManager;
    private String blockedPackageName;
    private boolean dueToPenalty = false;
    private FirebaseBuddyManager buddyManager;
    private CountDownTimer countDownTimer;
    private Handler handler;
    private Random random;
    private boolean isGameActive = false;
    private BroadcastReceiver gameReceiver;


    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // sakrivam action bar da uklonim "Escape" naslov
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_blocking);

        // pravim aktivnost full screen i sprečavam zatvaranje
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setFinishOnTouchOutside(false);

        handler = new Handler(Looper.getMainLooper());
        random = new Random();

        initializeViews();
        setupBlocking();
        setupGameReceiver();
        animateEntrance();
    }


    private void initializeViews() {
        quoteTextView = findViewById(R.id.quoteTextView);
        timeRemainingTextView = findViewById(R.id.timeRemainingTextView);
        appNameTextView = findViewById(R.id.appNameTextView);
        buddyPenaltyBanner = findViewById(R.id.buddyPenaltyBanner);
        exitButton = findViewById(R.id.exitButton);
        playGameButton = findViewById(R.id.playGameButton);
        kairosPurchaseButton = findViewById(R.id.kairosPurchaseButton);
        blockedIconContainer = findViewById(R.id.blockedIconContainer);
    }

    private void setupBlocking() {
        preferencesManager = new PreferencesManager(this);
        usageStatsManager = new AppUsageStatsManager(this);
        blockedPackageName = getIntent().getStringExtra("blocked_package");
        dueToPenalty = getIntent().getBooleanExtra("due_to_penalty", false);
        buddyManager = new FirebaseBuddyManager(this);

        // prikazujem informacije o blokiranoj aplikaciji
        AppRestriction restriction = preferencesManager.getAppRestriction(blockedPackageName);
        if (restriction != null) {
            appNameTextView.setText(restriction.getAppName());
            updateTimeRemaining(restriction);
        }

        // prikazujem buddy penalty banner ako je ovaj blok zbog buddy sistema penaltyja
        if (dueToPenalty && buddyPenaltyBanner != null) {
            buddyPenaltyBanner.setVisibility(View.VISIBLE);
        } else if (buddyPenaltyBanner != null) {
            buddyPenaltyBanner.setVisibility(View.GONE);
        }

        // prikazujem random motivacioni citat
        displayRandomQuote();
        
        // updateujem citat na latin ako treba (poslije što je setovan)
        if (LocaleHelper.getLanguage(this).equals("sr-Latn") && quoteTextView != null) {
            String currentQuote = quoteTextView.getText().toString();
            if (currentQuote.startsWith("\"") && currentQuote.endsWith("\"")) {
                // uklanjam quotes za lookup
                String quoteText = currentQuote.substring(1, currentQuote.length() - 1);
                Context appContext = getApplicationContext();
                String latinQuote = LocaleHelper.getLatinStringForCyrillic(appContext, quoteText);
                if (latinQuote != null && !latinQuote.equals(quoteText)) {
                    quoteTextView.setText("\"" + latinQuote + "\"");
                }
            }
        }

        // postavljam exit dugme
        exitButton.setOnClickListener(v -> {
            animateButtonPress(v);
            exitAppAndOverlay();
        });

        // postavljam random game dugme
        playGameButton.setOnClickListener(v -> {
            animateButtonPress(v);
            launchRandomGame();
        });

        // postavljam Kairos purchase dugme (+5 minuta za 1 Kairos)
        if (kairosPurchaseButton != null) {
            kairosPurchaseButton.setOnClickListener(v -> {
                animateButtonPress(v);
                handleKairosPurchase();
            });
        }

        // pokrećem countdown timer
        startCountdownTimer();
    }

    // kupovina Kairosa: ako korisnik ima bar 1 Kairos, trošim ga i dajem 5 minuta
    // bonus vremena. Ako nema dovoljno Kairosa, prikazujem poruku
    private void handleKairosPurchase() {
        if (preferencesManager == null || blockedPackageName == null) return;

        int currentKairos = preferencesManager.getKairosBalance();
        if (currentKairos < 1) {
            // nema dovoljno Kairosa
            Toast toast = Toast.makeText(
                this,
                getString(R.string.not_enough_kairos),
                Toast.LENGTH_LONG
            );
            View view = toast.getView();
            if (view != null) {
                view.setBackgroundColor(0xFF1B1B2F); // deep navy background
                if (view instanceof android.widget.LinearLayout) {
                    for (int i = 0; i < ((android.widget.LinearLayout) view).getChildCount(); i++) {
                        View child = ((android.widget.LinearLayout) view).getChildAt(i);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(0xFFFFD93D); // Kairos yellow
                        }
                    }
                }
            }
            toast.show();
            return;
        }

        // ima dovoljno Kairosa: trošim 1 i dajem 5-minutni bonus na ovu aplikaciju
        preferencesManager.addKairos(-1);

        AppRestriction restriction = preferencesManager.getAppRestriction(blockedPackageName);
        if (restriction != null) {
            // 5 minuta u ms, koristim isto polje kao Sudoku bonus
            long existingBonus = restriction.getSudokuBonusRemainingMillis();
            restriction.setSudokuBonusRemainingMillis(existingBonus + (5 * 60 * 1000L));
            preferencesManager.saveAppRestriction(restriction);

            // dajem immediate feedback
            Toast.makeText(
                this,
                getString(R.string.purchased_5_minutes),
                Toast.LENGTH_LONG
            ).show();
        }

        // zatvaram overlay da korisnik može da se vrati u aplikaciju
        exitAppAndOverlay();
    }

    private void animateEntrance() {
        // animiram blokirani icon
        if (blockedIconContainer != null) {
            blockedIconContainer.setScaleX(0f);
            blockedIconContainer.setScaleY(0f);
            blockedIconContainer.setAlpha(0f);
            
            blockedIconContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        }

        // animiram citat
        if (quoteTextView != null) {
            quoteTextView.setAlpha(0f);
            quoteTextView.setTranslationY(20f);
            
            quoteTextView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200)
                .setDuration(400)
                .start();
        }

        // animiram dugmad
        if (playGameButton != null) {
            playGameButton.setAlpha(0f);
            playGameButton.setTranslationY(30f);
            
            playGameButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(300)
                .setDuration(400)
                .start();
        }
    }

    private void animateButtonPress(View v) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.95f);
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f, 1f);
        
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);
        scaleDown.setDuration(50);
        
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);
        scaleUp.setDuration(50);
        
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(scaleDown, scaleUp);
        set.start();
    }

    private void setupGameReceiver() {
        gameReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.escape.app.BONUS_TIME_AWARDED".equals(action)) {
                    String packageName = intent.getStringExtra("blocked_package");
                    if (blockedPackageName != null && blockedPackageName.equals(packageName)) {
                        AppRestriction restriction = preferencesManager.getAppRestriction(blockedPackageName);
                        if (restriction != null && !restriction.isLimitExceeded()) {
                            finish();
                        }
                    }
                } else if ("com.escape.app.SUDOKU_GAME_STARTED".equals(action) ||
                           "com.escape.app.WORDLE_GAME_STARTED".equals(action)) {
                    isGameActive = true;
                } else if ("com.escape.app.SUDOKU_GAME_ENDED".equals(action) ||
                           "com.escape.app.WORDLE_GAME_ENDED".equals(action)) {
                    isGameActive = false;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.escape.app.BONUS_TIME_AWARDED");
        filter.addAction("com.escape.app.SUDOKU_GAME_STARTED");
        filter.addAction("com.escape.app.SUDOKU_GAME_ENDED");
        filter.addAction("com.escape.app.WORDLE_GAME_STARTED");
        filter.addAction("com.escape.app.WORDLE_GAME_ENDED");
        LocalBroadcastManager.getInstance(this).registerReceiver(gameReceiver, filter);
    }

    /**
     * Launch a random game
     */
    private void launchRandomGame() {
        int gameType = random.nextInt(TOTAL_GAMES);
        
        switch (gameType) {
            case GAME_SUDOKU:
                launchSudokuGame();
                break;
            case GAME_WORDLE:
                launchWordleGame();
                break;
            case GAME_CONNECT_FOUR:
                launchConnectFourGame();
                break;
            case GAME_2048:
                launch2048Game();
                break;
        }
    }

    private void launchSudokuGame() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        Intent intent = new Intent(this, SudokuGameActivity.class);
        intent.putExtra("blocked_package", blockedPackageName);
        startActivity(intent);
    }

    private void launchWordleGame() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.WORDLE_GAME_STARTED"));
        
        Intent intent = new Intent(this, WordleGameActivity.class);
        intent.putExtra("blocked_package", blockedPackageName);
        startActivity(intent);
    }

    private void launchConnectFourGame() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        Intent intent = new Intent(this, ConnectFourGameActivity.class);
        intent.putExtra("blocked_package", blockedPackageName);
        startActivity(intent);
    }

    private void launch2048Game() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        Intent intent = new Intent(this, Game2048Activity.class);
        intent.putExtra("blocked_package", blockedPackageName);
        startActivity(intent);
    }

    private void displayRandomQuote() {
        int quoteIndex = random.nextInt(QUOTE_RESOURCE_IDS.length);
        String quote = getString(QUOTE_RESOURCE_IDS[quoteIndex]);
        quoteTextView.setText("\"" + quote + "\"");
    }

    private void startCountdownTimer() {
        countDownTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                AppRestriction restriction = preferencesManager.getAppRestriction(blockedPackageName);
                if (restriction != null) {
                    updateTimeRemaining(restriction);
                }
            }

            @Override
            public void onFinish() {
            }
        }.start();
    }

    private void updateTimeRemaining(AppRestriction restriction) {
        long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.add(Calendar.DAY_OF_YEAR, 1);      // move to next day
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long millisToMidnight = cal.getTimeInMillis() - now;
        if (millisToMidnight < 0) millisToMidnight = 0;

        long hours = millisToMidnight / (1000 * 60 * 60);
        long minutes = (millisToMidnight / (1000 * 60)) % 60;
        long seconds = (millisToMidnight / 1000) % 60;

        String timeString;
        if (hours > 0) {
            timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeString = String.format("%02d:%02d", minutes, seconds);
        }

        timeRemainingTextView.setText(timeString);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isGameActive) {
            return;
        }

        AppRestriction restriction = preferencesManager.getAppRestriction(blockedPackageName);

        // za normalne (non-penalty) blokove:
        // zadržavam overlay dokle god je REAL usage za danas na/iznad limita i
        // nema aktivnog bonus vremena (Sudoku / Kairos).
        // za buddy-penalty blokove: zadržavam overlay dokle god je penalty flag aktivan.
        if (!dueToPenalty) {
            if (restriction == null) {
                // restrikcija je uklonjena dok je overlay prikazan; zatvaram ga.
                finish();
                return;
            }

            // ako je bonus aktivan, korisnik je kupio/zaradio vreme -> zatvaram overlay
            if (restriction.isSudokuBonusActive()) {
                finish();
                return;
            }

            if (usageStatsManager != null) {
                long usedTodayMs = usageStatsManager.getAppUsageTimeToday(blockedPackageName);
                long limitMs = restriction.getDailyLimitMinutes() * 60 * 1000L;

                // ako je korisnik pao ispod limita (npr. limit promijenjen), zatvaram overlay
                if (limitMs <= 0 || usedTodayMs < limitMs) {
                    finish();
                    return;
                }
            }
        } else {
            // buddy penalty: auto-zatvaram samo kad kombinovani penalty flag više nije aktivan.
            boolean penaltyActive = buddyManager != null && buddyManager.isPenaltyActive();
            if (!penaltyActive) {
                finish();
                return;
            }
        }

        handler.postDelayed(() -> {
            if (!isFinishing() && !isGameActive) {
                bringToFront();
            }
        }, 100);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // sprečavam back dugme da zatvori aktivnost
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            case KeyEvent.KEYCODE_APP_SWITCH:
                return true;
            case KeyEvent.KEYCODE_HOME:
                exitAppAndOverlay();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (gameReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(gameReceiver);
        }
    }

    private void extendGracePeriod() {
        Intent intent = new Intent("com.escape.app.EXTEND_GRACE_PERIOD");
        intent.putExtra("blocked_package", blockedPackageName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void exitAppAndOverlay() {
        extendGracePeriod();

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);

        finish();
    }

    private void bringToFront() {
        Intent intent = new Intent(this, BlockingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                       Intent.FLAG_ACTIVITY_NEW_TASK |
                       Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("blocked_package", blockedPackageName);
        startActivity(intent);
    }
}
