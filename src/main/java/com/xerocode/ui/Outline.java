package com.xerocode.ui;

import java.util.ArrayList;
import java.util.List;

public final class Outline {
    private static final int BUCKET = 64;

    public static List<int[]> of(List<int[]> rects, int pad) {
        int n = rects.size();
        int[] bx0 = new int[n], by0 = new int[n], bx1 = new int[n], by1 = new int[n];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int[] r = rects.get(i);
            bx0[i] = r[0] - pad;
            by0[i] = r[1] - pad;
            bx1[i] = r[2] + pad;
            by1[i] = r[3] + pad;
            lo = Math.min(lo, by0[i]);
            hi = Math.max(hi, by1[i]);
        }
        if (n == 0) return List.of();

        int rows = (hi - lo) / BUCKET + 1;
        int[] count = new int[rows + 1];
        for (int i = 0; i < n; i++)
            for (int b = row(by0[i], lo, rows); b <= row(by1[i], lo, rows); b++) count[b]++;
        int[] start = new int[rows + 1];
        for (int b = 1; b <= rows; b++) start[b] = start[b - 1] + count[b - 1];
        int[] grid = new int[start[rows] + count[rows]];
        int[] at = start.clone();
        for (int i = 0; i < n; i++)
            for (int b = row(by0[i], lo, rows); b <= row(by1[i], lo, rows); b++) grid[at[b]++] = i;

        List<int[]> out = new ArrayList<>();
        Cut cut = new Cut(bx0, by0, bx1, by1, grid, start, at, lo, rows);
        for (int i = 0; i < n; i++) {
            cut.flat(i, by0[i], bx0[i], bx1[i], out);
            cut.flat(i, by1[i], bx0[i], bx1[i], out);
            cut.tall(i, bx0[i], by0[i], by1[i], out);
            cut.tall(i, bx1[i], by0[i], by1[i], out);
        }
        return merge(out);
    }

    private static int row(int y, int lo, int rows) {
        return Math.max(0, Math.min(rows, (y - lo) / BUCKET));
    }

    private static final class Cut {
        private final int[] x0, y0, x1, y1, grid, from, to;
        private final int lo, rows;
        private int[] parts = new int[64], spare = new int[64];
        private int size;

        Cut(int[] x0, int[] y0, int[] x1, int[] y1, int[] grid, int[] from, int[] to,
            int lo, int rows) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.grid = grid; this.from = from; this.to = to;
            this.lo = lo; this.rows = rows;
        }

        void flat(int self, int line, int a, int b, List<int[]> out) {
            reset(a, b);
            int r = row(line, lo, rows);
            for (int k = from[r]; k < to[r]; k++) {
                int i = grid[k];
                if (i == self || line <= y0[i] || line >= y1[i]) continue;
                if (subtract(x0[i], x1[i])) return;
            }
            for (int p = 0; p < size; p += 2)
                if (parts[p + 1] > parts[p]) out.add(new int[]{parts[p], line, parts[p + 1], line});
        }

        void tall(int self, int line, int a, int b, List<int[]> out) {
            reset(a, b);
            int r0 = row(a, lo, rows), r1 = row(b, lo, rows);
            for (int r = r0; r <= r1; r++) {
                for (int k = from[r]; k < to[r]; k++) {
                    int i = grid[k];
                    if (i == self || line <= x0[i] || line >= x1[i]) continue;
                    if (subtract(y0[i], y1[i])) return;
                }
            }
            for (int p = 0; p < size; p += 2)
                if (parts[p + 1] > parts[p]) out.add(new int[]{line, parts[p], line, parts[p + 1]});
        }

        private void reset(int a, int b) {
            parts[0] = a;
            parts[1] = b;
            size = 2;
        }

        private boolean subtract(int cutLo, int cutHi) {
            if (spare.length < size + 2) spare = new int[Math.max(spare.length * 2, size + 2)];
            int write = 0;
            for (int p = 0; p < size; p += 2) {
                int s = parts[p], e = parts[p + 1];
                if (cutHi <= s || cutLo >= e) {
                    spare[write++] = s;
                    spare[write++] = e;
                    continue;
                }
                if (cutLo > s) { spare[write++] = s; spare[write++] = cutLo; }
                if (cutHi < e) { spare[write++] = cutHi; spare[write++] = e; }
            }
            int[] swap = parts;
            parts = spare;
            spare = swap;
            size = write;
            return size == 0;
        }
    }

    private static List<int[]> merge(List<int[]> segs) {
        if (segs.size() < 2) return segs;
        segs.sort((a, b) -> {
            int fa = a[1] == a[3] ? 0 : 1, fb = b[1] == b[3] ? 0 : 1;
            if (fa != fb) return fa - fb;
            int la = fa == 0 ? a[1] : a[0], lb = fb == 0 ? b[1] : b[0];
            if (la != lb) return la - lb;
            return (fa == 0 ? a[0] : a[1]) - (fb == 0 ? b[0] : b[1]);
        });
        List<int[]> out = new ArrayList<>(segs.size());
        int[] run = null;
        for (int[] s : segs) {
            if (run != null && same(run, s) && start(s) <= end(run)) {
                if (end(s) > end(run)) {
                    if (s[1] == s[3]) run[2] = s[2]; else run[3] = s[3];
                }
                continue;
            }
            run = s.clone();
            out.add(run);
        }
        return out;
    }

    private static boolean same(int[] a, int[] b) {
        boolean fa = a[1] == a[3], fb = b[1] == b[3];
        if (fa != fb) return false;
        return fa ? a[1] == b[1] : a[0] == b[0];
    }

    private static int start(int[] s) { return s[1] == s[3] ? s[0] : s[1]; }

    private static int end(int[] s) { return s[1] == s[3] ? s[2] : s[3]; }

    private Outline() {}
}
