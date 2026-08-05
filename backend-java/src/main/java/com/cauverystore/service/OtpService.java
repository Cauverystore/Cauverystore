package com.cauverystore.service;

import com.cauverystore.entities.EmailOtp;
import com.cauverystore.repository.EmailOtpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Purpose-scoped email OTPs for account/email verification and password reset.
 * The {@code EmailOtp} row is keyed by email + purpose so different flows do
 * not clobber each other's codes.
 */
@Service
public class OtpService {

    private static final long OTP_REQUEST_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_VERIFY_ATTEMPTS = 5;
    private static final int OTP_VALID_MINUTES = 15;

    private final EmailOtpRepository emailOtpRepo;
    private final EmailService emailService;

    public OtpService(EmailOtpRepository emailOtpRepo, EmailService emailService) {
        this.emailOtpRepo = emailOtpRepo;
        this.emailService = emailService;
    }

    @Transactional
    public void sendEmailOtp(String rawEmail, String purpose) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase();
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        EmailOtp existing = emailOtpRepo.findByEmailAndPurpose(email, purpose);
        if (existing != null && existing.getLastRequestedAt() != null
                && existing.getLastRequestedAt().isAfter(LocalDateTime.now().minusSeconds(OTP_REQUEST_COOLDOWN_SECONDS))) {
            throw new RuntimeException("Please wait a minute before requesting another code.");
        }
        String otp = String.format("%06d", new Random().nextInt(999999));
        EmailOtp record = existing != null ? existing : new EmailOtp();
        record.setEmail(email);
        record.setPurpose(purpose);
        record.setOtp(otp);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        record.setUsed(false);
        record.setVerifyAttempts(0);
        record.setLastRequestedAt(LocalDateTime.now());
        emailOtpRepo.save(record);

        if ("EMAIL_VERIFICATION".equals(purpose)) {
            emailService.sendEmailVerificationOtp(email, otp);
        } else {
            emailService.sendOtpEmail(email, otp);
        }
    }

    @Transactional
    public boolean verifyEmailOtp(String rawEmail, String purpose, String otp) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase();
        EmailOtp record = emailOtpRepo.findByEmailAndPurpose(email, purpose);
        if (record == null) {
            throw new RuntimeException("No OTP has been requested for this email");
        }
        if (record.isUsed()) {
            throw new RuntimeException("OTP has already been used");
        }
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired");
        }
        int attempts = record.getVerifyAttempts() != null ? record.getVerifyAttempts() : 0;
        if (attempts >= MAX_OTP_VERIFY_ATTEMPTS) {
            throw new RuntimeException("Too many incorrect attempts. Please request a new code.");
        }
        if (otp == null || !record.getOtp().equals(otp)) {
            record.setVerifyAttempts(attempts + 1);
            emailOtpRepo.save(record);
            throw new RuntimeException("Invalid OTP");
        }
        record.setUsed(true);
        emailOtpRepo.save(record);
        return true;
    }
}
