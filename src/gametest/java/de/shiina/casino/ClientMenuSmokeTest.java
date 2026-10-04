package de.shiina.casino;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.shiina.casino.client.CasinoScreen;
import de.shiina.casino.logic.Game;
import de.shiina.casino.logic.PersonalRounds;
import de.shiina.casino.logic.Roulette;
import de.shiina.casino.logic.Rules;
import de.shiina.casino.menu.CasinoMenu;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only: real Minecraft fonts, GUI rendering and local controls at scales 1-4.
 * Fixture snapshots intentionally do not claim to test multiplayer transport or money. */
@EventBusSubscriber(modid=ShiinaCasino.ID,value=Dist.CLIENT)
public final class ClientMenuSmokeTest {
    private record Case(String kind,int guiScale,String page) {}
    private static final boolean ENABLED=Boolean.getBoolean("shiinacasino.menuTest");
    private static final List<Case> cases=new ArrayList<>();
    private static final List<String> checked=new ArrayList<>();
    private static final Gson JSON=new Gson();
    private static boolean started,worldRequested,ready,finished,capturing,navigated;
    private static int index=-1,ticks,renderedFrames;
    private static int liveStage;
    private static long liveChips;
    private static BlockPos liveStation;
    private static final long start=System.nanoTime();

    @SubscribeEvent public static void rendered(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if(ENABLED && index>=0 && event.getScreen() instanceof CasinoScreen) renderedFrames++;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED || finished || capturing) return;
        var mc=Minecraft.getInstance();
        if((System.nanoTime()-start)/1_000_000_000L>180) throw new IllegalStateException("Menu client test timed out: "+checked);
        if(!started && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());
        }
        if(!started && mc.screen instanceof TitleScreen) {
            try { Files.deleteIfExists(mc.gameDirectory.toPath().resolve("client-menu-result.txt")); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            started=true;mc.options.languageCode="de_de";
            mc.getLanguageManager().setSelected("de_de");mc.reloadResourcePacks();
            return;
        }
        if(started && !worldRequested && mc.getOverlay()==null && mc.screen instanceof TitleScreen) {
            worldRequested=true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
            mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
            var settings=new LevelSettings("Casino menu regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("menus-"+System.currentTimeMillis(),settings,new WorldOptions(20261004,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null) return;
        if(liveStage>0) { live(mc);return; }
        if(!ready) {
            checkGlyphs(mc);
            for(String kind:List.of("blackjack","poker","roulette","wheel","exchange")) for(int scale=1;scale<=4;scale++) {
                for(String page:List.of("","help","history","settings")) cases.add(new Case(kind,scale,page));
                if(!kind.equals("exchange")) cases.add(new Case(kind,scale,"result"));
                if(kind.equals("roulette")) cases.add(new Case(kind,scale,"bets"));
            }
            ready=true;
        }
        if(index<0) { next(mc);return; }
        ++ticks;
        Case current=cases.get(index);
        if(!(mc.screen instanceof CasinoScreen screen)) throw new IllegalStateException("Fixture screen was unexpectedly closed");
        if(!hasControls(screen)) return;
        if(!navigated && !current.page.isEmpty() && !current.page.equals("result") && ticks>=2) {
            navigate(mc,screen,current);navigated=true;ticks=0;renderedFrames=0;return;
        }
        if(ticks<6 || renderedFrames<3) return;
        checkControls(screen,mc);
        if(current.page.isEmpty() && !current.kind.equals("exchange")) {
            try {
                var method=CasinoScreen.class.getDeclaredMethod("latestResult");method.setAccessible(true);
                if(method.invoke(screen)==null) throw new IllegalStateException("Missing personal result: "+current);
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        checked.add(current.toString());
        if(current.guiScale==2 && current.page.isEmpty() || current.guiScale==4 && (current.page.isEmpty() || current.page.equals("result") || current.kind.equals("poker") && current.page.equals("help"))) {
            capturing=true;
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"menu-"+current.kind+"-scale-"+current.guiScale+(current.page.isEmpty()?"":"-"+current.page)+".png",mc.getMainRenderTarget(),message->mc.execute(()->{capturing=false;next(mc);}));
        } else next(mc);
    }
    private static boolean hasControls(CasinoScreen screen) {
        try { var field=CasinoScreen.class.getDeclaredField("controls");field.setAccessible(true);return !((List<?>)field.get(screen)).isEmpty(); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void next(Minecraft mc) {
        if(++index>=cases.size()) {
            liveStage=1;renderedFrames=0;ticks=0;
            mc.player.containerMenu=mc.player.inventoryMenu;mc.setScreen(null);
            UUID id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(id);var level=server.overworld();
                liveStation=player.blockPosition().offset(1,0,0);
                level.setBlockAndUpdate(liveStation,CasinoContent.TABLE_BLOCKS.get("wheel").get().defaultBlockState());
                player.getInventory().clearContent();player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(CasinoContent.CHIP.get(),100));
                de.shiina.casino.server.CasinoServer.get(server).open(player,liveStation,"wheel");
            });
            return;
        }
        Case current=cases.get(index);ticks=0;renderedFrames=0;navigated=false;
        mc.options.guiScale().set(current.guiScale);mc.resizeDisplay();
        CasinoMenu menu=fixture(mc,current);
        mc.player.containerMenu=menu;
        CasinoScreen screen=new CasinoScreen(menu,mc.player.getInventory(),Component.translatable("casino.title."+current.kind));
        mc.setScreen(screen);
    }
    private static void live(Minecraft mc) {
        if(!(mc.player.containerMenu instanceof CasinoMenu menu) || !menu.kind.equals("wheel") || menu.view==null || !(mc.screen instanceof CasinoScreen)) return;
        if(liveStage==1) {
            if(!menu.view.phase().equals("betting") || menu.chips!=100) throw new IllegalStateException("Unexpected initial live wheel snapshot");
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new de.shiina.casino.network.CasinoNetwork.Action(
                    menu.containerId,menu.table,menu.view.round(),menu.view.revision(),++menu.sequence,"spin",20,0));
            liveStage=2;ticks=0;renderedFrames=0;return;
        }
        if(liveStage==2) {
            if(menu.view.phase().equals("spinning")) {
                if(menu.acknowledgedSequence!=1 || !menu.error.equals("casino.status.spin") || menu.chips!=80) throw new IllegalStateException("Live spin was not confirmed correctly");
                return;
            }
            if(!menu.view.phase().equals("result") || renderedFrames<3 || ++ticks<6) return;
            var history=menu.lastState.getAsJsonArray("history");
            if(history.size()!=1) throw new IllegalStateException("Live history must complete exactly once");
            var result=JSON.fromJson(history.get(0),PersonalRounds.Result.class);
            long payout=20L*menu.view.segments().get(menu.view.result());
            if(result.stake()!=20 || result.payout()!=payout || menu.chips!=80+payout) throw new IllegalStateException("Incorrect live payout/net or inventory delivery");
            liveChips=menu.chips;capturing=true;
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"live-wheel-result.png",mc.getMainRenderTarget(),message->mc.execute(()->{
                capturing=false;liveStage=3;ticks=0;renderedFrames=0;
                UUID id=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(id);
                    player.closeContainer();de.shiina.casino.server.CasinoServer.get(server).open(player,liveStation,"wheel");});
            }));
            return;
        }
        if(liveStage==3) {
            if(!menu.view.phase().equals("betting") || renderedFrames<3 || ++ticks<6) return;
            if(menu.lastState.getAsJsonArray("history").size()!=1 || menu.chips!=liveChips || menu.lastState.get("lastBet").getAsLong()!=20)
                throw new IllegalStateException("Reopening replayed payment or lost personal session data");
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("client-menu-result.txt"),"PASS: "+checked.size()+" rendered fixture layouts; GUI scales 1-4; German/English Minecraft glyph coverage; control bounds, overlaps and complete labels; local subview navigation. Also a LIVE integrated-server wheel round through actual payloads: accepted stake, inventory debit, result, payout, own history once, close/reopen without repeat payment. Two-client multiplayer not tested.\n"+String.join("\n",checked),StandardCharsets.UTF_8);
            } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            finished=true;System.out.println("CASINO CLIENT MENU TEST PASSED: "+checked.size()+" layouts AND LIVE SERVER ROUND");mc.stop();
        }
    }
    private static void navigate(Minecraft mc,CasinoScreen screen,Case current) {
        try {
            var scaleField=CasinoScreen.class.getDeclaredField("scale");scaleField.setAccessible(true);double scale=scaleField.getFloat(screen);
            var xField=CasinoScreen.class.getDeclaredField("originX");xField.setAccessible(true);double ox=xField.getInt(screen);
            var yField=CasinoScreen.class.getDeclaredField("originY");yField.setAccessible(true);double oy=yField.getInt(screen);
            double x=switch(current.page) { case "help"->376;case "history"->293;case "settings"->343;default->374; };
            double y=current.page.equals("bets")?202:12;
            if(!screen.mouseClicked(ox+x*scale,oy+y*scale,0)) throw new IllegalStateException("Local navigation click failed: "+current);
            var field=CasinoScreen.class.getDeclaredField("page");field.setAccessible(true);
            if(!current.page.equals(field.get(screen))) throw new IllegalStateException("Incorrect subview: "+current);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static CasinoMenu fixture(Minecraft mc,Case current) {
        CasinoMenu menu=new CasinoMenu(88,mc.player.getInventory(),UUID.randomUUID(),BlockPos.ZERO,current.kind);
        long now=System.nanoTime()/1_000_000L;
        List<Game.Seat> seats=new ArrayList<>();
        for(int i=0;i<6;i++) seats.add(new Game.Seat(i==0?mc.player.getUUID().toString():UUID.randomUUID().toString(),i==0?"Shiina":i==1?"Tom":"Spieler"+i,i,200,20,
                current.kind.equals("poker") && i>0?List.of(-1,-1):List.of(12,21),false,i==0 && !current.page.equals("result"),false,""));
        List<Game.Bet> bets=new ArrayList<>();
        for(int i=0;i<12;i++) bets.add(new Game.Bet(UUID.randomUUID().toString(),mc.player.getUUID().toString(),"number",i,20));
        String phase=current.page.equals("result")?"result":switch(current.kind) {case "blackjack"->"playing";case "poker"->"flop";default->"betting";};
        List<String> actions=current.page.equals("result")?List.of():switch(current.kind) {
            case "blackjack"->List.of("hit","stand","double");case "poker"->List.of("fold","check","raise","allin","leave");
            case "roulette"->List.of("cancel","leave");case "wheel"->List.of("spin");default->List.of();
        };
        menu.view=current.kind.equals("exchange")?null:new Game.View(current.kind,phase,7,3,0,now-2500,now+12000,now-500,
                List.of("blackjack","poker").contains(current.kind)?seats:List.of(),current.kind.equals("poker")?List.of(2,18,42):List.of(),current.kind.equals("blackjack")?List.of(12,-1):List.of(),120,-1,current.kind.equals("wheel")?11:1,Rules.WHEEL_SEGMENTS,current.kind.equals("roulette")?bets:List.of(),actions,0,4,"",2,10000,20,10000);
        JsonObject state=menu.view==null?new JsonObject():JSON.toJsonTree(menu.view).getAsJsonObject();
        var results=new ArrayList<PersonalRounds.Result>();
        for(int i=0;i<10;i++) results.add(new PersonalRounds.Result(i==0 && !current.kind.equals("exchange")?current.kind:List.of("blackjack","poker","roulette","wheel").get(i%4),UUID.randomUUID(),6-i,20,i%3==0?40:i%3==1?0:20));
        state.add("history",JSON.toJsonTree(results));state.addProperty("lastBet",20);
        state.add("lastWagers",JSON.toJsonTree(List.of(new PersonalRounds.Wager(Roulette.Kind.RED,0,20))));
        state.addProperty("smallBlind",1);state.addProperty("bigBlind",2);state.addProperty("seated",true);
        state.addProperty("diamonds",3);state.addProperty("maxBuy",3);state.addProperty("maxSell",2);state.addProperty("claimCapacity",64);
        menu.lastState=state;menu.chips=200;menu.balance=35;menu.rate=100;menu.error="casino.status.bet";menu.feedbackAt=now;
        return menu;
    }
    private static void checkControls(CasinoScreen screen,Minecraft mc) {
        try {
            var field=CasinoScreen.class.getDeclaredField("controls");field.setAccessible(true);
            List<?> controls=(List<?>)field.get(screen);List<int[]> bounds=new ArrayList<>();
            for(Object control:controls) {
                int[] rect=new int[4];String[] names={"x","y","width","height"};
                for(int i=0;i<4;i++) { var value=control.getClass().getDeclaredField(names[i]);value.setAccessible(true);rect[i]=value.getInt(control); }
                if(rect[0]<0 || rect[1]<0 || rect[0]+rect[2]>420 || rect[1]+rect[3]>326) throw new IllegalStateException("Control outside panel: "+control);
                var labelField=control.getClass().getDeclaredField("text");labelField.setAccessible(true);String label=(String)labelField.get(control);
                if(mc.font.width(label)>rect[2]-4) throw new IllegalStateException("Clipped control label: "+control+" fontWidth="+mc.font.width(label));
                for(int[] other:bounds) if(rect[0]<other[0]+other[2] && rect[0]+rect[2]>other[0] && rect[1]<other[1]+other[3] && rect[1]+rect[3]>other[1]) throw new IllegalStateException("Overlapping control: "+control);
                bounds.add(rect);
            }
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void checkGlyphs(Minecraft mc) {
        try {
            var method=net.minecraft.client.gui.Font.class.getDeclaredMethod("getFontSet",net.minecraft.resources.ResourceLocation.class);method.setAccessible(true);
            var font=(net.minecraft.client.gui.font.FontSet)method.invoke(mc.font,net.minecraft.network.chat.Style.DEFAULT_FONT);
            for(String locale:List.of("de_de","en_us")) {
                var resource=mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ShiinaCasino.ID,"lang/"+locale+".json")).orElseThrow();
                try(var reader=new java.io.InputStreamReader(resource.open(),StandardCharsets.UTF_8)) {
                    var language=JsonParser.parseReader(reader).getAsJsonObject();
                    for(var entry:language.entrySet()) entry.getValue().getAsString().codePoints().filter(cp->cp>32).forEach(cp->{
                        if(font.getGlyphInfo(cp,false)==net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING) throw new IllegalStateException("Missing Minecraft glyph U+"+Integer.toHexString(cp)+" in "+entry.getKey());
                    });
                }
            }
        } catch(ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private ClientMenuSmokeTest() {}
}
