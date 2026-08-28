package com.cukkoo.soundculling.integration;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.SoundRuleEngine;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sound Culling 2.0 configuration UI.
 *
 * The default view exposes simple presets and live health information. Technical
 * region/window controls are moved into Advanced so normal users do not need to
 * understand tracker internals.
 */
public class SoundCullingConfigScreen extends Screen {

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String actionLabel(String action) {
        String normalized =
                SoundCullingConfig.normalizeAction(action);

        return tr(
                "soundculling.ui.action."
                        + normalized.toLowerCase(Locale.ROOT)
        );
    }

    private enum Page {
        OVERVIEW,
        PERFORMANCE,
        LIVE,
        ADVANCED
    }

    private final Screen parent;
    private final SoundCullingConfig config;

    private Page page = Page.OVERVIEW;

    private boolean enabled;
    private String preset;
    private boolean adaptiveCulling;
    private int adaptiveStart;
    private int adaptiveFull;
    private double adaptiveStrength;
    private int maxTotal;
    private int windowTicks;
    private int regionSize;
    private int neighborRadius;
    private boolean debugLogging;
    private boolean directionalPriority;
    private boolean distancePriority;
    private boolean protectPlayerSounds;

    private int limitHostile;
    private int limitNeutral;
    private int limitBlock;
    private int limitAmbient;
    private int limitDefault;

    private final List<SoundCullingConfig.SoundRule> rules = new ArrayList<>();
    private String selectedSoundId;
    private String ruleStatus = "";

    public SoundCullingConfigScreen(Screen parent) {
        super(Component.literal(tr("soundculling.ui.title")));
        this.parent = parent;
        this.config = SoundCulling.getConfig();

        enabled = config.enabled;
        preset = config.preset;
        adaptiveCulling = config.adaptiveCulling;
        adaptiveStart = config.adaptiveStartPerSecond;
        adaptiveFull = config.adaptiveFullPerSecond;
        adaptiveStrength = config.adaptiveStrength;
        maxTotal = config.maxTotalPerRegion;
        windowTicks = config.windowTicks;
        regionSize = (int) config.regionSize;
        neighborRadius = config.spatialNeighborRadius;
        debugLogging = config.debugLogging;
        directionalPriority = config.directionalPriority;
        distancePriority = config.distancePriority;
        protectPlayerSounds = config.protectPlayerSounds;

        limitHostile = config.limitHostile;
        limitNeutral = config.limitNeutral;
        limitBlock = config.limitBlock;
        limitAmbient = config.limitAmbient;
        limitDefault = config.limitDefault;

        rules.addAll(config.copyRules());
    }

