# Open Source Project Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PDFViewFX self-describing and easier to use, contribute to, and maintain as an open-source project.

**Architecture:** Complete community documentation and GitHub templates, correct the existing Maven project metadata and README, then modernize the existing GitHub workflows without changing project behavior or release identity. Keep these deliverables isolated so each can be reviewed and validated independently.

**Tech Stack:** Markdown, GitHub issue forms and Actions YAML, Maven POM, Maven Wrapper, Java 21.

**Spec:** `docs/superpowers/specs/2026-09-30-open-source-readiness-design.md`

## Global Constraints

- Preserve artifact coordinates and publisher identity.
- No library behavior or API changes, dependency additions, or unrelated source refactoring.
- No full audit of third-party dependencies or demo asset provenance. Do not add unverified license or attribution claims.
- No changes to release versioning, artifact coordinates, or publisher identity.
- Keep existing build, analysis, and release triggers and publishing behavior.
- Use the repository Maven Wrapper and JDK baseline for CI.
- Pass the manually supplied release version to shell steps through an environment variable and validate it before modifying project files.
- Preserve required permissions, credentials, and publishing operations.
- Leave all changes uncommitted.

---

### Task 1: Add community governance and contributor guidance

**Files:**
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `CODE_OF_CONDUCT.md`

**Interfaces:**
- Consumers: project contributors, maintainers, and vulnerability reporters.
- Produces: contributor instructions using `./mvnw`; private security reporting guidance; a Contributor Covenant conduct policy.
- Reporting contact: use the maintainer email already listed in Maven metadata, and GitHub Security Advisories for private vulnerability reports. Do not promise a response time.

- [ ] **Step 1: Add contributor instructions**

Create `CONTRIBUTING.md` with:
  - Prerequisites: JDK 21 and Git.
  - Build/test command: `./mvnw -B verify`.
  - Demo command: `./mvnw javafx:run -f pdfviewfx-demo/pom.xml`.
  - Guidance to file issues with the corresponding issue form, submit focused pull requests, describe user-visible changes, and update `CHANGELOG.md` when appropriate.
  - A statement that contributors must follow `CODE_OF_CONDUCT.md`.

- [ ] **Step 2: Add private vulnerability reporting instructions**

Create `SECURITY.md` instructing reporters not to file public issues for exploitable vulnerabilities; use the repository’s “Report a vulnerability” GitHub Security Advisory feature when enabled, or email the maintainer address present in `pom.xml`. Ask for affected versions, impact, and reproduction details where safe. Do not state unsupported response or remediation deadlines.

- [ ] **Step 3: Add the Contributor Covenant**

Create `CODE_OF_CONDUCT.md` using the complete Contributor Covenant Code of Conduct version 2.1 text. Include the official source link and required Creative Commons Attribution 4.0 attribution. Set the reporting channel to the maintainer email already published in the root `pom.xml`. Do not invent additional enforcement roles or contacts.

- [ ] **Step 4: Validate the governance documents**

Review the files for placeholder text, agreement with the JDK/Maven commands in the repository, a valid maintainer contact, and absence of unsupported response-time commitments. Confirm the code-of-conduct source attribution and license notice are present.

**Expected:** Contributors can find setup and reporting guidance; governance documents contain accurate contact and policy information with no unsupported promises.

---

### Task 2: Add GitHub issue and pull-request templates

**Files:**
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `.github/ISSUE_TEMPLATE/config.yml`
- Create: `.github/PULL_REQUEST_TEMPLATE.md`

**Interfaces:**
 - Consumers: GitHub issue and pull-request submitters.
 - Produces: valid bug/feature issue forms, issue-form configuration, and a PR checklist.

- [ ] **Step 1: Add a bug-report form**

Create `.github/ISSUE_TEMPLATE/bug_report.yml` as a GitHub issue form with a `name`, `description`, `title`, and `labels`. Include required `textarea` fields for summary and reproducible steps, required `input` fields for PDFViewFX version and operating system, a required textarea for JDK and JavaFX versions, required expected and actual behavior textareas, and an optional textarea for logs or screenshots. Give each field a user-facing label and a concise description. Use stable unique YAML `id` values.

