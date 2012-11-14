package com.jrtc27.CraftMail;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.bukkit.ChatColor;

public class DatabaseManager {
	private static CraftMail plugin;
	
	private static final int DATABASE_VERSION = 1;
	
	private static String url;
	private static String username;
	private static String password;
	private static boolean initialised = false;
	
	public static boolean init(CraftMail plugin, String url, String username, String password) {
		if (initialised) return true;
		DatabaseManager.plugin = plugin;
		DatabaseManager.url = url;
		DatabaseManager.username = username;
		DatabaseManager.password = password;
		boolean success = checkTables();
		initialised = true;
		success = success && addSystemPlayers();
		success = success && upgradeDatabase();
		return success;
	}
	
	private static boolean addSystemPlayers() {
		return addPlayer("[Console]") && addPlayer("[Command Block]");
	}
	
	public static boolean addPlayer(String player) {
		if (!initialised) return false;
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.prepareStatement(INSERT_PLAYER_COMMAND);
			statement.setString(1, player);
			statement.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static boolean addMail(MailItem mail) {
		if (!initialised) return false;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.prepareStatement(INSERT_MESSAGE_COMMAND, Statement.RETURN_GENERATED_KEYS);
			// Columns: message_id, message, time, from
			long fromId = getIdForPlayer(mail.getFrom().getPlayer(), connection);
			long toId = getIdForPlayer(mail.getTo().getPlayer(), connection);
			if (fromId == -1 || toId == -1) return false;
			statement.setLong(1, 0); // 0 means auto-increment
			statement.setString(2, mail.getMessage());
			statement.setLong(3, mail.getTime());
			statement.setLong(4, fromId);
			statement.executeUpdate();
			result = statement.getGeneratedKeys();
			
			if (result.next()) {
				mail.setMessageId(result.getLong(1));
				statement.close();
				statement = connection.prepareStatement(INSERT_MESSAGE_PLAYER_JOIN_COMMAND);
				// Columns: message_id, player_id
				statement.setLong(1, mail.getMessageId());
				statement.setLong(2, toId);
				statement.executeUpdate();
				statement.close();
				
				statement = connection.prepareStatement(INSERT_MESSAGE_TO_EMAIL_COMMAND);
				statement.setLong(1, mail.getMessageId());
				statement.executeUpdate();
				
				return true;
			} else {
				throw new SQLException("Inserting message into database returned an empty ResultSet!");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
					connection = null;
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void markSent(long messageId) {
		if (!initialised) return;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.prepareStatement(DELETE_MESSAGE_TO_EMAIL_COMMAND);
			statement.setLong(1, messageId);
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			return;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
					connection = null;
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static MailItem[] getUnsent() {
		if (!initialised) return null;
		Connection connection = null;
		Statement statement = null;
		ResultSet result = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.createStatement();
			result = statement.executeQuery(GET_MESSAGES_TO_EMAIL_COMMAND);
			List<MailItem> items = new ArrayList<MailItem>();
			while (result.next()) {
				long messageId = result.getLong("message_id");
				MailItem message = getMessage(messageId, connection);
				if (message != null) {
					items.add(message);
				}
			}
			return items.toArray(new MailItem[0]);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
					connection = null;
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static MailItem[] getMessages(String player) {
		if (!initialised) return null;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.prepareStatement(GET_MESSAGES_COMMAND);
			statement.setString(1, player);
			result = statement.executeQuery();
			List<MailItem> items = new ArrayList<MailItem>();
			PlayerDetails recipient = new PlayerDetails(player, getEmailForPlayer(player));
			Map<Long, PlayerDetails> cachedDetails = new HashMap<Long, PlayerDetails>();
			while (result.next()) {
				String message = result.getString("message");
				long fromId = result.getLong("from") & 0xFFFFFFFF;
				PlayerDetails sender = cachedDetails.get(fromId);
				if (sender == null) {
					String from = getPlayerForId(fromId, connection);
					if (from == null) from = "[Unknown]";
					sender = new PlayerDetails(from, getEmailForPlayer(from));
					cachedDetails.put(fromId, sender);
				}
				long id = result.getLong("message_id") & 0xFFFFFFFF;
				long time = result.getLong("time");
				MailItem mail = new MailItem(sender, recipient, message, time);
				mail.setMessageId(id);
				items.add(mail);
			}
			return items.toArray(new MailItem[0]);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
					connection = null;
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static MailItem getMessage(long messageId) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			return getMessage(messageId, connection);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static MailItem getMessage(long messageId, Connection connection) {
		if (!initialised) return null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement(GET_MESSAGE_BY_ID_COMMAND);
			statement.setLong(1, messageId);
			result = statement.executeQuery();
			if (result.next()) {
				String recipientName = result.getString("player_name");
				PlayerDetails recipient = new PlayerDetails(recipientName, getEmailForPlayer(recipientName, connection));
				String message = result.getString("message");
				long fromId = result.getLong("from") & 0xFFFFFFFF;
				String from = getPlayerForId(fromId, connection);
				if (from == null) from = "[Unknown]";
				PlayerDetails sender = new PlayerDetails(from, getEmailForPlayer(from, connection));
				long time = result.getLong("time");
				MailItem mail = new MailItem(sender, recipient, message, time);
				mail.setMessageId(messageId);
				return mail;
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static String getPlayerForId(long playerId) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			return getPlayerForId(playerId, connection);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static String getPlayerForId(long playerId, Connection connection) {
		if (!initialised) return null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement(GET_PLAYER_NAME_COMMAND);
			statement.setLong(1, playerId);
			result = statement.executeQuery();
			if (result.next()) {
				return result.getString("player_name");
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static long getIdForPlayer(String player) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			return getIdForPlayer(player, connection);
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static long getIdForPlayer(String player, Connection connection) {
		if (!initialised) return -1;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement(GET_PLAYER_ID_COMMAND);
			statement.setString(1, player);
			result = statement.executeQuery();
			if (result.next()) {
				return result.getLong("player_id") & 0xFFFFFFFF;
			} else {
				return -1;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static InternetAddress getEmailForPlayer(String player) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			return getEmailForPlayer(player, connection);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static InternetAddress getEmailForPlayer(String player, Connection connection) {
		if (!initialised) return null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement(GET_PLAYER_EMAIL_BY_NAME_COMMAND);
			statement.setString(1, player);
			result = statement.executeQuery();
			if (result.next()) {
				String email = result.getString("player_email");
				return email == null ? null : new InternetAddress(email);
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} catch (AddressException e) {
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static InternetAddress getEmailForPlayer(long player) {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			return getEmailForPlayer(player, connection);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static InternetAddress getEmailForPlayer(long player, Connection connection) {
		if (!initialised) return null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement(GET_PLAYER_EMAIL_BY_ID_COMMAND);
			statement.setLong(1, player);
			result = statement.executeQuery();
			if (result.next()) {
				String email = result.getString("player_email");
				return email == null ? null : new InternetAddress(email);
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		} catch (AddressException e) {
			return null;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (result != null) {
					result.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static boolean checkTables() {
		Connection connection = null;
		Statement statement = null;
		try {
			connection = DriverManager.getConnection(url, username, password);
			final DatabaseMetaData metadata = connection.getMetaData();
			statement = connection.createStatement();
			
			createTable(metadata, statement, "messages", "(`message_id` INT UNSIGNED NOT NULL AUTO_INCREMENT, `message` VARCHAR(4096) NOT NULL, `from` INT UNSIGNED NOT NULL, `time` BIGINT NOT NULL, PRIMARY KEY (`message_id`))");
			createTable(metadata, statement, "players", "(`player_id` INT UNSIGNED NOT NULL AUTO_INCREMENT, `player_name` VARCHAR(64) NOT NULL, `player_email` VARCHAR(256), PRIMARY KEY (`player_id`), UNIQUE (`player_name`))");
			createTable(metadata, statement, "message_player_join", "(`message_id` INT UNSIGNED NOT NULL, `player_id` INT UNSIGNED NOT NULL, PRIMARY KEY(`message_id`,`player_id`), UNIQUE(message_id))");
			createTable(metadata, statement, "messages_to_email", "(`message_id` INT UNSIGNED NOT NULL, PRIMARY KEY(`message_id`))");
			createTable(metadata, statement, "settings", "(`key` VARCHAR(128) NOT NULL, `value` VARCAR(128), PRIMARY KEY (`key`))");
			statement.execute("ALTER TABLE `players` AUTO_INCREMENT = 1");
			statement.execute("ALTER TABLE `messages` AUTO_INCREMENT = 1");
			
			return true;
		} catch (SQLException e) {
			plugin.log.severe(ChatColor.RED + CraftMailUtil.prependName("Failed to check tables!"));
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				plugin.log.severe(ChatColor.RED + CraftMailUtil.prependName("Failed to close connection!"));
				e.printStackTrace();
				return false;
			}
		}
	}
	
	private static void createTable(DatabaseMetaData metadata, Statement statement, String table, String columns) throws SQLException {
		if (!metadata.getTables(null, null, table, null).next()) {
			plugin.log.info("Creating table '" + table + "'.");
			statement.execute("CREATE TABLE `" + table + "` " + columns + " ENGINE=MyISAM");
			if (!metadata.getTables(null, null, table, null).next()) {
				throw new SQLException("Failed to create table '" + table + "'");
			}
		}
	}
	
	private static boolean upgradeDatabase() {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet results = null;
		String stepMessage = "check database version!";
		try {
			connection = DriverManager.getConnection(url, username, password);
			statement = connection.prepareStatement(GET_SETTING_COMMAND);
			statement.setString(1, "version");
			results = statement.executeQuery();
			if (results.next()) {
				String versionString = results.getString("value");
				int version;
				try {
					version = Integer.valueOf(versionString);
					
					if (version > DATABASE_VERSION || version < 1) {
						plugin.log.warning(ChatColor.RED + CraftMailUtil.prependName("Invalid version specified in 'settings' table - overwriting with current version!"));
						stepMessage = "set database version!";
						setSetting("version", String.valueOf(DATABASE_VERSION), connection);
					}
					/*if (version < 2) {
					 * phase = "upgrade database to version 2!";
						// Potential upgrade commands
					}*/
				} catch (NumberFormatException e) {
					plugin.log.warning(ChatColor.RED + CraftMailUtil.prependName("Invalid version specified in 'settings' table - overwriting with current version!"));
					setSetting("version", String.valueOf(DATABASE_VERSION), connection);
					return false;
				}
			} else {
				// First run - let's set the version
				stepMessage = "set database version!";
				setSetting("version", String.valueOf(DATABASE_VERSION), connection);
			}
			return true;
		} catch (SQLException e) {
			plugin.log.severe(ChatColor.RED + CraftMailUtil.prependName("Failed to " + stepMessage));
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
				if (results != null) {
					results.close();
				}
			} catch (SQLException e) {
				plugin.log.severe(ChatColor.RED + CraftMailUtil.prependName("Failed to close connection!"));
				e.printStackTrace();
				return false;
			}
		}
	}
	
	private static void setSetting(String key, String value, Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(REPLACE_SETTING_COMMAND);
		statement.setString(1, key);
		statement.setString(2, value);
		statement.executeUpdate();
		statement.close();
	}
	
	private static final String GET_MESSAGES_COMMAND = "SELECT `message`, `time`, `from`, `messages`.`message_id` FROM " +
			"`messages` JOIN `message_player_join` JOIN `players` " +
			"ON (`players`.`player_name` = ?) AND (`message_player_join`.`player_id` = `players`.`player_id`) " +
			"AND (`messages`.`message_id` = `message_player_join`.`message_id`)";
	private static final String GET_MESSAGE_BY_ID_COMMAND = "SELECT `message`, `time`, `from`, `player_name` FROM " +
			"`messages` JOIN `message_player_join` JOIN `players` " +
			"ON (`messages`.`message_id` = ?) AND (`message_player_join`.`message_id` = `messages`.`message_id`) " +
			"AND (`message_player_join`.`player_id` = `players`.`player_id`)";
	private static final String GET_MESSAGES_TO_EMAIL_COMMAND = "SELECT `message_id` FROM `messages_to_email`";
	private static final String GET_PLAYER_NAME_COMMAND = "SELECT `player_name` FROM `players` WHERE `player_id` = ?";
	private static final String GET_PLAYER_ID_COMMAND = "SELECT `player_id` FROM `players` WHERE `player_name` = ?";
	private static final String GET_PLAYER_EMAIL_BY_ID_COMMAND = "SELECT `player_email` FROM `players` WHERE `player_id` = ?";
	private static final String GET_PLAYER_EMAIL_BY_NAME_COMMAND = "SELECT `player_email` FROM `players` WHERE `player_name` = ?";
	private static final String GET_SETTING_COMMAND = "SELECT `value` FROM `settings` WHERE `key` = ?";
	
	private static final String INSERT_MESSAGE_COMMAND = "INSERT " +
			"INTO `messages` (`message_id`, `message`, `time`, `from`) " +
			"VALUES (?, ?, ?, ?)";
	private static final String INSERT_MESSAGE_TO_EMAIL_COMMAND = "INSERT " +
			"INTO `messages_to_email` (`message_id`) " +
			"VALUES (?)";
	private static final String INSERT_MESSAGE_PLAYER_JOIN_COMMAND = "INSERT " +
			"INTO `message_player_join` (`message_id`, `player_id`) " +
			"VALUES (?, ?)";
	private static final String INSERT_PLAYER_COMMAND = "INSERT " +
			"INTO `players` (`player_name`) VALUES (?) " +
			"ON DUPLICATE KEY UPDATE `player_name`=`player_name`";
	private static final String REPLACE_SETTING_COMMAND = "REPLACE " +
			"INTO `settings` (`key`, `value`) VALUES (?, ?)";
	
	private static final String DELETE_MESSAGE_TO_EMAIL_COMMAND = "DELETE " +
			"FROM `messages_to_email` " +
			"WHERE `message_id`=?";
}