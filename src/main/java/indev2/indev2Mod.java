import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class indev2Mod implements ModInitializer {

    // Create the item instance
    public static final Item BIOME_SCANNER = new BiomeScannerItem(new Item.Settings());

    @Override
    public void onInitialize() {
        // Register it to the game using your mod ID
        Registry.register(Registries.ITEM, Identifier.of("indev2Mod", "biome_scanner"), BIOME_SCANNER);
    }
}