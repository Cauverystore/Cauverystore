package com.cauverystore.dto;

import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the payload contract.
 *
 * The field names are the contract, so a rename that looks harmless in Java would silently
 * break whatever consumes this. These assertions exist to make that a failing test rather than
 * a support call.
 */
class SellerBillingProfileResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SellerRegistration seller() {
        User user = new User();
        user.setId(12L);

        SellerRegistration reg = new SellerRegistration();
        reg.setUser(user);
        reg.setBusinessName("Ananya Traders");
        reg.setGstin("33AABCA9012K1ZB");
        reg.setBusinessType("Retail");
        reg.setBusinessCategory("Kirana / General Merchant");
        reg.setBusinessAddress("14 Mettupalayam Road");
        reg.setCity("Coimbatore");
        reg.setState("Tamil Nadu");
        reg.setPincode("641043");
        return reg;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serialise(SellerRegistration reg) throws Exception {
        return mapper.readValue(
                mapper.writeValueAsString(SellerBillingProfileResponse.from(reg)), Map.class);
    }

    @Test
    void shouldSerialiseExactlyTheAgreedKeys() throws Exception {
        Map<String, Object> json = serialise(seller());

        assertEquals(java.util.Set.of("seller_id", "business_name", "gstin",
                        "business_type", "business_category", "business_address"),
                json.keySet(),
                "the payload is the contract - extra or renamed keys break its consumers");
    }

    @Test
    void shouldCarryTheValuesUnderTheirContractNames() throws Exception {
        Map<String, Object> json = serialise(seller());

        assertEquals("12", json.get("seller_id"));
        assertEquals("Ananya Traders", json.get("business_name"));
        assertEquals("33AABCA9012K1ZB", json.get("gstin"));
        assertEquals("Retail", json.get("business_type"));
        assertEquals("Kirana / General Merchant", json.get("business_category"));
    }

    @Test
    void shouldJoinTheAddressAsItPrintsOnAnInvoice() throws Exception {
        assertEquals("14 Mettupalayam Road, Coimbatore, Tamil Nadu, 641043",
                serialise(seller()).get("business_address"));
    }

    @Test
    void shouldSkipMissingAddressPartsRatherThanLeaveGapsAndCommas() throws Exception {
        SellerRegistration reg = seller();
        reg.setCity(null);
        reg.setPincode("  ");

        assertEquals("14 Mettupalayam Road, Tamil Nadu",
                serialise(reg).get("business_address"));
    }

    @Test
    void shouldNotLeakBankOrIdentityDetails() throws Exception {
        // The status endpoint returns the whole entity; this one must not. Bank details,
        // Aadhaar and document URLs have no business in a billing payload.
        SellerRegistration reg = seller();
        reg.setBankAccountNumber("123456789012");
        reg.setAadhaarNumber("999988887777");
        reg.setPanNumber("AABCA9012K");

        String json = mapper.writeValueAsString(SellerBillingProfileResponse.from(reg));

        assertFalse(json.contains("123456789012"), "bank account must not travel");
        assertFalse(json.contains("999988887777"), "Aadhaar must not travel");

        // The PAN is checked as its own key rather than as a substring: a GSTIN is BUILT from
        // the PAN - state code, then the 10-character PAN, then entity code, Z and checksum -
        // so 33AABCA9012K1ZB necessarily contains AABCA9012K. That is the GSTIN doing its job,
        // not the PAN leaking.
        assertFalse(serialise(reg).containsKey("pan"));
        assertFalse(serialise(reg).containsKey("pan_number"));
    }

    @Test
    void shouldTolerateARegistrationWithNothingFilledIn() throws Exception {
        Map<String, Object> json = serialise(new SellerRegistration());

        assertNull(json.get("business_address"), "an empty address is null, not an empty string");
        assertNull(json.get("seller_id"));
    }

    @Test
    void requestShouldAcceptBothCamelCaseAndSnakeCase() throws Exception {
        // The screens send camelCase and the published contract is snake_case. Accepting one
        // would have meant breaking the frontend or refusing the documented shape.
        SellerRegistrationRequest camel = mapper.readValue(
                "{\"businessType\":\"Retail\",\"businessCategory\":\"Footwear\"}",
                SellerRegistrationRequest.class);
        SellerRegistrationRequest snake = mapper.readValue(
                "{\"business_type\":\"Retail\",\"business_category\":\"Footwear\"}",
                SellerRegistrationRequest.class);

        assertEquals("Retail", camel.getBusinessType());
        assertEquals("Retail", snake.getBusinessType());
        assertEquals("Footwear", camel.getBusinessCategory());
        assertEquals("Footwear", snake.getBusinessCategory());
    }
}
