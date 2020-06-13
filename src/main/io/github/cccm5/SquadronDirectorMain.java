package io.github.cccm5;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.commands.MovecraftCommand;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.craft.ICraft;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BitmapHitBox;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
public class SquadronDirectorMain extends JavaPlugin implements Listener {
public static Logger logger;
public static final String ERROR_TAG = ChatColor.RED + "Error: " + ChatColor.DARK_RED;
public static final String SUCCESS_TAG = ChatColor.DARK_AQUA + "Squadron Director: " + ChatColor.WHITE;
private ArrayList<Player> playersInLaunchMode;
private ConcurrentHashMap<Player, CopyOnWriteArrayList<Craft>> directedCrafts;
private static SquadronDirectorMain instance;
private static Material SIGN_POST = Material.getMaterial("SIGN_POST");
private CraftManager craftManager;
private FileConfiguration config;
private boolean cardinalDistance;
private static boolean debug;


    public void onEnable() {
        logger = this.getLogger();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("sdrelease").setExecutor(this);
        this.getCommand("sdlaunch").setExecutor(this);
        this.getCommand("sdcruise").setExecutor(this);
        this.getCommand("sdrotate").setExecutor(this);
        this.getCommand("sdlever").setExecutor(this);
        this.getCommand("sdbutton").setExecutor(this);
        this.getCommand("sdtorp").setExecutor(this);
        playersInLaunchMode = new ArrayList<Player>();
        directedCrafts = new ConcurrentHashMap<Player, CopyOnWriteArrayList<Craft>>();
        instance = this;
        //************************
        //* Check server version *
        //************************
        String packageName = getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        String[] parts = version.split("_");

        //************************
        //*       Configs        *
        //************************
        config = getConfig();
        config.addDefault("Max crafts",24);
        config.addDefault("Craft types", Arrays.asList("Airskiff","Subairskiff"));
        config.addDefault("Debug mode",false);
        config.options().copyDefaults(true);
        this.saveConfig();
        debug = config.getBoolean("Debug mode");
        //************************
        //*    Load Movecraft    *
        //************************
        if(getServer().getPluginManager().getPlugin("Movecraft") == null || !getServer().getPluginManager().getPlugin("Movecraft").isEnabled()) {
            logger.log(Level.SEVERE, "Movecraft not found or not enabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        craftManager = CraftManager.getInstance();

        // update the directed crafts every 10 seconds, or Movecraft will remove them due to inactivity
        new BukkitRunnable() {
            @Override
            public void run() {
                for(CopyOnWriteArrayList<Craft> cl : directedCrafts.values()) {
                    for (Craft c : cl) {
                        if (System.currentTimeMillis() - c.getLastCruiseUpdate() > 10000) {
                            c.setLastCruiseUpdate(System.currentTimeMillis());
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(SquadronDirectorMain.getInstance(),20,20);
    }

    public void onDisable() {
        logger = null;
        instance = null;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { // Plugin
        if(!(sender instanceof Player)){
            sender.sendMessage(ERROR_TAG + "You need to be a player to execute that command!");
            return true;
        }
        Player player= (Player) sender;

        if (command.getName().equalsIgnoreCase("sdrelease")) {

            if(!player.hasPermission("Squadron.command.release")) {
                player.sendMessage(ERROR_TAG + "You do not have permissions to execute that command!");
                return true;
            }
            releaseSquadrons(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sdlaunch")) {

            if(!player.hasPermission("Squadron.command.launch")) {
                player.sendMessage(ERROR_TAG + "You do not have permissions to execute that command!");
                return true;
            }
            launchModeToggle(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sdcruise")) {

            if(!player.hasPermission("Squadron.command.cruise")) {
                player.sendMessage(ERROR_TAG + "You do not have permissions to execute that command!");
                return true;
            }
//            cruiseToggle(player);
            return true;
        }
        return false;

    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        Player player=e.getPlayer();
        if (!(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction()==Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        if (!e.getClickedBlock().getType().name().equals("SIGN_POST") && !e.getClickedBlock().getType().name().endsWith("SIGN") && !e.getClickedBlock().getType().name().endsWith("WALL_SIGN")) {
            return;
        }
        Sign sign = (Sign) e.getClickedBlock().getState();
        boolean signIsOnCraft=false;
        Craft craftSignIsOn=null;
        MovecraftLocation mloc = MathUtils.bukkit2MovecraftLoc(e.getClickedBlock().getLocation());
        CraftManager.getInstance().getCraftsInWorld(e.getClickedBlock().getWorld());
        for (Craft craft : CraftManager.getInstance().getCraftsInWorld(e.getClickedBlock().getWorld())) {
            if (craft == null || craft.getDisabled()) {
                continue;
            }
            for (MovecraftLocation tloc : craft.getHitBox()) {
                if (tloc.equals(mloc)) {
                    signIsOnCraft=true;
                    craftSignIsOn=craft;
                }
            }
        }

        if (sign.getLine(0).equals(ChatColor.DARK_AQUA + "SquadronDirector")) {
            if(!signIsOnCraft) {
                player.sendMessage(ERROR_TAG + "The command sign is not on a piloted craft!");
                return;
            }

            if(sign.getLine(1).equals(ChatColor.DARK_BLUE + "[Release]")) {
                if(!player.hasPermission("Squadron.command.release")) {
                    player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                    return;
                }
                releaseSquadrons(player);
                e.setCancelled(true);
                return;
            }

            if(sign.getLine(1).equals(ChatColor.DARK_BLUE + "[Launch]")) {
                if(!player.hasPermission("Squadron.command.launch")) {
                    player.sendMessage(ERROR_TAG + "You do not have permissions to use that sign!");
                    return;
                }
                launchModeToggle(player);
                e.setCancelled(true);
                return;
            }

            player.sendMessage(ERROR_TAG + "Squadron Director command not recognized!");
            return;
        }
        if(playersInLaunchMode.contains(e.getPlayer()) && e.getAction()==Action.LEFT_CLICK_BLOCK) {
            if(signIsOnCraft) {
                if(CraftManager.getInstance().getPlayerFromCraft(craftSignIsOn)==null) {
                    player.sendMessage(ERROR_TAG + "This craft is already being directed!");
                    return;
                }
            }
            String foundCraft = null;
            for(String craftName : config.getStringList("Craft types")) {
                if(sign.getLine(0).equalsIgnoreCase(craftName)) {
                    foundCraft = craftName;
                }
            }
            if(foundCraft!=null) {
                if(!player.hasPermission("Squadron."+foundCraft)) {
                    player.sendMessage(ERROR_TAG + "You do not have permissions to direct that craft type!");
                    return;
                }

            // now try to detect the craft
            Location loc = e.getClickedBlock().getLocation();
            MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            CraftType type = CraftManager.getInstance().getCraftTypeFromString(foundCraft);
            final Craft c = new ICraft(type, loc.getWorld());

            if (c.getType().getCruiseOnPilot()) {
                player.sendMessage(ERROR_TAG + "You can not direct a CruiseOnPilot craft!");
                return;
            }

            // determine the cruise direction of the craft, release it if there is no cruise sign
            Craft finalCraftSignIsOn = craftSignIsOn;
            new BukkitRunnable() {
                @Override
                public void run() {
                    determineCruiseDirection(c);
                }
            }.runTaskLater(SquadronDirectorMain.getInstance(), (10));


            if(signIsOnCraft) { // stop the parent craft from moving during detection, and remove the child craft from the parent to prevent overlap
                craftSignIsOn.setProcessing(true);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        BitmapHitBox parentHitBox= finalCraftSignIsOn.getHitBox();
                        parentHitBox.removeAll(c.getHitBox());
                    }
                }.runTaskLater(SquadronDirectorMain.getInstance(), (10));
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        finalCraftSignIsOn.setProcessing(false);
                    }
                }.runTaskLater(SquadronDirectorMain.getInstance(), (20));
            }
            c.detect(null, e.getPlayer(), startPoint);
            Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(c, CraftPilotEvent.Reason.PLAYER));

            CopyOnWriteArrayList <Craft>cl=directedCrafts.get(player);
            if(cl==null) {
                cl=new CopyOnWriteArrayList <Craft>();
            }
            cl.add(c);
            directedCrafts.put(player,cl);
            c.setLastCruiseUpdate(System.currentTimeMillis());
            player.sendMessage(SUCCESS_TAG+"You have attempted to launch a craft of type "+foundCraft);

            e.setCancelled(true);
            return;
            }
        }
    }

    @EventHandler
    public void onSignPlace(SignChangeEvent e){
        if(!e.getBlock().getType().name().equals("SIGN_POST") && !e.getBlock().getType().name().endsWith("SIGN") && !e.getBlock().getType().name().endsWith("WALL_SIGN")){
            return;
        }
        if(ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("SquadronDirector") ){
            e.setLine(0,ChatColor.DARK_AQUA + "SquadronDirector");
            if(ChatColor.stripColor(e.getLine(1)).equalsIgnoreCase("[release]") ){
                e.setLine(1,ChatColor.DARK_BLUE + "[Release]");
            } else if (ChatColor.stripColor(e.getLine(1)).equalsIgnoreCase("[launch]") ){
                e.setLine(1,ChatColor.DARK_BLUE + "[Launch]");
            } else if (ChatColor.stripColor(e.getLine(1)).equalsIgnoreCase("[Cruise]") ){
                e.setLine(1,ChatColor.DARK_BLUE + "[Cruise]");
            } else {
                e.getPlayer().sendMessage(ERROR_TAG + "Squadron Director command not recognized!");
            }

        }

    }

    public static boolean isDebug(){
        return debug;
    }

    public static SquadronDirectorMain getInstance(){
        return instance;
    }

    public void releaseSquadrons(Player player){
        if(directedCrafts.get(player)==null) {
            player.sendMessage(ERROR_TAG+"You have no squadron craft to release");
            return;
        }
        int numCraft=0;
        for(Craft c : directedCrafts.get(player)) {
            CraftManager.getInstance().removeCraft(c);
            numCraft++;
        }
        if(numCraft>1) {
            player.sendMessage(SUCCESS_TAG+"You have released "+numCraft+" squadron crafts");
        } else if(numCraft>0) {
            player.sendMessage(SUCCESS_TAG+"You have released "+numCraft+" squadron craft");
        } else {
            player.sendMessage(ERROR_TAG+"You have no squadron craft to release");
        }
    }

    public void launchModeToggle(Player player){
        if(playersInLaunchMode.contains(player)) {
            playersInLaunchMode.remove(player);
            player.sendMessage(SUCCESS_TAG+"You have left Launch Mode.");
        } else {
            playersInLaunchMode.add(player);
            player.sendMessage(SUCCESS_TAG+"You have entered Launch Mode. Left click crafts you wish to direct.");
        }
        return;
    }

    public void determineCruiseDirection(Craft craft) {
        if(craft==null)
            return;
        boolean foundCruise=false;
        for(MovecraftLocation tLoc : craft.getHitBox()) {
            BlockState state = craft.getW().getBlockAt(tLoc.getX(), tLoc.getY(), tLoc.getZ()).getState();
            if (state instanceof Sign) {
                Sign s=(Sign) state;
                if(s.getLine(0).equalsIgnoreCase("Cruise: OFF") || s.getLine(0).equalsIgnoreCase("Cruise: ON")) {
                    craft.setCruiseDirection(s.getRawData());
                    foundCruise=true;
                }
            }
        }
        if(!foundCruise) {
            craft.getNotificationPlayer().sendMessage(ERROR_TAG+"This craft has no Cruise sign and can not be directed");
            CraftManager.getInstance().removeCraft(craft);
        }
    }
}
