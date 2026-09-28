package org.joml;

public class Matrix3x2fStack extends Matrix3x2f {
    private float[] saved;
    private int depth;

    public Matrix3x2fStack(int stackSize) {
        saved = new float[Math.max(1, stackSize) * 6];
    }

    public Matrix3x2fStack pushMatrix() {
        if ((depth + 1) * 6 > saved.length) saved = java.util.Arrays.copyOf(saved, saved.length * 2);
        int at = depth * 6;
        saved[at] = m00;
        saved[at + 1] = m01;
        saved[at + 2] = m10;
        saved[at + 3] = m11;
        saved[at + 4] = m20;
        saved[at + 5] = m21;
        depth++;
        return this;
    }

    public Matrix3x2fStack popMatrix() {
        if (depth == 0) throw new IllegalStateException("already at the bottom of the stack");
        depth--;
        int at = depth * 6;
        m00 = saved[at];
        m01 = saved[at + 1];
        m10 = saved[at + 2];
        m11 = saved[at + 3];
        m20 = saved[at + 4];
        m21 = saved[at + 5];
        return this;
    }

    public Matrix3x2fStack clear() {
        depth = 0;
        identity();
        return this;
    }

    public int depth() { return depth; }
}
