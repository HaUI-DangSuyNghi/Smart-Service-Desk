package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
public record Sla(Instant startBy, Instant resolveBy, boolean startBreached, boolean resolveBreached,
                  boolean warning, boolean clockPaused) {}
