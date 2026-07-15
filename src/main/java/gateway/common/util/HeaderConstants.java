package gateway.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HeaderConstants {
    public static final String X_CORRELATION_ID = "X-Correlation-ID";
    public static final String X_API_KEY = "X-API-Key";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
}
