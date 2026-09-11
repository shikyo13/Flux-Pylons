package com.zerotheabsolute.quantumflux.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Render types for the pylon's fullbright, softly blended energy surfaces. */
public final class QFRenderTypes extends RenderType {

    private static final Function<ResourceLocation, RenderType> DEPTH_TESTED_EMISSIVE = Util.memoize(
            texture -> createSoftEmissive(texture, false));
    private static final Function<ResourceLocation, RenderType> SEE_THROUGH_EMISSIVE = Util.memoize(
            texture -> createSoftEmissive(texture, true));

    private QFRenderTypes() {
        super("quantumflux_dummy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
    }

    private static RenderType createSoftEmissive(ResourceLocation texture, boolean seeThrough) {
        return RenderType.create(
                seeThrough ? "quantumflux_soft_emissive_see_through" : "quantumflux_soft_emissive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        // The eyes shader preserves vertex color without directional
                        // entity lighting. Use ordinary alpha blending for a contained
                        // field, rather than the vanilla eyes render type's additive blend.
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(seeThrough ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    public static RenderType softEmissive(ResourceLocation texture, boolean seeThrough) {
        return (seeThrough ? SEE_THROUGH_EMISSIVE : DEPTH_TESTED_EMISSIVE).apply(texture);
    }
}
