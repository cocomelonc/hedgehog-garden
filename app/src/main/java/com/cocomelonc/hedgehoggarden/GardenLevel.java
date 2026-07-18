/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

/** Immutable tilemap and palette for one garden. */
final class GardenLevel {
    static final int COLS = 14;
    static final int ROWS = 8;

    static final char GRASS = '.';
    static final char BUSH = '#';
    static final char WATER = '~';
    static final char ROCK = 'R';
    static final char BRIDGE = '=';
    static final char START = 'S';
    static final char DEW = 'D';
    static final char FLOWER = 'F';
    static final char GATE = 'G';

    final int nameRes;
    final int backgroundTop;
    final int backgroundBottom;
    final int grassA;
    final int grassB;
    final int pathColor;
    final int waterColor;
    final int accentColor;
    final int flowerColor;
    final int seed;
    final String[] map;
    final int startRow;
    final int startCol;
    final int gateRow;
    final int gateCol;
    final int dewCount;
    final int flowerCount;

    private GardenLevel(
            int nameRes,
            int backgroundTop,
            int backgroundBottom,
            int grassA,
            int grassB,
            int pathColor,
            int waterColor,
            int accentColor,
            int flowerColor,
            int seed,
            String... map
    ) {
        this.nameRes = nameRes;
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.grassA = grassA;
        this.grassB = grassB;
        this.pathColor = pathColor;
        this.waterColor = waterColor;
        this.accentColor = accentColor;
        this.flowerColor = flowerColor;
        this.seed = seed;
        this.map = map.clone();

        if (map.length != ROWS) {
            throw new IllegalArgumentException("Garden must contain exactly " + ROWS + " rows");
        }
        int foundStartRow = -1;
        int foundStartCol = -1;
        int foundGateRow = -1;
        int foundGateCol = -1;
        int starts = 0;
        int gates = 0;
        int dews = 0;
        int flowers = 0;
        for (int row = 0; row < ROWS; row++) {
            if (map[row].length() != COLS) {
                throw new IllegalArgumentException("Garden row " + row + " must contain " + COLS + " tiles");
            }
            for (int col = 0; col < COLS; col++) {
                char tile = map[row].charAt(col);
                if (!isKnownTile(tile)) {
                    throw new IllegalArgumentException("Unknown garden tile: " + tile);
                }
                if (tile == START) {
                    starts++;
                    foundStartRow = row;
                    foundStartCol = col;
                } else if (tile == GATE) {
                    gates++;
                    foundGateRow = row;
                    foundGateCol = col;
                } else if (tile == DEW) {
                    dews++;
                } else if (tile == FLOWER) {
                    flowers++;
                }
            }
        }
        if (starts != 1 || gates != 1) {
            throw new IllegalArgumentException("Garden needs exactly one start and one gate");
        }
        if (dews == 0 || dews != flowers) {
            throw new IllegalArgumentException("Every flower needs one reachable dew drop");
        }
        startRow = foundStartRow;
        startCol = foundStartCol;
        gateRow = foundGateRow;
        gateCol = foundGateCol;
        dewCount = dews;
        flowerCount = flowers;
    }

    char tileAt(int row, int col) {
        return map[row].charAt(col);
    }

