package com.escape.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.escape.app.model.BuddyUser;
import com.escape.app.model.BuddyAppStats;
import com.escape.app.model.AppRestriction;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// manager klasa za sve Firebase Buddy System operacije
// handluje autentifikaciju, buddy konekcije i real-time usage tracking – sve što treba za buddy sistem
public class FirebaseBuddyManager {
    private static final String TAG = "FirebaseBuddyManager";
    
    // database putanje
    private static final String USERS_PATH = "users";
    private static final String BUDDY_CODES_PATH = "buddy_codes"; // index za brzu code lookup
    
    // više ne koristim 120-minutni dnevni limit
    // penaltyi su sada na osnovu individualnih app limitova
    
    // SharedPreferences
    private static final String PREFS_NAME = "buddy_prefs";
    private static final String KEY_PENALTY_ACTIVE = "penalty_active";
    // pratim odvojene izvore da moji limit-checkovi ne obrišu buddy-primenjeni penalty
    private static final String KEY_PENALTY_ACTIVE_SELF = "penalty_active_self";
    private static final String KEY_PENALTY_ACTIVE_BUDDY = "penalty_active_buddy";
    private static final String KEY_LAST_PENALTY_CHECK = "last_penalty_check";
    private static final String KEY_PENALTY_LOCKED_PACKAGE = "penalty_locked_package";
    private static final String KEY_PENALTY_LOCKED_APP_NAME = "penalty_locked_app_name";

    private final FirebaseAuth auth;
    private final DatabaseReference database;
    private final Context context;
    private final SharedPreferences prefs;
    
    // listeneri za real-time updates
    private ValueEventListener buddyListener;
    private ValueEventListener currentUserListener;
    private DatabaseReference buddyRef;
    private DatabaseReference currentUserRef;
    private String attachedBuddyId = null; // sprečavam ponovno attachovanje buddy listenera
    private boolean lastBuddyPenaltyState = false; // pratim prethodno buddy penalty stanje da detektujem tranzicije
    private boolean buddyPenaltyStateInitialized = false; // pratim da li sam inicijalizovao buddy penalty stanje
    private boolean hasActiveBuddyConnection = false; // true kad je trenutni user povezan sa buddyjem
    private final Random random = new Random();

