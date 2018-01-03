package com.civclassic.altmanager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import com.devotedmc.ExilePearl.ExilePearlPlugin;
import com.devotedmc.ExilePearl.event.PlayerPearledEvent;

public class ExilePearlHandler extends ImprisonmentHandler {

	ExilePearlHandler(AltManager plugin) {
		super(plugin);
	}

	@EventHandler
	public void onExilePearl(PlayerPearledEvent event) {
		UUID imprisoned = event.getPearl().getPlayerId();
		Set<UUID> alts = plugin.getAlts(imprisoned);
		int count = getImprisonedCount(alts, 1);
		if(count < plugin.getMaxImprisoned()) {
			return;
		}
		alts.remove(imprisoned);
		for(UUID id : alts) {
			Player player = Bukkit.getPlayer(id);
			if(player != null && player.isOnline()) {
				plugin.kickPlayer(player);
			}
		}
	}
	
	@Override
	public int getImprisonedCount(Set<UUID> alts) {
		Set<UUID> imprisoned = new HashSet<UUID>();
		for(UUID alt : alts) {
			if(ExilePearlPlugin.getApi().isPlayerExiled(alt)) {
				imprisoned.add(alt);
			}
		}
		return imprisoned.size();
	}
	
	@Override
	public int getImprisonedCount(Set<UUID> alts, int pearltype) {
		Set<UUID> imprisoned = new HashSet<UUID>();
		for(UUID alt : alts) {
  			if(ExilePearlPlugin.getApi().isPlayerExiled(alt) && (ExilePearlPlugin.getApi().getPearl(alt).getPearlType().toInt() == pearltype)) {
				imprisoned.add(alt);
			}
		}
		return imprisoned.size();
	}

	@Override
	public boolean isImprisoned(UUID player) {
		return ExilePearlPlugin.getApi().isPlayerExiled(player);
	}
}
