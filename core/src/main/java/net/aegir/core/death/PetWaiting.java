package net.aegir.core.death;

import net.aegir.core.AegirCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Pets stay with the body. On the owner's death, every tame pet the owner has within 48 blocks that is
 * not sitting starts waiting at the grave ({@link WaitAtGraveGoal}). Pets sitting elsewhere never learn
 * of the death. While waiting a pet takes no damage except /kill and the void, is ignored by hostiles,
 * never attacks, ignores other players' leads, and does not follow or teleport to its owner.
 * State lives in the pet's persistent NBT, so it survives restarts and chunk unloads.
 */
@EventBusSubscriber(modid = AegirCore.MOD_ID)
public final class PetWaiting {
    static final String TAG = "aegir_wait";
    static final double GATHER_RADIUS = 48;

    record Wait(UUID owner, BlockPos anchor, long since) {
        double minutesSinceDeath() { return (System.currentTimeMillis() - since) / 60000.0; }
    }

    static double radius(double minutes) { return Math.min(40, 6 + 2 * minutes); }

    static boolean isWaiting(LivingEntity e) { return e.getPersistentData().contains(TAG); }

    static Wait read(LivingEntity e) {
        if (!isWaiting(e)) return null;
        CompoundTag t = e.getPersistentData().getCompound(TAG);
        return new Wait(t.getUUID("owner"), new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")), t.getLong("since"));
    }

    static void startWaiting(TamableAnimal pet, UUID owner, BlockPos anchor) {
        CompoundTag t = new CompoundTag();
        t.putUUID("owner", owner);
        t.putInt("x", anchor.getX()); t.putInt("y", anchor.getY()); t.putInt("z", anchor.getZ());
        t.putLong("since", System.currentTimeMillis());
        pet.getPersistentData().put(TAG, t);
        pet.setTarget(null);
        pet.getNavigation().stop();
    }

    static void stopWaiting(TamableAnimal pet) {
        pet.getPersistentData().remove(TAG);
    }

    /** Called on the owner's death, once the grave position is known. Returns how many pets now wait. */
    static int gather(ServerLevel level, UUID owner, BlockPos deathPos, BlockPos anchor) {
        int n = 0;
        AABB box = new AABB(deathPos).inflate(GATHER_RADIUS);
        for (TamableAnimal pet : level.getEntitiesOfClass(TamableAnimal.class, box,
                p -> p.isTame() && owner.equals(p.getOwnerUUID()) && !p.isOrderedToSit() && !isWaiting(p))) {
            startWaiting(pet, owner, anchor);
            n++;
        }
        return n;
    }

    /** A random spot within r of the anchor where a mob can stand: solid below, air (no fluid) at feet and head. */
    static BlockPos randomStandable(ServerLevel level, BlockPos anchor, double r, RandomSource rnd) {
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            int x = anchor.getX() + (int) Math.round(Math.cos(a) * d), z = anchor.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos p = new BlockPos(x, anchor.getY() + dy, z);
                if (standable(level, p)) return p;
            }
        }
        return null;
    }

    static boolean standable(BlockGetter level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    /** Nearest standable block within 16 of pos (searching outward), or null. */
    static BlockPos nearestStandable(ServerLevel level, BlockPos pos) {
        if (standable(level, pos)) return pos;
        for (int r = 1; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (standable(level, p)) return p;
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof TamableAnimal pet)) return;
        pet.goalSelector.addGoal(0, new WaitAtGraveGoal(pet));
        Wait w = read(pet);
        if (w != null && event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level) {
            // The chunk was unloaded and is back: time passed for the pet too, so move it inside its current radius.
            BlockPos spot = randomStandable(level, w.anchor(), radius(w.minutesSinceDeath()), pet.getRandom());
            if (spot != null) pet.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, pet.getYRot(), pet.getXRot());
        }
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (isWaiting(event.getEntity()) && !event.getSource().is(DamageTypes.GENERIC_KILL)
                && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && isWaiting(target)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLead(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof TamableAnimal pet && isWaiting(pet) && event.getItemStack().is(Items.LEAD)
                && !event.getEntity().getUUID().equals(pet.getOwnerUUID())) {
            event.setCanceled(true);
        }
    }

    /** Surface position near x/z (for when nothing else is known). */
    static BlockPos surface(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private PetWaiting() {}
}
