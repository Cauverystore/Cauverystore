package com.cauverystore.config;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SeedDataRunner implements CommandLineRunner {
    private final UserRepository userRepo;
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final ProductImageRepository imageRepo;
    private final DiscountRepository discountRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final PasswordEncoder passwordEncoder;

    public SeedDataRunner(UserRepository ur, CategoryRepository cr, ProductRepository pr,
                          ProductImageRepository pir, DiscountRepository dr,
                          PermissionRepository perms, RolePermissionRepository rp,
                          SellerRegistrationRepository srr,
                          PasswordEncoder pe) {
        this.userRepo = ur; this.categoryRepo = cr; this.productRepo = pr;
        this.imageRepo = pir; this.discountRepo = dr;
        this.permissionRepo = perms; this.rolePermissionRepo = rp;
        this.sellerRegRepo = srr;
        this.passwordEncoder = pe;
    }

    @Override
    public void run(String... args) {
        User oldSuper = userRepo.findByEmail("super@cauverystore.in");
        if (oldSuper != null) userRepo.delete(oldSuper);

        createOrUpdateUser("Super Admin", "superadmin", "superadmin@cauverystore.in", "super123", Role.SUPER_ADMIN);
        createOrUpdateUser("Test Customer", "customer", "customer@cauverystore.in", "admin123", Role.CUSTOMER);
        createOrUpdateUser("Admin", "admin", "admin@cauverystore.in", "admin123", Role.ADMIN);
        createOrUpdateUser("Seller", "seller", "seller@cauverystore.in", "seller123", Role.SELLER);
        createOrUpdateUser("Executive", "executive", "executive@cauverystore.in", "exec123", Role.EXECUTIVE);

        if (categoryRepo.count() == 0) {
            String[][] cats = {{"Electronics","Smartphones, laptops, and gadgets"},{"Fashion","Clothing, shoes, and accessories"},{"Home & Kitchen","Furniture, appliances, and decor"},{"Books","Fiction, non-fiction, and educational"},{"Sports","Fitness equipment and sportswear"}};
            for (String[] c : cats) {
                Category cat = new Category(); cat.setName(c[0]); cat.setDescription(c[1]); categoryRepo.save(cat);
            }
        }

        User sellerUser = userRepo.findByEmail("seller@cauverystore.in");

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
                if (sellerUser != null) product.setSellerId(sellerUser.getId());
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
        // Fix existing products missing sellerId
        if (sellerUser != null) {
            List<Product> allProducts = productRepo.findAll();
            List<Product> orphanProducts = allProducts.stream().filter(p -> p.getSellerId() == null).collect(Collectors.toList());
            if (!orphanProducts.isEmpty()) {
                for (Product p : orphanProducts) {
                    p.setSellerId(sellerUser.getId());
                    productRepo.save(p);
                }
                System.out.println("=== Updated " + orphanProducts.size() + " products with sellerId ===");
            }
        }

        if (sellerUser != null && sellerRegRepo.findByUserId(sellerUser.getId()).isEmpty()) {
            SellerRegistration reg = new SellerRegistration();
            reg.setUser(sellerUser);
            reg.setBusinessName("Cauvery Retail Store");
            reg.setContactPerson("Seller");
            reg.setBusinessEmail("seller@cauverystore.in");
            reg.setBusinessPhone("9876543210");
            reg.setBusinessAddress("42, Gandhi Nagar, Adyar, Chennai, Tamil Nadu 600020");
            reg.setCity("Chennai");
            reg.setState("Tamil Nadu");
            reg.setPincode("600020");
            reg.setBusinessType("Retail");
            reg.setGstin("33ABCDE1234F1Z5");
            reg.setPanNumber("ABCDE1234F");
            reg.setStatus("APPROVED");
            reg.setOnboardingStep(5);
            sellerRegRepo.save(reg);
            System.out.println("=== Created SellerRegistration for seller@cauverystore.in ===");
        }

        if (permissionRepo.count() == 0) {
            String[][] permDefs = {
                {"user.create", "Create users", "USER", "CREATE"},
                {"user.read", "View users", "USER", "READ"},
                {"user.update", "Edit users", "USER", "UPDATE"},
                {"user.delete", "Delete users", "USER", "DELETE"},
                {"product.create", "Create products", "PRODUCT", "CREATE"},
                {"product.read", "View products", "PRODUCT", "READ"},
                {"product.update", "Edit products", "PRODUCT", "UPDATE"},
                {"product.delete", "Delete products", "PRODUCT", "DELETE"},
                {"order.create", "Create orders", "ORDER", "CREATE"},
                {"order.read", "View orders", "ORDER", "READ"},
                {"order.update", "Edit orders", "ORDER", "UPDATE"},
                {"order.delete", "Delete orders", "ORDER", "DELETE"},
                {"category.create", "Create categories", "CATEGORY", "CREATE"},
                {"category.read", "View categories", "CATEGORY", "READ"},
                {"category.update", "Edit categories", "CATEGORY", "UPDATE"},
                {"category.delete", "Delete categories", "CATEGORY", "DELETE"},
                {"settings.read", "View settings", "SETTINGS", "READ"},
                {"settings.update", "Edit settings", "SETTINGS", "UPDATE"},
                {"permissions.read", "View permissions", "PERMISSION", "READ"},
                {"permissions.update", "Edit permissions", "PERMISSION", "UPDATE"},
                {"impersonate", "Impersonate users", "IMPERSONATION", "EXECUTE"},
                {"cart.read", "View cart", "CART", "READ"},
                {"cart.create", "Add to cart", "CART", "CREATE"},
                {"cart.delete", "Remove from cart", "CART", "DELETE"},
                {"payment.read", "View payments", "PAYMENT", "READ"},
                {"payment.create", "Process payments", "PAYMENT", "CREATE"},
                {"report.read", "View reports", "REPORT", "READ"},
                {"inventory.read", "View inventory", "INVENTORY", "READ"},
                {"inventory.update", "Update inventory", "INVENTORY", "UPDATE"},
            };
            for (String[] p : permDefs) {
                Permission perm = new Permission();
                perm.setName(p[0]); perm.setDescription(p[1]);
                perm.setResource(p[2]); perm.setAction(p[3]);
                permissionRepo.save(perm);
            }
            // Assign permissions to roles
            String[][] rolePermAssign = {
                {"SUPER_ADMIN", "user.create,user.read,user.update,user.delete,product.create,product.read,product.update,product.delete,order.create,order.read,order.update,order.delete,category.create,category.read,category.update,category.delete,settings.read,settings.update,permissions.read,permissions.update,impersonate,cart.read,cart.create,cart.delete,payment.read,payment.create,report.read,inventory.read,inventory.update"},
                {"ADMIN", "user.read,user.update,product.read,product.update,order.read,order.update,category.read,category.update,report.read,inventory.read,inventory.update,settings.read"},
                {"SELLER", "product.create,product.read,product.update,product.delete,order.read,order.update,category.read,inventory.read,inventory.update"},
                {"EXECUTIVE", "product.read,product.update,order.read,order.update,report.read,inventory.read,inventory.update,category.read"},
                {"CUSTOMER", "cart.read,cart.create,cart.delete,order.create,order.read,payment.create,product.read,category.read"},
            };
            for (String[] assign : rolePermAssign) {
                String role = assign[0];
                String[] permNames = assign[1].split(",");
                for (String pName : permNames) {
                    Permission perm = permissionRepo.findByName(pName.trim()).orElse(null);
                    if (perm != null) {
                        RolePermission rp = new RolePermission();
                        rp.setRole(role);
                        rp.setPermission(perm);
                        rolePermissionRepo.save(rp);
                    }
                }
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
