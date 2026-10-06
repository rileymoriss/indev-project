# Indev++

A Fabric mod intended to bring ideas from the Legacy+ team's Indev+ mod to modern Minecraft.

## Current status

Finite Island world generation is implemented: one biome, a seeded irregular island, surrounding ocean, and a square vanilla world border. Island shaping can be toggled off to use vanilla terrain inside the same border. Size, terrain mode, and biome are selected during world creation and retained in the save. All 16 dyes connect persistent biome-family worlds, with a seeded biome variant selected once for each grouped destination. Structure/item destinations remain planned.

## Creating a finite world

1. Open **Singleplayer → Create New World → World**.
2. Change **World Type** to **Finite Island** and click **Customize**.
3. Select the biome family, **Island terrain** toggle, radius, and margin, then click **Done**.
4. Create the world normally. The border is centred at `(0, 0)` and applied automatically on every load.

Defaults are the forest family, a 128-block radius, and a 32-block ocean margin (a 320-block-wide border). Supported radii are 128–8,192 blocks; ocean margins are 32–4,096 blocks. The radius is an outer terrain envelope, so the irregular coastline can fall inside it.

With Island terrain On, terrain uses Minecraft's Overworld noise pipeline with an island envelope, retaining vanilla caves, ores, biome surfaces, vegetation, and mob generation. Structures can start only in the island's interior. The surrounding water retains the selected biome, so the entire Overworld has one biome. The vanilla Nether and End keep their standard generation and borders for now. The creation menu offers all 16 biome families in the dye palette below. The chosen family selects a biome variant when the world is created. Ordinary world types are unaffected. No additional terrain mod is required; compatibility with third-party terrain mods has not been verified.

## Dyed biome portals

1. Build an ordinary obsidian Nether portal and light it with flint and steel.
2. Physically throw a dye into the active portal to select its biome family (see the full palette below).
3. The whole portal changes colour. Enter it to travel using the normal portal delay and cooldown.

One dye is consumed when the colour changes. The remainder of a thrown stack stays in the source world; matching dye does not consume another item. Undyed portals retain their normal Nether behaviour. This feature applies to finite worlds whose starting biome belongs to the palette; ordinary worlds are unaffected.

Your starting world is its biome family's destination: green leads home when you start in forest, and yellow leads home when you start in desert. A portal targeting the biome you are already in does not move you. Each other family has one persistent dimension, with a distinct terrain seed derived from the world seed. All biome worlds use the starting world's radius, ocean margin, and terrain toggle, including after reopening the save.

Each entrance gets a linked arrival portal on clear, dry land inside the destination border. Its return colour points to the source biome. Separate entrances have separate return links, even though they reach the same destination world. Portal colours, links, and player changes persist with the save. A broken or recoloured linked arrival portal is replaced at a new safe site on the next trip; an obstructed landing refuses travel and leaves the player in the source world. Destination portal construction requires replaceable clearance and natural ground, avoids block entities and existing portal frames, and may replace terrain and vegetation to create its landing pad.

Older finite saves whose starting biome belongs to the palette gain the missing destinations when loaded. Existing generators keep their concrete biome and terrain seed; newly added grouped destinations select their variant once. Back up existing saves before loading them with the expanded palette.

## How it works

- The `indev2:finite_island` world preset adds the selectable world type. A client mixin connects its Customize button to `FiniteIslandScreen`, which validates the sizes and applies the selected biome.
- `IslandShape` uses distance from `(0, 0)` and seeded waves to define an irregular coastline with a smooth shore transition.
- `IslandDensity` adjusts vanilla terrain density and surface-height estimates: it ensures central land and lowers terrain into ocean outside the island.
- `FiniteIslandGenerator` uses a fixed biome source and delegates terrain, surfaces, caves, and mob generation to Minecraft's noise generator. Its codec stores the biome, radius, ocean margin, island toggle, and noise settings in the save; the world seed determines generation.
- `ServerLevelMixin` resolves grouped biome choices before chunk generation. The server saves all resolved destination generators, including datapack dimensions, so their concrete biomes survive future palette changes.
- A server mixin starts vanilla's spawn search at the bounded world's centre. On server startup, the mod applies a centred border with width `2 × (radius + ocean margin)`.

