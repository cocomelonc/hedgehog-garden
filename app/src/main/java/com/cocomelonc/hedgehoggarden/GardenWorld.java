/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Pure-Java game state and shortest-path movement. */
final class GardenWorld {
    enum State {
        TITLE,
        PLAYING,
        PAUSED,
        LEVEL_COMPLETE,
        JOURNEY_COMPLETE
    }

    interface Listener {
        void onDewCollected(float col, float row);

        void onFlowerBloomed(float col, float row);

        void onLevelComplete(int completedLevel);

        void onJourneyComplete();
    }

    private static final float MOVE_SPEED = 4.2f;
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private final GardenLevel[] levels;
    private final Listener listener;
    private final ArrayDeque<Integer> path = new ArrayDeque<>();

    private State state = State.TITLE;
    private GardenLevel level;
    private int levelIndex;
    private int row;
    private int col;
    private float visualRow;
    private float visualCol;
    private float facing = 1f;
    private boolean[][] collectedDew;
    private boolean[][] bloomedFlower;
    private int dewInventory;
    private int collectedCount;
    private int bloomedCount;

    GardenWorld(GardenLevel[] levels, Listener listener) {
        if (levels == null || levels.length == 0) {
            throw new IllegalArgumentException("At least one garden is required");
        }
        this.levels = levels.clone();
        this.listener = listener;
        loadLevel(0);
        state = State.TITLE;
    }

    void startJourney(int requestedLevel) {
        loadLevel(clampLevel(requestedLevel));
        state = State.PLAYING;
    }

    void restartJourney() {
        startJourney(0);
    }

    void showTitle() {
        path.clear();
        state = State.TITLE;
    }

    void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    boolean tapCell(int targetRow, int targetCol) {
        if (state != State.PLAYING || !level.isWalkable(targetRow, targetCol)) {
            return false;
        }
        if (targetRow == row && targetCol == col) {
            path.clear();
            return true;
        }

        int total = GardenLevel.ROWS * GardenLevel.COLS;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = encode(row, col);
        int goal = encode(targetRow, targetCol);
        visited[start] = true;
        open.add(start);

        while (!open.isEmpty() && !visited[goal]) {
            int current = open.removeFirst();
            int currentRow = current / GardenLevel.COLS;
            int currentCol = current % GardenLevel.COLS;
            for (int direction = 0; direction < ROW_STEP.length; direction++) {
                int nextRow = currentRow + ROW_STEP[direction];
                int nextCol = currentCol + COL_STEP[direction];
                if (!level.isWalkable(nextRow, nextCol)) {
                    continue;
                }
                int next = encode(nextRow, nextCol);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                open.addLast(next);
            }
        }

        if (!visited[goal]) {
            return false;
        }
        ArrayDeque<Integer> reversed = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            reversed.addFirst(cursor);
        }
        path.clear();
        path.addAll(reversed);
        return true;
    }

    void update(float elapsedSeconds) {
        if (state != State.PLAYING || path.isEmpty()) {
            return;
        }
        float remaining = MOVE_SPEED * Math.min(Math.max(elapsedSeconds, 0f), 0.1f);
        while (remaining > 0f && !path.isEmpty() && state == State.PLAYING) {
            int next = path.peekFirst();
            int nextRow = next / GardenLevel.COLS;
            int nextCol = next % GardenLevel.COLS;
            float deltaRow = nextRow - visualRow;
            float deltaCol = nextCol - visualCol;
            float distance = (float) Math.hypot(deltaRow, deltaCol);
            if (deltaCol != 0f) {
                facing = Math.signum(deltaCol);
            }
            if (distance <= remaining + 0.0001f) {
                visualRow = nextRow;
                visualCol = nextCol;
                row = nextRow;
                col = nextCol;
                remaining -= distance;
                path.removeFirst();
                enterCell();
            } else {
                visualRow += deltaRow / distance * remaining;
                visualCol += deltaCol / distance * remaining;
                remaining = 0f;
            }
        }
    }

    void continueAfterLevel() {
        if (state != State.LEVEL_COMPLETE) {
            return;
        }
        if (levelIndex + 1 >= levels.length) {
            state = State.JOURNEY_COMPLETE;
            if (listener != null) {
                listener.onJourneyComplete();
            }
            return;
        }
        loadLevel(levelIndex + 1);
        state = State.PLAYING;
    }

    private void enterCell() {
        char tile = level.tileAt(row, col);
        if (tile == GardenLevel.DEW && !collectedDew[row][col]) {
            collectedDew[row][col] = true;
            dewInventory++;
            collectedCount++;
            if (listener != null) {
                listener.onDewCollected(visualCol, visualRow);
            }
        } else if (tile == GardenLevel.FLOWER && !bloomedFlower[row][col] && dewInventory > 0) {
            bloomedFlower[row][col] = true;
            dewInventory--;
            bloomedCount++;
            if (listener != null) {
                listener.onFlowerBloomed(visualCol, visualRow);
            }
        }

        if (tile == GardenLevel.GATE && bloomedCount == level.flowerCount) {
            path.clear();
            state = State.LEVEL_COMPLETE;
            if (listener != null) {
                listener.onLevelComplete(levelIndex);
            }
        }
    }

    private void loadLevel(int index) {
        levelIndex = index;
        level = levels[levelIndex];
        row = level.startRow;
        col = level.startCol;
        visualRow = row;
        visualCol = col;
        facing = 1f;
        dewInventory = 0;
        collectedCount = 0;
        bloomedCount = 0;
        collectedDew = new boolean[GardenLevel.ROWS][GardenLevel.COLS];
        bloomedFlower = new boolean[GardenLevel.ROWS][GardenLevel.COLS];
        path.clear();
    }

    private int clampLevel(int requestedLevel) {
        return Math.max(0, Math.min(requestedLevel, levels.length - 1));
    }

    private static int encode(int encodedRow, int encodedCol) {
        return encodedRow * GardenLevel.COLS + encodedCol;
    }

    State getState() {
        return state;
    }

    GardenLevel getLevel() {
        return level;
    }

    int getLevelIndex() {
        return levelIndex;
    }

    int getLevelCount() {
        return levels.length;
    }

    int getRow() {
        return row;
    }

    int getCol() {
        return col;
    }

    float getVisualRow() {
        return visualRow;
    }

    float getVisualCol() {
        return visualCol;
    }

    float getFacing() {
        return facing;
    }

    int getDewInventory() {
        return dewInventory;
    }

    int getCollectedCount() {
        return collectedCount;
    }

    int getBloomedCount() {
        return bloomedCount;
    }

    int getPathLength() {
        return path.size();
    }

    boolean isDewCollected(int checkRow, int checkCol) {
        return collectedDew[checkRow][checkCol];
    }

    boolean isFlowerBloomed(int checkRow, int checkCol) {
        return bloomedFlower[checkRow][checkCol];
    }
}
