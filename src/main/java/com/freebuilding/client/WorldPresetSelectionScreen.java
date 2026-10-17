package com.freebuilding.client;

import com.freebuilding.WorldCreationSetup;
import com.freebuilding.preset.PresetFileInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Preset and full-map generation options shown from the vanilla Create World screen. */
public final class WorldPresetSelectionScreen extends Screen {
    private final CreateWorldScreen parent;
    private final List<PresetFileInfo> available;
    private final Consumer<WorldCreationSetup> onDone;
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean cityGeneration;
    private int cityChance;
    private PresetSelectionList presetList;
    private Checkbox cityGenerationCheck;

    public WorldPresetSelectionScreen(CreateWorldScreen parent, List<PresetFileInfo> available,
                                      WorldCreationSetup initial, Consumer<WorldCreationSetup> onDone) {
        super(Component.translatable("freebuilding.world_preset.title"));
        this.parent = parent;
        this.available = List.copyOf(available);
        this.onDone = onDone;
        WorldCreationSetup setup = initial.copy();
        setup.validate();
        this.cityGeneration = setup.cityGeneration;
        this.cityChance = setup.cityChance;
        for (PresetFileInfo info : this.available) {
            if (setup.presets.contains(info.name())) {
                this.selected.add(info.name());
            }
        }
    }

    @Override
    protected void init() {
        int left = 18;
        int width = Math.min(360, this.width - 36);
        int top = 78;
        int bottom = this.height - 54;
        this.presetList = new PresetSelectionList(this.minecraft, width, Math.max(40, bottom - top), top, 28, this);
        this.presetList.setLeftPos(left);
        this.presetList.setPresets(this.available);
        addRenderableWidget(this.presetList);

        this.cityGenerationCheck = new Checkbox(left, 28, width, 20,
                Component.translatable("freebuilding.world_preset.generate"), this.cityGeneration) {
            @Override
            public void onPress() {
                super.onPress();
                cityGeneration = this.selected();
            }
        };
        addRenderableWidget(this.cityGenerationCheck);
        addRenderableWidget(new IntSlider(left, 52, width, 18,
                "freebuilding.world_preset.chance", 0, 100, this.cityChance, value -> this.cityChance = value));

        int buttonY = this.height - 30;
        int buttonWidth = Math.max(64, (width - 12) / 4);
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.world_preset.all"), button -> selectAll())
                .bounds(left, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.world_preset.none"), button -> clearSelection())
                .bounds(left + buttonWidth + 4, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.done"), button -> finish())
                .bounds(left + (buttonWidth + 4) * 2, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.world_preset.cancel"), button -> onClose())
                .bounds(left + (buttonWidth + 4) * 3, buttonY, buttonWidth, 20).build());
    }

    private void selectAll() {
        for (PresetFileInfo info : this.available) {
            this.selected.add(info.name());
        }
    }

    private void clearSelection() {
        this.selected.clear();
    }

    private void finish() {
        WorldCreationSetup setup = new WorldCreationSetup();
        setup.cityGeneration = this.cityGeneration;
        setup.cityChance = this.cityChance;
        setup.presets = new ArrayList<>(this.selected);
        setup.validate();
        this.onDone.accept(setup);
    }

    public boolean isSelected(String name) {
        return this.selected.contains(name);
    }

    public void toggle(String name) {
        if (!this.selected.add(name)) {
            this.selected.remove(name);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = 15;
        int right = Math.min(this.width - 15, left + Math.min(366, this.width - 30));
        graphics.fill(left, 4, right, this.height - 38, 0xD010141A);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("freebuilding.world_preset.selected", this.selected.size(), this.available.size()),
                left + 4, 68, 0xFFB0BEC5, false);
        if (this.available.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("freebuilding.world_preset.empty"), this.width / 2, 110, 0xFFFFCC66);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static final class PresetEntry extends ObjectSelectionList.Entry<PresetEntry> {
        private final PresetSelectionList list;
        private final PresetFileInfo info;

        private PresetEntry(PresetSelectionList list, PresetFileInfo info) {
            this.list = list;
            this.info = info;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean selected = list.screen.isSelected(this.info.name());
            if (selected) {
                graphics.fill(left, top, left + width, top + height, 0x8040C0D0);
            } else if (hovered) {
                graphics.fill(left, top, left + width, top + height, 0x40202020);
            }
            String prefix = selected ? "[x] " : "[ ] ";
            graphics.drawString(Minecraft.getInstance().font, prefix + this.info.name(), left + 4, top + 3,
                    0xFFFFFFFF, false);
            graphics.drawString(Minecraft.getInstance().font,
                    this.info.width() + "x" + this.info.height() + "x" + this.info.depth(),
                    left + 4, top + 15, 0xFF9AA0A6, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.info.name());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.list.screen.toggle(this.info.name());
            return true;
        }
    }

    private static final class PresetSelectionList extends ObjectSelectionList<PresetEntry> {
        private final WorldPresetSelectionScreen screen;

        private PresetSelectionList(Minecraft minecraft, int width, int height, int top, int itemHeight,
                                    WorldPresetSelectionScreen screen) {
            super(minecraft, width, height, top, itemHeight, top + height);
            this.screen = screen;
            setRenderBackground(false);
        }

        private void setPresets(List<PresetFileInfo> presets) {
            clearEntries();
            for (PresetFileInfo info : presets) {
                addEntry(new PresetEntry(this, info));
            }
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }

    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final String key;
        private final IntConsumer consumer;

        private IntSlider(int x, int y, int width, int height, String key, int min, int max, int value,
                          IntConsumer consumer) {
            super(x, y, width, height, Component.empty(),
                    (Mth.clamp(value, min, max) - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.key = key;
            this.consumer = consumer;
            updateMessage();
        }

        private int intValue() {
            return this.min + (int) Math.round(this.value * (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(this.key, intValue()));
        }

        @Override
        protected void applyValue() {
            this.consumer.accept(intValue());
        }
    }
}
