package net.aegir.core.test;

import net.aegir.core.AegirCore;
import net.aegir.core.check.D20Command;
import net.aegir.core.death.DeathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automated checks, run with {@code gradlew runGameTestServer}. Registered only when game tests run
 * (RegisterGameTestsEvent); they do nothing in normal play.
 */
@GameTestHolder(AegirCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AegirGameTests {
    private static final String T = "floor";

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        return p;
    }

    private static Wolf wolf(GameTestHelper h, ServerPlayer owner, boolean sitting) {
        Wolf w = h.spawn(EntityType.WOLF, new BlockPos(5, 2, 5));
        w.tame(owner);
        w.setOrderedToSit(sitting);
        return w;
    }

    private static boolean waiting(Wolf w) { return w.getPersistentData().contains("aegir_wait"); }

    @GameTest(template = T, timeoutTicks = 60)
    public static void petWaitsWhenOwnerDies(GameTestHelper h) {
        ServerPlayer p = player(h);
        Wolf w = wolf(h, p, false);
        p.kill();
        h.succeedWhen(() -> h.assertTrue(waiting(w), "standing wolf should wait at the grave"));
    }

    @GameTest(template = T, timeoutTicks = 60)
    public static void sittingPetDoesNotWait(GameTestHelper h) {
        ServerPlayer p = player(h);
        Wolf w = wolf(h, p, true);
        p.kill();
        h.runAfterDelay(20, () -> {
            h.assertFalse(waiting(w), "a sitting wolf never learns of the death");
            h.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 80)
    public static void waitingPetIsSafeButKillable(GameTestHelper h) {
        ServerPlayer p = player(h);
        Wolf w = wolf(h, p, false);
        p.kill();
        h.runAfterDelay(10, () -> {
            h.assertTrue(waiting(w), "wolf should be waiting");
            float before = w.getHealth();
            w.hurt(h.getLevel().damageSources().generic(), 5);
            w.hurt(h.getLevel().damageSources().mobAttack(h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1))), 5);
            h.assertTrue(w.getHealth() == before, "waiting wolf must take no damage, had " + before + " now " + w.getHealth());
            w.kill();
            h.assertFalse(w.isAlive(), "/kill must still work");
            h.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 40)
    public static void bookOfTheFallenRecordsDeath(GameTestHelper h) {
        ServerPlayer p = player(h);
        int before = DeathData.get(h.getLevel().getServer()).fallen.size();
        p.kill();
        h.runAfterDelay(5, () -> {
            DeathData data = DeathData.get(h.getLevel().getServer());
            h.assertTrue(data.fallen.size() == before + 1, "fallen list should grow by one");
            h.assertTrue(data.lectern != null, "a lectern for the Book of the Fallen should exist");
            h.succeed();
        });
    }

    @GameTest(template = T)
    public static void d20RollsAreFair(GameTestHelper h) {
        ServerPlayer p = player(h);
        int[] seen = new int[21];
        for (int i = 0; i < 4000; i++) {
            D20Command.Result r = D20Command.roll(p, "smithing", 11);
            h.assertTrue(r.die() >= 1 && r.die() <= 20, "die out of range: " + r.die());
            if (r.die() == 20) h.assertTrue(r.outcome() == 2, "natural 20 must be a critical success");
            if (r.die() == 1) h.assertTrue(r.outcome() == -1, "natural 1 must be a critical failure");
            if (r.die() > 1 && r.die() < 20) h.assertTrue(r.success() == (r.total() >= 11), "success must follow total vs DC");
            seen[r.die()]++;
        }
        for (int f = 1; f <= 20; f++) h.assertTrue(seen[f] > 100, "face " + f + " rolled only " + seen[f] + " times in 4000");
        h.succeed();
    }

    @GameTest(template = T)
    public static void vanillaSettingsImportOnce(GameTestHelper h) {
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory("aegir-import");
            java.nio.file.Path van = java.nio.file.Files.createDirectories(root.resolve("vanilla")), inst = java.nio.file.Files.createDirectories(root.resolve("instance"));
            java.nio.file.Files.write(van.resolve("options.txt"), java.util.List.of("version:5023", "fov:1.0", "renderDistance:32",
                    "simulationDistance:20", "key_key.jump:key.keyboard.space", "resourcePacks:[\"vanilla\",\"file/Faithful\"]"));
            java.nio.file.Files.write(van.resolve("servers.dat"), new byte[]{1, 2, 3});
            java.nio.file.Files.write(inst.resolve("options.txt"), java.util.List.of("version:3955", "fov:0.0",
                    "resourcePacks:[\"vanilla\",\"mod_resources\",\"file/aegir-assets\"]", "key_key.aegir:key.keyboard.g"));
            h.assertTrue(net.aegir.core.client.VanillaSettings.importOnce(van, inst), "first run should import");
            var o = new java.util.HashMap<String, String>();
            for (String l : java.nio.file.Files.readAllLines(inst.resolve("options.txt"))) { int i = l.indexOf(':'); o.put(l.substring(0, i), l.substring(i + 1)); }
            h.assertTrue("3955".equals(o.get("version")), "pack data version must stay, got " + o.get("version"));
            h.assertTrue("1.0".equals(o.get("fov")), "vanilla FOV should win");
            h.assertTrue("12".equals(o.get("renderDistance")) && "8".equals(o.get("simulationDistance")), "view distances must be capped");
            h.assertTrue(o.get("resourcePacks").contains("aegir-assets"), "pack resource packs must stay");
            h.assertTrue("key.keyboard.space".equals(o.get("key_key.jump")) && "key.keyboard.g".equals(o.get("key_key.aegir")), "keybinds: vanilla + modded kept");
            h.assertTrue("true".equals(o.get("darkMojangStudiosBackground")), "loading screen should be black");
            h.assertTrue(java.nio.file.Files.exists(inst.resolve("servers.dat")), "server list should be copied");
            h.assertFalse(net.aegir.core.client.VanillaSettings.importOnce(van, inst), "second run must do nothing");
            h.succeed();
        } catch (java.io.IOException e) {
            h.fail("io: " + e);
        }
    }

    @EventBusSubscriber(modid = AegirCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Register {
        @SubscribeEvent
        static void register(RegisterGameTestsEvent event) {
            event.register(AegirGameTests.class);
        }
    }

    private AegirGameTests() {}
}
