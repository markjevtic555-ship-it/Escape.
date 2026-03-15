package com.escape.app.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

// DO DALJNJEG NE FUNKCIONIŠE JER SE FIREBASE STORAGE PLAĆA!!! Mi te pare NEMAMO
/**
 * Upravlja app icon upload/download to/from Firebase Storage
 * Dozvoljava buddyjima da vide app ikone čak i ako nemaju app instaliran
 */
public class AppIconManager {
    private static final String TAG = "AppIconManager";
    private static final String STORAGE_PATH_ICONS = "app_icons";
    private static final int ICON_SIZE_DP = 192;
    
    private final Context context;
    private final PackageManager packageManager;
    private final FirebaseStorage storage;
    private final Map<String, String> iconUrlCache = new HashMap<>();
    
    public AppIconManager(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.storage = FirebaseStorage.getInstance();
    }
    
    /**
     * Uploaduje app icon u Firebase Storage
     * Poziva se kad se app promijeni da osiguram da je ikona dostupna za buddyje
     */
    public void uploadAppIcon(String packageName, String appName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "uploadAppIcon: Package name is null or empty");
            return;
        }
        
        Log.d(TAG, "Attempting to upload icon for: " + packageName);
        
        // Uvijek pokušavam upload (Firebase Storage će handlovati duplikate)
        // Ovo osigurava da su ikone dostupne za buddyje čak i ako je cache očišćen
        
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            Drawable iconDrawable = packageManager.getApplicationIcon(appInfo);
            Log.d(TAG, "Got app icon drawable for: " + packageName);
            
            Bitmap iconBitmap = drawableToBitmap(iconDrawable);
            if (iconBitmap == null) {
                Log.w(TAG, "Failed to convert icon to bitmap for: " + packageName);
                return;
            }
            
            int sizePx = (int) (ICON_SIZE_DP * context.getResources().getDisplayMetrics().density);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(iconBitmap, sizePx, sizePx, true);
            
            // Konvertujem u PNG byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos);
            byte[] iconData = baos.toByteArray();
            
            // Uploadujem u Firebase Storage
            String fileName = sanitizePackageName(packageName) + ".png";
            StorageReference iconRef = storage.getReference().child(STORAGE_PATH_ICONS).child(fileName);
            
            Log.d(TAG, "Uploading icon to Firebase Storage: " + STORAGE_PATH_ICONS + "/" + fileName + " (size: " + iconData.length + " bytes)");
            
            UploadTask uploadTask = iconRef.putBytes(iconData);
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Log.d(TAG, "Icon upload successful for: " + packageName);
                    // Get download URL and cache it
                    iconRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<android.net.Uri>() {
                        @Override
                        public void onSuccess(android.net.Uri downloadUrl) {
                            iconUrlCache.put(packageName, downloadUrl.toString());
                            Log.d(TAG, "Icon uploaded successfully for: " + packageName + " -> " + downloadUrl);
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to get download URL for: " + packageName, e);
                        }
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Failed to upload icon for: " + packageName + " - Error: " + e.getMessage(), e);
                }
            }).addOnProgressListener(new com.google.firebase.storage.OnProgressListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Upload progress for " + packageName + ": " + String.format("%.2f", progress) + "%");
                }
            });
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "App not found for icon upload: " + packageName);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading icon for: " + packageName, e);
        }
    }
    
    /**
     * Downloaduje app icon iz Firebase Storage
     * Vraća lokalni file path ili null ako download ne uspije
     */
    public void downloadAppIcon(String packageName, IconDownloadCallback callback) {
        if (packageName == null || packageName.isEmpty()) {
            callback.onError("Package name is null or empty");
            return;
        }
        
        // Provjeravam lokalni cache prvo (najbrže)
        String cachedPath = getCachedIconPath(packageName);
        if (cachedPath != null) {
            callback.onSuccess(cachedPath);
            return;
        }
        
        // Uzimam iz Firebase Storage koristeći direktan file download (pouzdanije)
        String fileName = sanitizePackageName(packageName) + ".png";
        StorageReference iconRef = storage.getReference().child(STORAGE_PATH_ICONS).child(fileName);
        
        // Pravim cache direktorij
        File cacheDir = new File(context.getCacheDir(), "app_icons");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        File iconFile = new File(cacheDir, fileName);
        
        // Downloadujem direktno iz Firebase Storage u lokalni file
        iconRef.getFile(iconFile).addOnSuccessListener(new OnSuccessListener<com.google.firebase.storage.FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(com.google.firebase.storage.FileDownloadTask.TaskSnapshot taskSnapshot) {
                Log.d(TAG, "Icon downloaded successfully from Firebase Storage: " + packageName);
                callback.onSuccess(iconFile.getAbsolutePath());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Icon not found in Firebase Storage for: " + packageName + " - " + e.getMessage());
                // Try alternative: get download URL and download manually
                iconRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<android.net.Uri>() {
                    @Override
                    public void onSuccess(android.net.Uri downloadUrl) {
                        iconUrlCache.put(packageName, downloadUrl.toString());
                        downloadIconFromUrl(downloadUrl.toString(), packageName, callback);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e2) {
                        Log.w(TAG, "Failed to get download URL for: " + packageName);
                        callback.onError("Icon not available in Firebase Storage");
                    }
                });
            }
        });
    }
    
    /**
     * Downloaduje icon iz URL-a i čuva u lokalni cache
     */
    private void downloadIconFromUrl(String url, String packageName, IconDownloadCallback callback) {
        try {
            // Create cache directory
            File cacheDir = new File(context.getCacheDir(), "app_icons");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File iconFile = new File(cacheDir, sanitizePackageName(packageName) + ".png");
            
            // If already cached, return immediately
            if (iconFile.exists()) {
                callback.onSuccess(iconFile.getAbsolutePath());
                return;
            }
            
            // Download from URL
            new Thread(() -> {
                try {
                    java.net.URL downloadUrl = new java.net.URL(url);
                    java.io.InputStream inputStream = downloadUrl.openStream();
                    FileOutputStream outputStream = new FileOutputStream(iconFile);
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    outputStream.close();
                    inputStream.close();
                    
                    callback.onSuccess(iconFile.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading icon from URL: " + url, e);
                    callback.onError(e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up icon download: " + packageName, e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * Uzima cached icon file path ako postoji
     */
    @Nullable
    public String getCachedIconPath(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        
        File cacheDir = new File(context.getCacheDir(), "app_icons");
        File iconFile = new File(cacheDir, sanitizePackageName(packageName) + ".png");
        
        if (iconFile.exists()) {
            return iconFile.getAbsolutePath();
        }
        
        return null;
    }
    
    /**
     * Konvertuje Drawable u Bitmap
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }
        
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        
        if (width <= 0 || height <= 0) {
            width = ICON_SIZE_DP;
            height = ICON_SIZE_DP;
        }
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        
        return bitmap;
    }
    
    /**
     * Sanitizuje package name za korištenje kao filename
     */
    private String sanitizePackageName(String packageName) {
        return packageName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Callback za icon download
     */
    public interface IconDownloadCallback {
        void onSuccess(String filePath);
        void onError(String error);
    }
}

