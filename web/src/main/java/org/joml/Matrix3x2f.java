package org.joml;

public class Matrix3x2f implements Matrix3x2fc {
    public float m00 = 1, m01, m10, m11 = 1, m20, m21;

    public Matrix3x2f() {}

    public Matrix3x2f(Matrix3x2fc m) {
        set(m);
    }

    public Matrix3x2f(float m00, float m01, float m10, float m11, float m20, float m21) {
        this.m00 = m00;
        this.m01 = m01;
        this.m10 = m10;
        this.m11 = m11;
        this.m20 = m20;
        this.m21 = m21;
    }

    @Override public float m00() { return m00; }
    @Override public float m01() { return m01; }
    @Override public float m10() { return m10; }
    @Override public float m11() { return m11; }
    @Override public float m20() { return m20; }
    @Override public float m21() { return m21; }

    public Matrix3x2f set(Matrix3x2fc m) {
        m00 = m.m00();
        m01 = m.m01();
        m10 = m.m10();
        m11 = m.m11();
        m20 = m.m20();
        m21 = m.m21();
        return this;
    }

    public Matrix3x2f identity() {
        m00 = 1;
        m01 = 0;
        m10 = 0;
        m11 = 1;
        m20 = 0;
        m21 = 0;
        return this;
    }

    public Matrix3x2f translate(float x, float y) {
        m20 = m00 * x + m10 * y + m20;
        m21 = m01 * x + m11 * y + m21;
        return this;
    }

    public Matrix3x2f translate(Vector2f v) {
        return translate(v.x, v.y);
    }

    public Matrix3x2f scale(float x, float y) {
        m00 *= x;
        m01 *= x;
        m10 *= y;
        m11 *= y;
        return this;
    }

    public Matrix3x2f scale(float s) {
        return scale(s, s);
    }

    public Matrix3x2f rotate(float ang) {
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        float n00 = m00 * cos + m10 * sin, n01 = m01 * cos + m11 * sin;
        float n10 = m00 * -sin + m10 * cos, n11 = m01 * -sin + m11 * cos;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
        return this;
    }

    public Matrix3x2f mul(Matrix3x2fc r) {
        return mul(r, this);
    }

    @Override
    public Matrix3x2f mul(Matrix3x2fc r, Matrix3x2f dest) {
        float n00 = m00 * r.m00() + m10 * r.m01();
        float n01 = m01 * r.m00() + m11 * r.m01();
        float n10 = m00 * r.m10() + m10 * r.m11();
        float n11 = m01 * r.m10() + m11 * r.m11();
        float n20 = m00 * r.m20() + m10 * r.m21() + m20;
        float n21 = m01 * r.m20() + m11 * r.m21() + m21;
        dest.m00 = n00;
        dest.m01 = n01;
        dest.m10 = n10;
        dest.m11 = n11;
        dest.m20 = n20;
        dest.m21 = n21;
        return dest;
    }

    @Override
    public float determinant() { return m00 * m11 - m01 * m10; }

    public Matrix3x2f invert() { return invert(this); }

    @Override
    public Matrix3x2f invert(Matrix3x2f dest) {
        float s = 1f / (m00 * m11 - m01 * m10);
        float n00 = m11 * s, n01 = -m01 * s, n10 = -m10 * s, n11 = m00 * s;
        float n20 = (m10 * m21 - m20 * m11) * s, n21 = (m20 * m01 - m00 * m21) * s;
        dest.m00 = n00;
        dest.m01 = n01;
        dest.m10 = n10;
        dest.m11 = n11;
        dest.m20 = n20;
        dest.m21 = n21;
        return dest;
    }

    @Override
    public Vector2f transformPosition(float x, float y, Vector2f dest) {
        return dest.set(m00 * x + m10 * y + m20, m01 * x + m11 * y + m21);
    }

    @Override
    public Vector2f transformPosition(Vector2f v) {
        return transformPosition(v.x, v.y, v);
    }

    public Vector2f transformDirection(Vector2f v) {
        return v.set(m00 * v.x + m10 * v.y, m01 * v.x + m11 * v.y);
    }

    public Vector2f getScale(Vector2f dest) {
        return dest.set((float) Math.sqrt(m00 * m00 + m01 * m01), (float) Math.sqrt(m10 * m10 + m11 * m11));
    }

    public Vector2f getTranslation(Vector2f dest) {
        return dest.set(m20, m21);
    }

    @Override
    public boolean equals(Matrix3x2fc m, float delta) {
        if (this == m) return true;
        if (m == null) return false;
        return Math.abs(m00 - m.m00()) <= delta && Math.abs(m01 - m.m01()) <= delta
                && Math.abs(m10 - m.m10()) <= delta && Math.abs(m11 - m.m11()) <= delta
                && Math.abs(m20 - m.m20()) <= delta && Math.abs(m21 - m.m21()) <= delta;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Matrix3x2fc m && equals(m, 0f);
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(m00);
        h = 31 * h + Float.floatToIntBits(m01);
        h = 31 * h + Float.floatToIntBits(m10);
        h = 31 * h + Float.floatToIntBits(m11);
        h = 31 * h + Float.floatToIntBits(m20);
        return 31 * h + Float.floatToIntBits(m21);
    }
}
