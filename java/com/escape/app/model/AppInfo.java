package com.escape.app.model;

import android.graphics.drawable.Drawable;

// model klasa koja predstavlja instaliranu aplikaciju sa osnovnim informacijama
public class AppInfo {
    private String packageName;
    private String appName;
    private Drawable icon;
    private boolean isSystemApp;
    private long installTime;

    public AppInfo(String packageName, String appName, Drawable icon, boolean isSystemApp, long installTime) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
        this.installTime = installTime;
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

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public void setSystemApp(boolean systemApp) {
        isSystemApp = systemApp;
    }

    public long getInstallTime() {
        return installTime;
    }

    public void setInstallTime(long installTime) {
        this.installTime = installTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AppInfo appInfo = (AppInfo) obj;
        return packageName != null ? packageName.equals(appInfo.packageName) : appInfo.packageName == null;
    }

    @Override
    public int hashCode() {
        return packageName != null ? packageName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return appName != null ? appName : packageName;
    }
}
