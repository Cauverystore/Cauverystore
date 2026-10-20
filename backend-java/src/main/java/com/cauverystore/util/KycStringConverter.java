package com.cauverystore.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a seller's sensitive text (bank account number) encrypted, and reads it back in clear.
 *
 * Rows saved before encryption hold plain text; they read back unchanged and are encrypted the
 * next time they are saved. When the key is not configured, writes pass through as before so
 * existing records (an admin approving a seller, say) keep saving - the refusal to accept NEW
 * bank details without a key lives in the services that take them from the seller.
 */
@Converter
public class KycStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String value) {
        KycCrypto crypto = KycCrypto.get();
        if (value == null || value.isBlank() || !crypto.isConfigured()) return value;
        return crypto.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !KycCrypto.isEncrypted(stored)) return stored;
        KycCrypto crypto = KycCrypto.get();
        // Encrypted data with no key to read it: show nothing rather than fail the whole page.
        return crypto.isConfigured() ? crypto.decrypt(stored) : null;
    }
}
