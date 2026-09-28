package net.minecraft.resources;

public final class Identifier implements Comparable<Identifier> {
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        if (!validNamespace(namespace) || !validPath(path))
            throw new IllegalArgumentException("Non [a-z0-9_.-] character in identifier: " + namespace + ":" + path);
        return new Identifier(namespace, path);
    }

    public static Identifier withDefaultNamespace(String path) {
        return fromNamespaceAndPath(DEFAULT_NAMESPACE, path);
    }

    public static Identifier parse(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) return withDefaultNamespace(id);
        String ns = colon == 0 ? DEFAULT_NAMESPACE : id.substring(0, colon);
        return fromNamespaceAndPath(ns, id.substring(colon + 1));
    }

    public static Identifier tryParse(String id) {
        if (id == null) return null;
        try {
            return parse(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Identifier tryBuild(String namespace, String path) {
        return validNamespace(namespace) && validPath(path) ? new Identifier(namespace, path) : null;
    }

    private static boolean validNamespace(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    private static boolean validPath(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    public String getNamespace() { return namespace; }
    public String getPath() { return path; }

    public Identifier withPath(String p) { return fromNamespaceAndPath(namespace, p); }
    public Identifier withPrefix(String p) { return fromNamespaceAndPath(namespace, p + path); }
    public Identifier withSuffix(String s) { return fromNamespaceAndPath(namespace, path + s); }

    public String toShortLanguageKey() { return DEFAULT_NAMESPACE.equals(namespace) ? path : namespace + "." + path; }

    @Override
    public String toString() { return namespace + ":" + path; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Identifier i && namespace.equals(i.namespace) && path.equals(i.path));
    }

    @Override
    public int hashCode() { return 31 * namespace.hashCode() + path.hashCode(); }

    @Override
    public int compareTo(Identifier o) {
        int c = path.compareTo(o.path);
        return c != 0 ? c : namespace.compareTo(o.namespace);
    }
}
