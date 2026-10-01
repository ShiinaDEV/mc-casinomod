package de.shiina.casino;

import de.shiina.casino.block.CasinoBlock;
import de.shiina.casino.menu.CasinoMenu;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CasinoContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShiinaCasino.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShiinaCasino.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ShiinaCasino.ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShiinaCasino.ID);
    public static final DeferredItem<Item> CHIP = ITEMS.registerSimpleItem("casino_chip", new Item.Properties().stacksTo(64));
    public static final Map<String, DeferredBlock<CasinoBlock>> TABLE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<de.shiina.casino.block.StationPartBlock>> PART_BLOCKS = new LinkedHashMap<>();
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,ShiinaCasino.ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ShiinaCasino.ID);
    public static final Supplier<net.minecraft.world.entity.EntityType<de.shiina.casino.entity.CasinoSeatEntity>> SEAT = ENTITIES.register("seat",() ->
            net.minecraft.world.entity.EntityType.Builder.<de.shiina.casino.entity.CasinoSeatEntity>of(de.shiina.casino.entity.CasinoSeatEntity::new,net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(8).updateInterval(20).noSave().noSummon().build("shiinacasino:seat"));
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<de.shiina.casino.block.WheelBlockEntity>> WORLD_WHEEL = BLOCK_ENTITIES.register("world_wheel",() ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(de.shiina.casino.block.WheelBlockEntity::new,TABLE_BLOCKS.get("wheel").get(),TABLE_BLOCKS.get("roulette").get()).build(null));
    public static final Supplier<MenuType<CasinoMenu>> MENU = MENUS.register("casino", () -> IMenuTypeExtension.create(CasinoMenu::new));

    static {
        for (String kind : new String[]{"blackjack", "poker", "roulette", "wheel", "exchange"}) {
            String name = kind.equals("wheel") ? "fortune_wheel" : kind.equals("exchange") ? "chip_exchange" : kind + "_table";
            DeferredBlock<CasinoBlock> block = BLOCKS.register(name,
                    () -> new CasinoBlock(kind, BlockBehaviour.Properties.of().strength(2.5F, 1200F).sound(SoundType.WOOD).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
            TABLE_BLOCKS.put(kind, block);
            ITEMS.register(name, () -> new de.shiina.casino.block.StationItem(block.get(),new Item.Properties()));
            if (de.shiina.casino.logic.StationLayout.enlarged(kind)) PART_BLOCKS.put(kind,BLOCKS.register(name+"_part",() ->
                    new de.shiina.casino.block.StationPartBlock(kind,BlockBehaviour.Properties.of().strength(2.5F,1200F).sound(SoundType.WOOD).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))));
        }
        TABS.register("casino", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.shiinacasino"))
                .icon(() -> CHIP.get().getDefaultInstance()).displayItems((parameters, output) -> {
                    TABLE_BLOCKS.values().forEach(block -> output.accept(block.get()));
                    output.accept(CHIP.get());
                }).build());
    }

    private CasinoContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); MENUS.register(bus); TABS.register(bus); ENTITIES.register(bus); BLOCK_ENTITIES.register(bus); }
}
