package kz.minisiem.minisiem.service;

public class PasswordValidationException extends RuntimeException {

    public PasswordValidationException(String message) {
        super(message);
    }
}
