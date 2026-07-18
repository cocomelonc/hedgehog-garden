/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dependency-free renderer and input layer. The 1280x720 logical canvas scales
 * uniformly to phones, tablets, foldables, Chromebooks, and resizable windows.
 */
final class HedgehogGardenView extends View implements GardenWorld.Listener {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float BOARD_LEFT = 164f;
    private static final float BOARD_TOP = 102f;
    private static final float TILE = 68f;
    private static final float PAUSE_X = 1218f;
    private static final float PAUSE_Y = 53f;
    private static final float OVERLAY_LEFT = 310f;
    private static final float OVERLAY_TOP = 168f;
    private static final float OVERLAY_RIGHT = 970f;
    private static final float OVERLAY_BOTTOM = 552f;
    private static final float OVERLAY_PADDING = 72f;
    private static final String PREFS = "hedgehog_garden_progress";
    private static final String PREF_RESUME_LEVEL = "resume_level";
    private static final String PREF_LANGUAGE = "language";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final GardenWorld world;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(0xC0C0A11L);

    private Context localizedContext;
    private String language;
    private LinearGradient outsideGradient;
    private LinearGradient levelGradient;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private long lastFrameNanos;
    private boolean hostResumed = true;
    private float hintTime;
    private int targetRow = -1;
    private int targetCol = -1;
    private GardenWorld.State lastVisualState = GardenWorld.State.TITLE;
    private float overlayProgress = 1f;

