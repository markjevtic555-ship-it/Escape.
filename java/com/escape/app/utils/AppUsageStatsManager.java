package com.escape.app.utils;

import android.annotation.SuppressLint;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.escape.app.model.AppInfo;
import com.escape.app.model.AppRestriction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// utility klasa za upravljanje app usage statistics i monitoringom
public class AppUsageStatsManager {
    private static final String TAG = "EscapeUsageStats";

    private final android.app.usage.UsageStatsManager usageStatsManager;
    private final PackageManager packageManager;
    private final Context context;

    // cache za app informacije – osvježavamo svakih 5 minuta da ne bude zastario
    private Map<String, AppInfo> appInfoCache = new HashMap<>();
    private long lastAppInfoCacheUpdate = 0;
    private static final long CACHE_UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minuta

    public AppUsageStatsManager(Context context) {
        this.context = context;
        this.usageStatsManager = (android.app.usage.UsageStatsManager)
            context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.packageManager = context.getPackageManager();
    }

    // provjerava da li imamo usage stats permission – ako možemo da queryujemo, imamo ga
    public boolean hasUsageStatsPermission() {
        if (usageStatsManager == null) return false;

        long currentTime = System.currentTimeMillis();
        List<android.app.usage.UsageStats> stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60 * 60, // pre 1 sata
            currentTime
        );