    /*
     * This is a live diagnostics/configuration screen.
     *
     * Keeping the world ticking allows:
     * - Sound rate
     * - Adaptive pressure
     * - Live Sounds
     * - tracked regions
     *
     * to update while the screen is open.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        refreshUI();
    }

    private void refreshUI() {
        clearWidgets();
        Layout layout = layout();

        int tabGap = 4;
        int tabX = layout.panelX + 14;
        int tabY = layout.panelY + 36;
        int tabWidth = (layout.panelWidth - 28 - tabGap * 3) / 4;

        addTab(tabX, tabY, tabWidth, tr("soundculling.ui.tab.overview"), Page.OVERVIEW);
        addTab(tabX + tabWidth + tabGap, tabY, tabWidth, tr("soundculling.ui.tab.performance"), Page.PERFORMANCE);
        addTab(tabX + (tabWidth + tabGap) * 2, tabY, tabWidth, tr("soundculling.ui.tab.live"), Page.LIVE);
        addTab(tabX + (tabWidth + tabGap) * 3, tabY, tabWidth, tr("soundculling.ui.tab.advanced"), Page.ADVANCED);

        switch (page) {
            case OVERVIEW -> buildOverview(layout);
            case PERFORMANCE -> buildPerformance(layout);
            case LIVE -> buildLive(layout);
            case ADVANCED -> buildAdvanced(layout);
        }

        int bottomY = layout.panelY + layout.panelHeight - 25;

        int saveWidth = 104;
        int cancelWidth = 72;
        int right = layout.panelX + layout.panelWidth - 14;

        addRenderableWidget(
                Button.builder(Component.literal(tr("soundculling.ui.button.save_close")), button -> {
                    applyAndSave();
                    onClose();
                }).bounds(
                        right - saveWidth,
                        bottomY,
                        saveWidth,
                        18
                ).build()
        );

        addRenderableWidget(
                Button.builder(Component.literal(tr("soundculling.ui.button.cancel")), button -> onClose())
                        .bounds(
                                right - saveWidth - 6 - cancelWidth,
                                bottomY,
                                cancelWidth,
                                18
                        )
                        .build()
        );

        hideVanillaButtonSkins();
    }

    private void hideVanillaButtonSkins() {
        for (var child : children()) {
            if (child instanceof Button button) {
                button.setAlpha(0.0f);
            }
        }
    }
    private void addTab(int x, int y, int width, String label, Page target) {
        String text = page == target ? label : label;
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            page = target;
            ruleStatus = "";
            refreshUI();
        }).bounds(x, y, width, 18).build());
    }

    private void buildOverview(Layout layout) {
        int x = layout.panelX + 24;
        int y = layout.panelY + 78;
        int width = 142;

        addRenderableWidget(
                Button.builder(
                        Component.literal(enabled ? tr("soundculling.ui.sound_culling.on") : tr("soundculling.ui.sound_culling.off")),
                        button -> {
                            enabled = !enabled;
                            refreshUI();
                        }
                ).bounds(x, y, width, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(tr("soundculling.ui.preset", prettyPreset())),
                        button -> {
                            cyclePreset();
                            refreshUI();
                        }
                ).bounds(x, y + 28, width, 20)
                        .tooltip(Tooltip.create(
                                Component.literal(tr("soundculling.ui.tooltip.preset_cycle"))
                        ))
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(adaptiveCulling
                                ? tr("soundculling.ui.adaptive_engine.on")
                                : tr("soundculling.ui.adaptive_engine.off")),
                        button -> {
                            adaptiveCulling = !adaptiveCulling;
                            markCustom();
                            refreshUI();
                        }
                ).bounds(x, y + 56, width, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(tr("soundculling.ui.button.reset_stats")),
                        button -> {
                            SoundCullingTracker.resetStats();
                            refreshUI();
                        }
                ).bounds(x, y + 84, width, 20).build()
        );
    }

    private void buildPerformance(Layout layout) {
        int left = layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;
        int right = layout.panelX + layout.panelWidth / 2 + 4 + (layout.panelWidth / 2 - 188) / 2;
        int y = layout.panelY + 82;
        int row = 24;

        addNumRow(left, y, adaptiveStart, 10, 500,
                value -> adaptiveStart = value,
                tr("soundculling.ui.label.adaptive_start"), tr("soundculling.ui.tooltip.adaptive_start"));

        addNumRow(left, y + row, adaptiveFull, adaptiveStart + 10, 1000,
                value -> adaptiveFull = value,
                tr("soundculling.ui.label.adaptive_full"), tr("soundculling.ui.tooltip.adaptive_full"));

        addNumRow(left, y + row * 2, limitDefault, 1, 50,
                value -> limitDefault = value,
                tr("soundculling.ui.label.default"), tr("soundculling.ui.tooltip.default"));

        addNumRow(left, y + row * 3, limitBlock, 1, 50,
                value -> limitBlock = value,
                tr("soundculling.ui.label.blocks"), tr("soundculling.ui.tooltip.blocks"));

        addNumRow(right, y, limitHostile, 1, 50,
                value -> limitHostile = value,
                tr("soundculling.ui.label.hostile"), tr("soundculling.ui.tooltip.hostile"));

        addNumRow(right, y + row, limitNeutral, 1, 50,
                value -> limitNeutral = value,
                tr("soundculling.ui.label.neutral"), tr("soundculling.ui.tooltip.neutral"));

        addNumRow(right, y + row * 2, limitAmbient, 1, 50,
                value -> limitAmbient = value,
                tr("soundculling.ui.label.ambient"), tr("soundculling.ui.tooltip.ambient"));

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                protectPlayerSounds
                                        ? tr("soundculling.ui.player_protection.on")
                                        : tr("soundculling.ui.player_protection.off")
                        ),
                        button -> {
                            protectPlayerSounds = !protectPlayerSounds;
                            markCustom();
                            refreshUI();
                        }
                ).bounds(right, y + row * 3, 170, 18).build()
        );
    }

    private void buildLive(Layout layout) {
        int top = layout.panelY + 82;

        int contentLeft = layout.panelX + 14;
        int contentRight = layout.panelX + layout.panelWidth - 14;

        int actionCardWidth = 188;
        int actionCardX = contentRight - actionCardWidth;
        int innerPad = 8;

        int left = contentLeft + innerPad;
        int leftCardRight = actionCardX - 7;

        int actionWidth = 82;
        int actionGap = 8;
        int actionAreaWidth = actionWidth * 2 + actionGap;
        int actionX = actionCardX + (actionCardWidth - actionAreaWidth) / 2;

        int listWidth = Math.max(
                150,
                leftCardRight - left - innerPad
        );

        int maxRecent = layout.panelHeight >= 270 ? 6 : 5;

        List<SoundCullingTracker.RecentSoundSnapshot> recent =
                SoundCullingTracker.getRecentSounds(maxRecent);

        int y = top;

        for (SoundCullingTracker.RecentSoundSnapshot sound : recent) {
            String label =
                    trim(sound.soundId(), 24)
                            + "  x" + sound.triggered()
                            + "  c" + sound.culled();

            addRenderableWidget(
                    Button.builder(
                            Component.literal(label),
                            button -> {
                                selectedSoundId = sound.soundId();
                                ruleStatus = "";
                                refreshUI();
                            }
                    ).bounds(left, y, listWidth, 18)
                            .tooltip(Tooltip.create(
                                    Component.literal(sound.soundId())
                            ))
                            .build()
            );

            y += 21;
        }

        boolean hasSelection =
                selectedSoundId != null && !selectedSoundId.isBlank();

        int col1 = actionX;
        int col2 = actionX + actionWidth + actionGap;

        addRenderableWidget(
                actionButton(
                        col1, top, actionWidth,
                        tr("soundculling.ui.button.never_cull"), hasSelection,
                        () -> addRule(selectedSoundId, "NEVER_CULL")
                )
        );

        addRenderableWidget(
                actionButton(
                        col2, top, actionWidth,
                        tr("soundculling.ui.button.high_priority"), hasSelection,
                        () -> addRule(selectedSoundId, "HIGH")
                )
        );

        addRenderableWidget(
                actionButton(
                        col1, top + 22, actionWidth,
                        tr("soundculling.ui.button.aggressive"), hasSelection,
                        () -> addRule(selectedSoundId, "AGGRESSIVE")
                )
        );

        addRenderableWidget(
                actionButton(
                        col2, top + 22, actionWidth,
                        tr("soundculling.ui.button.always_cull"), hasSelection,
                        () -> addRule(selectedSoundId, "ALWAYS_CULL")
                )
        );

        addRenderableWidget(
                actionButton(
                        col1, top + 44, actionWidth,
                        tr("soundculling.ui.button.protect_mod"), hasSelection,
                        () -> addRule(
                                SoundRuleEngine.namespacePattern(selectedSoundId),
                                "NEVER_CULL"
                        )
                )
        );

        addRenderableWidget(
                actionButton(
                        col2, top + 44, actionWidth,
                        tr("soundculling.ui.button.clear_rule"), hasSelection,
                        () -> removeRule(selectedSoundId)
                )
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(tr("soundculling.ui.button.refresh")),
                        button -> refreshUI()
                ).bounds(
                        actionX,
                        top + 70,
                        actionAreaWidth,
                        18
                ).build()
        );
    }

    private Button actionButton(
            int x,
            int y,
            int width,
            String label,
            boolean active,
            Runnable action
    ) {
        Button button = Button.builder(
                Component.literal(label),
                ignored -> {
                    if (active) {
                        action.run();
                        refreshUI();
                    }
                }
        ).bounds(x, y, width, 18).build();

        button.active = active;
        return button;
    }

    private void buildAdvanced(Layout layout) {
        int left = layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;
        int right = layout.panelX + layout.panelWidth / 2 + 4 + (layout.panelWidth / 2 - 188) / 2;
        int y = layout.panelY + 82;
        int row = 24;

        addNumRow(left, y, maxTotal, 1, 100,
                value -> maxTotal = value,
                tr("soundculling.ui.label.region_total"), tr("soundculling.ui.tooltip.region_total"));

        addNumRow(left, y + row, windowTicks, 5, 200,
                value -> windowTicks = value,
                tr("soundculling.ui.label.window_ticks"), tr("soundculling.ui.tooltip.window_ticks"));

        addNumRow(left, y + row * 2, regionSize, 2, 64,
                value -> regionSize = value,
                tr("soundculling.ui.label.region_size"), tr("soundculling.ui.tooltip.region_size"));

        addNumRow(left, y + row * 3, neighborRadius, 0, 2,
                value -> neighborRadius = value,
                tr("soundculling.ui.label.neighbour_cells"), tr("soundculling.ui.tooltip.neighbour_cells"));

        addToggle(
                right, y,
                tr("soundculling.ui.label.directional_priority"),
                directionalPriority,
                value -> directionalPriority = value
        );

        addToggle(
                right, y + row,
                tr("soundculling.ui.label.distance_priority"),
                distancePriority,
                value -> distancePriority = value
        );

        addToggle(
                right, y + row * 2,
                tr("soundculling.ui.label.debug_logging"),
                debugLogging,
                value -> debugLogging = value
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(tr("soundculling.ui.button.factory_defaults")),
                        button -> {
                            applyLocalPreset("BALANCED");
                            windowTicks = 20;
                            regionSize = 16;
                            neighborRadius = 1;
                            debugLogging = false;
                            directionalPriority = true;
                            distancePriority = true;
                            protectPlayerSounds = true;

                            rules.clear();
                            rules.add(
                                    new SoundCullingConfig.SoundRule(
                                            "minecraft:entity.player.attack.*",
                                            "CRITICAL"
                                    )
                            );
                            rules.add(
                                    new SoundCullingConfig.SoundRule(
                                            "bettercombat:*",
                                            "HIGH"
                                    )
                            );

                            refreshUI();
                        }
                ).bounds(
                        right, y + row * 3, 170, 18
                ).build()
        );
    }

    private void addToggle(int x, int y, String label, boolean value, BooleanSetter setter) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (value ? tr("soundculling.ui.state.on") : tr("soundculling.ui.state.off"))), button -> {
            setter.set(!value);
            markCustom();
            refreshUI();
        }).bounds(x, y, 170, 18).build());
    }

    private void addNumRow(int x, int y, int value, int min, int max,
                           IntSetter setter, String label, String tooltip) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> {
            setter.set(Math.max(min, value - 1));
            markCustom();
            refreshUI();
        }).bounds(x + 92, y, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            setter.set(Math.min(max, value + 1));
            markCustom();
            refreshUI();
        }).bounds(x + 152, y, 18, 18)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    private void addRule(String pattern, String action) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        rules.removeIf(rule -> rule != null && pattern.equalsIgnoreCase(rule.pattern));
        rules.add(new SoundCullingConfig.SoundRule(pattern, SoundCullingConfig.normalizeAction(action)));
        preset = "CUSTOM";
        ruleStatus = tr("soundculling.ui.status.rule_set", pattern, actionLabel(action));
    }

    private void removeRule(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }

        // First prefer an exact rule for the selected sound.
        boolean removedExact = rules.removeIf(
                rule -> rule != null
                        && pattern.equalsIgnoreCase(rule.pattern)
        );

        String removedPattern = removedExact ? pattern : null;

        // Protect Mod creates namespace rules such as "minecraft:*".
        // If there is no exact rule, Clear Rule should remove the
        // namespace rule currently applying to this sound.
        if (!removedExact) {
            String namespacePattern =
                    SoundRuleEngine.namespacePattern(pattern);

            if (namespacePattern != null
                    && !namespacePattern.isBlank()
                    && !namespacePattern.equalsIgnoreCase(pattern)) {

                boolean removedNamespace = rules.removeIf(
                        rule -> rule != null
                                && namespacePattern.equalsIgnoreCase(
                                        rule.pattern
                                )
                );

                if (removedNamespace) {
                    removedPattern = namespacePattern;
                }
            }
        }

        preset = "CUSTOM";

        ruleStatus = removedPattern != null
                ? tr("soundculling.ui.status.rule_removed", removedPattern)
                : tr("soundculling.ui.status.no_rule", pattern);
    }
    private void cyclePreset() {
        String next = switch (preset == null ? "BALANCED" : preset) {
            case "BALANCED" -> "PERFORMANCE";
            case "PERFORMANCE" -> "AGGRESSIVE";
            default -> "BALANCED";
        };
        applyLocalPreset(next);
    }

    private void applyLocalPreset(String value) {
        switch (value) {
            case "PERFORMANCE" -> {
                preset = "PERFORMANCE";
                maxTotal = 6;
                limitHostile = 3;
                limitNeutral = 2;
                limitBlock = 4;
                limitAmbient = 3;
                limitDefault = 3;
                adaptiveStart = 30;
                adaptiveFull = 120;
                adaptiveStrength = 0.95;
                adaptiveCulling = true;
            }
            case "AGGRESSIVE" -> {
                preset = "AGGRESSIVE";
                maxTotal = 4;
                limitHostile = 2;
                limitNeutral = 2;
                limitBlock = 3;
                limitAmbient = 2;
                limitDefault = 2;
                adaptiveStart = 20;
                adaptiveFull = 80;
                adaptiveStrength = 1.0;
                adaptiveCulling = true;
            }
            default -> {
                preset = "BALANCED";
                maxTotal = 8;
                limitHostile = 4;
                limitNeutral = 3;
                limitBlock = 6;
                limitAmbient = 4;
                limitDefault = 4;
                adaptiveStart = 40;
                adaptiveFull = 160;
                adaptiveStrength = 0.85;
                adaptiveCulling = true;
            }
        }
    }

    private void markCustom() {
        preset = "CUSTOM";
    }

    private String prettyPreset() {
        String normalized =
                preset == null || preset.isBlank()
                        ? "BALANCED"
                        : preset.toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "PERFORMANCE" ->
                    tr("soundculling.ui.preset.performance");

            case "AGGRESSIVE" ->
                    tr("soundculling.ui.preset.aggressive");

            case "CUSTOM" ->
                    tr("soundculling.ui.preset.custom");

            default ->
                    tr("soundculling.ui.preset.balanced");
        };
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        Layout layout = layout();

        String title = tr("soundculling.ui.title");
        String subtitle = tr("soundculling.ui.subtitle");

        extractor.text(
                font,
                Component.literal(title),
                layout.panelX + 14,
                layout.panelY + 11,
                0xFFF4F7FB
        );

        if (layout.panelWidth >= 420) {
            extractor.text(
                    font,
                    Component.literal(subtitle),
                    layout.panelX
                            + layout.panelWidth
                            - font.width(subtitle)
                            - 14,
                    layout.panelY + 11,
                    0xFF8290A0
            );
        }

        int tabGap = 4;
        int tabX = layout.panelX + 14;
        int tabY = layout.panelY + 36;
        int tabWidth =
                (layout.panelWidth - 28 - tabGap * 3) / 4;

        int selectedIndex = switch (page) {
            case OVERVIEW -> 0;
            case PERFORMANCE -> 1;
            case LIVE -> 2;
            case ADVANCED -> 3;
        };

        int activeX =
                tabX
                        + selectedIndex
                        * (tabWidth + tabGap);

        // Active tab glow/accent
        extractor.fill(
                activeX + 5,
                tabY + 19,
                activeX + tabWidth - 5,
                tabY + 20,
                0xFF55A7FF
        );

        extractor.horizontalLine(
                layout.panelX + 14,
                layout.panelX + layout.panelWidth - 14,
                layout.panelY + 63,
                0xFF2B3540
        );

        switch (page) {
            case OVERVIEW -> renderOverview(extractor, layout);
            case PERFORMANCE -> renderPerformance(extractor, layout);
            case LIVE -> renderLive(extractor, layout);
            case ADVANCED -> renderAdvanced(extractor, layout);
        }

        renderModernControls(extractor, mouseX, mouseY, layout);
    }


    private void renderModernControls(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            Layout layout
    ) {
        // ----------------------------------------------------
        // TOP TABS
        // ----------------------------------------------------
        int tabGap = 4;
        int tabX = layout.panelX + 14;
        int tabY = layout.panelY + 36;
        int tabWidth =
                (layout.panelWidth - 28 - tabGap * 3) / 4;

        drawModernButton(
                extractor,
                tabX,
                tabY,
                tabWidth,
                20,
                tr("soundculling.ui.tab.overview"),
                page == Page.OVERVIEW,
                true,
                mouseX,
                mouseY
        );

        drawModernButton(
                extractor,
                tabX + tabWidth + tabGap,
                tabY,
                tabWidth,
                20,
                tr("soundculling.ui.tab.performance"),
                page == Page.PERFORMANCE,
                true,
                mouseX,
                mouseY
        );

        drawModernButton(
                extractor,
                tabX + (tabWidth + tabGap) * 2,
                tabY,
                tabWidth,
                20,
                tr("soundculling.ui.tab.live"),
                page == Page.LIVE,
                true,
                mouseX,
                mouseY
        );

        drawModernButton(
                extractor,
                tabX + (tabWidth + tabGap) * 3,
                tabY,
                tabWidth,
                20,
                tr("soundculling.ui.tab.advanced"),
                page == Page.ADVANCED,
                true,
                mouseX,
                mouseY
        );


        // ----------------------------------------------------
        // FOOTER
        // ----------------------------------------------------
        int bottomY =
                layout.panelY + layout.panelHeight - 25;

        int saveWidth = 104;
        int cancelWidth = 72;

        int rightEdge =
                layout.panelX + layout.panelWidth - 14;

        int saveX =
                rightEdge - saveWidth;

        int cancelX =
                saveX - 6 - cancelWidth;

        drawModernButton(
                extractor,
                cancelX,
                bottomY,
                cancelWidth,
                18,
                tr("soundculling.ui.button.cancel"),
                false,
                true,
                mouseX,
                mouseY
        );

        drawModernButton(
                extractor,
                saveX,
                bottomY,
                saveWidth,
                18,
                tr("soundculling.ui.button.save_close"),
                true,
                true,
                mouseX,
                mouseY
        );


        // ----------------------------------------------------
        // PAGE CONTROLS
        // ----------------------------------------------------
        switch (page) {

            case OVERVIEW -> {
                int x = layout.panelX + 24;
                int y = layout.panelY + 78;
                int w = 142;

                drawToggleSkin(
                        extractor,
                        x,
                        y,
                        w,
                        20,
                        tr("soundculling.ui.label.sound_culling"),
                        enabled,
                        mouseX,
                        mouseY
                );

                drawSelectorSkin(
                        extractor,
                        x,
                        y + 28,
                        w,
                        20,
                        tr("soundculling.ui.label.preset"),
                        prettyPreset(),
                        mouseX,
                        mouseY
                );

                drawToggleSkin(
                        extractor,
                        x,
                        y + 56,
                        w,
                        20,
                        tr("soundculling.ui.label.adaptive_engine"),
                        adaptiveCulling,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        x,
                        y + 84,
                        w,
                        20,
                        tr("soundculling.ui.button.reset_stats"),
                        false,
                        true,
                        mouseX,
                        mouseY
                );
            }


            case PERFORMANCE -> {
                int left =
                        layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;

                int right =
                        layout.panelX
                                + layout.panelWidth / 2
                                + 4
                                + (layout.panelWidth / 2 - 188) / 2;

                int y =
                        layout.panelY + 82;

                int row = 24;

                // Left numeric controls
                drawNumberControls(
                        extractor, left, y,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row * 2,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row * 3,
                        mouseX, mouseY
                );

                // Right numeric controls
                drawNumberControls(
                        extractor, right, y,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, right, y + row,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, right, y + row * 2,
                        mouseX, mouseY
                );

                drawToggleSkin(
                        extractor,
                        right, y + row * 3, 170, 18,
                        tr("soundculling.ui.label.player_protection"),
                        protectPlayerSounds,
                        mouseX,
                        mouseY
                );
            }


            case LIVE -> {
                int top =
                        layout.panelY + 82;

                int contentLeft =
                        layout.panelX + 14;

                int contentRight =
                        layout.panelX + layout.panelWidth - 14;

                int actionCardWidth = 188;
                int actionCardX =
                        contentRight - actionCardWidth;

                int innerPad = 8;

                int left =
                        contentLeft + innerPad;

                int leftCardRight =
                        actionCardX - 7;

                int actionWidth = 82;
                int actionGap = 8;

                int actionAreaWidth =
                        actionWidth * 2 + actionGap;

                int actionX =
                        actionCardX
                                + (actionCardWidth - actionAreaWidth) / 2;

                int listWidth =
                        Math.max(
                                150,
                                leftCardRight - left - innerPad
                        );

                int maxRecent =
                        layout.panelHeight >= 270
                                ? 6
                                : 5;

                List<SoundCullingTracker.RecentSoundSnapshot> recent =
                        SoundCullingTracker.getRecentSounds(maxRecent);

                int listY = top;

                for (SoundCullingTracker.RecentSoundSnapshot sound : recent) {
                    boolean selected =
                            sound.soundId()
                                    .equals(selectedSoundId);

                    drawLiveSoundRow(
                            extractor,
                            left,
                            listY,
                            listWidth,
                            18,
                            sound,
                            selected,
                            mouseX,
                            mouseY
                    );

                    listY += 21;
                }

                boolean hasSelection =
                        selectedSoundId != null
                                && !selectedSoundId.isBlank();

                int col1 = actionX;
                int col2 =
                        actionX
                                + actionWidth
                                + actionGap;

                drawModernButton(
                        extractor,
                        col1,
                        top,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.never_cull"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        col2,
                        top,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.high_priority"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        col1,
                        top + 22,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.aggressive"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        col2,
                        top + 22,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.always_cull"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        col1,
                        top + 44,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.protect_mod"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        col2,
                        top + 44,
                        actionWidth,
                        18,
                        tr("soundculling.ui.button.clear_rule"),
                        false,
                        hasSelection,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        actionX,
                        top + 70,
                        actionAreaWidth,
                        18,
                        tr("soundculling.ui.button.refresh"),
                        false,
                        true,
                        mouseX,
                        mouseY
                );
            }


            case ADVANCED -> {
                int left =
                        layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;

                int right =
                        layout.panelX
                                + layout.panelWidth / 2
                                + 4
                                + (layout.panelWidth / 2 - 188) / 2;

                int y =
                        layout.panelY + 82;

                int row = 24;

                drawNumberControls(
                        extractor, left, y,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row * 2,
                        mouseX, mouseY
                );

                drawNumberControls(
                        extractor, left, y + row * 3,
                        mouseX, mouseY
                );

                drawToggleSkin(
                        extractor,
                        right,
                        y,
                        170,
                        18,
                        tr("soundculling.ui.label.directional_priority"),
                        directionalPriority,
                        mouseX,
                        mouseY
                );

                drawToggleSkin(
                        extractor,
                        right,
                        y + row,
                        170,
                        18,
                        tr("soundculling.ui.label.distance_priority"),
                        distancePriority,
                        mouseX,
                        mouseY
                );

                drawToggleSkin(
                        extractor,
                        right,
                        y + row * 2,
                        170,
                        18,
                        tr("soundculling.ui.label.debug_logging"),
                        debugLogging,
                        mouseX,
                        mouseY
                );

                drawModernButton(
                        extractor,
                        right, y + row * 3, 170, 18,
                        tr("soundculling.ui.button.factory_defaults"),
                        false,
                        true,
                        mouseX,
                        mouseY
                );
            }
        }
    }


    private void drawNumberControls(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        drawModernButton(
                extractor,
                x + 92,
                y,
                18,
                18,
                "-",
                false,
                true,
                mouseX,
                mouseY
        );

        drawModernButton(
                extractor,
                x + 152,
                y,
                18,
                18,
                "+",
                false,
                true,
                mouseX,
                mouseY
        );
    }


    private void drawToggleSkin(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean state,
            int mouseX,
            int mouseY
    ) {
        boolean hover =
                isInside(
                        mouseX,
                        mouseY,
                        x,
                        y,
                        width,
                        height
                );

        int border =
                state
                        ? 0xFF55A7FF
                        : 0xFF374352;

        int background =
                hover
                        ? 0xFF283545
                        : 0xFF202833;

        extractor.fill(
                x,
                y,
                x + width,
                y + height,
                border
        );

        extractor.fill(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                background
        );

        extractor.text(
                font,
                Component.literal(label),
                x + 8,
                y + 5,
                0xFFE6EBF1
        );

        String stateText =
                state ? tr("soundculling.ui.state.on") : tr("soundculling.ui.state.off");

        int pillWidth = 29;
        int pillHeight = 12;

        int pillX =
                x + width - pillWidth - 8;

        int pillY =
                y + (height - pillHeight) / 2;

        int pillColor =
                state
                        ? 0xFF24679B
                        : 0xFF343E49;

        extractor.fill(
                pillX,
                pillY,
                pillX + pillWidth,
                pillY + pillHeight,
                pillColor
        );

        extractor.text(
                font,
                Component.literal(stateText),
                pillX
                        + (pillWidth - font.width(stateText)) / 2,
                pillY + 2,
                state
                        ? 0xFFFFFFFF
                        : 0xFFADB5BE
        );
    }


    private void drawSelectorSkin(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int width,
            int height,
            String label,
            String value,
            int mouseX,
            int mouseY
    ) {
        boolean hover =
                isInside(
                        mouseX,
                        mouseY,
                        x,
                        y,
                        width,
                        height
                );

        extractor.fill(
                x,
                y,
                x + width,
                y + height,
                0xFF374352
        );

        extractor.fill(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                hover
                        ? 0xFF283545
                        : 0xFF202833
        );

        extractor.text(
                font,
                Component.literal(label),
                x + 8,
                y + 5,
                0xFF9DA8B4
        );

        extractor.text(
                font,
                Component.literal(value),
                x
                        + width
                        - font.width(value)
                        - 8,
                y + 5,
                0xFF65B5FF
        );
    }


    /*
     * Live Sounds uses dedicated columns instead of one concatenated label.
     *
     * The counters therefore remain readable even after tens/hundreds of
     * thousands of sound events. Only the sound ID is shortened.
     */
    private void drawLiveSoundRow(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int width,
            int height,
            SoundCullingTracker.RecentSoundSnapshot sound,
            boolean selected,
            int mouseX,
            int mouseY
    ) {
        /*
         * Reuse the normal modern button skin for background, border,
         * selection and hover behaviour.
         */
        drawModernButton(
                extractor,
                x,
                y,
                width,
                height,
                "",
                selected,
                true,
                mouseX,
                mouseY
        );

        String triggeredText =
                "x" + sound.triggered();

        String culledText =
                "c" + sound.culled();

        int padding = 7;
        int counterGap = 10;
        int idGap = 10;

        /*
         * Counters are anchored from the right edge.
         *
         * Their positions depend on their actual pixel width, not character
         * count, so x30060 / c29784 and larger values remain intact.
         */
        int culledX =
                x
                        + width
                        - padding
                        - font.width(culledText);

        int triggeredX =
                culledX
                        - counterGap
                        - font.width(triggeredText);

        int idX =
                x + padding;

        int idAvailableWidth =
                Math.max(
                        0,
                        triggeredX
                                - idGap
                                - idX
                );

        String visibleId =
                trimToPixelWidth(
                        sound.soundId(),
                        idAvailableWidth
                );

        int textY =
                y
                        + Math.max(
                                2,
                                (height - 8) / 2
                        );

        int idColor =
                selected
                        ? 0xFFFFFFFF
                        : 0xFFE8EDF2;

        /*
         * Sound ID
         */
        extractor.text(
                font,
                Component.literal(visibleId),
                idX,
                textY,
                idColor
        );

        /*
         * Trigger count
         */
        extractor.text(
                font,
                Component.literal(triggeredText),
                triggeredX,
                textY,
                selected
                        ? 0xFFFFFFFF
                        : 0xFFB6C0CA
        );

        /*
         * Cull count.
         *
         * A non-zero value gets a slightly stronger tint so it can be scanned
         * quickly without sacrificing readability.
         */
        extractor.text(
                font,
                Component.literal(culledText),
                culledX,
                textY,
                sound.culled() > 0
                        ? 0xFFFFA0A0
                        : (
                        selected
                                ? 0xFFFFFFFF
                                : 0xFF9DA8B4
                )
        );
    }


