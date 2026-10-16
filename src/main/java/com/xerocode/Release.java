package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Release {
    public record Entry(String version, String date, String notes, String page, String jar, long size) {}

    public record News(String version, List<Entry> entries) {
        public Entry newest() { return entries.get(0); }
    }

    private static final String API = "https://api.github.com/repos/garfystyle/xerocode/releases?per_page=30";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final long EVERY = 30 * 60 * 1000L;
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)*");
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private static volatile News news;
    private static volatile boolean asking;
    private static volatile long askedAt;

    private Release() {}

    public static String current() { return Swap.version(); }

    public static News news() { return news; }

    public static void check() {
        long now = System.currentTimeMillis();
        if (asking || (askedAt != 0 && now - askedAt < EVERY)) return;
        asking = true;
        askedAt = now;
        Net.threads("xerocode-release").newThread(() -> {
            try {
                ask();
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] не удалось узнать о новой версии: {}", e.toString());
            } finally {
                asking = false;
            }
        }).start();
    }

    private static void ask() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "xerocode/" + current())
                .GET()
                .build();
        HttpResponse<String> answer = Net.client(TIMEOUT, true)
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (answer.statusCode() != 200) {
            XeroCode.LOG.warn("[xerocode] GitHub ответил {} на вопрос о новой версии", answer.statusCode());
            return;
        }
        List<Entry> fresh = newer(JsonParser.parseString(answer.body()).getAsJsonArray(), current());
        news = fresh.isEmpty() ? null : new News(fresh.get(0).version(), List.copyOf(fresh));
    }

    public static List<Entry> newer(JsonArray releases, String current) {
        List<Entry> out = new ArrayList<>();
        for (JsonElement el : releases) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            if (Json.flag(r, "draft") || Json.flag(r, "prerelease")) continue;
            String version = number(Json.str(r, "tag_name"));
            if (version.isEmpty() || compare(version, current) <= 0) continue;
            String jar = "";
            long size = 0;
            JsonArray assets = Json.arr(r, "assets");
            if (assets != null) for (JsonElement a : assets) {
                if (!a.isJsonObject()) continue;
                JsonObject asset = a.getAsJsonObject();
                if (!Json.str(asset, "name").endsWith(".jar")) continue;
                jar = Json.str(asset, "browser_download_url");
                size = Json.big(asset, "size");
                break;
            }
            out.add(new Entry(version, Json.str(r, "published_at"), Json.str(r, "body"),
                    Json.str(r, "html_url"), jar, size));
        }
        out.sort(Comparator.comparing(Entry::version, Release::compare).reversed());
        return out;
    }

    public static String number(String tag) {
        Matcher m = NUMBER.matcher(tag == null ? "" : tag);
        return m.find() ? m.group() : "";
    }

    public static int compare(String a, String b) {
        String[] x = number(a).split("\\."), y = number(b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = part(x, i), q = part(y, i);
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static long part(String[] parts, int i) {
        if (i >= parts.length || parts[i].isEmpty()) return 0;
        try {
            return Long.parseLong(parts[i]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String day(String iso, int thisYear) {
        if (iso == null || iso.length() < 10) return "";
        try {
            int year = Integer.parseInt(iso.substring(0, 4));
            int month = Integer.parseInt(iso.substring(5, 7));
            int day = Integer.parseInt(iso.substring(8, 10));
            if (month < 1 || month > 12) return "";
            return day + " " + MONTHS[month - 1] + (year == thisYear ? "" : " " + year);
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
