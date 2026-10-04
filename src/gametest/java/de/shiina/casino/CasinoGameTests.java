package de.shiina.casino;

import com.mojang.authlib.GameProfile;
import de.shiina.casino.server.CasinoServer;
import de.shiina.casino.server.ChipInventory;
import de.shiina.casino.storage.CasinoSavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ShiinaCasino.ID)
@PrefixGameTestTemplate(false)
public final class CasinoGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void registeredBlocksHaveNoTickingBlockEntities(GameTestHelper helper) {
        int index = 0;
        for (var registered : CasinoContent.TABLE_BLOCKS.values()) {
            BlockPos pos = new BlockPos(index++,1,1);
            helper.setBlock(pos,registered.get());
            helper.assertBlockPresent(registered.get(),pos);
            var state=helper.getLevel().getBlockState(helper.absolutePos(pos));
            helper.assertTrue(registered.get().getTicker(helper.getLevel(),state,CasinoContent.WORLD_WHEEL.get()) == null,"Casino block must have no ticker");
        }
        helper.assertTrue(CasinoContent.CHIP.get().getDefaultInstance().getMaxStackSize() == 64,"Chips stack to 64");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void exchangeIsAtomicAndFullInventoryKeepsBalance(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"CasinoInventory"));
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));
        helper.assertTrue(!ChipInventory.exchange(player,Items.DIAMOND,1,CasinoContent.CHIP.get(),100),"Full inventory must reject exchange");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2,"Rejected exchange must not consume diamonds");
        player.getInventory().setItem(1,ItemStack.EMPTY); player.getInventory().setItem(2,ItemStack.EMPTY);
        helper.assertTrue(ChipInventory.exchange(player,Items.DIAMOND,1,CasinoContent.CHIP.get(),100),"Exchange must issue 100 chips");
        helper.assertTrue(player.getInventory().getItem(1).getCount() + player.getInventory().getItem(2).getCount() == 100,"Correct chip count");
        helper.assertTrue(ChipInventory.exchange(player,CasinoContent.CHIP.get(),100,Items.DIAMOND,1),"Return exchange must succeed");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2,"Both exchange directions must use same rate");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        player.getInventory().setItem(0,new ItemStack(CasinoContent.CHIP.get(),63));
        var ledger = CasinoSavedData.get(helper.getLevel().getServer()).ledger;
        ledger.credit(player.getUUID(),100);
        long claimed = ledger.claim(player.getUUID(),2304,amount -> ChipInventory.give(player,amount));
        helper.assertTrue(claimed == 1 && ledger.balance(player.getUUID()) == 99,"Only actual delivery may reduce balance");
        helper.assertTrue(ledger.claim(player.getUUID(),2304,amount -> ChipInventory.give(player,amount)) == 0,"Full inventory must leave balance untouched");
        player.getInventory().clearContent();
        helper.assertTrue(ledger.claim(player.getUUID(),2304,amount -> ChipInventory.give(player,amount)) == 99,"Remaining balance must be claimable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void replacingIdleTableCleansRegistration(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2,1,1), absolute = helper.absolutePos(relative);
        helper.setBlock(relative,CasinoContent.TABLE_BLOCKS.get("blackjack").get());
        var player = FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"CasinoCleanup"));
        player.setPos(absolute.getX() + .5,absolute.getY() + 1,absolute.getZ() + 1);
        var manager = CasinoServer.get(helper.getLevel().getServer()); int before = manager.registeredTables();
        manager.open(player,absolute,"blackjack");
        helper.assertTrue(manager.registeredTables() == before + 1,"Opening must register table");
        helper.setBlock(relative,Blocks.AIR);
        helper.assertTrue(manager.registeredTables() == before,"Replacement must clean idle table");
        helper.succeed();
    }

    private static BlockPos expanded(GameTestHelper helper,String kind) {
        for(int x=0;x<9;x++) for(int z=0;z<9;z++) helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var level=helper.getLevel();
        var state=CasinoContent.TABLE_BLOCKS.get(kind).get().defaultBlockState().setValue(de.shiina.casino.block.CasinoBlock.EXPANDED,true);
        for(int x=2;x<=6;x++) for(int z=2;z<=6;z++) {
            BlockPos anchor=helper.absolutePos(new BlockPos(x,1,z));
            if(de.shiina.casino.block.StationStructure.space(level,anchor,state,null)) {
                level.setBlock(anchor,state,3);
                helper.assertTrue(de.shiina.casino.block.StationStructure.install(level,anchor,state),"Station installation");
                return anchor;
            }
        }
        throw new IllegalStateException("No single-chunk fixture location");
    }
    private static net.minecraft.server.level.ServerPlayer interactivePlayer(GameTestHelper helper,String name) {
        var profile=new GameProfile(UUID.randomUUID(),name);
        // FakePlayer intentionally disables startRiding/openMenu. Use vanilla behavior with its no-op connection.
        var player=new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=FakePlayerFactory.get(helper.getLevel(),profile).connection;
        return player;
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void seatsAreExclusiveMenuCloseKeepsMountAndRemovalReleasesEverything(GameTestHelper helper) {
        BlockPos anchor=expanded(helper,"blackjack");var level=helper.getLevel();
        var layout=de.shiina.casino.logic.StationLayout.parts("blackjack");
        var offset=layout.stream().filter(p->p.seat()==0).findFirst().orElseThrow();
        BlockPos chair=de.shiina.casino.block.StationStructure.partPos(anchor,offset,net.minecraft.core.Direction.NORTH);
        var a=interactivePlayer(helper,"CasinoSeatA");
        var b=interactivePlayer(helper,"CasinoSeatB");
        a.setPos(chair.getX()+.5,chair.getY(),chair.getZ()+.5);b.setPos(chair.getX()+.5,chair.getY(),chair.getZ()+.5);
        var seats=de.shiina.casino.server.CasinoSeats.get(level.getServer());int before=seats.registry.size();
        seats.sit(a,anchor,chair,0,"blackjack");helper.assertTrue(a.isPassenger(),"Player must actually ride seat");
        var mount=a.getVehicle();
        a.closeContainer();helper.assertTrue(a.getVehicle()==mount,"Closing menu keeps the mount");
        seats.sit(b,anchor,chair,0,"blackjack");helper.assertTrue(!b.isPassenger(),"Two players cannot share a chair");
        seats.sit(a,anchor,chair,0,"blackjack");helper.assertTrue(a.getVehicle()==mount,"Reopening menu must not spawn another entity");
        a.stopRiding();helper.assertTrue(seats.registry.size()==before && mount.isRemoved(),"Dismount releases occupancy and entity");
        seats.sit(b,anchor,chair,0,"blackjack");helper.assertTrue(b.isPassenger(),"Released chair can be reused");
        level.removeBlock(anchor,false);helper.assertTrue(!b.isPassenger() && seats.registry.size()==before,"Block removal frees all chairs");
        for(var part:layout) helper.assertTrue(level.getBlockState(de.shiina.casino.block.StationStructure.partPos(anchor,part,net.minecraft.core.Direction.NORTH)).isAir(),"All owned parts removed");
        a.closeContainer();b.closeContainer();helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void rouletteChairsResolveOneStationAndRejectWrongOwner(GameTestHelper helper) {
        BlockPos anchor=expanded(helper,"roulette");var level=helper.getLevel();
        for(var part:de.shiina.casino.logic.StationLayout.parts("roulette")) {
            BlockPos pos=de.shiina.casino.block.StationStructure.partPos(anchor,part,net.minecraft.core.Direction.NORTH);
            helper.assertTrue(anchor.equals(de.shiina.casino.block.StationStructure.anchor(level,pos)),"Every part has the same anchor");
        }
        var seat=de.shiina.casino.logic.StationLayout.parts("roulette").stream().filter(p->p.seat()==5).findFirst().orElseThrow();
        BlockPos chair=de.shiina.casino.block.StationStructure.partPos(anchor,seat,net.minecraft.core.Direction.NORTH);
        var p=interactivePlayer(helper,"CasinoOwner");p.setPos(chair.getX()+.5,chair.getY(),chair.getZ()+.5);
        var seats=de.shiina.casino.server.CasinoSeats.get(level.getServer());
        seats.sit(p,anchor.offset(1,0,0),chair,5,"roulette");helper.assertTrue(!p.isPassenger(),"Wrong station rejected");
        seats.sit(p,anchor,chair,5,"roulette");helper.assertTrue(seats.at(p,anchor),"Roulette seat attached to owner");
        helper.assertTrue(!seats.at(p,anchor.offset(1,0,0)),"Seat cannot authorize another table");
        p.stopRiding();p.closeContainer();level.removeBlock(anchor,false);helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void networkRejectsWrongTableFarPlayerAndDuplicateSequence(GameTestHelper helper) {
        BlockPos anchor=helper.absolutePos(new BlockPos(2,1,2));helper.setBlock(new BlockPos(2,1,2),CasinoContent.TABLE_BLOCKS.get("exchange").get());
        var player=interactivePlayer(helper,"CasinoPackets");
        player.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+1);
        player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,4));
        var manager=CasinoServer.get(helper.getLevel().getServer());manager.open(player,anchor,"exchange");
        var menu=(de.shiina.casino.menu.CasinoMenu)player.containerMenu;
        manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,UUID.randomUUID(),0,0,1,"buy",1,CasinoConfig.RATE.get()));
        helper.assertTrue(player.getInventory().getItem(0).getCount()==4,"Wrong station must not debit");
        player.setPos(anchor.getX()+20,anchor.getY(),anchor.getZ());
        manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,1,"buy",1,CasinoConfig.RATE.get()));
        helper.assertTrue(player.getInventory().getItem(0).getCount()==4,"Far action must not debit");
        player.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+1);
        var packet=new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,1,"buy",1,CasinoConfig.RATE.get());
        manager.action(player,packet);manager.action(player,packet);
        helper.assertTrue(player.getInventory().getItem(0).getCount()==3,"Duplicate sequence must debit exactly once");
        helper.assertTrue(menu.error.equals("casino.status.exchange"),"Success must come from the accepted server action");
        manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,2,"buy",1,CasinoConfig.RATE.get()));
        helper.assertTrue(player.getInventory().getItem(0).getCount()==3,"Old exchange revision must not consume items");
        helper.assertTrue(menu.error.equals("casino.error.stale") || menu.error.equals("casino.error.fast"),"Stale/limited action needs feedback");
        helper.assertTrue(menu.chips==CasinoConfig.RATE.get(),"Displayed spendable chips match main inventory");
        player.closeContainer();helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void bulkExchangeValidatesRateRevisionAndInventoryBeforeConsuming(GameTestHelper helper) {
        BlockPos anchor=helper.absolutePos(new BlockPos(2,1,2));
        helper.setBlock(new BlockPos(2,1,2),CasinoContent.TABLE_BLOCKS.get("exchange").get());
        var player=interactivePlayer(helper,"CasinoBulk");player.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+1);
        for(int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
        var manager=CasinoServer.get(helper.getLevel().getServer());manager.open(player,anchor,"exchange");
        var menu=(de.shiina.casino.menu.CasinoMenu)player.containerMenu;int rate=CasinoConfig.RATE.get();
        manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,1,"buy",1,rate));
        helper.assertTrue(menu.error.equals("casino.error.inventory"),"Full inventory must explain rejection");
        helper.assertTrue(ChipInventory.count(player,Items.DIAMOND)==3 && menu.exchangeRevision==0,"Rejection must not mutate items or revision");
        helper.runAfterDelay(2,()->{
            player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
            manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,2,"buy",2,rate+1));
            helper.assertTrue(menu.error.equals("casino.error.stale") && ChipInventory.count(player,Items.DIAMOND)==3,"Changed quoted rate must reject without consumption");
            manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,3,"buy",2,rate));
            helper.assertTrue(ChipInventory.count(player,Items.DIAMOND)==1 && ChipInventory.count(player,CasinoContent.CHIP.get())==2L*rate,"Bulk buy must use exact existing rate");
            helper.runAfterDelay(2,()->{
                manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,0,4,"buy",1,rate));
                helper.assertTrue(menu.error.equals("casino.error.stale") && ChipInventory.count(player,Items.DIAMOND)==1,"New sequence with old revision must not buy twice");
                manager.action(player,new de.shiina.casino.network.CasinoNetwork.Action(menu.containerId,menu.table,0,1,5,"sell",2,rate));
                helper.assertTrue(ChipInventory.count(player,Items.DIAMOND)==3 && ChipInventory.count(player,CasinoContent.CHIP.get())==0,"Bulk sell must conserve the original diamonds");
                player.closeContainer();helper.succeed();
            });
        });
    }
}
