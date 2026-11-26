package com.fieldward.jobs;

import java.net.URI;
import java.util.Locale;

final class JobSourcePolicy {
    private JobSourcePolicy() {}
    static boolean permits(JobSource source, String address) {
        if (!source.authorized() || source.allowedHosts() == null || address == null) return false;
        try {
            URI uri = URI.create(address);
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && source.allowedHosts().contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
