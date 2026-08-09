package com.cauverystore.service;

import com.cauverystore.entities.User;
import com.cauverystore.exception.UserNotFoundException;
import com.cauverystore.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userRepo.findByUsername(username));
    }

    public User getUserByUsername(String username) {
        User user = userRepo.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + username);
        }
        return user;
    }

    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    public User getUserById(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    public User suspendUser(Long userId, Long suspendedByUserId) {
        return suspendUser(userId, suspendedByUserId, null);
    }

    /**
     * Stops one account. Works for a customer or a seller - the difference is only what the
     * account can do, not how it is stopped.
     *
     * Clearing the refresh token was not enough on its own. It stops a new access token being
     * issued but does nothing to the one the person is already holding, and JwtFilter only ever
     * compared the token version - which nothing incremented. So suspending somebody mid-session
     * blocked their next login while leaving them free to carry on browsing, filling a basket
     * and placing orders until the token expired by itself. invalidateSessions bumps that
     * version, which makes every token already issued stop passing on the very next request.
     */
    public User suspendUser(Long userId, Long suspendedByUserId, String reason) {
        User user = getUserById(userId);
        user.setActive(false);
        user.setStatus("SUSPENDED");
        user.setSuspendedBy(suspendedByUserId);
        user.setSuspendedAt(LocalDateTime.now());
        user.setSuspensionReason(reason);
        // Deliberately not invalidateSessions(). A suspension is a wind-down: they keep the
        // session they are in so orders already placed can be seen out. New business is refused
        // where it is attempted instead. blockUser is the one that ends the session.
        user.setRefreshToken(null);
        return userRepo.save(user);
    }

    public User revokeUser(Long userId) {
        User user = getUserById(userId);
        user.setActive(true);
        user.setStatus("ACTIVE");
        user.setSuspendedBy(null);
        user.setSuspendedAt(null);
        user.setSuspensionReason(null);
        return userRepo.save(user);
    }

    public User blockUser(Long userId) {
        User user = getUserById(userId);
        user.setActive(false);
        user.setStatus("BLOCKED");
        // Same reasoning as suspendUser: without this the person keeps their live session and
        // carries on as though nothing happened.
        user.invalidateSessions();
        return userRepo.save(user);
    }

    public User unblockUser(Long userId) {
        User user = getUserById(userId);
        user.setActive(true);
        user.setStatus("ACTIVE");
        user.setSuspendedBy(null);
        user.setSuspendedAt(null);
        user.setSuspensionReason(null);
        return userRepo.save(user);
    }

    public User updateUserRole(Long userId, String role) {
        User user = getUserById(userId);
        user.setRole(com.cauverystore.entities.Role.valueOf(role));
        return userRepo.save(user);
    }

    public void updateUserProfile(Long id, User updated) {
        User user = getUserById(id);
        if (updated.getFullName() != null) {
            user.setFullName(updated.getFullName());
        }
        if (updated.getPhone() != null) {
            user.setPhone(updated.getPhone());
        }
        if (updated.getEmail() != null) {
            user.setEmail(updated.getEmail());
        }
        if (updated.getAddress() != null) {
            user.setAddress(updated.getAddress());
        }
        userRepo.save(user);
    }

    public void deleteUser(Long userId) {
        userRepo.deleteById(userId);
    }

    public boolean existsByUsername(String username) {
        return userRepo.existsByUsername(username);
    }

    public void logAudit(String authHeader, String message) {
    }

    public User getUser(Long id) {
        return getUserById(id);
    }

    public User promoteUser(Long id, String role) {
        User user = getUserById(id);
        user.setRole(com.cauverystore.entities.Role.valueOf(role));
        return userRepo.save(user);
    }

    public User demoteUser(Long id) {
        User user = getUserById(id);
        user.setRole(com.cauverystore.entities.Role.CUSTOMER);
        return userRepo.save(user);
    }
}
