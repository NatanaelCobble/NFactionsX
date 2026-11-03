package org.nfactionsx.core;

import org.nfactionsx.core.Model.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * Serialized state mutations, detached reads, and no PNX dependencies. Event-thread reads are synchronized.
 * All durable changes are committed before being exposed. Storage failure switches mutations to read-only.
 */
public final class FactionEngine {
    private State state;
    private Rules rules;
    private final StateStore store;
    private boolean readOnly;
    public FactionEngine(StateStore store, Rules rules) throws IOException {
        rules.validate(); this.store=store; this.rules=rules; state=store.load(); validate();
    }
    public synchronized Rules rules() { return rules; }
    public synchronized void rules(Rules next) { next.validate(); rules=next; }
    public synchronized boolean readOnly() { return readOnly; }
    public synchronized State snapshot() { return StateStore.copy(state); }
    public synchronized Faction faction(String player) {
        Profile p=state.players.get(player); Faction f=p==null?null:state.factions.get(p.faction);
        return f==null?null:StateStore.JSON.fromJson(StateStore.JSON.toJson(f),Faction.class);
    }
    public synchronized Faction byName(String name) {
        return state.factions.values().stream().filter(f->f.name.equalsIgnoreCase(name)||f.id.equals(name)).findFirst()
                .map(f->StateStore.JSON.fromJson(StateStore.JSON.toJson(f),Faction.class)).orElse(null);
    }
    public synchronized Profile profile(String id) {
        Profile p=state.players.get(id); return p==null?null:StateStore.JSON.fromJson(StateStore.JSON.toJson(p),Profile.class);
    }
    public synchronized String findPlayer(String name) {
        return state.players.values().stream().filter(p->p.name.equalsIgnoreCase(name)||p.uuid.equals(name))
                .map(p->p.uuid).findFirst().orElseThrow(()->new IllegalArgumentException("player-unknown"));
    }
    public synchronized String owner(String key) { return state.claims.get(key); }
    public synchronized String zone(String key) { return state.zones.get(key); }
    public synchronized boolean explosionAllowed(String key,Set<String> online) {
        if(state.zones.containsKey(key))return false;
        String id=state.claims.get(key);if(id==null)return true;
        if(!rules.raids||!rules.explosions||System.currentTimeMillis()<rules.graceUntil)return false;
        return rules.offlineRaids||state.factions.get(id).members.keySet().stream().anyMatch(online::contains);
    }
    public synchronized boolean pvp(String attacker,String victim,String attackerKey,String victimKey) {
        if("safe".equals(state.zones.get(attackerKey))||"safe".equals(state.zones.get(victimKey)))return false;
        Profile a=state.players.get(attacker),b=state.players.get(victim);
        String af=a==null?null:a.faction,bf=b==null?null:b.faction;
        if(af!=null&&af.equals(bf))return rules.friendlyFire;
        Relation relation=relation(af,bf);if(relation==Relation.ALLY)return rules.friendlyFire;
        return !rules.enemyOnlyPvp||relation==Relation.ENEMY||"war".equals(state.zones.get(victimKey));
    }
    public synchronized int lands(String faction) { return (int)state.claims.values().stream().filter(faction::equals).count(); }
    public synchronized double power(String faction) {
        Faction f=state.factions.get(faction); if(f==null)return 0;
        return f.members.keySet().stream().map(state.players::get).filter(Objects::nonNull).mapToDouble(p->p.power).sum();
    }
    public synchronized int capacity(String id) {
        Faction f=state.factions.get(id); if(f==null)return 0;
        return Math.max(0,Math.min(rules.maxClaims+f.upgrades.getOrDefault("claims",0)*rules.claimsPerUpgrade,(int)Math.floor(power(id))));
    }
    public synchronized boolean allowed(String player,String faction,Action action) {
        Faction f=state.factions.get(faction); if(f==null)return false;
        Role role=f.members.get(player); return role!=null&&role.ordinal()>=f.permissions.getOrDefault(action,rules.defaults.get(action)).ordinal();
    }
    public synchronized Relation relation(String a,String b) {
        if(a==null||b==null||a.equals(b))return Relation.NEUTRAL;
        Faction f=state.factions.get(a);return f==null?Relation.NEUTRAL:f.relations.getOrDefault(b,Relation.NEUTRAL);
    }
    public synchronized boolean canAccess(String player,String key,Action action,boolean bypass) {
        if(bypass)return true;
        if(state.zones.containsKey(key))return false;
        String owner=state.claims.get(key);if(owner==null)return true;
        if(allowed(player,owner,action))return true;
        Faction f=state.factions.get(owner); Profile p=state.players.get(player);
        return f.alliesInteract&&p!=null&&relation(owner,p.faction)==Relation.ALLY&&action==Action.INTERACT;
    }
    private void require(boolean condition,String message) { if(!condition)throw new IllegalArgumentException(message); }
    private Faction member(String player,Action action) {
        Profile p=state.players.get(player); require(p!=null&&p.faction!=null,"no-faction");
        Faction f=state.factions.get(p.faction);require(f!=null&&allowed(player,f.id,action),"rank-denied");return f;
    }
    private void unlocked(Faction f) { require(state.transfers.values().stream().noneMatch(t->t.faction.equals(f.id)&&t.status.equals("PENDING")),"bank-locked"); }
    private void audit(Faction f,String actor,String action) {
        Profile p=state.players.get(actor); f.audit.add(Instant.now()+" | "+(p==null?actor:p.name)+" | "+action);
        while(f.audit.size()>100)f.audit.remove(0);
    }
    private void change(Consumer<State> operation) {
        if(readOnly)throw new IllegalStateException("storage-readonly");
        State before=StateStore.copy(state);
        try { operation.accept(state); store.save(state); }
        catch(IOException error) { state=before;readOnly=true;throw new IllegalStateException("storage-readonly",error); }
        catch(RuntimeException error) { state=before;throw error; }
    }
    public synchronized void seen(String id,String name) {
        Profile existing=state.players.get(id);if(existing!=null&&existing.name.equals(name))return;
        change(s->{Profile p=s.players.computeIfAbsent(id,k->{Profile n=new Profile();n.uuid=id;n.power=rules.powerStart;return n;});p.name=name;});
    }
    public synchronized void create(String player,String name) {
        change(s->{Profile p=s.players.get(player);require(p!=null,"player-unknown");require(p.faction==null,"already-faction");
            require(name.matches("[A-Za-z0-9_]{3,16}"),"name-invalid");require(byName(name)==null,"name-used");require(s.factions.size()<rules.maxFactions,"server-limit");
            Faction f=new Faction();f.id=UUID.randomUUID().toString();f.name=name;f.created=System.currentTimeMillis();
            f.members.put(player,Role.LEADER);s.factions.put(f.id,f);p.faction=f.id;audit(f,player,"CREATE");});
    }
    public synchronized void invite(String actor,String target) {
        change(s->{Faction f=member(actor,Action.INVITE);require(s.players.containsKey(target),"player-unknown");
            f.invites.entrySet().removeIf(entry->entry.getValue()<System.currentTimeMillis());
            require(s.players.get(target).faction==null,"already-faction");require(f.invites.size()<200||f.invites.containsKey(target),"invite-limit");
            f.invites.put(target,System.currentTimeMillis()+rules.inviteSeconds*1000L);audit(f,actor,"INVITE "+target);});
    }
    public synchronized void join(String player,String faction) {
        change(s->{Faction f=s.factions.get(faction);Profile p=s.players.get(player);require(f!=null&&p!=null,"player-unknown");require(p.faction==null,"already-faction");
            require(f.open||f.invites.getOrDefault(player,0L)>=System.currentTimeMillis(),"invite-required");
            require(f.members.size()<rules.members+f.upgrades.getOrDefault("members",0)*rules.membersPerUpgrade,"members-full");
            f.members.put(player,Role.RECRUIT);f.invites.remove(player);p.faction=f.id;audit(f,player,"JOIN");});
    }
    public synchronized void leave(String player) {
        change(s->{Profile p=s.players.get(player);require(p!=null&&p.faction!=null,"no-faction");Faction f=s.factions.get(p.faction);
            require(f.members.get(player)!=Role.LEADER,"leader-transfer");f.members.remove(player);p.faction=null;audit(f,player,"LEAVE");});
    }
    public synchronized void kick(String actor,String target) {
        change(s->{Faction f=member(actor,Action.KICK);Role role=f.members.get(target);require(role!=null,"not-member");
            require(!actor.equals(target)&&role.ordinal()<f.members.get(actor).ordinal(),"rank-denied");f.members.remove(target);s.players.get(target).faction=null;audit(f,actor,"KICK "+target);});
    }
    public synchronized void rank(String actor,String target,Role next) {
        change(s->{Faction f=member(actor,Action.SETTINGS);require(f.members.get(actor)==Role.LEADER,"leader-only");require(f.members.containsKey(target),"not-member");
            require(!actor.equals(target),"rank-denied");if(next==Role.LEADER)f.members.put(actor,Role.OFFICER);f.members.put(target,next);audit(f,actor,"RANK "+target+" "+next);});
    }
    public synchronized void disband(String actor,boolean admin,String faction) {
        change(s->{Faction f=s.factions.get(faction);require(f!=null,"no-faction");require(admin||f.members.get(actor)==Role.LEADER,"leader-only");unlocked(f);
            require(f.bank==0,"empty-bank-first");for(String id:f.members.keySet())s.players.get(id).faction=null;
            s.claims.values().removeIf(f.id::equals);s.factions.remove(f.id);
            for(Faction other:s.factions.values()){other.relations.remove(f.id);other.allyRequests.remove(f.id);}});
    }
    public synchronized void settings(String actor,String description,String motd,boolean open,boolean allies) {
        configure(actor,member(actor,Action.SETTINGS).name,description,motd,open,allies);
    }
    public synchronized void configure(String actor,String name,String description,String motd,boolean open,boolean allies) {
        change(s->{Faction f=member(actor,Action.SETTINGS);require(description.length()<=120&&motd.length()<=200,"text-long");
            require(name.matches("[A-Za-z0-9_]{3,16}"),"name-invalid");Faction existing=byName(name);require(existing==null||existing.id.equals(f.id),"name-used");
            f.name=name;f.description=clean(description);f.motd=clean(motd);f.open=open;f.alliesInteract=allies;audit(f,actor,"SETTINGS");});
    }
    public static String clean(String text) { return text.replaceAll("[\\p{Cntrl}§&]",""); }
    public synchronized void rename(String actor,String name) {
        change(s->{Faction f=member(actor,Action.SETTINGS);require(name.matches("[A-Za-z0-9_]{3,16}"),"name-invalid");
            Faction existing=byName(name);require(existing==null||existing.id.equals(f.id),"name-used");audit(f,actor,"RENAME "+f.name+" -> "+name);f.name=name;});
    }
    public synchronized void permission(String actor,Action action,Role role) {
        change(s->{Faction f=member(actor,Action.SETTINGS);require(f.members.get(actor)==Role.LEADER,"leader-only");f.permissions.put(action,role);audit(f,actor,"PERM "+action+" "+role);});
    }
    public synchronized void diplomacy(String actor,String target,Relation wish) {
        change(s->{Faction a=member(actor,Action.DIPLOMACY);Faction b=s.factions.get(target);require(b!=null&&!b.id.equals(a.id),"target-invalid");
            if(wish==Relation.ALLY&& !a.allyRequests.contains(b.id)) {
                b.allyRequests.add(a.id);audit(a,actor,"ALLY REQUEST "+b.name);return;
            }
            a.relations.put(b.id,wish);b.relations.put(a.id,wish);a.allyRequests.remove(b.id);b.allyRequests.remove(a.id);
            audit(a,actor,"RELATION "+b.name+" "+wish);audit(b,actor,"RELATION "+a.name+" "+wish);});
    }
    public synchronized void claim(String actor,String world,int cx,int cz) {
        change(s->{Faction f=member(actor,Action.CLAIM);String key=Model.chunk(world,cx,cz);require(!rules.disabledWorlds.contains(world),"world-disabled");
            require(!s.zones.containsKey(key),"zone-protected");String previous=s.claims.get(key);require(!f.id.equals(previous),"already-claimed");
            require(lands(f.id)<capacity(f.id),"power-low");unlocked(f);require(f.bank>=rules.claimCost,"bank-low");
            if(previous!=null) {
                require(rules.raids&&System.currentTimeMillis()>=rules.graceUntil&&rules.overclaim&&relation(f.id,previous)==Relation.ENEMY&&lands(previous)>Math.max(0,power(previous)),"claim-owned");
                boolean edge=List.of(Model.chunk(world,cx+1,cz),Model.chunk(world,cx-1,cz),Model.chunk(world,cx,cz+1),Model.chunk(world,cx,cz-1))
                        .stream().anyMatch(k->!previous.equals(s.claims.get(k)));require(edge,"claim-edge");
            }
            if(rules.adjacency&&s.claims.entrySet().stream().anyMatch(e->e.getValue().equals(f.id)&&e.getKey().startsWith(world.length()+":"+world+":"))) {
                require(List.of(Model.chunk(world,cx+1,cz),Model.chunk(world,cx-1,cz),Model.chunk(world,cx,cz+1),Model.chunk(world,cx,cz-1))
                        .stream().anyMatch(k->f.id.equals(s.claims.get(k))),"claim-adjacent");
            }
            f.bank-=rules.claimCost;s.claims.put(key,f.id);audit(f,actor,"CLAIM "+world+" "+cx+" "+cz);
            if(previous!=null){Faction old=s.factions.get(previous);clearLocations(old,key);audit(old,actor,"OVERCLAIM "+key);}});
    }
    public synchronized void unclaim(String actor,String key,boolean admin) {
        change(s->{String owner=s.claims.get(key);require(owner!=null,"not-claimed");Faction f=s.factions.get(owner);
            require(admin||allowed(actor,owner,Action.CLAIM),"rank-denied");s.claims.remove(key);clearLocations(f,key);audit(f,actor,"UNCLAIM "+key);});
    }
    private void clearLocations(Faction f,String key) {
        if(f.home!=null&&Model.block(f.home.world,(int)Math.floor(f.home.x),(int)Math.floor(f.home.z)).equals(key))f.home=null;
        f.warps.values().removeIf(h->Model.block(h.world,(int)Math.floor(h.x),(int)Math.floor(h.z)).equals(key));
    }
    public synchronized void zone(String key,String type) {
        change(s->{require(type.equals("safe")||type.equals("war")||type.equals("none"),"target-invalid");
            String previous=s.claims.remove(key);if(previous!=null)clearLocations(s.factions.get(previous),key);
            if(type.equals("none"))s.zones.remove(key);else s.zones.put(key,type);});
    }
    public synchronized void home(String actor,String name,Home location) {
        change(s->{Faction f=member(actor,Action.CLAIM);
            require(f.id.equals(s.claims.get(Model.block(location.world,(int)Math.floor(location.x),(int)Math.floor(location.z)))),"home-own-land");
            if(name.equals("home"))f.home=location;else {
                require(name.matches("[A-Za-z0-9_-]{1,16}"),"warp-invalid");require(f.warps.containsKey(name)||f.warps.size()<rules.warps+f.upgrades.getOrDefault("warps",0)*rules.warpsPerUpgrade,"warps-full");f.warps.put(name,location);
            }audit(f,actor,"SETWARP "+name);});
    }
    public synchronized void deleteWarp(String actor,String name) {
        change(s->{Faction f=member(actor,Action.CLAIM);if(name.equals("home"))f.home=null;else f.warps.remove(name);audit(f,actor,"DELWARP "+name);});
    }
    public synchronized void death(String player) {
        change(s->{Profile p=s.players.get(player);if(p==null)return;p.power=Math.max(rules.powerMin,p.power-rules.deathLoss);p.lastDeath=System.currentTimeMillis();});
    }
    public synchronized void regenerate(Set<String> online) {
        if(online.stream().noneMatch(id->state.players.containsKey(id)&&state.players.get(id).power<rules.powerMax))return;
        change(s->{for(String id:online){Profile p=s.players.get(id);if(p!=null)p.power=Math.min(rules.powerMax,p.power+rules.regenPerMinute);}});
    }
    public synchronized void adminPower(String player,double value) {
        require(Double.isFinite(value)&&value>=rules.powerMin&&value<=rules.powerMax,"power-invalid");
        change(s->{require(s.players.containsKey(player),"player-unknown");s.players.get(player).power=value;});
    }
    public synchronized void upgrade(String actor,String upgrade) {
        change(s->{Faction f=member(actor,Action.UPGRADE);require(Set.of("members","claims","warps").contains(upgrade),"target-invalid");unlocked(f);
            int level=f.upgrades.getOrDefault(upgrade,0);require(level<rules.maxUpgrade,"upgrade-max");long cost=Math.multiplyExact(rules.upgradeCost,level+1L);
            require(f.bank>=cost,"bank-low");f.bank-=cost;f.upgrades.put(upgrade,level+1);audit(f,actor,"UPGRADE "+upgrade+" "+(level+1));});
    }
    public synchronized String beginTransfer(String actor,String account,String provider,long cents,boolean withdraw,double before) {
        require(cents>0&&cents<=rules.bankLimit&&Double.isFinite(before)&&before>=0,"amount-invalid");
        String id=UUID.randomUUID().toString();
        change(s->{Profile p=s.players.get(actor);require(p!=null&&p.faction!=null,"no-faction");Faction f=s.factions.get(p.faction);
            require(!withdraw||allowed(actor,f.id,Action.WITHDRAW),"rank-denied");unlocked(f);
            require(withdraw?f.bank>=cents:f.bank<=rules.bankLimit-cents,"bank-low");
            Transfer t=new Transfer();t.id=id;t.faction=f.id;t.player=actor;t.account=account;t.provider=provider;t.cents=cents;t.before=before;t.time=System.currentTimeMillis();t.direction=withdraw?"WITHDRAW":"DEPOSIT";
            if(withdraw)f.bank-=cents;s.transfers.put(id,t);audit(f,actor,"TRANSFER PREPARE "+id+" "+t.direction+" "+cents);
            // Keep pending records forever; prune only resolved records.
            if(s.transfers.size()>500)s.transfers.values().removeIf(x->!x.status.equals("PENDING")&&x.time<System.currentTimeMillis()-7L*86400000);
        });return id;
    }
    public synchronized void resolveTransfer(String id,boolean externalSuccess,String actor) {
        change(s->{Transfer t=s.transfers.get(id);require(t!=null&&t.status.equals("PENDING"),"transfer-invalid");Faction f=s.factions.get(t.faction);require(f!=null,"no-faction");
            if(externalSuccess&&t.direction.equals("DEPOSIT"))f.bank=Math.addExact(f.bank,t.cents);
            if(!externalSuccess&&t.direction.equals("WITHDRAW"))f.bank=Math.addExact(f.bank,t.cents);
            t.status=externalSuccess?"CONFIRMED":"CANCELLED";audit(f,actor,"TRANSFER "+t.status+" "+id);});
    }
    public static long cents(String text) {
        if(text==null||text.length()>20||!text.matches("[0-9]+([.,][0-9]{1,2})?"))throw new IllegalArgumentException("amount-invalid");
        try{return new BigDecimal(text.replace(',','.')).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();}
        catch(ArithmeticException error){throw new IllegalArgumentException("amount-invalid");}
    }
    public static String money(long cents) { return BigDecimal.valueOf(cents,2).toPlainString(); }
    private void validate() throws IOException {
        try {
            Set<String> members=new HashSet<>();Set<String> names=new HashSet<>();
            for(var entry:state.players.entrySet()){Profile p=entry.getValue();UUID.fromString(p.uuid);require(entry.getKey().equals(p.uuid)&&p.name!=null&&Double.isFinite(p.power),"profile-corrupt");}
            for(var entry:state.factions.entrySet()){
                Faction f=entry.getValue();require(entry.getKey().equals(f.id)&&f.name.matches("[A-Za-z0-9_]{3,16}")&&names.add(f.name.toLowerCase(Locale.ROOT)),"faction-corrupt");
                require(f.bank>=0&&f.bank<=100_000_000_000_000L&&f.members.values().stream().filter(r->r==Role.LEADER).count()==1,"faction-corrupt");
                for(String id:f.members.keySet()){Profile p=state.players.get(id);require(p!=null&&f.id.equals(p.faction)&&members.add(id),"membership-corrupt");}
            }
            for(Profile p:state.players.values())require(p.faction==null||members.contains(p.uuid),"membership-corrupt");
            for(String owner:state.claims.values())require(state.factions.containsKey(owner),"claim-corrupt");
            for(String key:state.claims.keySet())require(Model.validChunk(key)&&!state.zones.containsKey(key),"claim-corrupt");
            for(var zone:state.zones.entrySet())require(Model.validChunk(zone.getKey())&&Set.of("safe","war").contains(zone.getValue()),"zone-corrupt");
            for(var entry:state.transfers.entrySet()){Transfer t=entry.getValue();require(entry.getKey().equals(t.id)&&t.cents>0&&t.cents<=100_000_000_000_000L
                    &&Set.of("DEPOSIT","WITHDRAW").contains(t.direction)&&Set.of("PENDING","CONFIRMED","CANCELLED").contains(t.status)
                    &&Double.isFinite(t.before)&&t.before>=0&&(!t.status.equals("PENDING")||state.factions.containsKey(t.faction)),"transfer-corrupt");}
        }catch(RuntimeException error){throw new IOException("Donnees factions incoherentes : demarrage refuse",error);}
    }
}
