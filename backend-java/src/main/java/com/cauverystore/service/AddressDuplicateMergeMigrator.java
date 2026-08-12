package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.AddressRepository;
import com.cauverystore.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collapses duplicate addresses left behind before dedupe existed.
 *
 * Checkout used to save a fresh address row for every order, so a customer who ordered to the
 * same place N times can have N identical rows. Dedupe stops new lookalikes but cannot tidy the
 * old ones; this merges each cluster of identical (user, line, pincode) rows into the row with
 * the most orders behind it, repoints those orders' address links at the survivor and soft-deletes
 * the rest.
 *
 * Runs once on startup and is idempotent: once the lookalikes are gone there are no clusters of
 * size > 1 left to merge, so a restart changes nothing. NULL active_flag rows (which predate the
 * soft-delete column) are treated as active here.
 */
@Service
public class AddressDuplicateMergeMigrator {

    private static final Logger log = LoggerFactory.getLogger(AddressDuplicateMergeMigrator.class);

    private final AddressRepository addressRepo;
    private final OrderRepository orderRepo;

    public AddressDuplicateMergeMigrator(AddressRepository addressRepo, OrderRepository orderRepo) {
        this.addressRepo = addressRepo;
        this.orderRepo = orderRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            List<Address> all = addressRepo.findAll();
            Map<User, Map<String, List<Address>>> byUser = all.stream()
                    .filter(a -> a.getActiveFlag() == null || Boolean.TRUE.equals(a.getActiveFlag()))
                    .filter(a -> dedupeKey(a) != null)
                    .collect(Collectors.groupingBy(Address::getUser,
                            Collectors.groupingBy(this::dedupeKey, Collectors.toCollection(ArrayList::new))));

            int mergedRows = 0;
            int redirectedOrders = 0;
            for (Map<String, List<Address>> groups : byUser.values()) {
                for (List<Address> group : groups.values()) {
                    if (group.size() < 2) continue;

                    Address canonical = group.stream()
                            .max(Comparator.comparingLong((Address a) -> orderRepo.countByAddress_Id(a.getId()))
                                    .thenComparing(a -> a.getCreatedAt(),
                                            Comparator.nullsFirst(Comparator.naturalOrder())))
                            .orElse(group.get(0));

                    for (Address dupe : group) {
                        if (dupe.getId().equals(canonical.getId())) continue;
                        redirectedOrders += orderRepo.redirectOrders(dupe, canonical);
                        // The customer's default/billing choice might have been on the losing row.
                        if (Boolean.TRUE.equals(dupe.getIsDefault())
                                && !Boolean.TRUE.equals(canonical.getIsDefault())) {
                            canonical.setIsDefault(true);
                        }
                        if (Boolean.TRUE.equals(dupe.getIsBilling())
                                && !Boolean.TRUE.equals(canonical.getIsBilling())) {
                            canonical.setIsBilling(true);
                        }
                        dupe.setActiveFlag(false);
                        addressRepo.save(dupe);
                        mergedRows++;
                    }
                    addressRepo.save(canonical);
                }
            }

            if (mergedRows > 0) {
                log.warn("Merged {} duplicate address rows (redirected {} order links to the "
                        + "surviving row per user + line + pincode).", mergedRows, redirectedOrders);
            }
        } catch (Exception e) {
            // Reference data must never stop the application starting.
            log.error("Address duplicate-merge migration failed: {}", e.getMessage(), e);
        }
    }

    private String dedupeKey(Address address) {
        String line = address.getLine1() != null && !address.getLine1().isBlank()
                ? address.getLine1() : address.getStreet();
        String pincode = address.getPincode();
        if (line == null || line.isBlank() || pincode == null || pincode.isBlank()) {
            return null; // no key, no cluster - cannot be a duplicate without either
        }
        return line.trim().toLowerCase() + "|" + pincode.trim().toLowerCase();
    }
}
