package net.aegir.core.client;

import net.aegir.core.AegirCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Once per instance: copy the player's settings from the normal launcher's .minecraft (keybinds,
 * sensitivity, FOV, sound, GUI scale, language, ...) and its server list into this instance.
 * Pure file logic, no Minecraft classes, so the game tests can run it on temp folders.
 * Replaces the old Windows-only PowerShell pre-launch step.
 */
public final class VanillaSettings {
    /** Kept from the instance: the pack's data version and resource packs. */
    static final List<String> KEEP = List.of("version", "resourcePacks", "incompatibleResourcePacks");
    static final String DATA_VERSION_1_21_1 = "3955";
    static final String MARKER = "aegir/.vanilla-imported";

    /** Where the normal launcher keeps its game folder on this OS. */
    public static Path vanillaDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Path.of(appdata != null ? appdata : home + "\\AppData\\Roaming", ".minecraft");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "minecraft");
        return Path.of(home, ".minecraft");
    }

    /** Returns true when something was imported (the caller then reloads the options). */
    public static boolean importOnce(Path vanilla, Path instance) {
        Path marker = instance.resolve(MARKER);
        if (Files.exists(marker)) return false;
        boolean changed = false;
        try {
            Path src = vanilla.resolve("options.txt"), dst = instance.resolve("options.txt");
            if (Files.isRegularFile(src)) {
                Map<String, String> merged = merge(read(dst), read(src));
                Files.write(dst, merged.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).toList(), StandardCharsets.UTF_8);
                changed = true;
            }
            Path servers = vanilla.resolve("servers.dat"), ours = instance.resolve("servers.dat");
            if (Files.isRegularFile(servers) && !Files.exists(ours)) {
                Files.copy(servers, ours, StandardCopyOption.COPY_ATTRIBUTES);
                changed = true;
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "imported from " + vanilla + "\n");
            AegirCore.LOG.info("Imported vanilla settings from {}", vanilla);
        } catch (IOException e) {
            AegirCore.LOG.warn("Could not import vanilla settings from {}", vanilla, e); // retried next start
        }
        return changed;
    }

    /** Vanilla values over the instance's, except the pack's own keys; view distances capped for this pack. */
    static Map<String, String> merge(Map<String, String> instance, Map<String, String> vanilla) {
        Map<String, String> out = new LinkedHashMap<>(instance);
        out.putAll(vanilla);
        for (String k : KEEP) {
            if (instance.containsKey(k)) out.put(k, instance.get(k)); else out.remove(k);
        }
        out.putIfAbsent("version", DATA_VERSION_1_21_1);
        cap(out, "renderDistance", 12);    // Distant Horizons draws the far terrain
        cap(out, "simulationDistance", 8);
        out.put("onboardAccessibility", "false");
        out.put("darkMojangStudiosBackground", "true"); // black behind the AEGIR loading logo
        return out;
    }

    private static void cap(Map<String, String> m, String key, int max) {
        try {
            if (Integer.parseInt(m.getOrDefault(key, "0")) > max) m.put(key, String.valueOf(max));
        } catch (NumberFormatException ignored) {
        }
    }

    static Map<String, String> read(Path file) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return m;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int i = line.indexOf(':');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    private VanillaSettings() {}
}
