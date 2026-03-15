package com.escape.app.model;

import java.util.List;

// model klasa koja predstavlja focus sesiju sa restricted appovima i trajanjem
public class FocusSession {
    private long sessionId;
    private long startTime;
    private long endTime; // 0 ako je još aktivna
    private List<String> restrictedPackageNames; // appovi zabranjeni tokom ove sesije
    private boolean isActive;
    private String sessionName; // opcionalno ime za sesiju

    public FocusSession(long sessionId, List<String> restrictedPackageNames) {
        this.sessionId = sessionId;
        this.restrictedPackageNames = restrictedPackageNames;
        this.startTime = System.currentTimeMillis();
        this.endTime = 0;
        this.isActive = true;
        this.sessionName = "Focus Session";
    }

    public FocusSession(long sessionId, List<String> restrictedPackageNames, String sessionName) {
        this(sessionId, restrictedPackageNames);
        this.sessionName = sessionName;
    }

    // getteri i setteri
    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public List<String> getRestrictedPackageNames() {
        return restrictedPackageNames;
    }

    public void setRestrictedPackageNames(List<String> restrictedPackageNames) {
        this.restrictedPackageNames = restrictedPackageNames;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        if (!active && endTime == 0) {
            endTime = System.currentTimeMillis();
        }
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    // uzima trajanje sesije u milisekundama
    public long getDurationMillis() {
        if (isActive) {
            return System.currentTimeMillis() - startTime;
        } else {
            return endTime - startTime;
        }
    }

    // uzima trajanje sesije u minutama
    public long getDurationMinutes() {
        return getDurationMillis() / (60 * 1000);
    }

    // provjerava da li je package zabranjen u ovoj sesiji
    public boolean isPackageRestricted(String packageName) {
        return restrictedPackageNames != null && restrictedPackageNames.contains(packageName);
    }

    // zavrsava sesiju
    public void endSession() {
        setActive(false);
    }

    @Override
    public String toString() {
        return sessionName + " (" + getDurationMinutes() + " min, " +
               (isActive ? "active" : "ended") + ")";
    }
}
