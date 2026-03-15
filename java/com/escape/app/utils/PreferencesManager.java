package com.escape.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.escape.app.model.AppRestriction;
import com.escape.app.model.BreakSettings;
import com.escape.app.model.FocusSession;
import com.escape.app.model.UsageStats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// utility klasa za upravljanje app preferences i persistent data storage, sve što treba da se sačuva
public class PreferencesManager {
    private static final String PREFS_NAME = "escape_prefs";
    private static final String KEY_APP_RESTRICTIONS = "app_restrictions";
    private static final String KEY_FOCUS_SESSIONS = "focus_sessions";
    private static final String KEY_USAGE_STATS = "usage_stats";
    private static final String KEY_BREAK_SETTINGS = "break_settings";
    private static final String KEY_LAST_RESET_TIME = "last_reset_time";
    private static final String KEY_NOTIFICATION_WARNING_PERCENT = "notification_warning_percent";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_KAIROS_BALANCE = "kairos_balance";
    private static final String KEY_KAIROS_LAST_RESET = "kairos_last_reset";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_TOTAL_SCREEN_TIME = "total_screen_time_"; // prefix za date-based keys
    private static final String KEY_GAME_PLAYS_COUNT = "game_plays_count";
    private static final String KEY_GAME_LAST_PLAY_DATE = "game_last_play_date";

    private final SharedPreferences preferences;
    private final Gson gson;

