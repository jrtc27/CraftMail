package com.jrtc27.CraftMail;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class CraftMailUtil {
	private static CraftMail plugin;

	public static void init(CraftMail plugin) {
		CraftMailUtil.plugin = plugin;
	}
	
	public static String prependName(String subject) {
		return "["+plugin.pdfFile.getName()+"] " + subject;
	}
	
	public static OfflinePlayer getOfflinePlayer(String name) {
		for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
			if (player.getName().equals(name)) return player;
		}
		return null;
	}
	
	public static void notifyPlayer(Player player, MailItem mail) {
		player.sendMessage(ChatColor.GREEN + "You have a new message from " + ChatColor.GOLD + mail.getFrom().getPlayer() +
				".\n" + ChatColor.GREEN + "You can check your messages with " + ChatColor.GOLD + "/mail read" + ChatColor.GREEN + ".");
	}

}
