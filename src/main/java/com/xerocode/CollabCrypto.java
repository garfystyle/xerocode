package com.xerocode;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CollabCrypto {
    public static final int SECRET_BYTES = 16;
    private static final int NONCE = 12;
    private static final int SQUEEZE_FROM = 256;
    private static final int TAG = 128;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder PLAIN = Base64.getEncoder();
    private static final Base64.Decoder PLAIN_IN = Base64.getDecoder();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_IN = Base64.getUrlDecoder();

    public static byte[] freshSecret() {
        byte[] out = new byte[SECRET_BYTES];
        RANDOM.nextBytes(out);
        return out;
    }

    public static String code(byte[] secret) { return URL.encodeToString(secret); }

    public static byte[] fromCode(String code) {
        if (code == null) return null;
        String clean = code.replaceAll("\\s", "");
        if (clean.isEmpty()) return null;
        try {
            byte[] raw = URL_IN.decode(clean);
            return raw.length == SECRET_BYTES ? raw : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] derive(String tag, byte[] secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(tag.getBytes(StandardCharsets.US_ASCII));
            sha.update(secret);
            return sha.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String roomId(byte[] secret) {
        return Hex.of(derive("xerocode-room-v1", secret)).substring(0, 24);
    }

    public static byte[] authKey(byte[] secret) { return derive("xerocode-auth-v1", secret); }

    public static byte[] encKey(byte[] secret) { return derive("xerocode-key-v1", secret); }

    public static String proof(byte[] authKey, String room, String nonce) {
        return Hex.of(mac(authKey, room + "|" + nonce));
    }

    public static String slot(byte[] encKey, String rootId) {
        return Hex.of(mac(encKey, "slot|" + rootId)).substring(0, 16);
    }

    private static byte[] mac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String seal(byte[] key, String text, String aad) {
        try {
            byte[] body = pack(text.getBytes(StandardCharsets.UTF_8));
            byte[] nonce = new byte[NONCE];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(body);
            byte[] out = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
            return PLAIN.encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String open(byte[] key, String sealed, String aad) {
        try {
            byte[] raw = PLAIN_IN.decode(sealed);
            if (raw.length <= NONCE) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG, raw, 0, NONCE));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] body = cipher.doFinal(raw, NONCE, raw.length - NONCE);
            return new String(unpack(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] pack(byte[] raw) {
        boolean small = raw.length < SQUEEZE_FROM;
        byte[] body = small ? raw : squeeze(raw);
        byte[] out = new byte[body.length + 1];
        out[0] = (byte) (small ? 0 : 1);
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    private static byte[] unpack(byte[] body) throws Exception {
        if (body.length == 0) return body;
        byte[] rest = new byte[body.length - 1];
        System.arraycopy(body, 1, rest, 0, rest.length);
        return body[0] == 0 ? rest : swell(rest);
    }

    private static byte[] squeeze(byte[] raw) {
        Deflater z = new Deflater(Deflater.BEST_SPEED, true);
        try {
            z.setInput(raw);
            z.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
            byte[] buffer = new byte[8192];
            while (!z.finished()) out.write(buffer, 0, z.deflate(buffer));
            return out.toByteArray();
        } finally {
            z.end();
        }
    }

    private static byte[] swell(byte[] raw) throws Exception {
        Inflater z = new Inflater(true);
        try {
            z.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length * 3));
            byte[] buffer = new byte[8192];
            while (!z.finished()) {
                int n = z.inflate(buffer);
                if (n == 0 && (z.needsInput() || z.needsDictionary())) break;
                out.write(buffer, 0, n);
                if (out.size() > 64 << 20) throw new IllegalStateException("слишком много данных");
            }
            return out.toByteArray();
        } finally {
            z.end();
        }
    }

    private CollabCrypto() {}
}
