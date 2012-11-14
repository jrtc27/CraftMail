package com.jrtc27.CraftMail;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.AuthenticationFailedException;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {
	private static boolean initialised = false;
	private static List<MailItem> sendQueue = new ArrayList<MailItem>();
	private static Map<Message, MailItem> sending = new HashMap<Message, MailItem>();
	private static Session session;
	private static InternetAddress from;
	private static Transport transport = null;

	public static void initialise(final InternetAddress fromAddress, final String host, final String port, final String username, final String password, final String authType) {
		if (initialised) return;
		Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Initialising SMTP..."));
		String authTypeLower = authType.toLowerCase();
		Properties properties = new Properties();
		properties.put("mail.smtp.timeout", "10000");
		properties.put("mail.smtp.connectiontimeout", "10000");
		properties.setProperty("mail.smtp.host", host);
		if (port != null && port.length() > 0) {
			properties.setProperty("mail.smtp.port", port);
		}
		if (authTypeLower.equals("") || authTypeLower.equals("auto")) {
			if (username != null && username.length() > 0 && password != null && password.length() > 0) {
				properties.setProperty("mail.smtp.auth", "true");
				if (port.equals("587")) {
					properties.setProperty("mail.smtp.starttls.enable", "true");
				} else {
					properties.setProperty("mail.smtp.ssl.enable", "true");
					properties.put("mail.smtp.socketFactory.port", port);
			        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			        properties.put("mail.smtp.socketFactory.fallback", "false");
				}
			} else {
				properties.setProperty("mail.smtp.auth", "false");
			}
		} else if (authTypeLower.equals("ssl")) {
			properties.setProperty("mail.smtp.auth", "true");
			properties.setProperty("mail.smtp.ssl.enable", "true");
			properties.put("mail.smtp.socketFactory.port", port);
	        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
	        properties.put("mail.smtp.socketFactory.fallback", "false");
		} else if (authTypeLower.equals("tls")) {
			properties.setProperty("mail.smtp.auth", "true");
			properties.setProperty("mail.smtp.starttls.enable", "true");
		} else if (authTypeLower.equals("none")) {
			properties.setProperty("mail.smtp.auth", "false");
		} else {
			Logger.getLogger("Minecraft").severe(CraftMailUtil.prependName("Invalid auth type '"+authType+"' specified in config.yml!"));
			return;
		}
		from = fromAddress;
		session = Session.getInstance(properties, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});
		initialised = connect();
		if (initialised) {
			Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Initialised SMTP!"));
			Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Loading previously unsent messages..."));
			MailItem[] unsent = DatabaseManager.getUnsent();
			if (unsent != null) {
				int count = 0;
				for (MailItem message : unsent) {
					if (sendQueue.add(message))
						count++;
				}
				Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Loaded " + count + " previously unsent messages!"));
			} else {
				Logger.getLogger("Minecraft").severe(CraftMailUtil.prependName("Failed to load previously unsent messages!"));
			}
		} else {
			Logger.getLogger("Minecraft").severe(CraftMailUtil.prependName("Failed to initialise SMTP!"));
		}
	}

	public static boolean queueItem(MailItem item) {
		if (!initialised) return false;
		synchronized (sendQueue) {
			sendQueue.add(item);
			//Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Mail queued!"));
		}
		return true;
	}
	
	public static void sendQueuedItems(boolean unlimited) {
		if (session == null || transport == null) return;
		synchronized (sendQueue) {
			//Logger.getLogger("Minecraft").info(CraftMailUtil.prependName("Sending mail!"));
			Iterator<MailItem> iterator = sendQueue.iterator();
			int sent = 0;
			while (iterator.hasNext() && (sent < 5 || unlimited)) {
				MailItem item = iterator.next();
				InternetAddress recipient = item.getTo().getEmail();
				if (recipient != null) {
					Message message = new MimeMessage(session);
					try {
						message.setFrom(from);
						message.setRecipient(Message.RecipientType.TO, recipient);
						message.setSubject("New Message From "+item.getFrom().getPlayer());
						
						Date date = new Date(item.getTime()*1000);
						DateFormat dateFormat = DateFormat.getDateInstance();
						DateFormat timeFormat = DateFormat.getTimeInstance();
						message.setContent("From " + item.getFrom().getPlayer() + " on " + dateFormat.format(date)
								+ " at " + timeFormat.format(date) + ":\n" + item.getMessage(), "text/plain");
						
						transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
						sent++;
						sending.put(message, item);
						item.incSendAttempts();
						iterator.remove();
					} catch (MessagingException ex) {
						Logger.getLogger("Minecraft").log(Level.SEVERE, CraftMailUtil.prependName("Error handling message!\n"+ex.getLocalizedMessage()));
					}
				} else { // No valid email address
					iterator.remove();
					DatabaseManager.markSent(item.getMessageId());
				}
			}
		}
	}
	
	public static boolean connect() {
		close();
		try {
			transport = session.getTransport("smtp");
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			return false;
		}
		CraftMailSMTPListener listener = new CraftMailSMTPListener();
		transport.addConnectionListener(listener);
		transport.addTransportListener(listener);
		try {
			transport.connect();
		} catch (MessagingException e) {
			if (e instanceof AuthenticationFailedException) {
				Logger.getLogger("Minecraft").log(Level.SEVERE, CraftMailUtil.prependName("Failed to authenticate with SMTP server!"));
			}
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static void close() {
		if (transport != null && transport.isConnected()) {
			try {
				transport.close();
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}
		transport = null;
	}
	
	public static void sendCallback(Message message, boolean successful) {
		synchronized (sendQueue) {
			MailItem item = sending.get(message);
			if (item == null) return;
			boolean removeFromDatabase = successful;
			if (!successful) {
				if (item.getSendAttempts() < 5) {
					sendQueue.add(item);
				} else {
					Logger.getLogger("Minecraft").log(Level.SEVERE, CraftMailUtil.prependName("Failed to send message (id " + item.getMessageId() + ") - tried 5 times!\n"));
					removeFromDatabase = true;
				}
			}
			if (removeFromDatabase) {
				DatabaseManager.markSent(item.getMessageId());
			}
			sending.remove(message);
		}
	}
}