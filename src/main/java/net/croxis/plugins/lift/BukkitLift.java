/*
 * This file is part of Lift.
 *
 * Copyright (c) ${project.inceptionYear}-2012, croxis <https://github.com/croxis/>
 *
 * Lift is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lift is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lift. If not, see <http://www.gnu.org/licenses/>.
 */
package net.croxis.plugins.lift;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;

import net.h31ix.anticheat.api.AnticheatAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.*;

public class BukkitLift extends JavaPlugin implements Listener {
	public static boolean debug = false;
	boolean useSpout = false;
	//public double liftSpeed = 0.5;
	public static boolean redstone = true;
	public static int liftArea = 16;
	public static int maxHeight = 256;
	//public Material baseMaterial = Material.IRON_BLOCK;
	public HashMap<Material, Double> blockSpeeds = new HashMap<Material, Double>();
	public Material floorBlock = Material.GLASS;
	public boolean autoPlace = false;
	public boolean checkGlass = false;
	public boolean serverFlight = false;
	//public boolean useV10 = false;
	//public V10verlap_API v10verlap_API = null;
	public static BukkitElevatorManager manager;
	public boolean useAntiCheat = false;
	public AnticheatAPI anticheat = null;
	private boolean preventEntry = false;
	private boolean preventLeave = false;
	public static String stringDestination;
	public static String stringCurrentFloor;
	public static String stringOneFloor;
	public static String stringCantEnter;
	public static String stringCantLeave;
	
	public void logDebug(String message){
		if (debug)
			this.getLogger().log(Level.INFO, "[DEBUG] " + message);
	}
	
	public void logInfo(String message){
		this.getLogger().log(Level.INFO, message);
	}
	
    public void onDisable() {
    	BukkitElevatorManager.bukkitElevators.clear();
    	getServer().getScheduler().cancelTask(BukkitElevatorManager.taskid);
        System.out.println(this + " is now disabled!");
    }

    public void onEnable() {
    	new BukkitLiftRedstoneListener(this);
    	new BukkitLiftPlayerListener(this);
    	manager = new BukkitElevatorManager(this);
    	
    	//liftSpeed = this.getConfig().getDouble("liftSpeed");
    	this.getConfig().options().copyDefaults(true);
    	liftArea = this.getConfig().getInt("maxLiftArea");
    	maxHeight = this.getConfig().getInt("maxHeight");
    	debug = this.getConfig().getBoolean("debug");
    	//baseMaterial = Material.valueOf(this.getConfig().getString("baseBlock", "IRON_BLOCK"));
    	autoPlace = this.getConfig().getBoolean("autoPlace");
    	checkGlass = this.getConfig().getBoolean("checkGlass");
    	preventEntry = this.getConfig().getBoolean("preventEntry", false);
    	preventLeave = this.getConfig().getBoolean("preventLeave", false);
    	redstone = this.getConfig().getBoolean("redstone", false);
    	Set<String> baseBlockKeys = this.getConfig().getConfigurationSection("baseBlockSpeeds").getKeys(false);
    	for (String key : baseBlockKeys){
    		blockSpeeds.put(Material.valueOf(key), this.getConfig().getDouble("baseBlockSpeeds." + key));
    	}
    	floorBlock = Material.valueOf(getConfig().getString("floorBlock"));
    	stringOneFloor = getConfig().getString("STRING_oneFloor", "There is only one floor silly.");
    	stringCurrentFloor = getConfig().getString("STRING_currentFloor", "Current Floor:");
    	stringDestination = getConfig().getString("STRING_dest", "Dest:");
    	stringCantEnter = getConfig().getString("STRING_cantEnter", "Can't enter elevator in use");
    	stringCantLeave = getConfig().getString("STRING_cantLeave", "Can't leave elevator in use");
    	
        saveConfig();
        
        serverFlight = this.getServer().getAllowFlight();
        
        Plugin test = getServer().getPluginManager().getPlugin("Spout");
        
        if (preventEntry || preventLeave){
        	Bukkit.getServer().getPluginManager().registerEvents(this, this);
        }
        
        if(test != null) {
        	useSpout = true;
        	System.out.println(this + " detected Spout!");
        }
        
        if(getServer().getPluginManager().getPlugin("AntiCheat") != null)
        {
          useAntiCheat = true;
        }
        if (debug){
			System.out.println("maxArea: " + Integer.toString(liftArea));
			System.out.println("autoPlace: " + Boolean.toString(autoPlace));
			System.out.println("checkGlass: " + Boolean.toString(checkGlass));
			System.out.println("baseBlocks: " + blockSpeeds.toString());
		}
        
        try {
            BukkitMetrics bukkitMetrics = new BukkitMetrics(this);
            bukkitMetrics.start();
        } catch (IOException e) {
            // Failed to submit the stats :-(
        }
        
        System.out.println(this + " is now enabled!");
    }
    
    /*public void removeLift(Elevator elevator){
    	
    }*/
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event){
		for (BukkitElevator bukkitElevator : BukkitElevatorManager.bukkitElevators){
			if (bukkitElevator.chunks.contains(event.getTo().getChunk())){
				if (bukkitElevator.isInShaft(event.getPlayer()) 
						&& !bukkitElevator.isInLift(event.getPlayer())
						&& preventEntry){
					event.setCancelled(true);
					event.getPlayer().sendMessage(BukkitLift.stringCantEnter);
				} else if (!bukkitElevator.isInShaft(event.getPlayer())
						&& bukkitElevator.isInLift(event.getPlayer())
						&& preventLeave){
					event.setCancelled(true);
					event.getPlayer().sendMessage(BukkitLift.stringCantLeave);
				}
			}
		}
	}
	
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	if(cmd.getName().equalsIgnoreCase("lift") && sender instanceof Player){ // If the player typed /basic then do the following...
    		long time = System.currentTimeMillis();
    		Player player = (Player) sender;
    		player.sendMessage("Starting scan");
    		BukkitElevator bukkitElevator = new BukkitElevator();
    		//Location location = player.getLocation();
    		//location.setY(location.getY() - 2);
    		if (BukkitElevatorManager.isBaseBlock(player.getLocation().getBlock().getRelative(BlockFace.DOWN))){
    			bukkitElevator.baseBlockType = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
    			BukkitElevatorManager.scanBaseBlocks(player.getLocation().getBlock().getRelative(BlockFace.DOWN), bukkitElevator);
    		} else {
    			player.sendMessage("Not a valid base block type: " + player.getLocation().getBlock().getType().toString());
    			player.sendMessage("Options are: " + this.blockSpeeds.toString());
    			return true;
    		}
    		player.sendMessage("Base block type: " + bukkitElevator.baseBlockType + " | Size: " + Integer.toString(bukkitElevator.baseBlocks.size()));
    		player.sendMessage("Floor scan reports: " + BukkitElevatorManager.constructFloors(bukkitElevator));
    		player.sendMessage("Total time generating elevator: " + Long.toString(System.currentTimeMillis() - time));
    		return true;
    	} //If this has happened the function will break and return true. if this hasn't happened the a value of false will be returned.
    	return false; 
    }


}
