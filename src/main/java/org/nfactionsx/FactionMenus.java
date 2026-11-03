package org.nfactionsx;

import org.nfactionsx.core.*;
import org.nfactionsx.core.Model.*;
import org.powernukkitx.Player;
import org.powernukkitx.form.window.*;
import org.powernukkitx.form.element.simple.ButtonImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Native Bedrock UI: paginated buttons, contextual summaries and explicit destructive confirmations. */
public final class FactionMenus {
    private final NFactionsPlugin plugin;
    public FactionMenus(NFactionsPlugin plugin){this.plugin=plugin;}
    private Settings s(){return plugin.settings();}
    private FactionEngine e(){return plugin.engine();}
    private String id(Player p){return p.getUniqueId().toString();}
    private Faction own(Player p){Faction f=e().faction(id(p));if(f==null)throw new IllegalArgumentException("no-faction");return f;}
    private String name(String id){Profile profile=e().profile(id);return profile==null?id:profile.name;}
    private String role(Role role){return s().text("role."+role);}
    private SimpleForm form(String title,String body){return new SimpleForm(s().menu("theme.title-prefix")+s().menu("titles."+title),body);}
    private void button(SimpleForm form,String key,Consumer<Player> action){
        String label=s().menu("buttons."+key);String icon=s().menus.getString("icons."+key,"");AtomicBoolean used=new AtomicBoolean();
        Consumer<Player> callback=p->{if(used.compareAndSet(false,true))plugin.act(p,()->action.accept(p));};
        if(icon.isBlank())form.addButton(label,callback);else form.addButton(label,ButtonImage.Type.PATH.of(icon),callback);
    }
    private void dynamic(SimpleForm form,String label,Consumer<Player> action){AtomicBoolean used=new AtomicBoolean();form.addButton(label,p->{if(used.compareAndSet(false,true))plugin.act(p,()->action.accept(p));});}
    private void back(SimpleForm form){button(form,"back",this::dashboard);}
    public void input(Player player,String title,String label,String initial,Consumer<String> callback){
        AtomicBoolean used=new AtomicBoolean();new CustomForm(s().menu("theme.title-prefix")+s().menu("titles."+title))
                .addInput(s().text(label),"",initial).submitButton(s().menu("buttons.submit"))
                .onSubmit((p,response)->{if(used.compareAndSet(false,true))plugin.act(p,()->{String value=response.getInputResponse(0);if(value==null)throw new IllegalArgumentException("usage");callback.accept(value.trim());});}).send(player);
    }
    public void confirm(Player p,String message,Runnable callback){
        AtomicBoolean used=new AtomicBoolean();new ModalForm(s().menu("theme.title-prefix")+s().menu("titles.confirm"),s().text(message))
                .yes(s().menu("buttons.confirm"),player->{if(used.compareAndSet(false,true))plugin.act(player,callback);})
                .no(s().menu("buttons.cancel"),player->plugin.act(player,()->dashboard(player))).send(p);
    }
    public void dashboard(Player p){
        Faction f=e().faction(id(p));String body=f==null?s().text("ui.welcome"):summary(p,f);
        SimpleForm form=form("dashboard",body);
        if(f==null){button(form,"create",q->input(q,"create","ui.faction-name","",value->{e().create(id(q),value);dashboard(q);}));button(form,"directory",q->directory(q,0));button(form,"map",q->q.sendMessage(plugin.map(q)));}
        else for(String action:s().menus.getStringList("dashboard-actions"))switch(action){
            case "overview"->button(form,action,q->info(q,f.id));case "members"->button(form,action,q->members(q,0));
            case "claims"->button(form,action,this::claims);case "home"->{if(s().feature("teleport"))button(form,action,q->plugin.teleport(q,"home"));}
            case "warps"->{if(s().feature("teleport"))button(form,action,this::warps);}
            case "bank"->{if(s().feature("bank"))button(form,action,this::bank);}
            case "relations"->{if(s().feature("diplomacy"))button(form,action,q->relations(q,0));}
            case "upgrades"->{if(s().feature("upgrades"))button(form,action,this::upgrades);}
            case "chat"->{if(s().feature("chat"))button(form,action,q->plugin.execute(q,new String[]{"chat"}));}
            case "settings"->button(form,action,this::settings);case "permissions"->button(form,action,this::permissions);
            case "audit"->button(form,action,q->audit(q,0));case "directory"->button(form,action,q->directory(q,0));
            case "manage"->button(form,action,this::manage);
            case "leave"->button(form,action,q->plugin.execute(q,new String[]{"leave"}));
            case "disband"->{if(f.members.get(id(p))==Role.LEADER)button(form,action,q->plugin.execute(q,new String[]{"disband"}));}
            default->{ }
        }form.send(p);
    }
    public void manage(Player p){
        Faction f=own(p);SimpleForm form=form("manage",s().text("ui.manage"));
        button(form,"settings",this::settings);button(form,"permissions",this::permissions);button(form,"audit",q->audit(q,0));
        if(s().feature("upgrades"))button(form,"upgrades",this::upgrades);
        if(s().feature("chat"))button(form,"chat",q->plugin.execute(q,new String[]{"chat"}));
        button(form,"directory",q->directory(q,0));button(form,"leave",q->plugin.execute(q,new String[]{"leave"}));
        if(f.members.get(id(p))==Role.LEADER)button(form,"disband",q->plugin.execute(q,new String[]{"disband"}));
        back(form);form.send(p);
    }
    private String summary(Player p,Faction f){return s().text("ui.summary","name",f.name,"description",f.description,"role",f.members.containsKey(id(p))?role(f.members.get(id(p))):s().text("visitor"),"members",f.members.size(),"power",String.format(Locale.ROOT,"%.1f",e().power(f.id)),"claims",e().lands(f.id),"capacity",e().capacity(f.id),"bank",FactionEngine.money(f.bank));}
    public void info(Player p,String faction){
        Faction f=e().byName(faction);if(f==null)throw new IllegalArgumentException("faction-unknown");SimpleForm form=form("overview",summary(p,f));
        if(e().faction(id(p))==null&&(f.open||f.invites.getOrDefault(id(p),0L)>System.currentTimeMillis()))button(form,"join",q->{e().join(id(q),f.id);dashboard(q);});
        back(form);form.send(p);
    }
    private <T> List<T> page(List<T> list,int page){int start=Math.max(0,page)*s().menuPageSize;return start>=list.size()?List.of():list.subList(start,Math.min(list.size(),start+s().menuPageSize));}
    private void pagination(SimpleForm form,int page,int size,Consumer<Integer> action){if(page>0)button(form,"previous",p->action.accept(page-1));if((page+1)*s().menuPageSize<size)button(form,"next",p->action.accept(page+1));back(form);}
    public void directory(Player p,int page){
        List<Faction> list=new ArrayList<>(e().snapshot().factions.values());list.sort(Comparator.comparingLong((Faction f)->f.bank).reversed().thenComparing(f->f.name));
        SimpleForm form=form("directory",s().text("ui.directory","count",list.size(),"page",page+1));
        for(Faction f:page(list,page))dynamic(form,s().text("ui.directory-entry","name",f.name,"members",f.members.size(),"bank",FactionEngine.money(f.bank)),q->info(q,f.id));
        pagination(form,page,list.size(),n->directory(p,n));form.send(p);
    }
    public void members(Player p,int page){
        Faction f=own(p);List<Map.Entry<String,Role>> list=new ArrayList<>(f.members.entrySet());list.sort(Map.Entry.<String,Role>comparingByValue().reversed());
        SimpleForm form=form("members",s().text("ui.member-count","count",list.size()));
        for(var member:page(list,page))dynamic(form,s().text("ui.member-entry","name",name(member.getKey()),"role",role(member.getValue()),"online",s().text(plugin.online().contains(member.getKey())?"online":"offline")),q->member(q,member.getKey()));
        if(e().allowed(id(p),f.id,Action.INVITE))button(form,"invite",q->input(q,"invite","ui.player-name","",value->{e().invite(id(q),e().findPlayer(value));plugin.tell(q,"invited");}));
        pagination(form,page,list.size(),n->members(p,n));form.send(p);
    }
    private void member(Player p,String target){
        Faction f=own(p);Role current=f.members.get(target);if(current==null)throw new IllegalArgumentException("not-member");SimpleForm form=form("member",s().text("ui.member-detail","name",name(target),"role",role(current)));
        if(f.members.get(id(p))==Role.LEADER&&!id(p).equals(target))for(Role next:Role.values())dynamic(form,s().text("ui.rank-action","role",role(next)),q->{
            if(next==Role.LEADER)confirm(q,"confirm-leader",()->{e().rank(id(q),target,next);dashboard(q);});else{e().rank(id(q),target,next);members(q,0);}
        });
        if(e().allowed(id(p),f.id,Action.KICK)&&!id(p).equals(target))button(form,"kick",q->confirm(q,"confirm-kick",()->{e().kick(id(q),target);members(q,0);}));
        button(form,"back",q->members(q,0));form.send(p);
    }
    public void claims(Player p){
        Faction f=own(p);SimpleForm form=form("claims",s().text("ui.claims","territory",plugin.territory(p),"world",p.getLevel().getName(),"x",Math.floorDiv(p.getFloorX(),16),"z",Math.floorDiv(p.getFloorZ(),16),"claims",e().lands(f.id),"capacity",e().capacity(f.id),"cost",FactionEngine.money(s().rules.claimCost)));
        if(e().allowed(id(p),f.id,Action.CLAIM)){button(form,"claim",q->{plugin.claim(q);claims(q);});button(form,"unclaim",q->confirm(q,"confirm-unclaim",()->{e().unclaim(id(q),NFactionsPlugin.key(q),false);claims(q);}));}
        button(form,"map",q->q.sendMessage(plugin.map(q)));if(s().feature("teleport"))button(form,"sethome",q->plugin.setHome(q,"home"));back(form);form.send(p);
    }
    public void warps(Player p){warps(p,0);}
    private void warps(Player p,int currentPage){
        if(!s().feature("teleport"))throw new IllegalArgumentException("feature-disabled");
        Faction f=own(p);SimpleForm form=form("warps",s().text("ui.warps"));
        List<String> warpNames=new ArrayList<>(new TreeSet<>(f.warps.keySet()));
        for(String name:page(warpNames,currentPage))dynamic(form,s().text("ui.warp-entry","name",name),q->{
            SimpleForm detail=form("warps",name);button(detail,"teleport",r->plugin.teleport(r,name));
            if(e().allowed(id(q),f.id,Action.CLAIM))button(detail,"delete",r->confirm(r,"confirm-delete",()->{e().deleteWarp(id(r),name);warps(r);}));
            button(detail,"back",this::warps);detail.send(q);
        });
        if(e().allowed(id(p),f.id,Action.CLAIM))button(form,"addwarp",q->input(q,"warps","ui.warp-name","",value->{plugin.setHome(q,value);warps(q);}));pagination(form,currentPage,warpNames.size(),n->warps(p,n));form.send(p);
    }
    public void bank(Player p){
        if(!s().feature("bank"))throw new IllegalArgumentException("feature-disabled");
        Faction f=own(p);String provider=plugin.integrations().economy().map(org.nvaultx.api.Economy::getName).orElse(s().text("economy-missing"));
        long pending=e().snapshot().transfers.values().stream().filter(t->t.faction.equals(f.id)&&t.status.equals("PENDING")).count();
        SimpleForm form=form("bank",s().text("ui.bank","balance",FactionEngine.money(f.bank),"provider",provider,"pending",pending));
        button(form,"deposit",q->input(q,"bank","ui.amount","",value->{plugin.integrations().transfer(q,value,false);bank(q);}));
        if(e().allowed(id(p),f.id,Action.WITHDRAW))button(form,"withdraw",q->input(q,"bank","ui.amount","",value->{plugin.integrations().transfer(q,value,true);bank(q);}));back(form);form.send(p);
    }
    public void settings(Player p){
        Faction f=own(p);if(!e().allowed(id(p),f.id,Action.SETTINGS))throw new IllegalArgumentException("rank-denied");AtomicBoolean used=new AtomicBoolean();
        new CustomForm(s().menu("theme.title-prefix")+s().menu("titles.settings"))
                .addInput(s().text("ui.faction-name"),"",f.name).addInput(s().text("ui.description"),"",f.description).addInput(s().text("ui.motd"),"",f.motd)
                .addToggle(s().text("ui.open"),f.open).addToggle(s().text("ui.allies-interact"),f.alliesInteract)
                .submitButton(s().menu("buttons.submit")).onSubmit((q,r)->{if(used.compareAndSet(false,true))plugin.act(q,()->{
                    e().configure(id(q),r.getInputResponse(0).trim(),r.getInputResponse(1),r.getInputResponse(2),r.getToggleResponse(3),r.getToggleResponse(4));dashboard(q);
                });}).send(p);
    }
    public void permissions(Player p){
        permissions(p,0);
    }
    private void permissions(Player p,int currentPage){
        Faction f=own(p);if(f.members.get(id(p))!=Role.LEADER)throw new IllegalArgumentException("leader-only");SimpleForm form=form("permissions",s().text("ui.permissions"));
        for(Action action:page(Arrays.asList(Action.values()),currentPage))dynamic(form,s().text("ui.permission-entry","action",s().text("action."+action),"role",role(f.permissions.getOrDefault(action,s().rules.defaults.get(action)))),q->{
            SimpleForm choice=form("permissions",s().text("action."+action));for(Role role:Role.values())dynamic(choice,this.role(role),r->{e().permission(id(r),action,role);permissions(r);});button(choice,"back",this::permissions);choice.send(q);
        });pagination(form,currentPage,Action.values().length,n->permissions(p,n));form.send(p);
    }
    public void relations(Player p,int page){
        if(!s().feature("diplomacy"))throw new IllegalArgumentException("feature-disabled");
        Faction f=own(p);List<Faction> list=e().snapshot().factions.values().stream().filter(other->!other.id.equals(f.id)).sorted(Comparator.comparing(other->other.name)).toList();
        SimpleForm form=form("relations",s().text("ui.relations"));
        for(Faction other:page(list,page))dynamic(form,s().text("ui.relation-entry","name",other.name,"relation",s().text("relation."+e().relation(f.id,other.id)),"request",f.allyRequests.contains(other.id)?s().text("ui.ally-request"):""),q->{
            SimpleForm detail=form("relations",other.name);for(Relation wish:Relation.values())dynamic(detail,s().text("relation."+wish),r->{plugin.execute(r,new String[]{wish==Relation.ALLY?"ally":wish==Relation.ENEMY?"enemy":"neutral",other.id});relations(r,0);});button(detail,"back",r->relations(r,page));detail.send(q);
        });pagination(form,page,list.size(),n->relations(p,n));form.send(p);
    }
    public void upgrades(Player p){
        if(!s().feature("upgrades"))throw new IllegalArgumentException("feature-disabled");
        Faction f=own(p);SimpleForm form=form("upgrades",s().text("ui.upgrades","balance",FactionEngine.money(f.bank)));
        for(String upgrade:List.of("members","claims","warps")){int level=f.upgrades.getOrDefault(upgrade,0);long cost=s().rules.upgradeCost*(level+1L);
            dynamic(form,s().text("ui.upgrade-entry","name",s().text("upgrade."+upgrade),"level",level,"max",s().rules.maxUpgrade,"cost",FactionEngine.money(cost)),q->confirm(q,"confirm-upgrade",()->{plugin.execute(q,new String[]{"upgrade",upgrade});upgrades(q);}));
        }back(form);form.send(p);
    }
    public void audit(Player p,int page){
        Faction f=own(p);if(!e().allowed(id(p),f.id,Action.SETTINGS))throw new IllegalArgumentException("rank-denied");List<String> lines=new ArrayList<>(f.audit);Collections.reverse(lines);
        SimpleForm form=form("audit",String.join("\n",page(lines,page)));pagination(form,page,lines.size(),n->audit(p,n));form.send(p);
    }
}
