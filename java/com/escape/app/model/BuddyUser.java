package com.escape.app.model;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

// user data model za buddy sistem
// čuva se u Firebase Realtime Database
@IgnoreExtraProperties
public class BuddyUser {
    
    public String odorId;
    public String email;
    public String displayName;
    public String buddyCode;      // jedinstveni 8-karakterski kod za ovog korisnika
    public String buddyId;        // userId povezanog buddyja (null ako nije povezan)
    public String buddyEmail;     // email povezanog buddyja za prikaz
    public long dailyMinutesUsed; // minuti korišćeni danas (zastarjelo, zadržano za kompatibilnost)
    public long lastUpdated;      // timestamp poslednje nadogradnje
    public long lastResetDate;    // datum kad je dailyMinutesUsed poslednji put resetovan
    public boolean penaltyActive; // da li je kazna trenutno aktivna
    public int totalDaysTracked;  // total dana u sistemu
    public int streakDays;        // trenutni streak ostajanja ispod limita

    // snapshot ovog userovog restricted appova + usage, izložen njihovom buddyju (read-only na buddy strani)
    public Map<String, BuddyAppStats> restrictedApps;
    
    // real-time app usage tracking
    public String currentAppPackage;  // package name aplikacije koja se trenutno koristi
    public String currentAppName;     // display name trenutne aplikacije
    public long currentAppStartTime;  // timestamp kad je trenutni app usage poceo

    // obavezni prazan konstruktor za Firebase
    public BuddyUser() {}

    public BuddyUser(String odorId, String email) {
        this.odorId = odorId;
        this.email = email;
        this.displayName = extractDisplayName(email);
        this.buddyCode = generateBuddyCode(odorId);
        this.buddyId = null;
        this.buddyEmail = null;
        this.dailyMinutesUsed = 0;
        this.lastUpdated = System.currentTimeMillis();
        this.lastResetDate = System.currentTimeMillis();
        this.penaltyActive = false;
        this.totalDaysTracked = 0;
        this.streakDays = 0;
        this.currentAppPackage = null;
        this.currentAppName = null;
        this.currentAppStartTime = 0;
        this.restrictedApps = new HashMap<>();
    }

    // generise jedinstveni 8-karakterski buddy kod iz userId
    private String generateBuddyCode(String odorId) {
        if (odorId == null || odorId.length() < 8) {
            return "ESCAPE00";
        }
        // uzimam karaktere iz različitih dijlova userId i pravim uppercase
        StringBuilder code = new StringBuilder();
        code.append(odorId.substring(0, 2).toUpperCase());
        code.append(odorId.substring(odorId.length() / 2, odorId.length() / 2 + 2).toUpperCase());
        code.append(odorId.substring(odorId.length() - 4).toUpperCase());
        
        // zamjenjujem bilo koje non-alphanumeric karaktere
        String result = code.toString().replaceAll("[^A-Z0-9]", "X");
        
        // osiguravam tačno 8 karaktera
        if (result.length() < 8) {
            result = result + "00000000".substring(0, 8 - result.length());
        } else if (result.length() > 8) {
            result = result.substring(0, 8);
        }
        
        return result;
    }

    // izvlaci display name iz emaila
    private String extractDisplayName(String email) {
        if (email == null || !email.contains("@")) {
            return "User";
        }
        String name = email.substring(0, email.indexOf("@"));
        //  prvo slovo veliko
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // proverava da li user ima povezanog buddyja
    @Exclude
    public boolean hasBuddy() {
        return buddyId != null && !buddyId.isEmpty();
    }

    // proverava da li dnevni usage prelazi limit (zastarjelo - vise se ne koristi)
    @Exclude
    public boolean hasExceededLimit() {
        return false;
    }
    
    // uzima trajanje trenutnog korišćenja aplikacije u milisekundama
    @Exclude
    public long getCurrentAppDurationMs() {
        if (currentAppStartTime <= 0) return 0;
        return System.currentTimeMillis() - currentAppStartTime;
    }
    
    // uzima trajanje trenutnog app usagea u minutama
    @Exclude
    public long getCurrentAppDurationMinutes() {
        return getCurrentAppDurationMs() / (60 * 1000);
    }
    
    // provjerava da li je trenutna aplikacija korišćen preko 1 sata
    @Exclude
    public boolean isCurrentAppOverOneHour() {
        return getCurrentAppDurationMinutes() >= 60;
    }

    // uzima formatirani usage string
    @Exclude
    public String getFormattedUsage() {
        if (dailyMinutesUsed >= 60) {
            long hours = dailyMinutesUsed / 60;
            long mins = dailyMinutesUsed % 60;
            return hours + "h " + mins + "m";
        }
        return dailyMinutesUsed + "m";
    }

    // uzima usage percentage (zastarjelo - vise ne koristim 120-minutni limit)
    @Exclude
    public int getUsagePercentage() {
        return 0; // vise ne koristim percentage
    }

    // konvertuje u Map za Firebase nadogradnje
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("userId", odorId);
        result.put("email", email);
        result.put("displayName", displayName);
        result.put("buddyCode", buddyCode);
        result.put("buddyId", buddyId);
        result.put("buddyEmail", buddyEmail);
        result.put("dailyMinutesUsed", dailyMinutesUsed);
        result.put("lastUpdated", lastUpdated);
        result.put("lastResetDate", lastResetDate);
        result.put("penaltyActive", penaltyActive);
        result.put("totalDaysTracked", totalDaysTracked);
        result.put("streakDays", streakDays);
        result.put("currentAppPackage", currentAppPackage);
        result.put("currentAppName", currentAppName);
        result.put("currentAppStartTime", currentAppStartTime);
        result.put("restrictedApps", restrictedApps);
        return result;
    }

    @Override
    public String toString() {
        return "BuddyUser{" +
                "email='" + email + '\'' +
                ", buddyCode='" + buddyCode + '\'' +
                ", hasBuddy=" + hasBuddy() +
                ", dailyMinutesUsed=" + dailyMinutesUsed +
                '}';
    }
}

