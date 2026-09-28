package org.joml;

public class Vector2f {
    public float x, y;

    public Vector2f() {}

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float x() { return x; }
    public float y() { return y; }

    public Vector2f set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public String toString() { return "(" + x + " " + y + ")"; }
}
