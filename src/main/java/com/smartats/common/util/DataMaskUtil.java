package com.smartats.common.util;

/**
 * Data masking utility
 * <p>
 * Masks sensitive fields such as phone numbers and email addresses for display purposes.
 * Masked values are only used for frontend rendering and are <b>never written to the database</b>.
 */
public class DataMaskUtil {

    private DataMaskUtil() {}

    /**
     * Mask a phone number: keep the first 3 and last 4 digits, replacing the middle with ****
     * <p>
     * Example: 13812345678 → 138****5678
     *
     * @param phone the original phone number, may be null
     * @return the masked phone number; returned unchanged when null or shorter than 7 characters
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Mask an email address: keep the first 2 characters before the @, replace the middle with ****, keep everything after the @ intact
     * <p>
     * Example: zhangsan@example.com → zh****@example.com
     *
     * @param email the original email address, may be null
     * @return the masked email address; returned unchanged when null or when it contains no @
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex); // includes the @

        if (local.length() <= 2) {
            return local + "****" + domain;
        }
        return local.substring(0, 2) + "****" + domain;
    }
}
