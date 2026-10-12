# Open Source Project Readiness Design

**Date:** 2026-09-30
**Status:** Approved for implementation planning

## Goal

Make the PDFViewFX repository self-describing and easier to use, contribute to,
and maintain as an open-source project. Complete the community-facing
information, correct package metadata, and update the existing GitHub workflows
without changing library behavior or release identity.

## Scope

### Community and contributor information

- Add `CONTRIBUTING.md` with the JDK 21/Maven Wrapper setup, build and test
  commands, and a concise pull-request process.
- Add `SECURITY.md` with private vulnerability-reporting instructions using
  GitHub Security Advisories and the maintainer contact already published in
  the project metadata. Do not promise an unsupported response time.
- Add a Contributor Covenant code of conduct and make its reporting channel
  explicit without inventing a new contact.
- Add GitHub bug-report and feature-request issue forms, plus a pull-request
  checklist. Requests should include enough environment and reproduction
  information to be actionable.

### Project and package information

- Correct the GemsFX-specific identifier and repository properties in the root
  `pom.xml`.
- Make the Apache-2.0 license name and URL, SCM coordinates, and issue-tracker
  metadata accurate and consistent in the parent, library, and demo POMs.
- Preserve artifact coordinates and publisher identity.
- Update `README.md` with library Maven coordinates, the JDK baseline, license
  information, and links to the new contributor and security guidance.

### GitHub workflows

- Update `build.yml`, `codeql.yml`, and `release.yml` as a coherent set.
- Keep the existing build, analysis, and release triggers and publishing
  behavior.
- Have CI use the repository Maven Wrapper and JDK baseline.
- Replace obsolete GitHub Actions versions with supported major versions.
- Pass the manually supplied release version to shell steps through an
  environment variable and validate it before modifying project files. Accept
  stable `MAJOR.MINOR.PATCH` versions with an optional prerelease suffix; reject
  whitespace and shell metacharacters.
- Preserve required permissions, credentials, and publishing operations; make
  other workflow changes only where needed for clear reliability or security.

## Out of scope

- No library behavior or API changes, dependency additions, or unrelated source
  refactoring.
- No full audit of third-party dependencies or demo asset provenance. Do not
  add unverified license or attribution claims.
- No changes to release versioning, artifact coordinates, or publisher identity.

## Validation

- Run `./mvnw -B verify` for the complete Maven reactor.
- Inspect effective Maven metadata for each module to confirm corrected project,
  license, SCM, and issue-tracker values.
- Validate workflow syntax and lint the GitHub Actions files using an available
  workflow linter; if none is available, perform a syntax check without adding
  tooling to the project.
- Check that README references resolve and that added GitHub templates are
  recognized by their expected paths.

## Delivery

Leave all design and implementation changes in the working tree; do not create
commits automatically.
