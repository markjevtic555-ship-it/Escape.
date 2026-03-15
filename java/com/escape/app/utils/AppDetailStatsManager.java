package com.escape.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

// manager za statistiku aplikacije
public class AppDetailStatsManager {
    private static final String TAG = "AppDetailStatsManager";
    private static final String PREFS_NAME = "app_detail_stats";
    private static final String KEY_OPEN_COUNTS = "open_counts";
    private static final String KEY_HOURLY_USAGE = "hourly_usage";
    private static final String KEY_LIMIT_EXCEEDED = "limit_exceeded";
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";

    private final SharedPreferences prefs;
    private final Gson gson;

    public AppDetailStatsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    // snima app open event, brojim koliko puta je app otvoren danas
    public void recordAppOpen(String packageName) {
        Map<String, Integer> openCounts = getOpenCounts();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        
        int count = openCounts.getOrDefault(key, 0);
        openCounts.put(key, count + 1);
        
        saveOpenCounts(openCounts);
    }

    // uzima open count za danas
    public int getOpenCountToday(String packageName) {
        Map<String, Integer> openCounts = getOpenCounts();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        return openCounts.getOrDefault(key, 0);
    }

    // snima hourly usage za app
    // millis parametar predstavlja vrijeme provedeno u milisekundama
    public void recordHourlyUsage(String packageName, int hour, long millis) {
        Map<String, Map<Integer, Long>> hourlyUsage = getHourlyUsage();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        
        Map<Integer, Long> hourMap = hourlyUsage.getOrDefault(key, new HashMap<>());
        long current = hourMap.getOrDefault(hour, 0L);
        hourMap.put(hour, current + millis);
        hourlyUsage.put(key, hourMap);
        
        saveHourlyUsage(hourlyUsage);
    }

    public long[] getHourlyUsageToday(String packageName) {
        Map<String, Map<Integer, Long>> hourlyUsage = getHourlyUsage();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        
        Map<Integer, Long> hourMap = hourlyUsage.getOrDefault(key, new HashMap<>());
        long[] result = new long[24];
        for (int i = 0; i < 24; i++) {
            result[i] = hourMap.getOrDefault(i, 0L);
        }
        return result;
    }

    public void recordLimitExceeded(String packageName) {
        Map<String, Integer> exceeded = getLimitExceeded();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        
        int count = exceeded.getOrDefault(key, 0);
        exceeded.put(key, count + 1);
        
        saveLimitExceeded(exceeded);
    }

    public int getLimitExceededToday(String packageName) {
        Map<String, Integer> exceeded = getLimitExceeded();
        String today = getTodayKey();
        String key = packageName + "_" + today;
        return exceeded.getOrDefault(key, 0);
    }

    private String getTodayKey() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return String.format("%04d-%02d-%02d", 
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private void checkAndResetIfNeeded() {
        long lastReset = prefs.getLong(KEY_LAST_RESET_DATE, 0);
        java.util.Calendar lastCal = java.util.Calendar.getInstance();
        lastCal.setTimeInMillis(lastReset);
        
        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        
        if (lastCal.get(java.util.Calendar.DAY_OF_YEAR) != nowCal.get(java.util.Calendar.DAY_OF_YEAR) ||
            lastCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR)) {
            // novi dan – podaci će se prirodno zamijeniti kad se koriste novi ključevi
            prefs.edit().putLong(KEY_LAST_RESET_DATE, System.currentTimeMillis()).apply();
        }
    }

    private Map<String, Integer> getOpenCounts() {
        checkAndResetIfNeeded();
        String json = prefs.getString(KEY_OPEN_COUNTS, "{}");
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        Map<String, Integer> map = gson.fromJson(json, type);
        return map != null ? map : new HashMap<>();
    }

    private void saveOpenCounts(Map<String, Integer> map) {
        prefs.edit().putString(KEY_OPEN_COUNTS, gson.toJson(map)).apply();
    }

    private Map<String, Map<Integer, Long>> getHourlyUsage() {
        checkAndResetIfNeeded();
        String json = prefs.getString(KEY_HOURLY_USAGE, "{}");
        Type type = new TypeToken<Map<String, Map<Integer, Long>>>(){}.getType();
        Map<String, Map<Integer, Long>> map = gson.fromJson(json, type);
        return map != null ? map : new HashMap<>();
    }

    private void saveHourlyUsage(Map<String, Map<Integer, Long>> map) {
        prefs.edit().putString(KEY_HOURLY_USAGE, gson.toJson(map)).apply();
    }

    private Map<String, Integer> getLimitExceeded() {
        checkAndResetIfNeeded();
        String json = prefs.getString(KEY_LIMIT_EXCEEDED, "{}");
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        Map<String, Integer> map = gson.fromJson(json, type);
        return map != null ? map : new HashMap<>();
    }

    private void saveLimitExceeded(Map<String, Integer> map) {
        prefs.edit().putString(KEY_LIMIT_EXCEEDED, gson.toJson(map)).apply();
    }

    // data klasa za detalje aplikacijske statistike
    public static class AppDetailStats {
        public int opensToday;
        public int limitsExceeded;
        public long longestSessionMs;
        public int daysWithinLimit;
        public long[] hourlyUsage; // 24 sata
        public long[] weeklyUsage; // 7 dana

        public AppDetailStats() {
            this.opensToday = 0;
            this.limitsExceeded = 0;
            this.longestSessionMs = 0;
            this.daysWithinLimit = 0;
            this.hourlyUsage = new long[24];
            this.weeklyUsage = new long[7];
        }
    }

    // uzima comprehensive app statistike
    public AppDetailStats getAppStats(String packageName) {
        AppDetailStats stats = new AppDetailStats();

        stats.opensToday = getOpenCountToday(packageName);

        stats.limitsExceeded = getLimitExceededToday(packageName);

        stats.hourlyUsage = getHourlyUsageToday(packageName);

        long maxHourlyUsage = 0;
        for (long usage : stats.hourlyUsage) {
            if (usage > maxHourlyUsage) {
                maxHourlyUsage = usage;
            }
        }
        stats.longestSessionMs = maxHourlyUsage;

        stats.weeklyUsage = new long[7];
        long todayTotal = 0;
        for (long usage : stats.hourlyUsage) {
            todayTotal += usage;
        }
        for (int i = 0; i < 7; i++) {
            stats.weeklyUsage[i] = todayTotal; // placeholder – trebalo bi istorijski podaci
        }

        stats.daysWithinLimit = 0;

        return stats;
    }
}

