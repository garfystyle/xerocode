package com.xerocode.code;

import xero.dialect.Dialect;
import xero.dialect.Dialects;

public final class CodeFile {
    public String path;
    public Doc doc;
    public Dialect dialect;
    public int scrollX, scrollY;
    boolean loaded;

    CodeFile(String path) {
        this.path = path;
    }

    public String name() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public String folder() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    void bind() {
        dialect = Dialects.forFile(path);
        if (doc != null && dialect != null) doc.language(dialect.syntax(), dialect.lexicon());
    }

    public boolean source() { return dialect != null; }
}