    /*
     * Pixel-aware trimming for UI rows.
     *
     * Character-count trimming is unreliable because Minecraft's font is
     * proportional: "iiii" and "WWWW" do not occupy the same width.
     */
    private String trimToPixelWidth(
            String text,
            int maxWidth
    ) {
        if (text == null
                || text.isEmpty()
                || maxWidth <= 0) {
            return "";
        }

        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth =
                font.width(ellipsis);

        if (ellipsisWidth > maxWidth) {
            return "";
        }

        int end =
                text.length();

        while (end > 0) {
            String candidate =
                    text.substring(0, end)
                            + ellipsis;

            if (font.width(candidate) <= maxWidth) {
                return candidate;
            }

            end--;
        }

        return ellipsis;
    }

    private void drawModernButton(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean accent,
            boolean active,
            int mouseX,
            int mouseY
    ) {
        boolean hover =
                active
                        && isInside(
                                mouseX,
                                mouseY,
                                x,
                                y,
                                width,
                                height
                        );

        int border;
        int background;
        int textColor;

        if (!active) {
            border = 0xFF252C34;
            background = 0xFF171C22;
            textColor = 0xFF66717C;
        } else if (accent) {
            border = 0xFF55A7FF;
            background =
                    hover
                            ? 0xFF28577E
                            : 0xFF214866;
            textColor = 0xFFFFFFFF;
        } else {
            border = 0xFF374352;
            background =
                    hover
                            ? 0xFF2A3746
                            : 0xFF202833;
            textColor = 0xFFE8EDF2;
        }

        extractor.fill(
                x,
                y,
                x + width,
                y + height,
                border
        );

        extractor.fill(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                background
        );

        String text = label == null ? "" : label;

        int maxTextWidth =
                Math.max(8, width - 12);

        if (font.width(text) > maxTextWidth) {
            String source = text;
            String suffix = "...";
            int end = source.length();

            while (end > 1
                    && font.width(
                            source.substring(0, end) + suffix
                    ) > maxTextWidth) {
                end--;
            }

            text =
                    source.substring(
                            0,
                            Math.max(1, end)
                    ) + suffix;
        }

        extractor.text(
                font,
                Component.literal(text),
                x + (width - font.width(text)) / 2,
                y + Math.max(
                        2,
                        (height - 8) / 2
                ),
                textColor
        );
    }

