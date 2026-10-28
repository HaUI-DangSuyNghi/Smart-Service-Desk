package vn.haui.dx.core.workflow.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("r11-clock-probe")
public class TimerRuntimeProbe {
    @Bean @Primary
    Clock timerProbeClock() {
        return new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) {
                if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("UTC probe only");
                return this;
            }
            public Instant instant() {
                try {
                    long seconds = Long.parseLong(Files.readString(Path.of("/probe/offset")).trim());
                    if (seconds < 0 || seconds > 604800) throw new IllegalArgumentException("Invalid probe offset");
                    return Instant.now().plusSeconds(seconds);
                } catch (java.io.IOException failure) { throw new IllegalStateException("Probe clock unavailable", failure); }
            }
        };
    }
}
