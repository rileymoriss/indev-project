package indev2.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent directed links; each source portal can retain links for multiple destination colours. */
public final class PortalLinks extends SavedData {
    private record Key(PortalRef from, String destination) {}
    public record Link(PortalRef from, String destination, PortalRef to) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                PortalRef.CODEC.fieldOf("from").forGetter(Link::from),
                Codec.STRING.fieldOf("destination").forGetter(Link::destination),
                PortalRef.CODEC.fieldOf("to").forGetter(Link::to)
        ).apply(i, Link::new));
    }
    public static final Codec<PortalLinks> CODEC = Link.CODEC.listOf().fieldOf("links").codec()
            .xmap(PortalLinks::new, PortalLinks::entries);
    public static final SavedDataType<PortalLinks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("indev2", "portal_links"), PortalLinks::new, CODEC, null);
    private final Map<Key, PortalRef> links = new HashMap<>();

    public PortalLinks() {}
    private PortalLinks(List<Link> entries) {
        for (Link link : entries) links.put(new Key(link.from(), link.destination()), link.to());
    }
    private List<Link> entries() {
        List<Link> entries = new ArrayList<>();
        links.forEach((key, to) -> entries.add(new Link(key.from(), key.destination(), to)));
        return entries;
    }
    public static PortalLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public PortalRef find(PortalRef from, BiomeDestination destination) {
        return links.get(new Key(from, destination.id()));
    }
    public void connect(PortalRef from, BiomeDestination destination, PortalRef to, BiomeDestination origin) {
        links.put(new Key(from, destination.id()), to);
        links.put(new Key(to, origin.id()), from);
        setDirty();
    }
}
