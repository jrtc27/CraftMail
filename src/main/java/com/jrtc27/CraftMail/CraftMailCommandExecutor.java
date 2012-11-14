package com.jrtc27.CraftMail;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CraftMailCommandExecutor implements CommandExecutor {
	private final CraftMail plugin;
	
	public CraftMailCommandExecutor(CraftMail plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String baseCommand;
		if (args.length == 0) {
			baseCommand = null;
		} else {
			baseCommand = args[0].toLowerCase();
		}
		
		if ("read".equals(baseCommand)) {
			String player;
			boolean ownMessages = false;
			if (args.length == 1 || (args.length == 2 && args[1].equals(sender.getName()))) {
				if (!sender.hasPermission("craftmail.read")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to perform this action.");
					return true;
				}
				ownMessages = true;
				if (sender instanceof Player) {
					player = sender.getName();
				} else {
					player =  "[Console]";
				}
			} else if (args.length == 2) {
				if (!sender.hasPermission("craftmail.read.others")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to perform this action.");
					return true;
				} else {
					if (CraftMailUtil.getOfflinePlayer(args[1]) == null) {
						sender.sendMessage(ChatColor.RED + "Player '" + ChatColor.GOLD + args[1] + ChatColor.RED + "' does not exist.");
						return true;
					}
					player = args[1];
				}
			} else {
				String slash = "";
				if (sender instanceof Player) slash = "/";
				String playerArg = "";
				if (sender.hasPermission("craftmail.read.others"))
					playerArg = " [player]";
				sender.sendMessage("Usage: " + slash + label + " read" + playerArg);
				return true;
			}
			MailItem[] messages = DatabaseManager.getMessages(player);
			if (messages == null) {
				sender.sendMessage(ChatColor.RED + "An error occurred while performing this action.");
			} else {
				if (messages.length == 0) {
					if (ownMessages) {
						sender.sendMessage(ChatColor.GREEN + "You have no messages.");
					} else {
						sender.sendMessage(ChatColor.GOLD + player + ChatColor.GREEN + " has no messages.");
					}
				}
				for (MailItem message : messages) {
					Date date = new Date(message.getTime()*1000);
					DateFormat dateFormat = DateFormat.getDateInstance();
					DateFormat timeFormat = DateFormat.getTimeInstance();
					sender.sendMessage(ChatColor.GRAY + "From " + ChatColor.GOLD 
							+ message.getFrom().getPlayer() + ChatColor.GRAY + " on " + ChatColor.LIGHT_PURPLE
							+ dateFormat.format(date) + ChatColor.GRAY + " at " + ChatColor.LIGHT_PURPLE
							+ timeFormat.format(date) + ChatColor.GRAY + ":"
							+ "\n" + ChatColor.WHITE + message.getMessage());
				}
			}
			return true;
		} else if ("send".equals(baseCommand)) {
			if (!sender.hasPermission("craftmail.send")) {
				sender.sendMessage(ChatColor.RED + "You do not have permission to perform this action.");
				return true;
			}
			if (args.length < 3) { // Need "send", a player and a message
				String slash = "";
				if (sender instanceof Player) slash = "/";
				sender.sendMessage("Usage: " + slash + label + " send <player> <message>");
				return true;
			}
			
			long time = System.currentTimeMillis()/1000;
			
			if (CraftMailUtil.getOfflinePlayer(args[1]) == null) {
				sender.sendMessage(ChatColor.RED + "Player '" + ChatColor.GOLD + args[1] + ChatColor.RED + "' does not exist.");
				return true;
			}
			DatabaseManager.addPlayer(args[1]);
			
			PlayerDetails from;
			if (sender instanceof Player) {
				String player = sender.getName();
				from = new PlayerDetails(player, DatabaseManager.getEmailForPlayer(player));
			} else {
				from = new PlayerDetails("[Console]", null);
			}
			
			StringBuilder message = new StringBuilder(args[2]);
			for (int i = 3; i < args.length; i++) {
				message.append(" "+args[i]);
			}
			
			PlayerDetails to = new PlayerDetails(args[1], DatabaseManager.getEmailForPlayer(args[1]));
			
			MailItem mail = new MailItem(from, to, message.toString(), time);
			if (DatabaseManager.addMail(mail)) {
				sender.sendMessage(ChatColor.GREEN + "Message sent to " + args[1]);
				
				Player onlinePlayer = plugin.getServer().getPlayerExact(args[1]);
				if (onlinePlayer != null) {
					CraftMailUtil.notifyPlayer(onlinePlayer, mail);
				}
				
				EmailSender.queueItem(mail);
			} else {
				sender.sendMessage(ChatColor.RED + "Unable to send message to " + args[1]);
			}
			return true;
		} else if ("sendall".equals(baseCommand)) {
			if (!sender.hasPermission("craftmail.sendall")) {
				sender.sendMessage(ChatColor.RED + "You do not have permission to perform this action.");
				return false;
			}
			if (args.length < 2) { // Need "sendall" and a message
				String slash = "";
				if (sender instanceof Player) slash = "/";
				sender.sendMessage("Usage: " + slash + label + " sendall <player> <message>");
				return false;
			}
			
			long time = System.currentTimeMillis()/1000;
			
			PlayerDetails from;
			if (sender instanceof Player) {
				String player = sender.getName();
				from = new PlayerDetails(player, DatabaseManager.getEmailForPlayer(player));
			} else {
				from = new PlayerDetails("[Console]", null);
			}
			
			StringBuilder message = new StringBuilder();
			for (int i = 1; i < args.length; i++) {
				message.append(args[i]);
			}
			
			List<String> failedToSend = new ArrayList<String>();
			for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
				String playerName = player.getName();
				DatabaseManager.addPlayer(playerName);
				PlayerDetails to = new PlayerDetails(playerName, DatabaseManager.getEmailForPlayer(playerName));
				
				MailItem mail = new MailItem(from, to, message.toString(), time);
				if (!DatabaseManager.addMail(mail)) {
					failedToSend.add(playerName);
				} else {
					Player onlinePlayer = player.getPlayer();
					if (onlinePlayer != null) {
						CraftMailUtil.notifyPlayer(onlinePlayer, mail);
					}
					
					EmailSender.queueItem(mail);
				}
			}
			if (failedToSend.size() == 0) {
				sender.sendMessage(ChatColor.GREEN + "Message sent to everyone");
			} else {
				StringBuilder builder = new StringBuilder();
				Iterator<String> iterator = failedToSend.iterator();
				while (iterator.hasNext()) {
					String player = iterator.next();
					builder.append(ChatColor.GOLD + player + ChatColor.RED);
					if (iterator.hasNext()) {
						builder.append(", ");
					}
				}
				sender.sendMessage(ChatColor.RED + "Failed to send message to " + builder.toString() + ".\n"
						+ "Players not listed here have received the message.");
			}
			return true;
		} else if ("help".equals(baseCommand)) {
			sender.sendMessage(ChatColor.RED + "You are doomed!");
			return true;
		}
		
		if (baseCommand != null)
			sender.sendMessage(ChatColor.RED + "Invalid command: '" + ChatColor.GOLD + baseCommand + ChatColor.RED + "'");
		return false;
	}

}