The border restricts player movement; ocean chunks can still generate beyond it. Default changes apply to newly created worlds. Existing worlds retain their saved generator settings.

## Development setup

- Minecraft: 26.1.2
- Java: JDK 25 (also required by the Gradle build and CI)
- Fabric Loader: 0.19.2
- Fabric API: 0.148.0+26.1.2
- Mod ID and artifact name: `indev2`
- Main entrypoint: `indev2.Indev2Mod`

Build with `./gradlew build` (`gradlew.bat build` on Windows).
Launch the development client with `./gradlew runClient` from the project folder. Minecraft opens in a separate desktop window with the mod loaded. IntelliJ is optional; if used, set both the Project SDK and Gradle JVM to Java 25 and refresh the Gradle project.
Built JARs are written to `build/libs/`.

Java source belongs in `src/main/java`; client-only code belongs in `src/client/java`.
Resources belong in the corresponding `resources` directories.

Run the isolated Minecraft integration tests with `./gradlew runClientGameTest`. This launches a test client, checks the menu toggle and validation, compares disabled island shaping against vanilla terrain, generates island terrain, verifies the border, saves and reopens a world, and checks ordinary world types. Portal tests cover every dye, all-family travel and return, seeded variant selection and persistence, thrown dye stacks, recolouring, both starting biomes, travel and return, independent entrance links, broken arrival portals, and save/reload persistence. Test saves and screenshots are placed under `build/run/clientGameTest/`; the test mod is excluded from the release JAR.

## Agreed design

### Finite island worlds

- Each world has a single biome. Island terrain is configurable: a roughly circular island with a natural, irregular coastline, or vanilla terrain bounded by the same square border.
- Land radius is configurable. Ocean surrounds the island inside a larger square boundary.
- Minecraft's built-in world border provides a hard limit that players cannot cross.
- Island radius and ocean margin are configurable in the world creation menu and saved with the world.
- The square's width is calculated as `2 × (island radius + ocean margin)`. With the defaults, a 128-block radius and 32-block ocean margin produce a 320-block-wide border.
- Coastline variation should keep the island clear of the border.

### Travel between biome worlds

- All 16 dye colours in the palette below are implemented. Dye is thrown into the active portal.
- Each colour leads to one persistent destination world within a save. Returning through that colour takes the player back to the same island, preserving builds and changes.
- Biome worlds share one Minecraft save. The starting biome uses the Overworld; other biomes use separate dimensions. All biome worlds share the starting world's size and terrain toggle.
- Each destination retains its biome, generation seed, shared island size, builds, and portal connections.
- To extend the palette, add a `BiomeDestination` entry, a matching `data/indev2/dimension/<id>.json` with a finite island generator, unique `seed_salt`, and `random_variant: true`, a portal blockstate file using the shared portal models, and translations. Dye recognition, block registration, menu choices, dimension size inheritance, and link/travel logic iterate the palette rather than hardcoding the first pair. Keep destination IDs and seed salts stable once worlds have been created.

### Full dye palette

All mappings below are implemented. Creation-menu choices select families rather than individual variants.

| Dye | Destination family | Possible biome |
|---|---|---|
| Green | Forests | Forest, Flower Forest, Birch Forest, Old Growth Birch Forest |
| Lime | Jungles | Jungle, Sparse Jungle, Bamboo Jungle |
| Brown | Taigas | Taiga, Old Growth Pine Taiga, Old Growth Spruce Taiga |
| Yellow | Desert | Desert |
| Orange | Badlands | Badlands, Eroded Badlands, Wooded Badlands |
| Red | Savannas | Savanna, Savanna Plateau, Windswept Savanna |
| Black | Dark forest | Dark Forest |
| White | Snowy lowlands | Snowy Plains, Snowy Taiga |
| Light Grey | Alpine snow | Grove, Snowy Slopes, Frozen Peaks, Jagged Peaks |
| Grey | Rocky highlands | Windswept Hills, Windswept Gravelly Hills, Windswept Forest, Stony Peaks |
| Pink | Cherry grove | Cherry Grove |
| Cyan | Swamps | Swamp, Mangrove Swamp |
| Light Blue | Ice spikes | Ice Spikes |
| Magenta | Mushroom island | Mushroom Fields |
| Purple | Pale garden | Pale Garden |
| Blue | Open countryside | Plains, Sunflower Plains, Meadow |

