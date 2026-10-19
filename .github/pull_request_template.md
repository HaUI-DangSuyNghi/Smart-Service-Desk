## Change

Describe the problem, resulting behavior and owning tier.

## Validation

List checks run and any checks requiring external services.

## Architecture

- [ ] No new Tier 3 to Core dependency
- [ ] No Tier 2 dependency on application semantics or private Core implementation
- [ ] No Core dependency on upper tiers
- [ ] No private database ownership crossed or cross-owner FK added
- [ ] No lower-tier implementation DTO exposed through a new public API
- [ ] Architecture tests updated where needed; resolved debt removed from the exact inventory
