package com.escape.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Context;

import com.escape.app.R;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.WordleWordList;
import com.escape.app.utils.LocaleHelper;

// wordle igra - možeš da zaradiš bonus vreme
public class WordleGameActivity extends BaseActivity {
    private static final int WORD_LENGTH = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int BONUS_MINUTES = 10;
    
    private GridLayout wordleGrid;
    private TextView[][] letterCells;
    private LinearLayout keyboardLayout;
    
    private String targetWord;
    private int currentRow = 0;
    private int currentCol = 0;
    private StringBuilder currentGuess;
    private boolean gameOver = false;
    
    private String blockedPackageName;
    private PreferencesManager preferencesManager;
    
    // boje koje se poklapaju sa temom aplikacije
    private static final int CELL_BACKGROUND_DEFAULT = 0xFF1A1A3A;
    private static final int CELL_BORDER_COLOR = 0xFF3A3A5A; // suptilni border
    private static final int CELL_CORRECT = 0xFF538D4E; // meko zeleno za tačnu poziciju
    private static final int CELL_PRESENT = 0xFFB59F3B; // toplo jantarno za pogrešnu poziciju
    private static final int CELL_ABSENT = 0xFF3A3A4A; // prigušeno sivo za nije u riječi
    private static final int TEXT_COLOR = 0xFFFFFFFF; // bijeli tekst
    private static final int KEY_BACKGROUND = 0xFF14106E; // poklapa se sa search bar bojom
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wordle_game);
        
        // držim ekran upaljenim
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        preferencesManager = new PreferencesManager(this);
        blockedPackageName = getIntent().getStringExtra("blocked_package");
        
        // notifikujem servis da Wordle igra počinje
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.WORDLE_GAME_STARTED"));
        
        currentGuess = new StringBuilder();
        letterCells = new TextView[MAX_ATTEMPTS][WORD_LENGTH];
        
        initializeGrid();
        initializeKeyboard();
        setupButtons();
        startNewGame();
    }
    
    private void initializeGrid() {
        wordleGrid = findViewById(R.id.wordleGrid);
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = (int) (screenWidth * 0.14); // svaka ćelija je 14% širine ekrana
        int cellMargin = (int) (screenWidth * 0.01); // 1% margin
        
        for (int row = 0; row < MAX_ATTEMPTS; row++) {
            for (int col = 0; col < WORD_LENGTH; col++) {
                TextView cell = new TextView(this);
                cell.setWidth(cellSize);
                cell.setHeight(cellSize);
                // koristim CENTER gravity za horizontalno i vertikalno centriranje
                cell.setGravity(Gravity.CENTER);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                }
                // računam text size na osnovu veličine ćelije za bolje uklapanje
                // koristim oko 40% veličine ćelije za tekst, ali capujem na razumnu veličinu
                float textSize = Math.min(cellSize * 0.4f, 32);
                cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
                cell.setTypeface(null, Typeface.BOLD);
                cell.setTextColor(TEXT_COLOR);
                // uklanjam sve paddinge za savršeno poravnanje
                cell.setPadding(0, 0, 0, 0);
                // uklanjam font padding da eliminiram extra prostor iznad/ispod teksta
                cell.setIncludeFontPadding(false);
                // postavljam min dimenzije da osiguram da ćelija uzima pun prostor
                cell.setMinHeight(cellSize);
                cell.setMinWidth(cellSize);
                // osiguravam single line i bez ellipsisa
                cell.setSingleLine(true);
                cell.setMaxLines(1);
                cell.setEllipsize(null);
                
                // postavljam default background sa borderom
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(8);
                drawable.setColor(CELL_BACKGROUND_DEFAULT);
                drawable.setStroke(3, CELL_BORDER_COLOR);
                cell.setBackground(drawable);
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
                params.rowSpec = GridLayout.spec(row);
                params.columnSpec = GridLayout.spec(col);
                cell.setLayoutParams(params);
                
                letterCells[row][col] = cell;
                wordleGrid.addView(cell);
            }
        }
    }
    
    private void initializeKeyboard() {
        keyboardLayout = findViewById(R.id.keyboardLayout);
        
        // uzimam keyboard layout na osnovu trenutnog jezika
        String[] rows = WordleWordList.getKeyboardRows(this);
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        
        // padding sa strane da osiguram da se tipke ne odsijeku
        int horizontalPadding = (int) (screenWidth * 0.02); // 2% padding sa svake strane
        int availableWidth = screenWidth - (horizontalPadding * 2);
        
        // skupljam prva i posljednja slova iz svakog reda da premjestim u action red
        StringBuilder actionRowLetters = new StringBuilder();
        
        for (int i = 0; i < rows.length; i++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(horizontalPadding, 0, horizontalPadding, 0);
            
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 4, 0, 4);
            rowLayout.setLayoutParams(rowParams);
            
            String row = rows[i];
            
            // izvlačim prva i posljednja slova za action red
            if (row.length() > 0) {
                char firstLetter = row.charAt(0);
                char lastLetter = row.charAt(row.length() - 1);
                actionRowLetters.append(firstLetter).append(lastLetter);
                
                // uklanjam prva i posljednja slova iz reda
                row = row.substring(1, row.length() - 1);
            }
            
            int keyCount = row.length();
            
            // računam širinu tipke dinamički na osnovu dužine reda da se uklopi ekran
            int keyMargin = 2; // manji margin za bolje uklapanje
            int totalMarginWidth = keyMargin * 2 * keyCount; // margin sa obje strane svake tipke
            int keyWidth = (availableWidth - totalMarginWidth) / keyCount;
            int keyHeight = (int) (screenWidth * 0.12);
            
            // osiguravam minimalnu širinu tipke za čitljivost
            int minKeyWidth = (int) (screenWidth * 0.06);
            if (keyWidth < minKeyWidth) {
                keyWidth = minKeyWidth;
            }
            
            for (char c : row.toCharArray()) {
                Button key = createKeyButton(String.valueOf(c), keyWidth, keyHeight, keyMargin);
                key.setOnClickListener(v -> onLetterPressed(c));
                rowLayout.addView(key);
            }
            
            keyboardLayout.addView(rowLayout);
        }
        
        // dodajem action red sa prvim/posljednjim slovima, Enter i Delete
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        
        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionRowParams.setMargins(0, 8, 0, 0);
        actionRow.setLayoutParams(actionRowParams);
        
        int actionKeyHeight = (int) (screenWidth * 0.12);
        int keyMargin = 2;
        
        // računam širine za action row tipke
        // imam: lijeva slova + Enter + Delete + desna slova
        int letterKeyCount = actionRowLetters.length();
        int actionKeyCount = 2; // Enter i Delete
        int totalKeys = letterKeyCount + actionKeyCount;
        int totalMarginWidth = keyMargin * 2 * totalKeys;
        int letterKeyWidth = (availableWidth - totalMarginWidth) / totalKeys;
        int actionKeyWidth = letterKeyWidth * 2; // Enter i Delete su širi
        
        // dodajem lijeva slova (prvo slovo svakog reda)
        for (int i = 0; i < letterKeyCount; i += 2) {
            char letter = actionRowLetters.charAt(i);
            Button key = createKeyButton(String.valueOf(letter), letterKeyWidth, actionKeyHeight, keyMargin);
            key.setOnClickListener(v -> onLetterPressed(letter));
            actionRow.addView(key);
        }
        
        // dodajem Enter dugme
        Button enterKey = createKeyButton(getString(R.string.wordle_enter), actionKeyWidth, actionKeyHeight, keyMargin);
        enterKey.setTextSize(12);
        enterKey.setOnClickListener(v -> onEnterPressed());
        actionRow.addView(enterKey);
        
        // dodajem Delete dugme
        Button deleteKey = createKeyButton("⌫", actionKeyWidth, actionKeyHeight, keyMargin);
        deleteKey.setTextSize(18);
        deleteKey.setOnClickListener(v -> onDeletePressed());
        actionRow.addView(deleteKey);
        
        // dodajem desna slova (posljednje slovo svakog reda, u obrnutom redoslijedu)
        for (int i = letterKeyCount - 1; i >= 0; i -= 2) {
            char letter = actionRowLetters.charAt(i);
            Button key = createKeyButton(String.valueOf(letter), letterKeyWidth, actionKeyHeight, keyMargin);
            key.setOnClickListener(v -> onLetterPressed(letter));
            actionRow.addView(key);
        }
        
        keyboardLayout.addView(actionRow);
    }
    
    private Button createKeyButton(String text, int width, int height, int margin) {
        Button key = new Button(this);
        key.setText(text);
        key.setTextColor(TEXT_COLOR);
        key.setTextSize(14);
        key.setTypeface(null, Typeface.BOLD);
        key.setAllCaps(false);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(8);
        drawable.setColor(KEY_BACKGROUND);
        key.setBackground(drawable);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(margin, margin, margin, margin);
        key.setLayoutParams(params);
        key.setPadding(0, 0, 0, 0);
        
        return key;
    }
    
    private void setupButtons() {
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }
    
    private void startNewGame() {
        targetWord = WordleWordList.getRandomWord(this);
        currentRow = 0;
        currentCol = 0;
        currentGuess = new StringBuilder();
        gameOver = false;
        
        for (int row = 0; row < MAX_ATTEMPTS; row++) {
            for (int col = 0; col < WORD_LENGTH; col++) {
                letterCells[row][col].setText("");
                setCellBackground(row, col, CELL_BACKGROUND_DEFAULT, CELL_BORDER_COLOR);
            }
        }
    }
    
    private void onLetterPressed(char letter) {
        if (gameOver || currentCol >= WORD_LENGTH) {
            return;
        }
        
        currentGuess.append(letter);
        letterCells[currentRow][currentCol].setText(String.valueOf(letter));
        
        // dodajem suptilni highlight da pokažem da je ćelija popunjena
        setCellBackground(currentRow, currentCol, CELL_BACKGROUND_DEFAULT, 0xFF5A5A7A);
        
        currentCol++;
    }
    
    private void onDeletePressed() {
        if (gameOver || currentCol <= 0) {
            return;
        }
        
        currentCol--;
        currentGuess.deleteCharAt(currentGuess.length() - 1);
        letterCells[currentRow][currentCol].setText("");
        setCellBackground(currentRow, currentCol, CELL_BACKGROUND_DEFAULT, CELL_BORDER_COLOR);
    }
    
    private void onEnterPressed() {
        if (gameOver) {
            return;
        }
        
        if (currentCol < WORD_LENGTH) {
            Toast.makeText(this, R.string.wordle_not_enough_letters, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String guess = currentGuess.toString();
        
        // provjeravam da li je validna riječ (opciono – može se ukloniti za lakšu igru)
        if (!WordleWordList.isValidWord(this, guess)) {
            Toast.makeText(this, R.string.wordle_invalid_word, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // evaluiram guess
        evaluateGuess(guess);
        
        // provjeravam za pobijedu
        if (guess.equals(targetWord)) {
            gameOver = true;
            awardBonusTime();
            return;
        }
        
        // prelazim na sljedeći red ili game over
        currentRow++;
        currentCol = 0;
        currentGuess = new StringBuilder();
        
        if (currentRow >= MAX_ATTEMPTS) {
            gameOver = true;
            Toast.makeText(this, getString(R.string.wordle_game_over, targetWord), Toast.LENGTH_LONG).show();
        }
    }
    
    private void evaluateGuess(String guess) {
        // pratim koja slova u targetu su matchovana
        boolean[] targetMatched = new boolean[WORD_LENGTH];
        boolean[] guessMatched = new boolean[WORD_LENGTH];
        
        // prvi prolaz: nalazim tačna poklapanja (zeleno)
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (guess.charAt(i) == targetWord.charAt(i)) {
                setCellBackground(currentRow, i, CELL_CORRECT, CELL_CORRECT);
                targetMatched[i] = true;
                guessMatched[i] = true;
            }
        }
        
        // drugi prolaz: nalazim prisutna ali na pogrešnoj poziciji (žuto)
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (guessMatched[i]) continue;
            
            char guessChar = guess.charAt(i);
            boolean found = false;
            
            for (int j = 0; j < WORD_LENGTH; j++) {
                if (!targetMatched[j] && targetWord.charAt(j) == guessChar) {
                    found = true;
                    targetMatched[j] = true;
                    break;
                }
            }
            
            if (found) {
                setCellBackground(currentRow, i, CELL_PRESENT, CELL_PRESENT);
            } else {
                setCellBackground(currentRow, i, CELL_ABSENT, CELL_ABSENT);
            }
        }
    }
    
    private void setCellBackground(int row, int col, int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(8);
        drawable.setColor(fillColor);
        drawable.setStroke(3, strokeColor);
        letterCells[row][col].setBackground(drawable);
    }
    
    private void awardBonusTime() {
        if (blockedPackageName != null) {
            preferencesManager.grantSudokuBonus(blockedPackageName);
        }
        
        Intent intent = new Intent("com.escape.app.BONUS_TIME_AWARDED");
        intent.putExtra("blocked_package", blockedPackageName);
        intent.putExtra("bonus_millis", BONUS_MINUTES * 60 * 1000L);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Toast.makeText(this, R.string.wordle_correct, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.WORDLE_GAME_ENDED"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}

