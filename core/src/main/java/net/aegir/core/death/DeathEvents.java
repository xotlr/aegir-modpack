package net.aegir.core.death;

import net.aegir.core.AegirCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happens around a player's death: the pets' anchor (grave, else nearest standable block, else
 * last safe ground), the team death horn, the Book of the Fallen, Second Wind, no double losses,
 * cairns where emptied graves stood, and Aegir's voice after the 1st, 5th and 10th death.
 */
@EventBusSubscriber(modid = AegirCore.MOD_ID)
public final class DeathEvents {
    static final long PROTECT_MS = 10 * 60_000L;
    static final ResourceLocation GRAVE_BLOCK = ResourceLocation.fromNamespaceAndPath("gravestone", "gravestone");
    static final int CAIRN_SPACING = 24;

    private record Pending(UUID player, ServerLevel level, BlockPos deathPos, long dueTick) {}

    private static final List<Pending> pending = new ArrayList<>();
    private static final Map<UUID, BlockPos> lastSafe = new HashMap<>();

    // ---- death ------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        DeathData data = DeathData.get(server);
        UUID id = player.getUUID();
        int count = data.deaths.merge(id, 1, Integer::sum);

        // No double losses: a death within 10 min of the last respawn keeps the skill XP it had right now
        // (HIGHEST priority runs before Project MMO applies its death penalty); restored on respawn.
        Long respawned = data.lastRespawn.get(id);
        if (respawned != null && System.currentTimeMillis() - respawned <= PROTECT_MS) {
            Map<String, Long> xp = Pmmo.snapshot(player);
            if (xp != null) data.protectedXp.put(id, xp);
        } else {
            data.protectedXp.remove(id);
        }

        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        Component place = placeName(level, at);
        Component cause = event.getSource().getLocalizedDeathMessage(player);
        long day = level.getDayTime() / 24000L + 1;
        data.fallen.add(Component.translatable("aegir_core.book.entry", day, player.getGameProfile().getName(), place).getString()
                + "\n" + cause.getString());
        data.setDirty();
        updateBook(server, data);
        deathHorn(player, place);