    boolean isWalkable(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return false;
        }
        char tile = tileAt(row, col);
        return tile != BUSH && tile != WATER && tile != ROCK;
    }

    static GardenLevel[] createAll() {
        return new GardenLevel[]{
                morningPatch(), cloverLane(), lilyBrook(), roseCorner(), pebbleGarden(),
                bluebellBend(), mintMaze(), peachGrove(), moonGarden(), goldenMeadow()
        };
    }

    private static GardenLevel morningPatch() {
        return new GardenLevel(
                R.string.level_1,
                0xFFF8E9E4, 0xFFE8DDF0, 0xFFC6DDB6, 0xFFD4E7C2,
                0xFFE7DEBE, 0xFF9CCDD4, 0xFFF0B8A8, 0xFFF2A7B7, 1103,
                "..............",
                ".S..D.....F...",
                "....##........",
                "...F......D...",
                ".......##.....",
                "..D.......F...",
                "..........G...",
                ".............."
        );
    }

    private static GardenLevel cloverLane() {
        return new GardenLevel(
                R.string.level_2,
                0xFFF4EDE1, 0xFFDDE9D4, 0xFFB9D5AA, 0xFFCBE2B8,
                0xFFE3D9B8, 0xFF9FCBD1, 0xFFA8C995, 0xFFFFC96F, 2213,
                "..............",
                ".S..#...D..F..",
                "....#.........",
                ".D..#####.....",
                "....#....F....",
                ".F..#....D....",
                "....#......G..",
                ".............."
        );
    }

    private static GardenLevel lilyBrook() {
        return new GardenLevel(
                R.string.level_3,
                0xFFE5EDF5, 0xFFD5E5E8, 0xFFB8D3BE, 0xFFCBE1C8,
                0xFFE5DFC8, 0xFF91C8D3, 0xFF90B9C8, 0xFFF4C4D0, 3347,
                "..............",
                ".S..D.....F...",
                "......F....D..",
                "~~~~~=~~~~~~~~",
                "..............",
                "..D.....F.....",
                "..........G...",
                ".............."
        );
    }

    private static GardenLevel roseCorner() {
        return new GardenLevel(
                R.string.level_4,
                0xFFFFEBEA, 0xFFF0DCE5, 0xFFC7D6B1, 0xFFD9E4C0,
                0xFFE8D9C0, 0xFFA2CDD2, 0xFFE8AAB9, 0xFFF18FA8, 4481,
                "..............",
                ".S..###....D..",
                "....#F#.......",
                ".D..#.#..F....",
                "....#.#.......",
                ".F..#.#..D....",
                "....#......G..",
                ".............."
        );
    }

    private static GardenLevel pebbleGarden() {
        return new GardenLevel(
                R.string.level_5,
                0xFFF1EBE6, 0xFFDADDE7, 0xFFBBCDB6, 0xFFCEDCC4,
                0xFFE0D9C8, 0xFF9EC7CD, 0xFFAAA7B5, 0xFFFFD17B, 5527,
                "..R.....R.....",
                ".S..D.....F...",
                "....RR........",
                ".F......RR.D..",
                "..............",
                "..D.RRR...F...",
                "..........G...",
                ".....R........"
        );
    }

    private static GardenLevel bluebellBend() {
        return new GardenLevel(
                R.string.level_6,
                0xFFE9E9F6, 0xFFD9DDF0, 0xFFB8D0B7, 0xFFC9DFC4,
                0xFFE0DCC5, 0xFF8FC4D0, 0xFFAFA2D7, 0xFF9D91D5, 6673,
                "......~.......",
                ".S.D..~...F...",
                "..F...~..D....",
                "......=.......",
                "......~.......",
                "..D...~...F...",
                "......~....G..",
                "......=......."
        );
    }

    private static GardenLevel mintMaze() {
        return new GardenLevel(
                R.string.level_7,
                0xFFEAF4ED, 0xFFD7EBE1, 0xFFAFD2BA, 0xFFC2DEC8,
                0xFFE4DEC6, 0xFF95CAC7, 0xFF89BEA3, 0xFFF2B7C7, 7727,
                "..............",
                ".S.####...D.F.",
                "...#......#...",
                ".D.#.####.#...",
                "...#....F.#...",
                ".F.####...#D..",
                "............G.",
                ".............."
        );
    }

    private static GardenLevel peachGrove() {
        return new GardenLevel(
                R.string.level_8,
                0xFFFFECE3, 0xFFF1D9D2, 0xFFC5D2AB, 0xFFD9DFB8,
                0xFFE8D8B8, 0xFFA3CBD0, 0xFFF0A58D, 0xFFF2B0A0, 8861,
                "..............",
                ".S..#D..#..F..",
                "....#...#.....",
                ".F..#...#..D..",
                "....#...#.....",
                ".D......#..F..",
                "....###....G..",
                ".............."
        );
    }

    private static GardenLevel moonGarden() {
        return new GardenLevel(
                R.string.level_9,
                0xFFD9D8EC, 0xFFC5D5E1, 0xFFA9C5B4, 0xFFBCD4C0,
                0xFFD8D8C3, 0xFF86BAC8, 0xFFA99ECC, 0xFFF0D07D, 9917,
                "....~~~.......",
                ".S..~F....D...",
                "....~=~.......",
                "....~~~..F....",
                "..............",
                "..D..~~~..D...",
                ".F...~=~...G..",
                ".....~~~......"
        );
    }

    private static GardenLevel goldenMeadow() {
        return new GardenLevel(
                R.string.level_10,
                0xFFF2E7CF, 0xFFD8D4E5, 0xFFB9C99F, 0xFFCBD8AA,
                0xFFE7D5A5, 0xFF9FC7CF, 0xFFF0C866, 0xFFFFC857, 11071,
                "..............",
                ".S.D.#....F...",
                ".....#..~~~...",
                ".F...=..~=~D..",
                ".....#..~~~...",
                ".D...#....F...",
                ".....#.....G..",
                ".............."
        );
    }

    private static boolean isKnownTile(char tile) {
        return tile == GRASS || tile == BUSH || tile == WATER || tile == ROCK
                || tile == BRIDGE || tile == START || tile == DEW
                || tile == FLOWER || tile == GATE;
    }
}