- [ ] **Step 2: Add a feature-request form**

Create `.github/ISSUE_TEMPLATE/feature_request.yml` as a GitHub issue form with a `name`, `description`, `title`, and `labels`. Add required textareas for the problem/use case and proposed behavior, plus optional textareas for alternatives and additional context. Give each field a user-facing label, concise description, and unique YAML `id`.

- [ ] **Step 3: Configure the issue chooser**

Create `.github/ISSUE_TEMPLATE/config.yml` with `blank_issues_enabled: false` and `contact_links` for the README and the repository’s `SECURITY.md` page. Do not add an unsupported external support destination.

- [ ] **Step 4: Add the pull-request checklist**

Create `.github/PULL_REQUEST_TEMPLATE.md` with concise checkboxes for describing the change, linking any issue, running `./mvnw -B verify`, and updating docs/changelog for user-visible changes. Do not claim tests passed by default.

- [ ] **Step 5: Validate issue forms and templates**

Check YAML syntax for both issue forms and config, verify required GitHub field types and referenced paths, and check the PR template’s documented command against `CONTRIBUTING.md`.

**Expected:** GitHub recognizes the forms/templates; each asks for actionable information without introducing unsupported external links.

---

### Task 3: Correct Maven metadata

**Files:**
- Modify: `pom.xml`
- Modify: `pdfviewfx/pom.xml`
- Modify: `pdfviewfx-demo/pom.xml`

**Interfaces:**
- Consumes: existing Maven coordinates, maintainer identity, and repository URL.
- Produces: consistent Maven license, SCM, issue tracker, and project identity metadata.

- [ ] **Step 1: Correct the root Maven project properties**

In `pom.xml`:
  - Change `project.identifier` from the stale GemsFX value to `pdfviewfx`.
  - Change `project.github.repository` to `dlsc-software-consulting-gmbh/PDFViewFX`.
  - Keep group ID, artifact ID, version, developer identity, and publisher unchanged.

- [ ] **Step 2: Complete and normalize Maven license/SCM/issue metadata**

In the parent, library, and demo POMs:
  - For the existing Apache license element, set `<name>Apache License, Version 2.0</name>` and `<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>`.
  - Set a consistent `<scm>` connection, developer connection, and URL referencing `https://github.com/dlsc-software-consulting-gmbh/PDFViewFX.git` and the repository URL.
  - Add `<issueManagement>` with GitHub Issues at `https://github.com/dlsc-software-consulting-gmbh/PDFViewFX/issues`.
  - Correct the demo POM’s inconsistent `PdfViewFX` capitalization.
  - Do not alter artifact coordinates or versions.

- [ ] **Step 3: Validate effective Maven metadata**

Run:

```bash
./mvnw -B -pl :parent,:pdfviewfx,:pdfviewfx-demo help:effective-pom
```

Inspect effective POM output for all three modules and verify SCM, license, and issue-management entries, while confirming artifact coordinates are unchanged.

**Expected:** All modules report PDFViewFX repository metadata and Apache-2.0 license metadata with unchanged artifact coordinates.

---

### Task 4: Document project installation and governance

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: current README feature/usage material and Maven library coordinates.
- Produces: README installation guidance and direct links to project governance documents.

- [ ] **Step 1: Add project information and Maven installation instructions**

Retain current feature, printing, localization, and demo instructions. Add a concise project information section with:
  - The library coordinate `com.dlsc.pdfviewfx:pdfviewfx:3.5.0`, marked as the current project version.
  - This Maven dependency snippet:

```xml
<dependency>
    <groupId>com.dlsc.pdfviewfx</groupId>
    <artifactId>pdfviewfx</artifactId>
    <version>3.5.0</version>
</dependency>
```

  - JDK 21 requirement.
  - Apache License 2.0 link.
  - Links to `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and `CHANGELOG.md`.

- [ ] **Step 2: Validate README references and coordinates**

Check every new README relative link resolves to a tracked file and confirm the dependency coordinates match `pdfviewfx/pom.xml`.

**Expected:** README includes usable Maven setup and links to all governance files without removing existing usage guidance.

---

### Task 5: Modernize and harden project workflows

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `.github/workflows/codeql.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: Maven Wrapper, Java 21 toolchain, GitHub Actions secrets, manual `version` input, Maven Central and JReleaser configuration.
- Produces: CI build/test workflow, CodeQL analysis workflow, and manual release workflow, preserving their purpose and release outputs.

