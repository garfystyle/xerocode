package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.client.Minecraft;

public final class Swap {
    public enum Phase { IDLE, LOADING, FAILED, RESTART }

    private static final String LOADER = "xerocode-swap";
    private static final String STUB = "xerocode-replaced.txt";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final long MAX = 64L << 20;
    private static final Net.Who WHO = new Net.Who("GitHub не найден — нет сети?",
            "GitHub не отвечает", "GitHub не ответил за минуту — похоже, мешает VPN или прокси");
    private static final String BROKE = "связь оборвалась посреди загрузки — попробуй ещё раз";

    private static volatile Phase phase = Phase.IDLE;
    private static volatile long got, total;
    private static volatile String said = "";
    private static volatile boolean cancelled;
    private static String version;

    private Swap() {}

    public static Phase phase() { return phase; }

    public static long got() { return got; }

    public static long total() { return total; }

    public static String said() { return said; }

    public static void reset() {
        if (phase != Phase.LOADING) phase = Phase.IDLE;
    }

    public static boolean swapped() { return LOADER.equals(Swap.class.getClassLoader().getName()); }

    public static String version() {
        if (version != null) return version;
        String found = "";
        try {
            if (swapped()) {
                try (InputStream in = Swap.class.getClassLoader().getResourceAsStream("fabric.mod.json")) {
                    if (in != null) found = Json.str(JsonParser.parseReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(), "version");
                }
            } else {
                found = FabricLoader.getInstance().getModContainer("xerocode")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
            }
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] не удалось узнать свою версию", e);
        }
        version = found;
        return found;
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/update");
    }

    public static void clean() {
        if (swapped()) return;
        Path dir = folder();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] не удалось убрать старые обновления", e);
            }
        }
        Path mods = modsDir();
        if (mods != null) sweep(mods);
    }

    static void sweep(Path mods) {
        for (Path file : jars(mods)) {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                if (zip.getEntry(STUB) == null || zip.getEntry("fabric.mod.json") != null) continue;
            } catch (Throwable e) {
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] не удалось убрать заменённый jar {}", file.getFileName());
            }
        }
    }

    private static List<Path> jars(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".jar")
                    && Files.isRegularFile(f)).toList();
        } catch (Throwable e) {
            return List.of();
        }
    }

    private static boolean ours(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry meta = zip.getEntry("fabric.mod.json");
            if (meta == null) return false;
            try (Reader r = new InputStreamReader(zip.getInputStream(meta), StandardCharsets.UTF_8)) {
                return "xerocode".equals(Json.str(JsonParser.parseReader(r).getAsJsonObject(), "id"));
            }
        } catch (Throwable e) {
            return false;
        }
    }

    private static byte[] stub() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(STUB));
            zip.write(("Этот файл заменён новой версией XeroCode и удалится при следующем запуске игры.\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    public static void start(Release.Entry entry) {
        if (phase == Phase.LOADING) return;
        if (entry.jar().isEmpty()) {
            fail("у этой версии на GitHub нет jar-файла");
            return;
        }
        phase = Phase.LOADING;
        got = 0;
        total = entry.size();
        said = "";
        cancelled = false;
        Net.threads("xerocode-swap").newThread(() -> fetch(entry)).start();
    }

    public static void cancel() { cancelled = true; }

    private static void fail(String why) {
        said = why;
        phase = Phase.FAILED;
    }

    private static void fetch(Release.Entry entry) {
        Path dir = folder();
        Path part = dir.resolve("xerocode-" + entry.version() + ".jar.part");
        Path jar = dir.resolve("xerocode-" + entry.version() + ".jar");
        try {
            Files.createDirectories(dir);
            HttpRequest request = HttpRequest.newBuilder(URI.create(entry.jar()))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "xerocode/" + version())
                    .GET()
                    .build();
            HttpResponse<InputStream> answer = Net.client(TIMEOUT, true)
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (answer.statusCode() != 200) {
                answer.body().close();
                fail("GitHub ответил " + answer.statusCode());
                return;
            }
            long length = answer.headers().firstValueAsLong("Content-Length").orElse(entry.size());
            if (length > 0) total = length;
            try (InputStream in = answer.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[1 << 14];
                for (int n; (n = in.read(buf)) > 0 && !cancelled && got <= MAX; ) {
                    out.write(buf, 0, n);
                    got += n;
                }
            }
            if (cancelled) {
                Files.deleteIfExists(part);
                phase = Phase.IDLE;
                return;
            }
            if (got > MAX || (total > 0 && got != total)) {
                Files.deleteIfExists(part);
                fail(got > MAX ? "файл больше 64 МБ — это не XeroCode" : BROKE);
                return;
            }
            Check check = check(part, entry.version());
            if (check.why != null) {
                Files.deleteIfExists(part);
                fail(check.why);
                return;
            }
            Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
            Minecraft.getInstance().execute(() -> {
                if (check.restart) installOnly(jar);
                else apply(jar);
            });
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] обновление не скачалось", e);
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
            fail(got > 0 ? BROKE : Net.reason(e, WHO));
        }
    }

    private record Check(String why, boolean restart) {}

    private static Check check(Path jar, String wanted) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry meta = zip.getEntry("fabric.mod.json");
            if (meta == null) return new Check("скачался не мод Fabric", false);
            JsonObject json;
            try (Reader r = new InputStreamReader(zip.getInputStream(meta), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (!"xerocode".equals(Json.str(json, "id"))) return new Check("скачался не XeroCode", false);
            String inside = Json.str(json, "version");
            if (!wanted.equals(inside))
                return new Check("в файле версия " + inside + ", а не " + wanted, false);
            if (zip.getEntry("com/xerocode/XeroCode.class") == null)
                return new Check("в файле нет кода мода", false);
            String game = gameVersion();
            for (String need : minecraft(json))
                if (!fits(need, game)) return new Check("эта версия для Minecraft " + need
                        + ", а игра — " + game + "; скачай подходящую со страницы", false);
            boolean restart = json.has("mixins") || json.has("accessWidener")
                    || json.has("classTweaker");
            return new Check(null, restart);
        }
    }

    private static List<String> minecraft(JsonObject json) {
        List<String> out = new ArrayList<>();
        if (!json.has("depends") || !json.get("depends").isJsonObject()) return out;
        JsonElement need = json.getAsJsonObject("depends").get("minecraft");
        if (need == null) return out;
        if (need.isJsonPrimitive()) out.add(need.getAsString());
        if (need.isJsonArray()) {
            JsonArray any = need.getAsJsonArray();
            StringBuilder joined = new StringBuilder();
            for (JsonElement e : any) joined.append(joined.isEmpty() ? "" : " ").append(e.getAsString());
            if (!joined.isEmpty()) out.add(joined.toString());
        }
        return out;
    }

    private static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    private static boolean fits(String need, String game) {
        try {
            Version have = Version.parse(game);
            for (String one : need.split(" "))
                if (!one.isBlank() && VersionPredicate.parse(one).test(have)) return true;
            return false;
        } catch (Throwable e) {
            return true;
        }
    }

    private static void installOnly(Path jar) {
        String trouble = install(jar);
        if (!trouble.isEmpty()) {
            fail(trouble);
            return;
        }
        said = "новая версия установлена и заработает после перезапуска игры";
        phase = Phase.RESTART;
    }

    private static void apply(Path jar) {
        Minecraft mc = Minecraft.getInstance();
        String was = version();
        Loader loader = null;
        Class<?> main;
        ClientModInitializer next;
        try {
            loader = new Loader(jar);
            loader.preload();
            main = Class.forName("com.xerocode.XeroCode", false, loader);
            next = (ClientModInitializer) main.getConstructor().newInstance();
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] новая версия не загрузилась", e);
            close(loader);
            fail("новая версия не загрузилась: " + shortly(e));
            return;
        }
        boolean editor = mc.gui.screen() != null;
        XeroCode.retire();
        try {
            next.onInitializeClient();
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] новая версия не запустилась", e);
            call(main, "retire");
            XeroCode.revive();
            close(loader);
            fail("новая версия не запустилась: " + shortly(e));
            return;
        }
        phase = Phase.IDLE;
        mc.gui.setScreen(null);
        String trouble = install(jar);
        if (!call(main, "resumed", was, trouble, editor))
            XeroCode.LOG.warn("[xerocode] новая версия не открыла редактор сама");
    }

    private static void close(Loader loader) {
        if (loader == null) return;
        try {
            loader.close();
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] загрузчик новой версии не закрылся", e);
        }
    }

    private static boolean call(Class<?> main, String name, Object... args) {
        try {
            for (Method m : main.getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                m.invoke(null, args);
                return true;
            }
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] новая версия не ответила на " + name, e);
        }
        return false;
    }

    private static String shortly(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String text = root.getClass().getSimpleName()
                + (root.getMessage() == null ? "" : ": " + root.getMessage());
        return text.length() <= 90 ? text : text.substring(0, 90) + "…";
    }

    private static String install(Path jar) {
        Path mods = modsDir();
        if (mods == null) return "мод запущен не из jar — в mods ничего не записано";
        return place(jar, mods);
    }

    static String place(Path jar, Path mods) {
        Path fresh = mods.resolve(jar.getFileName().toString());
        boolean existed = Files.exists(fresh);
        try {
            Files.write(fresh, Files.readAllBytes(jar));
            byte[] stub = null;
            for (Path other : jars(mods)) {
                if (other.equals(fresh) || !ours(other)) continue;
                try {
                    Files.delete(other);
                } catch (IOException busy) {
                    if (stub == null) stub = stub();
                    Files.write(other, stub);
                }
            }
            return "";
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] новая версия не записалась в mods", e);
            if (!existed) {
                try {
                    Files.deleteIfExists(fresh);
                } catch (IOException ignored) {
                }
            }
            return "в mods не записалось — после перезапуска игры вернётся прежняя версия";
        }
    }

    private static Path modsDir() {
        ModContainer mod = FabricLoader.getInstance().getModContainer("xerocode").orElse(null);
        if (mod == null || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
        for (Path p : mod.getOrigin().getPaths())
            if (p.getFileName() != null && p.getFileName().toString().endsWith(".jar")) return p.getParent();
        return null;
    }

    private static final class Loader extends URLClassLoader {
        private static final String OWN = "com.xerocode.";

        private final Path jar;

        Loader(Path jar) throws IOException {
            super(LOADER, new URL[]{jar.toUri().toURL()}, Minecraft.class.getClassLoader());
            this.jar = jar;
        }

        void preload() throws IOException {
            List<String> names = new ArrayList<>();
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> all = zip.entries();
                while (all.hasMoreElements()) {
                    String name = all.nextElement().getName();
                    if (name.startsWith("com/xerocode/") && name.endsWith(".class"))
                        names.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }
            }
            for (String name : names) {
                try {
                    Class.forName(name, false, this);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(OWN)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            }
        }

        @Override
        public URL getResource(String name) {
            URL own = findResource(name);
            return own != null ? own : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> all = new ArrayList<>(Collections.list(findResources(name)));
            Enumeration<URL> parent = getParent().getResources(name);
            while (parent.hasMoreElements()) all.add(parent.nextElement());
            return Collections.enumeration(all);
        }
    }
}
