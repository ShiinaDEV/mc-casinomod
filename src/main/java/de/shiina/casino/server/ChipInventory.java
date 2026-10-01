package de.shiina.casino.server;

import de.shiina.casino.CasinoContent;
import de.shiina.casino.logic.Money;
import de.shiina.casino.logic.InventoryOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ChipInventory {
    private static final InventoryOps.Adapter<ItemStack> ADAPTER = new InventoryOps.Adapter<>() {
        public boolean is(ItemStack stack, Object item) { return stack.is((Item) item); }
        public boolean mergeable(ItemStack stack, Object item) { return ItemStack.isSameItemSameComponents(stack, new ItemStack((Item) item)); }
        public int count(ItemStack stack) { return stack.getCount(); }
        public int maximum(ItemStack stack) { return stack.getMaxStackSize(); }
        public ItemStack resize(ItemStack stack, int count) { return stack.copyWithCount(count); }
        public ItemStack create(Object item, int count) { return new ItemStack((Item) item, count); }
    };
    private ChipInventory() {}

    public static List<ItemStack> copy(ServerPlayer player) {
        List<ItemStack> slots = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) slots.add(player.getInventory().getItem(slot).copy());
        return slots;
    }

    public static boolean remove(List<ItemStack> slots, Item item, long amount) {
        List<ItemStack> result = InventoryOps.remove(slots, ADAPTER, item, amount);
        if (result == null) return false;
        for (int slot = 0; slot < slots.size(); slot++) slots.set(slot, result.get(slot));
        return true;
    }

    public static long insert(List<ItemStack> slots, Item item, long amount) {
        InventoryOps.Delivery<ItemStack> result = InventoryOps.insert(slots, ADAPTER, item, amount);
        for (int slot = 0; slot < slots.size(); slot++) slots.set(slot, result.slots().get(slot));
        return result.delivered();
    }

    public static void commit(ServerPlayer player, List<ItemStack> slots) {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, slots.get(slot));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    public static boolean debit(ServerPlayer player, long amount) {
        List<ItemStack> slots = copy(player);
        if (!remove(slots, CasinoContent.CHIP.get(), amount)) return false;
        commit(player, slots);
        return true;
    }

    public static boolean exchange(ServerPlayer player, Item input, long cost, Item output, long reward) {
        List<ItemStack> slots = InventoryOps.exchange(copy(player), ADAPTER, input, cost, output, reward);
        if (slots == null) return false;
        commit(player, slots);
        return true;
    }

    public static long give(ServerPlayer player, long amount) {
        List<ItemStack> slots = copy(player);
        long delivered = insert(slots, CasinoContent.CHIP.get(), amount);
        commit(player, slots);
        return delivered;
    }
}