        // The grave is placed while drops are handled, after this event: resolve the anchor a few ticks later.
        pending.add(new Pending(id, level, at, server.getTickCount() + 3));
        AegirCore.LOG.debug("{} died ({} deaths)", player.getGameProfile().getName(), count);
    }

    private static void deathHorn(ServerPlayer dead, Component place) {
        List<ServerPlayer> mates = net.aegir.core.compat.Ftb.partyMates(dead);
        if (mates == null) return;
        Component msg = Component.translatable("aegir_core.death.toast", dead.getDisplayName(), place).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer m : mates) {
            m.playNotifySound(SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(6).value(), SoundSource.PLAYERS, 0.7f, 0.6f);
            m.displayClientMessage(msg, true);
        }
    }

    static Component placeName(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .<Component>map(k -> Component.translatable(k.location().toLanguageKey("biome")))
                .orElse(Component.literal("?"));
    }

    // ---- respawn ----------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        DeathData data = DeathData.get(player.server);
        UUID id = player.getUUID();
        Map<String, Long> xp = data.protectedXp.remove(id);
        if (xp != null && Pmmo.restore(player, xp)) {
            player.sendSystemMessage(Component.translatable("aegir_core.death.no_double_loss").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        data.lastRespawn.put(id, System.currentTimeMillis());
        int n = data.deaths.getOrDefault(id, 0);
        if (n == 1 || n == 5 || n == 10) {
            player.sendSystemMessage(Component.translatable("aegir_core.voice." + n).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
        data.setDirty();
    }

    // ---- ticking: anchors, safe ground, cairns ----------------------------------------------------

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (tick < p.dueTick()) continue;
            it.remove();
            resolveAnchor(server, p);
        }
        if (tick % 20 == 0) {
            for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
                if (pl.onGround() && !pl.isInWater() && !pl.isInLava() && pl.isAlive()) lastSafe.put(pl.getUUID(), pl.blockPosition());
            }
        }
        if (tick % 100 == 0) checkCairns(server);
    }

    private static void resolveAnchor(MinecraftServer server, Pending p) {
        ServerLevel level = p.level();
        BlockPos grave = findGrave(level, p.deathPos());
        BlockPos anchor = grave != null ? PetWaiting.nearestStandable(level, grave.above()) : null;
        if (anchor == null) anchor = PetWaiting.nearestStandable(level, p.deathPos());
        if (anchor == null) anchor = lastSafe.getOrDefault(p.player(), p.deathPos());
        DeathData data = DeathData.get(server);
        if (grave != null) data.addGrave(p.player(), new DeathData.Place(level.dimension(), grave), System.currentTimeMillis());
        int pets = PetWaiting.gather(level, p.player(), p.deathPos(), anchor);
        AegirCore.LOG.debug("death anchor {} (grave {}), {} pets waiting", anchor, grave, pets);
    }

    private static BlockPos findGrave(ServerLevel level, BlockPos around) {
        Block graveBlock = BuiltInRegistries.BLOCK.get(GRAVE_BLOCK);
        if (graveBlock == Blocks.AIR) return null;
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-8, -8, -8), around.offset(8, 8, 8))) {
            if (level.getBlockState(p).is(graveBlock)) {
                double d = p.distSqr(around);
                if (d < bestD) { bestD = d; best = p.immutable(); }
            }
        }
        return best;
    }

    private static void checkCairns(MinecraftServer server) {
        DeathData data = DeathData.get(server);
        Block graveBlock = BuiltInRegistries.BLOCK.get(GRAVE_BLOCK);
        for (DeathData.Grave g : data.graves) {
            if (g.emptied) continue;
            ServerLevel level = server.getLevel(g.place.dim());
            BlockPos pos = g.place.pos();
            if (level == null || !level.isLoaded(pos) || level.getBlockState(pos).is(graveBlock)) continue;
            g.emptied = true;
            data.setDirty();
            boolean near = data.cairns.stream().anyMatch(c -> c.dim().equals(g.place.dim()) && c.pos().distSqr(pos) < CAIRN_SPACING * CAIRN_SPACING);
            if (!near && placeCairn(level, pos)) data.cairns.add(g.place);
        }
    }

    /** 3-5 mossy cobblestone and one unlit candle beside where the grave stood; only into empty space. */
    private static boolean placeCairn(ServerLevel level, BlockPos grave) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos base = grave.relative(dir);
            if (!PetWaiting.standable(level, base)) continue;
            BlockPos side = base.relative(dir.getClockWise());
            List<BlockPos> stones = new ArrayList<>(List.of(base, base.above()));
            if (level.getBlockState(side).canBeReplaced() && !level.getBlockState(side.below()).canBeReplaced()) stones.add(side);
            BlockPos back = base.relative(dir);
            if (level.getRandom().nextBoolean() && level.getBlockState(back).canBeReplaced() && !level.getBlockState(back.below()).canBeReplaced()) stones.add(back);
            BlockPos candle = base.above(2);
            if (!level.getBlockState(candle).canBeReplaced()) continue;
            for (BlockPos s : stones) level.setBlockAndUpdate(s, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            level.setBlockAndUpdate(candle, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false));
            return true;
        }
        return false;
    }

    // ---- Second Wind ------------------------------------------------------------------------------

    @SubscribeEvent
    static void onOpenGrave(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(GRAVE_BLOCK)) return;
        DeathData data = DeathData.get(player.server);
        for (DeathData.Grave g : data.graves) {
            if (!g.secondWindUsed && g.owner.equals(player.getUUID()) && g.place.pos().equals(event.getPos())
                    && g.place.dim().equals(player.level().dimension())) {
                g.secondWindUsed = true;
                data.setDirty();
                int t = 50 * 20;
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, t, 0));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 0));
                player.displayClientMessage(Component.translatable("aegir_core.death.second_wind").withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
    }

    // ---- Book of the Fallen -----------------------------------------------------------------------

    private static void updateBook(MinecraftServer server, DeathData data) {
        ServerLevel level = server.overworld();
        BlockPos pos = data.lectern != null && data.lectern.dim().equals(level.dimension()) ? data.lectern.pos() : null;
        if (pos == null || !(level.getBlockState(pos).getBlock() instanceof LecternBlock)) {
            if (pos != null && !level.getBlockState(pos).canBeReplaced()) pos = null; // someone built there: find a new spot
            if (pos == null) {
                BlockPos spawn = level.getSharedSpawnPos();
                pos = PetWaiting.surface(level, spawn.getX() + 3, spawn.getZ());
            }
            level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState());
            data.lectern = new DeathData.Place(level.dimension(), pos);
            data.setDirty();
        }
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            ItemStack book = book(data.fallen);
            lectern.setBook(book);
            LecternBlock.resetBookState(null, level, pos, level.getBlockState(pos), true);
        }
    }

    private static ItemStack book(List<String> fallen) {
        List<Filterable<Component>> pages = new ArrayList<>();
        int perPage = 3;
        List<String> recent = fallen.subList(Math.max(0, fallen.size() - 100 * perPage), fallen.size());
        for (int i = 0; i < recent.size(); i += perPage) {
            MutableComponent page = Component.empty();
            for (int j = i; j < Math.min(i + perPage, recent.size()); j++) {
                if (j > i) page.append("\n\n");
                page.append(Component.literal(recent.get(j)));
            }
            pages.add(Filterable.passThrough(page));
        }
        if (pages.isEmpty()) pages.add(Filterable.passThrough(Component.translatable("aegir_core.book.empty")));
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(Component.translatable("aegir_core.book.title").getString()), "AEGIR", 0, pages, true));
        return stack;
    }

    // ---- Project MMO bridge (only touched when PMMO is loaded) ------------------------------------

    static final class Pmmo {
        static Map<String, Long> snapshot(ServerPlayer p) {
            if (!AegirCore.has("pmmo")) return null;
            try {
                Map<String, Long> out = new HashMap<>();
                for (String skill : harmonised.pmmo.api.APIUtils.getAllLevels(p).keySet()) out.put(skill, harmonised.pmmo.api.APIUtils.getXp(skill, p));
                return out;
            } catch (Throwable t) {
                AegirCore.LOG.warn("Project MMO snapshot failed", t);
                return null;
            }
        }

        static boolean restore(ServerPlayer p, Map<String, Long> xp) {
            if (!AegirCore.has("pmmo")) return false;
            try {
                xp.forEach((skill, value) -> harmonised.pmmo.api.APIUtils.setXp(skill, p, value));
                return true;
            } catch (Throwable t) {
                AegirCore.LOG.warn("Project MMO restore failed", t);
                return false;
            }
        }
    }

    private DeathEvents() {}
}
