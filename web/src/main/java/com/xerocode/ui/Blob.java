package com.xerocode.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;

public final class Blob {
    static final class Slot {
        boolean push(VertexConsumer vc, int count) { return false; }

        void capture(VertexConsumer vc, long mark, int count) {}

        void drop() {}
    }

    private Blob() {}

    public static boolean usable(VertexConsumer vc) { return false; }

    public static long start(VertexConsumer vc) { return -1; }

    public static ByteBuffer capture(VertexConsumer vc, long mark, int count, ByteBuffer reuse) { return reuse; }

    public static boolean push(VertexConsumer vc, ByteBuffer blob, int count) { return false; }
}
