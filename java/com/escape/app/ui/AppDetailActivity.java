package com.escape.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.escape.app.R;
import com.escape.app.model.AppRestriction;
import com.escape.app.model.UsageStats;
import com.escape.app.utils.AppDetailStatsManager;
import com.escape.app.utils.AppUsageStatsManager;
import com.escape.app.utils.PreferencesManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

// detaljna statistika za aplikaciju
public class AppDetailActivity extends BaseActivity {

    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_APP_NAME = "app_name";

    private String packageName;
    private String appName;

    private PreferencesManager preferencesManager;
    private AppUsageStatsManager usageStatsManager;
    private AppDetailStatsManager detailStatsManager;

    private ImageView appIconView;
    private TextView appNameText;
    private TextView packageNameText;
    private TextView todayUsageText;
    private TextView limitText;
    private View usageProgressBar;
    private TextView usagePercentText;
    private TextView opensCountText;
    private TextView avgSessionText;
    private TextView limitsExceededText;
    private TextView longestSessionText;
    private LinearLayout hourlyUsageGraphContainer;
    private LinearLayout weeklyTrendContainer;
    private LinearLayout weeklyLabelsContainer;
    private TextView peakHourText;
    private TextView peakHourValue;
    private TextView insightText;
    private TextView streakText;
    private TextView deleteLimitButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        appName = getIntent().getStringExtra(EXTRA_APP_NAME);

        if (packageName == null || appName == null) {
            finish();
            return;
        }

