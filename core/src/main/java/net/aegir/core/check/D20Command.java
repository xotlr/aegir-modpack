package net.aegir.core.check;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.aegir.core.AegirCore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;

/**
 * {@code /aegir check <players> <skill> <dc> [label]}: roll a d20, add the player's Project MMO skill
 * modifier (+1 per 10 levels, so +10 at level 100) and compare with the difficulty, shown to everyone
 * within 32 blocks. A natural 20 always succeeds, a natural 1 always fails.
 * <p>
 * For Easy NPC dialogue and FTB Quests: the command result is the number of players that succeeded,
 * so {@code execute if ...} / {@code execute store result} can branch on it. The last roll is also
 * kept in scoreboards: {@code aegir_roll} (total) and {@code aegir_result} (-1 crit fail, 0 fail,
 * 1 success, 2 crit success).
 */
public final class D20Command {
    private static final double SHOW_RADIUS = 32;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("check").requires(s -> s.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .then(Commands.argument("dc", IntegerArgumentType.integer(1, 40))
                                        .executes(c -> run(c, ""))
                                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                                .executes(c -> run(c, StringArgumentType.getString(c, "label")))))));
    }

    private static int run(CommandContext<CommandSourceStack> c, String label) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        String skill = StringArgumentType.getString(c, "skill");
        int dc = IntegerArgumentType.getInteger(c, "dc");
        int successes = 0;
        for (ServerPlayer p : players) {
            Result r = roll(p, skill, dc);
            if (r.success()) successes++;
            announce(p, skill, dc, label, r);
            store(p, r);
        }
        return successes;
    }

    public record Result(int die, int modifier, int total, int outcome) {
        public boolean success() { return outcome > 0; }
    }

    public static Result roll(ServerPlayer p, String skill, int dc) {
        int die = 1 + p.getRandom().nextInt(20);
        int mod = modifier(p, skill);
        int total = die + mod;
        int outcome = die == 20 ? 2 : die == 1 ? -1 : total >= dc ? 1 : 0;
        return new Result(die, mod, total, outcome);
    }

    /** +1 per 10 Project MMO levels in the skill; 0 when PMMO is absent or the skill is unknown. */
    public static int modifier(ServerPlayer p, String skill) {
        if (!AegirCore.has("pmmo")) return 0;
        try {
            return (int) Math.min(10, harmonised.pmmo.api.APIUtils.getLevel(skill, p) / 10);
        } catch (Throwable t) {
            AegirCore.LOG.debug("PMMO level lookup failed for {}", skill, t);
            return 0;
        }
    }

    private static void announce(ServerPlayer p, String skill, int dc, String label, Result r) {
        ChatFormatting colour = switch (r.outcome()) {
            case 2 -> ChatFormatting.GOLD;
            case 1 -> ChatFormatting.GREEN;
            case 0 -> ChatFormatting.RED;
            default -> ChatFormatting.DARK_RED;
        };
        Component what = label.isEmpty() ? Component.literal(skill) : Component.literal(label);
        Component msg = Component.translatable("aegir_core.check.result",
                p.getDisplayName(), what, dc, r.die(), r.modifier() >= 0 ? "+" + r.modifier() : String.valueOf(r.modifier()),
                r.total(), Component.translatable("aegir_core.check.outcome." + r.outcome()).withStyle(colour));
        for (ServerPlayer near : p.serverLevel().players()) {
            if (near.distanceToSqr(p) <= SHOW_RADIUS * SHOW_RADIUS) near.sendSystemMessage(msg);
        }
    }

    private static void store(ServerPlayer p, Result r) {
        Scoreboard board = p.getServer().getScoreboard();
        set(board, "aegir_roll", p, r.total());
        set(board, "aegir_result", p, r.outcome());
    }

    private static void set(Scoreboard board, String name, ServerPlayer p, int value) {
        Objective o = board.getObjective(name);
        if (o == null) {
            o = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, true, null);
        }
        board.getOrCreatePlayerScore(p, o).set(value);
    }

    private D20Command() {}
}
