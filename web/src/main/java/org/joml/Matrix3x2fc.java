package org.joml;

public interface Matrix3x2fc {
    float m00();
    float m01();
    float m10();
    float m11();
    float m20();
    float m21();

    Vector2f transformPosition(float x, float y, Vector2f dest);

    Vector2f transformPosition(Vector2f v);

    boolean equals(Matrix3x2fc m, float delta);

    Matrix3x2f invert(Matrix3x2f dest);

    Matrix3x2f mul(Matrix3x2fc right, Matrix3x2f dest);

    float determinant();
}
