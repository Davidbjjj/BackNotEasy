package com.example.BancoDeDados.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    public String enviarEmail(String para, String assunto, String mensagem) {

        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(remetente);
            email.setTo(para);
            email.setSubject(assunto);
            email.setText(mensagem);
            mailSender.send(email);
            return "Email enviado";
        } catch (Exception e) {
            return "Erro ao enviar email!" + e.getLocalizedMessage();
        }
    }
}
