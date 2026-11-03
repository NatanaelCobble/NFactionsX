package probe;

import org.nfactionsx.NFactionsPlugin;
import org.nfactionsx.ProtectionListener;
import org.nfactionsx.api.FactionsService;
import org.nfactionsx.core.Model;
import org.nvaultx.api.*;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.command.*;
import org.powernukkitx.level.*;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.event.block.BlockFromToEvent;
import org.powernukkitx.event.entity.EntityExplodeEvent;
import psycofeu.economy.FrenchEconomy;
import java.util.*;

/** Isolated server test only. Never included in the distributable NFactionsX JAR. */
public final class FactionsProbe extends PluginBase {
    private int checks;
    private void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try {
            NFactionsPlugin plugin=(NFactionsPlugin)getServer().getPluginManager().getPlugin("NFactionsX");
            check(plugin!=null&&plugin.isEnabled(),"NFactionsX enabled");
            var registered=getServer().getServiceManager().getProvider(FactionsService.class);
            check(registered!=null&&registered.getProvider()==plugin,"native integration service registered");
            var engine=plugin.engine();String uuid=UUID.randomUUID().toString(),name="NFXProbe";
            engine.seen(uuid,name);String factionName="Probe"+uuid.substring(0,6);engine.create(uuid,factionName);
            int cx=20;while(engine.owner(Model.chunk("world",cx,20))!=null||engine.owner(Model.chunk("world",cx,21))!=null||engine.zone(Model.chunk("world",cx,20))!=null)cx+=4;
            int x=cx*16;
            engine.claim(uuid,"world",cx,20);engine.claim(uuid,"world",cx,21);
            var view=registered.getProvider().faction(UUID.fromString(uuid)).orElseThrow();
            check(view.name().equals(factionName)&&view.claims()==2,"public API faction snapshot");
            check(registered.getProvider().territory("world",x,320).factionId().equals(view.id()),"public API territory lookup");
            FrenchEconomy.getWalletManager().loadPlayer(name);var wallet=FrenchEconomy.getWalletManager().getWallet(name);wallet.setMoney(500);
            Economy economy=NVaultX.find(getServer(),Economy.class).orElseThrow();
            String deposit=engine.beginTransfer(uuid,name,economy.getName(),5000,false,economy.getBalance(name));
            check(economy.withdraw(name,50).successful(),"NVaultX player debit");engine.resolveTransfer(deposit,true,uuid);
            check(engine.faction(uuid).bank==5000&&wallet.getMoney()==450,"shared provider funded faction bank");
            String withdraw=engine.beginTransfer(uuid,name,economy.getName(),2000,true,economy.getBalance(name));
            check(economy.deposit(name,20).successful(),"NVaultX player credit");engine.resolveTransfer(withdraw,true,uuid);
            check(engine.faction(uuid).bank==3000&&wallet.getMoney()==470,"bank withdrawal conservation");
            var papi=getServer().getPluginManager().getPlugin("PlaceholderAPI");check(papi!=null&&papi.isEnabled(),"PlaceholderAPI enabled");
            var placeholders=papi.getClass().getDeclaredField("placeholders");placeholders.setAccessible(true);Map<?,?> values=(Map<?,?>)placeholders.get(papi);
            for(String field:List.of("name","role","power","claims","bank","members","territory"))check(values.containsKey("nfactions_"+field),"placeholder "+field);
            var region=getServer().getPluginManager().getPlugin("SRegionProtector");check(region!=null&&region.isEnabled(),"SRegionProtector enabled");
            Object manager=region.getClass().getMethod("getRegionManager").invoke(region);Level level=getServer().getLevelByName("world");
            Object created=manager.getClass().getMethod("createRegion",String.class,String.class,Vector3.class,Vector3.class,Level.class)
                    .invoke(manager,"probe"+uuid.substring(0,6),"Probe",new Vector3(1600,level.getMinHeight(),1600),new Vector3(1615,level.getMaxHeight()-1,1615),level);
            check(created!=null,"test region created");
            check(plugin.integrations().regionOverlap("world",100,100),"SRegion overlap blocks claims");
            check(!plugin.integrations().regionOverlap("world",105,105),"unprotected chunk allowed");
            ProtectionListener listener=new ProtectionListener(plugin);
            var inside=level.getBlock(x,64,320);var same=level.getBlock(x,64,336);var outside=level.getBlock(x-16,64,320);
            BlockFromToEvent crossing=new BlockFromToEvent(outside,inside);listener.fluid(crossing);check(crossing.isCancelled(),"liquid crossing protected border denied");
            BlockFromToEvent internal=new BlockFromToEvent(inside,same);listener.fluid(internal);check(!internal.isCancelled(),"same faction liquids allowed");
            engine.zone(Model.chunk("world",cx+1,20),"safe");var safe=level.getBlock(x+16,64,320);
            EntityExplodeEvent explosion=new EntityExplodeEvent(null,new Position(x+16,64,320,level),new ArrayList<>(List.of(inside,outside,safe)),1);
            listener.explode(explosion);check(!explosion.getBlockList().contains(safe)&&explosion.getBlockList().contains(inside),"TNT filters safezone while raid claims remain vulnerable");
            plugin.settings().rules.offlineRaids=false;EntityExplodeEvent offline=new EntityExplodeEvent(null,new Position(x,64,320,level),new ArrayList<>(List.of(inside,outside)),1);
            listener.explode(offline);check(!offline.getBlockList().contains(inside)&&offline.getBlockList().contains(outside),"offline raid policy filters only protected land");plugin.settings().rules.offlineRaids=true;
            String cleanup=engine.beginTransfer(uuid,name,economy.getName(),3000,true,economy.getBalance(name));
            check(economy.deposit(name,30).successful(),"return remaining test funds");engine.resolveTransfer(cleanup,true,uuid);
            check(wallet.getMoney()==500,"test leaves provider balance unchanged");
            engine.disband(uuid,false,view.id());engine.zone(Model.chunk("world",cx+1,20),"none");
            manager.getClass().getMethod("removeRegion",created.getClass()).invoke(manager,created);
            getLogger().info("FACTIONS PROBE PASS: "+checks+" server assertions; NVaultX/FrenchEconomy, PlaceholderAPI, SRegionProtector, service API, fluid and TNT event filters");
        }catch(Throwable error){getLogger().error("FACTIONS PROBE FAIL",error);}
        return true;
    }
}