    private boolean isInside(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }
    private void renderOverview(
            GuiGraphicsExtractor extractor,
            Layout layout
    ) {
        int cardX = layout.panelX + 192;
        int cardY = layout.panelY + 78;
        int cardWidth = layout.panelWidth - 216;

        int cardHeight = 20;
        int gap = 8;

        drawStatCard(
                extractor,
                cardX,
                cardY,
                cardWidth,
                cardHeight,
                tr("soundculling.ui.stat.sound_rate"),
                tr("soundculling.ui.value.per_second", SoundCullingTracker.getEventsPerSecond())
        );

        drawStatCard(
                extractor,
                cardX,
                cardY + (cardHeight + gap),
                cardWidth,
                cardHeight,
                tr("soundculling.ui.stat.adaptive_pressure"),
                Math.round(
                        SoundCullingTracker.getAdaptivePressure() * 100.0f
                ) + "%"
        );

        drawStatCard(
                extractor,
                cardX,
                cardY + (cardHeight + gap) * 2,
                cardWidth,
                cardHeight,
                tr("soundculling.ui.stat.culled_session"),
                String.valueOf(
                        SoundCullingTracker.getTotalCulled()
                )
        );

        drawStatCard(
                extractor,
                cardX,
                cardY + (cardHeight + gap) * 3,
                cardWidth,
                cardHeight,
                tr("soundculling.ui.stat.dampened"),
                String.valueOf(
                        SoundCullingTracker.getTotalDampened()
                )
        );
    }

