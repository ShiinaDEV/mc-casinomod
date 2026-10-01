package de.shiina.casino.client;

import de.shiina.casino.entity.CasinoSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The player is rendered by Minecraft; the chair by its block model. */
public final class SeatRenderer extends EntityRenderer<CasinoSeatEntity> {
    public SeatRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(CasinoSeatEntity entity,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light) {}
    @Override public ResourceLocation getTextureLocation(CasinoSeatEntity entity) { return ResourceLocation.fromNamespaceAndPath("shiinacasino","textures/block/wood.png"); }
}
