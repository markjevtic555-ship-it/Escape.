package com.escape.app.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.escape.app.model.AppRestriction;
import com.escape.app.model.BreakSettings;
import com.escape.app.model.FocusSession;
import com.escape.app.model.UsageStats;
import com.escape.app.ui.BlockingActivity;
import com.escape.app.utils.AppNotificationManager;
import com.escape.app.utils.FirebaseBuddyManager;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.AppUsageStatsManager;
import com.escape.app.utils.AppDetailStatsManager;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

// servis koji prati appove i blokira ih kad treba
public class AppMonitoringService extends Service {
    private static final String TAG = "AppMonitoringService";
    private static final long MONITORING_INTERVAL = 1000; // proverava svake sekunde
    private static final long USAGE_UPDATE_INTERVAL = 60 * 1000; // updateuje usage svaki minut
    private static final long BLOCKING_GRACE_PERIOD_MILLIS = 5000; // 5 sekundi grace period pre nego sto blokira
    private static final String ACTION_EXTEND_GRACE_PERIOD = "com.escape.app.EXTEND_GRACE_PERIOD";

    private Handler handler;
    private Runnable monitoringRunnable;
    private Runnable usageUpdateRunnable;

    private AppUsageStatsManager usageStatsManager;
    private PreferencesManager preferencesManager;
    private AppNotificationManager notificationManager;
    private FirebaseBuddyManager buddyManager;
    private AppDetailStatsManager detailStatsManager;

    private String lastForegroundApp;
    private long lastForegroundTime;
    private long lastUsageUpdateTime;
    private long lastBuddySyncTime = 0;
    private static final long BUDDY_SYNC_INTERVAL = 5000; // syncuje sa firebase svakih 5 sekundi

    private boolean isMonitoring = false;
    private String recentlyDismissedApp;
    private long lastDismissTime;
    private boolean isBlockingActive = false;
    private BroadcastReceiver broadcastReceiver;
    private BroadcastReceiver screenStateReceiver;
    private boolean isSudokuGameActive = false;
    private static final String DEBUG_LOG_PATH = "/data/data/com.escape.app/files/debug.log";
    private boolean buddyListenerStarted = false;
    
    // prati da li je ekran upaljen za total screen time
    private boolean isScreenOn = true; // pretpostavljam da je ekran upaljen kad se servis startuje
    private long screenOnStartTime = 0; // kad se ekran upalio
    private long lastScreenTimeUpdate = 0; // poslednji put kad sam sacuvao screen time

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        try {
            Log.d(TAG, "Starting service initialization...");

            // inicijalizujem sve utility klase
            Log.d(TAG, "Initializing usage stats manager...");
            usageStatsManager = new AppUsageStatsManager(this);

            Log.d(TAG, "Initializing preferences manager...");
            preferencesManager = new PreferencesManager(this);

            Log.d(TAG, "Initializing detail stats manager...");
            detailStatsManager = new AppDetailStatsManager(this);

            Log.d(TAG, "Initializing notification manager...");
            notificationManager = new AppNotificationManager(this);

            Log.d(TAG, "Initializing buddy manager...");
            buddyManager = new FirebaseBuddyManager(this);

            // mora da bude foreground service sa notifikacijom da ne bi android ubio servis
            Log.d(TAG, "Creating service notification...");
            Notification serviceNotification = notificationManager.createServiceNotification();
            if (serviceNotification != null) {
                Log.d(TAG, "Starting foreground service...");
                // startForeground radi od API 5, a minSdk je 26 tako da je ovo ok
                startForeground(AppNotificationManager.NOTIFICATION_ID_SERVICE, serviceNotification);
                Log.d(TAG, "Foreground service started successfully");
            } else {
                Log.e(TAG, "Failed to create service notification");
                stopSelf();
                return;
            }

            Log.d(TAG, "Setting up handler and monitoring...");
            handler = new Handler(Looper.getMainLooper());
            setupMonitoring();

            Log.d(TAG, "Service initialization completed successfully");

            // setupujem broadcast receiver za grace period
            setupBroadcastReceiver();
            
            // setupujem receiver za screen state da prati total screen time
            setupScreenStateReceiver();
            
            // proveravam pocetno stanje ekrana
            // koristim PowerManager da vidim da li je ekran upaljen
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                isScreenOn = pm != null && pm.isInteractive();
            } catch (Exception e) {
                Log.e(TAG, "Error checking initial screen state", e);
                isScreenOn = true; // ako ne moze da proveri, pretpostavljam da je upaljen
            }
            