    public PreferencesManager(Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    // vraća SharedPreferences instancu za direktan pristup
    public SharedPreferences getSharedPreferences() {
        return preferences;
    }

    // App Restrictions Management

    // čuva listu app restrikcija – serijalizujem u JSON i spremam
    public void saveAppRestrictions(List<AppRestriction> restrictions) {
        String json = gson.toJson(restrictions);
        preferences.edit().putString(KEY_APP_RESTRICTIONS, json).apply();
    }

    // učitava listu app restrikcija iz SharedPreferences
    public List<AppRestriction> loadAppRestrictions() {
        String json = preferences.getString(KEY_APP_RESTRICTIONS, null);
        if (json == null) return new ArrayList<>();

        Type type = new TypeToken<List<AppRestriction>>(){}.getType();
        return gson.fromJson(json, type);
    }

    // čuva jednu app restrikciju – uklanjam staru i dodajem novu
    public void saveAppRestriction(AppRestriction restriction) {
        List<AppRestriction> restrictions = loadAppRestrictions();

        // uklanjam postojeće restrikcije za ovaj package
        restrictions.removeIf(r -> r.getPackageName().equals(restriction.getPackageName()));

        // dodajem novu restrikciju
        restrictions.add(restriction);

        saveAppRestrictions(restrictions);
    }

    // uzima app restrikciju za određeni package
    public AppRestriction getAppRestriction(String packageName) {
        List<AppRestriction> restrictions = loadAppRestrictions();
        for (AppRestriction restriction : restrictions) {
            if (restriction.getPackageName().equals(packageName)) {
                return restriction;
            }
        }
        return null;
    }

    // uklanja app restrikciju
    public void removeAppRestriction(String packageName) {
        List<AppRestriction> restrictions = loadAppRestrictions();
        restrictions.removeIf(r -> r.getPackageName().equals(packageName));
        saveAppRestrictions(restrictions);
    }

    // daje 10-minutni sudoku bonus za app (dozvoljava usage bez obzira na limit)
    public void grantSudokuBonus(String packageName) {
        AppRestriction restriction = getAppRestriction(packageName);
        if (restriction != null) {
            restriction.grantSudokuBonus();
            saveAppRestriction(restriction);
        }
    }

    // Focus Sessions Management

    // čuva aktivnu focus sesiju
    public void saveFocusSession(FocusSession session) {
        String json = gson.toJson(session);
        preferences.edit().putString(KEY_FOCUS_SESSIONS, json).apply();
    }

    // učitava aktivnu focus sesiju
    public FocusSession loadFocusSession() {
        String json = preferences.getString(KEY_FOCUS_SESSIONS, null);
        if (json == null) return null;

        return gson.fromJson(json, FocusSession.class);
    }

    // čisti focus sesiju
    public void clearFocusSession() {
        preferences.edit().remove(KEY_FOCUS_SESSIONS).apply();
    }

    // Usage Stats Management

    // čuva usage statistike – mapiram po datumu
    public void saveUsageStats(UsageStats stats) {
        Map<String, UsageStats> allStats = loadUsageStats();
        allStats.put(String.valueOf(stats.getDate()), stats);

        String json = gson.toJson(allStats);
        preferences.edit().putString(KEY_USAGE_STATS, json).apply();
    }

    // učitava sve usage statistike
    public Map<String, UsageStats> loadUsageStats() {
        String json = preferences.getString(KEY_USAGE_STATS, null);
        if (json == null) return new HashMap<>();

        Type type = new TypeToken<Map<String, UsageStats>>(){}.getType();
        return gson.fromJson(json, type);
    }

    // uzima usage stats za određeni datum
    public UsageStats getUsageStatsForDate(long date) {
        Map<String, UsageStats> allStats = loadUsageStats();
        return allStats.get(String.valueOf(date));
    }

    // Break Settings Management

    // čuva break settings
    public void saveBreakSettings(BreakSettings settings) {
        String json = gson.toJson(settings);
        preferences.edit().putString(KEY_BREAK_SETTINGS, json).apply();
    }

    // učitava break settings
    public BreakSettings loadBreakSettings() {
        String json = preferences.getString(KEY_BREAK_SETTINGS, null);
        if (json == null) return new BreakSettings();

        return gson.fromJson(json, BreakSettings.class);
    }

    // General Settings

    // setuje last reset time
    public void setLastResetTime(long time) {
        preferences.edit().putLong(KEY_LAST_RESET_TIME, time).apply();
    }

    // uzima last reset time
    public long getLastResetTime() {
        return preferences.getLong(KEY_LAST_RESET_TIME, 0);
    }

    // setuje notification warning percentage
    public void setNotificationWarningPercent(int percent) {
        preferences.edit().putInt(KEY_NOTIFICATION_WARNING_PERCENT, percent).apply();
    }

    // uzima notification warning percentage
    public int getNotificationWarningPercent() {
        return preferences.getInt(KEY_NOTIFICATION_WARNING_PERCENT, 80);
    }

    // setuje service enabled status
    public void setServiceEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    // provjerava da li je service enabled
    public boolean isServiceEnabled() {
        return preferences.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    // čisti sve podatke – nuklearni reset
    public void clearAllData() {
        preferences.edit().clear().apply();
    }

    // Kairos Management

    // uzima trenutni kairos balance
    public int getKairosBalance() {
        return preferences.getInt(KEY_KAIROS_BALANCE, 0);
    }

    // setuje kairos balance – ne dozvoljavam negativne vrijednosti
    public void setKairosBalance(int balance) {
        preferences.edit().putInt(KEY_KAIROS_BALANCE, Math.max(0, balance)).apply();
    }

    // dodaje kairos na balance
    public void addKairos(int amount) {
        if (amount == 0) return;
        int newBalance = getKairosBalance() + amount;
        setKairosBalance(Math.max(0, newBalance));
    }

    // nedeljni reset kairos balancea (ako je prošlo 7+ dana)
    public void checkAndPerformWeeklyKairosReset() {
        long lastReset = preferences.getLong(KEY_KAIROS_LAST_RESET, 0);
        long now = System.currentTimeMillis();
        long sevenDaysMillis = 7L * 24 * 60 * 60 * 1000;
        if (now - lastReset >= sevenDaysMillis) {
            setKairosBalance(0);
            preferences.edit().putLong(KEY_KAIROS_LAST_RESET, now).apply();
        }
    }

    // Language Management

    // čuva izabrani app jezik
    public void setAppLanguage(String languageCode) {
        preferences.edit().putString(KEY_APP_LANGUAGE, languageCode).apply();
    }

    // uzima izabrani app jezik
    public String getAppLanguage() {
        return preferences.getString(KEY_APP_LANGUAGE, "en");
    }

    // Total Screen Time Management

    // uzima total screen time za određeni datum (datum je početak dana u milisekundama)
    public long getTotalScreenTimeForDate(long date) {
        String key = KEY_TOTAL_SCREEN_TIME + date;
        return preferences.getLong(key, 0);
    }

    // setuje total screen time za određeni datum (datum je početak dana u milisekundama)
    public void setTotalScreenTimeForDate(long date, long timeMillis) {
        String key = KEY_TOTAL_SCREEN_TIME + date;
        preferences.edit().putLong(key, timeMillis).apply();
    }

    // uzima total screen time za danas
    public long getTotalScreenTimeToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        return getTotalScreenTimeForDate(todayStart);
    }

    // Game Daily Plays Management (for random games)

    // provjerava i resetuje game plays ako je novi dan
    private void checkAndResetGamePlaysIfNeeded() {
        long lastPlayDate = preferences.getLong(KEY_GAME_LAST_PLAY_DATE, 0);
        Calendar lastCal = Calendar.getInstance();
        lastCal.setTimeInMillis(lastPlayDate);
        
        Calendar nowCal = Calendar.getInstance();
        
        // Provjerava da li je novi dan (poslije ponoći)
        if (lastCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR) ||
            lastCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)) {
            // Resetujem broj igara
            preferences.edit()
                .putInt(KEY_GAME_PLAYS_COUNT, 0)
                .putLong(KEY_GAME_LAST_PLAY_DATE, nowCal.getTimeInMillis())
                .apply();
        }
    }

    // uzima broj igara danas
    public int getGamePlaysToday() {
        checkAndResetGamePlaysIfNeeded();
        return preferences.getInt(KEY_GAME_PLAYS_COUNT, 0);
    }

    // povećava broj igara danas
    public void incrementGamePlays() {
        checkAndResetGamePlaysIfNeeded();
        int currentPlays = preferences.getInt(KEY_GAME_PLAYS_COUNT, 0);
        Calendar nowCal = Calendar.getInstance();
        preferences.edit()
            .putInt(KEY_GAME_PLAYS_COUNT, currentPlays + 1)
            .putLong(KEY_GAME_LAST_PLAY_DATE, nowCal.getTimeInMillis())
            .apply();
    }

    // provjerava da li je dostignut dnevni limit (2 igre)
    public boolean isGameDailyLimitReached() {
        return getGamePlaysToday() >= 2;
    }
}
