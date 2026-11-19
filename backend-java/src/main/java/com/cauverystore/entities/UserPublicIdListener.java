package com.cauverystore.entities;

import com.cauverystore.service.PublicIdService;
import jakarta.persistence.PrePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives every new user an account ID the moment they are first saved, whichever sign-up path
 * created them. A failure here must never stop someone registering, so it only logs; the
 * start-up backfill fills any gap.
 */
public class UserPublicIdListener {

    private static final Logger log = LoggerFactory.getLogger(UserPublicIdListener.class);

    @PrePersist
    public void assign(User user) {
        if (user.getPublicId() != null) return;
        PublicIdService service = PublicIdService.get();
        if (service == null) return;
        try {
            user.setPublicId(service.nextFor(user.getRole()));
        } catch (Exception e) {
            log.warn("Could not assign an account ID to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
