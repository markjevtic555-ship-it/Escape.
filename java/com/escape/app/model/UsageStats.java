package com.escape.app.model;

import java.util.HashMap;
import java.util.Map;

// model klasa za čuvanje usage statistics podataka
public class UsageStats {
    private long date; // datum u milisekundama (pocetak dana)
    private Map<String, Long> appUsageTime; // packageName -> vreme u milisekundama
    private long totalScreenTime; // total screen time za dan
    private int appSwitches; // broj app switchova tokom dana

    public UsageStats(long date) {
        this.date = date;
        this.appUsageTime = new HashMap<>();
        this.totalScreenTime = 0;
        this.appSwitches = 0;
    }

    // getteri i setteri
    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public Map<String, Long> getAppUsageTime() {
        return appUsageTime;
    }

    public void setAppUsageTime(Map<String, Long> appUsageTime) {
        this.appUsageTime = appUsageTime;
    }

    public long getTotalScreenTime() {
        return totalScreenTime;
    }

    public void setTotalScreenTime(long totalScreenTime) {
        this.totalScreenTime = totalScreenTime;
    }

    public int getAppSwitches() {
        return appSwitches;
    }

    public void setAppSwitches(int appSwitches) {
        this.appSwitches = appSwitches;
    }

    // dodaje usage time za specificni app
    public void addAppUsageTime(String packageName, long timeMillis) {
        Long currentTime = appUsageTime.get(packageName);
        if (currentTime == null) {
            currentTime = 0L;
        }
        appUsageTime.put(packageName, currentTime + timeMillis);
        totalScreenTime += timeMillis;
    }

    // uzima usage time za specificni app
    public long getAppUsageTime(String packageName) {
        Long time = appUsageTime.get(packageName);
        return time != null ? time : 0;
    }

    // uzima najkorisceniji app package name
    public String getMostUsedApp() {
        String mostUsed = null;
        long maxTime = 0;

        for (Map.Entry<String, Long> entry : appUsageTime.entrySet()) {
            if (entry.getValue() > maxTime) {
                maxTime = entry.getValue();
                mostUsed = entry.getKey();
            }
        }

        return mostUsed;
    }

    // uzima total broj koriscenih appova
    public int getAppsUsedCount() {
        return appUsageTime.size();
    }

    // povecava app switches counter
    public void incrementAppSwitches() {
        this.appSwitches++;
    }

    // formatuje vreme u milisekundama u string sa satima i minutama
    public static String formatTime(long timeMillis) {
        long totalMinutes = timeMillis / (1000 * 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    @Override
    public String toString() {
        return "UsageStats{" +
                "date=" + date +
                ", totalScreenTime=" + formatTime(totalScreenTime) +
                ", appsUsed=" + getAppsUsedCount() +
                ", appSwitches=" + appSwitches +
                '}';
    }
}
