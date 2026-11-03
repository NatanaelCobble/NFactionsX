package org.nfactionsx;

import org.nfactionsx.core.*;
import org.nfactionsx.core.Model.*;
import org.powernukkitx.Player;
import org.powernukkitx.entity.projectile.EntityProjectile;
import org.powernukkitx.block.Block;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.event.*;
import org.powernukkitx.event.block.*;
import org.powernukkitx.event.entity.*;
import org.powernukkitx.event.inventory.*;
import org.powernukkitx.event.player.*;
import org.powernukkitx.inventory.*;
import org.powernukkitx.level.Position;
import java.util.*;

/** Deny-only listeners: never undo another plugin's cancellation. Reads are synchronized in the engine. */
public final class ProtectionListener implements Listener {
    private final NFactionsPlugin plugin;
    public ProtectionListener(NFactionsPlugin plugin){this.plugin=plugin;}
    private FactionEngine engine(){return plugin.engine();}
    private String key(Position pos){return NFactionsPlugin.key(pos);}
    private boolean protectedAt(Position pos){String key=key(pos);return engine().owner(key)!=null||engine().zone(key)!=null;}
    private boolean allow(Player player,Position position,Action action){
        return engine().canAccess(player.getUniqueId().toString(),key(position),action,plugin.bypass(player));
    }
    private String domain(Position pos){String key=key(pos);String zone=engine().zone(key);return zone==null?engine().owner(key):"zone:"+zone;}
    private boolean boundary(Position a,Position b){return !Objects.equals(domain(a),domain(b));}
    private Player attacker(EntityDamageEvent event){
        if(!(event instanceof EntityDamageByEntityEvent by))return null;
        if(by.getDamager() instanceof Player player)return player;
        if(by.getDamager() instanceof EntityProjectile projectile&&projectile.shootingEntity instanceof Player player)return player;
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event){if(!allow(event.getPlayer(),event.getBlock(),Action.BUILD))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event){if(!allow(event.getPlayer(),event.getBlockReplace(),Action.BUILD))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event){
        if(event.getAction()==PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK||event.getAction()==PlayerInteractEvent.Action.LEFT_CLICK_BLOCK){
            if(!allow(event.getPlayer(),event.getBlock(),Action.INTERACT))event.setCancelled();
            // Adjacent claim also governs item use (buckets, fire, spawn eggs and item placement).
            if(event.getAction()==PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK&&event.getItem()!=null&&!event.getItem().isNull()
                    &&!allow(event.getPlayer(),event.getBlock().getSide(event.getFace()),Action.BUILD))event.setCancelled();
        }
    }
    private List<Position> positions(Inventory inventory){
        if(inventory instanceof DoubleChestInventory doubleChest){
            List<Position> positions=new ArrayList<>();positions.addAll(positions(doubleChest.getLeftSide()));positions.addAll(positions(doubleChest.getRightSide()));return positions;
        }
        Object holder=inventory.getHolder();return holder instanceof Position pos&&!(holder instanceof Player)&&pos.getLevel()!=null?List.of(pos):List.of();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void inventory(InventoryOpenEvent event){for(Position pos:positions(event.getInventory()))if(!allow(event.getPlayer(),pos,Action.CONTAINER)){event.setCancelled();return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void transaction(ItemStackRequestActionEvent event){
        Player player=event.getPlayer();
        if(player.getTopWindow().isPresent())for(Position pos:positions(player.getTopWindow().get()))
            if(!allow(player,pos,Action.CONTAINER)){event.setCancelled();return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automation(InventoryMoveItemEvent event){
        List<Position> source=positions(event.getInventory()),target=positions(event.getTargetInventory());
        if(event.getSource() instanceof BlockEntity holder){source=new ArrayList<>(source);source.add(holder);}
        for(Position a:source)for(Position b:target)if(boundary(a,b)){event.setCancelled();return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void empty(PlayerBucketEmptyEvent event){if(!allow(event.getPlayer(),event.getLiquid(),Action.BUILD)||!allow(event.getPlayer(),event.getBlockClicked(),Action.BUILD))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fill(PlayerBucketFillEvent event){if(!allow(event.getPlayer(),event.getLiquid(),Action.BUILD))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fluid(BlockFromToEvent event){if(boundary(event.getFrom(),event.getTo()))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonEvent event){
        for(Block b:event.getBlocks())if(boundary(event.getBlock(),b)||boundary(b,b.getSide(event.isExtending()?event.getDirection():event.getDirection().getOpposite()))){event.setCancelled();return;}
        for(Block b:event.getDestroyedBlocks())if(boundary(event.getBlock(),b)){event.setCancelled();return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent event){
        List<Block> blocks=new ArrayList<>(event.getBlockList());blocks.removeIf(b->!engine().explosionAllowed(key(b),plugin.online()));event.setBlockList(blocks);
        Set<Block> fire=new HashSet<>(event.getIgnitions());fire.removeIf(b->!engine().explosionAllowed(key(b),plugin.online())||!plugin.settings().rules.fire&&protectedAt(b));event.setIgnitions(fire);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explodeBlock(BlockExplodeEvent event){
        Set<Block> blocks=new HashSet<>(event.getAffectedBlocks());blocks.removeIf(b->!engine().explosionAllowed(key(b),plugin.online()));event.setAffectedBlocks(blocks);
        Set<Block> fire=new HashSet<>(event.getIgnitions());fire.removeIf(b->!engine().explosionAllowed(key(b),plugin.online())||!plugin.settings().rules.fire&&protectedAt(b));event.setIgnitions(fire);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent event){if(!plugin.settings().rules.fire&&protectedAt(event.getBlock()))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent event){if(!plugin.settings().rules.fire&&protectedAt(event.getBlock()))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent event){if(boundary(event.getSource(),event.getBlock())||!plugin.settings().rules.fire&&protectedAt(event.getBlock()))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityChange(EntityBlockChangeEvent event){if(protectedAt(event.getFrom())||protectedAt(event.getTo()))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityInteract(EntityInteractEvent event){if(protectedAt(event.getBlock())&&!(event.getEntity() instanceof Player))event.setCancelled();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        if((event.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION||event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                &&!engine().explosionAllowed(key(event.getEntity()),plugin.online())){event.setCancelled();return;}
        if(event.getEntity() instanceof Player victim){
            if(event.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION||event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION){
                if(!engine().explosionAllowed(key(victim),plugin.online())){event.setCancelled();return;}
            }
            Player attacker=attacker(event);if(attacker!=null){
                if(!engine().pvp(attacker.getUniqueId().toString(),victim.getUniqueId().toString(),key(attacker),key(victim))){event.setCancelled();return;}
            }
        }else {Player attacker=attacker(event);if(attacker!=null&&!allow(attacker,event.getEntity(),Action.INTERACT))event.setCancelled();}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityUse(PlayerInteractEntityEvent event){if(!(event.getEntity() instanceof Player)&&!allow(event.getPlayer(),event.getEntity(),Action.INTERACT))event.setCancelled();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageEvent event){
        if(event.getEntity() instanceof Player victim){
            plugin.cancelTeleport(victim.getUniqueId().toString(),true);
            Player attacker=attacker(event);if(attacker!=null){plugin.tag(victim);plugin.tag(attacker);}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent event){plugin.main(()->{plugin.seen(event.getPlayer());plugin.updateOnline();var f=engine().faction(event.getPlayer().getUniqueId().toString());if(f!=null&&!f.motd.isBlank())plugin.tell(event.getPlayer(),"motd","text",f.motd);});}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event){String id=event.getPlayer().getUniqueId().toString();boolean alive=event.getPlayer().isAlive();plugin.main(()->{if(alive&&plugin.inCombat(id)&&plugin.settings().config.getBoolean("combat.logout-power-loss",true))plugin.safe(null,()->engine().death(id));plugin.cancelTeleport(id,false);plugin.clearSession(id);plugin.updateOnline();});}
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent event){String id=event.getEntity().getUniqueId().toString();plugin.main(()->plugin.safe(null,()->engine().death(id)));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event){
        if(event.getFrom().getFloorX()==event.getTo().getFloorX()&&event.getFrom().getFloorY()==event.getTo().getFloorY()&&event.getFrom().getFloorZ()==event.getTo().getFloorZ()&&event.getFrom().getLevel()==event.getTo().getLevel())return;
        String id=event.getPlayer().getUniqueId().toString();plugin.cancelTeleport(id,true);
        if(!key(event.getFrom()).equals(key(event.getTo())))event.getPlayer().sendActionBar(plugin.settings().text("territory-enter","territory",plugin.territory(event.getTo())));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event){
        if(!plugin.inCombat(event.getPlayer().getUniqueId().toString()))return;
        String root=event.getMessage().split(" ",2)[0].toLowerCase(Locale.ROOT).replaceFirst("^/","");
        if(plugin.settings().config.getStringList("combat.blocked-commands").contains(root)){event.setCancelled();plugin.tell(event.getPlayer(),"combat-blocked");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){if(plugin.inCombat(event.getPlayer().getUniqueId().toString())&&!plugin.bypass(event.getPlayer())){event.setCancelled();plugin.tell(event.getPlayer(),"combat-blocked");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void chat(PlayerChatEvent event){
        String mode=plugin.chatMode(event.getPlayer().getUniqueId().toString());if(mode.equals("public"))return;
        var faction=engine().faction(event.getPlayer().getUniqueId().toString());if(faction==null)return;
        event.setCancelled();String text=plugin.settings().text(mode.equals("ally")?"chat-ally":"chat-faction","faction",faction.name,"player",event.getPlayer().getName(),"message",FactionEngine.clean(event.getMessage()));
        for(Player recipient:plugin.getServer().getOnlinePlayers().values()){
            var other=engine().faction(recipient.getUniqueId().toString());
            if(other!=null&&(other.id.equals(faction.id)||mode.equals("ally")&&engine().relation(faction.id,other.id)==Relation.ALLY))recipient.sendMessage(text);
            else if(plugin.spy(recipient.getUniqueId().toString())&&recipient.hasPermission("nfactions.admin"))recipient.sendMessage(plugin.settings().text("spy-prefix")+text);
        }
    }
}
