package com.cauverystore.config;

import com.cauverystore.service.SellerTeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Makes sure the seven built-in seller-team roles exist with their current permissions. */
@Component
public class SellerTeamRoleSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SellerTeamRoleSeeder.class);

    private final SellerTeamService teamService;

    public SellerTeamRoleSeeder(SellerTeamService teamService) {
        this.teamService = teamService;
    }

    @Override
    public void run(String... args) {
        try {
            teamService.ensureBuiltInRoles();
        } catch (Exception e) {
            // The store must still start; the next start tries again.
            log.error("Could not set up the built-in seller-team roles: {}", e.getMessage());
        }
    }
}
