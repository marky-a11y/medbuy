# ADR-0001: Java 8 + Spring Boot 2.7 Mandate

## Status
Accepted (2026-06-29)

## Date
2026-06-29

## Context
The project is governed by `java_web_app.md`, which mandates Java 8 as the runtime and compilation target for all backend code. Spring Boot 2.7 is the latest major version line that supports Java 8 without requiring additional compatibility flags or preview features. This decision affects all code written for the Media Buying Dashboard, including entity classes, services, repositories, controllers, and test suites.

Key constraints imposed by this decision:
- No Java 9+ language features: `var` (local-variable type inference), modules, private interface methods.
- No Java 14+ records — verbose POJOs for all DTOs.
- No Java 15+ sealed classes or text blocks.
- No Java 16+ `instanceof` pattern matching.
- No Java 17+ sealed interfaces, `Stream.toList()`, or `switch` expressions.

## Decision
Adopt Java 8 (JDK 8u312+) with Spring Boot 2.7.x as the baseline for all development.

### Mitigations for Missing Language Features

| Missing Feature | Mitigation Strategy |
|----------------|---------------------|
| Records (Java 14+) | Use Lombok `@Data` / `@Builder` / `@Value` for immutable DTOs |
| `var` (Java 10+) | Explicit type declarations; IDE auto-completion compensates |
| Text blocks (Java 13+) | String concatenation with `\n` or `StringBuilder` |
| Pattern matching (Java 16+) | Traditional `instanceof` + cast |
| `switch` expressions (Java 14+) | Traditional `switch` statements with `break` |
| `Stream.toList()` (Java 16+) | `collect(Collectors.toList())` |
| Sealed classes (Java 17+) | Not applicable — no sealed hierarchy needed at MVP scale |

### Upgrade Path
A follow-up ADR will be created when the team decides to migrate to Java 17. The migration plan includes:
1. Update `pom.xml` — change `java.version` to 17, update `maven-compiler-plugin`.
2. Upgrade Spring Boot to 3.x (requires Jakarta EE migration).
3. Convert verbose DTOs to `record` types.
4. Replace Lombok `@Builder` on DTOs with compact constructors where appropriate.
5. Migrate `WebSecurityConfigurerAdapter` to `SecurityFilterChain` bean (Spring Boot 3.x).

## Alternatives Considered

### Alternative 1: Upgrade to Java 17 Immediately
- **Pros**: Access to records, sealed classes, `var`, pattern matching, improved GC performance (ZGC).
- **Cons**: Violates `java_web_app.md` mandate. Requires migration from Spring Boot 2.7 → 3.x (breaking changes: Jakarta EE `javax.*` → `jakarta.*`). Would delay MVP by 2–3 weeks for migration and regression testing.

### Alternative 2: Use Java 8 with Lombok and Accept All Limitations
- **Pros**: No migration risk. Team is familiar with the toolchain. All existing documentation and CI infrastructure targets this combination.
- **Cons**: Verbose DTOs. No records for immutable data carriers. No text blocks for multi-line strings.

### Alternative 3: Keep Java 8 for MVP, Plan Java 17 Upgrade Post-MVP
- **Pros**: Unblocks MVP delivery. Provides a clear migration milestone. Team gains experience with the codebase before undertaking migration.
- **Cons**: Technical debt accumulates (Lombok annotations to remove, `javax.*` to replace). Migration cost increases with codebase size.

We chose **Alternative 3** — pragmatic and aligned with `java_web_app.md`.

## Consequences

### Positive
- Immediate compatibility with the mandated toolchain.
- No migration risk for MVP delivery.
- All CI/CD pipeline scripts and Dockerfiles work with the existing JDK base image.
- Team can use Lombok to reduce boilerplate, partially compensating for the lack of records.

### Negative
- DTOs are more verbose than they would be with Java 14+ records (approx. 4× lines of code per DTO).
- No `var` — variable declarations are more verbose, especially in lambda-heavy code.
- No text blocks — multi-line SQL queries in annotations and tests are harder to read.
- The eventual Java 17 migration will be a significant effort (estimated 3–5 person-days).

### Neutral
- Spring Boot 2.7 is well-tested and stable; many production systems run on this version.
- The Spring Boot 3.x migration is well-documented, and many migration guides exist.

## Related Documents
- `java_web_app.md` — Java web application standards and constraints
- [HLD.md](/design/HLD.md) §12 — Technology stack decisions
- [LLD.md](/design/LLD.md) §1 — Technology stack
- `pom.xml` — Build configuration with `java.version` property
