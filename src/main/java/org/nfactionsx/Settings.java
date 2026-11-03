package org.nfactionsx;

import org.nfactionsx.core.*;
import org.nfactionsx.core.Model.*;
import org.powernukkitx.utils.Config;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.nio.file.Files;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Configuration snapshots: callers never observe a partially reloaded set of policies. */
public final class Settings {
    public final Config config, messages, menus;
    public final Rules rules;
    public final int teleportSeconds,combatSeconds,menuPageSize,commandCooldownMs;
    public Settings(File folder) {
        for(String file:List.of("config.yml","messages.yml","menus.yml"))validateYaml(new File(folder,file));
        config=new Config(new File(folder,"config.yml"),Config.YAML);
        messages=new Config(new File(folder,"messages.yml"),Config.YAML);
        menus=new Config(new File(folder,"menus.yml"),Config.YAML);
        if(!config.isCorrect()||!messages.isCorrect()||!menus.isCorrect())throw new IllegalArgumentException("YAML invalide");
        rules=new Rules();rules.maxFactions=config.getInt("limits.factions",500);rules.members=config.getInt("limits.members",20);
        rules.maxClaims=config.getInt("limits.claims",100);rules.warps=config.getInt("limits.warps",3);rules.inviteSeconds=config.getInt("invites.expiry-seconds",300);
        rules.maxUpgrade=config.getInt("upgrades.max-level",5);rules.membersPerUpgrade=config.getInt("upgrades.members-per-level",5);
        rules.claimsPerUpgrade=config.getInt("upgrades.claims-per-level",10);rules.warpsPerUpgrade=config.getInt("upgrades.warps-per-level",1);
        rules.powerStart=config.getDouble("power.start",10);rules.powerMax=config.getDouble("power.max",10);rules.powerMin=config.getDouble("power.min",-10);
        rules.deathLoss=config.getDouble("power.death-loss",2);rules.regenPerMinute=config.getDouble("power.regen-per-minute",0.2);
        rules.claimCost=FactionEngine.cents(config.getString("economy.claim-cost","0"));rules.upgradeCost=FactionEngine.cents(config.getString("economy.upgrade-base-cost","500"));
        rules.bankLimit=FactionEngine.cents(config.getString("economy.bank-limit","1000000000000"));
        rules.adjacency=config.getBoolean("claims.adjacency",true);rules.overclaim=config.getBoolean("claims.overclaim",true);
        rules.enemyOnlyPvp=config.getBoolean("combat.enemy-only",false);rules.friendlyFire=config.getBoolean("combat.friendly-fire",false);
        rules.raids=config.getBoolean("raids.enabled",true);rules.explosions=config.getBoolean("raids.explosions",true);
        rules.offlineRaids=config.getBoolean("raids.offline",true);rules.fire=config.getBoolean("protection.fire",false);
        String grace=config.getString("raids.grace-until","");rules.graceUntil=grace.isBlank()?0:Instant.parse(grace).toEpochMilli();
        rules.disabledWorlds=new HashSet<>(config.getStringList("claims.disabled-worlds"));
        for(Action a:Action.values()) rules.defaults.put(a,Role.valueOf(config.getString("roles."+a.name().toLowerCase(Locale.ROOT),rules.defaults.get(a).name()).toUpperCase(Locale.ROOT)));
        rules.validate();teleportSeconds=config.getInt("teleport.delay-seconds",5);combatSeconds=config.getInt("combat.tag-seconds",15);
        menuPageSize=menus.getInt("page-size",8);commandCooldownMs=config.getInt("commands.cooldown-ms",350);
        if(teleportSeconds<0||teleportSeconds>120||combatSeconds<0||combatSeconds>600||menuPageSize<1||menuPageSize>20||commandCooldownMs<0||commandCooldownMs>60000)
            throw new IllegalArgumentException("Configuration menus/teleport/combat hors limites");
    }
    private static void validateYaml(File file){
        try{
            if(Files.size(file.toPath())>1024*1024)throw new IllegalArgumentException("Configuration trop volumineuse");
            LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setCodePointLimit(1024*1024);
            if(!(new Yaml(new SafeConstructor(options)).load(Files.readString(file.toPath())) instanceof Map))throw new IllegalArgumentException("Configuration vide ou non structuree");
        }catch(java.io.IOException|org.yaml.snakeyaml.error.YAMLException error){throw new IllegalArgumentException("config-invalid",error);}
    }
    public String text(String key,Object... pairs) {
        String value=messages.getString(key,key);
        for(int i=0;i+1<pairs.length;i+=2)value=value.replace("{"+pairs[i]+"}",String.valueOf(pairs[i+1]));
        return value.replace('&','§');
    }
    public String menu(String key) { return menus.getString(key,key).replace('&','§'); }
    public boolean feature(String name) { return config.getBoolean("features."+name,true); }
}
