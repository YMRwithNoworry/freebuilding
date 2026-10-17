package com.freebuilding.client;

import com.freebuilding.FreeBuilding;
import com.freebuilding.selection.Selection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = FreeBuilding.MOD_ID, value = Dist.CLIENT)
public final class SelectionRenderer {
    private SelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Selection selection = ClientSelection.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (selection == null || minecraft.level == null || !selection.isComplete()) {
            return;
        }
        if (!selection.dimension().equals(minecraft.level.dimension())) {
            return;
        }

        var min = selection.min();
        var max = selection.max();
        if (min == null || max == null) {
            return;
        }
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
        Camera camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        fillBox(poseStack, fill, box, 0.15F, 0.85F, 1.0F, 0.12F);
        buffers.endBatch(RenderType.debugFilledBox());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, 0.15F, 0.85F, 1.0F, 1.0F);
        buffers.endBatch(RenderType.lines());

        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void fillBox(PoseStack poseStack, VertexConsumer consumer, AABB box,
                                float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;
        quad(consumer, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, red, green, blue, alpha);
        quad(consumer, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, red, green, blue, alpha);
        quad(consumer, matrix, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, red, green, blue, alpha);
        quad(consumer, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, red, green, blue, alpha);
        quad(consumer, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, red, green, blue, alpha);
        quad(consumer, matrix, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x4, y4, z4).color(red, green, blue, alpha).endVertex();
    }
}
