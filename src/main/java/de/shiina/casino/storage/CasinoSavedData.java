package de.shiina.casino.storage;

import de.shiina.casino.logic.Ledger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class CasinoSavedData extends SavedData {
    public final Ledger ledger = new Ledger(this::setDirty);

    public static CasinoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CasinoSavedData::new, CasinoSavedData::load, null), "shiinacasino");
    }

    public static CasinoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CasinoSavedData data = new CasinoSavedData();
        ListTag balances = tag.getList("balances", 10);
        for (int index = 0; index < balances.size(); index++) {
            CompoundTag entry = balances.getCompound(index);
            data.ledger.restoreBalance(entry.getUUID("player"), entry.getLong("amount"));
        }
        ListTag pending = tag.getList("pending", 10);
        for (int index = 0; index < pending.size(); index++) {
            CompoundTag entry = pending.getCompound(index);
            Map<UUID, Long> amounts = new HashMap<>();
            ListTag recovery = entry.getList("recovery", 10);
            for (int player = 0; player < recovery.size(); player++) {
                CompoundTag amount = recovery.getCompound(player);
                amounts.put(amount.getUUID("player"), amount.getLong("amount"));
            }
            data.ledger.hold(entry.getUUID("id"), entry.getString("location"), entry.getString("game"), entry.getLong("round"), amounts);
        }
        return data;
    }

    public void checkpoint(MinecraftServer server) throws IOException {
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Files.createDirectories(directory);
        var target = directory.resolve("shiinacasino.dat");
        var temporary = directory.resolve("shiinacasino-checkpoint.tmp");
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), server.registryAccess()));
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        NbtIo.writeCompressed(root, temporary);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag balances = new ListTag();
        ledger.balances().forEach((player, amount) -> balances.add(amount(player, amount)));
        tag.put("balances", balances);
        ListTag pending = new ListTag();
        ledger.pending().forEach((id, obligation) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("location", obligation.location());
            entry.putString("game", obligation.game());
            entry.putLong("round", obligation.round());
            ListTag recovery = new ListTag();
            obligation.recovery().forEach((player, amount) -> recovery.add(amount(player, amount)));
            entry.put("recovery", recovery);
            pending.add(entry);
        });
        tag.put("pending", pending);
        return tag;
    }

    private static CompoundTag amount(UUID player, long amount) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putLong("amount", amount);
        return tag;
    }
}
