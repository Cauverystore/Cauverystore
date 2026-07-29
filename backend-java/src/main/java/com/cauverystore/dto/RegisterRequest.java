package com.cauverystore.dto;

public class RegisterRequest {
    private String fullName;
    private String username;
    private String email;
    private String password;
    private String phone;

    public String getFullName() {
        return this.fullName;
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPassword() {
        return this.password;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }
}
