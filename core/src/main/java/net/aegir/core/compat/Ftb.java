package net.aegir.core.compat;

import net.aegir.core.AegirCore;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that touches FTB Teams / FTB Chunks. Signatures use only Minecraft types, so other classes
 * (and NeoForge's event-subscriber scan) never need FTB classes; call only after checking {@link #teams()}
 * or {@link #chunks()}.
 */
public final class Ftb {
    public static boolean teams() { return AegirCore.has("ftbteams"); }
    public static boolean chunks() { return AegirCore.has("ftbchunks"); }

    /** Online members of the player's party team, excluding the player; null when not in a party. */
    public static List<ServerPlayer> partyMates(ServerPlayer player) {
        if (!teams()) return null;
        var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) return null;
        var team = api.getManager().getTeamForPlayer(player).orElse(null);
        if (team == null || !team.isPartyTeam()) return null;
        List<ServerPlayer> out = new ArrayList<>(team.getOnlineMembers());
        out.removeIf(p -> p.getUUID().equals(player.getUUID()));
        return out;
    }

    /** True when the target stands in a chunk claimed by a team the player is not a member of. */
    public static boolean claimedByOtherTeam(ServerPlayer player, ServerPlayer target) {
        if (!chunks()) return false;
        var api = dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api();
        if (!api.isManagerLoaded()) return false;
        var claim = api.getManager().getChunk(new dev.ftb.mods.ftblibrary.math.ChunkDimPos(target));
        if (claim == null) return false;
        var owner = claim.getTeamData().getTeam();
        return owner != null && !owner.getMembers().contains(player.getUUID());
    }

    private Ftb() {}
}
