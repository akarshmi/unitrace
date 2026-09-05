package dev.akarshmi.UniTrace.modules.utils.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendOtpEmail(String toEmail, String otp, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Your UniTrace verification code");
            helper.setText(buildBody(otp, ttlMinutes), false);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}", toEmail, e);
            throw new IllegalStateException("Unable to send verification email. Please try again.", e);
        }
    }

    private String buildBody(String otp, long ttlMinutes) {
        return """
                Your UniTrace verification code is: %s

                This code expires in %d minutes. If you didn't request this, you can ignore this email.
                """.formatted(otp, ttlMinutes);
    }
}