        initializeUtilities();
        initializeViews();
        loadAppDetails();
        animateEntrance();
    }

    private void initializeUtilities() {
        preferencesManager = new PreferencesManager(this);
        usageStatsManager = new AppUsageStatsManager(this);
        detailStatsManager = new AppDetailStatsManager(this);
    }

    private void initializeViews() {
        try {
            appIconView = findViewById(R.id.appIconView);
            appNameText = findViewById(R.id.appNameText);
            packageNameText = findViewById(R.id.packageNameText);
            todayUsageText = findViewById(R.id.todayUsageText);
            limitText = findViewById(R.id.limitText);
            usageProgressBar = findViewById(R.id.usageProgressBar);
            usagePercentText = findViewById(R.id.usagePercentText);
            opensCountText = findViewById(R.id.opensCountText);
            avgSessionText = findViewById(R.id.avgSessionText);
            limitsExceededText = findViewById(R.id.limitsExceededText);
            longestSessionText = findViewById(R.id.longestSessionText);
            hourlyUsageGraphContainer = findViewById(R.id.hourlyUsageGraphContainer);
            weeklyTrendContainer = findViewById(R.id.weeklyTrendContainer);
            weeklyLabelsContainer = findViewById(R.id.weeklyLabelsContainer);
            peakHourText = findViewById(R.id.peakHourText);
            peakHourValue = findViewById(R.id.peakHourValue);
            insightText = findViewById(R.id.insightText);
            streakText = findViewById(R.id.streakText);
            deleteLimitButton = findViewById(R.id.deleteLimitButton);

            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }

            View backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                backButton.setOnClickListener(v -> finish());
            }

            if (deleteLimitButton != null) {
                deleteLimitButton.setOnClickListener(v -> {
                    preferencesManager.removeAppRestriction(packageName);
                    showNoDataState();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAppDetails() {
        try {
            // učitavam ikonu aplikacije
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                if (appIconView != null) {
                    appIconView.setImageDrawable(icon);
                }
            } catch (PackageManager.NameNotFoundException e) {
                if (appIconView != null) {
                    appIconView.setImageResource(R.mipmap.ic_launcher);
                }
            }

            // postavljam informacije o aplikaciji
            if (appNameText != null) appNameText.setText(appName);
            if (packageNameText != null) packageNameText.setText(packageName);

            // učitavam restriction podatke
            AppRestriction restriction = preferencesManager.getAppRestriction(packageName);
            if (restriction == null) {
                showNoDataState();
                return;
            }

            // današnji usage
            long todayUsage = usageStatsManager.getAppUsageTimeToday(packageName);
            if (todayUsageText != null) todayUsageText.setText(UsageStats.formatTime(todayUsage));
            if (limitText != null) limitText.setText(getString(R.string.min_limit, restriction.getDailyLimitMinutes()));

            // usage progress
            long limitMillis = restriction.getDailyLimitMinutes() * 60 * 1000L;
            float progress = limitMillis > 0 ? Math.min(1f, (float) todayUsage / limitMillis) : 0f;
            int progressPercent = (int) (progress * 100);
            if (usagePercentText != null) usagePercentText.setText(progressPercent + "%");

            // animiram progress bar
            if (usageProgressBar != null) {
                animateProgressBar(progress, progressPercent);
            }

            // učitavam detaljne statistike
            AppDetailStatsManager.AppDetailStats stats = detailStatsManager.getAppStats(packageName);

            // broj otvaranja danas
            if (opensCountText != null) opensCountText.setText(String.valueOf(stats.opensToday));

            // prosječna dužina sesije
            if (avgSessionText != null) {
                if (stats.opensToday > 0 && todayUsage > 0) {
                    long avgSession = todayUsage / stats.opensToday;
                    avgSessionText.setText(formatShortTime(avgSession));
                } else {
                    avgSessionText.setText(getString(R.string.dash));
                }
            }

            // prekoračenja limita
            if (limitsExceededText != null) limitsExceededText.setText(String.valueOf(stats.limitsExceeded));

            // najduža sesija
            if (longestSessionText != null) {
                if (stats.longestSessionMs > 0) {
                    longestSessionText.setText(formatShortTime(stats.longestSessionMs));
                } else {
                    longestSessionText.setText(getString(R.string.dash));
                }
            }

            // streak (dani unutar limita)
            if (streakText != null) streakText.setText(stats.daysWithinLimit + " " + getString(R.string.days));

            // gradim hourly usage graf
            if (hourlyUsageGraphContainer != null) {
                buildHourlyUsageGraph(stats.hourlyUsage);
            }

            // gradim weekly trend
            if (weeklyTrendContainer != null && weeklyLabelsContainer != null) {
                buildWeeklyTrend(stats.weeklyUsage);
            }

            // peak hour
            int peakHour = findPeakHour(stats.hourlyUsage);
            if (peakHour >= 0) {
                if (peakHourValue != null) peakHourValue.setText(formatHour(peakHour));
                if (peakHourText != null) peakHourText.setText(getString(R.string.peak_hour));
            } else {
                if (peakHourValue != null) peakHourValue.setText("--");
                if (peakHourText != null) peakHourText.setText(getString(R.string.peak_hour));
            }

            // generišem insight
            generateInsight(stats, todayUsage, limitMillis, peakHour);
        } catch (Exception e) {
            e.printStackTrace();
            showNoDataState();
        }
    }

    private void animateProgressBar(float progress, int percent) {
        // postavljam boju na osnovu usagea
        GradientDrawable progressDrawable = new GradientDrawable();
        progressDrawable.setShape(GradientDrawable.RECTANGLE);
        progressDrawable.setCornerRadius(dpToPx(4));

        if (progress >= 1.0f) {
            progressDrawable.setColors(new int[]{
                Color.parseColor("#FF5252"),
                Color.parseColor("#D32F2F")
            });
            progressDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else if (progress >= 0.8f) {
            progressDrawable.setColors(new int[]{
                Color.parseColor("#FFB74D"),
                Color.parseColor("#FF9800")
            });
            progressDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else if (progress >= 0.5f) {
            progressDrawable.setColors(new int[]{
                Color.parseColor("#FFF176"),
                Color.parseColor("#FFC107")
            });
            progressDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else {
            progressDrawable.setColors(new int[]{
                Color.parseColor("#81C784"),
                Color.parseColor("#4CAF50")
            });
            progressDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        }
        usageProgressBar.setBackground(progressDrawable);

        // animiram širinu
        usageProgressBar.post(() -> {
            View parent = (View) usageProgressBar.getParent();
            int targetWidth = (int) (parent.getWidth() * progress);
            
            ValueAnimator animator = ValueAnimator.ofInt(0, targetWidth);
            animator.setDuration(1000);
            animator.setInterpolator(new DecelerateInterpolator(2f));
            animator.addUpdateListener(animation -> {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) usageProgressBar.getLayoutParams();
                params.width = (int) animation.getAnimatedValue();
                usageProgressBar.setLayoutParams(params);
            });
            animator.start();
        });
    }

    private void buildHourlyUsageGraph(long[] hourlyUsage) {
        hourlyUsageGraphContainer.removeAllViews();

        // nalazim max za skaliranje
        long maxUsage = 1;
        for (long usage : hourlyUsage) {
            if (usage > maxUsage) maxUsage = usage;
        }

        // kreiram 24 bara (jedan za svaki sat)
        for (int hour = 0; hour < 24; hour++) {
            // bar container
            LinearLayout barContainer = new LinearLayout(this);
            barContainer.setOrientation(LinearLayout.VERTICAL);
            barContainer.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            containerParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            barContainer.setLayoutParams(containerParams);

            // bar
            View bar = new View(this);
            int barHeight = maxUsage > 0 ? (int) (100 * hourlyUsage[hour] / (float) maxUsage) : 4;
            barHeight = Math.max(dpToPx(4), barHeight);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
            bar.setLayoutParams(barParams);

            GradientDrawable barDrawable = new GradientDrawable();
            barDrawable.setShape(GradientDrawable.RECTANGLE);
            barDrawable.setCornerRadii(new float[]{
                dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4), 0, 0, 0, 0
            });

            // boja na osnovu intenziteta
            float intensity = maxUsage > 0 ? hourlyUsage[hour] / (float) maxUsage : 0;
            if (intensity <= 0.01f) {
                barDrawable.setColor(Color.parseColor("#1A1A2E"));
            } else if (intensity < 0.25f) {
                barDrawable.setColor(Color.parseColor("#1E3A5F"));
            } else if (intensity < 0.5f) {
                barDrawable.setColor(Color.parseColor("#2E7D6E"));
            } else if (intensity < 0.75f) {
                barDrawable.setColors(new int[]{
                    Color.parseColor("#F9A825"),
                    Color.parseColor("#FF9800")
                });
                barDrawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
            } else {
                barDrawable.setColors(new int[]{
                    Color.parseColor("#E53935"),
                    Color.parseColor("#C62828")
                });
                barDrawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
            }
            bar.setBackground(barDrawable);

            barContainer.addView(bar);
            hourlyUsageGraphContainer.addView(barContainer);

            // animiram bar
            bar.setScaleY(0f);
            bar.setPivotY(barHeight);
            bar.animate()
                .scaleY(1f)
                .setStartDelay(hour * 30L)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
        }
    }

    private void buildWeeklyTrend(long[] weeklyUsage) {
        weeklyTrendContainer.removeAllViews();
        weeklyLabelsContainer.removeAllViews();

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        // nalazim max za skaliranje
        long maxUsage = 1;
        for (long usage : weeklyUsage) {
            if (usage > maxUsage) maxUsage = usage;
        }

        Calendar cal = Calendar.getInstance();
        int todayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // konvertujem u Mon=0

        for (int i = 0; i < 7; i++) {
            // bar container
            LinearLayout barContainer = new LinearLayout(this);
            barContainer.setOrientation(LinearLayout.VERTICAL);
            barContainer.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            containerParams.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            barContainer.setLayoutParams(containerParams);

            // usage value label
            TextView valueLabel = new TextView(this);
            long usageMinutes = weeklyUsage[i] / 60000;
            if (usageMinutes >= 60) {
                valueLabel.setText((usageMinutes / 60) + "h");
            } else {
                valueLabel.setText(usageMinutes + "m");
            }
            valueLabel.setTextSize(9);
            valueLabel.setTextColor(i == todayIndex ? Color.parseColor("#64B5F6") : Color.parseColor("#888888"));
            valueLabel.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams valueLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            valueLabelParams.setMargins(0, 0, 0, dpToPx(4));
            valueLabel.setLayoutParams(valueLabelParams);

            // bar
            View bar = new View(this);
            int barHeight = maxUsage > 0 ? (int) (80 * weeklyUsage[i] / (float) maxUsage) : 4;
            barHeight = Math.max(dpToPx(4), dpToPx(barHeight));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
            bar.setLayoutParams(barParams);

            GradientDrawable barDrawable = new GradientDrawable();
            barDrawable.setShape(GradientDrawable.RECTANGLE);
            barDrawable.setCornerRadii(new float[]{
                dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6), 0, 0, 0, 0
            });

            if (i == todayIndex) {
                barDrawable.setColors(new int[]{
                    Color.parseColor("#42A5F5"),
                    Color.parseColor("#1E88E5")
                });
                barDrawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
            } else {
                barDrawable.setColor(Color.parseColor("#3A3A5A"));
            }
            bar.setBackground(barDrawable);

            barContainer.addView(valueLabel);
            barContainer.addView(bar);
            weeklyTrendContainer.addView(barContainer);

            // day label
            TextView label = new TextView(this);
            label.setText(days[i]);
            label.setTextSize(10);
            label.setTextColor(i == todayIndex ? Color.parseColor("#64B5F6") : Color.parseColor("#666666"));
            label.setGravity(Gravity.CENTER);
            if (i == todayIndex) {
                label.setTypeface(null, Typeface.BOLD);
            }
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelParams);
            weeklyLabelsContainer.addView(label);

            // animiram bar
            bar.setScaleY(0f);
            bar.setPivotY(barHeight);
            bar.animate()
                .scaleY(1f)
                .setStartDelay(i * 80L)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
        }
    }

    private int findPeakHour(long[] hourlyUsage) {
        int peakHour = -1;
        long maxUsage = 0;
        for (int i = 0; i < 24; i++) {
            if (hourlyUsage[i] > maxUsage) {
                maxUsage = hourlyUsage[i];
                peakHour = i;
            }
        }
        return peakHour;
    }

    private void generateInsight(AppDetailStatsManager.AppDetailStats stats, 
                                  long todayUsage, long limitMillis, int peakHour) {
        StringBuilder insight = new StringBuilder();

        // usage pattern insight
        if (peakHour >= 0) {
            String timeOfDay;
            if (peakHour >= 5 && peakHour < 12) {
                timeOfDay = "mornings";
            } else if (peakHour >= 12 && peakHour < 17) {
                timeOfDay = "afternoons";
            } else if (peakHour >= 17 && peakHour < 21) {
                timeOfDay = "evenings";
            } else {
                timeOfDay = "late nights";
            }
            insight.append("You tend to use " + appName + " most during " + timeOfDay + ". ");
        }

        // limit insight
        float usageRatio = limitMillis > 0 ? (float) todayUsage / limitMillis : 0;
        if (usageRatio >= 1.0f) {
            insight.append("You've exceeded your limit today. Consider taking a break.");
        } else if (usageRatio >= 0.8f) {
            insight.append("You're approaching your limit. ");
            int remaining = (int) ((limitMillis - todayUsage) / 60000);
            insight.append(remaining + " minutes remaining.");
        } else if (usageRatio >= 0.5f) {
            insight.append("You're on track! Keep monitoring your usage.");
        } else if (stats.opensToday > 0) {
            insight.append("Great self-control! You're well within your limit.");
        } else {
            insight.append("No usage recorded today. Great job staying focused!");
        }

        // session pattern
        if (stats.opensToday > 5) {
            insight.append(" Consider reducing app opens to stay focused.");
        }

        insightText.setText(insight.toString());
    }

    private void showNoDataState() {
        todayUsageText.setText(getString(R.string.dash));
        limitText.setText(getString(R.string.no_limit_set));
        opensCountText.setText(getString(R.string.dash));
        avgSessionText.setText(getString(R.string.dash));
        limitsExceededText.setText(getString(R.string.dash));
        longestSessionText.setText(getString(R.string.dash));
        streakText.setText(getString(R.string.dash));
        peakHourValue.setText(getString(R.string.dash));
        insightText.setText(getString(R.string.no_data_available));
    }

    private void animateEntrance() {
        View headerCard = findViewById(R.id.headerCard);
        View statsGrid = findViewById(R.id.statsGrid);
        View hourlyUsageCard = findViewById(R.id.hourlyUsageCard);
        View weeklyCard = findViewById(R.id.weeklyCard);
        View insightCard = findViewById(R.id.insightCard);

        View[] views = {headerCard, statsGrid, hourlyUsageCard, weeklyCard, insightCard};
        
        for (int i = 0; i < views.length; i++) {
            if (views[i] != null) {
                views[i].setAlpha(0f);
                views[i].setTranslationY(dpToPx(30));
                views[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
            }
        }
    }

    private String formatShortTime(long millis) {
        long minutes = millis / 60000;
        if (minutes >= 60) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return hours + "h " + mins + "m";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            long seconds = millis / 1000;
            return seconds + "s";
        }
    }

    private String formatHour(int hour) {
        if (hour == 0) return "12 AM";
        if (hour == 12) return "12 PM";
        if (hour < 12) return hour + " AM";
        return (hour - 12) + " PM";
    }

    private String formatHourShort(int hour) {
        if (hour == 0 || hour == 24) return "12a";
        if (hour == 12) return "12p";
        if (hour < 12) return hour + "a";
        return (hour - 12) + "p";
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
