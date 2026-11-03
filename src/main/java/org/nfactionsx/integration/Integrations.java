package org.nfactionsx.integration;

import org.nfactionsx.NFactionsPlugin;
import org.nfactionsx.core.*;
import org.nfactionsx.core.Model.*;
import org.nvaultx.api.*;
import org.powernukkitx.Player;
import org.powernukkitx.plugin.Plugin;
import java.util.*;

/** Optional integrations use verified public signatures. Unknown enabled region APIs refuse new claims. */
public final class Integrations {
    private final NFactionsPlugin plugin;
    public Integrations(NFactionsPlugin plugin) { this.plugin=plugin; }
    public Optional<Economy> economy() { return NVaultX.find(plugin.getServer(),Economy.class); }
    public void transfer(Player player,String amount,boolean withdraw) {
        if(!plugin.settings().feature("bank"))throw new IllegalArgumentException("feature-disabled");
        Economy provider=economy().orElseThrow(()->new IllegalArgumentException("economy-missing"));
        String account=player.getName();long cents=FactionEngine.cents(amount);
        if(!provider.hasAccount(account))throw new IllegalArgumentException("economy-account");
        String id=plugin.engine().beginTransfer(player.getUniqueId().toString(),account,provider.getName(),cents,withdraw,provider.getBalance(account));
        // Once prepared, any thrown/unknown provider result remains pending for manual reconciliation.
        EconomyResponse response;
        try { response=withdraw?provider.deposit(account,cents/100.0):provider.withdraw(account,cents/100.0); }
        catch(RuntimeException error) { plugin.getLogger().error("NVaultX transfer uncertain: "+id,error);throw new IllegalArgumentException("transfer-pending"); }
        switch(response.status()) {
            case SUCCESS -> plugin.engine().resolveTransfer(id,true,player.getUniqueId().toString());
            case FAILURE,NOT_SUPPORTED -> {plugin.engine().resolveTransfer(id,false,player.getUniqueId().toString());throw new IllegalArgumentException("transfer-refused");}
            case UNKNOWN -> throw new IllegalArgumentException("transfer-pending");
        }
    }
    public boolean regionOverlap(String world,int cx,int cz) {
        if(!plugin.settings().config.getBoolean("integrations.sregion-protector",true))return false;
        Plugin region=plugin.getServer().getPluginManager().getPlugin("SRegionProtector");
        if(region==null||!region.isEnabled())return false;
        try {
            Object manager=region.getClass().getMethod("getRegionManager").invoke(region);
            Map<?,?> regions=(Map<?,?>)manager.getClass().getMethod("getRegions").invoke(manager);
            for(Object r:List.copyOf(regions.values())) {
                Class<?> type=r.getClass();
                if(!world.equals(type.getMethod("getLevel").invoke(r)))continue;
                double minX=((Number)type.getMethod("getMinX").invoke(r)).doubleValue();
                double maxX=((Number)type.getMethod("getMaxX").invoke(r)).doubleValue();
                double minZ=((Number)type.getMethod("getMinZ").invoke(r)).doubleValue();
                double maxZ=((Number)type.getMethod("getMaxZ").invoke(r)).doubleValue();
                if(maxX>=cx*16.0&&minX<cx*16.0+16&&maxZ>=cz*16.0&&minZ<cz*16.0+16)return true;
            }return false;
        }catch(ReflectiveOperationException|RuntimeException error){plugin.getLogger().warning("SRegionProtector check failed; claim refused: "+error.getClass().getSimpleName());return true;}
    }
    public void placeholders() {
        Plugin papi=plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if(papi==null||!papi.isEnabled()||!plugin.settings().config.getBoolean("integrations.placeholderapi",true))return;
        try {
            ClassLoader loader=papi.getClass().getClassLoader();
            Class<?> contract=Class.forName("org.powernukkitx.placeholderapi.placeholder.Placeholder",true,loader);
            var register=papi.getClass().getMethod("register",String.class,contract);
            for(String field:List.of("name","role","power","claims","bank","members","territory")) {
                Object placeholder=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{contract},(proxy,method,args)->{
                    if(method.getName().equals("process"))return plugin.isEnabled()?value((Player)args[0],field):"";
                    if(method.getName().equals("toString"))return "NFactionsX_"+field;
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("equals"))return proxy==args[0];return null;
                });register.invoke(papi,"nfactions_"+field,placeholder);
            }
        }catch(ReflectiveOperationException|LinkageError error){plugin.getLogger().warning("PlaceholderAPI unavailable: "+error.getMessage());}
    }
    private String value(Player p,String field) {
        if(p==null)return "";String id=p.getUniqueId().toString();Faction f=plugin.engine().faction(id);
        if(field.equals("territory"))return plugin.territory(p);
        if(f==null)return field.equals("name")?plugin.settings().text("wilderness"):"0";
        return switch(field){case "name"->f.name;case "role"->plugin.settings().text("role."+f.members.get(id));
            case "power"->String.format(Locale.ROOT,"%.1f",plugin.engine().power(f.id));case "claims"->String.valueOf(plugin.engine().lands(f.id));
            case "bank"->FactionEngine.money(f.bank);case "members"->String.valueOf(f.members.size());default->"";};
    }
}
