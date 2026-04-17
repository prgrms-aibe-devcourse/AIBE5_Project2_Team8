package com.knoc.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
   private static final Logger log = LoggerFactory.getLogger(EmailService.class);

   private final JavaMailSender mailSender;

   @Value("${spring.mail.username}")
    private String from;

   public void send(String subject, String body, String... to){
       SimpleMailMessage message = new SimpleMailMessage();
       message.setTo(to);
       message.setFrom(from);
       message.setSubject(subject);
       message.setText(body);
       mailSender.send(message);

       log.info("메일 전송 완료: 제목={}, 수신자={}", subject, String.join(", ", to));
   }

}
