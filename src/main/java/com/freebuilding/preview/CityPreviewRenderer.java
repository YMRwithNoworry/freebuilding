package com.freebuilding.preview;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.CityStyle;
import com.freebuilding.city.Plot;
import com.freebuilding.preset.Preset;
import net.minecraft.util.RandomSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

public final class CityPreviewRenderer {
    private static final int MAX_PNG_BYTES = 512 * 1024;

    private CityPreviewRenderer() {
    }

    public static byte[] render(CityLayout layout, CityConfig config, List<Preset> presets) {
        int maxScale = Math.max(1, Math.min(4, 512 / Math.max(1, layout.totalSize)));
        for (int scale = maxScale; scale >= 1; scale--) {
            byte[] png = renderAt(layout, config, scale);
            if (png.length <= MAX_PNG_BYTES || scale == 1) {
                return png;
            }
        }
        return new byte[0];
    }

    private static byte[] renderAt(CityLayout layout, CityConfig config, int scale) {
        int size = layout.totalSize;
        int imageSize = size * scale;
        int[] pixels = new int[imageSize * imageSize];
        Arrays.fill(pixels, 0xFF20242A);
        CityStyle style = config.style;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int worldX = layout.originX + x;
                int worldZ = layout.originZ + z;
                Plot plot = layout.plotAt(worldX, worldZ);
                int color;
                if (plot != null) {
                    color = plot.park ? style.parkColor() : style.plotColor();
                } else if (layout.isSidewalk(worldX, worldZ)) {
                    color = style.sidewalkColor();
                } else {
                    color = style.roadColor();
                }
                fillRect(pixels, imageSize, x * scale, z * scale, scale, scale, color);
            }
        }

        for (Plot plot : layout.getPlots()) {
            if (plot.hasBuilding()) {
                drawBuilding(pixels, imageSize, layout, plot, scale);
            } else if (plot.park) {
                drawPark(pixels, imageSize, layout, plot, scale);
            }
            drawLamp(pixels, imageSize, layout, plot, config, scale);
        }

        drawBorder(pixels, imageSize);
        return encode(pixels, imageSize);
    }

    private static void drawBuilding(int[] pixels, int imageSize, CityLayout layout, Plot plot, int scale) {
        int baseX = plot.buildMinX - layout.originX;
        int baseZ = plot.buildMinZ - layout.originZ;
        int width = plot.buildMaxX - plot.buildMinX + 1;
        int depth = plot.buildMaxZ - plot.buildMinZ + 1;
        if (plot.preset != null) {
            var data = plot.preset.preview().rotated(plot.rotation);
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    int sx = Math.min(data.width() - 1, x * data.width() / Math.max(1, width));
                    int sz = Math.min(data.depth() - 1, z * data.depth() / Math.max(1, depth));
                    fillRect(pixels, imageSize, (baseX + x) * scale, (baseZ + z) * scale,
                            scale, scale, data.colorAt(sx, sz));
                }
            }
        } else {
            fillRect(pixels, imageSize, baseX * scale, baseZ * scale, width * scale, depth * scale, 0xFFB0B0A8);
            fillRect(pixels, imageSize, (baseX + 1) * scale, (baseZ + 1) * scale,
                    Math.max(1, width - 2) * scale, Math.max(1, depth - 2) * scale, 0xFF7A4A3A);
            fillRect(pixels, imageSize, (baseX + width / 2) * scale, (baseZ + depth - 1) * scale,
                    scale, scale, 0xFF3A2A1A);
        }
    }

    private static void drawPark(int[] pixels, int imageSize, CityLayout layout, Plot plot, int scale) {
        RandomSource random = RandomSource.create(layout.seed
                ^ ((long) plot.gridX * 0x9E3779B97F4A7C15L)
                ^ ((long) plot.gridZ * 0xC2B2AE3D27D4EB4FL));
        int width = plot.maxX - plot.minX + 1;
        int depth = plot.maxZ - plot.minZ + 1;
        for (int i = 0; i < 6; i++) {
            int x = plot.minX - layout.originX + random.nextInt(width);
            int z = plot.minZ - layout.originZ + random.nextInt(depth);
            fillRect(pixels, imageSize, x * scale, z * scale, Math.max(1, scale), Math.max(1, scale), 0xFF2E5A24);
        }
    }

    private static void drawLamp(int[] pixels, int imageSize, CityLayout layout, Plot plot, CityConfig config, int scale) {
        RandomSource random = RandomSource.create(layout.seed
                ^ ((long) plot.gridX * 0x9E3779B97F4A7C15L)
                ^ ((long) plot.gridZ * 0xC2B2AE3D27D4EB4FL)
                ^ 0x51ED270BL);
        if (random.nextDouble() * 100.0D >= config.lampChance) {
            return;
        }
        int x = plot.minX - layout.originX - 1;
        int z = plot.minZ - layout.originZ;
        if (x < 0 || z < 0) {
            return;
        }
        fillRect(pixels, imageSize, x * scale, z * scale, Math.max(1, scale), Math.max(1, scale),
                config.style.lampColor());
    }

    private static void fillRect(int[] pixels, int imageSize, int x, int y, int width, int height, int color) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(imageSize, x + width);
        int y1 = Math.min(imageSize, y + height);
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                pixels[px + py * imageSize] = color;
            }
        }
    }

    private static void drawBorder(int[] pixels, int imageSize) {
        int color = 0xFF9AA0A6;
        for (int i = 0; i < imageSize; i++) {
            pixels[i] = color;
            pixels[i + (imageSize - 1) * imageSize] = color;
            pixels[i * imageSize] = color;
            pixels[i * imageSize + imageSize - 1] = color;
        }
    }

    private static byte[] encode(int[] pixels, int imageSize) {
        try {
            BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, imageSize, imageSize, pixels, 0, imageSize);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            return new byte[0];
        }
    }
}