            if (isScreenOn) {
                screenOnStartTime = System.currentTimeMillis();
                lastScreenTimeUpdate = System.currentTimeMillis();
            } else {
                screenOnStartTime = 0;
                lastScreenTimeUpdate = 0;
            }

            // mala pauza pre nego sto krene sve da radi
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Error during service initialization", e);
            Log.e(TAG, "Exception details:", e);
            e.printStackTrace();
            // ako se servis ne inicijalizuje, zaustavi ga
            stopSelf();
        }
    }

    // pokrece buddy listener u pozadini da bi penalty radio i kad nije otvoren BuddyActivity
    private void ensureBuddyListenerStarted() {
        if (buddyListenerStarted) return;
        if (buddyManager == null) return;
        if (!buddyManager.isLoggedIn()) return;

        // FirebaseAuth nekad kaze da je ulogovan pre nego sto je UID dostupan (timing problem)
        // ne markuj kao started dok ne mogu da attachujem listener, inace nikad ne bih retryovao
        if (buddyManager.getCurrentUserId() == null) return;

        try {
            buddyManager.startBuddyListener(new FirebaseBuddyManager.BuddyUpdateListener() {
            @Override
            public void onBuddyDataChanged(com.escape.app.model.BuddyUser buddy) {
                // nista, FirebaseBuddyManager interno proverava penalty state i primenjuje ga lokalno
            }

            @Override
            public void onBuddyDisconnected() {
                // ako se buddy diskonektuje, prestajem da pratim; ostavljam listenerStarted true da ne bih loopovao
                Log.d(TAG, "Buddy disconnected (service listener)");
            }

            @Override
            public void onPenaltyTriggered(String reason) {
                // nema UI ovde, samo logujem; penalty flag se setuje u FirebaseBuddyManager
                Log.d(TAG, "Penalty triggered (service listener): " + reason);
            }

            @Override
            public void onOneHourNotification(String appName, String message) {
                // nema notifikacija iz background monitoring servisa
            }
            });
            buddyListenerStarted = true;
            Log.d(TAG, "Buddy listener started from AppMonitoringService");
        } catch (Exception e) {
            buddyListenerStarted = false;
            Log.e(TAG, "Failed to start buddy listener from AppMonitoringService", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        if (!isMonitoring) {
            startMonitoring();
        }

        // START_STICKY da se servis restartuje ako ga android ubije
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // sacuvam finalni screen time pre nego sto se servis unisti
        if (isScreenOn && screenOnStartTime > 0) {
            long elapsed = System.currentTimeMillis() - screenOnStartTime;
            if (elapsed > 0) {
                saveTotalScreenTime(elapsed);
            }
        }

        // unregisterujem broadcast receivere
        if (broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        }
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering screen state receiver", e);
            }
        }

        stopMonitoring();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // nije bound service
    }

    // setupujem broadcast receiver za UI evente
    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_EXTEND_GRACE_PERIOD.equals(action)) {
                    String packageName = intent.getStringExtra("blocked_package");
                    Log.d(TAG, "Extending grace period for app: " + packageName);
                    recentlyDismissedApp = packageName;
                    lastDismissTime = System.currentTimeMillis();
                    isBlockingActive = false;
                } else if ("com.escape.app.SUDOKU_GAME_STARTED".equals(action)) {
                    Log.d(TAG, "Sudoku game started - pausing blocking");
                    isSudokuGameActive = true;
                } else if ("com.escape.app.SUDOKU_GAME_ENDED".equals(action)) {
                    Log.d(TAG, "Sudoku game ended - resuming blocking");
                    isSudokuGameActive = false;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_EXTEND_GRACE_PERIOD);
        filter.addAction("com.escape.app.SUDOKU_GAME_STARTED");
        filter.addAction("com.escape.app.SUDOKU_GAME_ENDED");
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
    }

    // setupujem receiver za screen state da prati total screen-on time
    private void setupScreenStateReceiver() {
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                long currentTime = System.currentTimeMillis();
                
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (!isScreenOn) {
                        // ekran se upravo upalio - sacuvam vreme koje je bilo akumulirano pre nego sto se ugasio
                        if (screenOnStartTime > 0) {
                            long elapsed = currentTime - screenOnStartTime;
                            if (elapsed > 0) {
                                saveTotalScreenTime(elapsed);
                            }
                        }
                        isScreenOn = true;
                        screenOnStartTime = currentTime;
                        Log.d(TAG, "Screen turned ON - starting screen time tracking");
                    }
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (isScreenOn) {
                        // ekran se upravo ugasio - sacuvam akumulirano vreme
                        if (screenOnStartTime > 0) {
                            long elapsed = currentTime - screenOnStartTime;
                            if (elapsed > 0) {
                                saveTotalScreenTime(elapsed);
                            }
                        }
                        isScreenOn = false;
                        screenOnStartTime = 0;
                        Log.d(TAG, "Screen turned OFF - stopping screen time tracking");
                    }
                }
            }
        };

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, screenFilter);
    }

    // setupujem monitoring runnableove
    private void setupMonitoring() {
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // pratim total screen time ako je ekran upaljen
                    if (isScreenOn && screenOnStartTime > 0) {
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - screenOnStartTime;
                        // updateujem screen time svakih 10 sekundi da ne bih previse cesto pisao
                        if (elapsed >= 10000 && currentTime - lastScreenTimeUpdate >= 10000) {
                            saveTotalScreenTime(elapsed);
                            screenOnStartTime = currentTime;
                            lastScreenTimeUpdate = currentTime;
                        }
                    }
                    
                    checkAppRestrictions();
                    checkBreakSystem();
                    checkDailyReset();
                } catch (Exception e) {
                    Log.e(TAG, "Error in monitoring runnable", e);
                }

                // zakazujem sledecu proveru
                try {
                    if (handler != null) {
                        handler.postDelayed(this, MONITORING_INTERVAL);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error scheduling next monitoring check", e);
                }
            }
        };

        usageUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateUsageStats();
                } catch (Exception e) {
                    Log.e(TAG, "Error in usage update runnable", e);
                }

                // zakazujem sledeci update
                try {
                    if (handler != null) {
                        handler.postDelayed(this, USAGE_UPDATE_INTERVAL);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error scheduling next usage update", e);
                }
            }
        };
    }

    // pokrece monitoring
    private void startMonitoring() {
        if (isMonitoring) return;

        try {
            isMonitoring = true;
            lastForegroundTime = System.currentTimeMillis();
            lastUsageUpdateTime = System.currentTimeMillis();
            lastBuddySyncTime = 0; // forsiraj immediate sync

            Log.d(TAG, "Started monitoring");

            // pokrecem buddy listener u pozadini da bi penalty radio i kad nije otvoren BuddyActivity
            ensureBuddyListenerStarted();

            // syncujem trenutni app odmah na startu (iskljucujem Escape app i system/launcher appove)
            if (buddyManager != null && usageStatsManager != null) {
                String currentApp = usageStatsManager.getForegroundApp();
                if (currentApp != null) {
                    boolean shouldExclude = currentApp.equals("com.escape.app") ||
                                          usageStatsManager.isSystemOrLauncherApp(currentApp);
                    if (shouldExclude) {
                        buddyManager.updateCurrentApp(null, null);
                    } else {
                        String appName = usageStatsManager.getAppName(currentApp);
                        buddyManager.updateCurrentApp(currentApp, appName);
                    }
                    lastBuddySyncTime = System.currentTimeMillis();
                }
            }

            // pokrecem monitoring i usage updateove
            if (handler != null && monitoringRunnable != null && usageUpdateRunnable != null) {
                handler.post(monitoringRunnable);
                handler.post(usageUpdateRunnable);
                Log.d(TAG, "Monitoring runnables posted successfully");
            } else {
                Log.e(TAG, "Handler or runnables are null!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting monitoring", e);
            isMonitoring = false;
        }
    }

    // zaustavlja monitoring
    private void stopMonitoring() {
        if (!isMonitoring) return;

        isMonitoring = false;

        // bezbedno uklanjam callbackove sa null checkovima
        if (handler != null) {
            if (monitoringRunnable != null) {
                handler.removeCallbacks(monitoringRunnable);
            }
            if (usageUpdateRunnable != null) {
                handler.removeCallbacks(usageUpdateRunnable);
            }
        }

        Log.d(TAG, "Stopped monitoring");
    }

    // proverava da li appove treba blokirati na osnovu restrikcija
    private void checkAppRestrictions() {
        // nastavljam da pokusavam da startujem buddy listener ako se user uloguje posle sto se servis startovao
        ensureBuddyListenerStarted();

        // preskacem blocking ako je sudoku igra aktivna
        if (isSudokuGameActive) {
            Log.d(TAG, "Sudoku game is active, skipping app restrictions check");
            return;
        }

        if (!usageStatsManager.isUsageMonitoringAvailable()) {
            Log.w(TAG, "Usage monitoring not available");
            return;
        }

        String currentForegroundApp = usageStatsManager.getForegroundApp();
        if (currentForegroundApp == null) return;

        long currentTime = System.currentTimeMillis();

        // inicijalizujem tracking na prvom runu
        boolean appChanged;
        if (lastForegroundApp == null) {
            lastForegroundApp = currentForegroundApp;
            lastForegroundTime = currentTime;
            appChanged = true;
        } else {
            // akumuliram usage + trosim bonus time za app koji je trenutno u foregroundu
            long elapsed = currentTime - lastForegroundTime;
            if (elapsed > 0 && lastForegroundApp != null) {
                updateAppUsageTime(lastForegroundApp, elapsed);
            }
            lastForegroundTime = currentTime;

            // proveravam da li se foreground app promenio
            appChanged = !currentForegroundApp.equals(lastForegroundApp);
        }
        
        // syncujem trenutni app u Firebase za buddy tracking (na app change ili periodicno)
        // iskljucujem Escape app, system appove i launcher/home screenove iz trackinga
        if (buddyManager != null && currentForegroundApp != null) {
            // proveravam da li app treba da bude iskljucen
            boolean shouldExclude = currentForegroundApp.equals("com.escape.app") ||
                                   usageStatsManager.isSystemOrLauncherApp(currentForegroundApp);
            
            if (shouldExclude) {
                // cistim trenutni app kad je Escape app, system app ili launcher u foregroundu
                if (appChanged || (currentTime - lastBuddySyncTime) >= BUDDY_SYNC_INTERVAL) {
                    buddyManager.updateCurrentApp(null, null);
                    lastBuddySyncTime = currentTime;
                }
            } else {
                // pratim user-installable app (nema vise icon uploada u Firebase Storage)
                if (appChanged || (currentTime - lastBuddySyncTime) >= BUDDY_SYNC_INTERVAL) {
                    String appName = usageStatsManager.getAppName(currentForegroundApp);
                    buddyManager.updateCurrentApp(currentForegroundApp, appName);
                    lastBuddySyncTime = currentTime;
                }
            }
        }
        
        if (appChanged) {
            // cistim recently dismissed flag ako je proslo dovoljno vremena ili je drugi app sada u foregroundu
            if (recentlyDismissedApp != null &&
                (!recentlyDismissedApp.equals(currentForegroundApp) ||
                 (currentTime - lastDismissTime) > BLOCKING_GRACE_PERIOD_MILLIS)) {
                recentlyDismissedApp = null;
            }

            // cistim blocking active flag ako se prebacujem na drugi app (koji nije restricted)
            List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();
            boolean isCurrentAppRestricted = false;
            for (AppRestriction restriction : restrictions) {
                if (restriction.getPackageName().equals(currentForegroundApp)) {
                    isCurrentAppRestricted = true;
                    break;
                }
            }

            if (isBlockingActive && !isCurrentAppRestricted) {
                Log.d(TAG, "Clearing blocking active flag - switched to non-restricted app: " + currentForegroundApp);
                isBlockingActive = false;
                recentlyDismissedApp = lastForegroundApp; // markujem prethodni app kao recently dismissed
                lastDismissTime = currentTime;
            }
            Log.d(TAG, "Foreground app changed to: " + currentForegroundApp);
        }

        // proveravam da li trenutni app treba da bude blokiran (i na app change i kontinuirano dok app radi)
        List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();

        // ako se foreground app upravo promenio na restricted app, snimim "open" za detailed stats
        if (appChanged && detailStatsManager != null) {
            for (AppRestriction restriction : restrictions) {
                if (restriction.getPackageName().equals(currentForegroundApp)) {
                    detailStatsManager.recordAppOpen(currentForegroundApp);
                    break;
                }
            }
        }

        // proveravam da li je penalty aktivan (proveravam svaki loop iteration, ne samo na app change)
        boolean penaltyActive = buddyManager != null && buddyManager.isPenaltyActive();
        
        // debug logging za penalty state
        if (penaltyActive) {
            Log.d(TAG, "Penalty is ACTIVE - checking if current app should be blocked: " + currentForegroundApp);
        }
        
        // buddy penalty: ako je penalty aktivan, instant blokiram bilo koji enabled restricted app
        // ovo preskace grace period - penalty ima prioritet
        // proveravam kontinuirano (ne samo na app change) da bi penalty radio i ako je app vec otvoren
        if (penaltyActive && !isBlockingActive) {
            boolean shouldBlock = shouldForceBlockForPenalty(currentForegroundApp, restrictions);
            if (shouldBlock) {
                Log.d(TAG, "Buddy penalty active - forcing block for restricted app: " + currentForegroundApp);
                // cistim recently dismissed flag da preskocim grace period kad je penalty aktivan
                recentlyDismissedApp = null;
                blockApp(currentForegroundApp, true /*dueToPenalty*/);
                // nastavljam na ostale provere (break system, daily reset, itd)
            } else if (currentForegroundApp != null) {
                // debug: logujem zasto ne blokiram
                Log.d(TAG, "Penalty active but not blocking " + currentForegroundApp + " - checking restrictions...");
                for (AppRestriction restriction : restrictions) {
                    if (restriction.getPackageName().equals(currentForegroundApp)) {
                        Log.d(TAG, "  Found restriction for " + currentForegroundApp + 
                              " - enabled: " + restriction.isEnabled() + 
                              ", limit: " + restriction.getDailyLimitMinutes() + " min");
                        break;
                    }
                }
            }
        }

        // ne blokiram odmah app koji je upravo dismissovan (dajem grace period)
        // ALI: preskacem grace period ako je penalty aktivan (handluje se gore)
        boolean recentlyDismissed = recentlyDismissedApp != null &&
                                   recentlyDismissedApp.equals(currentForegroundApp) &&
                                   (System.currentTimeMillis() - lastDismissTime) < BLOCKING_GRACE_PERIOD_MILLIS &&
                                   !penaltyActive; // grace period ne vazi kad je penalty aktivan

        // proveravam da li trenutni foreground app treba da bude blokiran (kontinuirano monitorisem)
        if (!recentlyDismissed && !isBlockingActive && usageStatsManager.shouldBlockApp(currentForegroundApp, restrictions)) {
            Log.d(TAG, "Time limit exceeded for current foreground app: " + currentForegroundApp + ", showing blocking overlay immediately");
            blockApp(currentForegroundApp);
        }

        // proveravam za time limit warnings
        checkTimeLimitWarnings(restrictions);
        
        // proveravam da li je bilo koji app limit prekrsen i updateujem penalty status
        // ovo handluje penalty setting/clearing za buddy system
        checkAndUpdatePenaltyStatus(restrictions);

        // updateujem last foreground app reference posle procesiranja
        lastForegroundApp = currentForegroundApp;
    }

    // updateuje usage time za app
    private void updateAppUsageTime(String packageName, long timeSpent) {
        if (packageName == null || timeSpent <= 0) return;

        List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();
        for (AppRestriction restriction : restrictions) {
            if (restriction.getPackageName().equals(packageName)) {
                restriction.addUsageTime(timeSpent);
                restriction.consumeSudokuBonus(timeSpent);
                preferencesManager.saveAppRestriction(restriction);

                // feedujem detailed hourly usage stats (u milisekundama) za restricted app
                if (detailStatsManager != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                    detailStatsManager.recordHourlyUsage(packageName, hour, timeSpent);
                }
                break;
            }
        }
    }

    // blokira app pokazivanjem blocking activity
    private void blockApp(String packageName) {
        blockApp(packageName, false);
    }

    // blokira app pokazivanjem blocking activity
    // kad je dueToPenalty true, blokiram jer je buddy penalty aktivan (ne jer su limiti prekrseni)
    private void blockApp(String packageName, boolean dueToPenalty) {
        // ne blokiram ako je sudoku igra aktivna
        if (isSudokuGameActive) {
            Log.d(TAG, "Sudoku game is active, skipping block for: " + packageName);
            return;
        }

        // ne blokiram ako vec blokiram ovaj app
        if (isBlockingActive) {
            Log.d(TAG, "Already blocking an app, skipping block for: " + packageName);
            return;
        }

        Log.d(TAG, "Blocking app: " + packageName + (dueToPenalty ? " (buddy penalty)" : ""));
        isBlockingActive = true;

        // snimim da je app limit prekrsen za detailed stats
        if (detailStatsManager != null && !dueToPenalty) {
            detailStatsManager.recordLimitExceeded(packageName);
        }

        // otkazujem time warning notifikaciju jer sada pokazujem blocking overlay
        notificationManager.cancelTimeLimitWarning();

        Intent intent = new Intent(this, BlockingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                       Intent.FLAG_ACTIVITY_CLEAR_TOP |
                       Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("blocked_package", packageName);
        intent.putExtra("due_to_penalty", dueToPenalty);
        startActivity(intent);
    }

    // vraca true kad trenutni app treba da bude blokiran samo jer je buddy penalty aktivan
    // kad je penalty aktivan, force-blokiram SAMO jedan app koji je random izabran za ovaj penalty
    private boolean shouldForceBlockForPenalty(String packageName, List<AppRestriction> restrictions) {
        if (packageName == null) return false;

        if (buddyManager == null) return false;
        String lockedPackage = buddyManager.getLockedPenaltyPackage();
        if (lockedPackage == null || !lockedPackage.equals(packageName)) {
            return false;
        }

        for (AppRestriction restriction : restrictions) {
            if (!restriction.isEnabled()) {
                continue;
            }

            if (lockedPackage.equals(restriction.getPackageName())) {
                return true;
            }
        }

        return false;
    }


    // thresholdi na kojima pokazujem notifikacije (samo ovi specificni procenti)
    private static final int[] NOTIFICATION_THRESHOLDS = {50, 80, 90};

    // proverava time limit warnings i salje notifikacije samo na specificnim thresholdima
    // (50%, 80%, 90%) i samo jednom po thresholdu po danu
    private void checkTimeLimitWarnings(List<AppRestriction> restrictions) {
        for (AppRestriction restriction : restrictions) {
            int usagePercent = restriction.getUsagePercentage();
            int lastNotified = restriction.getLastNotifiedThreshold();
            
            // nadjem najvisi threshold koji je presao ali nije jos notifikovan
            for (int threshold : NOTIFICATION_THRESHOLDS) {
                if (usagePercent >= threshold && lastNotified < threshold) {
                    // ovaj threshold je presao i nisam jos notifikovao za njega
                    notificationManager.showTimeLimitWarning(
                        restriction,
                        threshold,
                        restriction.getRemainingTimeMinutes()
                    );
                    
                    // updateujem last notified threshold
                    restriction.setLastNotifiedThreshold(threshold);
                    preferencesManager.saveAppRestriction(restriction);
                    
                    // notifikujem samo za jedan threshold odjednom
                    break;
                }
            }
            
            // ako usage dodje do 100%, otkazujem warning notifikaciju
            if (usagePercent >= 100) {
                notificationManager.cancelTimeLimitWarning();
            }
        }
    }
    
    // proverava da li je bilo koji app limit prekrsen i updateuje penalty status u Firebase
    // proverava SVE enabled restrikcije - ako je BILO KOJI app limit prekrsen, setujem penalty
    // proverava samo appove koji imaju validan limit (> 0 minuta)
    // KRITICNO: setujem penalty samo ako je limit STVARNO prekrsen (ne samo blizu limita)
    private void checkAndUpdatePenaltyStatus(List<AppRestriction> restrictions) {
        if (buddyManager == null) return;

        // buddy penalty je buddy-system feature; ako nema aktivne buddy konekcije,
        // ocistim bilo koji lingering penalty state jednom i preskocim dalje provere
        if (!buddyManager.hasActiveBuddyConnection()) {
            if (buddyManager.isPenaltyActive()) {
                buddyManager.setPenaltyActive(false, "No buddy connected - clearing buddy penalty");
            }
            return;
        }

        int exceededAppCount = 0;
        String lastExceededAppName = null;

        // proveravam SVE enabled restrikcije - ako vise od jednog appa ima prekrsen limit, setujem penalty
        for (AppRestriction restriction : restrictions) {
            if (!restriction.isEnabled()) {
                continue; // preskacem disabled restrikcije
            }

            // preskacem restrikcije bez limita (0 minuta znaci unlimited)
            if (restriction.getDailyLimitMinutes() <= 0) {
                continue;
            }

            // ako postoji aktivan bonus (Sudoku / Kairos), ne smatram ga jos prekrsenim
            if (restriction.isSudokuBonusActive()) {
                continue;
            }

            // koristim pravi system usage za dan da bi buddy penalty radio preko restartova
            long usedTodayMs = usageStatsManager.getAppUsageTimeToday(restriction.getPackageName());
            long limitMs = restriction.getDailyLimitMinutes() * 60 * 1000L;

            if (usedTodayMs > limitMs) {
                exceededAppCount++;
                lastExceededAppName = restriction.getAppName();
                Log.d(TAG, "App limit EXCEEDED: " + restriction.getAppName() +
                      " (used: " + (usedTodayMs / 60000) + "m, " +
                      "limit: " + restriction.getDailyLimitMinutes() + "m, " +
                      "exceeded by: " + ((usedTodayMs - limitMs) / 60000) + "m)");
            }
        }
        
        // updateujem penalty status - setujem na true SAMO ako je vise od jednog app limita prekrseno
        boolean shouldApplyPenalty = exceededAppCount > 1;
        buddyManager.setPenaltyActive(shouldApplyPenalty,
            shouldApplyPenalty ? "You exceeded limits for multiple apps, last: " + lastExceededAppName : null);
        
        if (!shouldApplyPenalty) {
            Log.d(TAG, "Buddy penalty not active - 0 or 1 app over limit (count=" + exceededAppCount + ")");
        }
    }

    // cuva total screen time u preferences
    private void saveTotalScreenTime(long elapsedMillis) {
        if (elapsedMillis <= 0) return;
        
        try {
            // uzimam danasnji date key (pocetak dana u milisekundama)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();
            
            // uzimam trenutni total screen time za danas
            long currentTotal = preferencesManager.getTotalScreenTimeForDate(todayStart);
            
            // dodajem elapsed time
            long newTotal = currentTotal + elapsedMillis;
            
            // cuvam u preferences
            preferencesManager.setTotalScreenTimeForDate(todayStart, newTotal);
            
            Log.d(TAG, "Saved screen time: +" + (elapsedMillis / 1000) + "s, Total today: " + (newTotal / 1000) + "s");
        } catch (Exception e) {
            Log.e(TAG, "Error saving total screen time", e);
        }
    }

    // proverava break system i pokazuje reminderove
    private void checkBreakSystem() {
        BreakSettings breakSettings = preferencesManager.loadBreakSettings();
        if (!breakSettings.isEnabled()) return;

        long currentTime = System.currentTimeMillis();

        if (breakSettings.isTimeForBreak(currentTime)) {
            notificationManager.showBreakReminder(breakSettings.getBreakDurationMinutes());
            breakSettings.startBreak();
            preferencesManager.saveBreakSettings(breakSettings);
        } else if (breakSettings.shouldBreakEnd(currentTime)) {
            breakSettings.endBreak();
            preferencesManager.saveBreakSettings(breakSettings);
        }
    }

    // proverava da li je potreban daily reset
    private void checkDailyReset() {
        long lastReset = preferencesManager.getLastResetTime();
        long currentTime = System.currentTimeMillis();

        Calendar lastResetCalendar = Calendar.getInstance();
        lastResetCalendar.setTimeInMillis(lastReset);

        Calendar currentCalendar = Calendar.getInstance();
        currentCalendar.setTimeInMillis(currentTime);

        // proveravam da li je novi dan
        if (lastResetCalendar.get(Calendar.DAY_OF_YEAR) != currentCalendar.get(Calendar.DAY_OF_YEAR) ||
            lastResetCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)) {

            performDailyReset();
            preferencesManager.setLastResetTime(currentTime);
        }
    }

    // izvrsava daily reset usage statistika
    private void performDailyReset() {
        Log.d(TAG, "Performing daily reset");

        List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();
        for (AppRestriction restriction : restrictions) {
            restriction.resetDailyUsage();
        }
        preferencesManager.saveAppRestrictions(restrictions);
    }

    // updateuje usage statistike
    private void updateUsageStats() {
        // ovo bi obicno sakupilo detaljnije usage stats
        // za sada se oslanjam na real-time monitoring gore
        Log.d(TAG, "Updating usage statistics");
        
        // updateujem Firebase sa total daily usage i per-app stats za buddy system
        if (buddyManager != null && buddyManager.isLoggedIn()) {
            List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();
            long totalUsageMs = 0;
            java.util.Map<String, com.escape.app.model.BuddyAppStats> restrictedApps = new HashMap<>();

            for (AppRestriction restriction : restrictions) {
                long appUsage = usageStatsManager.getAppUsageTimeToday(restriction.getPackageName());
                totalUsageMs += appUsage;

                com.escape.app.model.BuddyAppStats stats = new com.escape.app.model.BuddyAppStats(
                    restriction.getPackageName(),
                    restriction.getAppName(),
                    restriction.getDailyLimitMinutes(),
                    appUsage
                );
                restrictedApps.put(restriction.getPackageName(), stats);
            }

            long totalMinutes = totalUsageMs / (1000 * 60);
            buddyManager.updateDailyUsage(totalMinutes);
            buddyManager.updateRestrictedAppStats(restrictedApps);
        }
    }
}
