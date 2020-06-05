package io.github.cccm5;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.MovecraftLocation;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
public class SquadronDirectorMain extends JavaPlugin implements Listener {
    public static final String ERROR_TAG = ChatColor.RED + "Error: " + ChatColor.DARK_RED;
    public static final String SUCCESS_TAG = ChatColor.DARK_AQUA + "Cargo: " + ChatColor.WHITE;
    public static Logger logger;
    private static ArrayList<Player> playersInQue;
    private static double unloadTax,loadTax;
    private static SquadronDirectorMain instance;
    private static int delay;//ticks
    private static boolean isPre1_13 = false;
    private static Material SIGN_POST = Material.getMaterial("SIGN_POST");
    private CraftManager craftManager;
    private FileConfiguration config;
    private boolean cardinalDistance;
    private static boolean debug;
    private double scanRange;

    public static boolean isIsPre1_13() {
        return isPre1_13;
    }

    public void onEnable() {
        logger = this.getLogger();
        this.getServer().getPluginManager().registerEvents(this, this);
        playersInQue = new ArrayList<Player>();
        instance = this;
        //************************
        //* Check server version *
        //************************
        String packageName = getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        String[] parts = version.split("_");
        isPre1_13 = Integer.parseInt(parts[1]) < 13;
        //************************
        //*       Configs        *
        //************************
        config = getConfig();
        config.addDefault("Scan range",100.0);
        config.addDefault("Transfer delay ticks",300);
        config.addDefault("Load tax percent", 0.01D);
        config.addDefault("Unload tax percent", 0.01D);
        config.addDefault("Cardinal distance",true);
        config.addDefault("Debug mode",false);
        config.options().copyDefaults(true);
        this.saveConfig();
        scanRange = config.getDouble("Scan range") >= 1.0 ? config.getDouble("Scan range") : 100.0;
        delay = config.getInt("Transfer delay ticks");
        loadTax = config.getDouble("Load tax percent")<=1.0 && config.getDouble("Load tax percent")>=0.0 ? config.getDouble("Load tax percent") : 0.01;
        unloadTax = config.getDouble("Unload tax percent")<=1.0 && config.getDouble("Unload tax percent")>=0.0 ? config.getDouble("Unload tax percent") : 0.01;
        cardinalDistance = config.getBoolean("Cardinal distance");
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
    }

    public void onDisable() {
        logger = null;
        instance = null;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { // Plugin
        if (command.getName().equalsIgnoreCase("unload")) {
            if(!(sender instanceof Player)){
                sender.sendMessage(ERROR_TAG + "You need to be a player to execute that command!");
                return true;
            }
            unload((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("load")) {
            if(!(sender instanceof Player)){
                sender.sendMessage(ERROR_TAG + "You need to be a player to execute that command!");
                return true;
            }
            load((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("cargo")) {
            if(!sender.hasPermission("Cargo.cargo")){
                sender.sendMessage(ERROR_TAG + "You don't have permission to do that!");
                return true;
            }
            sender.sendMessage(ChatColor.WHITE + "--[ " + ChatColor.DARK_AQUA + "  Movecraft Cargo " + ChatColor.WHITE + " ]--");
            sender.sendMessage(ChatColor.DARK_AQUA + "Scan Range: " + ChatColor.WHITE + scanRange + " Blocks");
            sender.sendMessage(ChatColor.DARK_AQUA + "Transfer Delay: " + ChatColor.WHITE + delay + " ticks");
            sender.sendMessage(ChatColor.DARK_AQUA + "Unload Tax: " + ChatColor.WHITE + String.format("%.2f",100*unloadTax) + "%");
            sender.sendMessage(ChatColor.DARK_AQUA + "Load Tax: " + ChatColor.WHITE + String.format("%.2f",100*loadTax) + "%");
            if(cardinalDistance)
                sender.sendMessage(ChatColor.DARK_AQUA + "Distance Type: " + ChatColor.WHITE + "Cardinal");
            else
                sender.sendMessage(ChatColor.DARK_AQUA + "Distance Type: " + ChatColor.WHITE + "Direct");
            return true;
        }
        return false;

    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!e.getClickedBlock().getType().name().equals("SIGN_POST") && !e.getClickedBlock().getType().name().endsWith("SIGN") && !e.getClickedBlock().getType().name().endsWith("WALL_SIGN")) {
            return;
        }
        Sign sign = (Sign) e.getClickedBlock().getState();
        if (sign.getLine(0).equals(ChatColor.DARK_AQUA + "[UnLoad]")) {
            unload(e.getPlayer());
            return;
        }
        if (sign.getLine(0).equals(ChatColor.DARK_AQUA + "[Load]")) {
            load(e.getPlayer());
        }

    }

    @EventHandler
    public void onSignPlace(SignChangeEvent e){
        if(!e.getBlock().getType().name().equals("SIGN_POST") && !e.getBlock().getType().name().endsWith("SIGN") && !e.getBlock().getType().name().endsWith("WALL_SIGN")){
            return;
        }
        if(ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("[Load]") || ChatColor.stripColor(e.getLine(0)).equalsIgnoreCase("[UnLoad]")){
            e.setLine(0,ChatColor.DARK_AQUA + (ChatColor.stripColor(e.getLine(0))).replaceAll("u","U").replaceAll("l","L"));
        }

    }

    public static boolean isDebug(){
        return debug;
    }

    public static List<Player> getQue(){
        return playersInQue;
    }

    public static double getLoadTax(){
        return loadTax;
    }

    public static double getUnloadTax(){
        return unloadTax;
    }

    public static int getDelay(){
        return delay;
    }

    public static SquadronDirectorMain getInstance(){
        return instance;
    }

    private void unload(Player player){

        if(!player.hasPermission("Cargo.unload")){
            player.sendMessage(ERROR_TAG + "You don't have permission to do that!");
            return;
        }
        Craft playerCraft = craftManager.getCraftByPlayer(player);
        if(playersInQue.contains(player)){
            player.sendMessage(ERROR_TAG + "You're already moving cargo!");
            return;
        }

        if(playerCraft == null){
            player.sendMessage(ERROR_TAG + "You need to be piloting a craft to do that!");
            return;
        }

        if(player.getInventory().getItemInMainHand() == null || player.getInventory().getItemInMainHand().getType() == Material.AIR){
            player.sendMessage(ERROR_TAG + "You need to be holding a cargo item to do that!");
            return;
        }
    }

    private void load(Player player){
        if(!player.hasPermission("Cargo.load")){
            player.sendMessage(ERROR_TAG + "You don't have permission to do that!");
            return;
        }
        Craft playerCraft = craftManager.getCraftByPlayer(player);
        if(playersInQue.contains(player)){
            player.sendMessage(ERROR_TAG + "You're already moving cargo!");
            return;
        }

        if(playerCraft == null){
            player.sendMessage(ERROR_TAG + "You need to be piloting a craft to do that!");
            return;
        }

    }

}
