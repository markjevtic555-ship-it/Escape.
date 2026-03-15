package com.escape.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.utils.FirebaseBuddyManager;
import com.escape.app.utils.LocaleHelper;
import com.escape.app.utils.PreferencesManager;

// aktivnost za konfigurisanje postavki aplikacije
public class SettingsActivity extends BaseActivity {

    private Button logoutButton;
    private PreferencesManager preferencesManager;
    private FirebaseBuddyManager buddyManager;
    private TextView languageDisplay;
    private LinearLayout languageSelector;
    private LinearLayout languageEnglishOption;
    private LinearLayout languageSerbianOption;
    private LinearLayout languageSerbianLatinOption;
    private TextView languageEnglishCheck;
    private TextView languageSerbianCheck;
    private TextView languageSerbianLatinCheck;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initializeUtilities();
        initializeViews();
        setupLanguageSwitcher();
    }

    // inicijalizujem utility klase
    private void initializeUtilities() {
        preferencesManager = new PreferencesManager(this);
        buddyManager = new FirebaseBuddyManager(this);
    }

    // inicijalizujem UI komponente
    private void initializeViews() {
        logoutButton = findViewById(R.id.logoutButton);
        languageDisplay = findViewById(R.id.languageDisplay);
        languageSelector = findViewById(R.id.languageSelector);
        languageEnglishOption = findViewById(R.id.languageEnglishOption);
        languageSerbianOption = findViewById(R.id.languageSerbianOption);
        languageSerbianLatinOption = findViewById(R.id.languageSerbianLatinOption);
        languageEnglishCheck = findViewById(R.id.languageEnglishCheck);
        languageSerbianCheck = findViewById(R.id.languageSerbianCheck);
        languageSerbianLatinCheck = findViewById(R.id.languageSerbianLatinCheck);

        logoutButton.setOnClickListener(v -> performLogout());

        // language display container klik da toggleujem selector
        View languageDisplayContainer = findViewById(R.id.languageDisplayContainer);
        if (languageDisplayContainer != null) {
            languageDisplayContainer.setOnClickListener(v -> {
                boolean isVisible = languageSelector.getVisibility() == View.VISIBLE;
                languageSelector.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            });
        }

        // postavljam naslov
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    // izvršavam logout iz Buddy System naloga i vraćam se na login ekran
    private void performLogout() {
        // pokušavam logout samo ako buddy sistem ima autentifikovanog korisnika
        if (buddyManager != null && buddyManager.isLoggedIn()) {
            buddyManager.logout();
        }

        // navigiram na login ekran i čistim back stack da korisnik ne može nazad
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    // postavljam language switcher
    private void setupLanguageSwitcher() {
        String currentLanguage = preferencesManager.getAppLanguage();
        updateLanguageDisplay(currentLanguage);

        languageEnglishOption.setOnClickListener(v -> {
            if (!currentLanguage.equals("en")) {
                changeLanguage("en");
            }
        });

        languageSerbianOption.setOnClickListener(v -> {
            if (!currentLanguage.equals("sr")) {
                changeLanguage("sr");
            }
        });

        languageSerbianLatinOption.setOnClickListener(v -> {
            if (!currentLanguage.equals("sr-Latn")) {
                changeLanguage("sr-Latn");
            }
        });
    }

    /**
     * Update language display
     */
    private void updateLanguageDisplay(String languageCode) {
        languageEnglishCheck.setVisibility(View.GONE);
        languageSerbianCheck.setVisibility(View.GONE);
        languageSerbianLatinCheck.setVisibility(View.GONE);

        if (languageCode.equals("sr-Latn")) {
            languageDisplay.setText(getString(R.string.language_serbian_latin));
            languageSerbianLatinCheck.setVisibility(View.VISIBLE);
        } else if (languageCode.equals("sr")) {
            languageDisplay.setText(getString(R.string.language_serbian));
            languageSerbianCheck.setVisibility(View.VISIBLE);
        } else {
            languageDisplay.setText(getString(R.string.language_english));
            languageEnglishCheck.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Change app language
     */
    private void changeLanguage(String languageCode) {
        preferencesManager.setAppLanguage(languageCode);
        
        // ponovo kreiram aktivnost da primenim novi jezik
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}




