package org.nfactionsx.core;

import java.util.*;

/** Persisted schema. Returned to consumers only as detached copies. Player identity is UUID-based. */
public final class Model {
    private Model() { }
    public enum Role { RECRUIT, MEMBER, OFFICER, LEADER }
    public enum Relation { NEUTRAL, ALLY, ENEMY }
    public enum Action { BUILD, INTERACT, CONTAINER, INVITE, KICK, CLAIM, HOME, WARP, WITHDRAW, DIPLOMACY, SETTINGS, UPGRADE }
    public static final class State {
        public int schema = 1;
        public Map<String, Profile> players = new LinkedHashMap<>();
        public Map<String, Faction> factions = new LinkedHashMap<>();
        public Map<String, String> claims = new LinkedHashMap<>();
        public Map<String, String> zones = new LinkedHashMap<>();
        public Map<String, Transfer> transfers = new LinkedHashMap<>();
    }
    public static final class Profile {
        public String uuid, name, faction;
        public double power;
        public long lastDeath;
    }
    public static final class Faction {
        public String id, name, description = "", motd = "";
        public long created, bank;
        public boolean open, alliesInteract;
        public Map<String, Role> members = new LinkedHashMap<>();
        public Map<String, Long> invites = new LinkedHashMap<>();
        public Map<String, Relation> relations = new LinkedHashMap<>();
        public Set<String> allyRequests = new HashSet<>();
        public Map<String, Home> warps = new LinkedHashMap<>();
        public Map<Action, Role> permissions = new EnumMap<>(Action.class);
        public Map<String, Integer> upgrades = new HashMap<>();
        public List<String> audit = new ArrayList<>();
        public Home home;
    }
    public static final class Home {
        public String world;
        public double x, y, z, yaw, pitch;
        public Home() { }
        public Home(String world, double x, double y, double z, double yaw, double pitch) {
            this.world=world; this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch;
        }
    }
    public static final class Transfer {
        public String id, faction, player, account, provider, direction, status = "PENDING";
        public long cents, time;
        public double before;
    }
    /** Chunk key uses world names without delimiter ambiguity, including negative coordinates. */
    public static String chunk(String world, int cx, int cz) { return world.length()+":"+world+":"+cx+":"+cz; }
    public static String block(String world, int x, int z) { return chunk(world, Math.floorDiv(x,16),Math.floorDiv(z,16)); }
    public static boolean validChunk(String key){
        try{
            int separator=key.indexOf(':'),length=Integer.parseInt(key.substring(0,separator)),start=separator+1;
            if(length<1||length>256||key.charAt(start+length)!=':')return false;
            String world=key.substring(start,start+length);String[] coordinates=key.substring(start+length+1).split(":",-1);
            return coordinates.length==2&&key.equals(chunk(world,Integer.parseInt(coordinates[0]),Integer.parseInt(coordinates[1])));
        }catch(RuntimeException error){return false;}
    }
}
