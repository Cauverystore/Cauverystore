package com.cauverystore.exception;

public class PasswordResetRequiredException extends RuntimeException {
    private final String email;

    public PasswordResetRequiredException(String email) {
        super("Password reset required before login");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
