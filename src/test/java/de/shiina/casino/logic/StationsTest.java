package de.shiina.casino.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StationsTest {
    @Test void occupancyIsExclusiveForBothPlayerAndChair() {
        SeatRegistry seats=new SeatRegistry();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var first=new SeatRegistry.Key("overworld",12,0);var second=new SeatRegistry.Key("overworld",12,1);
        assertTrue(seats.claim(a,first));assertTrue(seats.claim(a,first));
        assertFalse(seats.claim(b,first));assertFalse(seats.claim(a,second));assertEquals(1,seats.size());
        assertEquals(first,seats.release(a));assertNull(seats.release(a));assertTrue(seats.claim(b,first));
    }
    @Test void stationsAndDimensionsHaveIndependentSeatsAndRestartDropsOccupancy() {
        SeatRegistry seats=new SeatRegistry();
        for(int i=0;i<6;i++) assertTrue(seats.claim(UUID.randomUUID(),new SeatRegistry.Key("overworld",12,i)));
        assertTrue(seats.claim(UUID.randomUUID(),new SeatRegistry.Key("overworld",13,0)));
        assertTrue(seats.claim(UUID.randomUUID(),new SeatRegistry.Key("nether",12,0)));
        assertEquals(6,seats.at("overworld",12).size());assertEquals(8,seats.size());
        seats.at("overworld",12).forEach(seats::release);assertEquals(2,seats.size());
        seats.clear();assertEquals(0,seats.size());
        assertThrows(IllegalArgumentException.class,()->new SeatRegistry.Key("overworld",12,6));
    }
    @Test void layoutsHaveExactFootprintsSixChairsAndUnambiguousRotatedOwnership() {
        for(String kind:List.of("blackjack","poker","roulette","wheel")) {
            var layout=StationLayout.parts(kind);
            assertEquals(!kind.equals("roulette") && !kind.equals("wheel")?11:kind.equals("roulette")?14:8,layout.size());
            assertEquals(kind.equals("wheel")?0:6,layout.stream().filter(p->p.seat()>=0).count());
            assertEquals(!kind.equals("roulette") && !kind.equals("wheel")?5:8,layout.stream().filter(p->p.seat()<0).count());
            for(int rotation=0;rotation<4;rotation++) {
                var positions=new HashSet<String>();positions.add("0:0:0");
                for(var part:layout) {
                    var p=part.rotated(rotation);
                    assertTrue(positions.add(p.x()+":"+p.y()+":"+p.z()));
                    assertEquals(part,p.rotated(4-rotation));
                }
            }
        }
    }
    @Test void blackjackHandsUsePhysicalSeatsAndCannotDebitAReservedSeat() {
        TestBank bank=new TestBank();UUID a=bank.player(100),b=bank.player(100),c=bank.player(100);
        bank.seats.put(a,5);bank.seats.put(b,1);bank.seats.put(c,5);
        BlackjackGame game=new BlackjackGame(bank,Rules.defaults(),new java.security.SecureRandom());
        game.guarded(a,game.round(),game.revision(),"bet",20,0,0);
        game.guarded(b,game.round(),game.revision(),"bet",20,0,0);
        assertEquals(5,game.view(a,0).seats().stream().filter(s->s.id().equals(a.toString())).findFirst().orElseThrow().seat());
        assertThrows(IllegalArgumentException.class,()->game.guarded(c,game.round(),game.revision(),"bet",20,0,0));
        assertEquals(100,bank.inventory.get(c));assertEquals(2,game.players().size());
        game.depart(a,1000);game.depart(a,1001);assertEquals(20,bank.ledger.balance(a));
        game.guarded(c,game.round(),game.revision(),"bet",20,0,1002);assertEquals(2,game.players().size());
    }
    @Test void pokerBuyInsUsePhysicalSeatsAndReservedSeatCannotDebit() {
        TestBank bank=new TestBank();UUID a=bank.player(1000),b=bank.player(1000),c=bank.player(1000);
        bank.seats.put(a,5);bank.seats.put(b,1);bank.seats.put(c,5);
        PokerGame game=new PokerGame(bank,Rules.defaults(),new java.security.SecureRandom());
        game.guarded(a,game.round(),game.revision(),"join",200,0,0);
        game.guarded(b,game.round(),game.revision(),"join",200,0,0);
        assertEquals(5,game.view(a,0).seats().stream().filter(s->s.id().equals(a.toString())).findFirst().orElseThrow().seat());
        assertThrows(IllegalArgumentException.class,()->game.guarded(c,game.round(),game.revision(),"join",200,0,0));
        assertEquals(1000,bank.inventory.get(c));
    }
    @Test void rouletteSeatDepartureKeepsWagersAndTheSharedRound() {
        TestBank bank=new TestBank();UUID a=bank.player(100),b=bank.player(100);
        RouletteGame game=new RouletteGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(1));
        game.guarded(a,game.round(),game.revision(),"roulette_red",10,0,0);
        game.guarded(b,game.round(),game.revision(),"roulette_number",10,1,0);
        var before=game.view(a,0);game.depart(a,1000);
        assertEquals(before,game.view(a,1000));game.timeout(20000);game.timeout(25000);game.close();
        assertEquals(20,bank.ledger.balance(a));assertEquals(360,bank.ledger.balance(b));
    }
    @Test void animationStopsEverySegmentExactlyAtThePointerAndCannotSettleMoney() {
        for(int count:List.of(12,37)) for(int segment=0;segment<count;segment++) {
            double sector=Math.PI*2/count;
            double finalAngle=WheelAnimation.angle(segment,count,100,100);
            assertEquals(Math.PI/2,finalAngle+(segment+.5)*sector,1e-12);
            assertEquals(finalAngle,WheelAnimation.angle(segment,count,100000,100));
        }
        assertFalse(WheelAnimation.newer(8,9));assertFalse(WheelAnimation.newer(9,9));assertTrue(WheelAnimation.newer(10,9));
        TestBank bank=new TestBank();UUID player=bank.player(100);
        WheelGame game=new WheelGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(11));
        game.guarded(player,game.round(),game.revision(),"spin",10,0,0);
        for(int i=0;i<100;i++) WheelAnimation.angle(0,12,i*1000,80);
        assertEquals(0,bank.ledger.balance(player));assertEquals(11,game.view(UUID.randomUUID(),0).result());
        game.depart(player,1000);game.timeout(4000);game.timeout(5000);game.close();game.close();assertEquals(40,bank.ledger.balance(player));
        assertEquals(List.of(0,0,0,0,0,0,1,1,1,2,2,4),Rules.WHEEL_SEGMENTS);
    }
    @Test void duplicateAndOldActionsCannotDebitTwiceOrRestartAnOldRound() {
        ActionGate gate=new ActionGate();assertTrue(gate.accept(1,10));assertFalse(gate.accept(1,11));assertFalse(gate.accept(0,12));assertTrue(gate.accept(2,12));
        TestBank bank=new TestBank();UUID player=bank.player(100);
        WheelGame game=new WheelGame(bank,Rules.defaults(),new RouletteWheelTest.FixedRandom(11));
        long round=game.round(),revision=game.revision();game.guarded(player,round,revision,"spin",10,0,0);
        game.timeout(4000);
        assertThrows(IllegalArgumentException.class,()->game.guarded(player,round,revision,"spin",10,0,4001));
        assertEquals(90,bank.inventory.get(player));assertEquals(40,bank.ledger.balance(player));
    }
}
