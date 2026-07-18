/*
 * Hedgehog Garden
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.hedgehoggarden;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Gentle procedural chimes; the game bundles no audio files or codecs. */
final class AudioEngine {
    private static final int SAMPLE_RATE = 22_050;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hedgehog-chime");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    void playDew(int number) {
        float base = 587.33f * (1f + number * 0.08f);
        play(new float[]{base, base * 1.34f}, 0.24f, 0.12f);
    }

    void playBloom(int number) {
        float base = 440f * (1f + number * 0.09f);
        play(new float[]{base, base * 1.25f, base * 1.5f}, 0.42f, 0.13f);
    }

    void playLevelComplete() {
        play(new float[]{523.25f, 659.25f, 783.99f}, 0.56f, 0.14f);
    }

    void playJourneyComplete() {
        play(new float[]{392f, 523.25f, 659.25f, 783.99f}, 0.82f, 0.13f);
    }

    private void play(float[] frequencies, float durationSeconds, float volume) {
        if (closed) {
            return;
        }
        try {
            executor.execute(() -> synthesizeAndPlay(frequencies, durationSeconds, volume));
        } catch (RejectedExecutionException ignored) {
            // The optional sound was queued during activity shutdown.
        }
    }

    private void synthesizeAndPlay(float[] frequencies, float durationSeconds, float volume) {
        int sampleCount = Math.max(256, (int) (SAMPLE_RATE * durationSeconds));
        short[] pcm = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / (double) SAMPLE_RATE;
            double progress = i / (double) sampleCount;
            double attack = Math.min(1.0, progress / 0.05);
            double release = Math.pow(Math.max(0.0, 1.0 - progress), 2.5);
            double value = 0.0;
            for (int note = 0; note < frequencies.length; note++) {
                double stagger = note * 0.052;
                if (time >= stagger) {
                    double localTime = time - stagger;
                    value += Math.sin(Math.PI * 2.0 * frequencies[note] * localTime)
                            + 0.18 * Math.sin(Math.PI * 4.0 * frequencies[note] * localTime);
                }
            }
            value /= frequencies.length * 1.18;
            pcm[i] = (short) (Short.MAX_VALUE * volume * attack * release * value);
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED
                    || track.write(pcm, 0, pcm.length) < 0) {
                return;
            }
            track.play();
            Thread.sleep((long) (durationSeconds * 1000f) + 40L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Audio is optional and must never interrupt play on unusual vendor stacks.
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (IllegalStateException ignored) {
                    // Some devices invalidate an AudioTrack while the app is closing.
                }
                track.release();
            }
        }
    }

    void close() {
        closed = true;
        executor.shutdownNow();
    }
}
