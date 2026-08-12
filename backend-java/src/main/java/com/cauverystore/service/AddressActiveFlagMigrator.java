package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.repository.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfills active_flag for address rows that predate the soft-delete column.
 *
 * ddl-auto=update added active_flag as NULL for every existing row, and the soft-delete queries
 * treat only TRUE as active - so every pre-existing address was invisible to the address book and
 * to the dedupe search, making each checkout mint a fresh duplicate row for an address that was
 * already saved. Nothing was actually deleted then; these rows are all still live.
 *
 * Runs once on startup and is idempotent: it only touches rows whose active_flag is still NULL,
 * so a restart changes nothing.
 */
@Service
public class AddressActiveFlagMigrator {

    private static final Logger log = LoggerFactory.getLogger(AddressActiveFlagMigrator.class);

    private final AddressRepository addressRepo;

    public AddressActiveFlagMigrator(AddressRepository addressRepo) {
        this.addressRepo = addressRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            List<Address> legacy = new ArrayList<>();
            for (Address address : addressRepo.findAll()) {
                if (address.getActiveFlag() == null) {
                    address.setActiveFlag(true);
                    legacy.add(address);
                }
            }
            if (!legacy.isEmpty()) {
                addressRepo.saveAll(legacy);
                log.warn("Set active_flag=true on {} legacy address rows that predate soft delete.", legacy.size());
            }
        } catch (Exception e) {
            // Reference data must never stop the application starting.
            log.error("Address active_flag migration failed: {}", e.getMessage(), e);
        }
    }
}
