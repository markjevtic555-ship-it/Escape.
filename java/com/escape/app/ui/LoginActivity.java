package com.escape.app.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.utils.FirebaseBuddyManager;
import com.google.firebase.auth.FirebaseUser;

// login aktivnost
public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private LinearLayout loginButton;
    private TextView loginButtonText;
    private ProgressBar loginProgress;
    private TextView errorText;
    private TextView signUpLink;
    private TextView skipButton;

    private FirebaseBuddyManager buddyManager;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        buddyManager = new FirebaseBuddyManager(this);

        // provjeravam da li je već ulogovan
        if (buddyManager.isLoggedIn()) {
            navigateToMain();
            return;
        }

        initializeViews();
        setupClickListeners();
        animateEntrance();
    }

    private void initializeViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        loginButtonText = findViewById(R.id.loginButtonText);
        loginProgress = findViewById(R.id.loginProgress);
        errorText = findViewById(R.id.errorText);
        signUpLink = findViewById(R.id.signUpLink);
        skipButton = findViewById(R.id.skipButton);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> attemptLogin());

        signUpLink.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        skipButton.setOnClickListener(v -> {
            // Continue without account
            navigateToMain();
        });
    }

    private void attemptLogin() {
        if (isLoading) return;

        // čistim prethodne errore
        errorText.setVisibility(View.GONE);

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

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
            showError("Please enter your password");
            shakeView(passwordInput);
            return;
        }

        // prikazujem loading stanje
        setLoading(true);

        // pokušavam login
        buddyManager.login(email, password, new FirebaseBuddyManager.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, 
                        "Welcome back!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(formatFirebaseError(error));
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        loginButtonText.setVisibility(loading ? View.GONE : View.VISIBLE);
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setAlpha(loading ? 0.7f : 1.0f);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
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
        
        if (error.contains("no user record") || error.contains("user not found")) {
            return "No account found with this email";
        } else if (error.contains("password is invalid") || error.contains("wrong password")) {
            return "Incorrect password. Please try again.";
        } else if (error.contains("badly formatted")) {
            return "Please enter a valid email address";
        } else if (error.contains("network")) {
            return "Network error. Please check your connection.";
        } else if (error.contains("too many requests") || error.contains("blocked")) {
            return "Too many attempts. Please try again later.";
        } else if (error.contains("disabled")) {
            return "This account has been disabled.";
        }
        
        return error;
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void animateEntrance() {
        // animiram content
        View scrollView = findViewById(android.R.id.content).getRootView();
        scrollView.setAlpha(0f);
        scrollView.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
}

