package com.xerocode.ui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public final class Blob {
    private static final int OFFSET_BITS = 40;
    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;

    private static boolean tried, ready;
    private static int maxVertices;
    private static VarHandle buffer, vertexPointer, vertices, elementsToFill, vertexSize;
    private static VarHandle pointer, writeOffset;

    private Blob() {}

    static final class Slot {
        private ByteBuffer bytes;
        private boolean ok;

        boolean push(VertexConsumer vc, int count) {
            return ok && Blob.push(vc, bytes, count);
        }

        void capture(VertexConsumer vc, long mark, int count) {
            ByteBuffer got = Blob.capture(vc, mark, count, bytes);
            ok = got != null;
            if (ok) bytes = got;
        }

        void drop() { ok = false; }
    }

    private static void reflect() {
        tried = true;
        try {
            MethodHandles.Lookup bb = MethodHandles.privateLookupIn(BufferBuilder.class, MethodHandles.lookup());
            MethodHandles.Lookup bbb = MethodHandles.privateLookupIn(ByteBufferBuilder.class, MethodHandles.lookup());
            buffer = bb.findVarHandle(BufferBuilder.class, "buffer", ByteBufferBuilder.class);
            vertexPointer = bb.findVarHandle(BufferBuilder.class, "vertexPointer", long.class);
            vertices = bb.findVarHandle(BufferBuilder.class, "vertices", int.class);
            elementsToFill = bb.findVarHandle(BufferBuilder.class, "elementsToFill", int.class);
            vertexSize = bb.findVarHandle(BufferBuilder.class, "vertexSize", int.class);
            maxVertices = (int) bb.findStaticVarHandle(BufferBuilder.class, "MAX_VERTEX_COUNT", int.class).get();
            pointer = bbb.findVarHandle(ByteBufferBuilder.class, "pointer", long.class);
            writeOffset = bbb.findVarHandle(ByteBufferBuilder.class, "writeOffset", long.class);
            ready = true;
        } catch (Throwable e) {
            ready = false;
        }
    }

    public static boolean usable(VertexConsumer vc) {
        if (!tried) reflect();
        return ready && vc instanceof BufferBuilder;
    }

    public static long start(VertexConsumer vc) {
        if (!usable(vc)) return -1;
        try {
            BufferBuilder bb = (BufferBuilder) vc;
            ByteBufferBuilder b = (ByteBufferBuilder) buffer.get(bb);
            return (long) writeOffset.get(b) | ((long) (int) vertices.get(bb) << OFFSET_BITS);
        } catch (Throwable e) {
            ready = false;
            return -1;
        }
    }

    public static ByteBuffer capture(VertexConsumer vc, long mark, int count, ByteBuffer reuse) {
        if (mark < 0 || !usable(vc)) return null;
        try {
            BufferBuilder bb = (BufferBuilder) vc;
            long offset = mark & OFFSET_MASK;
            int before = (int) (mark >>> OFFSET_BITS);
            if ((int) vertices.get(bb) != before + count || (int) elementsToFill.get(bb) != 0) return null;
            ByteBufferBuilder b = (ByteBufferBuilder) buffer.get(bb);
            long bytes = (long) writeOffset.get(b) - offset;
            if (bytes != (long) (int) vertexSize.get(bb) * count || bytes <= 0 || bytes > Integer.MAX_VALUE)
                return null;
            ByteBuffer out = reuse != null && reuse.capacity() == bytes ? reuse : ByteBuffer.allocateDirect((int) bytes);
            MemoryUtil.memCopy((long) pointer.get(b) + offset, MemoryUtil.memAddress(out), bytes);
            return out;
        } catch (Throwable e) {
            ready = false;
            return null;
        }
    }

    public static boolean push(VertexConsumer vc, ByteBuffer blob, int count) {
        if (blob == null || !usable(vc)) return false;
        try {
            BufferBuilder bb = (BufferBuilder) vc;
            int size = (int) vertexSize.get(bb);
            int bytes = blob.capacity();
            if (bytes != size * count) return false;
            int have = (int) vertices.get(bb);
            if (have > 0 && (int) elementsToFill.get(bb) != 0) return false;
            if ((long) have + count > maxVertices) return false;
            long dst = ((ByteBufferBuilder) buffer.get(bb)).reserve(bytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(blob), dst, bytes);
            vertices.set(bb, have + count);
            vertexPointer.set(bb, dst + bytes - size);
            elementsToFill.set(bb, 0);
            return true;
        } catch (Throwable e) {
            ready = false;
            return false;
        }
    }
}
