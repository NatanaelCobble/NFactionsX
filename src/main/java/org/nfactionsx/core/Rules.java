package org.nfactionsx.core;

import java.util.*;
import org.nfactionsx.core.Model.*;

/** Validated server policy, replaced as a whole on configuration reload. Money is stored in cents. */
public final class Rules {
    public int maxFactions=500, members=20, maxClaims=100, warps=3, inviteSeconds=300;
    public int maxUpgrade=5, membersPerUpgrade=5, claimsPerUpgrade=10, warpsPerUpgrade=1;
    public double powerStart=10, powerMax=10, powerMin=-10, deathLoss=2, regenPerMinute=0.2;
    public long claimCost=0, upgradeCost=50_000, bankLimit=100_000_000_000_000L;
    public boolean adjacency=true, overclaim=true, enemyOnlyPvp=false, friendlyFire=false;
    public boolean explosions=true, fire=false, offlineRaids=true, raids=true;
    public long graceUntil=0;
    public Set<String> disabledWorlds=new HashSet<>();
    public EnumMap<Action,Role> defaults=new EnumMap<>(Action.class);
    public Rules() {
        for(Action action:Action.values()) defaults.put(action, Role.OFFICER);
        defaults.put(Action.BUILD,Role.MEMBER); defaults.put(Action.INTERACT,Role.RECRUIT);
        defaults.put(Action.CONTAINER,Role.MEMBER); defaults.put(Action.HOME,Role.RECRUIT);
        defaults.put(Action.WARP,Role.RECRUIT); defaults.put(Action.SETTINGS,Role.LEADER);
    }
    public void validate() {
        if(maxFactions<1||maxFactions>10000||members<1||members>500||maxClaims<1||maxClaims>10000||warps<0||warps>100
                ||inviteSeconds<1||inviteSeconds>86400||maxUpgrade<0||maxUpgrade>20||membersPerUpgrade<0
                ||claimsPerUpgrade<0||warpsPerUpgrade<0||claimCost<0||upgradeCost<0||bankLimit<1
                ||bankLimit>100_000_000_000_000L||claimCost>bankLimit||upgradeCost>bankLimit
                ||!Double.isFinite(powerStart)||!Double.isFinite(powerMax)||!Double.isFinite(powerMin)
                ||!Double.isFinite(deathLoss)||!Double.isFinite(regenPerMinute)||powerMin>powerStart||powerStart>powerMax
                ||powerMax>10000||powerMin< -10000||deathLoss<0||regenPerMinute<0||regenPerMinute>1000)
            throw new IllegalArgumentException("Configuration factions hors limites");
    }
}