    private void renderPerformance(
            GuiGraphicsExtractor extractor,
            Layout layout
    ) {
        int left = layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;
        int right = layout.panelX + layout.panelWidth / 2 + 4 + (layout.panelWidth / 2 - 188) / 2;
        int y = layout.panelY + 82;
        int row = 24;

        renderValue(extractor, left, y,
                tr("soundculling.ui.label.adaptive_start"), tr("soundculling.ui.value.per_second", adaptiveStart));

        renderValue(extractor, left, y + row,
                tr("soundculling.ui.label.adaptive_full"), tr("soundculling.ui.value.per_second", adaptiveFull));

        renderValue(extractor, left, y + row * 2,
                tr("soundculling.ui.label.default"), String.valueOf(limitDefault));

        renderValue(extractor, left, y + row * 3,
                tr("soundculling.ui.label.blocks"), String.valueOf(limitBlock));

        renderValue(extractor, right, y,
                tr("soundculling.ui.label.hostile"), String.valueOf(limitHostile));

        renderValue(extractor, right, y + row,
                tr("soundculling.ui.label.neutral"), String.valueOf(limitNeutral));

        renderValue(extractor, right, y + row * 2,
                tr("soundculling.ui.label.ambient"), String.valueOf(limitAmbient));
    }

    private void renderLive(
            GuiGraphicsExtractor extractor,
            Layout layout
    ) {
        int contentLeft = layout.panelX + 14;
        int contentRight = layout.panelX + layout.panelWidth - 14;

        int actionCardWidth = 188;
        int actionCardX = contentRight - actionCardWidth;
        int leftCardRight = actionCardX - 7;
        int innerPad = 8;

        int headerY = layout.panelY + 73;

        String recentTitle = tr("soundculling.ui.live.recent_sounds");

        int recentTitleX =
                contentLeft
                        + (leftCardRight
                        - contentLeft
                        - font.width(recentTitle)) / 2;

        extractor.text(
                font,
                Component.literal(recentTitle),
                recentTitleX,
                headerY,
                0xFFAAB4C0
        );

        String actionTitle;
        int actionTitleColor;

        if (selectedSoundId != null && !selectedSoundId.isBlank()) {
            actionTitle = trim(selectedSoundId, 22);
            actionTitleColor = 0xFF8FC7FF;
        } else {
            actionTitle = tr("soundculling.ui.live.select_sound");
            actionTitleColor = 0xFF84909D;
        }

        int actionTitleX =
                actionCardX
                        + (actionCardWidth
                        - font.width(actionTitle)) / 2;

        extractor.text(
                font,
                Component.literal(actionTitle),
                actionTitleX,
                headerY,
                actionTitleColor
        );

        if (!ruleStatus.isBlank()) {
            extractor.text(
                    font,
                    Component.literal(trim(ruleStatus, 48)),
                    contentLeft + innerPad,
                    layout.panelY
                            + layout.panelHeight
                            - 49,
                    0xFF7EE787
            );
        }
    }

