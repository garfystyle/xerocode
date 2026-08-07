package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketId {
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_IN = Base64.getUrlDecoder();
    private static final Base64.Encoder PLAIN = Base64.getEncoder();
    private static final Base64.Decoder PLAIN_IN = Base64.getDecoder();

    private static PrivateKey secret;
    private static byte[] pub;
    private static String account = "";
    private static String work = "";
    private static boolean read;

    public static synchronized boolean have() {
        load();
        return secret != null;
    }

    public static synchronized String account() {
        load();
        return account;
    }

    private static Path home;

    public static synchronized void home(Path dir) {
        home = dir;
        read = false;
        secret = null;
        pub = null;
        account = "";
        work = "";
    }

    public static Path file() {
        Path base = home != null ? home : MinecraftClient.getInstance().runDirectory.toPath();
        return base.resolve("xerocode/market-id.json");
    }

    private static void load() {
        if (read) return;
        read = true;
        Path path = file();
        if (!Files.exists(path)) return;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            byte[] pkcs8 = PLAIN_IN.decode(o.get("key").getAsString());
            secret = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            pub = URL_IN.decode(o.get("pub").getAsString());
            work = o.has("work") ? o.get("work").getAsString() : "";
            account = idOf(pub);
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] ключ магазина не читается", e);
            secret = null;
            pub = null;
        }
    }

    public static synchronized void create(int bits) throws Exception {
        load();
        if (secret != null) return;
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        store(pair.getPrivate(), raw, pair.getPrivate().getEncoded(), mine(raw, bits));
    }

    private static void store(PrivateKey key, byte[] raw, byte[] pkcs8, String found)
            throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("pub", URL.encodeToString(raw));
        o.addProperty("key", PLAIN.encodeToString(pkcs8));
        o.addProperty("work", found);
        o.addProperty("about", ABOUT);
        Path path = file();
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(o.toString());
        }
        secret = key;
        pub = raw;
        work = found;
        account = idOf(raw);
        read = true;
    }

    public static synchronized String work() {
        load();
        return work;
    }

    private static String mine(byte[] key, int bits) throws Exception {
        if (bits <= 0) return "0";
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] tag = "xerocode-market-pow-v1".getBytes(StandardCharsets.US_ASCII);
        for (long n = 0; n < (1L << 40); n++) {
            sha.reset();
            sha.update(tag);
            sha.update(key);
            sha.update(Long.toString(n).getBytes(StandardCharsets.US_ASCII));
            byte[] out = sha.digest();
            int head = ((out[0] & 0xFF) << 24) | ((out[1] & 0xFF) << 16)
                    | ((out[2] & 0xFF) << 8) | (out[3] & 0xFF);
            if ((head >>> (32 - bits)) == 0) return Long.toString(n);
        }
        throw new IllegalStateException("работа над ключом не сошлась");
    }

    private static String idOf(byte[] key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("xerocode-market-account-v1".getBytes(StandardCharsets.US_ASCII));
            sha.update(key);
            return Hex.of(sha.digest()).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static synchronized Map<String, String> headers(String op, byte[] body) {
        load();
        if (secret == null) return Map.of();
        try {
            long stamp = System.currentTimeMillis() / 1000L;
            byte[] fresh = new byte[12];
            new java.security.SecureRandom().nextBytes(fresh);
            String nonce = URL.encodeToString(fresh);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String digest = Hex.of(sha.digest(body));
            String message = "xerocode-market-v1\n" + op + "\n" + stamp + "\n" + nonce
                    + "\n" + digest;
            Signature sign = Signature.getInstance("Ed25519");
            sign.initSign(secret);
            sign.update(message.getBytes(StandardCharsets.UTF_8));
            Map<String, String> out = new LinkedHashMap<>();
            out.put("X-Acc", account);
            out.put("X-Pub", URL.encodeToString(pub));
            out.put("X-Ts", Long.toString(stamp));
            out.put("X-Nonce", nonce);
            out.put("X-Sig", URL.encodeToString(sign.sign()));
            return out;
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не вышло подписать обращение к магазину", e);
            return Map.of();
        }
    }

    public static synchronized String export() {
        load();
        if (secret == null) return "";
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("pub", URL.encodeToString(pub));
        o.addProperty("key", PLAIN.encodeToString(secret.getEncoded()));
        o.addProperty("work", work);
        return "xc1:" + URL.encodeToString(o.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized boolean adopt(String text) {
        if (text == null) return false;
        String body = text.trim().replaceAll("\\s", "");
        if (!body.startsWith("xc1:")) return false;
        try {
            String json = new String(URL_IN.decode(body.substring(4)), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            byte[] pkcs8 = PLAIN_IN.decode(o.get("key").getAsString());
            PrivateKey key = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            byte[] raw = URL_IN.decode(o.get("pub").getAsString());
            if (raw.length != 32) return false;
            store(key, raw, pkcs8, Json.str(o, "work"));
            return true;
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] ключ магазина не принят", e);
            return false;
        }
    }

    private static final String ABOUT = "Это ключ аккаунта магазина модулей. Он же пароль: "
            + "перенеси эту строку — вернёшь аккаунт на другом компьютере.";

    public static synchronized void forget() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception ignored) {
        }
        secret = null;
        pub = null;
        account = "";
        work = "";
        read = false;
    }

    private MarketId() {}
}
