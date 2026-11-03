package probe;

import com.google.gson.*;
import org.nfactionsx.NFactionsPlugin;
import org.nfactionsx.core.Model;
import org.nfactionsx.core.Model.*;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.blockentity.BlockEntityChest;
import org.powernukkitx.command.*;
import org.powernukkitx.event.Event;
import org.powernukkitx.event.block.*;
import org.powernukkitx.event.entity.*;
import org.powernukkitx.event.inventory.*;
import org.powernukkitx.form.window.*;
import org.powernukkitx.inventory.*;
import org.powernukkitx.inventory.request.ItemStackRequestContext;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.*;
import org.powernukkitx.plugin.PluginBase;
import org.nvaultx.api.*;
import psycofeu.economy.FrenchEconomy;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Test-only simulated actors. Actual menus/commands/events run on a real PNX server; no network login is claimed. */
public final class SimulatedPlayersProbe extends PluginBase {
    private final Map<Player,Form<?>> forms=new IdentityHashMap<>();
    private final List<String> results=new ArrayList<>();
    private Path report;
    private NFactionsPlugin factions;
    private Level world;
    private int sequence;
    private void check(boolean ok,String label){results.add((ok?"PASS ":"FAIL ")+label);if(!ok)throw new AssertionError(label);}
    private String id(Player p){return p.getUniqueId().toString();}
    private Player actor(String name,int x,int z,boolean staff)throws Exception{
        Player p=mock(Player.class);UUID uuid=UUID.randomUUID();
        p.level=world;p.x=x+0.5;p.y=65;p.z=z+0.5;
        when(p.getName()).thenReturn(name);when(p.getUniqueId()).thenReturn(uuid);when(p.getServer()).thenReturn(getServer());
        when(p.isOnline()).thenReturn(true);when(p.isAlive()).thenReturn(true);when(p.getLevel()).thenReturn(world);
        when(p.getFloorX()).thenReturn(x);when(p.getFloorY()).thenReturn(65);when(p.getFloorZ()).thenReturn(z);
        when(p.getLocation()).thenReturn(new Location(x+0.5,65,z+0.5,0,0,world));when(p.getTopWindow()).thenReturn(Optional.empty());
        when(p.hasPermission(anyString())).thenAnswer(call->call.getArgument(0).equals("nfactions.use")||staff);
        Object inventory=mock(Player.class.getMethod("getInventory").getReturnType());doReturn(inventory).when(p).getInventory();
        doAnswer(call->{Form<?> form=call.getArgument(0);forms.put(p,form);Files.writeString(report.resolve("form-"+(++sequence)+".json"),form.toJson());return sequence;}).when(p).sendForm(any(Form.class));
        factions.engine().seen(uuid.toString(),name);FrenchEconomy.getWalletManager().loadPlayer(name);FrenchEconomy.getWalletManager().getWallet(name).setMoney(1000);
        return p;
    }
    private void command(Player actor,String value){getServer().executeCommand(actor,"f "+value);}
    private void click(Player p,String contains){
        Form<?> current=forms.get(p);check(current instanceof SimpleForm,"simple menu for "+contains);
        JsonArray buttons=JsonParser.parseString(current.toJson()).getAsJsonObject().getAsJsonArray("elements");
        for(int i=0;i<buttons.size();i++)if(buttons.get(i).getAsJsonObject().get("text").getAsString().contains(contains)){current.respond(p,String.valueOf(i),null);return;}
        throw new AssertionError("Missing menu button: "+contains);
    }
    private void submit(Player p,String json){Form<?> form=forms.get(p);check(form instanceof CustomForm,"custom form submission");form.respond(p,json,null);}
    private void event(Event event){getServer().getPluginManager().callEvent(event);}
    private ChestInventory chest(int x,int z){
        BlockEntityChest holder=mock(BlockEntityChest.class);when(holder.getLevel()).thenReturn(world);when(holder.getFloorX()).thenReturn(x);when(holder.getFloorY()).thenReturn(65);when(holder.getFloorZ()).thenReturn(z);
        ChestInventory inventory=mock(ChestInventory.class);when(inventory.getHolder()).thenReturn(holder);return inventory;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            report=getDataFolder().toPath().resolve("reports").resolve(UUID.randomUUID().toString());Files.createDirectories(report);
            factions=(NFactionsPlugin)getServer().getPluginManager().getPlugin("NFactionsX");world=getServer().getDefaultLevel();
            String run=UUID.randomUUID().toString().substring(0,5);int cx=40;
            while(factions.engine().owner(Model.chunk(world.getName(),cx,40))!=null)cx+=4;
            int x=cx*16,z=640;
            Player alice=actor("Alice"+run,x,z,false),bob=actor("Bob"+run,x+32,z,false),carol=actor("Carol"+run,x,z,false),staff=actor("Staff"+run,x,z,true);
            String alpha="Alpha"+run,beta="Beta"+run;
            command(alice,"");check(forms.get(alice).toJson().contains("FONDER"),"factionless dashboard opens through registered /f command");
            click(alice,"FONDER");submit(alice,"[\""+alpha+"\"]");
            check(factions.engine().faction(id(alice)).name.equals(alpha),"faction created through real form callback");
            JsonArray dashboard=JsonParser.parseString(forms.get(alice).toJson()).getAsJsonObject().getAsJsonArray("elements");check(dashboard.size()==8,"member dashboard has eight configured sections");
            command(bob,"create "+beta);check(factions.engine().faction(id(bob))!=null,"second faction created by registered command");
            command(alice,"invite "+carol.getName());command(carol,"join "+alpha);check(factions.engine().faction(id(carol))!=null,"invite and join by registered commands");
            command(alice,"claim");String key=Model.chunk(world.getName(),cx,40);check(factions.engine().owner(key).equals(factions.engine().faction(id(alice)).id),"player command claims actual chunk");
            Block stone=Block.get("minecraft:stone",world,x,65,z);Item air=Item.get("minecraft:air");
            BlockBreakEvent recruit=new BlockBreakEvent(carol,stone,air,new Item[0]);event(recruit);check(recruit.isCancelled(),"recruit cannot break faction blocks through real event bus");
            command(alice,"rank "+carol.getName()+" MEMBER");BlockBreakEvent member=new BlockBreakEvent(carol,stone,air,new Item[0]);event(member);check(!member.isCancelled(),"promotion changes real block-break authorization");
            BlockBreakEvent foreign=new BlockBreakEvent(bob,stone,air,new Item[0]);event(foreign);check(foreign.isCancelled(),"outsider cannot break claimed blocks");
            BlockPlaceEvent place=new BlockPlaceEvent(bob,stone,stone,stone,air);event(place);check(place.isCancelled(),"outsider cannot place claimed blocks");
            ChestInventory left=chest(x,z),right=chest(x+1,z);DoubleChestInventory doubleChest=mock(DoubleChestInventory.class);
            BlockEntityChest leftHolder=(BlockEntityChest)left.getHolder();
            when(doubleChest.getLeftSide()).thenReturn(left);when(doubleChest.getRightSide()).thenReturn(right);when(doubleChest.getHolder()).thenReturn(leftHolder);
            InventoryOpenEvent openMember=new InventoryOpenEvent(doubleChest,carol);event(openMember);check(!openMember.isCancelled(),"member opens both halves of faction chest");
            InventoryOpenEvent openOutsider=new InventoryOpenEvent(doubleChest,bob);event(openOutsider);check(openOutsider.isCancelled(),"outsider double-chest access denied");
            when(carol.getTopWindow()).thenReturn(Optional.of(doubleChest));command(alice,"rank "+carol.getName()+" RECRUIT");
            ItemStackRequestActionEvent request=new ItemStackRequestActionEvent(carol,null,mock(ItemStackRequestContext.class));event(request);
            check(request.isCancelled(),"revoked container access blocks already-open inventory actions");when(carol.getTopWindow()).thenReturn(Optional.empty());
            command(alice,"bank");click(alice,"Déposer");Form<?> deposit=forms.get(alice);submit(alice,"[\"25\"]");
            check(factions.engine().faction(id(alice)).bank==2500,"menu deposit credits faction bank");
            Economy economy=NVaultX.find(getServer(),Economy.class).orElseThrow();check(economy.getBalance(alice.getName())==975,"menu deposit debits real FrenchEconomy account");
            deposit.respond(alice,"[\"25\"]",null);check(factions.engine().faction(id(alice)).bank==2500&&economy.getBalance(alice.getName())==975,"duplicate form submission does not duplicate transfer");
            command(alice,"bank");click(alice,"Retirer");submit(alice,"[\"10\"]");check(factions.engine().faction(id(alice)).bank==1500&&economy.getBalance(alice.getName())==985,"menu withdrawal conserves funds");
            command(alice,"settings");submit(alice,"[\""+alpha+"\",\"Test description\",\"Hello team\",\"false\",\"true\"]");check(factions.engine().faction(id(alice)).description.equals("Test description"),"configuration form updates identity fields");
            command(alice,"ally "+beta);check(factions.engine().relation(factions.engine().faction(id(alice)).id,factions.engine().faction(id(bob)).id)==Relation.NEUTRAL,"first ally command only sends request");
            command(bob,"ally "+alpha);EntityDamageByEntityEvent allied=new EntityDamageByEntityEvent(alice,bob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);event(allied);check(allied.isCancelled(),"allied PvP blocked by real event bus");
            command(alice,"enemy "+beta);EntityDamageByEntityEvent enemy=new EntityDamageByEntityEvent(alice,bob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);event(enemy);check(!enemy.isCancelled(),"enemy PvP allowed");
            check(factions.inCombat(id(alice))&&factions.inCombat(id(bob)),"actual damage event marks both players in combat");
            command(alice,"sethome");command(alice,"home"); // Combat must reject scheduling the teleport.
            var teleportMap=NFactionsPlugin.class.getDeclaredField("teleports");teleportMap.setAccessible(true);check(((Map<?,?>)teleportMap.get(factions)).isEmpty(),"combat prevents /f home scheduling");
            factions.engine().zone(key,"safe");EntityDamageByEntityEvent safe=new EntityDamageByEntityEvent(bob,alice,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);event(safe);check(safe.isCancelled(),"safezone PvP blocks incoming attack");
            factions.engine().zone(key,"none");factions.engine().claim(id(alice),world.getName(),cx,40);
            BlockBreakEvent staffDefault=new BlockBreakEvent(staff,stone,air,new Item[0]);event(staffDefault);check(staffDefault.isCancelled(),"staff permission alone does not activate bypass");
            command(staff,"admin bypass");BlockBreakEvent staffBypass=new BlockBreakEvent(staff,stone,air,new Item[0]);event(staffBypass);check(!staffBypass.isCancelled(),"explicit staff bypass works");command(staff,"admin bypass");
            // Remove the live combat tag only inside this isolated fixture to test normal menu actions next.
            var combat=NFactionsPlugin.class.getDeclaredField("combat");combat.setAccessible(true);((Map<?,?>)combat.get(factions)).clear();
            command(alice,"bank withdraw 15");check(economy.getBalance(alice.getName())==1000&&factions.engine().faction(id(alice)).bank==0,"fixture returns all deposited funds");
            command(alice,"disband");check(forms.get(alice) instanceof ModalForm,"disband requires modal confirmation");forms.get(alice).respond(alice,"false",null);check(factions.engine().faction(id(alice))!=null,"cancel disband leaves faction intact");
            command(alice,"disband");forms.get(alice).respond(alice,"true",null);check(factions.engine().faction(id(alice))==null&&factions.engine().faction(id(carol))==null,"confirmed disband removes faction and membership");
            command(bob,"disband");forms.get(bob).respond(bob,"true",null);
            Files.writeString(report.resolve("results.txt"),String.join("\n",results));
            getLogger().info("SIMULATED PLAYERS PASS: "+results.size()+" checks; real commands, menus, roles, containers, economy and PvP; report="+report);
        }catch(Throwable error){
            try{if(report!=null)Files.writeString(report.resolve("results.txt"),String.join("\n",results)+"\nERROR "+error);}catch(Exception ignored){}
            getLogger().error("SIMULATED PLAYERS FAIL",error);
        }
        return true;
    }
}