    private void renderAdvanced(
            GuiGraphicsExtractor extractor,
            Layout layout
    ) {
        int left = layout.panelX + 14 + (layout.panelWidth / 2 - 188) / 2;
        int right = layout.panelX + layout.panelWidth / 2 + 4 + (layout.panelWidth / 2 - 188) / 2;
        int y = layout.panelY + 82;
        int row = 24;

        renderValue(
                extractor,
                left,
                y,
                tr("soundculling.ui.label.region_total"),
                String.valueOf(maxTotal)
        );

        renderValue(
                extractor,
                left,
                y + row,
                tr("soundculling.ui.label.window_ticks"),
                String.valueOf(windowTicks)
        );

        renderValue(
                extractor,
                left,
                y + row * 2,
                tr("soundculling.ui.label.region_size"),
                tr("soundculling.ui.value.blocks", regionSize)
        );

        renderValue(
                extractor,
                left,
                y + row * 3,
                tr("soundculling.ui.label.neighbour_cells"),
                String.valueOf(neighborRadius)
        );

        int statsY = y + row * 4;

        String rulesText =
                tr("soundculling.ui.advanced.rules", rules.size());

        String regionsText =
                tr(
                        "soundculling.ui.advanced.tracked_regions",
                        SoundCullingTracker.getTrackedRegionCount()
                );

        extractor.text(
                font,
                Component.literal(rulesText),
                right + (170 - font.width(rulesText)) / 2,
                statsY,
                0xFF84909D
        );

        extractor.text(
                font,
                Component.literal(regionsText),
                right + (170 - font.width(regionsText)) / 2,
                statsY + 13,
                0xFF84909D
        );
    }

