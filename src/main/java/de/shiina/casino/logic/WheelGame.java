package de.shiina.casino.logic;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One physical wheel has one shared spin. Only the server owns its fixed award. */
public final class WheelGame extends Game {
    private record Spin(UUID owner, UUID transaction, long bet, int segment, long payout,
                        long started, List<Integer> segments, boolean paid) {}
    private Spin spin;
    private final SecureRandom random;
    public WheelGame(Bank bank, Rules rules, SecureRandom random) { super(bank,rules); this.random=random; }
    @Override public String kind() { return "wheel"; }
    @Override public boolean contains(UUID player) { return funded() && spin.owner.equals(player); }
    @Override public boolean funded() { return spin!=null && !spin.paid; }
    @Override public List<UUID> players() { return funded()?List.of(spin.owner):List.of(); }
    @Override public void nextRules(Rules next) { if(!funded()) rules=next; }
    @Override public void action(UUID player,String action,long amount,int option,long now) {
        require(rules.fortune(), "casino.error.disabled");
        require(action.equals("spin"));
        require(!funded(), "casino.error.unavailable");
        rules.bet(amount,false);
        List<Integer> segments=Rules.WHEEL_SEGMENTS;
        int result=random.nextInt(segments.size());
        long payout=Money.multiply(amount,segments.get(result));
        debit(player,amount);
        bank.rememberBet(player,kind(),amount);
        UUID transaction=UUID.randomUUID();
        bank.hold(transaction,kind()+":segment="+result,round+1,Map.of(player,payout));
        spin=new Spin(player,transaction,amount,result,payout,now,segments,false);
        phase="spinning"; startedAt=now; deadline=now+4000; round++;
    }
    @Override public void timeout(long now) {
        if(deadline==0 || now<deadline) return;
        if(funded()) {
            bank.settle(spin.transaction);
            spin=new Spin(spin.owner,spin.transaction,spin.bet,spin.segment,spin.payout,spin.started,spin.segments,true);
            phase="result"; deadline=now+10000;
            recordResult();
        } else { spin=null; phase="betting"; deadline=0; }
        changedAt=now; revision++;
    }
    @Override public void depart(UUID player,long now) {}
    private void recordResult() { bank.completed(spin.owner,new PersonalRounds.Result(kind(),spin.transaction,round,spin.bet,spin.payout)); }
    @Override public void close() {
        if(funded()) { bank.settle(spin.transaction); recordResult(); }
        spin=null; deadline=0;
    }
    @Override public View view(UUID viewer,long now) {
        return new View(kind(),phase,round,revision,0,spin==null?0:spin.started,deadline,changedAt,
                List.of(),List.of(),List.of(),spin==null?0:spin.bet,-1,spin==null?-1:spin.segment,
                spin==null?Rules.WHEEL_SEGMENTS:spin.segments,List.of(),funded() || !rules.fortune()?List.of():List.of("spin"),
                0,0,spin==null?"":Long.toString(spin.payout),rules.minBet(),rules.maxBet(),rules.buyInMin(),rules.buyInMax());
    }
}
