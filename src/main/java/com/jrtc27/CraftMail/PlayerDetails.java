package com.jrtc27.CraftMail;

import javax.mail.internet.InternetAddress;

public class PlayerDetails {
	private final String player;
	private final InternetAddress email;
	
	public PlayerDetails(String player, InternetAddress email) {
		this.player = player;
		this.email = email;
	}
	
	public String getPlayer() {
		return player;
	}
	
	public InternetAddress getEmail() {
		return email;
	}
}
