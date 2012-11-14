package com.jrtc27.CraftMail;

public class MailItem {
	private PlayerDetails from;
	private PlayerDetails to;
	private String message;
	private long time;
	private long messageId = -1;
	private int sendAttempts = 0;

	public MailItem(PlayerDetails from, PlayerDetails to, String message, long time) {
		this.from = from;
		this.to = to;
		this.message = message;
		this.time = time;
	}

	public PlayerDetails getFrom() {
		return from;
	}

	public PlayerDetails getTo() {
		return to;
	}

	public String getMessage() {
		return message;
	}
	
	public long getTime() {
		return time;
	}
	
	public long getMessageId() {
		return messageId;
	}
	
	public void setFrom(PlayerDetails from) {
		this.from = from;
	}
	
	public void setTo(PlayerDetails to) {
		this.to = to;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public void setTime(long time) {
		this.time = time;
	}
	
	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}
	
	public void incSendAttempts() {
		this.sendAttempts++;
	}
	
	public int getSendAttempts() {
		return this.sendAttempts;
	}
}