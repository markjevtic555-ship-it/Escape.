package com.escape.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Context;

import com.escape.app.R;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.SudokuGenerator;
import com.escape.app.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;

// sudoku igrica (stevoooooo)
public class SudokuGameActivity extends BaseActivity {
    private static final int SIZE = 9;
    private static final int BONUS_MINUTES = 10;
    private static final int DIFFICULTY = 35; // broj ćelija za uklanjanje
    private static final int MAX_MISTAKES = 3;
    private static final int MAX_HINTS = 3;
    
    private GridLayout sudokuGrid;
    private TextView[][] cells;
    private int[][] puzzle;
    private int[][] userGrid;
    private int[][] originalPuzzle;
    private int[][] solution;
    
    private int selectedRow = -1;
    private int selectedCol = -1;
    
    private int mistakes = 0;
    private int hintsRemaining = MAX_HINTS;
    private boolean gameOver = false;
    
    private TextView mistakesText;
    private TextView hintsText;
    private LinearLayout gameOverOverlay;
    private MaterialButton btnTryAgain;
    private MaterialButton btnHint;
    
    private String blockedPackageName;
    private PreferencesManager preferencesManager;
    private SudokuGenerator generator;
    private Handler handler;
    
    // boje koje se poklapaju sa temom aplikacije
    private static final int COLOR_CELL_DARK = 0xFF14106E;
    private static final int COLOR_CELL_LIGHT = 0xFF1A1A3A;     // malo svjetlije za kontrast
    private static final int COLOR_CELL_SELECTED = 0xFF2A2A5A;  // tamnije plavo za selekciju
    private static final int COLOR_CELL_HIGHLIGHTED = 0xFF1E1E4A; // suptilni highlight
    private static final int COLOR_CELL_HINT = 0xFF1B4332;      // zelena nijansa za hintove
    private static final int COLOR_TEXT_ORIGINAL = 0xFFFFFFFF;  // bijelo za originalne brojeve
    private static final int COLOR_TEXT_USER_CORRECT = 0xFF4CAF50; // zeleno za tačno
    private static final int COLOR_TEXT_USER_INCORRECT = 0xFFF44336; // crveno za netačno
    private static final int COLOR_TEXT_HINT = 0xFF81C784;      // svijetlo zeleno za hintove
    private static final int COLOR_SELECTED_BORDER = 0xFF4FC3F7;
    
