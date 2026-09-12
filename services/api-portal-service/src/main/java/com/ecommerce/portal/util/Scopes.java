package com.ecommerce.portal.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scopes are represented two ways in this service, and this class is the
 * single place that converts between them:
 * <ul>
 *   <li>at the API boundary (DTOs) and in the {@code scope} JWT claim, a
 *       space-delimited string - the OAuth2 client-credentials convention
 *       (RFC 6749 §3.3), which is what {@code POST /oauth/token} issues;</li>
 *   <li>in memory / for set operations (e.g. intersecting requested vs.
 *       granted scopes), a {@link Set} of strings, order-preserving.</li>
 * </ul>
 * Persistence (producer_apis.required_scopes, consumer_grants.scopes)
 * stores the same space-delimited string form directly - no join table for
 * v1's small per-row scope lists.
 */
public final class Scopes {

    private Scopes() {
    }

    public static String join(List<String> scopes) {
        return scopes.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    public static Set<String> asSet(String spaceDelimited) {
        if (spaceDelimited == null || spaceDelimited.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(spaceDelimited.trim().split("\\s+")));
    }

    public static List<String> asList(String spaceDelimited) {
        return List.copyOf(asSet(spaceDelimited));
    }
}
