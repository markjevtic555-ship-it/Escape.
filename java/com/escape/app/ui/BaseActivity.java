package com.escape.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.escape.app.R;
import com.escape.app.utils.LocaleHelper;

// base aktivnost koja primjenjuje locale postavke na sve aktivnosti
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ako koristim srpski latinicu, ručno updateujem sve TextViewove poslije inflacije
        // ovo je workaround jer Android LayoutInflater ne koristi naš Resources wrapper
        if (LocaleHelper.getLanguage(this).equals("sr-Latn")) {
            // postujem da osiguram da je layout inflatovan
            getWindow().getDecorView().post(() -> {
                updateTextViewsForLatin(getWindow().getDecorView());
            });
        }
    }
    
    // rekurzivno updateujem sve TextViewove da koriste latiničke stringove
    // ovo je workaround jer Android LayoutInflater zaobiđe naš Resources wrapper
    private void updateTextViewsForLatin(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (text != null && text.length() > 0) {
                String textStr = text.toString();
                
                // koristim application context koji bi trebao imati wrapped Resources
                // Activity context možda nema wrapped Resources
                Context appContext = getApplicationContext();
                String latinText = LocaleHelper.getLatinStringForCyrillic(appContext, textStr);
                if (latinText != null && !latinText.equals(textStr)) {
                    textView.setText(latinText);
                }
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                updateTextViewsForLatin(group.getChildAt(i));
            }
        }
    }
    
    // updateujem dialog viewove za latinički prijevod
    // pozivam ovo poslije inflacije dialog viewa ako je latin aktivan
    protected void updateDialogViewsForLatin(View dialogView) {
        if (LocaleHelper.getLanguage(this).equals("sr-Latn")) {
            updateTextViewsForLatin(dialogView);
        }
    }
    
}

