package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepo;

    public List<Address> getUserAddresses(User user) {
        return addressRepo.findByUser(user);
    }

    public Address getDefaultAddress(User user) {
        return addressRepo.findByUserAndIsDefaultTrue(user).orElse(null);
    }

    public Address getBillingAddress(User user) {
        return addressRepo.findByUserAndIsBillingTrue(user).orElse(null);
    }

    public Address addAddress(User user, Address address) {
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
        if (updated.getStreet() != null) existing.setStreet(updated.getStreet());
        if (updated.getCity() != null) existing.setCity(updated.getCity());
        if (updated.getState() != null) existing.setState(updated.getState());
        if (updated.getPincode() != null) existing.setPincode(updated.getPincode());
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

    @Transactional
    public void deleteAddress(User user, Long addressId) {
        Address address = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        addressRepo.delete(address);
    }

    private void clearDefaultFlag(User user) {
        addressRepo.findByUserAndIsDefaultTrue(user).ifPresent(addr -> {
            addr.setIsDefault(false);
            addressRepo.save(addr);
        });
    }
}
