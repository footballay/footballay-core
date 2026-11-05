---
apply: always
---

# AGENTS.md

## Scope

This repository is `footballay-core`, a Spring Boot Kotlin backend.

Use Korean when explaining work to the user. Korean sentences must end with `.`, `?`, or `!`, not a closing colon.

These rules bias toward caution over speed. For trivial tasks, use judgment.

## Core behavior rules

### 1. Think before coding

* Do not assume. Do not hide confusion. Surface tradeoffs.
* Before non-trivial implementation, state assumptions, a short plan, and the verification target.
* If multiple interpretations exist, present them instead of silently choosing one.
* If a simpler approach exists, say so and prefer it.
* If something is unclear enough to affect correctness, stop and ask.

### 2. Simplicity first

* Write the minimum code that solves the requested problem.
* Do not add features beyond what was asked.
* Do not add abstractions for single-use code.
* Do not add flexibility, configurability, dependencies, or broad error handling unless requested.
* If the change becomes much larger than necessary, simplify before continuing.
* Prefer boring, readable code over clever or highly abstract code.

### 3. Surgical changes

* Touch only files directly related to the task.
* Every changed line should trace directly to the user request or to fixing a consequence of that change.
* Match existing style, even if you would write it differently.
* Do not refactor adjacent code, reformat unrelated files, improve unrelated comments, or delete pre-existing dead code.
* If you notice unrelated problems, mention them instead of fixing them.
* Remove only imports, variables, functions, or files made unused by your own change.

### 4. Goal-driven execution

* Convert work into verifiable goals.
* For bug fixes, reproduce the bug with a failing test or a targeted verification before changing production code.
* For validation changes, test invalid inputs and make those tests pass.
* For refactors, verify behavior before and after when practical.
* Do not mark work complete until the requested behavior is implemented and relevant verification has been run.

## Safety and permission rules

* Do not commit, push, delete files, change deployment/configuration, or install new production dependencies without explicit user approval.
* If a logical change is ready, suggest a semantic commit message, but do not run `git commit` unless approved.
* Do not weaken assertions, skip tests, or broaden matchers just to make tests pass.

## Commit message conventions

* Use `<type>: <short imperative summary>`. Scope is optional.
* Use `feat` for new behavior, `fix` for incorrect behavior, `perf` for performance improvements, and `refactor` for internal restructuring without a behavior or performance goal.
* Use `test` for test-only changes, `docs` for documentation-only changes, `build` for build or dependency changes, and `ci` for CI changes.
* Use `chore` for maintenance that fits none of the above. When tests accompany a code change, use the code change's type.

## Architecture rules

* Football entities follow the Core/Backbone split.
* Core represents real football domain objects and is the public-facing identity boundary.
* Backbone represents provider-specific data structures.
* User-facing requests must use Core UID only.
* Admin flows may use provider-specific IDs when collecting or reconciling external data.
* Prefer this layer flow.
  `Controller -> Web Service -> Domain Facade -> Domain Service -> Repository`.
* Controller handles web/API concerns and validation.
* Web Service handles web-adjacent application concerns such as response assembly and caching.
* Domain Facade is the main entry point for web, scheduler, and batch use cases.
* Domain Service contains reusable domain logic.
* Repository only provides persistence access.
* Simple admin/dev queries may temporarily bypass the facade, but any logic-bearing path must go through a Domain Facade.
* Duplication is acceptable when admin, scheduler, batch, statistics, or performance needs are conceptually different.
* JPA Entity must not cross the Domain Facade boundary.
* Facades must return domain models or use-case DTOs, never DTOs containing JPA entities.

## Testing rules

* Code should be testable. Extract pure or smaller logic when a service method becomes hard to test.
* One test method should verify one main concern unless the layer is inherently integration-heavy.
* Test method names should be descriptive English. Use `@DisplayName` for Korean explanation.
* If a unit test needs excessive mocking, consider an integration test or improve the implementation design.
* In Spring Boot 3.4+, use `@MockitoBean`, not deprecated `@MockBean`.
* If code changed, run the smallest relevant test/build check before saying the work is done.
* If checks fail, read the actual error, stack trace, SQL state, constraint name, and failing test before guessing a fix.

## Command and token discipline

### Code dump

* When the user requests a "code dump" or "코드 덤프", create or overwrite the requested dump file by command-line streaming, not by reading each source file into the model and reproducing it through a patch.
* Resolve the target files with Git first. Unless the user specifies another scope, include every changed `src/` file relative to `HEAD`, including untracked source files, and exclude ignored planning files.
* Write each target file's complete current filesystem contents to one Markdown file, with a file-path heading and fenced code block. Do not summarize, omit code, or reinterpret source contents.
* Prefer a single shell pipeline or short shell script that writes directly to the dump file. Verify the number of dumped file headings matches the resolved target file count.

* Prefer targeted commands before full-suite commands.
* For Gradle tests, prefer.
  `./gradlew test --tests "fully.qualified.TestName" --console=plain`.
* For long command output, save the raw log and inspect only relevant slices.
  `./gradlew test --console=plain 2>&1 | tee /tmp/footballay-test.log`.
* Do not paste or load huge logs into context.
* Inspect logs with `rg`, `grep`, `tail`, or `sed`.
* Do not rely only on LLM-summarized output for test failures, DB errors, transaction issues, duplicate key errors, or flaky suite-only failures.
* For DB/test failures, preserve and inspect raw evidence such as failing test name, table, constraint, SQLState, transaction boundary, fixture path, and test order.
* Run the full suite only when needed or explicitly requested.  
* Git should not track code dump file.  
* If a `plan/` directory exists at the project root and is ignored by Git, write code dumps there.  

## Context notes for large tasks

* For multi-step work that spans many files or may continue across sessions, create or update.

    * `checklist.md` for concrete tasks.
    * `context-notes.md` for decisions and reasoning.
* Do not create these files for small, local, one-shot changes.
* Keep notes short and factual.

## New file comments

* For new source files, add comment using KDoc or Javadoc in Korean at least one line or more.
* Skip config files, generated files, simple DTOs, and files where such a comment would violate project style.

## Done criteria

A task is complete only when.

* The requested behavior is implemented or the question is answered.
* Relevant tests, build, or compile checks were run when code changed.
* If checks could not run, the exact reason and command to run manually are reported.
* The diff was reviewed for unrelated changes.
* The final report lists changed files, verification result, and remaining risks.

## ETC
- Do not run `./gradlew --stop`, `clean`, or use temporary `GRADLE_USER_HOME` / Kotlin daemon overrides unless explicitly required.
- Gradle/Kotlin first runs may take several minutes with little output; do not treat them as hung or terminate daemons prematurely.