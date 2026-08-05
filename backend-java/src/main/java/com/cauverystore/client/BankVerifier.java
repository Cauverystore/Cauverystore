package com.cauverystore.client;

import java.util.Map;

/** Pluggable bank account verification (penny-drop). Simulated by default. */
public interface BankVerifier {

    String getName();

    default boolean isSimulated() {
        return true;
    }

    /** Returns e.g. {verified:true, accountName:"...", reference:"...", simulated:true}. */
    Map<String, Object> verify(String accountNumber, String ifsc, String accountName);
}
