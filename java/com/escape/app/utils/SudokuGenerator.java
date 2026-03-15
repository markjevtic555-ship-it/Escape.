package com.escape.app.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Utility klasa za generisanje Sudoku zagonetki backtracking algoritam za validne rješenja, nema na čemu Marko
 */
public class SudokuGenerator {
    private static final int SIZE = 9;
    private static final int SUBGRID_SIZE = 3;
    
    private int[][] solution;
    private int[][] puzzle;
    private Random random;
    
    public SudokuGenerator() {
        solution = new int[SIZE][SIZE];
        puzzle = new int[SIZE][SIZE];
        random = new Random();
    }
    
    /**
     * Generiše novu Sudoku zagonetku
     * @param difficulty Broj ćelija za uklanjanje (više = teže)
     * @return Puzzle grid (0 = prazna ćelija)
     */
    public int[][] generatePuzzle(int difficulty) {
        // Generišem kompletan validan solution
        generateSolution();
        
        // Kopiram solution u puzzle
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(solution[i], 0, puzzle[i], 0, SIZE);
        }
        
        // Uklanjam ćelije na osnovu difficulty
        int cellsToRemove = Math.min(difficulty, 50); // Cap na 50 da ostane solvable
        removeCells(cellsToRemove);
        
        return puzzle;
    }

    /**
     * Uzima solution grid
     */
    public int[][] getSolution() {
        return solution;
    }

    /**
     * Generiše kompletan validan Sudoku solution koristeći backtracking
     */
    private boolean generateSolution() {
        // Čistim grid
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                solution[i][j] = 0;
            }
        }
        
        return fillGrid(0, 0);
    }

    /**
     * Popunjava grid koristeći backtracking
     */
    private boolean fillGrid(int row, int col) {
        if (row == SIZE) {
            return true; // Uspješno popunjen cijeli grid
        }
        
        int nextRow = (col == SIZE - 1) ? row + 1 : row;
        int nextCol = (col + 1) % SIZE;
        
        // Pravim shuffled listu brojeva 1-9
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= SIZE; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers, random);
        
        // Pokušavam svaki broj
        for (int num : numbers) {
            if (isValid(solution, row, col, num)) {
                solution[row][col] = num;
                
                if (fillGrid(nextRow, nextCol)) {
                    return true;
                }
                
                solution[row][col] = 0; // Backtrack
            }
        }
        
        return false;
    }

    /**
     * Provjerava da li je stavljanje broja na poziciju validno
     */
    private boolean isValid(int[][] grid, int row, int col, int num) {
        // Provjeravam red
        for (int i = 0; i < SIZE; i++) {
            if (grid[row][i] == num) {
                return false;
            }
        }
        
        // Provjeravam kolonu
        for (int i = 0; i < SIZE; i++) {
            if (grid[i][col] == num) {
                return false;
            }
        }
        
        // Provjeravam 3x3 subgrid
        int startRow = (row / SUBGRID_SIZE) * SUBGRID_SIZE;
        int startCol = (col / SUBGRID_SIZE) * SUBGRID_SIZE;
        
        for (int i = startRow; i < startRow + SUBGRID_SIZE; i++) {
            for (int j = startCol; j < startCol + SUBGRID_SIZE; j++) {
                if (grid[i][j] == num) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Uklanja ćelije iz puzle
     */
    private void removeCells(int count) {
        List<int[]> positions = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                positions.add(new int[]{i, j});
            }
        }
        Collections.shuffle(positions, random);
        
        int removed = 0;
        for (int[] pos : positions) {
            if (removed >= count) break;
            puzzle[pos[0]][pos[1]] = 0;
            removed++;
        }
    }

    /**
     * Provjerava da li je puzzle solution tačan
     * @param userGrid Userov pokušaj rješenja
     * @return true ako je solution validan i kompletan
     */
    public static boolean checkSolution(int[][] userGrid) {
        // Provjeravam za prazne ćelije
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (userGrid[i][j] == 0) {
                    return false;
                }
            }
        }
        
        // Provjeravam redove
        for (int i = 0; i < SIZE; i++) {
            if (!isValidGroup(userGrid[i])) {
                return false;
            }
        }
        
        // Provjeravam kolone
        for (int j = 0; j < SIZE; j++) {
            int[] col = new int[SIZE];
            for (int i = 0; i < SIZE; i++) {
                col[i] = userGrid[i][j];
            }
            if (!isValidGroup(col)) {
                return false;
            }
        }
        
        // Provjeravam 3x3 subgridove
        for (int blockRow = 0; blockRow < SUBGRID_SIZE; blockRow++) {
            for (int blockCol = 0; blockCol < SUBGRID_SIZE; blockCol++) {
                int[] block = new int[SIZE];
                int idx = 0;
                for (int i = blockRow * SUBGRID_SIZE; i < (blockRow + 1) * SUBGRID_SIZE; i++) {
                    for (int j = blockCol * SUBGRID_SIZE; j < (blockCol + 1) * SUBGRID_SIZE; j++) {
                        block[idx++] = userGrid[i][j];
                    }
                }
                if (!isValidGroup(block)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Provjerava da li grupa (red, kolona ili subgrid) sadrži sve brojeve 1-9
     */
    private static boolean isValidGroup(int[] group) {
        boolean[] seen = new boolean[SIZE + 1];
        for (int num : group) {
            if (num < 1 || num > SIZE || seen[num]) {
                return false;
            }
            seen[num] = true;
        }
        return true;
    }
}


