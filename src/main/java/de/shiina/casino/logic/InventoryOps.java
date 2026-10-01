package de.shiina.casino.logic;

import java.util.ArrayList;
import java.util.List;

public final class InventoryOps {
    public interface Adapter<Stack> {
        boolean is(Stack stack, Object item);
        boolean mergeable(Stack stack, Object item);
        int count(Stack stack);
        int maximum(Stack stack);
        Stack resize(Stack stack, int count);
        Stack create(Object item, int count);
    }
    public record Delivery<Stack>(List<Stack> slots, long delivered) {}
    private InventoryOps() {}

    public static <Stack> List<Stack> remove(List<Stack> source, Adapter<Stack> adapter, Object item, long amount) {
        Money.checked(amount);
        long available = source.stream().filter(stack -> adapter.is(stack, item)).mapToLong(adapter::count).sum();
        if (available < amount) return null;
        List<Stack> result = new ArrayList<>(source);
        for (int slot = 0; slot < result.size() && amount > 0; slot++) {
            Stack stack = result.get(slot);
            if (!adapter.is(stack, item)) continue;
            int taken = (int) Math.min(amount, adapter.count(stack));
            result.set(slot, adapter.resize(stack, adapter.count(stack) - taken)); amount -= taken;
        }
        return result;
    }

    public static <Stack> Delivery<Stack> insert(List<Stack> source, Adapter<Stack> adapter, Object item, long amount) {
        Money.checked(amount);
        List<Stack> result = new ArrayList<>(source);
        long remaining = amount;
        for (int slot = 0; slot < result.size() && remaining > 0; slot++) {
            Stack stack = result.get(slot);
            if (adapter.count(stack) == 0 || !adapter.mergeable(stack, item)) continue;
            int added = (int) Math.min(Math.max(0, adapter.maximum(stack) - adapter.count(stack)), remaining);
            result.set(slot, adapter.resize(stack, adapter.count(stack) + added)); remaining -= added;
        }
        int maximum = adapter.maximum(adapter.create(item, 1));
        for (int slot = 0; slot < result.size() && remaining > 0; slot++) {
            if (adapter.count(result.get(slot)) != 0) continue;
            int added = (int) Math.min(maximum, remaining);
            result.set(slot, adapter.create(item, added)); remaining -= added;
        }
        return new Delivery<>(result, amount - remaining);
    }

    public static <Stack> List<Stack> exchange(List<Stack> source, Adapter<Stack> adapter,
                                               Object input, long cost, Object output, long reward) {
        List<Stack> removed = remove(source, adapter, input, cost);
        if (removed == null) return null;
        Delivery<Stack> delivery = insert(removed, adapter, output, reward);
        return delivery.delivered == reward ? delivery.slots : null;
    }
}