    // pratim koje ćelije su popunjene hintovima
    private boolean[][] hintedCells;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sudoku_game);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        preferencesManager = new PreferencesManager(this);
        blockedPackageName = getIntent().getStringExtra("blocked_package");
        handler = new Handler(Looper.getMainLooper());
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        generator = new SudokuGenerator();
        cells = new TextView[SIZE][SIZE];
        hintedCells = new boolean[SIZE][SIZE];
        
        initializeViews();
        initializeGrid();
        generateNewPuzzle();
        setupButtons();
        updateStatusDisplay();
    }
    
    private void initializeViews() {
        mistakesText = findViewById(R.id.mistakesText);
        hintsText = findViewById(R.id.hintsText);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        btnTryAgain = findViewById(R.id.btnTryAgain);
        btnHint = findViewById(R.id.btnHint);
        
        btnTryAgain.setOnClickListener(v -> restartGame());
    }
    
    private void initializeGrid() {
        sudokuGrid = findViewById(R.id.sudokuGrid);
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = 12;
        int cellMargin = 2;
        int subgridMargin = 4;
        
        int totalMargins = (cellMargin * 2 * SIZE) + (subgridMargin * 4);
        int availableWidth = (int) (screenWidth * 0.92) - gridPadding - totalMargins;
        int cellSize = availableWidth / SIZE;
        
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                TextView cell = new TextView(this);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(20);
                cell.setTypeface(null, Typeface.BOLD);
                
                // checkerboard pattern po 3x3 subgridu koristeći drawableove
                boolean isLightCell = ((i / 3) + (j / 3)) % 2 == 0;
                cell.setBackgroundResource(isLightCell ? R.drawable.sudoku_cell_background : R.drawable.sudoku_cell_dark);
                
                int leftMargin = (j % 3 == 0 && j > 0) ? subgridMargin : cellMargin;
                int topMargin = (i % 3 == 0 && i > 0) ? subgridMargin : cellMargin;
                int rightMargin = cellMargin;
                int bottomMargin = cellMargin;
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
                params.rowSpec = GridLayout.spec(i);
                params.columnSpec = GridLayout.spec(j);
                cell.setLayoutParams(params);
                
                final int row = i;
                final int col = j;
                cell.setOnClickListener(v -> selectCell(row, col));
                
                cells[i][j] = cell;
                sudokuGrid.addView(cell);
            }
        }
    }
    
    private void generateNewPuzzle() {
        puzzle = generator.generatePuzzle(DIFFICULTY);
        solution = generator.getSolution();
        userGrid = new int[SIZE][SIZE];
        originalPuzzle = new int[SIZE][SIZE];
        hintedCells = new boolean[SIZE][SIZE];
        
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(puzzle[i], 0, userGrid[i], 0, SIZE);
            System.arraycopy(puzzle[i], 0, originalPuzzle[i], 0, SIZE);
        }
        
        updateGridDisplay();
    }
    
    private void updateGridDisplay() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                updateCellDisplay(i, j);
            }
        }
    }
    
    private void updateCellDisplay(int row, int col) {
        TextView cell = cells[row][col];
        int value = userGrid[row][col];
        
        if (value == 0) {
            cell.setText("");
        } else {
            cell.setText(String.valueOf(value));
        }
        
        boolean isSelected = (row == selectedRow && col == selectedCol);
        boolean isInSameRowOrCol = (row == selectedRow || col == selectedCol);
        boolean isInSameSubgrid = (selectedRow >= 0 && selectedCol >= 0 &&
                                   (row / 3 == selectedRow / 3) && (col / 3 == selectedCol / 3));
        
        // base drawable iz checkerboard patterna
        boolean isLightCell = ((row / 3) + (col / 3)) % 2 == 0;
        int baseDrawable = isLightCell ? R.drawable.sudoku_cell_background : R.drawable.sudoku_cell_dark;
        
        // određujem background drawable
        if (isSelected) {
            cell.setBackgroundResource(R.drawable.sudoku_cell_selected);
        } else if (hintedCells[row][col]) {
            cell.setBackgroundResource(R.drawable.sudoku_cell_hint);
        } else if (isInSameRowOrCol || isInSameSubgrid) {
            cell.setBackgroundResource(R.drawable.sudoku_cell_highlighted);
        } else {
            cell.setBackgroundResource(baseDrawable);
        }
        
        // određujem text boju
        if (originalPuzzle[row][col] != 0) {
            cell.setTextColor(COLOR_TEXT_ORIGINAL);
            cell.setTypeface(null, Typeface.BOLD);
        } else if (hintedCells[row][col]) {
            cell.setTextColor(COLOR_TEXT_HINT);
            cell.setTypeface(null, Typeface.BOLD);
        } else if (value != 0) {
            if (solution != null && value == solution[row][col]) {
                cell.setTextColor(COLOR_TEXT_USER_CORRECT);
            } else {
                cell.setTextColor(COLOR_TEXT_USER_INCORRECT);
            }
            cell.setTypeface(null, Typeface.BOLD);
        } else {
            cell.setTextColor(COLOR_TEXT_ORIGINAL);
        }
    }
    
    private void selectCell(int row, int col) {
        if (gameOver) return;
        
        if (originalPuzzle[row][col] == 0 && !hintedCells[row][col]) {
            selectedRow = row;
            selectedCol = col;
            updateGridDisplay();
            animateCellSelection(cells[row][col]);
        }
    }
    
    private void animateCellSelection(View cell) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 1f, 1.1f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(150);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }
    
    private void setupButtons() {
        int[] buttonIds = {
            R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6,
            R.id.btn7, R.id.btn8, R.id.btn9
        };
        
        for (int i = 0; i < buttonIds.length; i++) {
            final int number = i + 1;
            View btn = findViewById(buttonIds[i]);
            btn.setOnClickListener(v -> enterNumber(number));
        }
        
        // erase dugme
        findViewById(R.id.btnErase).setOnClickListener(v -> clearCell());
        
        // hint dugme
        btnHint.setOnClickListener(v -> useHint());
        
        // check solution dugme
        findViewById(R.id.btnCheck).setOnClickListener(v -> checkSolution());
        
        // cancel dugme
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }
    
    private void enterNumber(int number) {
        if (gameOver) return;
        if (selectedRow < 0 || selectedCol < 0) return;
        if (originalPuzzle[selectedRow][selectedCol] != 0) return;
        if (hintedCells[selectedRow][selectedCol]) return;
        
        int previousValue = userGrid[selectedRow][selectedCol];
        userGrid[selectedRow][selectedCol] = number;
        
        // provjeravam da li je unos tačan
        if (solution != null && number != solution[selectedRow][selectedCol]) {
            // pogrešan odgovor!
            mistakes++;
            updateStatusDisplay();
            animateWrongAnswer(cells[selectedRow][selectedCol]);
            
            if (mistakes >= MAX_MISTAKES) {
                showGameOver();
                return;
            }
        } else {
            // tačan odgovor – animiram uspjeh
            animateCorrectAnswer(cells[selectedRow][selectedCol]);
        }
        
        updateGridDisplay();
        
        // provjeravam da li je puzzle završen
        if (isPuzzleComplete()) {
            awardBonusTime();
        }
    }
    
    private void animateWrongAnswer(View cell) {
        // shake animacija
        ObjectAnimator shake = ObjectAnimator.ofFloat(cell, "translationX", 0, 10, -10, 10, -10, 5, -5, 0);
        shake.setDuration(300);
        shake.start();
    }
    
    private void animateCorrectAnswer(View cell) {
        // pulse animacija
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 1f, 1.15f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }
    
    private void clearCell() {
        if (gameOver) return;
        if (selectedRow >= 0 && selectedCol >= 0 && 
            originalPuzzle[selectedRow][selectedCol] == 0 &&
            !hintedCells[selectedRow][selectedCol]) {
            userGrid[selectedRow][selectedCol] = 0;
            updateGridDisplay();
        }
    }
    
    private void useHint() {
        if (gameOver) return;
        if (hintsRemaining <= 0) {
            Toast.makeText(this, getString(R.string.no_hints_remaining), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // nalazim praznu ćeliju da popunim hintom
        int[] emptyCell = findRandomEmptyCell();
        if (emptyCell == null) {
            Toast.makeText(this, getString(R.string.no_empty_cells_hint), Toast.LENGTH_SHORT).show();
            return;
        }
        
        int row = emptyCell[0];
        int col = emptyCell[1];
        
        hintsRemaining--;
        hintedCells[row][col] = true;
        userGrid[row][col] = solution[row][col];
        
        updateStatusDisplay();
        updateGridDisplay();
        
        // animiram hint reveal
        animateHintReveal(cells[row][col]);
        
        // provjeravam da li je puzzle završen
        if (isPuzzleComplete()) {
            handler.postDelayed(this::awardBonusTime, 500);
        }
    }
    
    private int[] findRandomEmptyCell() {
        java.util.List<int[]> emptyCells = new java.util.ArrayList<>();
        
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (originalPuzzle[i][j] == 0 && userGrid[i][j] == 0 && !hintedCells[i][j]) {
                    emptyCells.add(new int[]{i, j});
                }
            }
        }
        
        if (emptyCells.isEmpty()) {
            // pokušavam ćelije sa pogrešnim odgovorima
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    if (originalPuzzle[i][j] == 0 && !hintedCells[i][j] && 
                        userGrid[i][j] != solution[i][j]) {
                        emptyCells.add(new int[]{i, j});
                    }
                }
            }
        }
        
        if (emptyCells.isEmpty()) return null;
        
        return emptyCells.get(new java.util.Random().nextInt(emptyCells.size()));
    }
    
    private void animateHintReveal(View cell) {
        cell.setAlpha(0f);
        cell.setScaleX(0.5f);
        cell.setScaleY(0.5f);
        
        ObjectAnimator alpha = ObjectAnimator.ofFloat(cell, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 0.5f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 0.5f, 1.2f, 1f);
        
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(400);
        set.setInterpolator(new OvershootInterpolator(1.5f));
        set.start();
    }
    
    private void updateStatusDisplay() {
        mistakesText.setText(mistakes + "/" + MAX_MISTAKES);
        hintsText.setText(String.valueOf(hintsRemaining));
        
        // updateujem hint dugme stanje
        btnHint.setEnabled(hintsRemaining > 0);
        btnHint.setAlpha(hintsRemaining > 0 ? 1f : 0.5f);
        
        // bojim mistakes tekst na osnovu ozbiljnosti
        if (mistakes == 0) {
            mistakesText.setTextColor(0xFF4CAF50); // zeleno
        } else if (mistakes == 1) {
            mistakesText.setTextColor(0xFFFF9800); // narandžasto
        } else {
            mistakesText.setTextColor(0xFFFF6B6B); // crveno
        }
    }
    
    private boolean isPuzzleComplete() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (userGrid[i][j] != solution[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void checkSolution() {
        if (gameOver) return;
        
        if (isPuzzleComplete()) {
            awardBonusTime();
        } else {
            // highlightujem sve netačne ćelije
            boolean hasErrors = false;
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    if (userGrid[i][j] != 0 && userGrid[i][j] != solution[i][j]) {
                        hasErrors = true;
                        animateWrongAnswer(cells[i][j]);
                    }
                }
            }
            
            if (hasErrors) {
                Toast.makeText(this, getString(R.string.errors_in_solution), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.looking_good_fill_remaining), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showGameOver() {
        gameOver = true;
        gameOverOverlay.setVisibility(View.VISIBLE);
        
        // animiram overlay pojavljivanje
        gameOverOverlay.setAlpha(0f);
        gameOverOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start();
    }
    
    private void restartGame() {
        gameOver = false;
        mistakes = 0;
        hintsRemaining = MAX_HINTS;
        selectedRow = -1;
        selectedCol = -1;
        
        gameOverOverlay.setVisibility(View.GONE);
        
        generateNewPuzzle();
        updateStatusDisplay();
    }
    
    private void awardBonusTime() {
        if (blockedPackageName != null) {
            preferencesManager.grantSudokuBonus(blockedPackageName);
        }
        
        Intent intent = new Intent("com.escape.app.BONUS_TIME_AWARDED");
        intent.putExtra("blocked_package", blockedPackageName);
        intent.putExtra("bonus_millis", BONUS_MINUTES * 60 * 1000L);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Toast.makeText(this, R.string.sudoku_correct, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_ENDED"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
    
    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
