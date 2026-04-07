package io.github.raphonzius.lvc.live.domain;

import java.time.ZoneId;

/** Shared time-zone constants used across domain and infrastructure layers. */
public final class TimeZones {

    private TimeZones() {}

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
}
