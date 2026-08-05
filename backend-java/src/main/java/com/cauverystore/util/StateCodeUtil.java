package com.cauverystore.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps Indian state names to their GST state codes and the reverse. */
public final class StateCodeUtil {

    private static final Map<String, String> STATE_TO_CODE = new LinkedHashMap<>();
    private static final Map<String, String> CODE_TO_STATE = new LinkedHashMap<>();

    static {
        STATE_TO_CODE.put("JAMMU AND KASHMIR", "01");
        STATE_TO_CODE.put("HIMACHAL PRADESH", "02");
        STATE_TO_CODE.put("PUNJAB", "03");
        STATE_TO_CODE.put("CHANDIGARH", "04");
        STATE_TO_CODE.put("UTTARAKHAND", "05");
        STATE_TO_CODE.put("HARYANA", "06");
        STATE_TO_CODE.put("DELHI", "07");
        STATE_TO_CODE.put("RAJASTHAN", "08");
        STATE_TO_CODE.put("UTTAR PRADESH", "09");
        STATE_TO_CODE.put("BIHAR", "10");
        STATE_TO_CODE.put("SIKKIM", "11");
        STATE_TO_CODE.put("ARUNACHAL PRADESH", "12");
        STATE_TO_CODE.put("NAGALAND", "13");
        STATE_TO_CODE.put("MANIPUR", "14");
        STATE_TO_CODE.put("MIZORAM", "15");
        STATE_TO_CODE.put("TRIPURA", "16");
        STATE_TO_CODE.put("MEGHALAYA", "17");
        STATE_TO_CODE.put("ASSAM", "18");
        STATE_TO_CODE.put("WEST BENGAL", "19");
        STATE_TO_CODE.put("JHARKHAND", "20");
        STATE_TO_CODE.put("ODISHA", "21");
        STATE_TO_CODE.put("CHHATTISGARH", "22");
        STATE_TO_CODE.put("MADHYA PRADESH", "23");
        STATE_TO_CODE.put("GUJARAT", "24");
        STATE_TO_CODE.put("DADRA AND NAGAR HAVELI AND DAMAN AND DIU", "26");
        STATE_TO_CODE.put("MAHARASHTRA", "27");
        STATE_TO_CODE.put("ANDHRA PRADESH", "37");
        STATE_TO_CODE.put("KARNATAKA", "29");
        STATE_TO_CODE.put("GOA", "30");
        STATE_TO_CODE.put("LAKSHADWEEP", "31");
        STATE_TO_CODE.put("KERALA", "32");
        STATE_TO_CODE.put("TAMIL NADU", "33");
        STATE_TO_CODE.put("PUDUCHERRY", "34");
        STATE_TO_CODE.put("ANDAMAN AND NICOBAR ISLANDS", "35");
        STATE_TO_CODE.put("TELANGANA", "36");
        STATE_TO_CODE.put("LADAKH", "38");
        STATE_TO_CODE.put("OTHER TERRITORY", "97");
        STATE_TO_CODE.put("CENTRE JURISDICTION", "99");
        STATE_TO_CODE.forEach((k, v) -> CODE_TO_STATE.put(v, k));
    }

    private StateCodeUtil() {
    }

    public static String stateNameToCode(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return STATE_TO_CODE.get(state.trim().toUpperCase());
    }

    public static String codeToStateName(String code) {
        if (code == null) {
            return null;
        }
        return CODE_TO_STATE.get(code.trim());
    }

    public static Map<String, String> stateCodes() {
        return new LinkedHashMap<>(STATE_TO_CODE);
    }
}
