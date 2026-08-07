package com.cauverystore.service;

import com.cauverystore.entities.BusinessType;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves the legal constitution out of business_type, where it used to be stored.
 *
 * The onboarding form originally asked "Business Type" and offered Sole Proprietorship,
 * Partnership, LLP, Private Limited and so on - the constitution of the business. The field
 * now means how the seller trades: Retail, Wholesale, Distributor, Service, Manufacturing.
 * Those are different questions with different answers, and every existing seller has the old
 * kind of value sitting in the column.
 *
 * Simply redefining the column would leave a Private Limited company recorded as an invalid
 * trade type, and the constitution - which is on their GST registration and cannot be
 * reconstructed - gone for good. So the old value is copied into constitutionOfBusiness and
 * the trade type is left blank for the seller to answer.
 *
 * Runs once on startup and is idempotent: it only touches rows whose business_type is not a
 * valid trade type and whose constitution is still empty, so a restart changes nothing.
 */
@Service
public class SellerBusinessFieldMigrator {

    private static final Logger log = LoggerFactory.getLogger(SellerBusinessFieldMigrator.class);

    private final SellerRegistrationRepository regRepo;

    public SellerBusinessFieldMigrator(SellerRegistrationRepository regRepo) {
        this.regRepo = regRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        try {
            List<SellerRegistration> moved = new ArrayList<>();
            for (SellerRegistration reg : regRepo.findAll()) {
                String current = reg.getBusinessType();
                if (current == null || current.isBlank()) continue;
                if (BusinessType.isValid(current)) continue;              // already a trade type
                if (reg.getConstitutionOfBusiness() != null
                        && !reg.getConstitutionOfBusiness().isBlank()) continue;   // already moved

                reg.setConstitutionOfBusiness(current);
                // Left blank rather than guessed. A Private Limited company might be retail,
                // wholesale or manufacturing, and inventing an answer would put a fabricated
                // value on their invoices and in the GST reports segmented by it.
                reg.setBusinessType(null);
                moved.add(reg);
            }
            if (!moved.isEmpty()) {
                regRepo.saveAll(moved);
                log.warn("Moved the legal constitution out of business_type for {} sellers. They "
                        + "now have no trade type and will be asked for one.", moved.size());
            }
        } catch (Exception e) {
            // Reference data must never stop the application starting.
            log.error("Seller business-field migration failed: {}", e.getMessage(), e);
        }
    }
}
