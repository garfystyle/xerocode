package com.mojang.blaze3d.vertex;

import org.joml.Matrix3x2fc;

public interface VertexConsumer {
    VertexConsumer addVertex(float x, float y, float z);

    VertexConsumer setColor(int argb);

    VertexConsumer setUv(float u, float v);

    default VertexConsumer addVertexWith2DPose(Matrix3x2fc pose, float x, float y) {
        return addVertex(pose.m00() * x + pose.m10() * y + pose.m20(), pose.m01() * x + pose.m11() * y + pose.m21(), 0);
    }

    default VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor((a << 24) | (r << 16) | (g << 8) | b);
    }
}