- [ ] **Step 1: Update the build workflow**

In `build.yml`:
  - Keep push-to-`master` and pull-request triggers.
  - Use `./mvnw -B verify` instead of system `mvn`.
  - Keep Java 21 setup and existing Maven caching.
  - Replace obsolete Sonar cache action usage with a currently supported cache action major version, preserving its cache path/key.
  - Keep Sonar analysis and its token behavior unchanged.

- [ ] **Step 2: Update CodeQL action versions and build**

In `codeql.yml`:
  - Keep push, PR, and scheduled triggers.
  - Upgrade checkout, setup-java, cache, and CodeQL actions to current supported major versions consistent with the build/release workflows (`checkout@v5`, `setup-java@v5`, `cache@v5`, `codeql-action@v4`).
  - Keep Java analysis, security-and-quality queries, and the existing permissions.
  - Replace autobuild with `./mvnw -B -DskipTests package` for deterministic Maven project discovery; retain analysis upload.

- [ ] **Step 3: Validate the manual release version before shell use**

In `release.yml`, add an environment mapping from `inputs.version` to a shell environment variable. In the version-update step, validate that it matches a stable `MAJOR.MINOR.PATCH` version with an optional prerelease suffix, for example:

```bash
if [[ ! "$VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$ ]]; then
  echo "Invalid release version: expected MAJOR.MINOR.PATCH with an optional prerelease suffix" >&2
  exit 1
fi
```

Use the validated variable for Maven `-DnewVersion` and commit message construction. Do not interpolate `${{ github.event.inputs.version }}` directly into shell source.

- [ ] **Step 4: Update release actions while preserving publishing**

In `release.yml`:
  - Retain the manual dispatch input, checkout history depth, Java 21 setup, Maven Central deployment, JReleaser invocation, and JReleaser log artifact.
  - Keep compatible supported action versions (`checkout@v5`, `setup-java@v5`, `cache@v5`; retain `upload-artifact@v4` unless repository runner/action compatibility requires an upgrade).
  - Preserve all existing required secrets and server IDs.
  - Keep the existing release branch target and commit/push operation because it is part of the current release behavior.
  - Avoid echoing credentials or tokens.

- [ ] **Step 5: Validate workflow syntax and action references**

Run an available GitHub Actions linter such as `actionlint` if installed. If unavailable, parse the three YAML files with an installed YAML parser and inspect workflow expressions, action references, triggers, permissions, and environment-variable quoting. Do not add project dependencies solely for validation.

**Expected:** All three workflows parse; build uses the wrapper/JDK 21; CodeQL uses supported action majors; a malformed release version exits before POM changes; release publishing and artifact upload steps remain present.

---

### Task 6: Run integrated readiness verification

**Files:**
- Verify: all files created or modified in Tasks 1–5.

**Interfaces:**
- Consumes: completed community, Maven/README, and workflow deliverables.
- Produces: a verified working tree with no automatically created commit.

- [ ] **Step 1: Run the full Maven reactor**

Run:

```bash
./mvnw -B verify
```

Expected: reactor build completes successfully. Record any environment-specific failure distinctly rather than treating it as success.

- [ ] **Step 2: Check all documentation and template references**

Verify the README links, contributor/security links, issue forms, conduct contact, workflow filenames, and documented commands point to valid repository paths and match configured project values.

- [ ] **Step 3: Review the final diff and worktree**

Run:

```bash
git diff --check
git status --short
```

Inspect the complete diff for accidental changes to source behavior, artifact coordinates, release identity, secrets, or untracked generated build output. Do not commit.

**Expected:** Documentation, metadata, workflows, and GitHub templates are the only in-scope changes; all work remains uncommitted.
