package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Blend;

public final class RenderPipelines {
    public static final RenderPipeline TEXT = new RenderPipeline("text", 33, true, Blend.ALPHA);
    public static final RenderPipeline GUI = new RenderPipeline("gui", 67, false, Blend.ALPHA);
    public static final RenderPipeline GUI_INVERT = new RenderPipeline("gui_invert", 68, false, Blend.INVERT);
    public static final RenderPipeline GUI_TEXT_HIGHLIGHT = new RenderPipeline("gui_text_highlight", 69, false, Blend.HIGHLIGHT);
    public static final RenderPipeline GUI_TEXTURED = new RenderPipeline("gui_textured", 70, true, Blend.ALPHA);
    public static final RenderPipeline GUI_TEXTURED_PREMULTIPLIED_ALPHA =
            new RenderPipeline("gui_textured_premultiplied_alpha", 71, true, Blend.PREMULTIPLIED);

    private RenderPipelines() {}
}
