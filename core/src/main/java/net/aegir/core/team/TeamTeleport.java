package net.aegir.core.team;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aegir.core.AegirCore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team teleport menu ({@code /tt}, {@code /aegir team}): a server-side chest menu with a head for every
 * online member of the player's FTB Teams party. Clicking a head teleports instantly (no accept step
 * within a team). Refused when the destination chunk is claimed by another team (FTB Chunks).
 * 30 s cooldown. Players outside the team keep using FTB Essentials' /tpa.
 */
public final class TeamTeleport {
    private static final int ROWS = 3, SLOTS = ROWS * 9;
    private static final long COOLDOWN_TICKS = 30 * 20;
    private static final Map<UUID, Long> lastUse = new HashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("team").executes(c -> open(c.getSource().getPlayerOrException()));
    }

    public static int open(ServerPlayer player) {
        List<ServerPlayer> mates = net.aegir.core.compat.Ftb.partyMates(player);
        if (mates == null) {
            player.sendSystemMessage(Component.translatable("aegir_core.team.no_team").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (mates.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aegir_core.team.nobody_online").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        SimpleContainer box = new SimpleContainer(SLOTS);
        List<UUID> targets = new ArrayList<>();
        for (ServerPlayer m : mates) {
            if (targets.size() == SLOTS) break;
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(m.getGameProfile()));
            head.set(DataComponents.CUSTOM_NAME, Component.literal(m.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
            box.setItem(targets.size(), head);
            targets.add(m.getUUID());
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menu(id, inv, box, targets),
                Component.translatable("aegir_core.team.title")));
        return 1;
    }

    static void teleport(ServerPlayer player, UUID targetId) {
        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        long now = player.server.getTickCount();
        Long last = lastUse.get(player.getUUID());
        if (target == null) {
            player.sendSystemMessage(Component.translatable("aegir_core.team.gone").withStyle(ChatFormatting.GRAY));
        } else if (last != null && now - last < COOLDOWN_TICKS) {
            player.sendSystemMessage(Component.translatable("aegir_core.team.cooldown", (COOLDOWN_TICKS - (now - last)) / 20 + 1).withStyle(ChatFormatting.GRAY));
        } else if (net.aegir.core.compat.Ftb.claimedByOtherTeam(player, target)) {
            player.sendSystemMessage(Component.translatable("aegir_core.team.claimed", target.getDisplayName()).withStyle(ChatFormatting.RED));
        } else {
            lastUse.put(player.getUUID(), now);
            player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            player.sendSystemMessage(Component.translatable("aegir_core.team.arrived", target.getDisplayName()).withStyle(ChatFormatting.GREEN));
        }
    }

    /** Chest menu whose slots are buttons: clicking a head teleports, nothing can be taken or moved. */
    static final class Menu extends ChestMenu {
        private final List<UUID> targets;

        Menu(int id, Inventory inv, SimpleContainer box, List<UUID> targets) {
            super(MenuType.GENERIC_9x3, id, inv, box, ROWS);
            this.targets = targets;
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < targets.size() && player instanceof ServerPlayer sp) {
                sp.closeContainer();
                teleport(sp, targets.get(slot));
            }
            // Never fall through to the vanilla click logic: heads stay put, the player's own inventory is untouched.
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private TeamTeleport() {}
}
