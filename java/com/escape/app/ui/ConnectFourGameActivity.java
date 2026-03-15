package com.escape.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
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

import java.util.Random;

//  Connect Four igra
public class ConnectFourGameActivity extends BaseActivity {
    private static final int ROWS = 6;
    private static final int COLS = 8;
    private static final int BONUS_MINUTES = 10;
    private static final String PREFS_NAME = "connect_four_prefs";
    private static final String KEY_PLAYER_WINS = "player_wins";
    private static final String KEY_AI_WINS = "ai_wins";
    
    private static final int EMPTY = 0;
    private static final int HUMAN = 1;
    private static final int COMPUTER = 2;
    
    private GridLayout gameGrid;
    private FrameLayout gridContainer;
    private View[][] cellViews;
    private int[][] board;
    
    private TextView turnIndicator;
    private View turnDot;
    private LinearLayout turnIndicatorContainer;
    private TextView playerScoreText;
    private TextView aiScoreText;
    private TextView statusTextView;
    private TextView statusSubtext;
    private LinearLayout gameOverOverlay;
    private MaterialButton btnPlayAgain;
    
    private boolean isHumanTurn = true;
    private boolean gameOver = false;
    private boolean animating = false;
    
    private int playerWins = 0;
    private int aiWins = 0;
    
    private int[][] winningCells = null;
    
    private String blockedPackageName;
    private PreferencesManager preferencesManager;
    private SharedPreferences gamePrefs;
    private Random random;
    private Handler handler;
    
