package com.civclassic.altmanager;

import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Listener;

public abstract class ImprisonmentHandler implements Listener {
	
	protected AltManager plugin;
	
	ImprisonmentHandler(AltManager plugin) {
		this.plugin = plugin;
	}

	abstract int getImprisonedCount(Set<UUID> alts);

	abstract int getImprisonedCount(Set<UUID> alts, int pearltype);
	
	abstract boolean isImprisoned(UUID player);
}
