package com.micomuna.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailServiceSmtp implements MailService {

	private final JavaMailSender sender;
	private final String remitente;

	public MailServiceSmtp(JavaMailSender sender, @Value("${app.mail.remitente}") String remitente) {
		this.sender = sender;
		this.remitente = remitente;
	}

	@Override
	public void enviar(String destino, String asunto, String texto) {
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(destino);
		mensaje.setSubject(asunto);
		mensaje.setText(texto);
		sender.send(mensaje);
	}
}
