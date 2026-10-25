"""App-owned experiment bindings. No study changes the authoritative triage policy."""

POLICIES = {
    "tev1": ("tev1-policy-v0.1.json", "tev1-systemone@0.1.0"),
    "tev1-t1": ("tev1-clarified-unknown-v0.2.json", "tev1-systemone@0.1.0"),
    "tev1-t2-rotate": ("tev1-order-rotate-v0.2.json", "tev1-systemone@0.1.0"),
    "tev1-t2-reverse": ("tev1-order-reverse-v0.2.json", "tev1-systemone@0.1.0"),
    "tev1-t2-interleave": ("tev1-order-interleave-v0.2.json", "tev1-systemone@0.1.0"),
    "gemma3": ("gemma-policy-v0.1.json", "gemma-chat-schema@0.1.0"),
    "gemma3-g1": ("gemma-classify-only-v0.2.json", "gemma-classify-only@0.2.0"),
}
