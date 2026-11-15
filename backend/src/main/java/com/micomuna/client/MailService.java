package com.micomuna.client;

// Envío de correo detrás de una interfaz propia: en desarrollo va a Mailpit y en producción a un SMTP real.
// Lanza una excepción de runtime si no pudo enviar; quien llama decide qué hacer.
public interface MailService {

	void enviar(String destino, String asunto, String texto);
}
