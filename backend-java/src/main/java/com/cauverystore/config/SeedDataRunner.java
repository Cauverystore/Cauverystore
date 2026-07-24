package com.cauverystore.config;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedDataRunner implements CommandLineRunner {
    private final UserRepository userRepo;
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final ProductImageRepository imageRepo;
    private final DiscountRepository discountRepo;
    private final PasswordEncoder passwordEncoder;

    public SeedDataRunner(UserRepository ur, CategoryRepository cr, ProductRepository pr,
                          ProductImageRepository pir, DiscountRepository dr, PasswordEncoder pe) {
        this.userRepo = ur; this.categoryRepo = cr; this.productRepo = pr;
        this.imageRepo = pir; this.discountRepo = dr; this.passwordEncoder = pe;
    }

    @Override
    public void run(String... args) {
        // Remove old superadmin email if it exists (migrated to super@cauverystore.in)
        User oldSuper = userRepo.findByEmail("superadmin@cauverystore.in");
        if (oldSuper != null) userRepo.delete(oldSuper);

        createOrUpdateUser("Test Customer", "customer", "customer@cauverystore.in", "admin123", Role.CUSTOMER);
        createOrUpdateUser("Admin", "admin", "admin@cauverystore.in", "admin123", Role.ADMIN);
        createOrUpdateUser("Seller", "seller", "seller@cauverystore.in", "seller123", Role.SELLER);
        createOrUpdateUser("Super Admin", "superadmin", "super@cauverystore.in", "super123", Role.SUPER_ADMIN);
        createOrUpdateUser("Executive", "executive", "executive@cauverystore.in", "exec123", Role.EXECUTIVE);

        if (categoryRepo.count() == 0) {
            String[][] cats = {{"Electronics","Smartphones, laptops, and gadgets"},{"Fashion","Clothing, shoes, and accessories"},{"Home & Kitchen","Furniture, appliances, and decor"},{"Books","Fiction, non-fiction, and educational"},{"Sports","Fitness equipment and sportswear"}};
            for (String[] c : cats) {
                Category cat = new Category(); cat.setName(c[0]); cat.setDescription(c[1]); categoryRepo.save(cat);
            }
        }

        if (productRepo.count() == 0) {
            Object[][] prods = {
                {"iPhone 15 Pro","Apple",134990.0,50,"Apple iPhone 15 Pro, 256GB, Titanium Black","Electronics","https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=400",10.0},
                {"Samsung Galaxy S24","Samsung",129999.0,35,"Samsung Galaxy S24 Ultra, 512GB","Electronics","https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?w=400",8.0},
                {"MacBook Air M3","Apple",164900.0,20,"Apple MacBook Air 15-inch, M3 chip, 16GB RAM","Electronics","https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400",5.0},
                {"Sony WH-1000XM5","Sony",29990.0,60,"Wireless Noise Cancelling Headphones","Electronics","https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=400",12.0},
            };
            for (Object[] p : prods) {
                Product product = new Product();
                product.setName((String)p[0]); product.setBrand((String)p[1]);
                product.setPrice((Double)p[2]); product.setStock((Integer)p[3]);
                product.setDescription((String)p[4]); product.setActive(true);
                product.setProductStatus("published");
                Category cat = categoryRepo.findByName((String)p[5]).orElse(null);
                if (cat != null) product.setCategory(cat);
                product = productRepo.save(product);

                ProductImage img = new ProductImage();
                img.setProduct(product); img.setUrl((String)p[6]); img.setMain(true); imageRepo.save(img);

                Discount d = new Discount();
                d.setProduct(product); d.setType("PERCENTAGE"); d.setValue((Double)p[7]);
                d.setStartDate(java.time.LocalDate.now()); d.setEndDate(java.time.LocalDate.now().plusMonths(3));
                d.setActive(true); discountRepo.save(d);
            }
        }
        System.out.println("=== Seed data loaded successfully ===");
    }

    private void createOrUpdateUser(String fullName, String username, String email, String rawPassword, Role role) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setUsername(username);
            user.setFullName(fullName);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setStatus("ACTIVE");
            user.setActive(true);
            user.setFailedLoginAttempts(0);
            userRepo.save(user);
        } else {
            boolean changed = false;
            if (!user.getFullName().equals(fullName)) { user.setFullName(fullName); changed = true; }
            if (!user.getUsername().equals(username)) { user.setUsername(username); changed = true; }
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) { user.setPassword(passwordEncoder.encode(rawPassword)); changed = true; }
            if (user.getRole() != role) { user.setRole(role); changed = true; }
            if (!"ACTIVE".equals(user.getStatus())) { user.setStatus("ACTIVE"); changed = true; }
            if (!user.isActive()) { user.setActive(true); changed = true; }
            if (changed) userRepo.save(user);
        }
    }
}
