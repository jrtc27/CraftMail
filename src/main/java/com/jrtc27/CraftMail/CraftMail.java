package com.jrtc27.CraftMail;

import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class CraftMail extends JavaPlugin {
	public PluginDescriptionFile pdfFile;
	public Logger log = Logger.getLogger("Minecraft");
	public boolean useSMTP;
	public String errorMessage = null;
	private int emailSenderTaskId = -1;
	
	CraftMailPlayerListener playerListener;

	@Override
	public void onEnable() {
		pdfFile = this.getDescription();
		CraftMailUtil.init(this);
		saveDefaultConfig();
		Configuration config = getConfig();
		boolean keepSettingUp = true;
		useSMTP = config.getBoolean("mail.enabled");
		if (useSMTP) {
			String from = config.getString("mail.smtp.from");
			String fromName = config.getString("mail.smtp.name");
			InternetAddress fromAddress = null;
			try {
				fromAddress = new InternetAddress(from);
			} catch (AddressException ex) {
				log.severe(CraftMailUtil.prependName("Invalid from address specified in config.yml!"));
				if (errorMessage == null) errorMessage = "Failed to set up SMTP! Please check console for details.";
				keepSettingUp = false;
			}
			keepSettingUp = keepSettingUp && fromAddress != null;
			if (keepSettingUp) {
				try {
					fromAddress.setPersonal(fromName);
				} catch (UnsupportedEncodingException e) {
					log.severe(CraftMailUtil.prependName("Invalid from name specified in config.yml!"));
					if (errorMessage == null) errorMessage = "Invalid from name specified in config.yml!";
				}
				final InternetAddress fromAddressFinal = fromAddress;
				final String host = config.getString("mail.smtp.host");
				final String port = config.getString("mail.smtp.port");
				final String username = config.getString("mail.smtp.auth.username");
				final String password = config.getString("mail.smtp.auth.password");
				final String authType = config.getString("mail.smtp.auth.type");
				final CraftMail plugin = this;
				getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable() {
					@Override
					public void run() {
						EmailSender.initialise(fromAddressFinal, host, port, username, password, authType);
						plugin.startSendAsyncTask();
					}
				});
			}
		}
		if (keepSettingUp) {
			String hostname = config.getString("sql.hostname");
			int port = config.getInt("sql.port");
			String database = config.getString("sql.database");
			if (database == null) database = String.valueOf(config.getInt("sql.database"));
			String url = "jdbc:mysql://" + hostname + ":" + port + "/" + database;
			
			String username = config.getString("sql.username");
			if (username == null) username = String.valueOf(config.getInt("sql.username"));
			String password = config.getString("sql.password");
			if (password == null) password = String.valueOf(config.getInt("sql.password"));
			
			if (!DatabaseManager.init(this, url, username, password)) {
				keepSettingUp = false;
				log.severe(CraftMailUtil.prependName("Failed to set up database!"));
				if (errorMessage == null) errorMessage = "Failed to set up database! Please check console for details.";
			}
		}
		getCommand("mail").setExecutor(new CraftMailCommandExecutor(this));
		playerListener = new CraftMailPlayerListener(this);
		getServer().getPluginManager().registerEvents(playerListener, this);
		log.info(pdfFile.getFullName() + " is enabled!");
	}
	
	public void startSendAsyncTask() {
		if (emailSenderTaskId != -1) return;
		emailSenderTaskId = getServer().getScheduler().scheduleAsyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				EmailSender.sendQueuedItems(false);
			}
		}, 200L, 200L);
	}
	
	public void stopSendAsyncTask() {
		if (emailSenderTaskId == -1) return;
		getServer().getScheduler().cancelTask(emailSenderTaskId);
		emailSenderTaskId = -1;
	}
	
	@Override
	public void onDisable() {
		this.stopSendAsyncTask();
		log.info(pdfFile.getFullName() + " is disabled!");
	}
}
