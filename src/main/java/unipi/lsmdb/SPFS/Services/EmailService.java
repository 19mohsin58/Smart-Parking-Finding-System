package unipi.lsmdb.SPFS.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("SPFS - Your Verification Code");
            message.setText("Welcome to SPFS! Your verification code is: " + code);
            mailSender.send(message);
            System.out.println("Verification email sent to " + to);
        } catch (Exception e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("SPFS - Password Reset Request");
            message.setText("You requested a password reset. Your code is: " + code);
            mailSender.send(message);
            System.out.println("Password reset email sent to " + to);
        } catch (Exception e) {
            System.err.println("Failed to send password reset email: " + e.getMessage());
        }
    }
}