        return stats != null && !stats.isEmpty();
    }

    // uzima usage time za određenu aplikaciju u zadnjih 24 sata
    public long getAppUsageTime(String packageName, long startTime, long endTime) {
        if (usageStatsManager == null) return 0;

        List<android.app.usage.UsageStats> stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        );

        for (android.app.usage.UsageStats stat : stats) {
            if (stat.getPackageName().equals(packageName)) {
                return stat.getTotalTimeInForeground();
            }
        }

        return 0;
    }

    // uzima usage time za određeni app danas (od ponoci do sada)
    public long getAppUsageTimeToday(String packageName) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startOfDay = calendar.getTimeInMillis();
        long currentTime = System.currentTimeMillis();

        return getAppUsageTime(packageName, startOfDay, currentTime);
    }

    public String getForegroundApp() {
        if (usageStatsManager == null) return null;

        long currentTime = System.currentTimeMillis();
        // queryujem evente iz zadnjih 24 sata da sigurno uhvatim MOVE_TO_FOREGROUND event
        // čak i ako je app bio u foregroundu dugo vremena
        UsageEvents events = usageStatsManager.queryEvents(currentTime - 1000 * 60 * 60 * 24, currentTime);

        String foregroundApp = null;
        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.getPackageName();
            }
        }

        return foregroundApp;
    }

    // uzima listu svih instaliranih appova (isključuje system appove po defaultu)
    public List<AppInfo> getInstalledApps(boolean includeSystemApps) {
        updateAppInfoCacheIfNeeded();

        List<AppInfo> apps = new ArrayList<>();
        for (AppInfo app : appInfoCache.values()) {
            if (includeSystemApps || !app.isSystemApp()) {
                apps.add(app);
            }
        }

        return apps;
    }

    // uzima listu user-installable appova (isključuje system appove i non-launchable appove)
    public List<AppInfo> getUserApps() {
        long t0 = System.currentTimeMillis();
        List<AppInfo> apps = new ArrayList<>();

        // queryujem appove sa launcher aktivnostima (user-installed appovi)
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
        long t1 = System.currentTimeMillis();

        Log.d(TAG, "Found " + resolveInfos.size() + " launchable apps");

        for (ResolveInfo ri : resolveInfos) {
            String packageName = ri.activityInfo.packageName;
            String appName = ri.loadLabel(packageManager).toString();
            Drawable icon = ri.loadIcon(packageManager);

            AppInfo appInfo = new AppInfo(packageName, appName, icon, false, 0L);
            apps.add(appInfo);

            // debug log popularnih appova
            if (packageName.toLowerCase().contains("youtube") ||
                packageName.toLowerCase().contains("instagram") ||
                packageName.toLowerCase().contains("facebook") ||
                packageName.toLowerCase().contains("subway") ||
                packageName.toLowerCase().contains("telegram") ||
                packageName.toLowerCase().contains("whatsapp")) {
                Log.d(TAG, "Popular app found: " + appName + " (" + packageName + ")");
            }
        }

        Log.d(TAG, "Total user apps: " + apps.size());
        return apps;
    }



    // uzima AppInfo za određeni package
    public AppInfo getAppInfo(String packageName) {
        updateAppInfoCacheIfNeeded();
        return appInfoCache.get(packageName);
    }
    
    // uzima app name iz package name – ako nema u cacheu, idem direktno u PackageManager
    public String getAppName(String packageName) {
        AppInfo appInfo = getAppInfo(packageName);
        if (appInfo != null) {
            return appInfo.getAppName();
        }
        // fallback: pokušavam da uzmem direktno iz PackageManager
        try {
            ApplicationInfo appInfo2 = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo2).toString();
        } catch (Exception e) {
            return packageName; // vraćam package name kao fallback
        }
    }

    // updateuje app info cache ako je prošlo dovoljno vremena
    private void updateAppInfoCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAppInfoCacheUpdate > CACHE_UPDATE_INTERVAL) {
            updateAppInfoCache();
            lastAppInfoCacheUpdate = currentTime;
        }
    }

    // forsira update app info cachea – prolazim kroz sve instalirane appove
    private void updateAppInfoCache() {
        appInfoCache.clear();

        List<android.content.pm.ApplicationInfo> packages = packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA);

        Log.d(TAG, "Found " + packages.size() + " installed applications");

        if (packages.isEmpty()) {
            Log.e(TAG, "No packages found! This is unexpected.");
        }

        int processedCount = 0;
        for (android.content.pm.ApplicationInfo appInfo : packages) {
            processedCount++;
            processedCount++;
            if (processedCount <= 10) { // logujem prvih 10 appova
                Log.d(TAG, "Processing app: " + appInfo.packageName);
            }
            try {
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                android.graphics.drawable.Drawable icon = packageManager.getApplicationIcon(appInfo);
                boolean isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;

                // setujem install time na trenutno vrijeme da izbjegnem AppsFilter probleme
                long installTime = System.currentTimeMillis();

                AppInfo app = new AppInfo(
                    appInfo.packageName,
                    appName,
                    icon,
                    isSystemApp,
                    installTime
                );

                appInfoCache.put(appInfo.packageName, app);
            } catch (Exception e) {
                Log.w(TAG, "Could not get app info for: " + appInfo.packageName, e);
            }
        }

        Log.d(TAG, "Updated app info cache with " + appInfoCache.size() + " apps");

        // logujem nekoliko sample appova iz cachea za debug
        int sampleCount = 0;
        for (AppInfo sampleApp : appInfoCache.values()) {
            if (sampleCount < 5) {
                Log.d(TAG, "Sample cached app: " + sampleApp.getAppName() + " (" + sampleApp.getPackageName() + ")");
                sampleCount++;
            } else {
                break;
            }
        }
    }

    // provjerava da li app treba da bude blokiran na osnovu restrikcija
    public boolean shouldBlockApp(String packageName, List<AppRestriction> restrictions) {
        for (AppRestriction restriction : restrictions) {
            if (!restriction.isEnabled()) {
                continue;
            }

            if (!restriction.getPackageName().equals(packageName)) {
                continue;
            }

            // ako postoji bilo koji aktivan bonus (Sudoku / Kairos), ne blokiram još
            if (restriction.isSudokuBonusActive()) {
                return false;
            }

            // koristim pravi system usage za trenutni dan da limiti rade i poslije restartova
            long usedTodayMs = getAppUsageTimeToday(packageName);
            long limitMs = restriction.getDailyLimitMinutes() * 60 * 1000L;

            if (limitMs > 0 && usedTodayMs >= limitMs) {
                return true;
            }
        }
        return false;
    }

    // uzima usage summary za više appova odjednom
    public Map<String, Long> getUsageSummary(List<String> packageNames, long startTime, long endTime) {
        Map<String, Long> summary = new HashMap<>();

        for (String packageName : packageNames) {
            long usageTime = getAppUsageTime(packageName, startTime, endTime);
            summary.put(packageName, usageTime);
        }

        return summary;
    }

    // provjerava da li je app usage monitoring dostupan
    public boolean isUsageMonitoringAvailable() {
        return usageStatsManager != null && hasUsageStatsPermission();
    }
    
    // uzima total screen time danas (suma SVIH appova iz UsageStats API-ja)
    // ovo je tačnije od custom screen-on trackinga jer koristi Android sistem podatke
    // VAŽNO: Ovo uključuje SVE appove (ne samo tracked/restricted), uključujući:
    // - Sve user-installed appove
    // - System appove koji su korišćeni
    // - Launcher/home screen vrijeme
    // - Bilo koji app koji je bio u foregroundu danas
    //Marko evo ovdje ti je zapisano da imaš
    public long getTotalScreenTimeToday() {
        if (usageStatsManager == null) return 0;
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        long startOfDay = calendar.getTimeInMillis();
        long currentTime = System.currentTimeMillis();
        
        // queryujem SVE appove iz UsageStats API-ja (ne samo tracked/restricted)
        // UsageStatsManager.queryUsageStats vraća SVE appove koji su korišćeni u datom periodu
        List<android.app.usage.UsageStats> stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            currentTime
        );
        
        if (stats == null || stats.isEmpty()) return 0;
        
        // sumiram total time in foreground za SVE appove (bez filtera)
        long totalTime = 0;
        for (android.app.usage.UsageStats stat : stats) {
            // dodajem vrijeme za SVAKI app, bez obzira da li je tracked ili ne
            long appTime = stat.getTotalTimeInForeground();
            if (appTime > 0) {
                totalTime += appTime;
            }
        }
        
        Log.d(TAG, "Total screen time today: " + (totalTime / 1000) + "s from " + stats.size() + " apps");
        
        return totalTime;
    }
    
    // provjerava da li je app system app ili launcher/home screen
    // ovi appovi treba da budu isključeni iz buddy trackinga
    public boolean isSystemOrLauncherApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return true;
        }
        
        // common system/launcher package prefixi za isključivanje
        String[] systemPrefixes = {
            "com.android.launcher",
            "com.samsung.android.launcher",
            "com.google.android.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",                    "com.android.dialer",        // Dialer
            "com.android.mms",                      "com.google.android.apps.nexuslauncher", // Pixel Launcher
            "com.lge.launcher2",
            "com.sec.android.app.launcher"
        };
        
        String lowerPackage = packageName.toLowerCase();
        for (String prefix : systemPrefixes) {
            if (lowerPackage.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        
        // provjeravam da li je system app provjeravanjem ApplicationInfo
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            
            // također provjeravam da li je launcher provjeravanjem HOME kategorije
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homeApps = packageManager.queryIntentActivities(homeIntent, 0);
            boolean isLauncher = false;
            for (ResolveInfo resolveInfo : homeApps) {
                if (resolveInfo.activityInfo.packageName.equals(packageName)) {
                    isLauncher = true;
                    break;
                }
            }
            
            // VAŽNO: ne isključujem system appove koji su user-installable (kao YouTube)
            // isključujem samo ako je system app I nije user-installable
            // provjeravam da li app ima launcher aktivnost (user-installable appovi obično imaju)
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> launcherApps = packageManager.queryIntentActivities(launcherIntent, 0);
            boolean hasLauncherActivity = false;
            for (ResolveInfo resolveInfo : launcherApps) {
                if (resolveInfo.activityInfo.packageName.equals(packageName)) {
                    hasLauncherActivity = true;
                    break;
                }
            }
            
            // ako ima launcher aktivnost, user-installable je (kao YouTube), tako da dozvoljavam
            if (hasLauncherActivity) {
                return false; // dozvoljavam user-installable appove čak i ako su system appovi
            }
            
            // isključujem samo ako je system app bez launcher aktivnosti ILI je launcher/home screen
            return (isSystemApp && !hasLauncherActivity) || isLauncher;
        } catch (PackageManager.NameNotFoundException e) {
            // ako ne mogu da nađem app, isključujem ga da budem siguran
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error checking if app is system/launcher: " + packageName, e);
            return false; // default dozvoljavam ako ne mogu da odredim
        }
    }
}
