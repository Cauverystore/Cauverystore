package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.AddressRepository;
import com.cauverystore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressService {

    /**
     * Once an order reaches any of these states it no longer counts as "in use" for the delete
     * guard - the goods have moved on (or never will), so unlinking the address cannot orphan a
     * live shipment.
     */
    private static final List<String> TERMINAL_ORDER_STATUSES = List.of("CANCELLED", "REFUNDED", "DELIVERED");

    private final AddressRepository addressRepo;
    private final OrderRepository orderRepo;

    public List<Address> getUserAddresses(User user) {
        return addressRepo.findByUserAndActiveFlagTrue(user);
    }

    public Address getDefaultAddress(User user) {
        return addressRepo.findByUserAndIsDefaultTrue(user).orElse(null);
    }

    public Address getBillingAddress(User user) {
        return addressRepo.findByUserAndIsBillingTrue(user).orElse(null);
    }

    /**
     * Saves an address unless the user already has an active one with the same primary line and
     * pincode - in that case the existing row is reused so one user_id + line1 + pincode can never
     * accumulate lookalike rows across repeat checkouts.
     */
    @Transactional
    public Address addAddress(User user, Address address) {
        normalize(address);
        Address duplicate = findDuplicate(user, address);
        if (duplicate != null) {
            if (Boolean.TRUE.equals(address.getIsDefault())) {
                clearDefaultFlag(user);
                duplicate.setIsDefault(true);
                return addressRepo.save(duplicate);
            }
            return duplicate;
        }
        address.setUser(user);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefaultFlag(user);
        }
        return addressRepo.save(address);
    }

    public Address updateAddress(User user, Long addressId, Address updated) {
        Address existing = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        if (!existing.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        if (updated.getFullName() != null) existing.setFullName(updated.getFullName());
        if (updated.getPhone() != null) existing.setPhone(updated.getPhone());
        if (updated.getLine1() != null) existing.setLine1(updated.getLine1());
        if (updated.getLine2() != null) existing.setLine2(updated.getLine2());
        if (updated.getStreet() != null) existing.setStreet(updated.getStreet());
        if (updated.getCity() != null) existing.setCity(updated.getCity());
        if (updated.getState() != null) existing.setState(updated.getState());
        if (updated.getPincode() != null) existing.setPincode(updated.getPincode());
        if (updated.getCountry() != null) existing.setCountry(updated.getCountry());
        if (updated.getLabel() != null) existing.setLabel(updated.getLabel());
        if (updated.getLandmark() != null) existing.setLandmark(updated.getLandmark());
        if (updated.getDeliveryInstructions() != null) existing.setDeliveryInstructions(updated.getDeliveryInstructions());
        if (updated.getIsDefault() != null) {
            if (Boolean.TRUE.equals(updated.getIsDefault())) {
                clearDefaultFlag(user);
            }
            existing.setIsDefault(updated.getIsDefault());
        }
        if (updated.getIsBilling() != null) existing.setIsBilling(updated.getIsBilling());
        return addressRepo.save(existing);
    }

    /**
     * Soft-deletes an address. An address still referenced by a live order (anything not yet
     * CANCELLED/REFUNDED/DELIVERED) cannot be removed - the row is the order's shipping record.
     */
    @Transactional
    public void deleteAddress(User user, Long addressId) {
        Address address = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        long liveOrders = orderRepo.countByAddress_IdAndStatusNotIn(addressId, TERMINAL_ORDER_STATUSES);
        if (liveOrders > 0) {
            throw new RuntimeException("This address is attached to " + liveOrders
                    + " active order(s) and cannot be removed. You can remove it once those orders are delivered or cancelled.");
        }
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            address.setIsDefault(false);
        }
        address.setActiveFlag(false);
        addressRepo.save(address);
    }

    @Transactional
    public Address restoreAddress(Long addressId) {
        Address address = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        address.setActiveFlag(true);
        return addressRepo.save(address);
    }

    /**
     * Admin view: every address row (active and soft-deleted) with its owner and order usage count.
     */
    public List<Map<String, Object>> getAllAddressesWithUsage() {
        List<Address> all = new ArrayList<>(addressRepo.findAll());
        all.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return all.stream().map(addr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", addr.getId());
            m.put("fullName", addr.getFullName());
            m.put("phone", addr.getPhone());
            m.put("line1", addr.getLine1());
            m.put("line2", addr.getLine2());
            m.put("street", addr.getStreet());
            m.put("city", addr.getCity());
            m.put("state", addr.getState());
            m.put("pincode", addr.getPincode());
            m.put("country", addr.getCountry());
            m.put("isDefault", addr.getIsDefault());
            m.put("active", Boolean.TRUE.equals(addr.getActiveFlag()));
            m.put("createdAt", addr.getCreatedAt() != null ? addr.getCreatedAt().toString() : null);
            User owner = addr.getUser();
            m.put("userName", owner != null ? owner.getFullName() : null);
            m.put("userEmail", owner != null ? owner.getEmail() : null);
            m.put("usageCount", orderRepo.countByAddress_Id(addr.getId()));
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * Looks up an existing active address for the user matching the dedupe key
     * (user_id + primary line + pincode). The primary line falls back to the legacy street
     * column so rows saved before line1 existed still dedupe correctly.
     */
    private Address findDuplicate(User user, Address incoming) {
        String line = normalizeLine(incoming);
        String pincode = incoming.getPincode() != null ? incoming.getPincode().trim().toLowerCase() : null;
        if (line == null || pincode == null) {
            return null;
        }
        return addressRepo.findByUserAndActiveFlagTrue(user).stream()
                .filter(a -> pincode.equals(a.getPincode() != null ? a.getPincode().trim().toLowerCase() : null))
                .filter(a -> line.equals(normalizeLine(a)))
                .findFirst()
                .orElse(null);
    }

    private String normalizeLine(Address address) {
        String line = address.getLine1() != null && !address.getLine1().isBlank()
                ? address.getLine1()
                : address.getStreet();
        return line != null ? line.trim().toLowerCase() : null;
    }

    private void normalize(Address address) {
        if (address.getActiveFlag() == null) {
            address.setActiveFlag(true);
        }
        if (address.getCountry() == null || address.getCountry().isBlank()) {
            address.setCountry("India");
        }
        if ((address.getLine1() == null || address.getLine1().isBlank())
                && address.getStreet() != null && !address.getStreet().isBlank()) {
            address.setLine1(address.getStreet());
        } else if ((address.getStreet() == null || address.getStreet().isBlank())
                && address.getLine1() != null && !address.getLine1().isBlank()) {
            address.setStreet(address.getLine1());
        }
    }

    private void clearDefaultFlag(User user) {
        addressRepo.findByUserAndIsDefaultTrue(user).ifPresent(addr -> {
            addr.setIsDefault(false);
            addressRepo.save(addr);
        });
    }
}
