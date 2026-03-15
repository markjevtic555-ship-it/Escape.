package com.escape.app.model;

import com.google.firebase.database.IgnoreExtraProperties;

// lightweight model za izlaganje korisnikovog restricted app stats njihovom buddyju preko Firebase-a
@IgnoreExtraProperties
public class BuddyAppStats {

    public String packageName;
    public String appName;
    public int dailyLimitMinutes;
    public long usageTodayMs;

    // required prazan konstruktor za Firebase
    public BuddyAppStats() {
    }

    public BuddyAppStats(String packageName, String appName, int dailyLimitMinutes, long usageTodayMs) {
        this.packageName = packageName;
        this.appName = appName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.usageTodayMs = usageTodayMs;
    }
}



