package com.cukkoo.soundculling.integration;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modern, colorful configuration screen with side-by-side categories
 * and advanced category-specific culling limit sliders.
 */
public class SoundCullingConfigScreen extends Screen {

    private final Screen parent;
    private final SoundCullingConfig config;

    // Live editing values
    private int maxTotal;
    private int windowTicks;
    private int regionSize;
    private boolean debugLogging;

    private int limitHostile;
    private int limitNeutral;
    private int limitBlock;
    private int limitAmbient;
    private int limitDefault;

    public SoundCullingConfigScreen(Screen parent) {
        super(Component.literal("Dynamic Sound Culling"));
        this.parent = parent;
        this.config = SoundCulling.getConfig();
        
        this.maxTotal = config.maxTotalPerRegion;
        this.windowTicks = config.windowTicks;
        this.regionSize = (int) config.regionSize;
        this.debugLogging = config.debugLogging;

        this.limitHostile = config.limitHostile;
        this.limitNeutral = config.limitNeutral;
        this.limitBlock = config.limitBlock;
        this.limitAmbient = config.limitAmbient;
        this.limitDefault = config.limitDefault;
    }

    @Override
    protected void init() {
        refreshUI();
    }

    private void refreshUI() {
        this.clearWidgets();

        int cx = this.width / 2;
        int leftColX = cx - 155;
        int rightColX = cx + 15;
        int startY = 45;
        int rowH = 22;

        // --- LEFT COLUMN: SYSTEM ---
        int leftStartY = startY + 15;
        
        // Row 1: Region Total
        addNumRow(leftColX, leftStartY,
                () -> { maxTotal = Math.max(1, maxTotal - 1); refreshUI(); },
                () -> { maxTotal = Math.min(100, maxTotal + 1); refreshUI(); },
                "Max total combined sounds within a single region"
        );

        // Row 2: Time Window
        addNumRow(leftColX, leftStartY + rowH,
                () -> { windowTicks = Math.max(5, windowTicks - 5); refreshUI(); },
                () -> { windowTicks = Math.min(200, windowTicks + 5); refreshUI(); },
                "Time window in game ticks (20 ticks = 1 second)"
        );

        // Row 3: Region Size
        addNumRow(leftColX, leftStartY + rowH * 2,
                () -> { regionSize = Math.max(2, regionSize - 2); refreshUI(); },
                () -> { regionSize = Math.min(64, regionSize + 2); refreshUI(); },
                "Region boundaries in blocks (16 blocks = 1 chunk)"
        );

        // Row 4: Debug Logging (Toggle)
        this.addRenderableWidget(
                Button.builder(Component.literal(debugLogging ? "ENABLED" : "DISABLED"), btn -> {
                    debugLogging = !debugLogging;
                    refreshUI();
                }).bounds(leftColX + 90, leftStartY + rowH * 3, 56, 16).build()
        );


        // --- RIGHT COLUMN: CATEGORY LIMITS ---
        int rightStartY = startY + 15;

        // Row 1: Hostile Limit
        addNumRow(rightColX, rightStartY,
                () -> { limitHostile = Math.max(1, limitHostile - 1); refreshUI(); },
                () -> { limitHostile = Math.min(50, limitHostile + 1); refreshUI(); },
                "Sound limit for Hostile mobs (Zombies, Creepers, etc.)"
        );

        // Row 2: Neutral Limit
        addNumRow(rightColX, rightStartY + rowH,
                () -> { limitNeutral = Math.max(1, limitNeutral - 1); refreshUI(); },
                () -> { limitNeutral = Math.min(50, limitNeutral + 1); refreshUI(); },
                "Sound limit for Neutral mobs (Cows, Sheep, etc.)"
        );

        // Row 3: Block Limit
        addNumRow(rightColX, rightStartY + rowH * 2,
                () -> { limitBlock = Math.max(1, limitBlock - 1); refreshUI(); },
                () -> { limitBlock = Math.min(50, limitBlock + 1); refreshUI(); },
                "Sound limit for blocks (Pistons, Spawners, etc.)"
        );

        // Row 4: Ambient Limit
        addNumRow(rightColX, rightStartY + rowH * 3,
                () -> { limitAmbient = Math.max(1, limitAmbient - 1); refreshUI(); },
                () -> { limitAmbient = Math.min(50, limitAmbient + 1); refreshUI(); },
                "Sound limit for environmental ambient sounds"
        );

        // Row 5: Default Limit
        addNumRow(rightColX, rightStartY + rowH * 4,
                () -> { limitDefault = Math.max(1, limitDefault - 1); refreshUI(); },
                () -> { limitDefault = Math.min(50, limitDefault + 1); refreshUI(); },
                "Default sound limit for categories not listed above"
        );


        // --- BOTTOM NAVIGATION BAR ---
        int btnWidth = 110;
        int gap = 8;
        int bottomY = this.height - 25;

        this.addRenderableWidget(
                Button.builder(Component.literal("Save & Close"), btn -> {
                    applyAndSave();
                    this.onClose();
                }).bounds(cx - btnWidth - gap, bottomY, btnWidth, 18).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> {
                    this.onClose();
                }).bounds(cx + gap, bottomY, btnWidth, 18).build()
        );

