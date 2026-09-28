package net.minecraft.client.gui.navigation;

import net.minecraft.util.Mth;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public record ScreenRectangle(ScreenPosition position, int width, int height) {
    private static final ScreenRectangle EMPTY = new ScreenRectangle(0, 0, 0, 0);

    public ScreenRectangle(int x, int y, int width, int height) {
        this(new ScreenPosition(x, y), width, height);
    }

    public static ScreenRectangle empty() { return EMPTY; }

    public int top() { return position.y(); }
    public int bottom() { return position.y() + height; }
    public int left() { return position.x(); }
    public int right() { return position.x() + width; }

    public ScreenRectangle intersection(ScreenRectangle o) {
        int l = Math.max(left(), o.left()), t = Math.max(top(), o.top());
        int r = Math.min(right(), o.right()), b = Math.min(bottom(), o.bottom());
        return l < r && t < b ? new ScreenRectangle(l, t, r - l, b - t) : null;
    }

    public boolean intersects(ScreenRectangle o) {
        return left() < o.right() && right() > o.left() && top() < o.bottom() && bottom() > o.top();
    }

    public boolean overlaps(ScreenRectangle o) {
        return Math.max(left(), o.left()) <= Math.min(right() - 1, o.right() - 1)
                && Math.max(top(), o.top()) <= Math.min(bottom() - 1, o.bottom() - 1);
    }

    public boolean encompasses(ScreenRectangle o) {
        return o.left() >= left() && o.top() >= top() && o.right() <= right() && o.bottom() <= bottom();
    }

    public boolean containsPoint(int x, int y) {
        return x >= left() && x < right() && y >= top() && y < bottom();
    }

    public ScreenRectangle transformAxisAligned(Matrix3x2fc m) {
        Vector2f tl = m.transformPosition(left(), top(), new Vector2f());
        Vector2f br = m.transformPosition(right(), bottom(), new Vector2f());
        return new ScreenRectangle(Mth.floor(tl.x), Mth.floor(tl.y), Mth.floor(br.x - tl.x), Mth.floor(br.y - tl.y));
    }

    public ScreenRectangle transformMaxBounds(Matrix3x2fc m) {
        Vector2f a = m.transformPosition(left(), top(), new Vector2f());
        Vector2f b = m.transformPosition(right(), top(), new Vector2f());
        Vector2f c = m.transformPosition(left(), bottom(), new Vector2f());
        Vector2f d = m.transformPosition(right(), bottom(), new Vector2f());
        float minX = Math.min(Math.min(a.x, c.x), Math.min(b.x, d.x));
        float maxX = Math.max(Math.max(a.x, c.x), Math.max(b.x, d.x));
        float minY = Math.min(Math.min(a.y, c.y), Math.min(b.y, d.y));
        float maxY = Math.max(Math.max(a.y, c.y), Math.max(b.y, d.y));
        return new ScreenRectangle(Mth.floor(minX), Mth.floor(minY), Mth.ceil(maxX - minX), Mth.ceil(maxY - minY));
    }
}
