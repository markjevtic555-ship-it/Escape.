package com.escape.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.utils.FirebaseBuddyManager;
import com.google.firebase.auth.FirebaseUser;

// sign up aktivnost
public class SignUpActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private LinearLayout signUpButton;
    private TextView signUpButtonText;
    private ProgressBar signUpProgress;
    private TextView errorText;
    private TextView loginLink;
    private View backButton;

    private FirebaseBuddyManager buddyManager;
    private boolean isLoading = false;
    private Handler timeoutHandler;
    private static final long SIGNUP_TIMEOUT_MS = 60000; // 60 seconds (increased for Firebase operations)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        buddyManager = new FirebaseBuddyManager(this);

        initializeViews();
        setupClickListeners();
        animateEntrance();
    }

    private void initializeViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        signUpButton = findViewById(R.id.signUpButton);
        signUpButtonText = findViewById(R.id.signUpButtonText);
        signUpProgress = findViewById(R.id.signUpProgress);
        errorText = findViewById(R.id.errorText);
        loginLink = findViewById(R.id.loginLink);
        backButton = findViewById(R.id.backButton);
    }

    private void setupClickListeners() {
        signUpButton.setOnClickListener(v -> attemptSignUp());

        loginLink.setOnClickListener(v -> {
            finish(); // Go back to login
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
    }

    private void attemptSignUp() {
        if (isLoading) return;

        // čistim prethodne errore
        errorText.setVisibility(View.GONE);

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        // validiram inpute
        if (email.isEmpty()) {
            showError("Please enter your email");
            shakeView(emailInput);
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            shakeView(emailInput);
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter a password");
            shakeView(passwordInput);
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            shakeView(passwordInput);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            shakeView(confirmPasswordInput);
            return;
        }

        // prikazujem loading stanje
        setLoading(true);

        // postavljam timeout handler da sprečim infinite loading
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(() -> {
            if (isLoading) {
                setLoading(false);
                showError("Request timed out. Please check your internet connection and try again.");
            }
        }, SIGNUP_TIMEOUT_MS);

        // pokušavam sign up
        buddyManager.signUp(email, password, new FirebaseBuddyManager.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                runOnUiThread(() -> {
                    // otkazujem timeout
                    if (timeoutHandler != null) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                    }
                    setLoading(false);
                    Toast.makeText(SignUpActivity.this, 
                        "Account created successfully!", Toast.LENGTH_SHORT).show();
                    
                    // navigiram na buddy aktivnost ili main
                    navigateToMain();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // otkazujem timeout
                    if (timeoutHandler != null) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                    }
                    setLoading(false);
                    showError(formatFirebaseError(error));
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        signUpButtonText.setVisibility(loading ? View.GONE : View.VISIBLE);
        signUpProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        signUpButton.setAlpha(loading ? 0.7f : 1.0f);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmPasswordInput.setEnabled(!loading);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        
        // animiram pojavu errora
        errorText.setAlpha(0f);
        errorText.animate()
            .alpha(1f)
            .setDuration(200)
            .start();
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 
            0, 10, -10, 10, -10, 5, -5, 0);
        shake.setDuration(400);
        shake.start();
    }

    private String formatFirebaseError(String error) {
        if (error == null) return "An error occurred";
        
        if (error.contains("email address is already in use")) {
            return "This email is already registered. Try logging in.";
        } else if (error.contains("badly formatted")) {
            return "Please enter a valid email address";
        } else if (error.contains("network")) {
            return "Network error. Please check your connection.";
        } else if (error.contains("weak password")) {
            return "Password is too weak. Please use a stronger password.";
        }
        
        return error;
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void animateEntrance() {
        // animiram glavni ScrollView content
        View scrollView = findViewById(android.R.id.content);
        if (scrollView != null) {
            scrollView.setAlpha(0f);
            scrollView.setTranslationY(50);
            scrollView.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    @Override
    public void onBackPressed() {
        // otkazujem timeout ako još učitava
        if (timeoutHandler != null) {
            timeoutHandler.removeCallbacksAndMessages(null);
        }
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // čistim timeout handler
        if (timeoutHandler != null) {
            timeoutHandler.removeCallbacksAndMessages(null);
        }
    }
}

