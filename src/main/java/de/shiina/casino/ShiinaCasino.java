package de.shiina.casino;

import de.shiina.casino.network.CasinoNetwork;
import de.shiina.casino.server.CasinoServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ShiinaCasino.ID)
public final class ShiinaCasino {
    public static final String ID = "shiinacasino";

    public ShiinaCasino(IEventBus bus, ModContainer container) {
        CasinoContent.register(bus);
        bus.addListener(CasinoNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, CasinoConfig.SPEC);
        NeoForge.EVENT_BUS.register(CasinoServer.class);
        NeoForge.EVENT_BUS.register(de.shiina.casino.block.PendingStationRemoval.class);
        NeoForge.EVENT_BUS.register(de.shiina.casino.server.CasinoSeats.class);
    }
}
