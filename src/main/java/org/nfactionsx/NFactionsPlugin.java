package org.nfactionsx;

import org.nfactionsx.api.FactionsService;
import org.nfactionsx.core.*;
import org.nfactionsx.core.Model.*;
import org.nfactionsx.integration.Integrations;
import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.plugin.PluginEnableEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent.TeleportCause;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.Position;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.plugin.service.ServicePriority;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Main-loop commands and callbacks; territory policy is independently synchronized for level-thread events. */
public final class NFactionsPlugin extends PluginBase implements Listener,FactionsService {
    private FactionEngine engine;
    private volatile Settings settings;
    private Integrations integrations;
    private FactionMenus menus;
    private volatile Set<String> online=Set.of();
    private final Map<String,Long> combat=new ConcurrentHashMap<>(),cooldowns=new ConcurrentHashMap<>();
    private final Map<String,String> chatModes=new ConcurrentHashMap<>();
    private final Map<String,PendingTeleport> teleports=new ConcurrentHashMap<>();
    private final Set<String> bypass=ConcurrentHashMap.newKeySet(),spies=ConcurrentHashMap.newKeySet();
    private record PendingTeleport(String token,String faction,Home destination) { }
    public FactionEngine engine(){return engine;}
    public Settings settings(){return settings;}
    public Integrations integrations(){return integrations;}
    public FactionMenus menus(){return menus;}
    public Set<String> online(){return online;}
    @Override public void onEnable(){
        saveResource("config.yml");saveResource("messages.yml");saveResource("menus.yml");
        try {settings=new Settings(getDataFolder());engine=new FactionEngine(new StateStore(getDataFolder().toPath().resolve("state.json")),settings.rules);}
        catch(Exception error){getLogger().error("NFactionsX: startup refused; server stopping to preserve territory protection",error);getServer().shutdown();throw new IllegalStateException("NFactionsX startup failed",error);}
        integrations=new Integrations(this);menus=new FactionMenus(this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this),this);
        if(!getServer().getServiceManager().register(FactionsService.class,this,this,ServicePriority.NORMAL))throw new IllegalStateException("FactionsService already registered");
        for(Player player:getServer().getOnlinePlayers().values())seen(player);updateOnline();integrations.placeholders();
        getServer().getScheduler().scheduleRepeatingTask(this,()->safe(null,()->{updateOnline();engine.regenerate(online);combat.entrySet().removeIf(e->e.getValue()<System.currentTimeMillis());}),1200);
        getLogger().info("NFactionsX 1.0.0: competitive factions enabled. /f admin status");
    }
    @Override public void onDisable(){teleports.clear();getServer().getServiceManager().cancel(this);}
    @EventHandler public void enabled(PluginEnableEvent event){if(event.getPlugin().getName().equals("PlaceholderAPI"))integrations.placeholders();}
    public void main(Runnable task){if(getServer().isPrimaryThread())task.run();else getServer().getScheduler().scheduleTask(this,task);}
    public void updateOnline(){Set<String> ids=new HashSet<>();for(Player p:getServer().getOnlinePlayers().values())if(p.isOnline())ids.add(p.getUniqueId().toString());online=Set.copyOf(ids);}
    public void seen(Player player){safe(player,()->engine.seen(player.getUniqueId().toString(),player.getName()));}
    public void clearSession(String id){chatModes.remove(id);bypass.remove(id);spies.remove(id);cooldowns.remove(id);}
    public boolean bypass(Player p){return bypass.contains(p.getUniqueId().toString())&&p.hasPermission("nfactions.bypass");}
    public boolean spy(String id){return spies.contains(id);}
    public boolean inCombat(String id){return combat.getOrDefault(id,0L)>System.currentTimeMillis();}
    public void tag(Player p){combat.put(p.getUniqueId().toString(),System.currentTimeMillis()+settings.combatSeconds*1000L);}
    public String chatMode(String id){return chatModes.getOrDefault(id,"public");}
    public static String key(Position pos){return Model.block(pos.getLevel().getName(),pos.getFloorX(),pos.getFloorZ());}
    public String territory(Position pos){String key=key(pos),zone=engine.zone(key);if(zone!=null)return settings.text("zone."+zone);String id=engine.owner(key);Faction f=id==null?null:engine.byName(id);return f==null?settings.text("wilderness"):f.name;}
    public void tell(CommandSender sender,String key,Object... pairs){if(sender!=null)sender.sendMessage(settings.text("prefix")+settings.text(key,pairs));}
    public void safe(CommandSender sender,Runnable action){
        try{action.run();}
        catch(IllegalArgumentException error){String key=error.getMessage();tell(sender,key!=null&&key.matches("[a-z-]+")?key:"usage");}
        catch(Exception error){getLogger().error("NFactionsX action failed",error);tell(sender,engine!=null&&engine.readOnly()?"storage-readonly":"internal-error");}
    }
    public void act(Player player,Runnable action){
        main(()->safe(player,()->{if(!player.isOnline()||!player.hasPermission("nfactions.use"))throw new IllegalArgumentException("permission-denied");action.run();}));
    }
    public void cancelTeleport(String id,boolean notify){if(teleports.remove(id)!=null&&notify)getServer().getPlayer(UUID.fromString(id)).ifPresent(p->tell(p,"teleport-cancelled"));}
    public void teleport(Player player,String name){
        if(!settings.feature("teleport"))throw new IllegalArgumentException("feature-disabled");
        String id=player.getUniqueId().toString();if(inCombat(id))throw new IllegalArgumentException("combat-blocked");
        Faction f=engine.faction(id);if(f==null)throw new IllegalArgumentException("no-faction");
        if(!engine.allowed(id,f.id,name.equals("home")?Action.HOME:Action.WARP))throw new IllegalArgumentException("rank-denied");
        Home home=name.equals("home")?f.home:f.warps.get(name);if(home==null)throw new IllegalArgumentException("home-missing");
        PendingTeleport pending=new PendingTeleport(UUID.randomUUID().toString(),f.id,home);teleports.put(id,pending);tell(player,"teleport-wait","seconds",settings.teleportSeconds);
        getServer().getScheduler().scheduleDelayedTask(this,()->safe(player,()->{
            if(!teleports.remove(id,pending)||!player.isOnline())return;
            Faction current=engine.faction(id);String chunk=Model.block(home.world,(int)Math.floor(home.x),(int)Math.floor(home.z));
            if(inCombat(id)||current==null||!current.id.equals(pending.faction)||!current.id.equals(engine.owner(chunk)))throw new IllegalArgumentException("teleport-cancelled");
            if(!engine.allowed(id,current.id,name.equals("home")?Action.HOME:Action.WARP))throw new IllegalArgumentException("rank-denied");
            var level=getServer().getLevelByName(home.world);if(level==null)throw new IllegalArgumentException("world-unloaded");
            int x=(int)Math.floor(home.x),y=(int)Math.floor(home.y),z=(int)Math.floor(home.z);
            if(y<=level.getMinHeight()||y>=level.getMaxHeight()-1||!level.loadChunk(Math.floorDiv(x,16),Math.floorDiv(z,16)))throw new IllegalArgumentException("teleport-unsafe");
            if(!level.getBlock(x,y,z).isAir()||!level.getBlock(x,y+1,z).isAir()||!level.getBlock(x,y-1,z).isSolid())throw new IllegalArgumentException("teleport-unsafe");
            if(player.teleport(new Location(home.x,home.y,home.z,home.yaw,home.pitch,level),TeleportCause.PLUGIN))tell(player,"teleport-done");else tell(player,"teleport-cancelled");
        }),Math.max(1,settings.teleportSeconds*20));
    }
    public void setHome(Player player,String name){if(!settings.feature("teleport"))throw new IllegalArgumentException("feature-disabled");Location l=player.getLocation();engine.home(player.getUniqueId().toString(),name,new Home(l.getLevel().getName(),l.x,l.y,l.z,l.yaw,l.pitch));tell(player,"saved");}
    public void claim(Player player){
        String world=player.getLevel().getName();int cx=Math.floorDiv(player.getFloorX(),16),cz=Math.floorDiv(player.getFloorZ(),16);
        if(inCombat(player.getUniqueId().toString()))throw new IllegalArgumentException("combat-blocked");
        if(integrations.regionOverlap(world,cx,cz))throw new IllegalArgumentException("region-overlap");
        String previous=engine.owner(Model.chunk(world,cx,cz));
        if(previous!=null&&!settings.rules.offlineRaids){Faction target=engine.byName(previous);if(target!=null&&target.members.keySet().stream().noneMatch(online::contains))throw new IllegalArgumentException("offline-protected");}
        engine.claim(player.getUniqueId().toString(),world,cx,cz);tell(player,"claimed","x",cx,"z",cz);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        safe(sender,()->{
            if(args.length>0&&args[0].equalsIgnoreCase("admin")){admin(sender,Arrays.copyOfRange(args,1,args.length));return;}
            if(!(sender instanceof Player player)){tell(sender,"console-help");return;}
            if(!player.hasPermission("nfactions.use"))throw new IllegalArgumentException("permission-denied");
            String id=player.getUniqueId().toString();long now=System.currentTimeMillis();if(now-cooldowns.getOrDefault(id,0L)<settings.commandCooldownMs)throw new IllegalArgumentException("cooldown");cooldowns.put(id,now);
            seen(player);execute(player,args);
        });return true;
    }
    private String arg(String[] args,int index){if(index>=args.length)throw new IllegalArgumentException("usage");return args[index];}
    private <E extends Enum<E>> E enumeration(Class<E> type,String value){try{return Enum.valueOf(type,value.toUpperCase(Locale.ROOT));}catch(Exception error){throw new IllegalArgumentException("usage");}}
    public void execute(Player p,String[] a){
        String id=p.getUniqueId().toString(),sub=a.length==0?"menu":a[0].toLowerCase(Locale.ROOT);
        String feature=switch(sub){case "bank"->"bank";case "ally","enemy","neutral","relations"->"diplomacy";case "upgrade","upgrades"->"upgrades";case "home","sethome","warp","setwarp","delwarp"->"teleport";case "chat"->"chat";default->"core";};
        if(!settings.feature(feature))throw new IllegalArgumentException("feature-disabled");
        switch(sub){
            case "menu"->menus.dashboard(p);
            case "help"->p.sendMessage(settings.text("help"));
            case "create"->{engine.create(id,arg(a,1));tell(p,"created","name",a[1]);menus.dashboard(p);}
            case "invite"->{engine.invite(id,engine.findPlayer(arg(a,1)));tell(p,"invited");Player target=getServer().getPlayerExact(a[1]);if(target!=null)tell(target,"invite-received","faction",engine.faction(id).name);}
            case "join"->{Faction f=engine.byName(arg(a,1));if(f==null)throw new IllegalArgumentException("faction-unknown");engine.join(id,f.id);tell(p,"joined");}
            case "leave"->menus.confirm(p,"confirm-leave",()->{engine.leave(id);tell(p,"left");});
            case "kick"->{engine.kick(id,engine.findPlayer(arg(a,1)));tell(p,"saved");}
            case "rank"->{engine.rank(id,engine.findPlayer(arg(a,1)),enumeration(Role.class,arg(a,2)));tell(p,"saved");}
            case "disband"->{Faction f=engine.faction(id);if(f==null)throw new IllegalArgumentException("no-faction");menus.confirm(p,"confirm-disband",()->{engine.disband(id,false,f.id);tell(p,"disbanded");});}
            case "claim"->claim(p);
            case "unclaim"->{engine.unclaim(id,key(p),false);tell(p,"unclaimed");}
            case "map"->p.sendMessage(map(p));
            case "power"->{Profile profile=engine.profile(id);tell(p,"power","power",String.format(Locale.ROOT,"%.1f",profile.power),"max",settings.rules.powerMax);}
            case "home"->teleport(p,"home");
            case "sethome"->setHome(p,"home");
            case "warp"->teleport(p,arg(a,1));
            case "setwarp"->setHome(p,arg(a,1));
            case "delwarp"->{engine.deleteWarp(id,arg(a,1));tell(p,"saved");}
            case "list","top"->menus.directory(p,0);
            case "show","info"->{Faction f=a.length>1?engine.byName(a[1]):engine.faction(id);if(f==null)throw new IllegalArgumentException("no-faction");menus.info(p,f.id);}
            case "members"->menus.members(p,0);
            case "settings"->menus.settings(p);
            case "rename","name"->{engine.rename(id,arg(a,1));tell(p,"saved");}
            case "perms"->menus.permissions(p);
            case "bank"->{if(a.length<3)menus.bank(p);else{String direction=arg(a,1);if(!direction.equals("deposit")&&!direction.equals("withdraw"))throw new IllegalArgumentException("usage");integrations.transfer(p,arg(a,2),direction.equals("withdraw"));tell(p,"bank-done");}}
            case "upgrade"->{engine.upgrade(id,arg(a,1));tell(p,"saved");}
            case "upgrades"->menus.upgrades(p);
            case "ally","enemy","neutral"->{Faction f=engine.byName(arg(a,1));if(f==null)throw new IllegalArgumentException("faction-unknown");engine.diplomacy(id,f.id,sub.equals("ally")?Relation.ALLY:sub.equals("enemy")?Relation.ENEMY:Relation.NEUTRAL);tell(p,"relation-updated");}
            case "relations"->menus.relations(p,0);
            case "chat"->{String mode=a.length>1?a[1]:chatMode(id).equals("public")?"faction":"public";if(!Set.of("public","faction","ally").contains(mode))throw new IllegalArgumentException("usage");if(!mode.equals("public")&&engine.faction(id)==null)throw new IllegalArgumentException("no-faction");chatModes.put(id,mode);tell(p,"chat-mode","mode",settings.text("mode."+mode));}
            case "audit"->menus.audit(p,0);
            default->throw new IllegalArgumentException("usage");
        }
    }
    public String map(Player p){
        StringBuilder out=new StringBuilder(settings.text("map-title"));Map<String,Character> symbols=new LinkedHashMap<>();int cx=Math.floorDiv(p.getFloorX(),16),cz=Math.floorDiv(p.getFloorZ(),16);String world=p.getLevel().getName();Faction own=engine.faction(p.getUniqueId().toString());
        for(int dz=-4;dz<=4;dz++){out.append('\n');for(int dx=-4;dx<=4;dx++){
            if(dx==0&&dz==0){out.append("§e+");continue;}String key=Model.chunk(world,cx+dx,cz+dz),zone=engine.zone(key),owner=engine.owner(key);
            if(zone!=null){out.append(zone.equals("safe")?"§aS":"§cW");continue;}if(owner==null){out.append("§8·");continue;}
            char symbol=symbols.computeIfAbsent(owner,k->(char)('A'+symbols.size()));String color=own!=null&&own.id.equals(owner)?"§b":own!=null&&engine.relation(own.id,owner)==Relation.ENEMY?"§c":own!=null&&engine.relation(own.id,owner)==Relation.ALLY?"§a":"§7";out.append(color).append(symbol);
        }}out.append('\n').append(settings.text("map-legend"));symbols.forEach((f,s)->{Faction faction=engine.byName(f);if(faction!=null)out.append("\n§7").append(s).append(" §f").append(faction.name);});return out.toString();
    }
    private void admin(CommandSender sender,String[] a){
        if(!sender.hasPermission("nfactions.admin"))throw new IllegalArgumentException("permission-denied");String sub=a.length==0?"status":a[0];
        switch(sub){
            case "status"->{tell(sender,"admin-status","factions",engine.snapshot().factions.size(),"claims",engine.snapshot().claims.size(),"pending",engine.snapshot().transfers.values().stream().filter(t->t.status.equals("PENDING")).count(),"storage",engine.readOnly()?"READONLY":"OK");}
            case "reload"->{Settings next;try{next=new Settings(getDataFolder());}catch(RuntimeException error){getLogger().warning("Invalid configuration; previous settings retained: "+error.getMessage());throw new IllegalArgumentException("config-invalid");}engine.rules(next.rules);settings=next;integrations.placeholders();tell(sender,"reloaded");}
            case "zone"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("player-only");engine.zone(key(p),arg(a,1));tell(sender,"saved");}
            case "power"->{double power;try{power=Double.parseDouble(arg(a,2));}catch(NumberFormatException error){throw new IllegalArgumentException("usage");}engine.adminPower(engine.findPlayer(arg(a,1)),power);tell(sender,"saved");}
            case "unclaim"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("player-only");engine.unclaim(p.getUniqueId().toString(),key(p),true);tell(p,"unclaimed");}
            case "bypass"->{if(!(sender instanceof Player p)||!p.hasPermission("nfactions.bypass"))throw new IllegalArgumentException("permission-denied");String id=p.getUniqueId().toString();if(!bypass.remove(id))bypass.add(id);tell(p,"admin-toggle","state",bypass.contains(id));}
            case "spy"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("player-only");String id=p.getUniqueId().toString();if(!spies.remove(id))spies.add(id);tell(p,"admin-toggle","state",spies.contains(id));}
            case "pending"->{for(Transfer t:engine.snapshot().transfers.values())if(t.status.equals("PENDING"))sender.sendMessage(t.id+" | "+t.account+" | "+t.direction+" | "+FactionEngine.money(t.cents)+" | "+t.provider+" | before="+t.before);}
            case "resolve"->{String result=arg(a,2);if(!Set.of("success","failed").contains(result)||!arg(a,3).equals("confirm"))throw new IllegalArgumentException("usage");engine.resolveTransfer(arg(a,1),result.equals("success"),"ADMIN");tell(sender,"saved");}
            case "disband"->{Faction f=engine.byName(arg(a,1));if(f==null)throw new IllegalArgumentException("faction-unknown");if(!arg(a,2).equals("confirm"))throw new IllegalArgumentException("usage");engine.disband("ADMIN",true,f.id);tell(sender,"disbanded");}
            default->throw new IllegalArgumentException("usage");
        }
    }
    @Override public Optional<FactionView> faction(UUID id){synchronized(engine){Faction f=engine.faction(id.toString());return f==null?Optional.empty():Optional.of(new FactionView(f.id,f.name,f.description,f.members.size(),engine.lands(f.id),engine.power(f.id),f.bank));}}
    @Override public TerritoryView territory(String world,int x,int z){synchronized(engine){String key=Model.block(world,x,z);return new TerritoryView(engine.owner(key),engine.zone(key));}}
}
