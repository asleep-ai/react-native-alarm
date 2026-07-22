# Branch Strategy

## Branch Structure

### Primary Branches

- **`main`**: Production release branch
  - Always maintain stable state
  - Publishing is driven by the `version` field in `package.json` (see Publishing Process)
  - No direct commits (merge via PR only)

### Supporting Branches

- **`develop`** (optional): Development integration branch

  - Merge feature branches here first
  - Merge to `main` after sufficient testing
  - Can be omitted for small projects

- **`feature/*`**: Feature development branches

  - Examples: `feature/add-android-support`, `feature/improve-error-handling`
  - Branch from `main` or `develop`
  - Create PR after development is complete

- **`fix/*`**: Bug fix branches

  - Examples: `fix/memory-leak`, `fix/ios-permission-issue`
  - Branch from `main`
  - Create PR after fix is complete

- **`hotfix/*`**: Emergency fix branches

  - For urgent production bug fixes
  - Branch from `main`
  - Merge to `main` immediately and deploy

- **`release/*`** (optional): Release preparation branches
  - Examples: `release/v0.2.0`
  - Update version numbers, write CHANGELOG, etc.
  - Merge to `main` and create tag when ready

## Workflow

### Standard Development Flow

```
1. Create feature/xxx branch
2. Develop and test
3. Create PR (main ← feature/xxx)
4. Code review and approval
5. Merge to main
6. Create tag and deploy if needed
```

### Emergency Fix Flow

```
1. Create hotfix/xxx branch (from main)
2. Fix and test
3. Create PR (main ← hotfix/xxx)
4. Merge immediately
5. Create tag and deploy
```

### Publishing Process

Merging to `main` never publishes. Releases are explicit:

1. Bump the version in your PR (e.g. `npm version patch --no-git-tag-version`) and merge to `main`
2. Trigger the release either way:
   - Push the matching tag: `git tag vX.Y.Z && git push origin vX.Y.Z`
   - Or run the "Publish to npm" workflow from the Actions tab (workflow_dispatch)
3. The workflow verifies the tag matches `package.json` and the version is not already on npm, publishes via OIDC trusted publishing (no tokens), and creates the GitHub Release (and tag, when run manually)

Manual fallback: `npm publish --access public` locally.

## Branch Protection

Enforced by the `main-protection` repository ruleset:

- Pull request required before merging
- Required status checks: `test` (typecheck + build) and `lint` (oxlint + format check)
- Force pushes and branch deletion blocked
- Repository admins may bypass

## Version Management

- Use **Semantic Versioning** (MAJOR.MINOR.PATCH)
- **MAJOR**: Breaking API changes
- **MINOR**: Backward-compatible feature additions
- **PATCH**: Backward-compatible bug fixes

## Simple Strategy (Small Projects)

For small libraries, a simpler strategy is also possible:

- **`main`**: All development and deployment
- **`feature/*`**: Feature development (optional)

In this case, develop directly on `main` or use `feature/*` branches only for large features.
