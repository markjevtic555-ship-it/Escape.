package com.escape.app.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.model.BuddyUser;
import com.escape.app.utils.FirebaseBuddyManager;

import java.util.HashMap;
import java.util.Map;

// jednostavna account stranica koja prikazuje ime i email, sa inline editovanjem display namea
// koristi istu tamnu temu kao MainActivity
public class AccountActivity extends BaseActivity {

    private TextView accountNameTextView;
    private EditText accountNameEditText;
    private TextView emailTextView;
    private TextView buddyCodeTextView;
    private ImageView editNameButton;
    private ImageView saveNameButton;
    private ProgressBar loadingIndicator;

    private FirebaseBuddyManager buddyManager;
    private BuddyUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        buddyManager = new FirebaseBuddyManager(this);
        initializeViews();
        loadAccountData();
    }

    private void initializeViews() {
        accountNameTextView = findViewById(R.id.accountNameTextView);
        accountNameEditText = findViewById(R.id.accountNameEditText);
        emailTextView = findViewById(R.id.emailTextView);
        buddyCodeTextView = findViewById(R.id.buddyCodeTextView);
        editNameButton = findViewById(R.id.editNameButton);
        saveNameButton = findViewById(R.id.saveNameButton);
        loadingIndicator = findViewById(R.id.accountLoadingIndicator);

        accountNameEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        editNameButton.setOnClickListener(v -> enterEditMode());
        saveNameButton.setOnClickListener(v -> saveDisplayName());

        setEditMode(false);
    }

    private void loadAccountData() {
        showLoading(true);
        if (!buddyManager.isLoggedIn()) {
            showLoading(false);
            Toast.makeText(this, "Not logged in to Buddy System", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buddyManager.getCurrentUserData(new FirebaseBuddyManager.UserDataCallback() {
            @Override
            public void onUserLoaded(BuddyUser user) {
                currentUser = user;
                runOnUiThread(() -> {
                    showLoading(false);
                    bindUser(user);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(AccountActivity.this, error, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void bindUser(BuddyUser user) {
        String name = user.displayName != null && !user.displayName.isEmpty()
            ? user.displayName
            : (user.email != null ? user.email : "User");

        accountNameTextView.setText(name);
        accountNameEditText.setText(name);

        if (user.email != null) {
            emailTextView.setText(user.email);
        } else {
            emailTextView.setText(getString(R.string.no_email));
        }

        if (user.buddyCode != null) {
            buddyCodeTextView.setText(getString(R.string.buddy_code_label, user.buddyCode));
        } else {
            buddyCodeTextView.setText("");
        }
    }

    private void enterEditMode() {
        if (currentUser == null) return;
        setEditMode(true);
        accountNameEditText.requestFocus();
        accountNameEditText.setSelection(accountNameEditText.getText().length());
    }

    private void setEditMode(boolean editing) {
        accountNameTextView.setVisibility(editing ? View.GONE : View.VISIBLE);
        accountNameEditText.setVisibility(editing ? View.VISIBLE : View.GONE);
        editNameButton.setVisibility(editing ? View.GONE : View.VISIBLE);
        saveNameButton.setVisibility(editing ? View.VISIBLE : View.GONE);
    }

    private void saveDisplayName() {
        if (currentUser == null) return;

        String newName = accountNameEditText.getText().toString().trim();
        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newName.length() > 40) {
            Toast.makeText(this, "Please choose a shorter name", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        String userId = buddyManager.getCurrentUserId();
        if (userId == null) {
            showLoading(false);
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", newName);
        updates.put("lastUpdated", System.currentTimeMillis());

        buddyManager.getDatabase()
            .child("users")
            .child(userId)
            .updateChildren(updates)
            .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                showLoading(false);
                currentUser.displayName = newName;
                bindUser(currentUser);
                setEditMode(false);
                Toast.makeText(AccountActivity.this, "Name updated", Toast.LENGTH_SHORT).show();
            }))
            .addOnFailureListener(e -> runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(AccountActivity.this, "Failed to update name: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }));
    }

    private void showLoading(boolean loading) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }
}