        // Reset to default button
        this.addRenderableWidget(
                Button.builder(Component.literal("Reset"), btn -> {
                    maxTotal = 6;
                    windowTicks = 20;
                    regionSize = 16;
                    debugLogging = false;
                    limitHostile = 3;
                    limitNeutral = 2;
                    limitBlock = 5;
                    limitAmbient = 4;
                    limitDefault = 3;
                    refreshUI();
                }).bounds(cx + 122, bottomY, 38, 18)
                        .tooltip(Tooltip.create(Component.literal("Reset all values to factory defaults")))
                        .build()
        );
    }

    private void addNumRow(int x, int y, Runnable onMinus, Runnable onPlus, String tooltip) {
        this.addRenderableWidget(
                Button.builder(Component.literal("-"), btn -> onMinus.run())
                        .bounds(x + 90, y, 16, 16).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("+"), btn -> onPlus.run())
                        .bounds(x + 130, y, 16, 16)
                        .tooltip(Tooltip.create(Component.literal(tooltip)))
                        .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int leftColX = cx - 155;
        int rightColX = cx + 15;
        int startY = 45;
        int rowH = 22;

        // Headers
        extractor.text(this.font, Component.literal("Dynamic Sound Culling"),
                cx - this.font.width("Dynamic Sound Culling") / 2, 8, 0xFFFFAA00);
        extractor.text(this.font, Component.literal("Configuration Panel"),
                cx - this.font.width("Configuration Panel") / 2, 20, 0xFF888888);

        // --- Left Column Rendering ---
        extractor.text(this.font, Component.literal("System Configuration"), leftColX, startY, 0xFF55FFFF);
        extractor.horizontalLine(leftColX, leftColX + 146, startY + 10, 0xFF333333);

        int leftStartY = startY + 15;

        // Row 1: Region Total
        extractor.text(this.font, Component.literal("Region Total"), leftColX, leftStartY + 4, 0xFFCCCCCC);
        String totalVal = String.valueOf(maxTotal);
        extractor.text(this.font, Component.literal(totalVal), leftColX + 114 - this.font.width(totalVal) / 2, leftStartY + 4, 0xFFFFFFFF);

        // Row 2: Time Window
        extractor.text(this.font, Component.literal("Time Window"), leftColX, leftStartY + rowH + 4, 0xFFCCCCCC);
        String windowVal = windowTicks + "t";
        extractor.text(this.font, Component.literal(windowVal), leftColX + 114 - this.font.width(windowVal) / 2, leftStartY + rowH + 4, 0xFFFFFFFF);

        // Row 3: Region Size
        extractor.text(this.font, Component.literal("Region Size"), leftColX, leftStartY + rowH * 2 + 4, 0xFFCCCCCC);
        String sizeVal = regionSize + "b";
        extractor.text(this.font, Component.literal(sizeVal), leftColX + 114 - this.font.width(sizeVal) / 2, leftStartY + rowH * 2 + 4, 0xFFFFFFFF);

        // Row 4: Debug Logs
        extractor.text(this.font, Component.literal("Debug Logs"), leftColX, leftStartY + rowH * 3 + 4, 0xFFCCCCCC);


        // --- Right Column Rendering ---
        extractor.text(this.font, Component.literal("Sound Category Limits"), rightColX, startY, 0xFF55FF55);
        extractor.horizontalLine(rightColX, rightColX + 146, startY + 10, 0xFF333333);

        int rightStartY = startY + 15;

        // Row 1: Hostile Limit
        extractor.text(this.font, Component.literal("Hostile Mobs"), rightColX, rightStartY + 4, 0xFFE57373);
        String hostileVal = String.valueOf(limitHostile);
        extractor.text(this.font, Component.literal(hostileVal), rightColX + 114 - this.font.width(hostileVal) / 2, rightStartY + 4, 0xFFFFFFFF);

        // Row 2: Neutral Limit
        extractor.text(this.font, Component.literal("Neutral Mobs"), rightColX, rightStartY + rowH + 4, 0xFF81C784);
        String neutralVal = String.valueOf(limitNeutral);
        extractor.text(this.font, Component.literal(neutralVal), rightColX + 114 - this.font.width(neutralVal) / 2, rightStartY + rowH + 4, 0xFFFFFFFF);

        // Row 3: Block Limit
        extractor.text(this.font, Component.literal("Blocks/Spawners"), rightColX, rightStartY + rowH * 2 + 4, 0xFFFFB74D);
        String blockVal = String.valueOf(limitBlock);
        extractor.text(this.font, Component.literal(blockVal), rightColX + 114 - this.font.width(blockVal) / 2, rightStartY + rowH * 2 + 4, 0xFFFFFFFF);

        // Row 4: Ambient Limit
        extractor.text(this.font, Component.literal("Ambient Sounds"), rightColX, rightStartY + rowH * 3 + 4, 0xFF64B5F6);
        String ambientVal = String.valueOf(limitAmbient);
        extractor.text(this.font, Component.literal(ambientVal), rightColX + 114 - this.font.width(ambientVal) / 2, rightStartY + rowH * 3 + 4, 0xFFFFFFFF);

        // Row 5: Default Limit
        extractor.text(this.font, Component.literal("Default Limit"), rightColX, rightStartY + rowH * 4 + 4, 0xFFB0BEC5);
        String defaultVal = String.valueOf(limitDefault);
        extractor.text(this.font, Component.literal(defaultVal), rightColX + 114 - this.font.width(defaultVal) / 2, rightStartY + rowH * 4 + 4, 0xFFFFFFFF);

        // --- Bottom Stats ---
        int sepY = this.height - 42;
        extractor.horizontalLine(cx - 155, cx + 161, sepY, 0xFF444444);

        int statsY = sepY + 5;
        String statsText = "Session Culled: " + SoundCullingTracker.getTotalCulled() + " sounds";
        extractor.text(this.font, Component.literal(statsText),
                cx - this.font.width(statsText) / 2, statsY, 0xFF81C784);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        this.extractMenuBackground(extractor);
    }

    private void applyAndSave() {
        config.maxTotalPerRegion = maxTotal;
        config.windowTicks = windowTicks;
        config.regionSize = regionSize;
        config.debugLogging = debugLogging;
        
        config.limitHostile = limitHostile;
        config.limitNeutral = limitNeutral;
        config.limitBlock = limitBlock;
        config.limitAmbient = limitAmbient;
        config.limitDefault = limitDefault;

        config.save();
        SoundCulling.LOGGER.info("[SoundCulling] GUI settings saved successfully.");
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
