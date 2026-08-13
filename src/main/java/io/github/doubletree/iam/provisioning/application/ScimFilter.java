package io.github.doubletree.iam.provisioning.application;

import io.github.doubletree.iam.provisioning.api.ScimProtocolException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ScimFilter(String attribute, String stringValue, Boolean booleanValue) {

    private static final Pattern COMPARISON = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9.$_-]*(?:\\.[A-Za-z$][A-Za-z0-9.$_-]*)?)\\s+([A-Za-z]+)\\s+(.+?)\\s*$");
    private static final Pattern STRING_VALUE = Pattern.compile("^\"([^\"\\r\\n]*)\"$");
    private static final Set<String> USER_ATTRIBUTES =
            Set.of("username", "displayname", "emails.value", "active");
    private static final Set<String> GROUP_ATTRIBUTES = Set.of("displayname", "members.value");

    static ScimFilter parse(String expression, Resource resource) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Matcher matcher = COMPARISON.matcher(expression);
        if (!matcher.matches()) {
            throw ScimProtocolException.invalidFilter("The filter syntax is not supported");
        }
        String attribute = matcher.group(1).toLowerCase(Locale.ROOT);
        String operator = matcher.group(2).toLowerCase(Locale.ROOT);
        if (!"eq".equals(operator)) {
            throw ScimProtocolException.invalidFilter("Only the eq filter operator is supported");
        }
        Set<String> supported = resource == Resource.USER ? USER_ATTRIBUTES : GROUP_ATTRIBUTES;
        if (!supported.contains(attribute)) {
            throw ScimProtocolException.invalidFilter("The filter attribute is not supported for this resource");
        }
        String rawValue = matcher.group(3);
        if ("active".equals(attribute)) {
            if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                throw ScimProtocolException.invalidFilter("The active filter value must be true or false");
            }
            return new ScimFilter(attribute, null, Boolean.valueOf(rawValue));
        }
        Matcher stringMatcher = STRING_VALUE.matcher(rawValue);
        if (!stringMatcher.matches()) {
            throw ScimProtocolException.invalidFilter("String filter values must be quoted");
        }
        return new ScimFilter(attribute, stringMatcher.group(1), null);
    }

    enum Resource {
        USER,
        GROUP
    }
}
