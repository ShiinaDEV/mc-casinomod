package de.shiina.casino.menu;

import de.shiina.casino.CasinoContent;
import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.logic.Game;
import de.shiina.casino.server.CasinoServer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class CasinoMenu extends AbstractContainerMenu {
    public final UUID table;
    public final BlockPos pos;
    public final String kind;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    public Game.View view;
    public long receivedAt;
    public long serverOffset;
    public com.google.gson.JsonObject lastState;
    public long balance;
    public long chips;
    public int rate;
    public String error = "";
    public long sequence;
    public final de.shiina.casino.logic.ActionGate actionGate = new de.shiina.casino.logic.ActionGate();

    public CasinoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readUUID(), buffer.readBlockPos(), buffer.readUtf(16));
    }
    public CasinoMenu(int id, Inventory inventory, UUID table, BlockPos pos, String kind) {
        super(CasinoContent.MENU.get(), id); this.table = table; this.pos = pos; this.kind = kind; this.dimension=inventory.player.level().dimension();
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || (player.isAlive() && player.level().dimension().equals(dimension) && player.level().hasChunkAt(pos) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && player.level().getBlockState(pos).getBlock() instanceof CasinoBlock block && block.kind.equals(kind));
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) CasinoServer.get(serverPlayer.server).unwatch(serverPlayer, table);
    }
}
