package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Menus {
    public static final int PREV = 1, NEXT = 2;
    public static final int KIT_SIZE = 36;

    public static final class Cell {
        public final int slot;
        public final String item;
        public String name = "", description = "";
        public int nav;
        public Node child;
        public Catalog.Action action;
        public Catalog.Category category;

        Cell(int slot, String item) { this.slot = slot; this.item = item; }
    }

    public static final class Page {
        public final String title;
        public final int size;
        public final List<Cell> cells = new ArrayList<>();

        Page(String title, int size) { this.title = title; this.size = size; }

        public Cell at(int slot) {
            for (Cell c : cells) if (c.slot == slot) return c;
            return null;
        }
    }

    public static final class Node {
        public final Catalog.Category category;
        public final List<Page> pages = new ArrayList<>();

        Node(Catalog.Category category) { this.category = category; }
    }

    private static final Map<String, Node> ROOTS = new HashMap<>();
    private static final List<Cell> KIT = new ArrayList<>();
    private static boolean loaded;
    private static int lost;

    public static List<Cell> kit() { load(); return KIT; }

    public static Node root(Catalog.Category c) { load(); return c == null ? null : ROOTS.get(c.name); }

    private static void load() {
        if (loaded || !Catalog.loaded()) return;
        loaded = true;
        try (InputStream in = Menus.class.getResourceAsStream("/assets/xerocode/menus.json")) {
            if (in == null) { XeroCode.LOG.error("[xerocode] menus.json not found in the jar"); return; }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject menus = root.getAsJsonObject("menus");
            for (String name : menus.keySet()) {
                Catalog.Category c = Catalog.category(name);
                if (c == null) continue;
                ROOTS.put(name, node(c, menus.getAsJsonObject(name)));
            }
            for (JsonElement e : root.getAsJsonArray("kit")) {
                JsonObject o = e.getAsJsonObject();
                Catalog.Category c = Catalog.category(o.get("c").getAsString());
                if (c == null) continue;
                Cell cell = new Cell(o.get("s").getAsInt(), c.block);
                cell.category = c;
                cell.name = c.name;
                KIT.add(cell);
            }
            if (lost > 0) XeroCode.LOG.warn("[xerocode] menus.json: {} записей нет в каталоге", lost);
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read menus.json", e);
        }
    }

    private static Node node(Catalog.Category c, JsonObject o) {
        Node n = new Node(c);
        for (JsonElement pe : o.getAsJsonArray("p")) {
            JsonObject po = pe.getAsJsonObject();
            Page page = new Page(po.get("t").getAsString(), po.get("z").getAsInt());
            for (JsonElement ce : po.getAsJsonArray("c")) {
                JsonObject co = ce.getAsJsonObject();
                Cell cell = new Cell(co.get("s").getAsInt(), co.get("i").getAsString());
                if (co.has("nav")) {
                    cell.nav = "prev".equals(co.get("nav").getAsString()) ? PREV : NEXT;
                } else if (co.has("k")) {
                    cell.name = co.get("n").getAsString();
                    cell.description = co.has("d") ? co.get("d").getAsString() : "";
                    cell.child = node(c, co.getAsJsonObject("k"));
                } else {
                    String sub = co.has("u") ? co.get("u").getAsString() : null;
                    cell.action = find(c, sub, co.get("a").getAsString(), cell.item);
                    if (cell.action == null) { lost++; continue; }
                    cell.name = cell.action.name;
                }
                page.cells.add(cell);
            }
            n.pages.add(page);
        }
        return n;
    }

    private static Catalog.Action find(Catalog.Category c, String sub, String name, String item) {
        Catalog.Action named = null;
        for (List<Catalog.Action> list : c.subActions)
            for (Catalog.Action a : list) {
                if (!a.name.equals(name) || !Objects.equals(a.subcategory, sub)) continue;
                if (a.item.equals(item)) return a;
                if (named == null) named = a;
            }
        return named;
    }
}
