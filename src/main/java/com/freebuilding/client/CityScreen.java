package com.freebuilding.client;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.CityStyle;
import com.freebuilding.city.PresetSetting;
import com.freebuilding.city.RotationMode;
import com.freebuilding.city.RuralMode;
import com.freebuilding.network.BuildCityPacket;
import com.freebuilding.network.DeletePresetPacket;
import com.freebuilding.network.ModNetwork;
import com.freebuilding.network.PreviewRequestPacket;
import com.freebuilding.network.UpdateConfigPacket;
import com.freebuilding.preset.PresetInfo;
import com.freebuilding.preview.PreviewMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntConsumer;

public final class CityScreen extends Screen {
    private CityConfig config;
    private List<PresetInfo> presets;
    private byte[] previewPng;
    private String summary;
    private PreviewMode previewMode = PreviewMode.MANUAL;

    private int tab;
    private int selectedPreset = -1;
    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private PresetListWidget presetList;
    private IntSlider weightSlider;
    private IntSlider categoryWeightSlider;
    private EditBox categoryBox;
    private Button enableButton;
    private Button deleteButton;
    private EditBox seedBox;
    private Checkbox worldgenCheck;
    private boolean updatingSelectionWidgets;

    private int previewCooldown;
    private boolean dirty;

    public CityScreen(CityConfig config, List<PresetInfo> presets, byte[] previewPng, String summary) {
        super(Component.translatable("freebuilding.gui.title"));
        this.config = config.copy();
        this.config.validate();
        this.presets = new ArrayList<>(presets);
        this.previewPng = previewPng;
        this.summary = summary;
        if (!this.presets.isEmpty()) {
            this.selectedPreset = 0;
        }
    }