    HedgehogGardenView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setKeepScreenOn(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        language = preferences.getString(PREF_LANGUAGE, "en");
        if (!"ru".equals(language)) {
            language = "en";
        }
        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.accessibility_game));

        world = new GardenWorld(GardenLevel.createAll(), this);
        outsideGradient = new LinearGradient(
                0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF7E8E4, 0xFFDCE7DF, 0xFFDCD8EA},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
        );
        rebuildLevelGradient();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewScale = Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT);
        viewOffsetX = (width - WORLD_WIDTH * viewScale) * 0.5f;
        viewOffsetY = (height - WORLD_HEIGHT * viewScale) * 0.5f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05f);
        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
            hintTime += dt;
        }

        GardenWorld.State visualState = world.getState();
        if (visualState != lastVisualState) {
            lastVisualState = visualState;
            overlayProgress = isOverlayState(visualState) ? 0f : 1f;
        }
        if (isOverlayState(visualState) && hostResumed) {
            overlayProgress = Math.min(1f, overlayProgress + dt * 5.5f);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        float time = now / 1_000_000_000f;
        if (visualState == GardenWorld.State.TITLE) {
            drawTitle(canvas, time);
        } else {
            drawLevel(canvas, time);
        }
        canvas.restore();

        if (hostResumed) {
            postInvalidateOnAnimation();
        }
    }

    private static boolean isOverlayState(GardenWorld.State state) {
        return state == GardenWorld.State.PAUSED || state == GardenWorld.State.LEVEL_COMPLETE;
    }

    private void drawTitle(Canvas canvas, float time) {
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);

        drawSun(canvas, 156f, 128f);
        drawCloud(canvas, 1010f, 121f, 1.05f);
        drawCloud(canvas, 242f, 255f, 0.72f);
        drawHill(canvas, 0xFFBDD4BC, 520f, 64f, 0.013f);
        drawHill(canvas, 0xFFA9C6AD, 584f, 46f, 0.017f);
        drawTitleFlowers(canvas, time);

        drawFittedText(canvas, text(R.string.game_title), 640f, 151f,
                70f, 780f, 0xFF62596D, true);
        drawFittedText(canvas, text(R.string.game_subtitle), 640f, 207f,
                25f, 720f, 0xFF817789, false);

        canvas.save();
        canvas.translate(307f, 434f + (float) Math.sin(time * 2f) * 3f);
        canvas.scale(1.75f, 1.75f);
        drawHedgehogAtOrigin(canvas, time, 1f);
        canvas.restore();

        float pulse = 0.98f + 0.025f * (float) Math.sin(time * 2.6f);
        canvas.save();
        canvas.scale(pulse, pulse, 692f, 337f);
        drawPill(canvas, 504f, 294f, 880f, 380f, 0xF7FFF9F1, 0x265F576A);
        drawFittedText(canvas, text(R.string.touch_to_begin), 692f, 347f,
                29f, 325f, 0xFF675E72, true);
        canvas.restore();

        drawFittedText(canvas, text(R.string.tap_to_walk), 700f, 423f,
                21f, 580f, 0xE8635D6D, false);
        drawFittedText(canvas, text(R.string.collect_dew), 700f, 458f,
                21f, 580f, 0xE8635D6D, false);
        drawLanguageSwitch(canvas, 1167f, 56f);

        int resume = preferences.getInt(PREF_RESUME_LEVEL, 0);
        float first = 640f - (world.getLevelCount() - 1) * 11f;
        for (int i = 0; i < world.getLevelCount(); i++) {
            paint.setColor(i == resume ? 0xFFFFCE72 : 0x78FFFFFF);
            canvas.drawCircle(first + i * 22f, 674f, i == resume ? 6f : 4f, paint);
        }
    }

    private void drawLevel(Canvas canvas, float time) {
        paint.setShader(levelGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);
        drawCloud(canvas, 74f, 56f, 0.56f);
        drawCloud(canvas, 1085f, 650f, 0.48f);

        drawBoard(canvas, time);
        drawTarget(canvas, time);
        drawParticles(canvas);
        float hedgehogX = BOARD_LEFT + (world.getVisualCol() + 0.5f) * TILE;
        float hedgehogY = BOARD_TOP + (world.getVisualRow() + 0.5f) * TILE + 6f;
        canvas.save();
        canvas.translate(hedgehogX, hedgehogY);
        canvas.scale(0.68f, 0.68f);
        drawHedgehogAtOrigin(canvas, time, world.getFacing());
        canvas.restore();
        drawHud(canvas, time);

        if (hintTime < 5.5f && world.getState() == GardenWorld.State.PLAYING) {
            float alpha = hintTime < 4f ? 1f : Math.max(0f, (5.5f - hintTime) / 1.5f);
            drawHint(canvas, alpha);
        }

        if (world.getState() == GardenWorld.State.PAUSED) {
            drawOverlay(canvas, R.string.paused, R.string.touch_to_continue, time);
        } else if (world.getState() == GardenWorld.State.LEVEL_COMPLETE) {
            drawOverlay(canvas, R.string.level_complete, R.string.touch_to_continue, time);
        } else if (world.getState() == GardenWorld.State.JOURNEY_COMPLETE) {
            drawJourneyComplete(canvas, time);
        }
    }

    private void drawBoard(Canvas canvas, float time) {
        GardenLevel level = world.getLevel();
        rect.set(BOARD_LEFT - 12f, BOARD_TOP - 12f,
                BOARD_LEFT + GardenLevel.COLS * TILE + 12f,
                BOARD_TOP + GardenLevel.ROWS * TILE + 12f);
        paint.setColor(0x2A5B5260);
        canvas.drawRoundRect(rect.left + 5f, rect.top + 8f, rect.right + 5f,
                rect.bottom + 8f, 31f, 31f, paint);
        paint.setColor(0xBFF9F3E6);
        canvas.drawRoundRect(rect, 31f, 31f, paint);

        for (int row = 0; row < GardenLevel.ROWS; row++) {
            for (int col = 0; col < GardenLevel.COLS; col++) {
                float left = BOARD_LEFT + col * TILE;
                float top = BOARD_TOP + row * TILE;
                int grass = ((row + col) & 1) == 0 ? level.grassA : level.grassB;
                paint.setColor(grass);
                canvas.drawRoundRect(left + 1.5f, top + 1.5f,
                        left + TILE - 1.5f, top + TILE - 1.5f, 13f, 13f, paint);
                drawGrassDetails(canvas, left, top, row, col, level.seed);
            }
        }

        for (int row = 0; row < GardenLevel.ROWS; row++) {
            for (int col = 0; col < GardenLevel.COLS; col++) {
                float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                float cy = BOARD_TOP + (row + 0.5f) * TILE;
                char tile = level.tileAt(row, col);
                if (tile == GardenLevel.BUSH) {
                    drawBush(canvas, cx, cy, level.accentColor);
                } else if (tile == GardenLevel.WATER) {
                    drawWater(canvas, cx, cy, level.waterColor, time, row + col);
                } else if (tile == GardenLevel.ROCK) {
                    drawRock(canvas, cx, cy);
                } else if (tile == GardenLevel.BRIDGE) {
                    drawBridge(canvas, cx, cy, level.waterColor);
                } else if (tile == GardenLevel.DEW && !world.isDewCollected(row, col)) {
                    drawDew(canvas, cx, cy, time + row * 0.7f + col);
                } else if (tile == GardenLevel.FLOWER) {
                    drawFlower(canvas, cx, cy + 3f, world.isFlowerBloomed(row, col),
                            level.flowerColor, time + col);
                } else if (tile == GardenLevel.GATE) {
                    drawGate(canvas, cx, cy, world.getBloomedCount() == level.flowerCount, time);
                }
            }
        }
    }

    private void drawGrassDetails(Canvas canvas, float left, float top, int row, int col, int seed) {
        int value = seed + row * 97 + col * 53;
        if (Math.floorMod(value, 4) == 0) {
            paint.setColor(0x39738E65);
            paint.setStrokeWidth(1.7f);
            paint.setStyle(Paint.Style.STROKE);
            float x = left + 13f + Math.floorMod(value, 31);
            float y = top + 47f;
            canvas.drawLine(x, y, x - 3f, y - 8f, paint);
            canvas.drawLine(x, y, x + 4f, y - 6f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (Math.floorMod(value, 11) == 0) {
            paint.setColor(0x70FFF5DE);
            canvas.drawCircle(left + 18f, top + 18f, 2.2f, paint);
        }
    }

    private void drawBush(Canvas canvas, float cx, float cy, int accent) {
        paint.setColor(darken(accent, 0.84f));
        canvas.drawCircle(cx - 19f, cy + 4f, 21f, paint);
        canvas.drawCircle(cx + 18f, cy + 5f, 22f, paint);
        canvas.drawCircle(cx, cy - 11f, 25f, paint);
        paint.setColor(lighten(accent, 0.10f));
        canvas.drawCircle(cx - 11f, cy - 10f, 12f, paint);
        canvas.drawCircle(cx + 16f, cy - 4f, 10f, paint);
        paint.setColor(0x8FFFF3CF);
        canvas.drawCircle(cx - 6f, cy - 15f, 2.4f, paint);
        canvas.drawCircle(cx + 20f, cy - 7f, 2f, paint);
    }

    private void drawWater(Canvas canvas, float cx, float cy, int color, float time, int phase) {
        paint.setColor(color);
        canvas.drawRoundRect(cx - TILE / 2f + 1.5f, cy - TILE / 2f + 1.5f,
                cx + TILE / 2f - 1.5f, cy + TILE / 2f - 1.5f, 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x75F6FFFF);
        float offset = (float) Math.sin(time * 1.8f + phase) * 3f;
        canvas.drawArc(cx - 24f + offset, cy - 9f, cx + 2f + offset, cy + 3f,
                195f, 95f, false, paint);
        canvas.drawArc(cx - 2f - offset, cy + 9f, cx + 25f - offset, cy + 20f,
                195f, 95f, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBridge(Canvas canvas, float cx, float cy, int waterColor) {
        paint.setColor(waterColor);
        canvas.drawRoundRect(cx - TILE / 2f + 1.5f, cy - TILE / 2f + 1.5f,
                cx + TILE / 2f - 1.5f, cy + TILE / 2f - 1.5f, 12f, 12f, paint);
        paint.setColor(0xFFB99772);
        canvas.drawRoundRect(cx - 31f, cy - 23f, cx + 31f, cy + 23f, 8f, 8f, paint);
        paint.setColor(0xFFDBC49F);
        for (int i = -2; i <= 2; i++) {
            canvas.drawRoundRect(cx - 29f + i * 12f, cy - 21f,
                    cx - 20f + i * 12f, cy + 21f, 3f, 3f, paint);
        }
        paint.setColor(0x4C6D5342);
        canvas.drawRoundRect(cx - 31f, cy - 25f, cx + 31f, cy - 21f, 2f, 2f, paint);
        canvas.drawRoundRect(cx - 31f, cy + 21f, cx + 31f, cy + 25f, 2f, 2f, paint);
    }

    private void drawRock(Canvas canvas, float cx, float cy) {
        path.reset();
        path.moveTo(cx - 26f, cy + 15f);
        path.quadTo(cx - 31f, cy - 7f, cx - 13f, cy - 23f);
        path.quadTo(cx + 15f, cy - 29f, cx + 28f, cy - 4f);
        path.quadTo(cx + 31f, cy + 20f, cx + 9f, cy + 25f);
        path.quadTo(cx - 12f, cy + 27f, cx - 26f, cy + 15f);
        paint.setColor(0xFF9F9BA5);
        canvas.drawPath(path, paint);
        paint.setColor(0x68FFFFFF);
        canvas.drawOval(cx - 12f, cy - 17f, cx + 8f, cy - 8f, paint);
    }

    private void drawDew(Canvas canvas, float cx, float cy, float time) {
        float bob = (float) Math.sin(time * 2.6f) * 3f;
        cy += bob;
        path.reset();
        path.moveTo(cx, cy - 24f);
        path.cubicTo(cx - 4f, cy - 13f, cx - 17f, cy - 2f, cx - 17f, cy + 9f);
        path.cubicTo(cx - 17f, cy + 29f, cx + 17f, cy + 29f, cx + 17f, cy + 9f);
        path.cubicTo(cx + 17f, cy - 2f, cx + 4f, cy - 13f, cx, cy - 24f);
        paint.setColor(0xFF6FC4D4);
        canvas.drawPath(path, paint);
        paint.setColor(0xBFFFFFFF);
        canvas.drawOval(cx - 8f, cy - 3f, cx - 2f, cy + 8f, paint);
    }

    private void drawFlower(Canvas canvas, float cx, float cy, boolean bloomed,
                            int flowerColor, float time) {
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF668D62);
        canvas.drawLine(cx, cy + 21f, cx, cy - 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawArc(cx - 15f, cy + 2f, cx + 1f, cy + 16f, 190f, 105f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        if (!bloomed) {
            paint.setColor(darken(flowerColor, 0.86f));
            canvas.drawOval(cx - 12f, cy - 16f, cx, cy + 2f, paint);
            canvas.drawOval(cx, cy - 16f, cx + 12f, cy + 2f, paint);
            paint.setColor(0x75FFFFFF);
            canvas.drawOval(cx - 4f, cy - 13f, cx, cy - 4f, paint);
            return;
        }
        float pulse = 1f + 0.035f * (float) Math.sin(time * 2.2f);
        canvas.save();
        canvas.scale(pulse, pulse, cx, cy - 9f);
        paint.setColor(flowerColor);
        for (int petal = 0; petal < 6; petal++) {
            double angle = petal * Math.PI / 3.0;
            float px = cx + (float) Math.cos(angle) * 14f;
            float py = cy - 9f + (float) Math.sin(angle) * 14f;
            canvas.drawCircle(px, py, 10f, paint);
        }
        paint.setColor(0xFFFFD36E);
        canvas.drawCircle(cx, cy - 9f, 9f, paint);
        paint.setColor(0x85FFFFFF);
        canvas.drawCircle(cx - 3f, cy - 12f, 2.5f, paint);
        canvas.restore();
    }

    private void drawGate(Canvas canvas, float cx, float cy, boolean open, float time) {
        if (open) {
            paint.setColor(0x35FFF4B0);
            canvas.drawCircle(cx, cy, 31f + (float) Math.sin(time * 2f) * 3f, paint);
        }
        paint.setColor(open ? 0xFF83A77A : 0xFF9A978F);
        canvas.drawRoundRect(cx - 27f, cy - 25f, cx - 21f, cy + 27f, 3f, 3f, paint);
        canvas.drawRoundRect(cx + 21f, cy - 25f, cx + 27f, cy + 27f, 3f, 3f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        canvas.drawRoundRect(cx - 22f, cy - 21f, cx + 22f, cy + 27f, 7f, 7f, paint);
        canvas.drawLine(cx - 20f, cy + 3f, cx + 20f, cy + 3f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(open ? 0xFFFFD97E : 0xFFD3C6A7);
        canvas.drawCircle(cx + 12f, cy + 12f, 3.5f, paint);
    }

    private void drawHedgehogAtOrigin(Canvas canvas, float time, float facing) {
        canvas.save();
        canvas.scale(facing < 0f ? -1f : 1f, 1f);
        float bounce = (float) Math.sin(time * 7f) * (world.getPathLength() > 0 ? 2.2f : 0.8f);
        canvas.translate(0f, bounce);

        paint.setColor(0x27625262);
        canvas.drawOval(-54f, 31f, 55f, 45f, paint);
        paint.setColor(0xFF71646D);
        path.reset();
        path.moveTo(-51f, 26f);
        path.lineTo(-63f, 6f);
        path.lineTo(-43f, 7f);
        path.lineTo(-51f, -18f);
        path.lineTo(-28f, -9f);
        path.lineTo(-24f, -34f);
        path.lineTo(-5f, -16f);
        path.lineTo(10f, -38f);
        path.lineTo(21f, -14f);
        path.quadTo(43f, -4f, 48f, 19f);
        path.quadTo(17f, 42f, -25f, 36f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFB19785);
        canvas.drawOval(-39f, -19f, 47f, 40f, paint);
        paint.setColor(0xFFC5AA95);
        path.reset();
        path.moveTo(11f, -12f);
        path.quadTo(53f, -12f, 67f, 13f);
        path.quadTo(56f, 33f, 28f, 34f);
        path.quadTo(8f, 23f, 11f, -12f);
        canvas.drawPath(path, paint);
        paint.setColor(0xFF675B61);
        canvas.drawCircle(41f, 3f, 4.7f, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(42.4f, 1.6f, 1.5f, paint);
        paint.setColor(0xFF4D454B);
        canvas.drawCircle(66f, 13f, 6.5f, paint);
        paint.setColor(0x79EF9F9D);
        canvas.drawCircle(47f, 18f, 6f, paint);
        paint.setColor(0xFF8D7568);
        canvas.drawOval(-19f, 31f, -2f, 41f, paint);
        canvas.drawOval(20f, 30f, 37f, 40f, paint);
        canvas.restore();
    }

    private void drawTarget(Canvas canvas, float time) {
        if (targetRow < 0 || world.getState() != GardenWorld.State.PLAYING) {
            return;
        }
        float cx = BOARD_LEFT + (targetCol + 0.5f) * TILE;
        float cy = BOARD_TOP + (targetRow + 0.5f) * TILE;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xA8FFF9E6);
        float radius = 17f + (float) Math.sin(time * 4f) * 3f;
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHud(Canvas canvas, float time) {
        GardenLevel level = world.getLevel();
        drawPill(canvas, 178f, 18f, 1104f, 86f, 0xDFFFFFF5, 0x20594F5E);
        drawFittedText(canvas, (world.getLevelIndex() + 1) + " / " + world.getLevelCount()
                        + "  ·  " + text(level.nameRes),
                640f, 60f, 27f, 430f, 0xFF625A67, true);

        drawDew(canvas, 224f, 52f, time);
        drawFittedText(canvas, text(R.string.dew_label) + "  " + world.getDewInventory()
                        + "  (" + world.getCollectedCount() + "/" + level.dewCount + ")",
                334f, 61f, 20f, 185f, 0xFF625A67, true);
        drawTinyFlower(canvas, 936f, 52f, level.flowerColor);
        drawFittedText(canvas, text(R.string.flowers_label) + "  "
                        + world.getBloomedCount() + "/" + level.flowerCount,
                1023f, 61f, 20f, 145f, 0xFF625A67, true);

        paint.setColor(0xEFFFFFF5);
        canvas.drawCircle(PAUSE_X, PAUSE_Y, 34f, paint);
        paint.setColor(0xFF716877);
        canvas.drawRoundRect(PAUSE_X - 9f, PAUSE_Y - 12f,
                PAUSE_X - 3f, PAUSE_Y + 12f, 3f, 3f, paint);
        canvas.drawRoundRect(PAUSE_X + 3f, PAUSE_Y - 12f,
                PAUSE_X + 9f, PAUSE_Y + 12f, 3f, 3f, paint);
    }

    private void drawHint(Canvas canvas, float alpha) {
        int a = Math.round(alpha * 220f);
        drawPill(canvas, 338f, 660f, 942f, 708f,
                Color.argb(a, 255, 251, 241), Color.argb(Math.round(alpha * 24f), 80, 70, 90));
        drawFittedText(canvas, text(R.string.tap_to_walk), 640f, 691f,
                19f, 555f, Color.argb(Math.round(alpha * 255f), 101, 91, 107), false);
    }

    private void drawOverlay(Canvas canvas, int titleRes, int subtitleRes, float time) {
        float eased = overlayProgress * overlayProgress * (3f - 2f * overlayProgress);
        paint.setColor(Color.argb(Math.round(120f * eased), 99, 91, 107));
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);

        float cardScale = 0.95f + 0.05f * eased;
        int layer = canvas.saveLayerAlpha(
                OVERLAY_LEFT - 12f, OVERLAY_TOP - 12f,
                OVERLAY_RIGHT + 16f, OVERLAY_BOTTOM + 20f,
                Math.round(255f * eased)
        );
        canvas.scale(cardScale, cardScale, 640f, 360f);
        paint.setColor(0x255B5260);
        canvas.drawRoundRect(OVERLAY_LEFT + 4f, OVERLAY_TOP + 7f,
                OVERLAY_RIGHT + 4f, OVERLAY_BOTTOM + 7f, 46f, 46f, paint);
        paint.setColor(0xF8FFF9EE);
        canvas.drawRoundRect(OVERLAY_LEFT, OVERLAY_TOP,
                OVERLAY_RIGHT, OVERLAY_BOTTOM, 46f, 46f, paint);

        float contentWidth = OVERLAY_RIGHT - OVERLAY_LEFT - OVERLAY_PADDING * 2f;
        float iconPulse = 1f + 0.03f * (float) Math.sin(time * 2.2f);
        canvas.save();
        canvas.translate(640f, 251f);
        canvas.scale(iconPulse, iconPulse);
        drawTinyFlower(canvas, 0f, 0f, world.getLevel().flowerColor);
        canvas.restore();
        drawFittedText(canvas, text(titleRes), 640f, 355f,
                39f, contentWidth, 0xFF625A69, true);
        drawFittedText(canvas, text(subtitleRes), 640f, 420f,
                22f, contentWidth, 0xFF817685, false);
        if (world.getState() == GardenWorld.State.PAUSED) {
            drawLanguageSwitch(canvas, 640f, 492f);
        }
        canvas.restoreToCount(layer);
    }

    private void drawJourneyComplete(Canvas canvas, float time) {
        paint.setColor(0x84635B6B);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        rect.set(278f, 123f, 1002f, 596f);
        paint.setColor(0xFAFFF9ED);
        canvas.drawRoundRect(rect, 48f, 48f, paint);
        canvas.save();
        canvas.translate(640f, 259f);
        canvas.scale(1.3f, 1.3f);
        drawHedgehogAtOrigin(canvas, time, 1f);
        canvas.restore();
        drawFittedText(canvas, text(R.string.journey_complete), 640f, 392f,
                42f, 620f, 0xFF605868, true);
        drawFittedText(canvas, text(R.string.journey_complete_subtitle), 640f, 444f,
                22f, 590f, 0xFF817686, false);
        drawPill(canvas, 454f, 480f, 826f, 548f, 0xFFFFE7A8, 0x245B5360);
        drawFittedText(canvas, text(R.string.play_again), 640f, 523f,
                23f, 325f, 0xFF665D6B, true);
    }

    private void drawLanguageSwitch(Canvas canvas, float cx, float cy) {
        drawPill(canvas, cx - 58f, cy - 27f, cx + 58f, cy + 27f,
                0xEFFFFFF4, 0x20584F60);
        paint.setColor(0xFF716877);
        paint.setStrokeWidth(2f);
        float selectedX = "en".equals(language) ? cx - 28f : cx + 28f;
        paint.setColor(0xFFFFD980);
        canvas.drawCircle(selectedX, cy, 21f, paint);
        drawFittedText(canvas, "EN", cx - 28f, cy + 7f, 17f, 36f, 0xFF615969, true);
        drawFittedText(canvas, "RU", cx + 28f, cy + 7f, 17f, 36f, 0xFF615969, true);
    }

    private void drawTitleFlowers(Canvas canvas, float time) {
        paint.setColor(0xFF89A87D);
        canvas.drawRect(0f, 611f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        int[] colors = {0xFFF3A9B9, 0xFFFFCE72, 0xFFB4A8DA, 0xFFF5C0A3};
        for (int i = 0; i < 18; i++) {
            float x = 24f + i * 75f;
            float y = 627f + Math.floorMod(i * 23, 53);
            drawFlower(canvas, x, y, true, colors[i % colors.length], time + i);
        }
    }

    private void drawSun(Canvas canvas, float cx, float cy) {
        paint.setColor(0x65FFF0A8);
        canvas.drawCircle(cx, cy, 78f, paint);
        paint.setColor(0xFFFFD985);
        canvas.drawCircle(cx, cy, 48f, paint);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float scale) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        paint.setColor(0x8AFFFDF4);
        canvas.drawCircle(-34f, 8f, 25f, paint);
        canvas.drawCircle(0f, -4f, 34f, paint);
        canvas.drawCircle(35f, 9f, 24f, paint);
        canvas.drawRoundRect(-58f, 6f, 58f, 34f, 17f, 17f, paint);
        canvas.restore();
    }

    private void drawHill(Canvas canvas, int color, float top, float amplitude, float frequency) {
        path.reset();
        path.moveTo(0f, WORLD_HEIGHT);
        path.lineTo(0f, top);
        for (int x = 0; x <= 1280; x += 32) {
            path.lineTo(x, top + (float) Math.sin(x * frequency) * amplitude);
        }
        path.lineTo(WORLD_WIDTH, WORLD_HEIGHT);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawTinyFlower(Canvas canvas, float cx, float cy, int color) {
        paint.setColor(color);
        for (int petal = 0; petal < 6; petal++) {
            double angle = petal * Math.PI / 3.0;
            canvas.drawCircle(cx + (float) Math.cos(angle) * 13f,
                    cy + (float) Math.sin(angle) * 13f, 9f, paint);
        }
        paint.setColor(0xFFFFD36E);
        canvas.drawCircle(cx, cy, 8f, paint);
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 5f, right + 3f, bottom + 5f,
                radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float baseline,
                                float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(preferredSize);
        float width = paint.measureText(value);
        if (width > maxWidth && width > 0f) {
            paint.setTextSize(preferredSize * maxWidth / width);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value, centerX, baseline, paint);
    }

    private void spawnParticles(float col, float row, int color, int count) {
        float x = BOARD_LEFT + (col + 0.5f) * TILE;
        float y = BOARD_TOP + (row + 0.5f) * TILE;
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 45f + random.nextFloat() * 80f;
            particles.add(new Particle(
                    x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 25f,
                    0.7f + random.nextFloat() * 0.55f,
                    3f + random.nextFloat() * 4f,
                    color
            ));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= dt;
            if (particle.life <= 0f) {
                particles.remove(i);
                continue;
            }
            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.velocityY += 34f * dt;
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.min(1f, particle.life * 2f);
            paint.setColor(Color.argb(Math.round(alpha * 255f),
                    Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color)));
            canvas.drawCircle(particle.x, particle.y, particle.radius * alpha, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || viewScale <= 0f) {
            return true;
        }
        float x = (event.getX() - viewOffsetX) / viewScale;
        float y = (event.getY() - viewOffsetY) / viewScale;
        performClick();
        GardenWorld.State state = world.getState();

        if (state == GardenWorld.State.TITLE) {
            if (isLanguageHit(x, y, 1167f, 56f)) {
                toggleLanguage();
            } else {
                world.startJourney(preferences.getInt(PREF_RESUME_LEVEL, 0));
                hintTime = 0f;
                targetRow = -1;
                rebuildLevelGradient();
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            }
        } else if (state == GardenWorld.State.PLAYING) {
            if (distance(x, y, PAUSE_X, PAUSE_Y) <= 43f) {
                world.pause();
            } else {
                int col = (int) ((x - BOARD_LEFT) / TILE);
                int row = (int) ((y - BOARD_TOP) / TILE);
                if (x >= BOARD_LEFT && y >= BOARD_TOP
                        && col >= 0 && col < GardenLevel.COLS
                        && row >= 0 && row < GardenLevel.ROWS
                        && world.tapCell(row, col)) {
                    targetRow = row;
                    targetCol = col;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
        } else if (state == GardenWorld.State.PAUSED) {
            if (isLanguageHit(x, y, 640f, 492f)) {
                toggleLanguage();
            } else {
                world.resume();
            }
        } else if (state == GardenWorld.State.LEVEL_COMPLETE) {
            world.continueAfterLevel();
            targetRow = -1;
            hintTime = 0f;
            rebuildLevelGradient();
        } else if (state == GardenWorld.State.JOURNEY_COMPLETE) {
            preferences.edit().putInt(PREF_RESUME_LEVEL, 0).apply();
            world.restartJourney();
            targetRow = -1;
            hintTime = 0f;
            rebuildLevelGradient();
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean isLanguageHit(float x, float y, float centerX, float centerY) {
        return Math.abs(x - centerX) <= 70f && Math.abs(y - centerY) <= 42f;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private void toggleLanguage() {
        language = "en".equals(language) ? "ru" : "en";
        preferences.edit().putString(PREF_LANGUAGE, language).apply();
        applyLanguage(language);
        setContentDescription(text(R.string.accessibility_game));
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        invalidate();
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }

    private void rebuildLevelGradient() {
        GardenLevel level = world.getLevel();
        levelGradient = new LinearGradient(0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{level.backgroundTop, level.backgroundBottom}, null, Shader.TileMode.CLAMP);
    }

    @Override
    public void onDewCollected(float col, float row) {
        audio.playDew(world.getCollectedCount());
        spawnParticles(col, row, 0xFFB9F1F4, 12);
        announceForAccessibility(text(R.string.accessibility_dew_found));
    }

    @Override
    public void onFlowerBloomed(float col, float row) {
        audio.playBloom(world.getBloomedCount());
        spawnParticles(col, row, world.getLevel().flowerColor, 18);
        announceForAccessibility(text(R.string.accessibility_flower_bloomed));
    }

    @Override
    public void onLevelComplete(int completedLevel) {
        int resume = Math.min(completedLevel + 1, world.getLevelCount() - 1);
        preferences.edit().putInt(PREF_RESUME_LEVEL, resume).apply();
        audio.playLevelComplete();
        targetRow = -1;
        announceForAccessibility(text(R.string.accessibility_level_complete));
    }

    @Override
    public void onJourneyComplete() {
        audio.playJourneyComplete();
    }

    boolean handleBack() {
        GardenWorld.State state = world.getState();
        if (state == GardenWorld.State.PLAYING) {
            world.pause();
            invalidate();
            return true;
        }
        if (state == GardenWorld.State.PAUSED
                || state == GardenWorld.State.LEVEL_COMPLETE
                || state == GardenWorld.State.JOURNEY_COMPLETE) {
            world.showTitle();
            targetRow = -1;
            invalidate();
            return true;
        }
        return false;
    }

    void onHostPause() {
        hostResumed = false;
        lastFrameNanos = 0L;
        world.pause();
    }

    void onHostResume() {
        hostResumed = true;
        lastFrameNanos = 0L;
        invalidate();
    }

    void close() {
        hostResumed = false;
        audio.close();
    }

    private static int lighten(int color, float amount) {
        int red = Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount));
        int green = Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount));
        int blue = Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount));
        return Color.rgb(red, green, blue);
    }

    private static int darken(int color, float factor) {
        return Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private static final class Particle {
        float x;
        float y;
        final float velocityX;
        float velocityY;
        float life;
        final float radius;
        final int color;

        Particle(float x, float y, float velocityX, float velocityY,
                 float life, float radius, int color) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.radius = radius;
            this.color = color;
        }
    }
}
