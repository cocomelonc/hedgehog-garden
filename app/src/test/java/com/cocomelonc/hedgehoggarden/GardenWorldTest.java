/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GardenWorldTest {
    @Test
    public void blockedTilesCannotBeSelected() {
        GardenWorld world = new GardenWorld(GardenLevel.createAll(), null);
        world.startJourney(0);
        assertFalse(world.tapCell(2, 4));
        assertEquals(0, world.getPathLength());
    }

    @Test
    public void sleepyFlowerNeedsDew() {
        GardenWorld world = new GardenWorld(GardenLevel.createAll(), null);
        world.startJourney(0);
        move(world, 3, 3);
        assertEquals(0, world.getBloomedCount());
        assertEquals(0, world.getDewInventory());

        moveToFirst(world, GardenLevel.DEW);
        assertEquals(1, world.getDewInventory());
        moveToFirstUnfinishedFlower(world);
        assertEquals(1, world.getBloomedCount());
        assertEquals(0, world.getDewInventory());
    }

    @Test
    public void allTenGardensCanBeCompleted() {
        GardenLevel[] levels = GardenLevel.createAll();
        GardenWorld world = new GardenWorld(levels, null);
        world.startJourney(0);

        for (int levelIndex = 0; levelIndex < levels.length; levelIndex++) {
            assertEquals(levelIndex, world.getLevelIndex());
            collectEvery(world, GardenLevel.DEW);
            collectEvery(world, GardenLevel.FLOWER);
            assertEquals(world.getLevel().dewCount, world.getCollectedCount());
            assertEquals(world.getLevel().flowerCount, world.getBloomedCount());
            move(world, world.getLevel().gateRow, world.getLevel().gateCol);
            assertEquals(GardenWorld.State.LEVEL_COMPLETE, world.getState());
            world.continueAfterLevel();
        }
        assertEquals(GardenWorld.State.JOURNEY_COMPLETE, world.getState());
    }

    @Test
    public void resumeLevelIsClamped() {
        GardenWorld world = new GardenWorld(GardenLevel.createAll(), null);
        world.startJourney(999);
        assertEquals(9, world.getLevelIndex());
        world.startJourney(-5);
        assertEquals(0, world.getLevelIndex());
    }

    private static void collectEvery(GardenWorld world, char tile) {
        GardenLevel level = world.getLevel();
        for (int row = 0; row < GardenLevel.ROWS; row++) {
            for (int col = 0; col < GardenLevel.COLS; col++) {
                if (level.tileAt(row, col) == tile) {
                    move(world, row, col);
                }
            }
        }
    }

    private static void moveToFirst(GardenWorld world, char tile) {
        GardenLevel level = world.getLevel();
        for (int row = 0; row < GardenLevel.ROWS; row++) {
            for (int col = 0; col < GardenLevel.COLS; col++) {
                if (level.tileAt(row, col) == tile) {
                    move(world, row, col);
                    return;
                }
            }
        }
        throw new AssertionError("Tile not found: " + tile);
    }

    private static void moveToFirstUnfinishedFlower(GardenWorld world) {
        GardenLevel level = world.getLevel();
        for (int row = 0; row < GardenLevel.ROWS; row++) {
            for (int col = 0; col < GardenLevel.COLS; col++) {
                if (level.tileAt(row, col) == GardenLevel.FLOWER
                        && !world.isFlowerBloomed(row, col)) {
                    move(world, row, col);
                    return;
                }
            }
        }
        throw new AssertionError("Unfinished flower not found");
    }

    private static void move(GardenWorld world, int row, int col) {
        assertTrue("No route to " + row + "," + col, world.tapCell(row, col));
        int guard = 0;
        while (world.getPathLength() > 0 && world.getState() == GardenWorld.State.PLAYING) {
            world.update(0.1f);
            if (++guard > 1000) {
                throw new AssertionError("Movement did not settle");
            }
        }
        assertEquals(row, world.getRow());
        assertEquals(col, world.getCol());
    }
}