    @Override
    protected void init() {
        this.panelLeft = 12;
        this.panelTop = 38;
        this.panelW = Mth.clamp(this.width / 3, 150, 210);
        this.panelH = this.height - 92;
        this.previewW = Mth.clamp(this.width - this.panelW - 44, 110, 260);
        this.previewH = Math.min(this.previewW, this.panelH - 28);
        this.previewX = this.width - this.previewW - 12;
        this.previewY = this.panelTop;
        if (this.previewX < this.panelLeft + this.panelW + 12) {
            this.previewW = Math.max(100, this.width - this.panelW - 40);
            this.previewH = Math.min(this.previewW, this.panelH - 28);
            this.previewX = this.width - this.previewW - 12;
        }

        int tabW = Math.max(24, this.panelW / 5 - 2);
        String[] tabKeys = {"build", "layout", "decor", "world", "rural"};
        for (int i = 0; i < 5; i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.tab." + tabKeys[i]),
                            button -> {
                                this.tab = index;
                                this.rebuildWidgets();
                            })
                    .bounds(this.panelLeft + i * (tabW + 2), 18, tabW, 16)
                    .build());
        }

        if (this.presets.isEmpty()) {
            this.selectedPreset = -1;
        } else {
            this.selectedPreset = Mth.clamp(this.selectedPreset, 0, this.presets.size() - 1);
        }

        if (this.tab == 0) {
            initBuildTab();
        } else if (this.tab == 1) {
            initLayoutTab();
        } else if (this.tab == 2) {
            initDecorTab();
        } else if (this.tab == 3) {
            initWorldTab();
        } else {
            initRuralTab();
        }
        initBottomButtons();
        updateSelectionWidgets();
    }

    private void initBuildTab() {
        int listHeight = Math.max(40, this.panelH - 104);
        this.presetList = new PresetListWidget(this.minecraft, this.panelW, listHeight, this.panelTop, 22, this);
        addRenderableWidget(this.presetList);
        this.presetList.setPresets(this.presets, this.selectedPreset);

        int categoryY = this.panelTop + this.panelH - 98;
        this.categoryBox = new EditBox(this.font, this.panelLeft, categoryY, this.panelW, 18,
                Component.translatable("freebuilding.gui.category"));
        this.categoryBox.setMaxLength(32);
        this.categoryBox.setHint(Component.translatable("freebuilding.gui.category_hint"));
        this.categoryBox.setResponder(value -> {
            if (this.updatingSelectionWidgets || getSelectedName() == null) {
                return;
            }
            String category = CityConfig.normalizeCategory(value);
            PresetSetting setting = this.config.setting(getSelectedName());
            if (!setting.category.equals(category)) {
                setting.category = category;
                this.config.ensureCategory(category);
                markDirty();
            }
            if (this.categoryWeightSlider != null) {
                this.categoryWeightSlider.setIntValue(getSelectedCategoryWeight());
            }
        });
        addRenderableWidget(this.categoryBox);

        this.categoryWeightSlider = new IntSlider(this.panelLeft, this.panelTop + this.panelH - 78,
                this.panelW, 18, "freebuilding.gui.category_weight", 0, 100,
                getSelectedCategoryWeight(), value -> {
            setSelectedCategoryWeight(value);
            markDirty();
        });
        addRenderableWidget(this.categoryWeightSlider);

        this.weightSlider = new IntSlider(this.panelLeft, this.panelTop + this.panelH - 58,
                this.panelW, 18, "freebuilding.gui.preset_weight", 0, 100, getSelectedWeight(), value -> {
            setSelectedWeight(value);
            markDirty();
        });
        addRenderableWidget(this.weightSlider);

        int buttonY = this.panelTop + this.panelH - 30;
        int half = this.panelW / 2 - 2;
        this.enableButton = Button.builder(Component.empty(), button -> {
            toggleSelected();
            updateSelectionWidgets();
        }).bounds(this.panelLeft, buttonY, half, 18).build();
        this.deleteButton = Button.builder(Component.translatable("freebuilding.gui.delete"), button -> deleteSelected())
                .bounds(this.panelLeft + half + 4, buttonY, half, 18)
                .build();
        addRenderableWidget(this.enableButton);
        addRenderableWidget(this.deleteButton);
    }

    private void initLayoutTab() {
        int x = this.panelLeft;
        int w = this.panelW;
        int y = this.panelTop;
        addRenderableWidget(CycleButton.builder((CityStyle style) -> Component.translatable(style.translationKey()))
                .withValues(CityStyle.values())
                .withInitialValue(this.config.style)
                .create(x, y, w, 18, Component.translatable("freebuilding.gui.style"), (button, value) -> {
                    this.config.style = value;
                    markDirty();
                }));
        addRenderableWidget(CycleButton.builder((RotationMode mode) -> Component.translatable(mode.translationKey()))
                .withValues(RotationMode.values())
                .withInitialValue(this.config.rotation)
                .create(x, y + 20, w, 18, Component.translatable("freebuilding.gui.rotation"), (button, value) -> {
                    this.config.rotation = value;
                    markDirty();
                }));
        addRenderableWidget(new IntSlider(x, y + 40, w, 18, "freebuilding.gui.plot", 12, 64,
                this.config.plotSize, value -> {
            this.config.plotSize = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 60, w, 18, "freebuilding.gui.road", 3, 9,
                this.config.roadWidth, value -> {
            this.config.roadWidth = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 80, w, 18, "freebuilding.gui.grid", 3, 15,
                this.config.gridSize, value -> {
            this.config.gridSize = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 100, w, 18, "freebuilding.gui.jitter", 0, 4,
                this.config.jitter, value -> {
            this.config.jitter = value;
            markDirty();
        }));
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.fit_plot"), button -> {
            this.config.plotSize = requiredPlotSize();
            markDirty();
        }).bounds(x, y + 120, w, 18).build());
    }

    private void initDecorTab() {
        int x = this.panelLeft;
        int w = this.panelW;
        int y = this.panelTop;
        addRenderableWidget(new IntSlider(x, y, w, 18, "freebuilding.gui.ruin", 0, 100,
                this.config.ruinChance, value -> {
            this.config.ruinChance = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 20, w, 18, "freebuilding.gui.lamp", 0, 100,
                this.config.lampChance, value -> {
            this.config.lampChance = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 40, w, 18, "freebuilding.gui.park", 0, 100,
                this.config.parkChance, value -> {
            this.config.parkChance = value;
            markDirty();
        }));
        addRenderableWidget(Button.builder(previewModeLabel(), button -> {
                    this.previewMode = this.previewMode == PreviewMode.MANUAL ? PreviewMode.AUTO : PreviewMode.MANUAL;
                    button.setMessage(previewModeLabel());
                    markDirty();
                    this.previewCooldown = 0;
                })
                .bounds(x, y + 60, w, 18)
                .build());
    }

    private void initWorldTab() {
        int x = this.panelLeft;
        int w = this.panelW;
        int y = this.panelTop;
        this.worldgenCheck = new Checkbox(x, y, w, 18,
                Component.translatable("freebuilding.gui.worldgen"), this.config.worldgenEnabled) {
            @Override
            public void onPress() {
                super.onPress();
                config.worldgenEnabled = this.selected();
                markDirty();
            }
        };
        addRenderableWidget(this.worldgenCheck);
        addRenderableWidget(new IntSlider(x, y + 20, w, 18, "freebuilding.gui.worldgen_chance", 0, 100,
                this.config.worldgenChance, value -> {
            this.config.worldgenChance = value;
            markDirty();
        }));

        this.seedBox = new EditBox(this.font, x, y + 42, Math.max(40, w - 52), 18,
                Component.translatable("freebuilding.gui.seed"));
        this.seedBox.setMaxLength(20);
        this.seedBox.setValue(Long.toString(this.config.seed));
        this.seedBox.setResponder(value -> {
            try {
                this.config.seed = Long.parseLong(value.trim());
                markDirty();
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(this.seedBox);
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.random"), button -> {
            this.config.seed = new Random().nextLong();
            if (this.seedBox != null) {
                this.seedBox.setValue(Long.toString(this.config.seed));
            }
            markDirty();
        }).bounds(x + w - 48, y + 42, 48, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.locate"), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("fb city locate");
            }
        }).bounds(x, y + 64, w, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.import_lostcities"), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("fb import lostcities");
            }
        }).bounds(x, y + 86, w, 18).build());
    }

    private void initRuralTab() {
        int x = this.panelLeft;
        int w = this.panelW;
        int y = this.panelTop;
        addRenderableWidget(new Checkbox(x, y, w, 18,
                Component.translatable("freebuilding.gui.rural_enabled"), this.config.ruralEnabled) {
            @Override
            public void onPress() {
                super.onPress();
                config.ruralEnabled = this.selected();
                markDirty();
            }
        });
        addRenderableWidget(CycleButton.builder((RuralMode mode) -> Component.translatable(mode.translationKey()))
                .withValues(RuralMode.values())
                .withInitialValue(this.config.ruralMode)
                .create(x, y + 20, w, 18, Component.translatable("freebuilding.gui.rural_mode"), (button, value) -> {
                    this.config.ruralMode = value;
                    markDirty();
                }));
        addRenderableWidget(new IntSlider(x, y + 40, w, 18, "freebuilding.gui.rural_chance", 0, 100,
                this.config.ruralChance, value -> {
            this.config.ruralChance = value;
            markDirty();
        }));
        addRenderableWidget(new IntSlider(x, y + 60, w, 18, "freebuilding.gui.rural_plot", 12, 32,
                this.config.ruralPlotSize, value -> {
            this.config.ruralPlotSize = value;
            markDirty();
        }));
    }

    private void initBottomButtons() {
        int buttonWidth = Math.min(96, Math.max(60, (this.width - 28) / 4));
        int y = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.refresh"), button -> {
            this.previewCooldown = 0;
            markDirty();
        }).bounds(12, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.build"), button -> confirmBuild())
                .bounds(16 + buttonWidth, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.save_preview"), button -> savePreview())
                .bounds(20 + buttonWidth * 2, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("freebuilding.gui.done"), button -> onClose())
                .bounds(24 + buttonWidth * 3, y, buttonWidth, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanels(graphics);
        drawPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSummary(graphics);
    }

    private void drawPanels(GuiGraphics graphics) {
        graphics.fill(this.panelLeft - 3, this.panelTop - 3,
                this.panelLeft + this.panelW + 3, this.panelTop + this.panelH + 3, 0xD010141A);
        graphics.fill(this.previewX - 3, this.previewY - 3,
                this.previewX + this.previewW + 3, this.previewY + this.previewH + 3, 0xD010141A);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 4, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("freebuilding.gui.preview"),
                this.previewX, this.previewY - 12, 0xFF9AA0A6, false);
    }

    private void drawPreview(GuiGraphics graphics) {
        int availableH = Math.max(40, this.previewH - 26);
        int drawSize = Math.min(this.previewW, availableH);
        int drawX = this.previewX + (this.previewW - drawSize) / 2;
        int drawY = this.previewY;
        if (PreviewTexture.isReady()) {
            int texW = Math.max(1, PreviewTexture.width());
            int texH = Math.max(1, PreviewTexture.height());
            graphics.blit(PreviewTexture.ID, drawX, drawY, drawSize, drawSize,
                    0.0F, 0.0F, texW, texH, texW, texH);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("freebuilding.gui.preview.waiting"),
                    drawX + drawSize / 2, drawY + drawSize / 2 - 4, 0xFF9AA0A6);
        }
        graphics.fill(drawX - 1, drawY - 1, drawX + drawSize + 1, drawY, 0xFF40C0D0);
        graphics.fill(drawX - 1, drawY + drawSize, drawX + drawSize + 1, drawY + drawSize + 1, 0xFF40C0D0);
        graphics.fill(drawX - 1, drawY, drawX, drawY + drawSize, 0xFF40C0D0);
        graphics.fill(drawX + drawSize, drawY, drawX + drawSize + 1, drawY + drawSize, 0xFF40C0D0);
    }

    private void drawSummary(GuiGraphics graphics) {
        int y = this.previewY + Math.max(40, this.previewH - 26) + 4;
        String text = this.summary == null ? Component.translatable("freebuilding.gui.preview.none").getString() : this.summary;
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(text), this.previewW);
        int maxLines = Math.min(3, lines.size());
        for (int i = 0; i < maxLines; i++) {
            graphics.drawString(this.font, lines.get(i), this.previewX, y + i * 10, 0xFFB0BEC5, false);
        }
    }

    @Override
    public void tick() {
        if (this.dirty) {
            if (this.previewCooldown > 0) {
                this.previewCooldown--;
            }
            if (this.previewCooldown <= 0) {
                sendConfig();
                sendPreview();
                this.dirty = false;
                this.previewCooldown = 8;
            }
        }
    }

    private void markDirty() {
        this.dirty = true;
        this.previewCooldown = 8;
    }

    private void sendConfig() {
        ModNetwork.CHANNEL.sendToServer(new UpdateConfigPacket(this.config.toJson()));
    }

    private void sendPreview() {
        ModNetwork.CHANNEL.sendToServer(new PreviewRequestPacket(this.config.toJson(),
                this.previewMode == PreviewMode.AUTO));
    }

    public void onPreview(byte[] png, String summary) {
        this.previewPng = png;
        this.summary = summary;
    }

    public void onPresetList(List<PresetInfo> newPresets, CityConfig newConfig) {
        String selectedName = getSelectedName();
        this.presets = new ArrayList<>(newPresets);
        this.config = newConfig.copy();
        this.config.validate();
        this.selectedPreset = -1;
        if (selectedName != null) {
            for (int i = 0; i < this.presets.size(); i++) {
                if (this.presets.get(i).name().equals(selectedName)) {
                    this.selectedPreset = i;
                    break;
                }
            }
        }
        if (this.selectedPreset < 0 && !this.presets.isEmpty()) {
            this.selectedPreset = 0;
        }
        this.rebuildWidgets();
    }

    public void selectPreset(int index) {
        if (index < 0 || index >= this.presets.size()) {
            return;
        }
        this.selectedPreset = index;
        this.config.ensurePreset(this.presets.get(index).name());
        updateSelectionWidgets();
        if (this.presetList != null) {
            this.presetList.select(index);
        }
    }

    private void updateSelectionWidgets() {
        boolean valid = this.selectedPreset >= 0 && this.selectedPreset < this.presets.size();
        this.updatingSelectionWidgets = true;
        if (this.categoryBox != null) {
            this.categoryBox.active = valid;
            this.categoryBox.setValue(valid ? getSelectedCategory() : "");
        }
        this.updatingSelectionWidgets = false;
        if (this.categoryWeightSlider != null) {
            this.categoryWeightSlider.active = valid;
            this.categoryWeightSlider.setIntValue(getSelectedCategoryWeight());
        }
        if (this.weightSlider != null) {
            this.weightSlider.active = valid;
            this.weightSlider.setIntValue(getSelectedWeight());
        }
        if (this.enableButton != null) {
            this.enableButton.active = valid;
            this.enableButton.setMessage(Component.translatable(getSelectedEnabled()
                    ? "freebuilding.gui.disable"
                    : "freebuilding.gui.enable"));
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = valid;
        }
    }

    private String getSelectedName() {
        if (this.selectedPreset >= 0 && this.selectedPreset < this.presets.size()) {
            return this.presets.get(this.selectedPreset).name();
        }
        return null;
    }

    private int getSelectedWeight() {
        String name = getSelectedName();
        if (name == null) {
            return 0;
        }
        return this.config.setting(name).weight;
    }

    private void setSelectedWeight(int weight) {
        String name = getSelectedName();
        if (name != null) {
            this.config.setting(name).weight = weight;
        }
    }

    private String getSelectedCategory() {
        String name = getSelectedName();
        return name == null ? CityConfig.normalizeCategory(null) : this.config.setting(name).category;
    }

    private int getSelectedCategoryWeight() {
        return this.config.categorySetting(getSelectedCategory()).weight;
    }

    private void setSelectedCategoryWeight(int weight) {
        this.config.categorySetting(getSelectedCategory()).weight = weight;
    }

    private boolean getSelectedEnabled() {
        String name = getSelectedName();
        return name == null || this.config.setting(name).enabled;
    }

    public int plotSize() {
        return this.config.plotSize;
    }

    public boolean isPresetEnabled(String name) {
        PresetSetting setting = this.config.presets.get(name);
        return setting == null || setting.enabled;
    }

    public String presetCategory(String name) {
        return this.config.setting(name).category;
    }

    private int requiredPlotSize() {
        int required = 12;
        for (PresetInfo info : this.presets) {
            PresetSetting setting = this.config.presets.get(info.name());
            if (setting != null && setting.enabled && setting.weight > 0
                    && this.config.categorySetting(setting.category).weight > 0) {
                required = Math.max(required, Math.max(info.width(), info.depth()));
            }
        }
        return Mth.clamp(required, 12, 64);
    }

    private void toggleSelected() {
        String name = getSelectedName();
        if (name != null) {
            var setting = this.config.setting(name);
            setting.enabled = !setting.enabled;
            markDirty();
        }
    }

    private void deleteSelected() {
        String name = getSelectedName();
        if (name == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ModNetwork.CHANNEL.sendToServer(new DeletePresetPacket(name));
            }
            minecraft.setScreen(this);
        }, Component.translatable("freebuilding.gui.delete.title"),
                Component.translatable("freebuilding.gui.delete.message", name),
                Component.translatable("freebuilding.gui.confirm.yes"),
                Component.translatable("freebuilding.gui.confirm.no")));
    }

    private void confirmBuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        CityConfig copy = this.config.copy();
        copy.validate();
        copy.ensureSeed();
        CityLayout layout = CityLayout.manual(minecraft.player.blockPosition(), copy, List.of(), copy.seed);
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ModNetwork.CHANNEL.sendToServer(new BuildCityPacket(this.config.toJson()));
                minecraft.setScreen(null);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("freebuilding.gui.confirm.title"),
                Component.translatable("freebuilding.gui.confirm.message",
                        layout.totalSize, layout.totalSize, layout.originX, layout.minZ(), layout.groundY),
                Component.translatable("freebuilding.gui.confirm.yes"),
                Component.translatable("freebuilding.gui.confirm.no")));
    }

    private void savePreview() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (this.previewPng == null || this.previewPng.length == 0) {
            minecraft.player.displayClientMessage(Component.translatable("freebuilding.msg.preview.empty"), false);
            return;
        }
        try {
            Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            String fileName = "freebuilding_preview_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT))
                    + ".png";
            Path file = directory.resolve(fileName);
            Files.write(file, this.previewPng);
            minecraft.player.displayClientMessage(Component.translatable("freebuilding.msg.preview.saved",
                    file.toAbsolutePath().toString()), false);
        } catch (Exception exception) {
            minecraft.player.displayClientMessage(Component.translatable("freebuilding.msg.preview.failed"), false);
        }
    }

    private Component previewModeLabel() {
        return Component.translatable("freebuilding.gui.preview_mode",
                Component.translatable(this.previewMode == PreviewMode.AUTO
                        ? "freebuilding.gui.mode.auto"
                        : "freebuilding.gui.mode.manual"));
    }

    @Override
    public void onClose() {
        sendConfig();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final String key;
        private final IntConsumer consumer;

        private IntSlider(int x, int y, int width, int height, String key, int min, int max, int value,
                          IntConsumer consumer) {
            super(x, y, width, height, Component.empty(), (Mth.clamp(value, min, max) - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.key = key;
            this.consumer = consumer;
            updateMessage();
        }

        private int intValue() {
            return this.min + (int) Math.round(this.value * (this.max - this.min));
        }

        private void setIntValue(int value) {
            this.value = (Mth.clamp(value, this.min, this.max) - this.min) / (double) (this.max - this.min);
            updateMessage();
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
