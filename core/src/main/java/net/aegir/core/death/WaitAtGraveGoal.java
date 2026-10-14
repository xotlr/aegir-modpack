package net.aegir.core.death;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A pet waiting at its owner's grave (see {@link PetWaiting}). Priority 0 with MOVE/LOOK/JUMP, so it
 * outranks FollowOwnerGoal (which would teleport the pet to its owner) and every attack goal.
 * <ul>
 *   <li>first 60 s: stay at the grave (wolves and cats lie down)</li>
 *   <li>then wander inside r = min(40, 6 + 2 x minutes since death); each minute a 20% chance to walk
 *       back and sit at the grave for 10-20 s</li>
 *   <li>cues every 45-90 s: wolves howl (volume 4, ~64 blocks), cats meow, parrots squawk</li>
 *   <li>owner within 4 blocks: stop, back off a step, sniff 2-3 s (half when crouching), then
 *       recognise: jump, hearts, horn; waiting ends</li>
 * </ul>
 */
final class WaitAtGraveGoal extends Goal {
    private final TamableAnimal pet;
    private int wanderCooldown, cueCooldown, sitUntil, reunionTicks = -1;
    private long lastMinuteRoll;

    WaitAtGraveGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() { return PetWaiting.isWaiting(pet); }
    @Override public boolean canContinueToUse() { return PetWaiting.isWaiting(pet); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        cueCooldown = 200 + pet.getRandom().nextInt(600);
        reunionTicks = -1;
    }

    @Override
    public void stop() {
        pet.setInSittingPose(pet.isOrderedToSit());
        pet.getNavigation().stop();
    }

    @Override
    public void tick() {
        PetWaiting.Wait w = PetWaiting.read(pet);
        if (w == null || !(pet.level() instanceof ServerLevel level)) return;
        pet.setTarget(null);
        RandomSource rnd = pet.getRandom();
        Vec3 anchor = Vec3.atBottomCenterOf(w.anchor());
        double minutes = w.minutesSinceDeath();

        ServerPlayer owner = pet.getOwner() instanceof ServerPlayer p && p.level() == level && p.isAlive() ? p : null;
        if (owner != null && owner.distanceToSqr(pet) < 16) {
            reunion(level, owner, rnd);
            return;
        }
        reunionTicks = -1;

        if (--cueCooldown <= 0) {
            cueCooldown = 900 + rnd.nextInt(900); // 45-90 s
            cue(level);
        }

        boolean early = minutes < 1.0;
        long minute = (long) minutes;
        if (!early && minute != lastMinuteRoll) {
            lastMinuteRoll = minute;
            if (rnd.nextFloat() < 0.2f) sitUntil = pet.tickCount + 200 + rnd.nextInt(200);
        }
        if (early || pet.tickCount < sitUntil) {
            if (pet.position().distanceToSqr(anchor) > 4) {
                pet.setInSittingPose(false);
                pet.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.0);
            } else {
                pet.getNavigation().stop();
                if (!(pet instanceof Parrot)) pet.setInSittingPose(true);
            }
            return;
        }

        pet.setInSittingPose(false);
        double r = PetWaiting.radius(minutes);
        if (pet.position().distanceToSqr(anchor) > (r + 4) * (r + 4)) {
            pet.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.0);
        } else if (--wanderCooldown <= 0 || pet.getNavigation().isDone() && wanderCooldown > 60) {
            wanderCooldown = 100 + rnd.nextInt(140);
            BlockPos spot = PetWaiting.randomStandable(level, w.anchor(), r, rnd);
            if (spot != null) pet.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.7);
        }
    }

    private void reunion(ServerLevel level, ServerPlayer owner, RandomSource rnd) {
        if (reunionTicks < 0) {
            reunionTicks = (40 + rnd.nextInt(21)) / (owner.isCrouching() ? 2 : 1); // sniff 2-3 s
            pet.setInSittingPose(false);
            Vec3 away = pet.position().subtract(owner.position()).normalize();
            pet.getNavigation().moveTo(pet.getX() + away.x, pet.getY(), pet.getZ() + away.z, 0.6); // back off a step
        }
        pet.getLookControl().setLookAt(owner, 30, 30);
        if (reunionTicks % 10 == 0) level.sendParticles(ParticleTypes.CLOUD, pet.getX(), pet.getY() + 0.3, pet.getZ(), 1, 0.1, 0.05, 0.1, 0);
        if (--reunionTicks > 0) return;
        pet.getNavigation().stop();
        pet.setDeltaMovement(pet.getDeltaMovement().add(0, 0.45, 0));
        level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 7, 0.4, 0.3, 0.4, 0);
        level.playSound(null, pet.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), SoundSource.NEUTRAL, 0.8f, 1.2f);
        level.playSound(null, pet.blockPosition(), happySound(), SoundSource.NEUTRAL, 1f, 1f);
        PetWaiting.stopWaiting(pet);
    }

    private void cue(ServerLevel level) {
        SoundEvent s; float vol;
        if (pet instanceof Wolf) { s = SoundEvents.WOLF_HOWL; vol = 4f; }
        else if (pet instanceof Cat) { s = SoundEvents.CAT_STRAY_AMBIENT; vol = 1.5f; }
        else if (pet instanceof Parrot) { s = SoundEvents.PARROT_AMBIENT; vol = 2f; }
        else return;
        level.playSound(null, pet.blockPosition(), s, SoundSource.NEUTRAL, vol, Mth.nextFloat(pet.getRandom(), 0.9f, 1.05f));
    }

    private SoundEvent happySound() {
        if (pet instanceof Wolf) return SoundEvents.WOLF_PANT;
        if (pet instanceof Cat) return SoundEvents.CAT_PURREOW;
        if (pet instanceof Parrot) return SoundEvents.PARROT_AMBIENT;
        return SoundEvents.PLAYER_LEVELUP;
    }
}
