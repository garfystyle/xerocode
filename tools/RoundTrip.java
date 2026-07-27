import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.Catalog;
import com.xerocode.Exporter;
import com.xerocode.Functions;
import com.xerocode.Importer;
import com.xerocode.Mapping;
import com.xerocode.Pickers;
import com.xerocode.Script;
import com.xerocode.Values;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Круг «код мира → полотно → код мира» без запущенной игры: единственный способ проверить
 * обратный маппинг на настоящих 906 блоках, не перезапуская клиент ради каждой правки.
 * Предметы офлайн не разворачиваются (нет реестров) — их сравнение отбрасывает сам сравниватель.
 */
public final class RoundTrip {
    public static void main(String[] args) throws Exception {
        Catalog.load();
        Values.load();
        Pickers.load();
        Mapping.load();

        JsonObject root = JsonParser.parseString(
                Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray handlers = root.getAsJsonArray("handlers");

        Script script = new Script();
        Importer.Result res = new Importer.Result();
        for (JsonElement he : handlers) {
            List<Script.Node> chain = Importer.chainOf(he.getAsJsonObject(), res);
            Script.Root r = new Script.Root(40, script.roots.size() * 100);
            r.chain.addAll(chain);
            script.roots.add(r);
        }
        Functions.rebuild(script);
        System.out.println("импорт: блоков " + res.blocks + ", значений " + res.values
                + ", маркеров " + res.markers + ", потеряно " + res.skippedValues);

        Exporter.Result out = Exporter.export(script);
        Exporter.Report rep = out.report();
        System.out.println("экспорт: строк " + rep.lines + ", блоков " + rep.blocks
                + ", значений " + rep.values + ", маркеров " + rep.markers
                + ", без события " + rep.headless + ", нет на сервере " + rep.unmapped
                + ", потеряно значений " + rep.lostValues);
        if (!rep.problems.isEmpty()) System.out.println("проблемы: " + rep.problems);

        Files.writeString(Path.of(args[1]), out.json().toString(), StandardCharsets.UTF_8);
        System.out.println("записано " + args[1]);
    }
}
