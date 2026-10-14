package net.aegir.core.client;

import net.aegir.core.AegirCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client start: import the player's normal Minecraft settings once, then reload them in place. */
@EventBusSubscriber(modid = AegirCore.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (VanillaSettings.importOnce(VanillaSettings.vanillaDir(), mc.gameDirectory.toPath())) {
                mc.options.load();
                KeyMapping.resetMapping();
                mc.options.save();
            }
        });
    }

    private ClientSetup() {}
}
