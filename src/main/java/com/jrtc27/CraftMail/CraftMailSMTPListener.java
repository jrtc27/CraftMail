package com.jrtc27.CraftMail;

import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;

public class CraftMailSMTPListener implements ConnectionListener, TransportListener {

	public CraftMailSMTPListener() {}
	
	@Override
	public void closed(ConnectionEvent e) {}

	@Override
	public void disconnected(ConnectionEvent e) {
		EmailSender.connect();
	}

	@Override
	public void opened(ConnectionEvent e) {}

	@Override
	public void messageDelivered(TransportEvent arg0) {
		EmailSender.sendCallback(arg0.getMessage(), true);
	}

	@Override
	public void messageNotDelivered(TransportEvent arg0) {
		System.out.println("Failed to send message!");
		EmailSender.sendCallback(arg0.getMessage(), true);
	}

	@Override
	public void messagePartiallyDelivered(TransportEvent arg0) {
		System.out.println("Failed to send message fully!");
		EmailSender.sendCallback(arg0.getMessage(), true);
	}
}
