package com.freebuilding.client;

import com.freebuilding.FreeBuilding;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class PreviewTexture {
    public static final ResourceLocation ID = new ResourceLocation(FreeBuilding.MOD_ID, "city_preview");
    private static DynamicTexture texture;
    private static int width;
    private static int height;

    private PreviewTexture() {
    }

    public static void update(byte[] png) {
        if (png == null || png.length == 0) {
            return;
        }
        try {
            NativeImage image = NativeImage.read(png);
            DynamicTexture old = texture;
            DynamicTexture replacement = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(ID, replacement);
            texture = replacement;
            width = image.getWidth();
            height = image.getHeight();
            if (old != null) {
                old.close();
            }
        } catch (Exception exception) {
            FreeBuilding.LOGGER.warn("Failed to decode city preview image", exception);
        }
    }

    public static boolean isReady() {
        return texture != null;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }
}