For grouped destinations, randomly select one biome when the destination is first generated, using the save's seed and destination identity. Keep that choice permanently: recolouring a portal or throwing more dye must not reroll the world. Variants currently have equal chances. The selected concrete biome is saved so it does not change on reload. The starting world's biome occupies its family's colour destination, rather than creating a duplicate world.

Cave biomes have no dedicated dye destinations; use whatever underground generation occurs beneath each world. Beaches, shores, rivers, and ordinary ocean biomes have no dedicated dye destinations. Nether and End destinations remain separate from this palette.

### Planned item destinations

Throw these items into a lit, undyed Nether portal to select a special persistent destination world. These destinations are planned, not yet implemented.

| Activation item | Destination | Required generation |
|---|---|---|
| Any raw fish: raw cod, raw salmon, tropical fish, or pufferfish | Ocean monument world | Ocean terrain with a guaranteed ocean monument inside the border |
| Deepslate Tiles (`minecraft:deepslate_tiles`) | Ancient city world | A guaranteed ancient city inside the border |
| Eye of Ender (`minecraft:ender_eye`) | End city world | End terrain with a guaranteed end city and end ship inside the border |
| Dark Oak Log | Woodland mansion world | Dark forest with a guaranteed woodland mansion |
| Copper Block | Trial chambers world | Guaranteed underground trial chambers beneath a normal surface |
| Gold Block | Bastion remnant world | Nether landscape with a guaranteed bastion remnant |
| Hay Bale | Village world | Countryside with a guaranteed village |
| Chiseled Sandstone | Desert temple world | Desert with a guaranteed desert temple |
| Mossy Cobblestone | Jungle temple world | Jungle with a guaranteed jungle temple |
| Cauldron | Witch hut world | Swamp with a guaranteed witch hut |
| Snow Block | Igloo world | Snowy terrain with a guaranteed igloo and basement |
| Crossbow | Pillager outpost world | Open landscape with a guaranteed pillager outpost |
| Terracotta | Trail ruins world | Forest with guaranteed buried trail ruins to excavate |
| Barrel | Shipwreck and ocean ruins world | Ocean with guaranteed shipwreck and ocean ruins, separate from the monument world |
| Sea Pickle | Coral reef world | Warm, shallow ocean with a guaranteed coral reef |

These are dedicated worlds within the same save, not searches for structures in an existing biome destination. Each retains builds and changes between visits and shares the configured world size. The ocean monument world uses ocean terrain regardless of the island toggle. Arrival portals must provide a safe landing and a linked return route; ancient city terrain and special portal appearance remain to be designed.

Each promised structure must fit wholly inside the world border. Activation items should be obtainable before visiting their destination; do not require destination-exclusive loot to unlock that world. Terrain for special destinations should support the promised structure or biome feature, rather than forcing the ordinary island envelope where it would conflict.

The planned undyed portal destination remains the Nether, with a guaranteed Nether fortress. This guarantee is not yet implemented. The purple Pale Garden portal uses pale lavender colouring, white particles, and the mod's neutral animated portal texture to distinguish it from the ordinary purple Nether portal.

### First milestone and open decisions

- Implemented first milestone: one playable, single-biome finite island with configurable size and a working world border.
- Implemented world creation interface: a selectable Finite Island option with a biome choice, island radius, and ocean margin.
- Terrain shaping and default sizes can be refined through playtesting. The full dye palette and structure activation items above are agreed; future adjustments to variant weights, special destination terrain details, and the future role of the vanilla Nether and End remain to be decided.
- Larion's disc world mod remains a possible reference for future refinements; the current implementation uses vanilla terrain generation without an additional dependency.

### Terrain toggle

In **World → World Type: Finite Island → Customize**, set **Island terrain** to **On** for the custom circular island and surrounding ocean, or **Off** for vanilla noise terrain with the square world border. Both modes retain the selected single biome. Island terrain defaults to On, including for older saves.

The border width remains `2 × (radius + margin)` (320 blocks with the default 128 + 32). With island terrain Off, these fields are labelled World radius and Border padding; the padding does not force an ocean. The setting is saved and shared by every dyed-portal biome world, alongside the size settings. Vanilla terrain can contain ocean or land at the border, and does not guarantee a dry island centre.
