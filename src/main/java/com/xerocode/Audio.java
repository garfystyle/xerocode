package com.xerocode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public final class Audio {
    public enum State { EMPTY, LOADING, READY, MISSING }

    private static final long SETTLE_MS = 120;

    private static String wanted = "";
    private static long wantedAt;
    private static String loaded = "";
    private static State state = State.EMPTY;

    private static CompletableFuture<Pcm> job;
    private static String jobFor = "";

    private static int source, buffer;
    private static double duration;
    private static double head;
    private static boolean running;
    private static boolean loop;
    private static float gain = 1, pitch = 1;
    private static boolean broken;

    private record Pcm(ByteBuffer data, int format, int rate, double seconds) {}

    public static void want(String soundId) {
        String id = soundId == null ? "" : soundId;
        if (id.equals(wanted)) return;
        wanted = id;
        wantedAt = System.currentTimeMillis();
        if (id.equals(loaded)) return;
        free();
        state = id.isEmpty() ? State.EMPTY : State.LOADING;
    }

    public static State state() { return state; }

    public static boolean ready() { return state == State.READY && source != 0; }

    public static double duration() { return duration; }

    public static void tick() {
        if (broken) return;
        if (job == null && state == State.LOADING && !wanted.isEmpty()
                && System.currentTimeMillis() - wantedAt >= SETTLE_MS)
            start(wanted);
        if (job != null && job.isDone()) {
            Pcm pcm = job.getNow(null);
            String id = jobFor;
            job = null;
            jobFor = "";
            if (id.equals(wanted)) {
                if (pcm == null) state = State.MISSING;
                else upload(pcm, id);
            }
        }
        if (!ready()) return;
        boolean now = sourceState() == AL10.AL_PLAYING;
        if (running && !now && sourceState() != AL10.AL_PAUSED) head = 0;
        running = now;
    }

    public static void play() {
        if (!ready() || playing()) return;
        boolean resuming = sourceState() == AL10.AL_PAUSED;
        AL10.alSourcePlay(source);
        if (!resuming && head > 0.001) offset(head);
        running = true;
    }

    public static void pause() {
        if (ready() && playing()) {
            head = position();
            AL10.alSourcePause(source);
            running = false;
        }
    }

    public static void toggle() {
        if (playing()) pause(); else play();
    }

    public static void stop() {
        if (!ready()) return;
        AL10.alSourceStop(source);
        head = 0;
        running = false;
    }

    public static void rewind() {
        if (!ready()) return;
        boolean was = playing();
        AL10.alSourceStop(source);
        head = 0;
        running = false;
        if (was) play();
    }

    public static boolean playing() {
        return ready() && sourceState() == AL10.AL_PLAYING;
    }

    public static double position() {
        if (!ready()) return 0;
        int st = sourceState();
        if (st == AL10.AL_PLAYING || st == AL10.AL_PAUSED)
            return Math.min(duration, AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET));
        return head;
    }

    public static void seek(double seconds) {
        head = Math.max(0, Math.min(Math.max(0, duration - 0.02), seconds));
        if (!ready()) return;
        int st = sourceState();
        if (st == AL10.AL_PLAYING || st == AL10.AL_PAUSED) offset(head);
    }

    public static boolean loop() { return loop; }

    public static void setLoop(boolean on) {
        loop = on;
        if (ready()) AL10.alSourcei(source, AL10.AL_LOOPING, on ? AL10.AL_TRUE : AL10.AL_FALSE);
    }

    public static void mix(double volume, double pitchValue, String channel) {
        MinecraftClient client = MinecraftClient.getInstance();
        float slider = 1;
        if (client.options != null) {
            SoundCategory category;
            try {
                category = SoundCategory.valueOf(channel == null ? "MASTER" : channel);
            } catch (IllegalArgumentException e) {
                category = SoundCategory.MASTER;
            }
            slider = client.options.getSoundVolume(category);
        }
        float g = (float) Math.max(0, Math.min(2, volume)) * slider;
        float p = (float) Math.max(0.5, Math.min(2, pitchValue));
        if (Math.abs(g - gain) > 0.001f) {
            gain = g;
            if (ready()) AL10.alSourcef(source, AL10.AL_GAIN, gain);
        }
        if (Math.abs(p - pitch) > 0.001f) {
            pitch = p;
            if (ready()) AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        }
    }

    public static boolean muted() { return gain <= 0.001f; }

    public static boolean broken() { return broken; }

    private static void start(String id) {
        Identifier file = fileOf(id);
        if (file == null) { state = State.MISSING; return; }
        jobFor = id;
        job = CompletableFuture.supplyAsync(() -> decode(file), Util.getMainWorkerExecutor());
    }

    private static Identifier fileOf(String soundId) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier ident = Identifier.tryParse(soundId);
        if (ident == null || client.getSoundManager() == null) return null;
        WeightedSoundSet set = client.getSoundManager().get(ident);
        if (set == null) return null;
        Sound sound = set.getSound(Random.create());
        if (sound != null && sound.getRegistrationType() == Sound.RegistrationType.SOUND_EVENT) {
            WeightedSoundSet inner = client.getSoundManager().get(sound.getIdentifier());
            sound = inner == null ? null : inner.getSound(Random.create());
        }
        if (sound == null || sound == SoundManager.MISSING_SOUND) return null;
        return sound.getLocation();
    }

    private static Pcm decode(Identifier file) {
        MinecraftClient client = MinecraftClient.getInstance();
        try (InputStream in = client.getResourceManager().open(file);
             OggAudioStream ogg = new OggAudioStream(in)) {
            AudioFormat format = ogg.getFormat();
            ByteBuffer data = ogg.readAll();
            int channels = Math.max(1, format.getChannels());
            int bytes = Math.max(1, format.getSampleSizeInBits() / 8);
            int rate = (int) format.getSampleRate();
            int id = channels == 1
                    ? (bytes == 1 ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16)
                    : (bytes == 1 ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16);
            double seconds = data.remaining() / (double) (channels * bytes) / Math.max(1, rate);
            return new Pcm(data, id, rate, seconds);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] cannot read {} for the preview: {}", file, e.toString());
            return null;
        }
    }

    private static void upload(Pcm pcm, String id) {
        try {
            AL10.alGetError();
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, pcm.format(), pcm.data(), pcm.rate());
            source = AL10.alGenSources();
            if (AL10.alGetError() != AL10.AL_NO_ERROR) { free(); state = State.MISSING; return; }
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcei(source, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
            AL10.alSourcef(source, AL10.AL_GAIN, gain);
            AL10.alSourcef(source, AL10.AL_PITCH, pitch);
            duration = pcm.seconds();
            head = 0;
            running = false;
            loaded = id;
            state = State.READY;
        } catch (Throwable t) {
            XeroCode.LOG.warn("[xerocode] OpenAL refused the sound preview", t);
            broken = true;
            free();
            state = State.MISSING;
        }
    }

    private static void free() {
        try {
            if (source != 0) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            if (buffer != 0) AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {
        }
        source = 0;
        buffer = 0;
        duration = 0;
        head = 0;
        running = false;
        loaded = "";
    }

    public static void release() {
        job = null;
        jobFor = "";
        wanted = "";
        state = State.EMPTY;
        free();
    }

    private static int sourceState() {
        return source == 0 ? AL10.AL_STOPPED : AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
    }

    private static void offset(double seconds) {
        AL10.alSourcef(source, AL11.AL_SEC_OFFSET, (float) seconds);
    }

    private Audio() {}
}
