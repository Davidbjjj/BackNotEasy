package com.example.BancoDeDados.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.mail.username:}")
    private String remetente;

    public String enviarEmail(String para, String assunto, String mensagem) {
        if (remetente == null || remetente.isBlank()) {
            log.debug("EmailService desativado - remetente não configurado");
            return "Email desativado";
        }
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(remetente);
            email.setTo(para);
            email.setSubject(assunto);
            email.setText(mensagem);
            mailSender.send(email);
            return "Email enviado";
        } catch (Exception e) {
            log.error("Erro ao enviar email: {}", e.getMessage(), e);
            return "Erro ao enviar email!" + e.getLocalizedMessage();
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        if (remetente == null || remetente.isBlank()) {
            log.debug("Email de recuperação não enviado: remetente não configurado");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(remetente);
        message.setTo(toEmail);
        message.setSubject("Recuperação de Senha - Banco de Questões");
        message.setText(
                "Você solicitou a recuperação de senha.\n\n" +
                        "Seu token de recuperação é: " + token + "\n\n" +
                        "Este token expira em 15 minutos.\n\n" +
                        "Se você não solicitou esta recuperação, ignore este email."
        );
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Erro ao enviar email de recuperação: {}", e.getMessage(), e);
        }
    }
}