    // callbackovi
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String error);
    }

    public interface BuddyCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface UserDataCallback {
        void onUserLoaded(BuddyUser user);
        void onError(String error);
    }

    public interface BuddyCodeCallback {
        void onCodeFound(String odorId);
        void onCodeNotFound();
        void onError(String error);
    }

    // vraća true kad je trenutni user povezan sa buddyjem (na osnovu najnovijih realtime podataka)
    public boolean hasActiveBuddyConnection() {
        return hasActiveBuddyConnection;
    }

    public interface BuddyUpdateListener {
        void onBuddyDataChanged(BuddyUser buddy);
        void onBuddyDisconnected();
        void onPenaltyTriggered(String reason);
        void onOneHourNotification(String appName, String message);
    }

    public FirebaseBuddyManager(Context context) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();

        // inicijalizujem Firebase Database referencu sa ispravnim regionalnim URL-om
        DatabaseReference dbRef;
        try {
            // koristim ispravni regionalni database URL (europe-west1)
            String databaseUrl = "https://escape-392eb-default-rtdb.europe-west1.firebasedatabase.app";
            FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance(databaseUrl);
            dbRef = firebaseDatabase.getReference();
            Log.d(TAG, "Firebase Database initialized successfully with URL: " + databaseUrl);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase Database with regional URL, trying default", e);
            // fallback na default URL ako regionalni ne uspije
            try {
                FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
                dbRef = firebaseDatabase.getReference();
                Log.w(TAG, "Using default Firebase Database URL (may not match your database region)");
            } catch (Exception e2) {
                Log.e(TAG, "Failed to initialize Firebase Database with default URL", e2);
                dbRef = null;
            }
        }
        this.database = dbRef;

        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // izlazim database referencu za fokusirane account/profile updateove
    // koristi pažljivo – preferiraj više-nivo helpere kad je moguće
    public DatabaseReference getDatabase() {
        return database;
    }

    // ==================== AUTHENTICATION ====================

    // registruje novog usera sa emailom i passwordom – kreira profil u pozadini
    public void signUp(String email, String password, AuthCallback callback) {
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            callback.onError("Email and password are required");
            return;
        }

        if (password.length() < 6) {
            callback.onError("Password must be at least 6 characters");
            return;
        }

        Log.d(TAG, "Attempting to sign up user: " + email);
        
        // provjeravam da li je Firebase pravilno inicijalizovan
        if (auth == null) {
            Log.e(TAG, "Firebase Auth is null!");
            callback.onError("Firebase not initialized. Please restart the app.");
            return;
        }
        
        if (database == null) {
            Log.e(TAG, "Firebase Database is null!");
            callback.onError("Firebase Database not initialized. Please restart the app.");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                Log.d(TAG, "Signup task completed. Success: " + task.isSuccessful());
                if (task.isSuccessful()) {
                    Log.d(TAG, "Firebase auth signup successful");
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        Log.d(TAG, "Got Firebase user: " + user.getUid());
                        // vraćam success odmah (brzo kao login)
                        callback.onSuccess(user);
                        
                        // pravim profil u pozadini (non-blocking)
                        Log.d(TAG, "Creating profile in background...");
                        createUserProfile(user, new BuddyCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "User profile created successfully in background");
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Profile creation failed in background (non-critical): " + error);
                                // ne-kritično – profil će biti kreiran kad pristupim buddy featureima
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to get user after signup");
                        callback.onError("Failed to get user after signup. Please try logging in.");
                    }
                } else {
                    Exception exception = task.getException();
                    String error = "Signup failed";
                    if (exception != null) {
                        error = exception.getMessage();
                        Log.e(TAG, "Firebase auth signup failed", exception);
                    } else {
                        Log.e(TAG, "Firebase auth signup failed with no exception");
                    }
                    
                    // dajem user-friendly error poruke
                    if (error != null) {
                        if (error.contains("email address is already in use")) {
                            error = "This email is already registered. Please sign in instead.";
                        } else if (error.contains("network")) {
                            error = "Network error. Please check your internet connection.";
                        } else if (error.contains("invalid-email")) {
                            error = "Invalid email address. Please check and try again.";
                        } else if (error.contains("weak-password")) {
                            error = "Password is too weak. Please use a stronger password.";
                        }
                    }
                    callback.onError(error);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Signup failure listener triggered", e);
                String error = e.getMessage();
                if (error == null || error.isEmpty()) {
                    error = "Network error. Please check your connection and try again.";
                }
                callback.onError(error);
            });
    }

    // loguje postojećeg usera
    public void login(String email, String password, AuthCallback callback) {
        // trimujem i validiram inpute
        if (email == null) email = "";
        if (password == null) password = "";
        email = email.trim();
        
        if (email.isEmpty() || password.isEmpty()) {
            callback.onError("Email and password are required");
            return;
        }

        // validiram email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            callback.onError("Please enter a valid email address");
            return;
        }

        // provjeravam da li je Firebase pravilno inicijalizovan
        if (auth == null) {
            Log.e(TAG, "Firebase Auth is null!");
            callback.onError("Firebase not initialized. Please restart the app.");
            return;
        }

        Log.d(TAG, "Attempting to login user: " + email);

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                Log.d(TAG, "Login task completed. Success: " + task.isSuccessful());
                if (task.isSuccessful()) {
                    Log.d(TAG, "Firebase auth login successful");
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        Log.d(TAG, "Got Firebase user: " + user.getUid());
                        // odmah pozivam success – ne blokiram na profile checku
                        callback.onSuccess(user);
                        // provjeravam i kreiram profil asinhrono u pozadini (non-blocking)
                        ensureUserProfileExistsAsync(user);
                    } else {
                        Log.e(TAG, "Failed to get user after login");
                        callback.onError("Failed to get user after login. Please try again.");
                    }
                } else {
                    Exception exception = task.getException();
                    String error = "Login failed";
                    if (exception != null) {
                        error = exception.getMessage();
                        Log.e(TAG, "Firebase auth login failed", exception);
                        
                        // dajem user-friendly error poruke
                        if (error != null) {
                            if (error.contains("no user record") || error.contains("USER_NOT_FOUND")) {
                                error = "No account found with this email. Please sign up first.";
                            } else if (error.contains("wrong password") || error.contains("INVALID_PASSWORD") || 
                                       error.contains("invalid credential") || error.contains("INVALID_CREDENTIAL")) {
                                error = "Incorrect password. Please try again or reset your password.";
                            } else if (error.contains("badly formatted") || error.contains("INVALID_EMAIL")) {
                                error = "Invalid email address. Please check and try again.";
                            } else if (error.contains("network") || error.contains("NETWORK_ERROR")) {
                                error = "Network error. Please check your internet connection.";
                            } else if (error.contains("too many requests") || error.contains("TOO_MANY_ATTEMPTS")) {
                                error = "Too many login attempts. Please try again later.";
                            } else if (error.contains("disabled") || error.contains("USER_DISABLED")) {
                                error = "This account has been disabled. Please contact support.";
                            } else if (error.contains("expired") || error.contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN")) {
                                error = "Your session has expired. Please try logging in again.";
                            }
                        }
                    } else {
                        Log.e(TAG, "Firebase auth login failed with no exception");
                    }
                    callback.onError(error);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Login failure listener triggered", e);
                String error = e.getMessage();
                if (error == null || error.isEmpty()) {
                    error = "Network error. Please check your connection and try again.";
                } else if (error.contains("invalid credential") || error.contains("INVALID_CREDENTIAL")) {
                    error = "Incorrect email or password. Please check your credentials and try again.";
                }
                callback.onError(error);
            });
    }

    // logoutuje trenutnog usera
    public void logout() {
        removeListeners();
        auth.signOut();
    }

    // provjerava da li je user ulogovan
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    // uzima trenutni user ID
    @Nullable
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // uzima trenutni user email
    @Nullable
    public String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    // ==================== USER PROFILE ====================

    // kreira user profil u databaseu poslije signupa
    private void createUserProfile(FirebaseUser firebaseUser, BuddyCallback callback) {
        String odorId = firebaseUser.getUid();
        String email = firebaseUser.getEmail();

        if (odorId == null || email == null) {
            Log.e(TAG, "Invalid user data - odorId or email is null");
            callback.onError("Invalid user data");
            return;
        }

        BuddyUser user = new BuddyUser(odorId, email);

        Log.d(TAG, "Creating user profile for: " + email + " with code: " + user.buddyCode);
        Log.d(TAG, "Database reference: " + (database != null ? "valid" : "null"));

        if (database == null) {
            Log.e(TAG, "Database is null, cannot create profile");
            callback.onError("Database not available");
            return;
        }

        // čuvam user podatke sa agresivnom retry logikom
        try {
            Log.d(TAG, "Saving user profile and buddy code index...");
            Log.d(TAG, "User ID: " + odorId + ", Code: " + user.buddyCode);
            
            // čuvam i user profil i code index u jednoj atomic operaciji
            Map<String, Object> updates = new HashMap<>();
            updates.put(USERS_PATH + "/" + odorId, user.toMap());
            updates.put(BUDDY_CODES_PATH + "/" + user.buddyCode, odorId);
            
            database.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile and buddy code index saved successfully");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save both together, trying separately...", e);
                    
                    // retry: čuvam user profil prvo
                    database.child(USERS_PATH).child(odorId).setValue(user.toMap())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "User profile saved, now saving code index...");
                            // Then save code index
                            database.child(BUDDY_CODES_PATH).child(user.buddyCode).setValue(odorId)
                                .addOnSuccessListener(aVoid2 -> {
                                    Log.d(TAG, "Code index saved successfully");
                                    callback.onSuccess();
                                })
                                .addOnFailureListener(e2 -> {
                                    Log.w(TAG, "Code index save failed, but profile exists", e2);
                                    // i dalje uspijevam – code može da se nađe preko user searcha
                                    callback.onSuccess();
                                });
                        })
                        .addOnFailureListener(e2 -> {
                            Log.e(TAG, "Failed to save user profile", e2);
                            // posljednji pokušaj: pokušavam sa samo essential poljima
                            Map<String, Object> minimalUser = new HashMap<>();
                            minimalUser.put("odorId", odorId);
                            minimalUser.put("email", email);
                            minimalUser.put("buddyCode", user.buddyCode);
                            
                            database.child(USERS_PATH).child(odorId).setValue(minimalUser)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Minimal profile saved");
                                    callback.onSuccess();
                                })
                                .addOnFailureListener(e3 -> {
                                    Log.e(TAG, "All save attempts failed", e3);
                                    String errorMsg = e3.getMessage();
                                    if (errorMsg == null || errorMsg.isEmpty()) {
                                        errorMsg = "Failed to create profile. Please check Firebase database permissions.";
                                    }
                                    callback.onError(errorMsg);
                                });
                        });
                });
            
            // stara metoda zadržana kao referenca ali zamijenjena gore
            /*
            database.child(USERS_PATH).child(odorId).setValue(user.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile saved successfully to: " + USERS_PATH + "/" + odorId);
                    // također čuvam buddy code index za brzu lookup
                    database.child(BUDDY_CODES_PATH).child(user.buddyCode).setValue(odorId)
                        .addOnSuccessListener(aVoid2 -> {
                            Log.d(TAG, "Buddy code index saved successfully");
                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save buddy code index", e);
                            // i dalje pozivam success – user profil je kreiran, code index je opciono
                            // code može da se regeneriše iz userId ako treba
                            Log.w(TAG, "Continuing despite code index save failure");
                            callback.onSuccess();
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user profile", e);
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "Failed to create user profile. This might be due to database permissions. The account was created but profile setup failed.";
                    } else if (errorMsg.contains("permission") || errorMsg.contains("PERMISSION_DENIED")) {
                        errorMsg = "Database permission denied. Please check Firebase console settings.";
                    } else if (errorMsg.contains("network")) {
                        errorMsg = "Network error while saving profile. Please check your connection.";
                    }
                    callback.onError(errorMsg);
                })
                .addOnCompleteListener(task -> {
                    Log.d(TAG, "Profile creation task completed. Success: " + task.isSuccessful());
                });
            */
        } catch (Exception e) {
            Log.e(TAG, "Exception while creating user profile", e);
            callback.onError("Exception: " + e.getMessage());
        }
    }

    // osiguravam da user profil postoji u databaseu, kreiram ako nedostaje (non-blocking, async)
    private void ensureUserProfileExistsAsync(FirebaseUser firebaseUser) {
        String odorId = firebaseUser.getUid();
        
        // provjeravam da li profil postoji asinhrono (ne blokiram login)
        database.child(USERS_PATH).child(odorId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        // profil ne postoji, kreiram ga u pozadini
                        Log.d(TAG, "User profile not found, creating in background...");
                        createUserProfile(firebaseUser, new BuddyCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "User profile created successfully in background");
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Failed to create profile in background: " + error);
                                // ne-kritično – profil može da se kreira kasnije kad pristupim buddy featureima
                            }
                        });
                    } else {
                        Log.d(TAG, "User profile already exists");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error checking user profile in background", error.toException());
                    // ne-kritično – samo logujem error
                }
            });
    }

    // uzima trenutnog userove podatke
    public void getCurrentUserData(UserDataCallback callback) {
        String odorId = getCurrentUserId();
        if (odorId == null) {
            callback.onError("Not logged in");
            return;
        }

        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("User not found");
            return;
        }

        // prvo, pokušavam da uzmem postojeći profil iz databasea
        database.child(USERS_PATH).child(odorId).addListenerForSingleValueEvent(
            new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    BuddyUser user = snapshot.getValue(BuddyUser.class);
                    if (user != null && user.buddyCode != null && !user.buddyCode.isEmpty()) {
                        // postojeći profil nađen sa buddy kodom
                        Log.d(TAG, "Found existing user profile with code: " + user.buddyCode);
                        callback.onUserLoaded(user);
                    } else {
                        // nema profila još u databaseu – kreiram BuddyUser odmah sa generisanim kodom
                        // ovo osigurava da je kod dostupan instantno, čak i ako je database write spor
                        Log.d(TAG, "No profile found, creating BuddyUser on-the-fly");
                        BuddyUser newUser = new BuddyUser(firebaseUser.getUid(), firebaseUser.getEmail());
                        Log.d(TAG, "Generated buddy code: " + newUser.buddyCode);
                        
                        // vraćam odmah sa generisanim kodom
                        callback.onUserLoaded(newUser);
                        
                        // čuvam u database u pozadini (non-blocking)
                        createUserProfile(firebaseUser, new BuddyCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "User profile saved to database successfully");
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Failed to save profile to database (non-critical): " + error);
                                // ne-kritično – user već ima svoj kod prikazan
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error reading user profile, creating on-the-fly", error.toException());
                    // čak i ako database read ne uspije, kreiram usera sa kodom odmah
                    BuddyUser newUser = new BuddyUser(firebaseUser.getUid(), firebaseUser.getEmail());
                    callback.onUserLoaded(newUser);
                    
                    // pokušavam da sačuvam u pozadini
                    createUserProfile(firebaseUser, new BuddyCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "User profile saved after read error");
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Failed to save profile after read error: " + error);
                        }
                    });
                }
            });
    }

    // ==================== BUDDY CONNECTION ====================

    // generiše buddy kod iz user ID (ista logika kao BuddyUser)
    private String generateBuddyCodeFromUserId(String userId) {
        if (userId == null || userId.length() < 8) {
            return "ESCAPE00";
        }
        StringBuilder code = new StringBuilder();
        code.append(userId.substring(0, 2).toUpperCase());
        code.append(userId.substring(userId.length() / 2, userId.length() / 2 + 2).toUpperCase());
        code.append(userId.substring(userId.length() - 4).toUpperCase());
        
        String result = code.toString().replaceAll("[^A-Z0-9]", "X");
        
        if (result.length() < 8) {
            result = result + "00000000".substring(0, 8 - result.length());
        } else if (result.length() > 8) {
            result = result.substring(0, 8);
        }
        
        return result;
    }

    // nalazi usera po buddy kodu – koristi više strategija za maksimalnu pouzdanost
    public void findUserByBuddyCode(String buddyCode, BuddyCodeCallback callback) {
        
        if (buddyCode == null || buddyCode.length() != 8) {
            Log.e(TAG, "Invalid buddy code format: " + buddyCode);
            callback.onError("Invalid buddy code format");
            return;
        }

        String upperCode = buddyCode.toUpperCase();
        Log.d(TAG, "Searching for buddy code: " + upperCode);

        if (database == null) {
            Log.e(TAG, "Database is null!");
            callback.onError("Database not available");
            return;
        }
        

        // strategija 1: pokušavam code index lookup (najbrže)
        // strategija 2: pretražujem sve usere (fallback)
        // strategija 3: pokušavam da reverse-engineerujem iz Firebase Auth usera (posljednji izbor)
        
        Handler lookupTimeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] callbackFired = {false};
        
        Runnable lookupTimeout = () -> {
            if (!callbackFired[0]) {
                callbackFired[0] = true;
                Log.e(TAG, "All lookup strategies timed out");
                // pokušavam još jednom sa user searchom
                searchUsersByCodeFallback(upperCode, new BuddyCodeCallback() {
                    @Override
                    public void onCodeFound(String userId) {
                        if (!callbackFired[0]) {
                            callbackFired[0] = true;
                            callback.onCodeFound(userId);
                        }
                    }

                    @Override
                    public void onCodeNotFound() {
                        if (!callbackFired[0]) {
                            callbackFired[0] = true;
                            callback.onCodeNotFound();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (!callbackFired[0]) {
                            callbackFired[0] = true;
                            callback.onError("Code not found. Make sure the code is correct and the user has signed up.");
                        }
                    }
                }, lookupTimeoutHandler);
            }
        };
        lookupTimeoutHandler.postDelayed(lookupTimeout, 12000); // 12 sekundi total timeout

        // strategija 1: provjeravam code index
        DatabaseReference codeRef = database.child(BUDDY_CODES_PATH).child(upperCode);
        codeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (callbackFired[0]) return;
                    
                    Log.d(TAG, "Code index lookup result - exists: " + snapshot.exists());
                    if (snapshot.exists()) {
                        String odorId = snapshot.getValue(String.class);
                        if (odorId != null && !odorId.isEmpty()) {
                            lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                            callbackFired[0] = true;
                            Log.d(TAG, "Found buddy user ID via index: " + odorId);
                            callback.onCodeFound(odorId);
                            return;
                        }
                    }
                    
                    // strategija 2: pretražujem sve usere (pokrećem odmah paralelno)
                    Log.d(TAG, "Code not in index, searching all users...");
                    searchUsersByCodeFallback(upperCode, new BuddyCodeCallback() {
                        @Override
                        public void onCodeFound(String userId) {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onCodeFound(userId);
                            }
                        }

                        @Override
                        public void onCodeNotFound() {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onCodeNotFound();
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onError(error);
                            }
                        }
                    }, lookupTimeoutHandler);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (callbackFired[0]) return;
                    
                    // ako je permission denied, dajem korisnu error poruku
                    if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                        Log.e(TAG, "PERMISSION_DENIED: Firebase Database rules are blocking access. Please check Firebase Console -> Realtime Database -> Rules");
                    }
                    
                    Log.e(TAG, "Code index lookup cancelled, trying user search", error.toException());
                    // pokušavam user search kao fallback
                    searchUsersByCodeFallback(upperCode, new BuddyCodeCallback() {
                        @Override
                        public void onCodeFound(String userId) {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onCodeFound(userId);
                            }
                        }

                        @Override
                        public void onCodeNotFound() {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onCodeNotFound();
                            }
                        }

                        @Override
                        public void onError(String errorMsg) {
                            if (!callbackFired[0]) {
                                lookupTimeoutHandler.removeCallbacks(lookupTimeout);
                                callbackFired[0] = true;
                                callback.onError(errorMsg);
                            }
                        }
                    }, lookupTimeoutHandler);
                }
            });
    }

    // povezuje se sa buddyjem koristeci njihov kod (optimizovano za brzinu sa fallbackom)
    public void connectToBuddy(String buddyCode, BuddyCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        // uzimam trenutni user email odmah iz Firebase Auth (brzo, bez database poziva)
        String currentUserEmail = getCurrentUserEmail();
        if (currentUserEmail == null || currentUserEmail.isEmpty()) {
            callback.onError("Unable to get your email. Please try again.");
            return;
        }

        Log.d(TAG, "Connecting to buddy code: " + buddyCode);

        // dodajem agresivni timeout - ako database ne odgovara, koristim fallback
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] callbackFired = {false};
        
        Runnable timeoutRunnable = () -> {
            if (!callbackFired[0]) {
                callbackFired[0] = true;
                Log.e(TAG, "Connection timeout - database unresponsive. This usually means:");
                Log.e(TAG, "1. Firebase Realtime Database is not enabled in Firebase Console");
                Log.e(TAG, "2. Database rules are blocking access");
                Log.e(TAG, "3. Check Firebase Console -> Realtime Database -> Rules");
                // posljednji izbor: pokušavam da se povezem direktno pokušavanjem da pišem u database
                // ovo radi čak i ako su read operacije blokirane
                attemptDirectConnection(buddyCode, currentUserId, currentUserEmail, callback);
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 8000); // 8 sekundi timeout

        findUserByBuddyCode(buddyCode, new BuddyCodeCallback() {
            @Override
            public void onCodeFound(String buddyUserId) {
                if (callbackFired[0]) return;
                callbackFired[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                Log.d(TAG, "Buddy code found, user ID: " + buddyUserId);
                
                // sprečavam self-connection
                if (buddyUserId.equals(currentUserId)) {
                    Log.w(TAG, "Attempted self-connection");
                    callback.onError("You cannot connect to yourself!");
                    return;
                }

                Log.d(TAG, "Creating connection: " + currentUserId + " <-> " + buddyUserId);

                // kreiram konekciju odmah (optimistic update)
                createBuddyConnectionOptimistic(currentUserId, currentUserEmail, buddyUserId, callback);
            }

            @Override
            public void onCodeNotFound() {
                if (callbackFired[0]) return;
                callbackFired[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                Log.w(TAG, "Buddy code not found: " + buddyCode);
                callback.onError("Buddy code not found. Please check and try again. Make sure the other user has signed up.");
            }

            @Override
            public void onError(String error) {
                if (callbackFired[0]) return;
                callbackFired[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                Log.e(TAG, "Error finding buddy code: " + error);
                // pokusavam direktnu konekciju kao fallback
                attemptDirectConnection(buddyCode, currentUserId, currentUserEmail, callback);
            }
        });
    }

    // pokušava direktnu konekciju kad code lookup ne uspije (posljednji izbor)
    // pokušava da se poveže pisanjem u database direktno
    private void attemptDirectConnection(String buddyCode, String currentUserId, 
                                         String currentUserEmail, BuddyCallback callback) {
        Log.d(TAG, "Attempting direct connection for code: " + buddyCode);
        
        // dodajem timeout za direktnu konekciju search
        Handler directTimeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] directCallbackFired = {false};
        
        Runnable directTimeout = () -> {
            if (!directCallbackFired[0]) {
                directCallbackFired[0] = true;
                Log.e(TAG, "Direct connection search timed out");
                callback.onError("Connection timed out. The database may be slow or unresponsive. Please try again later.");
            }
        };
        directTimeoutHandler.postDelayed(directTimeout, 10000); // 10 sekundi timeout
        
        // strategija: uzimam sve usere i nalazim onog sa matching generisanim kodom
        database.child(USERS_PATH)
            .limitToFirst(100) // limitiram na prvih 100 usera za performanse
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (directCallbackFired[0]) return;
                    directCallbackFired[0] = true;
                    directTimeoutHandler.removeCallbacks(directTimeout);
                    
                    if (!snapshot.exists()) {
                        callback.onError("No users found in database. Please make sure both users have signed up.");
                        return;
                    }
                    
                    Log.d(TAG, "Checking " + snapshot.getChildrenCount() + " users for code match...");
                    
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String userId = userSnapshot.getKey();
                        if (userId == null || userId.equals(currentUserId)) continue;
                        
                        // generišem kod za ovaj user ID
                        String generatedCode = generateBuddyCodeFromUserId(userId);
                        if (generatedCode.equals(buddyCode.toUpperCase())) {
                            Log.d(TAG, "Found matching user by code generation: " + userId);
                            
                            // uzimam user email
                            BuddyUser user = userSnapshot.getValue(BuddyUser.class);
                            String buddyEmail = (user != null && user.email != null) ? 
                                user.email : "buddy@email.com";
                            
                            // povezujem se direktno
                            createBuddyConnectionOptimistic(currentUserId, currentUserEmail, userId, callback);
                            return;
                        }
                    }
                    
                    callback.onError("Code not found. Please verify the code is correct and the user has signed up.");
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (directCallbackFired[0]) return;
                    directCallbackFired[0] = true;
                    directTimeoutHandler.removeCallbacks(directTimeout);
                    Log.e(TAG, "Direct connection search cancelled", error.toException());
                    callback.onError("Database error: " + error.getMessage());
                }
            });
    }

    // fallback: pretražuje usere da nađe onog sa matching buddy kodom
    // također pokušava da generiše kod iz user IDova ako code polje nedostaje
    private void searchUsersByCodeFallback(String buddyCode, BuddyCodeCallback callback, Handler timeoutHandler) {
        Log.d(TAG, "Searching users for code: " + buddyCode);
        
        final Runnable fallbackTimeout = () -> {
            Log.e(TAG, "Fallback user search timed out");
            callback.onError("Search timed out. The code may not exist. Please verify the code is correct.");
        };
        timeoutHandler.postDelayed(fallbackTimeout, 10000);
        
        DatabaseReference usersRef = database.child(USERS_PATH);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    
                    timeoutHandler.removeCallbacks(fallbackTimeout);
                    
                    if (!snapshot.exists()) {
                        Log.w(TAG, "No users found in database");
                        callback.onCodeNotFound();
                        return;
                    }
                    
                    Log.d(TAG, "Searching through " + snapshot.getChildrenCount() + " users...");
                    
                    // strategija 1: provjeravam da li user ima buddyCode polje setovano
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        BuddyUser user = userSnapshot.getValue(BuddyUser.class);
                        if (user != null) {
                            String userCode = null;
                            
                            // provjeravam da li je kod sačuvan u databaseu
                            if (user.buddyCode != null && !user.buddyCode.isEmpty()) {
                                userCode = user.buddyCode.toUpperCase();
                            } else if (user.odorId != null) {
                                // generišem kod iz user ID (deterministic)
                                userCode = generateBuddyCodeFromUserId(user.odorId);
                                Log.d(TAG, "Generated code for user " + user.odorId + ": " + userCode);
                            }
                            
                            if (userCode != null && userCode.equals(buddyCode)) {
                                String userId = user.odorId != null ? user.odorId : userSnapshot.getKey();
                                Log.d(TAG, "Found user with matching code: " + userId);
                                
                                // čuvam kod u index za buduće brze lookupove (non-blocking)
                                if (userId != null) {
                                    database.child(BUDDY_CODES_PATH).child(buddyCode).setValue(userId)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Code index updated for future lookups");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.w(TAG, "Failed to update code index (non-critical)", e);
                                        });
                                }
                                
                                callback.onCodeFound(userId);
                                return;
                            }
                        }
                    }
                    
                    // strategija 2: pokušavam da generišem kodove za sve user IDove i poredim
                    Log.d(TAG, "Code not found in stored fields, trying ID-based generation...");
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String userId = userSnapshot.getKey();
                        if (userId != null && userId.length() >= 8) {
                            String generatedCode = generateBuddyCodeFromUserId(userId);
                            if (generatedCode.equals(buddyCode)) {
                                Log.d(TAG, "Found user by generating code from ID: " + userId);
                                
                                // updateujem user profil sa kodom i čuvam u index
                                Map<String, Object> updates = new HashMap<>();
                                updates.put(USERS_PATH + "/" + userId + "/buddyCode", generatedCode);
                                updates.put(BUDDY_CODES_PATH + "/" + generatedCode, userId);
                                
                                database.updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Updated user profile and code index");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Failed to update profile (non-critical)", e);
                                    });
                                
                                callback.onCodeFound(userId);
                                return;
                            }
                        }
                    }
                    
                    Log.w(TAG, "Code not found after exhaustive search: " + buddyCode);
                    callback.onCodeNotFound();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    timeoutHandler.removeCallbacks(fallbackTimeout);
                    Log.e(TAG, "Error in fallback user search", error.toException());
                    
                    String errorMsg = "Database error";
                    if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                        errorMsg = "Database permission denied. Please check Firebase rules.";
                    } else if (error.getMessage() != null) {
                        errorMsg = error.getMessage();
                    }
                    callback.onError(errorMsg);
                }
            });
    }

    // kreira buddy konekciju optimistički (brzo, non-blocking)
    private void createBuddyConnectionOptimistic(String user1Id, String user1Email, 
                                                  String user2Id, BuddyCallback callback) {
        Log.d(TAG, "Creating connection optimistically for: " + user1Id + " -> " + user2Id);
        
        // koristim placeholder email inicijalno – bit će updateovan kad se buddy podaci učitaju
        String placeholderEmail = "buddy@email.com";
        
        // kreiram konekciju odmah (ne čekam email fetch)
        createBuddyConnection(user1Id, user1Email, user2Id, placeholderEmail, callback);
        
        // uzimam i updateujem buddy email u pozadini (non-blocking)
        database.child(USERS_PATH).child(user2Id).child("email")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String user2Email = snapshot.getValue(String.class);
                    if (user2Email != null && !user2Email.isEmpty() && !user2Email.equals(placeholderEmail)) {
                        // updateujem sa pravim emailom u pozadini
                        Log.d(TAG, "Updating buddy email to: " + user2Email);
                        database.child(USERS_PATH).child(user1Id).child("buddyEmail").setValue(user2Email);
                        database.child(USERS_PATH).child(user2Id).child("buddyEmail").setValue(user1Email);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // ne-kritično – konekcija je već uspostavljena
                    Log.w(TAG, "Could not fetch buddy email, using placeholder");
                }
            });
        
        // Validate in background (non-blocking)
        validateBuddyConnection(user1Id, user2Id);
    }

    // validiram buddy konekciju u pozadini (non-blocking)
    private void validateBuddyConnection(String user1Id, String user2Id) {
        database.child(USERS_PATH).child(user2Id)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    BuddyUser buddyUser = snapshot.getValue(BuddyUser.class);
                    if (buddyUser != null && buddyUser.hasBuddy() && 
                        !buddyUser.buddyId.equals(user1Id)) {
                        Log.w(TAG, "Buddy was already connected to someone else");
                        // mogao bih da diskonektujem ovdje, ali za sada samo logujem
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // ne-kritična validacija
                }
            });
    }

    // kreiram bidirectional buddy konekciju (optimizovano – jedan database write sa timeoutom)
    private void createBuddyConnection(String user1Id, String user1Email,
                                        String user2Id, String user2Email,
                                        BuddyCallback callback) {
        Log.d(TAG, "Creating buddy connection: " + user1Id + " <-> " + user2Id);
        
        // dodajem timeout da osiguram da se callback uvijek pozove
        Handler connectionTimeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] connectionCallbackFired = {false};
        
        Runnable connectionTimeout = () -> {
            if (!connectionCallbackFired[0]) {
                connectionCallbackFired[0] = true;
                Log.e(TAG, "Connection write timed out - database may be unresponsive");
                // i dalje pozivam success – konekcija je možda uspjela, samo sporo
                // user može da provjeri provjeravanjem buddy statusa
                callback.onSuccess();
            }
        };
        connectionTimeoutHandler.postDelayed(connectionTimeout, 8000); // 8 sekundi timeout
        
        Map<String, Object> updates = new HashMap<>();
        
        // updateujem user1ov buddy info
        updates.put(USERS_PATH + "/" + user1Id + "/buddyId", user2Id);
        updates.put(USERS_PATH + "/" + user1Id + "/buddyEmail", user2Email);
        
        // updateujem user2ov buddy info
        updates.put(USERS_PATH + "/" + user2Id + "/buddyId", user1Id);
        updates.put(USERS_PATH + "/" + user2Id + "/buddyEmail", user1Email);

        // jedan atomic database write (brzo)
        Log.d(TAG, "Writing connection to database...");
        database.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                if (connectionCallbackFired[0]) return;
                connectionCallbackFired[0] = true;
                connectionTimeoutHandler.removeCallbacks(connectionTimeout);
                Log.d(TAG, "Buddy connection created successfully in database");
                // osiguravam da se callback pozove na main threadu
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    callback.onSuccess();
                } else {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onSuccess();
                    });
                }
            })
            .addOnFailureListener(e -> {
                if (connectionCallbackFired[0]) return;
                connectionCallbackFired[0] = true;
                connectionTimeoutHandler.removeCallbacks(connectionTimeout);
                Log.e(TAG, "Failed to create buddy connection", e);
                String errorMsg = e.getMessage();
                
                // dajem specifične error poruke na osnovu tipa errora
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Failed to connect. Please try again.";
                } else if (errorMsg.contains("PERMISSION_DENIED") || errorMsg.contains("permission")) {
                    errorMsg = "Database permission denied. Please check Firebase Realtime Database rules in the Firebase Console.";
                } else if (errorMsg.contains("network") || errorMsg.contains("NetworkError")) {
                    errorMsg = "Network error. Please check your internet connection.";
                } else if (errorMsg.contains("UNAVAILABLE") || errorMsg.contains("unavailable")) {
                    errorMsg = "Firebase service unavailable. Please try again in a moment.";
                } else {
                    // pokazujem pravi error za debugging
                    errorMsg = "Connection failed: " + errorMsg;
                }
                
                Log.e(TAG, "Final error message: " + errorMsg);
                final String finalError = errorMsg; // pravim final za lambda
                // osiguravam da se callback pozove na main threadu
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    callback.onError(finalError);
                } else {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError(finalError);
                    });
                }
            });
    }

    // diskonektuje se od trenutnog buddyja
    public void disconnectBuddy(BuddyCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        getCurrentUserData(new UserDataCallback() {
            @Override
            public void onUserLoaded(BuddyUser user) {
                if (!user.hasBuddy()) {
                    callback.onError("You don't have a buddy to disconnect from");
                    return;
                }

                String buddyId = user.buddyId;

                // pokušavam da uklonim konekciju za oba usera prvo
                Map<String, Object> updates = new HashMap<>();
                updates.put(USERS_PATH + "/" + currentUserId + "/buddyId", null);
                updates.put(USERS_PATH + "/" + currentUserId + "/buddyEmail", null);
                updates.put(USERS_PATH + "/" + currentUserId + "/penaltyActive", false);
                updates.put(USERS_PATH + "/" + buddyId + "/buddyId", null);
                updates.put(USERS_PATH + "/" + buddyId + "/buddyEmail", null);
                updates.put(USERS_PATH + "/" + buddyId + "/penaltyActive", false);

                database.updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Buddy disconnected successfully (both users updated)");
                        // čistim sve lokalne penalty flagove jer buddy sistem više nije aktivan
                        clearAllPenaltyDueToNoBuddy();
                        removeListeners();
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        String errorMsg = e.getMessage();
                        Log.w(TAG, "Failed to disconnect both users, trying current user only", e);
                        
                        // ako je permission denied, pokušavam da diskonektujem samo trenutnog usera
                        if (errorMsg != null && (errorMsg.contains("PERMISSION_DENIED") || 
                            errorMsg.contains("permission") || errorMsg.contains("Permission denied"))) {
                            Log.d(TAG, "Permission denied for buddy update, disconnecting current user only");
                            disconnectCurrentUserOnly(currentUserId, callback);
                        } else {
                            // za ostale errore, i dalje pokušavam da diskonektujem lokalno
                            Log.w(TAG, "Other error occurred, disconnecting current user only");
                            disconnectCurrentUserOnly(currentUserId, callback);
                        }
                    });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // diskonektuje samo trenutnog usera (fallback kad ne mogu da updateujem buddyjeve podatke)
    private void disconnectCurrentUserOnly(String currentUserId, BuddyCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(USERS_PATH + "/" + currentUserId + "/buddyId", null);
        updates.put(USERS_PATH + "/" + currentUserId + "/buddyEmail", null);
                updates.put(USERS_PATH + "/" + currentUserId + "/penaltyActive", false);

        database.child(USERS_PATH).child(currentUserId).updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Current user disconnected successfully (buddy side may still show connection)");
                clearAllPenaltyDueToNoBuddy();
                removeListeners();
                callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to disconnect current user", e);
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Failed to disconnect. Please check your internet connection and try again.";
                } else if (errorMsg.contains("PERMISSION_DENIED") || errorMsg.contains("permission")) {
                    errorMsg = "Permission denied. Please check Firebase Realtime Database security rules.";
                }
                callback.onError(errorMsg);
            });
    }

    // ==================== USAGE TRACKING ====================

    // updateuje trenutnog userovog dnevnog usagea
    public void updateDailyUsage(long minutesUsed) {
        String odorId = getCurrentUserId();
        if (odorId == null) return;

        // provjeravam da li treba da resetujem za novi dan
        checkAndResetDailyUsage(odorId, () -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("dailyMinutesUsed", minutesUsed);
            updates.put("lastUpdated", System.currentTimeMillis());

            database.child(USERS_PATH).child(odorId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Usage updated: " + minutesUsed + " min"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update usage", e));
        });
    }

    // updateuje snapshot trenutnog userovog restricted appova + njihovog usagea da buddy može da vidi detailed stats
    public void updateRestrictedAppStats(Map<String, BuddyAppStats> restrictedApps) {
        String odorId = getCurrentUserId();
        if (odorId == null || database == null) return;

        database.child(USERS_PATH)
            .child(odorId)
            .child("restrictedApps")
            .setValue(restrictedApps)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update restricted app stats", e));
    }

    // provjerava da li dnevni usage treba da se resetuje (novi dan)
    private void checkAndResetDailyUsage(String odorId, Runnable onComplete) {
        database.child(USERS_PATH).child(odorId).child("lastResetDate")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long lastReset = snapshot.getValue(Long.class);
                    if (lastReset == null) lastReset = 0L;

                    Calendar lastCal = Calendar.getInstance();
                    lastCal.setTimeInMillis(lastReset);

                    Calendar nowCal = Calendar.getInstance();

                    // provjeravam da li je novi dan
                    if (lastCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR) ||
                        lastCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)) {
                        
                        // resetujem dnevni usage
                        Map<String, Object> resetUpdates = new HashMap<>();
                        resetUpdates.put("dailyMinutesUsed", 0);
                        resetUpdates.put("lastResetDate", System.currentTimeMillis());
                        resetUpdates.put("penaltyActive", false);

                        database.child(USERS_PATH).child(odorId).updateChildren(resetUpdates)
                            .addOnCompleteListener(task -> onComplete.run());
                    } else {
                        onComplete.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onComplete.run();
                }
            });
    }

    // ==================== REAL-TIME LISTENERS ====================

    // pokreće slušanje buddyjevih promjena podataka
    public void startBuddyListener(BuddyUpdateListener listener) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        // prvo, slušam trenutnog usera da uzmem buddy ID
        currentUserRef = database.child(USERS_PATH).child(currentUserId);
        currentUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                BuddyUser currentUser = snapshot.getValue(BuddyUser.class);
                if (currentUser != null) {
                    // syncujem kombinovano penalty stanje iz Firebase u lokalne preferences
                    // VAŽNO: ne prepisujem per-source flagove osim ako Firebase ne kaže da je penalty false
                    boolean firebasePenaltyState = currentUser.penaltyActive;
                    boolean localCombined = prefs.getBoolean(KEY_PENALTY_ACTIVE, false);
                    if (firebasePenaltyState != localCombined) {
                        Log.d(TAG, "Syncing penalty state from Firebase: " + firebasePenaltyState + " (was " + localCombined + ")");
                        prefs.edit().putBoolean(KEY_PENALTY_ACTIVE, firebasePenaltyState).apply();
                    }
                    if (!firebasePenaltyState) {
                        // ako Firebase kaže da nema penaltyja, čistim sve lokalne izvore da izbjegnem stale state
                        prefs.edit()
                            .putBoolean(KEY_PENALTY_ACTIVE_SELF, false)
                            .putBoolean(KEY_PENALTY_ACTIVE_BUDDY, false)
                            .putBoolean(KEY_PENALTY_ACTIVE, false)
                            .apply();
                    } else {
                        // If Firebase says penalty is active but we have no local source, preserve it as "buddy/remote"
                        // so local limit checks can't wipe it.
                        boolean self = prefs.getBoolean(KEY_PENALTY_ACTIVE_SELF, false);
                        boolean buddy = prefs.getBoolean(KEY_PENALTY_ACTIVE_BUDDY, false);
                        if (!self && !buddy) {
                            prefs.edit().putBoolean(KEY_PENALTY_ACTIVE_BUDDY, true).apply();
                        }
                    }
                    
                    boolean hasBuddy = currentUser.hasBuddy();
                    boolean wasConnected = hasActiveBuddyConnection;
                    hasActiveBuddyConnection = hasBuddy;

                    if (hasBuddy) {
                        // pokrećem slušanje buddyja (samo ako se buddyId promijenio)
                        if (currentUser.buddyId != null && !currentUser.buddyId.equals(attachedBuddyId)) {
                            attachBuddyListener(currentUser.buddyId, listener);
                        }
                    } else {
                        // nema povezanog buddyja – djelujem samo na tranziciji iz "connected" u "disconnected"
                        if (wasConnected) {
                            removeBuddyListener();
                            // čistim sve penalty izvore kad uopšte nema buddy konekcije
                            clearAllPenaltyDueToNoBuddy();
                            listener.onBuddyDisconnected();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Current user listener cancelled", error.toException());
                
                // handlujem permission denied
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.e(TAG, "PERMISSION_DENIED: Cannot read current user's data. Check Firebase security rules.");
                }
                
                // uklanjam listenere i notifikujem disconnect na bilo koji error
                removeListeners();
                listener.onBuddyDisconnected();
            }
        };
        currentUserRef.addValueEventListener(currentUserListener);
    }

    // attachujem listener na buddyjeve podatke
    private void attachBuddyListener(String buddyId, BuddyUpdateListener listener) {
        if (buddyId == null || buddyId.isEmpty()) return;

        // ako sam već attachovan na ovog buddyja, ne reattachujem (sprečavam state reset + log spam)
        if (buddyListener != null && buddyRef != null && buddyId.equals(attachedBuddyId)) {
            return;
        }

        // uklanjam postojeći buddy listener ako postoji
        removeBuddyListener();
        
        // resetujem state tracking kad attachujem novi listener
        lastBuddyPenaltyState = false;
        buddyPenaltyStateInitialized = false; // bit će setovano na true na prvoj data change
        attachedBuddyId = buddyId;

        buddyRef = database.child(USERS_PATH).child(buddyId);
        buddyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                BuddyUser buddy = snapshot.getValue(BuddyUser.class);
                if (buddy != null) {
                    listener.onBuddyDataChanged(buddy);
                    
                    // provjeravam za 1-satnu notifikaciju
                    checkOneHourNotification(buddy, listener);
                    
                    // provjeravam za penalty uslov (buddy je prekršio bilo koji app limit)
                    // triggeruje se samo na state tranziciji iz false u true (poslije inicijalizacije)
                    checkBuddyAppLimits(buddy, listener);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Buddy listener cancelled", error.toException());
                
                // handlujem permission denied specifično
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.e(TAG, "PERMISSION_DENIED: Cannot read buddy's data. This usually means:");
                    Log.e(TAG, "1. Firebase Realtime Database security rules are blocking access");
                    Log.e(TAG, "2. Users can only read their own data, not their buddy's data");
                    Log.e(TAG, "3. Check Firebase Console -> Realtime Database -> Rules");
                    Log.e(TAG, "4. Rules should allow authenticated users to read their buddy's data");
                    
                    // notifikujem listener da je buddy diskonektovan zbog permission errora
                    // ovo sprečava da app visi u broken stanju
                    removeBuddyListener();
                    listener.onBuddyDisconnected();
                } else {
                    // za ostale errore, također uklanjam listener i notifikujem disconnect
                    // ovo osigurava da app ne ostane u broken stanju
                    removeBuddyListener();
                    listener.onBuddyDisconnected();
                }
            }
        };
        buddyRef.addValueEventListener(buddyListener);
    }

    // triggeruje penalty za oba usera
    // triggeruje se samo jednom po penalty state promjeni (handluje se u checkBuddyAppLimits)
    private void triggerPenalty(BuddyUpdateListener listener, String reason) {
        // primjenjujem buddy penalty stanje (lokalno + Firebase)
        applyPenaltySilently(reason);

        // biram random restricted app na ovom uređaju da zaključam za trajanje penaltyja
        pickAndStoreRandomLockedApp();

        // notifikujem UI (BuddyActivity / ostale) da mogu da pokažu koji app je zaključan
        listener.onPenaltyTriggered(reason);
    }

    // primjenjuje penalty lokalno + u Firebase bez notifikovanja UI
    // ovo je važno za background servise i za "first load" slučaj gdje je buddyjev penalty
    // već aktivan kad attachujem listener
    private void applyPenaltySilently(@Nullable String reason) {
        // markujem kao buddy-primenjeni penalty da self limit checkovi ne mogu da ga obrišu
        prefs.edit().putBoolean(KEY_PENALTY_ACTIVE_BUDDY, true).apply();
        updateCombinedPenaltyStateInFirebase(reason);
    }

    // biram random enabled restricted app na ovom uređaju i čuvam ga kao "locked" app
    // za trenutni buddy penalty; ako nema pogodnih appova, čistim bilo koji prethodni lock
    private void pickAndStoreRandomLockedApp() {
        try {
            PreferencesManager preferencesManager = new PreferencesManager(context);
            List<AppRestriction> restrictions = preferencesManager.loadAppRestrictions();

            java.util.List<AppRestriction> candidates = new java.util.ArrayList<>();
            for (AppRestriction restriction : restrictions) {
                if (restriction == null) continue;
                if (!restriction.isEnabled()) continue;
                if (restriction.getDailyLimitMinutes() <= 0) continue;
                candidates.add(restriction);
            }

            if (candidates.isEmpty()) {
                // nema eligible appova – čistim bilo koji prethodni locked app
                prefs.edit()
                    .remove(KEY_PENALTY_LOCKED_PACKAGE)
                    .remove(KEY_PENALTY_LOCKED_APP_NAME)
                    .apply();
                Log.d(TAG, "No eligible apps to lock for buddy penalty");
                return;
            }

            AppRestriction chosen = candidates.get(random.nextInt(candidates.size()));
            String pkg = chosen.getPackageName();
            String name = chosen.getAppName();

            prefs.edit()
                .putString(KEY_PENALTY_LOCKED_PACKAGE, pkg)
                .putString(KEY_PENALTY_LOCKED_APP_NAME, name)
                .apply();

            Log.d(TAG, "Buddy penalty locked app selected: " + name + " (" + pkg + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error selecting random locked app for buddy penalty", e);
        }
    }
    
    // updateuje kombinovano penalty stanje (self ILI buddy) lokalno i u Firebase
    private void updateCombinedPenaltyStateInFirebase(@Nullable String reasonForLog) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        boolean self = prefs.getBoolean(KEY_PENALTY_ACTIVE_SELF, false);
        boolean buddy = prefs.getBoolean(KEY_PENALTY_ACTIVE_BUDDY, false);
        boolean combined = self || buddy;

        boolean previousCombined = prefs.getBoolean(KEY_PENALTY_ACTIVE, false);

        // čuvam kombinovano lokalno za brzo čitanje iz AppMonitoringService
        prefs.edit().putBoolean(KEY_PENALTY_ACTIVE, combined).apply();

        // izbjegavam spamming Firebase writeove kad se ništa nije promijenilo i nema eksplicitnog razloga
        if (previousCombined == combined && reasonForLog == null) {
            return;
        }

        // pišem kombinovano u Firebase da bi UI ostao syncovan preko uređaja
        Map<String, Object> updates = new HashMap<>();
        updates.put("penaltyActive", combined);
        updates.put("lastUpdated", System.currentTimeMillis());

        database.child(USERS_PATH).child(currentUserId).updateChildren(updates)
            .addOnSuccessListener(aVoid -> Log.d(
                TAG,
                "Penalty status updated in Firebase: " + combined +
                    " (self=" + self + ", buddy=" + buddy + ")" +
                    (reasonForLog != null ? (" - " + reasonForLog) : "")
            ))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update combined penalty status", e));
    }
    
    // setuje penalty aktivan status (poziva AppMonitoringService kad je app limit prekoračen)
    public void setPenaltyActive(boolean active, String reason) {
        // ovaj API se koristi od strane AppMonitoringService za *self* limit exceed tracking
        // ne smije da obriše buddy-primenjeni penalty
        prefs.edit().putBoolean(KEY_PENALTY_ACTIVE_SELF, active).apply();
        updateCombinedPenaltyStateInFirebase(reason);
    }
    
    // provjerava da li je penalty aktivan
    // također provjerava Firebase ako je lokalno stanje false (da uhvatim slučajeve gdje je penalty setovan dok servis nije radio)
    public boolean isPenaltyActive() {
        return prefs.getBoolean(KEY_PENALTY_ACTIVE, false);
    }

    // uzima package name appa trenutno zaključanog zbog buddy penaltyja, ako postoji
    @Nullable
    public String getLockedPenaltyPackage() {
        return prefs.getString(KEY_PENALTY_LOCKED_PACKAGE, null);
    }

    // uzima display name appa trenutno zaključanog zbog buddy penaltyja, ako postoji
    @Nullable
    public String getLockedPenaltyAppName() {
        return prefs.getString(KEY_PENALTY_LOCKED_APP_NAME, null);
    }

    // čisti penalty (pozivam na početku novog dana)
    public void clearPenalty() {
        // čistim sve izvore i syncujem kombinovano stanje
        prefs.edit()
            .putBoolean(KEY_PENALTY_ACTIVE_SELF, false)
            .putBoolean(KEY_PENALTY_ACTIVE_BUDDY, false)
            .putBoolean(KEY_PENALTY_ACTIVE, false)
            .remove(KEY_PENALTY_LOCKED_PACKAGE)
            .remove(KEY_PENALTY_LOCKED_APP_NAME)
            .apply();
        updateCombinedPenaltyStateInFirebase("Cleared penalty");
    }

    private String lastSyncedAppPackage = null;
    private long lastSyncedAppStartTime = 0;
    
    // updateuje trenutni app koji se koristi (poziva AppMonitoringService)
    // eksplicitno isključuje Escape app (com.escape.app) iz trackinga
    public void updateCurrentApp(String packageName, String appName) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;
        
        // eksplicitno isključujem Escape app – tretiram ga kao da nema appa
        if (packageName != null && packageName.equals("com.escape.app")) {
            packageName = null;
            appName = null;
        }
        
        long currentTime = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        
        if (packageName != null && appName != null) {
            // provjeravam da li se app promijenio – ako jeste, resetujem start time
            if (!packageName.equals(lastSyncedAppPackage)) {
                lastSyncedAppPackage = packageName;
                lastSyncedAppStartTime = currentTime;
            }
            
            // uvijek updateujem sa trenutnim start timeom (ako je app i dalje isti, zadržavam originalni start time)
            updates.put("currentAppPackage", packageName);
            updates.put("currentAppName", appName);
            updates.put("currentAppStartTime", lastSyncedAppStartTime);
        } else {
            // nema appa u foregroundu (ili je Escape app filtriran)
            updates.put("currentAppPackage", null);
            updates.put("currentAppName", null);
            updates.put("currentAppStartTime", 0);
            lastSyncedAppPackage = null;
            lastSyncedAppStartTime = 0;
        }
        updates.put("lastUpdated", currentTime);
        
        database.child(USERS_PATH).child(currentUserId).updateChildren(updates)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update current app", e));
    }
    
    // provjerava da li je buddy prekršio bilo koji od svojih app limitova
    // izvodim ovo iz buddy.restrictedApps (trenutni snapshot), tako da se penalty
    // čisti odmah kad buddy više nema appove preko limita
    // (ili briše limite), bez obzira na bilo koji stale penaltyActive flag u Firebase
    private void checkBuddyAppLimits(BuddyUser buddy, BuddyUpdateListener listener) {
        // izvodim trenutno "apps over limit" stanje iz restrictedApps snapshota
        boolean currentPenaltyState = false;
        if (buddy.restrictedApps != null && !buddy.restrictedApps.isEmpty()) {
            for (BuddyAppStats stats : buddy.restrictedApps.values()) {
                if (stats == null) continue;
                if (stats.dailyLimitMinutes <= 0) continue; // 0 = unlimited
                long limitMs = stats.dailyLimitMinutes * 60L * 1000L;
                if (stats.usageTodayMs > limitMs) {
                    currentPenaltyState = true;
                    break;
                }
            }
        }
        
        // na prvom učitavanju, syncujem lokalno penalty stanje sa buddy stanjem bez spamming UI
        // ovo osigurava da se penalty primjenjuje čak i ako je buddy već prekršio limite prije nego što sam attachovao
        if (!buddyPenaltyStateInitialized) {
            // prvi put vidim ovo buddyjevo penalty stanje – zapisujem ga i syncujem tiho
            lastBuddyPenaltyState = currentPenaltyState;
            buddyPenaltyStateInitialized = true;
            Log.d(TAG, "Initialized buddy penalty state tracking (derived from apps over limit): " + currentPenaltyState);
            if (currentPenaltyState) {
                applyPenaltySilently("Buddy has apps over limit on first sync");
            } else {
                // buddy penalty nije aktivan – čistim samo buddy-source lokalno
                // VAŽNO: ne pišem u Firebase na prvom syncu kad je penalty neaktivan (sprečava spam)
                prefs.edit().putBoolean(KEY_PENALTY_ACTIVE_BUDDY, false).apply();
                boolean self = prefs.getBoolean(KEY_PENALTY_ACTIVE_SELF, false);
                prefs.edit().putBoolean(KEY_PENALTY_ACTIVE, self).apply();
            }
            return;
        }
        
        // triggerujem penalty samo kad buddyjevo penalty stanje prelazi iz false u true
        // ovo sprečava spam kad je penalty već aktivan
        if (currentPenaltyState && !lastBuddyPenaltyState) {
            // penalty je upravo postao aktivan (tranzicija iz false u true)
            Log.d(TAG, "Buddy penalty state changed: false -> true, triggering penalty");
            String reason = "Penalty applied, buddy reached one of set limits.";
            triggerPenalty(listener, reason);
        } else if (!currentPenaltyState && lastBuddyPenaltyState) {
            // penalty je upravo postao neaktivan (tranzicija iz true u false)
            Log.d(TAG, "Buddy penalty state changed: true -> false, clearing penalty");
            clearBuddyPenalty();
        }
        // ako se stanje nije promijenilo, ne radim ništa (sprečava spam)
        
        // updateujem posljednje poznato stanje
        lastBuddyPenaltyState = currentPenaltyState;
    }
    
    // čisti penalty lokalno (kad buddyjevo penalty postane false)
    private void clearBuddyPenalty() {
        // čistim samo buddy-source penalty; zadržavam self penalty ako je aktivan
        prefs.edit()
            .putBoolean(KEY_PENALTY_ACTIVE_BUDDY, false)
            .remove(KEY_PENALTY_LOCKED_PACKAGE)
            .remove(KEY_PENALTY_LOCKED_APP_NAME)
            .apply();
        updateCombinedPenaltyStateInFirebase("Buddy penalty became inactive");
    }

    // čisti sve penalty izvore kad uopšte nema buddy konekcije
    // ovo osigurava da buddy penaltyi nikad ne ostanu poslije disconnecta
    private void clearAllPenaltyDueToNoBuddy() {
        prefs.edit()
            .putBoolean(KEY_PENALTY_ACTIVE_SELF, false)
            .putBoolean(KEY_PENALTY_ACTIVE_BUDDY, false)
            .putBoolean(KEY_PENALTY_ACTIVE, false)
            .remove(KEY_PENALTY_LOCKED_PACKAGE)
            .remove(KEY_PENALTY_LOCKED_APP_NAME)
            .apply();
        String currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("penaltyActive", false);
            updates.put("lastUpdated", System.currentTimeMillis());
            database.child(USERS_PATH).child(currentUserId).updateChildren(updates);
        }
    }
    
    // provjerava i notifikuje ako buddy koristi app preko 1 sata
    private void checkOneHourNotification(BuddyUser buddy, BuddyUpdateListener listener) {
        if (buddy.currentAppPackage == null || buddy.currentAppName == null) return;
        if (buddy.currentAppStartTime <= 0) return;
        
        long durationMinutes = buddy.getCurrentAppDurationMinutes();
        
        // provjeravam da li je upravo prešao 1-satni prag
        String key = "one_hour_notified_" + buddy.odorId + "_" + buddy.currentAppPackage;
        boolean alreadyNotified = prefs.getBoolean(key, false);
        
        if (durationMinutes >= 60 && !alreadyNotified) {
            String buddyName = buddy.displayName != null ? buddy.displayName : 
                              (buddy.email != null ? buddy.email.split("@")[0] : "Your buddy");
            String message = "Hey! " + buddyName + " is using " + buddy.currentAppName + 
                           " for over 1 hour, you should invite him to hang out!";
            
            // markujem kao notifikovano
            prefs.edit().putBoolean(key, true).apply();
            
            // notifikujem listener (koji će pokazati notifikaciju u BuddyActivity)
            listener.onOneHourNotification(buddy.currentAppName, message);
        } else if (durationMinutes < 60) {
            // resetujem notification flag ako je ispod 1 sata
            prefs.edit().putBoolean(key, false).apply();
        }
    }
    
    // uklanja buddy listener
    private void removeBuddyListener() {
        if (buddyRef != null && buddyListener != null) {
            buddyRef.removeEventListener(buddyListener);
            buddyListener = null;
            buddyRef = null;
            attachedBuddyId = null;
        }
    }

    // uklanja sve listenere
    public void removeListeners() {
        removeBuddyListener();
        if (currentUserRef != null && currentUserListener != null) {
            currentUserRef.removeEventListener(currentUserListener);
            currentUserListener = null;
            currentUserRef = null;
        }
    }

    // ==================== UTILITY ====================

    // uzima buddyjeve podatke jednom (ne real-time)
    public void getBuddyData(String buddyId, UserDataCallback callback) {
        database.child(USERS_PATH).child(buddyId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    BuddyUser buddy = snapshot.getValue(BuddyUser.class);
                    if (buddy != null) {
                        callback.onUserLoaded(buddy);
                    } else {
                        callback.onError("Buddy not found");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onError(error.getMessage());
                }
            });
    }
}

