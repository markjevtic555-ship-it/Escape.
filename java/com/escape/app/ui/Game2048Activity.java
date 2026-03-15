package com.escape.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Context;

import com.escape.app.R;
import com.escape.app.utils.PreferencesManager;
import com.escape.app.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//  2048 igra sa smooth animacijama(nema na čemu)(hvala za ideju Anja)
public class Game2048Activity extends BaseActivity {
    private static final int SIZE = 4;
    private static final int WIN_VALUE = 256;
    private static final int BONUS_MINUTES = 10;
    private static final String PREFS_NAME = "game_2048_prefs";
    private static final String KEY_BEST_SCORE = "best_score";
    
    private static final int[][] TILE_COLORS = {
        {0xFF1B263B, 0xFF1B263B},  // Empty
        {0xFF3D5A80, 0xFF2D4A70},  // 2 - Steel blue
        {0xFF4A6FA5, 0xFF3A5F95},  // 4 - Lighter steel
        {0xFFE07A5F, 0xFFD06A4F},  // 8 - Terracotta
        {0xFFE85D45, 0xFFD84D35},  // 16 - Coral
        {0xFFE84118, 0xFFD83108},  // 32 - Bright red
        {0xFFF7B731, 0xFFE7A721},  // 64 - Gold
        {0xFFEDC531, 0xFFDDB521},  // 128 - Bright gold
        {0xFFECC850, 0xFFDCB840},  // 256 - Light gold
        {0xFFEDC22E, 0xFFDDB21E},  // 512 - Winning gold
        {0xFF3DD1E7, 0xFF2DC1D7},  // 1024+ - Cyan
    };
    
    private static final int TEXT_COLOR_LIGHT = 0xFFF5F5F5;
    private static final int TEXT_COLOR_DARK = 0xFF1B263B;
    
    private FrameLayout gridContainer;
    private GridLayout gameGrid;
    private FrameLayout gameContainer;
    private TextView[][] cellViews;
    private TextView scoreTextView;
    private TextView bestScoreTextView;
    private TextView movesTextView;
    private TextView statusTextView;
    private TextView finalScoreText;
    private LinearLayout gameOverOverlay;
    private MaterialButton btnPlayAgain;
    
    private int[][] board;
    private int[][] previousBoard;
    private int score = 0;
    private int bestScore = 0;
    private int moves = 0;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private boolean animating = false;
    
    private String blockedPackageName;
    private PreferencesManager preferencesManager;
    private SharedPreferences gamePrefs;
    private Random random;
    private Handler handler;
    private GestureDetector gestureDetector;
    
