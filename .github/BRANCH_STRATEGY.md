# Branch Strategy

## Branch Structure

### Primary Branches

- **`main`**: Production release branch
  - Always maintain stable state
  - Only allow npm publishing via tags (`v*.*.*`)
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

#### Method 1: GitHub Actions Manual Deployment

1. Go to GitHub repository → Actions tab
2. Select "Publish to npm" workflow
3. Click "Run workflow"
4. Select version type (patch/minor/major)
5. Automatically updates version, creates tag, and publishes to npm

#### Method 2: Deploy via Tag

```bash
# Update version
npm version patch|minor|major

# Push tag
git push origin main
git push origin --tags

# GitHub Actions automatically deploys
```

#### Method 3: Manual Deployment

```bash
npm run build
npm publish --access public
```

## Branch Protection Rules Recommendations

Recommended settings in GitHub repository:

### main Branch Protection

- ✅ Require pull request reviews before merging
- ✅ Require status checks to pass before merging
- ✅ Require branches to be up to date before merging
- ✅ Do not allow bypassing the above settings

### Status Checks

- Build (TypeScript compilation)
- Typecheck (Type checking)

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
