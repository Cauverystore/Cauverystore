package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.SellerStore;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.SellerStoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a shopper is allowed to know about who sells a product.
 *
 * <h2>Why this exists</h2>
 *
 * The storefront's whole claim is that every product comes from a real shop or trader in Tamil
 * Nadu, yet the product page had no way to say which one: a product carries only a seller id,
 * and the seller's own records sit behind the seller login.
 *
 * <h2>What it gives away, and what it does not</h2>
 *
 * The shop's name and its town - what is painted on the shopfront. Nothing else: the
 * registration also holds a phone number, an email, a street address, PAN and bank details, and
 * this endpoint is open to anyone, so the answer is built field by field rather than by handing
 * back the entity and trusting an ignore list.
 *
 * Only an approved seller is named. A product added by the marketplace's own staff has no
 * registration behind it, and one whose seller is still being vetted has not earned the
 * endorsement a name on the page implies. In both cases the answer is empty and the page says
 * nothing rather than something untrue.
 */
@Service
public class PublicSellerService {

    private final ProductRepository productRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final SellerStoreRepository storeRepo;

    public PublicSellerService(ProductRepository productRepo,
                               SellerRegistrationRepository sellerRegRepo,
                               SellerStoreRepository storeRepo) {
        this.productRepo = productRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.storeRepo = storeRepo;
    }

    /** The seller of a product, or an empty map when there is no approved seller to name. */
    @Transactional(readOnly = true)
    public Map<String, Object> sellerOfProduct(Long productId) {
        Map<String, Object> seller = new LinkedHashMap<>();
        Long sellerId = productRepo.findById(productId).map(Product::getSellerId).orElse(null);
        if (sellerId == null) return seller;

        Optional<SellerRegistration> found = sellerRegRepo.findByUserId(sellerId);
        if (found.isEmpty() || !"APPROVED".equalsIgnoreCase(found.get().getStatus())) return seller;
        SellerRegistration reg = found.get();

        // The name the seller chose for their shop front wins over the registered business
        // name, which is often a proprietor's own name or a legal entity nobody shops by.
        String name = storeRepo.findBySellerId(sellerId)
                .map(SellerStore::getStoreName)
                .filter(n -> !n.isBlank())
                .orElse(reg.getBusinessName());
        if (name == null || name.isBlank()) return seller;

        seller.put("name", name.trim());
        if (reg.getCity() != null && !reg.getCity().isBlank()) seller.put("city", reg.getCity().trim());
        if (reg.getState() != null && !reg.getState().isBlank()) seller.put("state", reg.getState().trim());
        return seller;
    }
}