    private void drawStatCard(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            int width,
            int height,
            String label,
            String value
    ) {
        extractor.fill(
                x,
                y,
                x + width,
                y + height,
                0xFF1D2530
        );

        // Accent stripe
        extractor.fill(
                x,
                y,
                x + 2,
                y + height,
                0xFF55A7FF
        );

        extractor.text(
                font,
                Component.literal(label),
                x + 10,
                y + 6,
                0xFF98A5B3
        );

        extractor.text(
                font,
                Component.literal(value),
                x + width - font.width(value) - 10,
                y + 6,
                0xFFF7F9FB
        );
    }

    private void renderValue(
            GuiGraphicsExtractor extractor,
            int x,
            int y,
            String label,
            String value
    ) {
        extractor.text(
                font,
                Component.literal(label),
                x,
                y + 5,
                0xFFC5CDD6
        );

        extractor.text(
                font,
                Component.literal(value),
                x + 131 - font.width(value) / 2,
                y + 5,
                0xFFFFFFFF
        );
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor extractor,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractBackground(extractor, mouseX, mouseY, delta);

        Layout layout = layout();

        // World dim
        extractor.fill(
                0, 0, width, height,
                0xD6080A0E
        );

        // Soft fake shadow
        extractor.fill(
                layout.panelX + 4,
                layout.panelY + 5,
                layout.panelX + layout.panelWidth + 4,
                layout.panelY + layout.panelHeight + 5,
                0x70000000
        );

        // Main panel
        extractor.fill(
                layout.panelX,
                layout.panelY,
                layout.panelX + layout.panelWidth,
                layout.panelY + layout.panelHeight,
                0xF410141A
        );

        // Header
        extractor.fill(
                layout.panelX,
                layout.panelY,
                layout.panelX + layout.panelWidth,
                layout.panelY + 31,
                0xFF171D25
        );

        // Accent line
        extractor.fill(
                layout.panelX,
                layout.panelY,
                layout.panelX + layout.panelWidth,
                layout.panelY + 2,
                0xFF55A7FF
        );

        int contentTop = layout.panelY + 72;
        int contentBottom = layout.panelY + layout.panelHeight - 38;

        switch (page) {
            case OVERVIEW -> {
                extractor.fill(
                        layout.panelX + 14,
                        contentTop,
                        layout.panelX + 176,
                        contentBottom,
                        0xB8171D25
                );

                extractor.fill(
                        layout.panelX + 182,
                        contentTop,
                        layout.panelX + layout.panelWidth - 14,
                        contentBottom,
                        0xB8171D25
                );
            }

            case PERFORMANCE -> {
                int middle = layout.panelX + layout.panelWidth / 2;

                extractor.fill(
                        layout.panelX + 14,
                        contentTop,
                        middle - 4,
                        contentBottom,
                        0xB8171D25
                );

                extractor.fill(
                        middle + 4,
                        contentTop,
                        layout.panelX + layout.panelWidth - 14,
                        contentBottom,
                        0xB8171D25
                );
            }

            case LIVE -> {
                int actionWidth = 188;
                int actionX =
                        layout.panelX
                                + layout.panelWidth
                                - actionWidth
                                - 14;

                extractor.fill(
                        layout.panelX + 14,
                        contentTop,
                        actionX - 7,
                        contentBottom,
                        0xB8171D25
                );

                extractor.fill(
                        actionX,
                        contentTop,
                        layout.panelX + layout.panelWidth - 14,
                        contentBottom,
                        0xB8171D25
                );
            }

            case ADVANCED -> {
                int middle = layout.panelX + layout.panelWidth / 2;

                extractor.fill(
                        layout.panelX + 14,
                        contentTop,
                        middle - 4,
                        contentBottom,
                        0xB8171D25
                );

                extractor.fill(
                        middle + 4,
                        contentTop,
                        layout.panelX + layout.panelWidth - 14,
                        contentBottom,
                        0xB8171D25
                );
            }
        }

        // Footer
        extractor.fill(
                layout.panelX,
                layout.panelY + layout.panelHeight - 34,
                layout.panelX + layout.panelWidth,
                layout.panelY + layout.panelHeight,
                0xFF13181F
        );
    }