    private int cellSize;
    private int cellMargin = 3;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_four_game);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        preferencesManager = new PreferencesManager(this);
        gamePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        blockedPackageName = getIntent().getStringExtra("blocked_package");
        random = new Random();
        handler = new Handler(Looper.getMainLooper());
        
        playerWins = gamePrefs.getInt(KEY_PLAYER_WINS, 0);
        aiWins = gamePrefs.getInt(KEY_AI_WINS, 0);
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            new Intent("com.escape.app.SUDOKU_GAME_STARTED"));
        
        board = new int[ROWS][COLS];
        cellViews = new View[ROWS][COLS];
        
        initializeViews();
        initializeGrid();
        updateScoreDisplay();
        updateTurnIndicator();
        animateGridEntrance();
    }
    
    private void initializeViews() {
        gameGrid = findViewById(R.id.gameGrid);
        gridContainer = findViewById(R.id.gridContainer);
        turnIndicator = findViewById(R.id.turnIndicator);
        turnDot = findViewById(R.id.turnDot);
        turnIndicatorContainer = findViewById(R.id.turnIndicatorContainer);
        playerScoreText = findViewById(R.id.playerScoreText);
        aiScoreText = findViewById(R.id.aiScoreText);
        statusTextView = findViewById(R.id.statusTextView);
        statusSubtext = findViewById(R.id.statusSubtext);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        
        findViewById(R.id.btnNewGame).setOnClickListener(v -> {
            animateButtonPress(v);
            resetGame();
        });
        
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        btnPlayAgain.setOnClickListener(v -> {
            hideGameOverOverlay();
            resetGame();
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
    
    private void initializeGrid() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        // računam activity padding (12dp * 2) + grid container padding (8dp * 2) + margine između ćelija
        int activityPadding = (int) (24 * getResources().getDisplayMetrics().density);
        int gridContainerPadding = (int) (16 * getResources().getDisplayMetrics().density);
        // svaka ćelija ima margin sa obje strane, ali susjedne ćelije dijele margine
        int totalCellMargins = cellMargin * 2 * COLS;
        
        int availableWidth = screenWidth - activityPadding - gridContainerPadding - totalCellMargins;
        cellSize = availableWidth / COLS;
        
        // osiguravam da ćelije nisu previsoke za ekran
        int maxCellHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.40 / ROWS);
        cellSize = Math.min(cellSize, maxCellHeight);
        
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                View cell = new View(this);
                cell.setBackgroundResource(R.drawable.connect_four_cell_empty);
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                // uniformne margine sa svih strana
                params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
                params.rowSpec = GridLayout.spec(i);
                params.columnSpec = GridLayout.spec(j);
                cell.setLayoutParams(params);
                
                final int col = j;
                cell.setOnClickListener(v -> onColumnClicked(col));
                
                cellViews[i][j] = cell;
                gameGrid.addView(cell);
            }
        }
    }
    
    private void animateGridEntrance() {
        gridContainer.setAlpha(0f);
        gridContainer.setScaleX(0.9f);
        gridContainer.setScaleY(0.9f);
        
        gridContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(new OvershootInterpolator(1.1f))
            .start();
        
        // staggered cell pojavljivanje
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                View cell = cellViews[i][j];
                cell.setAlpha(0f);
                cell.setScaleX(0.5f);
                cell.setScaleY(0.5f);
                
                int delay = (i * COLS + j) * 20;
                
                cell.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(delay + 200)
                    .setDuration(150)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
            }
        }
    }
    
    private void onColumnClicked(int col) {
        if (gameOver || !isHumanTurn || animating) {
            return;
        }
        
        int row = findLowestEmptyRow(col);
        if (row == -1) {
            // kolona puna – shake animacija
            animateColumnFull(col);
            return;
        }
        
        animating = true;
        animateDropPiece(row, col, HUMAN, () -> {
            board[row][col] = HUMAN;
            animating = false;
            
            if (checkWin(HUMAN)) {
                gameOver = true;
                playerWins++;
                saveScores();
                updateScoreDisplay();
                animateWinningCells();
                handler.postDelayed(() -> showWin(true), 800);
                return;
            }
            
            if (isBoardFull()) {
                gameOver = true;
                showDraw();
                return;
            }
            
            isHumanTurn = false;
            updateTurnIndicator();
            
            handler.postDelayed(this::computerMove, 600);
        });
    }
    
    private void animateDropPiece(int targetRow, int col, int player, Runnable onComplete) {
        View cell = cellViews[targetRow][col];
        
        // postavljam boju komada
        cell.setBackgroundResource(player == HUMAN ? 
            R.drawable.connect_four_cell_player : R.drawable.connect_four_cell_ai);
        
        // počinjem od vrha
        float startY = -cellSize * (targetRow + 1);
        cell.setTranslationY(startY);
        cell.setAlpha(1f);
        
        // animiram pad sa bounce efektom
        ObjectAnimator dropAnim = ObjectAnimator.ofFloat(cell, "translationY", startY, 0f);
        dropAnim.setDuration(300 + (targetRow * 50));
        dropAnim.setInterpolator(new BounceInterpolator());
        
        dropAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onComplete.run();
            }
        });
        
        dropAnim.start();
    }
    
    private void animateColumnFull(int col) {
        // tresem ćelije kolone
        for (int i = 0; i < ROWS; i++) {
            View cell = cellViews[i][col];
            ObjectAnimator shake = ObjectAnimator.ofFloat(cell, "translationX", 0, 5, -5, 5, -5, 0);
            shake.setDuration(200);
            shake.setStartDelay(i * 20);
            shake.start();
        }
    }
    
    private void computerMove() {
        if (gameOver) return;
        
        int col = findBestMove();
        int row = findLowestEmptyRow(col);
        
        if (row != -1) {
            animating = true;
            animateDropPiece(row, col, COMPUTER, () -> {
                board[row][col] = COMPUTER;
                animating = false;
                
                if (checkWin(COMPUTER)) {
                    gameOver = true;
                    aiWins++;
                    saveScores();
                    updateScoreDisplay();
                    animateWinningCells();
                    handler.postDelayed(() -> showWin(false), 800);
                    return;
                }
                
                if (isBoardFull()) {
                    gameOver = true;
                    showDraw();
                    return;
                }
                
                isHumanTurn = true;
                updateTurnIndicator();
            });
        }
    }
    
    private int findBestMove() {
        // prioritet 1: pobijedim ako mogu
        for (int c = 0; c < COLS; c++) {
            int r = findLowestEmptyRow(c);
            if (r != -1) {
                board[r][c] = COMPUTER;
                if (checkWin(COMPUTER)) {
                    board[r][c] = EMPTY;
                    return c;
                }
                board[r][c] = EMPTY;
            }
        }
        
        // prioritet 2: blokiram čovjeka da pobijedi
        for (int c = 0; c < COLS; c++) {
            int r = findLowestEmptyRow(c);
            if (r != -1) {
                board[r][c] = HUMAN;
                if (checkWin(HUMAN)) {
                    board[r][c] = EMPTY;
                    return c;
                }
                board[r][c] = EMPTY;
            }
        }
        
        // prioritet 3: preferiram centralne kolone
        int[] preferredCols = {3, 4, 2, 5, 1, 6, 0, 7};
        for (int c : preferredCols) {
            if (c < COLS && findLowestEmptyRow(c) != -1) {
                return c;
            }
        }
        
        // fallback: random kolona
        int col;
        do {
            col = random.nextInt(COLS);
        } while (findLowestEmptyRow(col) == -1);
        
        return col;
    }
    
    private int findLowestEmptyRow(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row][col] == EMPTY) {
                return row;
            }
        }
        return -1;
    }
    
    private boolean checkWin(int player) {
        // provjeravam horizontalno
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c <= COLS - 4; c++) {
                if (board[r][c] == player && board[r][c+1] == player &&
                    board[r][c+2] == player && board[r][c+3] == player) {
                    winningCells = new int[][] {{r, c}, {r, c+1}, {r, c+2}, {r, c+3}};
                    return true;
                }
            }
        }
        
        // provjeravam vertikalno
        for (int r = 0; r <= ROWS - 4; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == player && board[r+1][c] == player &&
                    board[r+2][c] == player && board[r+3][c] == player) {
                    winningCells = new int[][] {{r, c}, {r+1, c}, {r+2, c}, {r+3, c}};
                    return true;
                }
            }
        }
        
        // provjeravam dijagonalno (dolje-desno)
        for (int r = 0; r <= ROWS - 4; r++) {
            for (int c = 0; c <= COLS - 4; c++) {
                if (board[r][c] == player && board[r+1][c+1] == player &&
                    board[r+2][c+2] == player && board[r+3][c+3] == player) {
                    winningCells = new int[][] {{r, c}, {r+1, c+1}, {r+2, c+2}, {r+3, c+3}};
                    return true;
                }
            }
        }
        
        // provjeravam dijagonalno (dolje-lijevo)
        for (int r = 0; r <= ROWS - 4; r++) {
            for (int c = 3; c < COLS; c++) {
                if (board[r][c] == player && board[r+1][c-1] == player &&
                    board[r+2][c-2] == player && board[r+3][c-3] == player) {
                    winningCells = new int[][] {{r, c}, {r+1, c-1}, {r+2, c-2}, {r+3, c-3}};
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void animateWinningCells() {
        if (winningCells == null) return;
        
        for (int i = 0; i < winningCells.length; i++) {
            int[] pos = winningCells[i];
            View cell = cellViews[pos[0]][pos[1]];
            
            // mijenjam u zlatnu winning boju
            cell.setBackgroundResource(R.drawable.connect_four_cell_win);
            
            // pulse animacija
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(cell, "scaleX", 1f, 1.2f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(cell, "scaleY", 1f, 1.2f, 1f);
            
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY);
            set.setDuration(300);
            set.setStartDelay(i * 100);
            set.setInterpolator(new OvershootInterpolator(2f));
            set.start();
        }
    }
    
    private boolean isBoardFull() {
        for (int c = 0; c < COLS; c++) {
            if (board[0][c] == EMPTY) {
                return false;
            }
        }
        return true;
    }
    
    private void updateTurnIndicator() {
        if (gameOver) {
            turnIndicatorContainer.setVisibility(View.INVISIBLE);
            return;
        }
        
        turnIndicatorContainer.setVisibility(View.VISIBLE);
        
        GradientDrawable dotDrawable = (GradientDrawable) turnDot.getBackground();
        
        if (isHumanTurn) {
            turnIndicator.setText(R.string.connect_four_your_turn);
            turnIndicator.setTextColor(0xFF4CAF50);
            dotDrawable.setColor(0xFF4CAF50);
            
            // Pulse animation on turn change
            animateTurnChange();
        } else {
            turnIndicator.setText(R.string.connect_four_computer_turn);
            turnIndicator.setTextColor(0xFFF44336);
            dotDrawable.setColor(0xFFF44336);
        }
    }
    
    private void animateTurnChange() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(turnIndicatorContainer, "scaleX", 1f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(turnIndicatorContainer, "scaleY", 1f, 1.05f, 1f);
        
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.start();
    }
    
    private void updateScoreDisplay() {
        playerScoreText.setText(String.valueOf(playerWins));
        aiScoreText.setText(String.valueOf(aiWins));
    }
    
    private void saveScores() {
        gamePrefs.edit()
            .putInt(KEY_PLAYER_WINS, playerWins)
            .putInt(KEY_AI_WINS, aiWins)
            .apply();
    }
    
    private void showWin(boolean playerWon) {
        if (playerWon) {
            statusTextView.setText(getString(R.string.you_win));
            statusTextView.setTextColor(0xFF4CAF50);
            statusSubtext.setText(getString(R.string.congratulations));
            awardBonusTime();
        } else {
            statusTextView.setText(getString(R.string.ai_wins));
            statusTextView.setTextColor(0xFFF44336);
            statusSubtext.setText(getString(R.string.better_luck_next_time));
        }
        showGameOverOverlay();
    }
    
    private void showDraw() {
        statusTextView.setText(getString(R.string.draw));
        statusTextView.setTextColor(0xFFFFD700);
        statusSubtext.setText(getString(R.string.its_a_tie));
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
    
    private void resetGame() {
        // Clear board with animation
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = EMPTY;
                View cell = cellViews[i][j];
                
                int delay = ((ROWS - 1 - i) * COLS + j) * 20;
                
                cell.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setStartDelay(delay)
                    .setDuration(100)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            cell.setBackgroundResource(R.drawable.connect_four_cell_empty);
                            cell.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .setInterpolator(new OvershootInterpolator(1.2f))
                                .setListener(null)
                                .start();
                        }
                    })
                    .start();
            }
        }
        
        winningCells = null;
        isHumanTurn = true;
        gameOver = false;
        animating = false;
        
        handler.postDelayed(this::updateTurnIndicator, 500);
    }
    
    private void awardBonusTime() {
        if (blockedPackageName != null) {
            preferencesManager.grantSudokuBonus(blockedPackageName);
        }
        
        Intent intent = new Intent("com.escape.app.BONUS_TIME_AWARDED");
        intent.putExtra("blocked_package", blockedPackageName);
        intent.putExtra("bonus_millis", BONUS_MINUTES * 60 * 1000L);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Toast.makeText(this, R.string.connect_four_bonus_awarded, Toast.LENGTH_LONG).show();
        
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
