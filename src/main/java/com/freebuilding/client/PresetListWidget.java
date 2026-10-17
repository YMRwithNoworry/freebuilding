package com.freebuilding.client;

import com.freebuilding.preset.PresetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PresetListWidget extends ObjectSelectionList<PresetListWidget.Entry> {
    private final CityScreen screen;

    public PresetListWidget(Minecraft minecraft, int width, int height, int top, int itemHeight, CityScreen screen) {
        super(minecraft, width, height, top, itemHeight, top + height);
        this.screen = screen;
        setRenderBackground(false);
    }

    public void setPresets(List<PresetInfo> presets, int selectedIndex) {
        clearEntries();
        for (int i = 0; i < presets.size(); i++) {
            addEntry(new Entry(this, i, presets.get(i)));
        }
        if (selectedIndex >= 0 && selectedIndex < children().size()) {
            setSelected(children().get(selectedIndex));
        }
    }

    public void select(int index) {
        if (index >= 0 && index < children().size()) {
            setSelected(children().get(index));
        }
    }

    @Override
    public int getRowWidth() {
        return width - 12;
    }

    public static final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final PresetListWidget list;
        private final int index;
        private final PresetInfo info;

        private Entry(PresetListWidget list, int index, PresetInfo info) {
            this.list = list;
            this.index = index;
            this.info = info;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (list.getSelected() == this) {
                graphics.fill(left, top, left + width, top + height, 0x8040C0D0);
            } else if (hovered) {
                graphics.fill(left, top, left + width, top + height, 0x40202020);
            }
            var font = Minecraft.getInstance().font;
            String prefix = info.enabled() ? "" : "[x] ";
            graphics.drawString(font, prefix + info.name(), left + 3, top + 2, 0xFFFFFFFF, false);
            String details = info.width() + "x" + info.height() + "x" + info.depth()
                    + "  w:" + info.weight();
            graphics.drawString(font, details, left + 3, top + 12, 0xFF9AA0A6, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(info.name());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.screen.selectPreset(index);
            return true;
        }
    }
}
