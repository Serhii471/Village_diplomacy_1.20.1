package com.diplomacy.villagediplomacy.client.renderer;

import com.diplomacy.villagediplomacy.block.RouteSignpostBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class RouteSignpostRenderer implements BlockEntityRenderer<RouteSignpostBlockEntity> {
    private final Font font;

    public RouteSignpostRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(RouteSignpostBlockEntity sign, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (sign.targetName().isBlank()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5D, 0.76D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot()));
        pose.translate(0.0D, 0.0D, 0.13D);
        pose.scale(0.008F, -0.008F, 0.008F);
        String name = font.plainSubstrByWidth(sign.targetName(), 96);
        String details = Component.translatable("direction.villagediplomacy." + sign.direction()).getString()
                + " " + sign.distance() + "m";
        int color = sign.allied() ? 0x174F1B : 0x241B12;
        font.drawInBatch(name, -font.width(name) / 2.0F, -8.0F, color, false, pose.last().pose(), buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        font.drawInBatch(details, -font.width(details) / 2.0F, 3.0F, color, false, pose.last().pose(), buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }
}
