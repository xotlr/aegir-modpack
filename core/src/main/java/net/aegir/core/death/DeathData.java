package net.aegir.core.death;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything AEGIR Core remembers about deaths, stored with the world (data/aegir_core_deaths.dat). */
public final class DeathData extends SavedData {
    private static final String NAME = "aegir_core_deaths";

    public final Map<UUID, Integer> deaths = new HashMap<>();
    /** Real time (ms) of each player's last respawn, for "no double losses". */
    public final Map<UUID, Long> lastRespawn = new HashMap<>();
    /** Project MMO XP per skill taken at death, restored on respawn when the death was protected. */
    public final Map<UUID, Map<String, Long>> protectedXp = new HashMap<>();
    public final List<Grave> graves = new ArrayList<>();
    public final List<Place> cairns = new ArrayList<>();
    public final List<String> fallen = new ArrayList<>();
    public Place lectern;

    public record Place(ResourceKey<Level> dim, BlockPos pos) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim.location().toString());
            t.putInt("x", pos.getX()); t.putInt("y", pos.getY()); t.putInt("z", pos.getZ());
            return t;
        }
        static Place load(CompoundTag t) {
            return new Place(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                    new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }
    }

    public static final class Grave {
        public final UUID owner; public final Place place; public final long time;
        public boolean secondWindUsed, emptied;
        Grave(UUID owner, Place place, long time) { this.owner = owner; this.place = place; this.time = time; }
    }

    public static DeathData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DeathData::new, DeathData::load, null), NAME);
    }

    public void addGrave(UUID owner, Place place, long time) {
        graves.add(new Grave(owner, place, time));
        while (graves.size() > 500) graves.removeFirst();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag d = new CompoundTag();
        deaths.forEach((k, v) -> d.putInt(k.toString(), v));
        tag.put("deaths", d);
        CompoundTag r = new CompoundTag();
        lastRespawn.forEach((k, v) -> r.putLong(k.toString(), v));
        tag.put("lastRespawn", r);
        CompoundTag px = new CompoundTag();
        protectedXp.forEach((k, m) -> { CompoundTag s = new CompoundTag(); m.forEach(s::putLong); px.put(k.toString(), s); });
        tag.put("protectedXp", px);
        ListTag gl = new ListTag();
        for (Grave g : graves) {
            CompoundTag t = g.place.save();
            t.putUUID("owner", g.owner); t.putLong("time", g.time);
            t.putBoolean("secondWind", g.secondWindUsed); t.putBoolean("emptied", g.emptied);
            gl.add(t);
        }
        tag.put("graves", gl);
        ListTag cl = new ListTag();
        cairns.forEach(c -> cl.add(c.save()));
        tag.put("cairns", cl);
        ListTag fl = new ListTag();
        fallen.forEach(f -> fl.add(net.minecraft.nbt.StringTag.valueOf(f)));
        tag.put("fallen", fl);
        if (lectern != null) tag.put("lectern", lectern.save());
        return tag;
    }

    private static DeathData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeathData data = new DeathData();
        CompoundTag d = tag.getCompound("deaths");
        for (String k : d.getAllKeys()) data.deaths.put(UUID.fromString(k), d.getInt(k));
        CompoundTag r = tag.getCompound("lastRespawn");
        for (String k : r.getAllKeys()) data.lastRespawn.put(UUID.fromString(k), r.getLong(k));
        CompoundTag px = tag.getCompound("protectedXp");
        for (String k : px.getAllKeys()) {
            CompoundTag s = px.getCompound(k); Map<String, Long> m = new HashMap<>();
            for (String skill : s.getAllKeys()) m.put(skill, s.getLong(skill));
            data.protectedXp.put(UUID.fromString(k), m);
        }
        for (Tag t : tag.getList("graves", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Grave g = new Grave(c.getUUID("owner"), Place.load(c), c.getLong("time"));
            g.secondWindUsed = c.getBoolean("secondWind"); g.emptied = c.getBoolean("emptied");
            data.graves.add(g);
        }
        for (Tag t : tag.getList("cairns", Tag.TAG_COMPOUND)) data.cairns.add(Place.load((CompoundTag) t));
        for (Tag t : tag.getList("fallen", Tag.TAG_STRING)) data.fallen.add(t.getAsString());
        if (tag.contains("lectern")) data.lectern = Place.load(tag.getCompound("lectern"));
        return data;
    }
}
