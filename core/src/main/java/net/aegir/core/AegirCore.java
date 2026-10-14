package net.aegir.core;

import com.mojang.brigadier.CommandDispatcher;
import net.aegir.core.check.D20Command;
import net.aegir.core.team.TeamTeleport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AEGIR Core: the AEGIR pack's own mod.
 * <ul>
 *   <li>{@code /aegir check} - d20 skill checks (check/D20Command)</li>
 *   <li>{@code /aegir team} - team teleport menu (team/TeamTeleport)</li>
 *   <li>death and lost-and-found: pets wait at the grave, death horn, Book of the Fallen, cairns (death/)</li>
 * </ul>
 * World wrap is handled by the Exotic Worlds mod, landmarks and hall seats wait for Ronald's builds.
 */
@Mod(AegirCore.MOD_ID)
public final class AegirCore {
    public static final String MOD_ID = "aegir_core";
    public static final Logger LOG = LoggerFactory.getLogger("AEGIR Core");

    public AegirCore() {
        LOG.info("AEGIR Core loaded (FTB Teams: {}, FTB Chunks: {}, Project MMO: {}, GraveStone: {})",
                has("ftbteams"), has("ftbchunks"), has("pmmo"), has("gravestone"));
    }

    public static boolean has(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class Commands_ {
        @SubscribeEvent
        static void register(RegisterCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
            var root = Commands.literal("aegir");
            root.then(D20Command.build());
            root.then(TeamTeleport.build());
            d.register(root);
            d.register(Commands.literal("tt").executes(c -> TeamTeleport.open(c.getSource().getPlayerOrException())));
        }
    }
}
