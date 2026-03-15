package com.escape.app.model;

// NE RADI NIJE TRENUTNO U APLIKACIJI, OSTAVLJENO ZA BUDUĆE NADOGRADNJE
// model klasa za break sistem postavke
public class BreakSettings {
    private boolean enabled; // da li je break system ukljucen
    private int workIntervalMinutes; // minuti rada pre pauze (default 60)
    private int breakDurationMinutes; // minuti trajanja pauze (default 5)
    private long lastBreakTime; // timestamp poslednjeg pauze
    private boolean isOnBreak; // da li je trenutno na pauzi

    public BreakSettings() {
        this.enabled = true;
        this.workIntervalMinutes = 60; // 1 sat rada
        this.breakDurationMinutes = 5; // 5 minuta pauze
        this.lastBreakTime = 0;
        this.isOnBreak = false;
    }

    // getteri i setteri
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWorkIntervalMinutes() {
        return workIntervalMinutes;
    }

    public void setWorkIntervalMinutes(int workIntervalMinutes) {
        this.workIntervalMinutes = workIntervalMinutes;
    }

    public int getBreakDurationMinutes() {
        return breakDurationMinutes;
    }

    public void setBreakDurationMinutes(int breakDurationMinutes) {
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public long getLastBreakTime() {
        return lastBreakTime;
    }

    public void setLastBreakTime(long lastBreakTime) {
        this.lastBreakTime = lastBreakTime;
    }

    public boolean isOnBreak() {
        return isOnBreak;
    }

    public void setOnBreak(boolean onBreak) {
        isOnBreak = onBreak;
    }

    // provjerava da li je vrijeme za pauzu na osnovu intervala rada
    public boolean isTimeForBreak(long currentTime) {
        if (!enabled || isOnBreak) return false;

        long timeSinceLastBreak = currentTime - lastBreakTime;
        long workIntervalMillis = workIntervalMinutes * 60 * 1000L;

        return timeSinceLastBreak >= workIntervalMillis;
    }

    // provjerava da li trenutna pauza treba da se završi
    public boolean shouldBreakEnd(long currentTime) {
        if (!enabled || !isOnBreak) return false;

        long breakDurationMillis = breakDurationMinutes * 60 * 1000L;
        long timeSinceBreakStart = currentTime - lastBreakTime;

        return timeSinceBreakStart >= breakDurationMillis;
    }

    // pokreće break
    public void startBreak() {
        this.isOnBreak = true;
        this.lastBreakTime = System.currentTimeMillis();
    }

    // završava break
    public void endBreak() {
        this.isOnBreak = false;
    }

    // uzima preostalo vrijeme u trenutnom breaku (u milisekundama)
    public long getBreakTimeRemaining(long currentTime) {
        if (!isOnBreak) return 0;

        long breakDurationMillis = breakDurationMinutes * 60 * 1000L;
        long elapsedBreakTime = currentTime - lastBreakTime;

        return Math.max(0, breakDurationMillis - elapsedBreakTime);
    }

    // uzima vreme do sledeće pauze (u milisekundama)
    public long getTimeUntilNextBreak(long currentTime) {
        if (!enabled || isOnBreak) return 0;

        long timeSinceLastBreak = currentTime - lastBreakTime;
        long workIntervalMillis = workIntervalMinutes * 60 * 1000L;

        return Math.max(0, workIntervalMillis - timeSinceLastBreak);
    }

    @Override
    public String toString() {
        return "BreakSettings{" +
                "enabled=" + enabled +
                ", workInterval=" + workIntervalMinutes + "min" +
                ", breakDuration=" + breakDurationMinutes + "min" +
                ", onBreak=" + isOnBreak +
                '}';
    }
}
