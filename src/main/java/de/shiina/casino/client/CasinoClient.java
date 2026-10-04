package de.shiina.casino.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import de.shiina.casino.CasinoContent;
import de.shiina.casino.ShiinaCasino;
import de.shiina.casino.logic.Game;
import de.shiina.casino.menu.CasinoMenu;
import de.shiina.casino.network.CasinoNetwork;
import de.shiina.casino.network.SnapshotDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = ShiinaCasino.ID, dist = Dist.CLIENT)
public final class CasinoClient {
    private static final Gson JSON = new Gson();

    public CasinoClient(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CasinoClientConfig.SPEC);
        bus.addListener(CasinoClient::screens);
        bus.addListener(CasinoClient::renderers);
        de.shiina.casino.block.WheelBlockEntity.clientAnimation=WorldWheelSounds::update;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WorldWheelSounds::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WorldWheelSounds::logout);
        CasinoNetwork.clientReceiver = state -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || !(minecraft.player.containerMenu instanceof CasinoMenu menu)
                    || menu.containerId != state.menu() || !menu.table.equals(state.table())) return;
            Game.View previous = menu.view;
            var update = JsonParser.parseString(state.json()).getAsJsonObject();
            var candidate=SnapshotDelta.receive(menu.lastState, update, state.full(), menu.updateSequence);
            if (candidate == null) return;
            long updateSequence = candidate.get("updateSequence").getAsLong();
            if (updateSequence <= menu.updateSequence) return;
            var view=menu.kind.equals("exchange") ? null : JSON.fromJson(candidate, Game.View.class);
            if(previous!=null && view!=null && (view.round()<previous.round() || view.round()==previous.round() && view.revision()<previous.revision())) return;
            long previousAck = menu.acknowledgedSequence;
            boolean initial = menu.lastState == null;
            String oldHistory = initial ? "" : menu.lastState.getAsJsonArray("history").toString();
            menu.lastState=candidate; menu.view=view; menu.updateSequence=updateSequence;
            menu.acknowledgedSequence=candidate.get("ack").getAsLong();
            menu.exchangeRevision=candidate.get("exchangeRevision").getAsLong();
            menu.receivedAt = System.nanoTime() / 1000000L;
            if (initial || menu.acknowledgedSequence != previousAck || !menu.error.equals(state.error())) menu.feedbackAt=menu.receivedAt;
            menu.serverOffset = state.serverMillis() - menu.receivedAt;
            menu.balance = state.balance(); menu.chips = state.chips(); menu.rate = state.rate(); menu.error = state.error();
            if (!initial && menu.acknowledgedSequence > previousAck && menu.error.startsWith("casino.status."))
                CasinoEffects.play(SoundEvents.UI_BUTTON_CLICK.value(), 1F, .35F);
            if (!initial && !oldHistory.equals(candidate.getAsJsonArray("history").toString()))
                CasinoEffects.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1F, .4F);
            if (minecraft.screen instanceof CasinoScreen screen) screen.received(previous);
        };
    }

    private static void screens(RegisterMenuScreensEvent event) { event.register(CasinoContent.MENU.get(), CasinoScreen::new); }
    private static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CasinoContent.SEAT.get(),SeatRenderer::new);
        event.registerBlockEntityRenderer(CasinoContent.WORLD_WHEEL.get(),WorldWheelRenderer::new);
    }
}
