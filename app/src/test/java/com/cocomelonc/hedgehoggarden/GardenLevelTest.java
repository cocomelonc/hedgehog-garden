/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;

public final class GardenLevelTest {
    @Test
    public void tenGardensHaveReachableObjectivesAndBalancedDew() {
        GardenLevel[] levels = GardenLevel.createAll();
        assertEquals(10, levels.length);

        for (GardenLevel level : levels) {
            assertEquals(3, level.dewCount);
            assertEquals(level.dewCount, level.flowerCount);
            boolean[][] reached = flood(level);
            assertTrue(reached[level.gateRow][level.gateCol]);
            for (int row = 0; row < GardenLevel.ROWS; row++) {
                for (int col = 0; col < GardenLevel.COLS; col++) {
                    char tile = level.tileAt(row, col);
                    if (tile == GardenLevel.DEW || tile == GardenLevel.FLOWER) {
                        assertTrue("Objective is isolated at " + row + "," + col,
                                reached[row][col]);
                    }
                }
            }
        }
    }

    private static boolean[][] flood(GardenLevel level) {
        boolean[][] reached = new boolean[GardenLevel.ROWS][GardenLevel.COLS];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        reached[level.startRow][level.startCol] = true;
        open.add(level.startRow * GardenLevel.COLS + level.startCol);
        int[] rowStep = {-1, 0, 1, 0};
        int[] colStep = {0, 1, 0, -1};
        while (!open.isEmpty()) {
            int current = open.removeFirst();
            int row = current / GardenLevel.COLS;
            int col = current % GardenLevel.COLS;
            for (int direction = 0; direction < rowStep.length; direction++) {
                int nextRow = row + rowStep[direction];
                int nextCol = col + colStep[direction];
                if (level.isWalkable(nextRow, nextCol) && !reached[nextRow][nextCol]) {
                    reached[nextRow][nextCol] = true;
                    open.add(nextRow * GardenLevel.COLS + nextCol);
                }
            }
        }
        return reached;
    }
}
