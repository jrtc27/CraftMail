package com.jrtc27.CraftMail;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class CraftMailPlayerListener implements Listener {
	final CraftMail plugin;

	public CraftMailPlayerListener(CraftMail plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(final PlayerJoinEvent event) {
		Player player = event.getPlayer();
		DatabaseManager.addPlayer(player.getName());
		if (DatabaseManager.getEmailForPlayer(player.getName()) == null) {
			player.sendMessage(ChatColor.DARK_GREEN + "You currently have no valid email address set.\n" +
					"Please use /mail setemail <address> if you wish to receive messages via email.");
		}
		if (plugin.errorMessage != null && player.hasPermission("craftmail.notifyerror")) {
			player.sendMessage(ChatColor.RED + CraftMailUtil.prependName(plugin.errorMessage));
		}
	}

}
