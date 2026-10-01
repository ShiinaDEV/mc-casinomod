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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = ShiinaCasino.ID, dist = Dist.CLIENT)
public final class CasinoClient {
    private static final Gson JSON = new Gson();

    public CasinoClient(IEventBus bus) {
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
            if(!state.full() && menu.lastState==null) return;
            var candidate=state.full() ? update : SnapshotDelta.apply(menu.lastState, update);
            var view=menu.kind.equals("exchange") ? null : JSON.fromJson(candidate, Game.View.class);
            if(previous!=null && view!=null && (view.round()<previous.round() || view.round()==previous.round() && view.revision()<previous.revision())) return;
            menu.lastState=candidate; menu.view=view;
            menu.receivedAt = System.nanoTime() / 1000000L;
            menu.serverOffset = state.serverMillis() - menu.receivedAt;
            menu.balance = state.balance(); menu.chips = state.chips(); menu.rate = state.rate(); menu.error = state.error();
            if (menu.view != null && (previous == null || !previous.phase().equals(menu.view.phase())))
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(menu.view.phase().equals("result")
                        ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.UI_BUTTON_CLICK.value(), 1));
            if (minecraft.screen instanceof CasinoScreen screen) screen.received(previous);
        };
    }

    private static void screens(RegisterMenuScreensEvent event) { event.register(CasinoContent.MENU.get(), CasinoScreen::new); }
    private static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CasinoContent.SEAT.get(),SeatRenderer::new);
        event.registerBlockEntityRenderer(CasinoContent.WORLD_WHEEL.get(),WorldWheelRenderer::new);
    }
}
