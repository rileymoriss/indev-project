# Indev++

A Fabric mod intended to bring ideas from the Legacy+ team's Indev+ mod to modern Minecraft.

## Current status

Finite Island world generation is implemented: one biome, a seeded irregular island, surrounding ocean, and a square vanilla world border. Size and biome are selected during world creation and retained in the save. Dyed portals and additional biome dimensions are future work.

## Creating a finite world

1. Open **Singleplayer → Create New World → World**.
2. Change **World Type** to **Finite Island** and click **Customize**.
3. Select the biome, island radius, and ocean margin, then click **Done**.
4. Create the world normally. The border is centred at `(0, 0)` and applied automatically on every load.

Defaults are a forest biome, a 128-block radius, and a 32-block ocean margin (a 320-block-wide border). Supported radii are 128–8,192 blocks; ocean margins are 32–4,096 blocks. The radius is an outer terrain envelope, so the irregular coastline can fall inside it.

Terrain uses Minecraft's Overworld noise pipeline with an island envelope, retaining vanilla caves, ores, biome surfaces, vegetation, and mob generation. Structures can start only in the island's interior. The surrounding water retains the selected biome, so the entire Overworld has one biome. The vanilla Nether and End keep their standard generation and borders for now. Ordinary world types are unaffected. No additional terrain mod is required; compatibility with third-party terrain mods has not been verified.

## How it works

- The `indev2:finite_island` world preset adds the selectable world type. A client mixin connects its Customize button to `FiniteIslandScreen`, which validates the sizes and applies the selected biome.
- `IslandShape` uses distance from `(0, 0)` and seeded waves to define an irregular coastline with a smooth shore transition.
- `IslandDensity` adjusts vanilla terrain density and surface-height estimates: it ensures central land and lowers terrain into ocean outside the island.
- `FiniteIslandGenerator` uses a fixed biome source and delegates terrain, surfaces, caves, and mob generation to Minecraft's noise generator. Its codec stores the biome, radius, ocean margin, and noise settings in the save; the world seed determines generation.
- A server mixin starts vanilla's spawn search at the island centre. On server startup, the mod applies a centred border with width `2 × (radius + ocean margin)`.

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

Run the isolated Minecraft integration tests with `./gradlew runClientGameTest`. This launches a test client, checks the menu and validation, generates island terrain, verifies the border, saves and reopens a world, and checks ordinary world types. Test saves and screenshots are placed under `build/run/clientGameTest/`; the test mod is excluded from the release JAR.

## Agreed design

### Finite island worlds

- Each world has a single biome and a roughly circular island with a natural, irregular coastline.
- Land radius is configurable. Ocean surrounds the island inside a larger square boundary.
- Minecraft's built-in world border provides a hard limit that players cannot cross.
- Island radius and ocean margin are configurable in the world creation menu and saved with the world.
- The square's width is calculated as `2 × (island radius + ocean margin)`. With the defaults, a 128-block radius and 32-block ocean margin produce a 320-block-wide border.
- Coastline variation should keep the island clear of the border.

### Future travel between biome worlds

- Dyed Nether portals select a destination biome by colour; green leading to forest is the agreed example.
- Each colour leads to one persistent destination world within a save. Returning through that colour takes the player back to the same island, preserving builds and changes.
- Biome worlds are planned as separate dimensions within the same Minecraft save.
- Each destination should retain its biome, generation seed, island radius, ocean margin, and portal connections.
- Design the initial generation and settings system with these future destinations in mind.

### First milestone and open decisions

- Implemented first milestone: one playable, single-biome finite island with configurable size and a working world border.
- Implemented world creation interface: a selectable Finite Island option with a biome choice, island radius, and ocean margin.
- Terrain shaping and default sizes can be refined through playtesting. The full colour-to-biome mapping, portal construction and linking rules, and the future role of the vanilla Nether and End remain to be decided.
- Larion's disc world mod remains a possible reference for future refinements; the current implementation uses vanilla terrain generation without an additional dependency.