    private void applyAndSave() {
        config.enabled = enabled;
        config.preset = preset;
        config.adaptiveCulling = adaptiveCulling;
        config.adaptiveStartPerSecond = adaptiveStart;
        config.adaptiveFullPerSecond = adaptiveFull;
        config.adaptiveStrength = adaptiveStrength;
        config.maxTotalPerRegion = maxTotal;
        config.windowTicks = windowTicks;
        config.regionSize = regionSize;
        config.spatialNeighborRadius = neighborRadius;
        config.debugLogging = debugLogging;
        config.directionalPriority = directionalPriority;
        config.distancePriority = distancePriority;
        config.protectPlayerSounds = protectPlayerSounds;

        config.limitHostile = limitHostile;
        config.limitNeutral = limitNeutral;
        config.limitBlock = limitBlock;
        config.limitAmbient = limitAmbient;
        config.limitDefault = limitDefault;

        config.rules = new ArrayList<>();
        for (SoundCullingConfig.SoundRule rule : rules) {
            if (rule != null) {
                config.rules.add(rule.copy());
            }
        }

        config.save();
        SoundCulling.LOGGER.info("[SoundCulling] 2.0 settings saved.");
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private Layout layout() {
        int panelWidth = Math.min(520, Math.max(360, width - 32));
        int panelHeight = Math.min(280, Math.max(240, height - 24));

        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        return new Layout(panelX, panelY, panelWidth, panelHeight);
    }

    private static String trim(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight) {
    }
}