    private int cellSize;
    private int cellMargin = 6;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_2048);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        preferencesManager = new PreferencesManager(this);
        gamePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        blockedPackageName = getIntent().getStringExtra("blocked_package");
        random = new Random();
        handler = new Handler(Looper.getMainLooper());
        
        bestScore = gamePrefs.getInt(KEY_BEST_SCORE, 0);
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        board = new int[SIZE][SIZE];
        previousBoard = new int[SIZE][SIZE];
        cellViews = new TextView[SIZE][SIZE];
        
        initializeViews();
        setupGestureDetector();
        initializeGrid();
        startNewGame();
    }
    
    private void initializeViews() {
        gridContainer = findViewById(R.id.gridContainer);
        gameGrid = findViewById(R.id.gameGrid);
        gameContainer = findViewById(R.id.gameContainer);
        scoreTextView = findViewById(R.id.scoreTextView);
        bestScoreTextView = findViewById(R.id.bestScoreTextView);
        movesTextView = findViewById(R.id.movesTextView);
        statusTextView = findViewById(R.id.statusTextView);
        finalScoreText = findViewById(R.id.finalScoreText);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        
        bestScoreTextView.setText(String.valueOf(bestScore));
        
        findViewById(R.id.btnNewGame).setOnClickListener(v -> {
            animateButtonPress((View) v);
            startNewGame();
        });
        
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        btnPlayAgain.setOnClickListener(v -> {
            hideGameOverOverlay();
            startNewGame();
        });
    }
    
    private void animateButtonPress(View v) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.95f);
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f, 1f);
        
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);
        scaleDown.setDuration(50);
        
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);
        scaleUp.setDuration(50);
        
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(scaleDown, scaleUp);
        set.start();
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 50;
            private static final int SWIPE_VELOCITY_THRESHOLD = 50;
            
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (animating || gameOver || gameWon) return false;
                if (e1 == null || e2 == null) return false;
                
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            moveRight();
                        } else {
                            moveLeft();
                        }
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            moveDown();
                        } else {
                            moveUp();
                        }
                        return true;
                    }
                }
                return false;
            }
            
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
        
        gameContainer.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }
    
    private void initializeGrid() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = 16;
        int totalMargins = cellMargin * 2 * SIZE;
        int availableWidth = (int) (screenWidth * 0.92) - gridPadding - totalMargins;
        cellSize = availableWidth / SIZE;
        
        gameGrid.removeAllViews();
        
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                TextView cell = new TextView(this);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(28);
                cell.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                cell.setTextColor(TEXT_COLOR_LIGHT);
                
                // kreiram rounded background
                GradientDrawable bg = createTileBackground(0);
                cell.setBackground(bg);
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
                params.rowSpec = GridLayout.spec(i);
                params.columnSpec = GridLayout.spec(j);
                cell.setLayoutParams(params);
                
                cellViews[i][j] = cell;
                gameGrid.addView(cell);
            }
        }
    }
    
    private GradientDrawable createTileBackground(int value) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12f);
        
        int colorIndex = getColorIndex(value);
        int[] colors = TILE_COLORS[colorIndex];
        
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
        drawable.setColors(new int[]{colors[0], colors[1]});
        
        return drawable;
    }
    
    private int getColorIndex(int value) {
        if (value == 0) return 0;
        int index = 0;
        int v = value;
        while (v > 2 && index < TILE_COLORS.length - 2) {
            v /= 2;
            index++;
        }
        return Math.min(index + 1, TILE_COLORS.length - 1);
    }
    
    private void startNewGame() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = 0;
            }
        }
        
        score = 0;
        moves = 0;
        gameOver = false;
        gameWon = false;
        
        gameOverOverlay.setVisibility(View.GONE);
        
        addRandomTile(false);
        addRandomTile(false);
        
        updateDisplay();
        
        // animiram grid entrance
        animateGridEntrance();
    }
    
    private void animateGridEntrance() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                TextView cell = cellViews[i][j];
                cell.setAlpha(0f);
                cell.setScaleX(0.8f);
                cell.setScaleY(0.8f);
                
                int delay = (i * SIZE + j) * 30;
                
                cell.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(delay)
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
            }
        }
    }
    
    private void addRandomTile(boolean animate) {
        List<int[]> emptyCells = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) {
                    emptyCells.add(new int[]{i, j});
                }
            }
        }
        
        if (!emptyCells.isEmpty()) {
            int[] cell = emptyCells.get(random.nextInt(emptyCells.size()));
            board[cell[0]][cell[1]] = random.nextInt(10) < 9 ? 2 : 4;
            
            if (animate) {
                animateNewTile(cell[0], cell[1]);
            }
        }
    }
    
    private void animateNewTile(int row, int col) {
        TextView cell = cellViews[row][col];
        
        // updateujem ćeliju prvo
        updateCellDisplay(row, col);
        
        cell.setScaleX(0f);
        cell.setScaleY(0f);
        cell.setAlpha(0f);
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(cell, "scaleX", 0f, 1.1f, 1f),
            ObjectAnimator.ofFloat(cell, "scaleY", 0f, 1.1f, 1f),
            ObjectAnimator.ofFloat(cell, "alpha", 0f, 1f)
        );
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new OvershootInterpolator(1.5f));
        animatorSet.start();
    }
    
    private void animateMerge(int row, int col) {
        TextView cell = cellViews[row][col];
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 1f, 1.25f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 1f, 1.25f, 1f);
        
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(150);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }
    
    private void animateScoreIncrease(int points) {
        // animiram score tekst
        ValueAnimator animator = ValueAnimator.ofInt(score - points, score);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            scoreTextView.setText(String.valueOf(value));
        });
        animator.start();
        
        // pulse animacija na scoreu
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(scoreTextView, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(scoreTextView, "scaleY", 1f, 1.2f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.start();
    }
    
    private void updateDisplay() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                updateCellDisplay(i, j);
            }
        }
        
        scoreTextView.setText(String.valueOf(score));
        movesTextView.setText(getString(R.string.moves, moves));
        
        if (score > bestScore) {
            bestScore = score;
            bestScoreTextView.setText(String.valueOf(bestScore));
            gamePrefs.edit().putInt(KEY_BEST_SCORE, bestScore).apply();
        }
    }
    
    private void updateCellDisplay(int row, int col) {
        int value = board[row][col];
        TextView cell = cellViews[row][col];
        
        if (value == 0) {
            cell.setText("");
        } else {
            cell.setText(String.valueOf(value));
        }
        
        // updateujem background
        GradientDrawable bg = createTileBackground(value);
        cell.setBackground(bg);
        
        // text boja – tamno za svijetle tileove, svijetlo za tamne tileove
        if (value <= 4) {
            cell.setTextColor(value == 0 ? TEXT_COLOR_LIGHT : TEXT_COLOR_DARK);
        } else {
            cell.setTextColor(TEXT_COLOR_LIGHT);
        }
        
        // prilagođavam text size na osnovu vrijednosti
        if (value >= 1000) {
            cell.setTextSize(20);
        } else if (value >= 100) {
            cell.setTextSize(24);
        } else {
            cell.setTextSize(28);
        }
    }
    
    private void saveBoardState() {
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(board[i], 0, previousBoard[i], 0, SIZE);
        }
    }
    
    private void moveLeft() {
        saveBoardState();
        if (performMove(0, -1)) {
            afterMove();
        }
    }
    
    private void moveRight() {
        saveBoardState();
        if (performMove(0, 1)) {
            afterMove();
        }
    }
    
    private void moveUp() {
        saveBoardState();
        if (performMove(-1, 0)) {
            afterMove();
        }
    }
    
    private void moveDown() {
        saveBoardState();
        if (performMove(1, 0)) {
            afterMove();
        }
    }
    
    private boolean performMove(int rowDir, int colDir) {
        boolean moved = false;
        boolean[][] merged = new boolean[SIZE][SIZE];
        List<int[]> mergedCells = new ArrayList<>();
        int pointsGained = 0;
        
        int startRow = rowDir > 0 ? SIZE - 1 : 0;
        int endRow = rowDir > 0 ? -1 : SIZE;
        int rowStep = rowDir > 0 ? -1 : 1;
        
        int startCol = colDir > 0 ? SIZE - 1 : 0;
        int endCol = colDir > 0 ? -1 : SIZE;
        int colStep = colDir > 0 ? -1 : 1;
        
        for (int i = startRow; i != endRow; i += rowStep) {
            for (int j = startCol; j != endCol; j += colStep) {
                if (board[i][j] != 0) {
                    int newRow = i;
                    int newCol = j;
                    
                    while (true) {
                        int nextRow = newRow + rowDir;
                        int nextCol = newCol + colDir;
                        
                        if (nextRow < 0 || nextRow >= SIZE || nextCol < 0 || nextCol >= SIZE) {
                            break;
                        }
                        
                        if (board[nextRow][nextCol] == 0) {
                            newRow = nextRow;
                            newCol = nextCol;
                        } else if (board[nextRow][nextCol] == board[i][j] && !merged[nextRow][nextCol]) {
                            newRow = nextRow;
                            newCol = nextCol;
                            break;
                        } else {
                            break;
                        }
                    }
                    
                    if (newRow != i || newCol != j) {
                        if (board[newRow][newCol] == board[i][j]) {
                            board[newRow][newCol] *= 2;
                            int points = board[newRow][newCol];
                            pointsGained += points;
                            merged[newRow][newCol] = true;
                            mergedCells.add(new int[]{newRow, newCol});
                            
                            if (board[newRow][newCol] >= WIN_VALUE && !gameWon) {
                                gameWon = true;
                            }
                        } else {
                            board[newRow][newCol] = board[i][j];
                        }
                        board[i][j] = 0;
                        moved = true;
                    }
                }
            }
        }
        
        if (moved) {
            moves++;
            if (pointsGained > 0) {
                score += pointsGained;
                animateScoreIncrease(pointsGained);
            }
        }
        
        // updateujem display odmah
        updateDisplay();
        
        // animiram spojene ćelije
        for (int[] cell : mergedCells) {
            handler.postDelayed(() -> animateMerge(cell[0], cell[1]), 50);
        }
        
        return moved;
    }
    
    private void afterMove() {
        animating = true;
        
        handler.postDelayed(() -> {
            addRandomTile(true);
            animating = false;
            
            if (gameWon) {
                showWin();
            } else if (!canMove()) {
                showGameOver();
            }
        }, 100);
    }
    
    private boolean canMove() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) return true;
            }
        }
        
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int value = board[i][j];
                if (j < SIZE - 1 && board[i][j + 1] == value) return true;
                if (i < SIZE - 1 && board[i + 1][j] == value) return true;
            }
        }
        
        return false;
    }
    
    private void showWin() {
        gameOver = true;
        statusTextView.setText(getString(R.string.you_win));
        statusTextView.setTextColor(0xFFFFD700);
        finalScoreText.setText(getString(R.string.final_score, score));
        showGameOverOverlay();
        awardBonusTime();
    }
    
    private void showGameOver() {
        gameOver = true;
        statusTextView.setText(getString(R.string.game_2048_game_over));
        statusTextView.setTextColor(0xFFFF6B6B);
        finalScoreText.setText(getString(R.string.final_score, score));
        showGameOverOverlay();
    }
    
    private void showGameOverOverlay() {
        gameOverOverlay.setVisibility(View.VISIBLE);
        gameOverOverlay.setAlpha(0f);
        gameOverOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    
    private void hideGameOverOverlay() {
        gameOverOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    gameOverOverlay.setVisibility(View.GONE);
                }
            })
            .start();
    }
    
    private void awardBonusTime() {
        if (blockedPackageName != null) {
            preferencesManager.grantSudokuBonus(blockedPackageName);
        }
        
        Intent intent = new Intent("com.escape.app.BONUS_TIME_AWARDED");
        intent.putExtra("blocked_package", blockedPackageName);
        intent.putExtra("bonus_millis", BONUS_MINUTES * 60 * 1000L);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Toast.makeText(this, R.string.game_2048_bonus_awarded, Toast.LENGTH_LONG).show();
        
        handler.postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 3000);
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
