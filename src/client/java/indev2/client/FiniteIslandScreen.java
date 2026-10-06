package indev2.client;

import indev2.world.FiniteIslandGenerator;
import indev2.world.IslandShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.CreateBuffetWorldScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public final class FiniteIslandScreen extends Screen {
    private final CreateWorldScreen parent;
    private Holder<Biome> biome;
    private String radiusText;
    private String marginText;
    private EditBox radiusBox;
    private EditBox marginBox;
    private Button doneButton;
    private Component status = Component.empty();

    public FiniteIslandScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("indev2.island.title"));
        this.parent = parent;
        FiniteIslandGenerator generator = (FiniteIslandGenerator) context.selectedDimensions().overworld();
        biome = generator.biome();
        radiusText = Integer.toString(generator.radius());
        marginText = Integer.toString(generator.oceanMargin());
    }

    @Override protected void init() {
        int left = width / 2 - 150;
        int top = height / 2 - 75;
        addRenderableWidget(Button.builder(Component.translatable("indev2.island.biome", biomeName()), button -> {
            WorldCreationContext context = parent.getUiState().getSettings().withDimensions((registries, dimensions) ->
                    dimensions.replaceOverworldGenerator(registries, new FiniteIslandGenerator(biome,
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            IslandShape.DEFAULT_RADIUS, IslandShape.DEFAULT_MARGIN, registries.lookupOrThrow(Registries.NOISE))));
            minecraft.setScreen(new CreateBuffetWorldScreen(this, context, chosen -> biome = chosen));
        }).bounds(left, top, 300, 20).build());
        radiusBox = addRenderableWidget(new EditBox(font, left + 170, top + 34, 130, 20,
                Component.translatable("indev2.island.radius")));
        radiusBox.setMaxLength(5);
        radiusBox.setValue(radiusText);
        radiusBox.setResponder(value -> { radiusText = value; validate(); });
        marginBox = addRenderableWidget(new EditBox(font, left + 170, top + 68, 130, 20,
                Component.translatable("indev2.island.margin")));
        marginBox.setMaxLength(5);
        marginBox.setValue(marginText);
        marginBox.setResponder(value -> { marginText = value; validate(); });
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            int radius = Integer.parseInt(radiusText);
            int margin = Integer.parseInt(marginText);
            parent.getUiState().updateDimensions((registries, dimensions) ->
                    dimensions.replaceOverworldGenerator(registries, new FiniteIslandGenerator(biome,
                            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD),
                            radius, margin, registries.lookupOrThrow(Registries.NOISE))));
            onClose();
        }).bounds(left, top + 150, 146, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + 154, top + 150, 146, 20).build());
        validate();
    }

    private Component biomeName() {
        return biome.unwrapKey().map(key -> Component.translatable(key.identifier().toLanguageKey("biome")))
                .orElse(Component.literal("Custom biome"));
    }

    private void validate() {
        if (doneButton == null) return;
        try {
            int radius = Integer.parseInt(radiusText);
            int margin = Integer.parseInt(marginText);
            if (radius < IslandShape.MIN_RADIUS || radius > IslandShape.MAX_RADIUS
                    || margin < IslandShape.MIN_MARGIN || margin > IslandShape.MAX_MARGIN) throw new NumberFormatException();
            doneButton.active = true;
            status = Component.translatable("indev2.island.width", IslandShape.worldWidth(radius, margin));
        } catch (NumberFormatException exception) {
            doneButton.active = false;
            status = Component.translatable("indev2.island.invalid");
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 150;
        int top = height / 2 - 75;
        graphics.centeredText(font, title, width / 2, top - 28, 0xffffffff);
        graphics.text(font, Component.translatable("indev2.island.radius"), left, top + 39, 0xffffffff);
        graphics.text(font, Component.translatable("indev2.island.radius.range"), left, top + 51, 0xffa0a0a0);
        graphics.text(font, Component.translatable("indev2.island.margin"), left, top + 73, 0xffffffff);
        graphics.text(font, Component.translatable("indev2.island.margin.range"), left, top + 85, 0xffa0a0a0);
        graphics.centeredText(font, status, width / 2, top + 108, doneButton.active ? 0xffa0e0a0 : 0xffff8080);
        graphics.centeredText(font, Component.translatable("indev2.island.description"), width / 2, top + 126, 0xffa0a0a0);
    }
}
