package indev2.portal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public enum StructureDestination implements PortalDestination {
    MONUMENT("monument", "deep_ocean", "overworld", 0x42cdb7, List.of("monument"), Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH),
    ANCIENT_CITY("ancient_city", "deep_dark", "overworld", 0x38b8bf, List.of("ancient_city"), Items.DEEPSLATE_TILES),
    END_CITY("end_city", "end_highlands", "end", 0xdbd29a, List.of("end_city"), Items.ENDER_EYE),
    MANSION("mansion", "dark_forest", "overworld", 0x78523d, List.of("mansion"), Items.DARK_OAK_LOG),
    TRIAL_CHAMBERS("trial_chambers", "plains", "overworld", 0xde9267, List.of("trial_chambers"), Items.COPPER_BLOCK),
    BASTION("bastion", "crimson_forest", "nether", 0xf0be42, List.of("bastion_remnant"), Items.GOLD_BLOCK),
    VILLAGE("village", "plains", "overworld", 0xd2bb52, List.of("village_plains"), Items.HAY_BLOCK),
    DESERT_TEMPLE("desert_temple", "desert", "overworld", 0xe9bc80, List.of("desert_pyramid"), Items.CHISELED_SANDSTONE),
    JUNGLE_TEMPLE("jungle_temple", "jungle", "overworld", 0x77a06a, List.of("jungle_pyramid"), Items.MOSSY_COBBLESTONE),
    WITCH_HUT("witch_hut", "swamp", "overworld", 0x839269, List.of("swamp_hut"), Items.CAULDRON),
    IGLOO("igloo", "snowy_plains", "overworld", 0xe1efff, List.of("igloo"), Items.SNOW_BLOCK),
    OUTPOST("outpost", "plains", "overworld", 0xb86c59, List.of("pillager_outpost"), Items.CROSSBOW),
    TRAIL_RUINS("trail_ruins", "forest", "overworld", 0xbb8570, List.of("trail_ruins"), Items.TERRACOTTA),
    SHIPWRECK("shipwreck", "warm_ocean", "overworld", 0x599cb8, List.of("shipwreck", "ocean_ruin_warm"), Items.BARREL),
    CORAL_REEF("coral_reef", "warm_ocean", "overworld", 0xef95c1, List.of(), Items.SEA_PICKLE),
    FORTRESS("fortress", "nether_wastes", "nether", 0xb76a9e, List.of("fortress"));

    private static final java.util.Map<String, StructureDestination> BY_ID = Arrays.stream(values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(StructureDestination::id, value -> value));
    private final String id, biome, settings;
    private final int color;
    private final List<String> structures;
    private final List<Item> items;
    StructureDestination(String id, String biome, String settings, int color, List<String> structures, Item... items) {
        this.id = id; this.biome = biome; this.settings = settings; this.color = color;
        this.structures = structures; this.items = List.of(items);
    }
    public String id() { return id; }
    public String biomeId() { return biome; }
    public String settingsId() { return settings; }
    public int color() { return color; }
    public boolean ocean() { return this == MONUMENT || this == SHIPWRECK || this == CORAL_REEF; }
    public List<String> structures() { return structures; }
    public List<Item> items() { return items; }
    public Identifier dimensionId() { return Identifier.fromNamespaceAndPath("indev2", "special_" + id); }
    public ResourceKey<Level> dimension() { return ResourceKey.create(Registries.DIMENSION, dimensionId()); }
    public ServerLevel level(MinecraftServer server) { return server.getLevel(dimension()); }
    public static Optional<StructureDestination> forItem(ItemStack stack) {
        return Arrays.stream(values()).filter(destination -> destination.items.stream().anyMatch(stack::is)).findFirst();
    }
    public static StructureDestination byId(String id) {
        var destination = BY_ID.get(id);
        if (destination == null) throw new IllegalArgumentException("Unknown structure destination: " + id);
        return destination;
    }
}
