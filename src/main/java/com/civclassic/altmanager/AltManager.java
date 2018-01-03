package com.civclassic.altmanager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;

import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.namelayer.NameAPI;

public class AltManager extends ACivMod implements Listener {

	private int maxImprisoned = 1;
	private String kickMessage;
	private Map<UUID, Set<UUID>> exceptions = new HashMap<UUID, Set<UUID>>();
	private Map<UUID, UUID> mains = new HashMap<UUID, UUID>();;
	private ImprisonmentHandler prisonHandler;
	
	public void onEnable() {
		if(Bukkit.getPluginManager().isPluginEnabled("ExilePearl")) {
			prisonHandler = new ExilePearlHandler(this);
		} else if(Bukkit.getPluginManager().isPluginEnabled("PrisonPearl")) {
			prisonHandler = new PrisonPearlHandler(this);
		} else {
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}
		saveDefaultConfig();
		reloadConfig();
		maxImprisoned = getConfig().getInt("maxImprisoned", maxImprisoned);
		kickMessage = getConfig().getString("kickMessage", "You have too many imprisoned alts, message modmail if you think this is an error.");
		loadExceptions();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(prisonHandler, this);
	}
	
	public String getPluginName() {
		return "AltManager";
	}
	
	private void loadExceptions() {
		exceptions.clear();
		mains.clear();
		File exceptionsFile = new File(getDataFolder(), "exceptions.yml");
		if(!exceptionsFile.exists()) {
			try {
				exceptionsFile.createNewFile();
			} catch (IOException e) {}
		}
		YamlConfiguration exceptionsYaml = YamlConfiguration.loadConfiguration(exceptionsFile);
		ConfigurationSection exceptionSection = exceptionsYaml.getConfigurationSection("exceptions");
		ConfigurationSection mainsSection = exceptionsYaml.getConfigurationSection("mains");
		for(String main : mainsSection.getKeys(false)) {
			for(String alt : mainsSection.getStringList(main)) {
				mains.put(NameAPI.getUUID(alt), NameAPI.getUUID(main));
			}
		}
		for(String main : exceptionSection.getKeys(false)) {
			Set<UUID> excepts = new HashSet<UUID>();
			for(String name : exceptionSection.getStringList(main)) {
				excepts.add(NameAPI.getUUID(name));
			}
			exceptions.put(NameAPI.getUUID(main), excepts);
		}
	}
	
	@EventHandler
	public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
		Set<UUID> alts = getAlts(event.getUniqueId());
		for(UUID alt : alts) {
			BSPlayer bsp = BSPlayer.byUUID(alt);
			if(bsp != null) {
				BSBan ban = bsp.getBan();
				if(ban != null) {
					event.disallow(Result.KICK_BANNED, "You are banned, message modmail if you think this is an error");
				}
			}
		}
		int count = prisonHandler.getImprisonedCount(alts, 1);
		getLogger().info("Imprisoned count for " + event.getUniqueId() + ": " + count);
		if(count >= maxImprisoned) {
			if(!prisonHandler.isImprisoned(event.getUniqueId())) {
				event.disallow(Result.KICK_BANNED, kickMessage);
			}
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equals("altstats")) {
			UUID most = null;
			int size = 0;
			for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
				BSPlayer bsp = BSPlayer.byUUID(player.getUniqueId());
				if(bsp != null) {
					int shares = bsp.getUnpardonedShareCardinality();
					if(shares > size) {
						size = shares;
						most = player.getUniqueId();
					}
				}
			}
			sender.sendMessage(ChatColor.GOLD + "Player with the most alts is " + NameAPI.getCurrentName(most) + " with " + size + " alts");
			Set<UUID> accounts = new HashSet<UUID>();
			for(Player player : Bukkit.getOnlinePlayers()) {
				accounts.add(player.getUniqueId());
			}
			for(Player player : Bukkit.getOnlinePlayers()) {
				Set<UUID> alts = getAlts(player.getUniqueId());
				alts.remove(player.getUniqueId());
				accounts.removeAll(alts);
			}
			sender.sendMessage(ChatColor.GOLD + "" + accounts.size() + "/" + Bukkit.getOnlinePlayers().size() + " players are unique");
			return true;
		}
		if(label.equals("reloadexceptions")) {
			loadExceptions();
			sender.sendMessage(ChatColor.GREEN + "Exceptions reloaded!");
			return true;
		}
		if(args.length < 1) {
			sender.sendMessage(ChatColor.RED + "Please specify a player to check alts for");
		} else {
			String player = args[0];
			UUID id;
			try {
				id = UUID.fromString(player);
			} catch (IllegalArgumentException ex) {
				id = NameAPI.getUUID(player);
			}
			String name = NameAPI.getCurrentName(id);
			Set<UUID> alts = getAlts(id);
			if(alts.size() == 0) {
				boolean pearled = prisonHandler.isImprisoned(id);
				sender.sendMessage((pearled ? ChatColor.DARK_AQUA : (Bukkit.getOfflinePlayer(id).isOnline() ? ChatColor.GREEN : ChatColor.WHITE)) + name + ChatColor.RESET + " has no alts");
				return true;
			}
			StringBuilder msgBuilder = new StringBuilder(ChatColor.GOLD + "Alts for " + name + ": ");
			for(UUID user : alts) {
				Player p = Bukkit.getPlayer(user);
				boolean pearled = prisonHandler.isImprisoned(user);
				msgBuilder.append(pearled ? ChatColor.DARK_AQUA : ((p != null && p.isOnline()) ? ChatColor.GREEN : ChatColor.WHITE)).append(NameAPI.getCurrentName(user)).append(", ");
			}
			String msg = msgBuilder.toString();
			sender.sendMessage(msg.substring(0, msg.length() - 2));
		}
		return true;
	}
	
	private Map<UUID, Set<UUID>> checked = new ConcurrentHashMap<UUID, Set<UUID>>();
	
	Set<UUID> getAlts(UUID player) {
		if(exceptions.containsKey(player) || mains.containsKey(player)) {
			UUID main = exceptions.containsKey(player) ? player : mains.get(player);
			return exceptions.get(main);
		}
		checked.put(player, new HashSet<UUID>());
		Set<UUID> shares = getShares(player, player);
		UUID main = exceptions.containsKey(player) ? player : mains.get(player);
		if(main != null) {
			shares.remove(exceptions.get(main));
		}
		checked.remove(player);
		return shares;
	}
	
	void kickPlayer(Player player) {
		player.kickPlayer(kickMessage);
	}
	
	int getMaxImprisoned() {
		return maxImprisoned;
	}
	
	private Set<UUID> getShares(UUID main, UUID player) {
		checked.get(main).add(player);
		BSPlayer bsp = BSPlayer.byUUID(player);
		Set<UUID> shares = new HashSet<UUID>();
		if(bsp != null) {
			for(BSShare share : bsp.getUnpardonedShares()) {
				UUID one = share.getFirstPlayer().getUUID();
				UUID two = share.getSecondPlayer().getUUID();
				if(!checked.get(main).contains(one)) {
					shares.addAll(getShares(main, one));
				}
				if(!checked.get(main).contains(two)) {
					shares.addAll(getShares(main, two));
				}
				shares.add(one);
				shares.add(two);
			}
		}
		return shares;
	}
}
