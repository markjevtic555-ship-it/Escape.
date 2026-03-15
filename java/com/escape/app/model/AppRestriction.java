package com.escape.app.model;

// model klasa koja predstavlja app restrikciju sa time limitima i usage trackingom
public class AppRestriction {
    private String packageName;
    private String appName;
    private int dailyLimitMinutes; // dnevni time limit u minutama
    private long totalTimeUsedToday; // vrijeme korišćeno danas u milisekundama
    private long lastResetTime; // timestamp posljednjeg dnevnog resetovanja
    private boolean isEnabled; // da li je restrikcija aktivna
    private long sudokuBonusRemainingMillis; // preostalo bonus vrijeme od igrice (broji se samo dok je app u foregroundu)
    private int lastNotifiedThreshold; // prati posljednji percentage threshold na koji sam notifikovao (50, 80, 90)

    public AppRestriction(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
        this.dailyLimitMinutes = 60; // default 1 sat
        this.totalTimeUsedToday = 0;
        this.lastResetTime = System.currentTimeMillis();
        this.isEnabled = true;
        this.sudokuBonusRemainingMillis = 0;
        this.lastNotifiedThreshold = 0;
    }

    // konstruktor sa ručno postavljenim ograničenjem
    public AppRestriction(String packageName, String appName, int dailyLimitMinutes) {
        this(packageName, appName);
        this.dailyLimitMinutes = dailyLimitMinutes;
    }

    // getteri i setteri
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getDailyLimitMinutes() {
        return dailyLimitMinutes;
    }

    public void setDailyLimitMinutes(int dailyLimitMinutes) {
        this.dailyLimitMinutes = dailyLimitMinutes;
    }

    public long getTotalTimeUsedToday() {
        return totalTimeUsedToday;
    }

    public void setTotalTimeUsedToday(long totalTimeUsedToday) {
        this.totalTimeUsedToday = totalTimeUsedToday;
    }

    public long getLastResetTime() {
        return lastResetTime;
    }

    public void setLastResetTime(long lastResetTime) {
        this.lastResetTime = lastResetTime;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public long getSudokuBonusRemainingMillis() {
        return sudokuBonusRemainingMillis;
    }

    public void setSudokuBonusRemainingMillis(long sudokuBonusRemainingMillis) {
        this.sudokuBonusRemainingMillis = Math.max(0, sudokuBonusRemainingMillis);
    }

    public int getLastNotifiedThreshold() {
        return lastNotifiedThreshold;
    }

    public void setLastNotifiedThreshold(int lastNotifiedThreshold) {
        this.lastNotifiedThreshold = lastNotifiedThreshold;
    }

    // daje 10 minuta neogranicenog koriscenja (broji se samo dok je app u foregroundu)
    public void grantSudokuBonus() {
        this.sudokuBonusRemainingMillis = 10 * 60 * 1000L; // 10 minuta u milisekundama
    }

    // proverava da li je sudoku bonus trenutno aktivan (user moze da koristi app bez obzira na limit)
    public boolean isSudokuBonusActive() {
        return sudokuBonusRemainingMillis > 0;
    }

    // trosi bonus vrijeme dok je app u foregroundu
    public void consumeSudokuBonus(long timeMillis) {
        if (timeMillis <= 0 || sudokuBonusRemainingMillis <= 0) return;
        sudokuBonusRemainingMillis = Math.max(0, sudokuBonusRemainingMillis - timeMillis);
    }

    // uzima preostalo bonus vrijeme u milisekundama (0 ako nema aktivnog bonusa)
    public long getRemainingBonusTimeMillis() {
        return sudokuBonusRemainingMillis;
    }

    // uzima preostalo vrijeme u milisekundama
    public long getRemainingTimeMillis() {
        long limitMillis = dailyLimitMinutes * 60 * 1000L;
        return Math.max(0, limitMillis - totalTimeUsedToday);
    }

    // uzima preostalo vrijeme u minutama
    public int getRemainingTimeMinutes() {
        return (int) (getRemainingTimeMillis() / (60 * 1000));
    }

    // proverava da li je dnevni limit prekrsen
    // vraca false ako je bonus aktivan (korisnik ima 10 min)
    public boolean isLimitExceeded() {
        // ako je sudoku bonus aktivan, ne blokiram
        if (isSudokuBonusActive()) {
            return false;
        }
        return totalTimeUsedToday >= (dailyLimitMinutes * 60 * 1000L);
    }

    // uzima procenat korišćenja (0-100)
    public int getUsagePercentage() {
        if (dailyLimitMinutes <= 0) return 0;
        long limitMillis = dailyLimitMinutes * 60 * 1000L;
        return (int) ((totalTimeUsedToday * 100) / limitMillis);
    }

    // dodaje količinu vremena iskorišteno na trenutni total
    public void addUsageTime(long timeMillis) {
        this.totalTimeUsedToday += timeMillis;
    }

    // resetuje dnevni usage counter
    public void resetDailyUsage() {
        this.totalTimeUsedToday = 0;
        this.sudokuBonusRemainingMillis = 0;
        this.lastNotifiedThreshold = 0;
        this.lastResetTime = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AppRestriction that = (AppRestriction) obj;
        return packageName != null ? packageName.equals(that.packageName) : that.packageName == null;
    }

    @Override
    public int hashCode() {
        return packageName != null ? packageName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return appName + " (" + dailyLimitMinutes + " min/day, " + getUsagePercentage() + "% used)";
    }
}
