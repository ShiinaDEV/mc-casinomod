package de.shiina.casino.network;

import de.shiina.casino.ShiinaCasino;
import de.shiina.casino.server.CasinoServer;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CasinoNetwork {
    public static Consumer<State> clientReceiver = state -> {};
    public record Action(int menu, UUID table, long round, long revision, long sequence, String action, long amount, int option) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShiinaCasino.ID, "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.menu); buffer.writeUUID(value.table); buffer.writeLong(value.round);
            buffer.writeLong(value.revision); buffer.writeLong(value.sequence); buffer.writeUtf(value.action, 32);
            buffer.writeLong(value.amount); buffer.writeInt(value.option);
        }, buffer -> new Action(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(32), buffer.readLong(), buffer.readInt()));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record State(int menu, UUID table, boolean full, long serverMillis, String json, long balance, long chips, int rate, String error) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShiinaCasino.ID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.menu); buffer.writeUUID(value.table); buffer.writeBoolean(value.full); buffer.writeLong(value.serverMillis); buffer.writeUtf(value.json, 65536);
            buffer.writeLong(value.balance); buffer.writeLong(value.chips); buffer.writeInt(value.rate); buffer.writeUtf(value.error, 128);
        }, buffer -> new State(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(), buffer.readLong(), buffer.readUtf(65536), buffer.readLong(), buffer.readLong(), buffer.readInt(), buffer.readUtf(128)));
        @Override public Type<State> type() { return TYPE; }
    }

    private CasinoNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Action.TYPE, Action.CODEC, (action, context) -> {
            if (context.player() instanceof ServerPlayer player) CasinoServer.get(player.server).action(player, action);
        });
        registrar.playToClient(State.TYPE, State.CODEC, (state, context) -> clientReceiver.accept(state));
    }
}
