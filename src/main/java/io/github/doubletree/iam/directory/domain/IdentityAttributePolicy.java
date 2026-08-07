package io.github.doubletree.iam.directory.domain;

import java.net.IDN;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;

public final class IdentityAttributePolicy {

    private IdentityAttributePolicy() {
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String value = email.strip();
        int separator = value.lastIndexOf('@');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Email address is invalid");
        }
        String localPart = value.substring(0, separator);
        String domain = IDN.toASCII(value.substring(separator + 1)).toLowerCase(Locale.ROOT);
        return localPart + "@" + domain;
    }

    public static String validateLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String value = locale.strip();
        try {
            new Locale.Builder().setLanguageTag(value).build();
            return value;
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Locale must be a valid BCP 47 language tag");
        }
    }

    public static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        String value = timezone.strip();
        try {
            ZoneId.of(value);
            return value;
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Timezone must be a valid IANA zone ID");
        }
    }

    public static String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String value = phoneNumber.strip();
        if (!value.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("Phone number must use E.164 format");
        }
        return value;
    }
}
