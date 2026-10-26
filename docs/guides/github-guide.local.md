# Git, GitHub, and VS Code: complete working manual

This is a project-agnostic Git/GitHub reference with a final section for the current ShiftReport repository. It is intentionally ignored by Git because it contains local operational context. Commands use placeholders such as `<OWNER>`, `<REPO>`, `<BRANCH>`, and `<SHA>` unless a section is explicitly marked **ShiftReport-specific**.

> Safety principle: inspect first, mutate second. Before a destructive or history-rewriting command, make a verified backup, resolve the exact repository and branch, and understand who else consumes the history.

## Contents

1. [Mental model and command safety](#1-mental-model-and-command-safety)
2. [Installation and initial configuration](#2-installation-and-initial-configuration)
3. [Create a repository locally and on GitHub](#3-create-a-repository-locally-and-on-github)
4. [Clone, fork, and configure remotes](#4-clone-fork-and-configure-remotes)
5. [Authentication: SSH, HTTPS, and GitHub CLI](#5-authentication-ssh-https-and-github-cli)
6. [Daily workflow and staging](#6-daily-workflow-and-staging)
7. [`.gitignore`, untracking, and data hygiene](#7-gitignore-untracking-and-data-hygiene)
8. [Branching, merging, rebasing, and conflicts](#8-branching-merging-rebasing-and-conflicts)
9. [Pull requests and GitHub collaboration](#9-pull-requests-and-github-collaboration)
10. [Tags, releases, and semantic versioning](#10-tags-releases-and-semantic-versioning)
11. [Undo, recovery, and debugging history](#11-undo-recovery-and-debugging-history)
12. [Advanced operations](#12-advanced-operations)
13. [History cleanup, secret removal, and full-history squashing](#13-history-cleanup-secret-removal-and-full-history-squashing)
14. [VS Code workflows and multi-root workspaces](#14-vs-code-workflows-and-multi-root-workspaces)
15. [Monorepo versus polyrepo](#15-monorepo-versus-polyrepo)
16. [Configuration extras, attributes, hooks, and signing](#16-configuration-extras-attributes-hooks-and-signing)
17. [GitHub Actions and repository controls](#17-github-actions-and-repository-controls)
18. [Stack-specific repository practices](#18-stack-specific-repository-practices)
19. [Maintenance and integrity](#19-maintenance-and-integrity)
20. [Common errors and exact recovery paths](#20-common-errors-and-exact-recovery-paths)
21. [Command reference](#21-command-reference)
22. [Reusable templates](#22-reusable-templates)
23. [ShiftReport-specific workflow and current state](#23-shiftreport-specific-workflow-and-current-state)

---

## 1. Mental model and command safety

Git normally involves four states:

```text
working tree -> staging/index -> local commits -> remote repository
```

- The **working tree** is what is currently on disk.
- The **index/staging area** is the exact snapshot prepared for the next commit.
- A **commit** is a local immutable snapshot with parent links.
- A **branch** is a movable name pointing to a commit.
- `origin` is a conventional remote name, not a special GitHub keyword.
- `origin/main` is a local remote-tracking ref last updated by fetch/pull.
- GitHub is hosting and collaboration around Git; Git itself works locally without GitHub.

### Read-only inspection commands

These are safe starting points:

```bash
pwd
git rev-parse --show-toplevel
git status --short --branch
git branch --show-current
git remote -v
git log --oneline --decorate --graph --all -12
git diff
git diff --cached
git diff --check
```

### Risk classes

| Class | Examples | Rule |
|---|---|---|
| Read-only | `status`, `diff`, `log`, `show`, `fetch` | Normally safe |
| Recoverable mutation | `add`, `commit`, `switch`, `merge`, `rebase` | Inspect state first |
| Destructive local | `restore file`, `reset --hard`, `clean -fd`, deleting `.git` | Back up and resolve exact target |
| Published-history rewrite | force push, `filter-repo`, orphan-root replacement | Coordinate, rotate secrets, use leases |

Do not copy destructive commands with unresolved variables, broad globs, `~`, `$HOME`, or `/`. Never assume history rewriting revokes a leaked credential.

---

## 2. Installation and initial configuration

### Prerequisites

| Tool | Purpose | Verify |
|---|---|---|
| Git | Version control | `git --version` |
| VS Code | Editor and source-control UI | `code --version` |
| GitHub account | Remote hosting and collaboration | Sign in at GitHub |
| Project runtime | Node, Python, JDK, etc. | Runtime-specific command |
| GitHub CLI (optional) | PR, issue, workflow, release CLI | `gh --version` |

Run identity configuration once per machine:

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
git config --global init.defaultBranch main
```

Recommended general defaults:

```bash
git config --global push.default simple
git config --global fetch.prune true
git config --global merge.conflictstyle zdiff3
git config --global diff.algorithm histogram
git config --global core.editor "code --wait"
```

Choose a pull policy deliberately. Rebase keeps local-only commits linear:

```bash
git config --global pull.rebase true
```

Use line-ending settings appropriate to the machine:

```bash
# Linux/macOS: keep LF in the repository
git config --global core.autocrlf input

# Windows: convert checkout to CRLF and commit to LF
git config --global core.autocrlf true
```

Inspect where a value came from:

```bash
git config --list --show-origin
git config --global --list
git config --local --list
```

Repository-local configuration overrides global configuration:

```bash
git config user.name "Project-specific Name"
git config user.email "project@example.com"
```

---

## 3. Create a repository locally and on GitHub

### Start from an existing local project

From the exact project directory:

```bash
git init -b main
git status
```

For older Git versions:

```bash
git init
git branch -M main
```

Create `.gitignore` before staging. Then inspect and commit:

```bash
git status --ignored --short
git add -A
git diff --cached --stat
git diff --cached
git commit -m "Initial commit"
```

### Create the GitHub repository

On GitHub: **New repository** → choose owner/name/visibility. If local files already exist, do not initialize the GitHub repository with a README, license, or `.gitignore`; that creates an unrelated remote root requiring reconciliation.

Connect and publish:

```bash
git remote add origin https://github.com/<OWNER>/<REPO>.git
git remote -v
git push -u origin main
```

`-u` records the upstream. Later, `git push` and `git pull` know the default counterpart.

### Create with GitHub CLI

```bash
gh auth login
gh repo create <REPO> --private --source=. --remote=origin --push
```

Choose `--public`, `--private`, or `--internal` intentionally.

### Remote management

```bash
git remote -v
git remote get-url origin
git remote set-url origin https://github.com/<OWNER>/<NEW-REPO>.git
git remote rename origin upstream
git remote remove origin
```

Rename a GitHub repository in repository Settings, then update the local URL. GitHub often redirects old URLs, but relying permanently on redirects is poor maintenance.

---

## 4. Clone, fork, and configure remotes

### Clone

```bash
git clone https://github.com/<OWNER>/<REPO>.git
git clone git@github.com:<OWNER>/<REPO>.git
git clone <URL> custom-folder
git clone --branch <BRANCH> <URL>
git clone --depth 1 <URL>
git clone --recurse-submodules <URL>
```

A shallow clone omits most history. It is useful for CI or quick inspection, but may limit blame, bisect, and history rewriting. Convert it later with:

```bash
git fetch --unshallow
```

### Fork workflow

1. Fork the upstream repository on GitHub.
2. Clone your fork as `origin`.
3. Add the original repository as `upstream`.

```bash
git clone git@github.com:<YOU>/<REPO>.git
cd <REPO>
git remote add upstream https://github.com/<ORIGINAL-OWNER>/<REPO>.git
git remote -v
```

Keep the fork synchronized:

```bash
git fetch upstream
git switch main
git merge --ff-only upstream/main
git push origin main
```

Create contribution branches on the fork, then open a PR against upstream.

### Multiple remotes

Useful in migrations or mirrored hosting:

```bash
git remote add backup <BACKUP-URL>
git push backup --all
git push backup --tags
```

Always inspect `git remote -v` before a destructive or privileged push.

---

## 5. Authentication: SSH, HTTPS, and GitHub CLI

### SSH: recommended for regular development

```bash
ssh-keygen -t ed25519 -C "you@example.com"
```

Use the default key path unless managing multiple identities, and protect the private key with a passphrase. Upload only the `.pub` content to GitHub → Settings → SSH and GPG keys.

```bash
ssh-add ~/.ssh/id_ed25519
ssh -T git@github.com
git remote set-url origin git@github.com:<OWNER>/<REPO>.git
```

Never paste or upload the private key.

### HTTPS and personal access tokens

GitHub no longer accepts account passwords for Git over HTTPS. Use a fine-grained personal access token with the narrowest repository and permissions needed, or authenticate through Git Credential Manager/VS Code.

When prompted:

- Username: GitHub username.
- Password: personal access token.

Avoid placing a token in a command or remote URL because it can enter shell history and logs.

Credential helpers:

```bash
# Temporary in-memory cache
git config --global credential.helper 'cache --timeout=3600'

# Inspect configured helper
git config --show-origin --get-all credential.helper
```

Plain `credential.helper store` writes credentials unencrypted and is not recommended on an untrusted or shared machine.

### GitHub CLI

```bash
gh auth login
gh auth status
gh repo view
gh issue list
gh pr list
gh pr create --title "..." --body "..."
gh pr checkout 42
gh pr merge 42 --squash
gh run list
gh run watch
gh release create v1.0.0 --notes "First release"
```

### Authentication failures

- `Permission denied (publickey)`: verify SSH agent, uploaded public key, and SSH remote.
- Password authentication removed: use PAT, SSH, GitHub CLI, or credential manager.
- HTTP 403: check repository access, token scope, organization SSO, and branch protection.
- Wrong account: inspect credential manager and `ssh -T git@github.com` output.

---

## 6. Daily workflow and staging

### Begin work by inspecting and fetching

```bash
git status --short --branch
git fetch origin --prune
git log --left-right --oneline HEAD...@{upstream}
```

`fetch` downloads refs without changing working files. `pull` is fetch followed by merge or rebase. Inspect-first workflow:

```bash
git fetch origin
git log HEAD..origin/main --oneline
git rebase origin/main
```

If the repository standard permits it, the shorter form is:

```bash
git pull --rebase
```

### Inspect, stage, and commit

```bash
git status
git diff                    # unstaged
git add path/to/file        # selected file
git add -p                  # interactively stage hunks
git diff --cached           # staged snapshot
git diff --cached --check   # whitespace/conflict-marker check
git commit -m "feat: describe the outcome"
```

Use `git add -A` only after reviewing the whole working tree. It stages modifications, additions, and deletions.

### Commit-message pattern

Conventional Commits are optional but useful:

```text
feat(scope): add capability
fix(scope): correct behavior
docs: improve documentation
refactor(scope): restructure without intended behavior change
test: add or update verification
chore: tooling or maintenance
```

Describe the outcome and reason. Keep unrelated work in separate commits.

### Push

```bash
git push
git push -u origin feature/short-description
```

### Staged versus unstaged inspection

```bash
git diff --name-only
git diff --cached --name-only
git status --short
git show --stat HEAD
```

### End-of-task checklist

```bash
git diff --check
git status --short
git log -1 --oneline --decorate
```

Run project-specific verification only as authorized and relevant.

---

## 7. `.gitignore`, untracking, and data hygiene

### How patterns work

Patterns are relative to the repository root:

| Pattern | Meaning |
|---|---|
| `node_modules/` | Matching directory at any depth |
| `/node_modules/` | Only at repository root |
| `*.log` | Matching files anywhere |
| `**/cache/` | `cache` directories at any depth |
| `!example.env` | Re-include a previously ignored path |
| `/build/` | Root build directory only |

`.gitignore` affects untracked files. It does not erase or untrack files already committed.

Debug rules:

```bash
git check-ignore -v path/to/file
git status --ignored --short
git ls-files path/to/file
```

No output from `check-ignore` means no ignore rule matched.

### Untrack without deleting locally

One file:

```bash
git rm --cached .env
git add .gitignore
git commit -m "chore: stop tracking local environment"
```

One directory:

```bash
git rm -r --cached path/to/generated/
```

Rebuild the entire index only when intentionally applying a reviewed `.gitignore` across the repository:

```bash
git rm -r --cached .
git add -A
git status
git diff --cached
git commit -m "chore: apply ignore rules"
```

This preserves disk files because `--cached` targets only the index, but it can stage a very large change. Review it carefully.

### Common ignore mistakes

- `.env$` treats `$` literally; use `.env`.
- `/folder/.env` matches only that location.
- CRLF artifacts can make patterns confusing; use `git check-ignore -v`.
- Negation works only if the parent directory is not excluded too broadly.
- A tracked secret remains tracked until `git rm --cached` or history cleanup.

### Secret and privacy rules

Never commit:

- API tokens, bot tokens, passwords, signing keys, keystores, recovery codes
- Real patient/customer/user data
- Database dumps, clinical exports, production backups
- Local machine paths and credential-bearing config

Commit an example with placeholders when useful:

```dotenv
API_URL=https://example.invalid
API_TOKEN=replace-me
```

Use local ignored files, CI secrets, platform secret stores, or a managed vault. Secret scanners such as gitleaks can supplement—not replace—manual staged-diff review.

If a secret is committed or pushed, rotate/revoke it first. Removing text from Git does not invalidate the credential.

---

## 8. Branching, merging, rebasing, and conflicts

### Branch names

```text
feature/user-authentication
fix/login-redirect
refactor/report-pipeline
docs/readme
chore/dependency-maintenance
```

Create and publish:

```bash
git switch -c feature/login
# work, stage, commit
git push -u origin feature/login
```

List and inspect:

```bash
git branch
git branch -a
git branch -vv
git log --oneline main..feature/login
```

Delete only after merge/verification:

```bash
git branch -d feature/login
git push origin --delete feature/login
git fetch --prune
```

`git branch -D` discards an unmerged branch name; verify commits are preserved elsewhere first.

### Merge

```bash
git switch main
git fetch origin
git merge --ff-only origin/main
git merge feature/login
```

Merge preserves branch topology and may create a merge commit.

### Rebase

```bash
git switch feature/login
git fetch origin
git rebase origin/main
```

Rebase rewrites the feature commits onto a new base. Do not rebase published commits used by others unless coordinated.

### Conflicts

```bash
git status
```

Markers look like:

```text
<<<<<<< HEAD
current side
=======
incoming side
>>>>>>> other-branch
```

Resolve the intended combined content, remove markers, then:

```bash
git add path/to/resolved-file
git merge --continue       # when supported, or git commit
git rebase --continue
git cherry-pick --continue
```

Abort safely before completion:

```bash
git merge --abort
git rebase --abort
git cherry-pick --abort
```

Do not blindly choose “ours,” “theirs,” “current,” or “incoming”; their meaning changes by operation.

### Unrelated histories

`--allow-unrelated-histories` is for intentionally joining two independent roots. Do not use it reflexively to fix a mistaken remote. First inspect:

```bash
git log --oneline --graph --all --decorate
git remote -v
```

---

## 9. Pull requests and GitHub collaboration

Typical flow:

```bash
git switch -c feature/example
# commit work
git push -u origin feature/example
gh pr create --title "feat: example" --body "Purpose, behavior, verification"
```

A useful PR includes:

- Problem and intended outcome
- Main implementation decisions
- Screenshots for UI changes
- Verification performed and not performed
- Migration/deployment/security effects
- Known limitations and follow-up work

GitHub capabilities:

| Feature | Purpose |
|---|---|
| Issues | Bugs, tasks, feature discussion |
| Pull requests | Review and merge changes |
| Actions | CI/CD workflows |
| Releases | Versioned artifacts and notes |
| Projects | Planning boards |
| Discussions | Community Q&A |
| Security | Alerts, dependency and secret scanning |
| CODEOWNERS | Automatic reviewers by path |

Branch protection/rulesets can require PRs, approvals, status checks, signed commits, linear history, and restricted pushes. Configure rules before expecting them to protect the branch.

Example `CODEOWNERS`:

```text
/frontend/ @OWNER
/backend/  @OWNER
*.md       @DOCS-OWNER
```

Templates commonly live at:

```text
.github/pull_request_template.md
.github/ISSUE_TEMPLATE/bug.yml
```

---

## 10. Tags, releases, and semantic versioning

Semantic versioning:

```text
MAJOR.MINOR.PATCH
```

- MAJOR: incompatible/breaking change
- MINOR: backward-compatible feature
- PATCH: backward-compatible fix
- Pre-release: `1.0.0-alpha.1`, `1.0.0-rc.2`

Annotated tags are preferred for releases:

```bash
git tag -a v1.2.3 -m "Release v1.2.3"
git show v1.2.3
git push origin v1.2.3
```

Lightweight tag:

```bash
git tag v1.2.3
```

Delete carefully:

```bash
git tag -d v1.2.3
git push origin --delete v1.2.3
```

Create a GitHub Release from the tag and attach generated artifacts there rather than committing binaries. Record source commit, toolchain, checksums, signing identity, and release notes.

---

## 11. Undo, recovery, and debugging history

### Working tree and staging

Unstage while keeping edits:

```bash
git restore --staged path/to/file
```

Discard one unstaged file only when its edits are disposable:

```bash
git restore path/to/file
```

Restore from another commit:

```bash
git restore --source=<SHA> -- path/to/file
```

### Amend an unshared commit

```bash
git add -A
git commit --amend
git commit --amend --no-edit
```

Amending changes the commit ID. Avoid amending a shared commit unless coordinated.

### Revert shared history

```bash
git revert <SHA>
git revert <OLDEST>^..<NEWEST>
```

Revert creates new commits and is normally the safest undo for published work.

### Reset local history

```bash
git reset --soft HEAD~1    # keep changes staged
git reset --mixed HEAD~1   # keep changes unstaged
git reset --hard HEAD~1    # discard tracked changes: destructive
```

Before `--hard`, inspect status and create a recovery branch or backup.

### Reflog

Reflog records local ref movements and often recovers lost commits:

```bash
git reflog
git show <RECOVERED-SHA>
git branch recovered-work <RECOVERED-SHA>
```

Reflog is local and expires eventually; it is not a backup strategy.

### Bisect

```bash
git bisect start
git bisect bad
git bisect good <KNOWN-GOOD-SHA>
# test checked-out midpoint, then repeat:
git bisect good
git bisect bad
git bisect reset
```

Automated bisect:

```bash
git bisect run <verification-command>
```

### Detached HEAD

Save work before switching away:

```bash
git switch -c recovered-branch
```

### Clean

Preview ignored/untracked deletion before any clean:

```bash
git clean -nd
git clean -ndX
```

`git clean -fd` and especially `git clean -fdx` permanently remove untracked/ignored files. They can delete credentials and local databases; avoid them unless targets are verified and backed up.

---

## 12. Advanced operations

### Stash

```bash
git stash push -m "wip: description"
git stash -u                         # include untracked
git stash list
git stash show -p stash@{0}
git stash apply stash@{0}            # keep stash
git stash pop                        # apply and drop on success
git stash drop stash@{0}
git stash branch recovered-stash stash@{0}
```

`git stash -a` also captures ignored files and can accidentally include secrets; use only with full awareness.

### Cherry-pick

```bash
git cherry-pick <SHA>
git cherry-pick <SHA1> <SHA2>
git cherry-pick <START>^..<END>
git cherry-pick --continue
git cherry-pick --abort
```

Cherry-pick copies changes as new commits; it does not preserve original ancestry.

### Interactive rebase

```bash
git rebase -i HEAD~5
```

Actions: `pick`, `reword`, `edit`, `squash`, `fixup`, `drop`. Reorder carefully. Never casually rewrite a shared branch.

Autosquash flow:

```bash
git commit --fixup <TARGET-SHA>
git rebase -i --autosquash <BASE-SHA>
```

### Worktrees

```bash
git worktree add ../repo-hotfix -b hotfix/urgent main
git worktree list
git worktree remove ../repo-hotfix
git worktree prune
```

Worktrees provide multiple checked-out branches without stashing.

### Submodules

```bash
git submodule add <URL> libs/example
git submodule update --init --recursive
git clone --recurse-submodules <URL>
git submodule foreach git status
```

Submodules pin exact commits and add operational complexity. Use only when independent repository ownership is required.

### Git LFS

```bash
git lfs install
git lfs track "*.psd"
git lfs track "*.mp4"
git add .gitattributes
git commit -m "chore: track large assets with LFS"
```

LFS must be installed for collaborators and CI. Adding a rule does not migrate already committed binaries; use `git lfs migrate` only after reviewing history-rewrite impact.

### Notes

```bash
git notes add -m "Reviewed by QA" <SHA>
git notes show <SHA>
git push origin refs/notes/commits
```

Notes are separate refs and are not fetched/pushed by default.

---

## 13. History cleanup, secret removal, and full-history squashing

### Decision tree

1. **File never committed:** add an ignore rule; nothing else required.
2. **Tracked but no sensitive history concern:** `git rm --cached`, commit normally.
3. **Only latest unpushed commit is wrong:** untrack and amend.
4. **Published secret:** rotate immediately, then consider history filtering.
5. **Large unwanted file throughout history:** use `git filter-repo`.
6. **Intentionally replace all history with current state:** use an orphan root or controlled reinitialization, then lease-protected force push.

### Mandatory preparation

Resolve the exact repository and remote:

```bash
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git branch -vv
git tag --list
git submodule status
```

Create a backup outside the repository and verify it. Generic archive example, executed from the parent directory after replacing exact names:

```bash
tar --exclude='node_modules' --exclude='.gradle' --exclude='build' \
    --exclude='.venv' --exclude='venv' --exclude='__pycache__' \
    -czf "REPO-backup-YYYYMMDD.tar.gz" REPO/
```

An additional Git-native safety copy is useful:

```bash
git bundle create ../REPO-before-rewrite.bundle --all
git bundle verify ../REPO-before-rewrite.bundle
```

Never place a backup containing secrets in cloud storage without appropriate encryption.

### Last unpushed commit cleanup

```bash
git rm --cached path/to/secret
git add .gitignore
git commit --amend --no-edit
git ls-files path/to/secret
git check-ignore -v path/to/secret
```

Rotate the credential even if exposure beyond the machine is uncertain.

### Remove selected paths from all history with `git filter-repo`

Prefer a fresh clone or verified backup:

```bash
git filter-repo --path path/to/file --invert-paths
git filter-repo --path-glob '**/__pycache__/**' --invert-paths
git filter-repo --path-glob '*.pyc' --invert-paths
```

`git filter-repo` may remove remotes as a safety measure. Re-add only the verified URL:

```bash
git remote add origin <VERIFIED-URL>
```

Inspect rewritten refs before publishing:

```bash
git log --oneline --graph --all
git tag --list
git fsck --no-reflogs --unreachable
```

Coordinate with collaborators. Publish rewritten branches/tags only when intentional:

```bash
git push --force-with-lease origin <BRANCH>
git push --force-with-lease origin --tags
```

An exact lease is safest when replacing one known remote tip:

```bash
git push --force-with-lease=<BRANCH>:<EXPECTED-REMOTE-SHA> origin <BRANCH>
```

### Replace all history with one root commit: orphan method

This preserves repository configuration and is safer than deleting `.git`:

```bash
git fetch origin
git switch --orphan new-root
git add -A
git diff --cached --stat
git diff --cached
git commit -m "Initial commit"
git branch -M <PRIMARY-BRANCH>
```

Verify one root commit and all tracked paths:

```bash
git rev-list --count <PRIMARY-BRANCH>
git log --oneline --decorate --graph
git ls-files
```

Then use an exact lease against the remote SHA recorded immediately before the rewrite:

```bash
git push --force-with-lease=<PRIMARY-BRANCH>:<EXPECTED-REMOTE-SHA> \
  -u origin <PRIMARY-BRANCH>
```

### Full reinitialization method: exceptional case

Deleting `.git` erases all local Git history, refs, remotes, hooks, worktree metadata, stash, config, notes, and submodule metadata. Use only for a personal repository after a verified external backup and recorded remote/branch values.

The conceptual sequence is:

```text
verify backup -> record remotes/branches -> remove only REPO/.git
-> initialize -> review ignored/tracked files -> create root commit
-> re-add verified remote -> lease-protected force push
```

Do not paste a recursive deletion command with an unresolved path. The orphan method is normally preferable.

### Deprecated `filter-branch`

`git filter-branch` is slow and error-prone. Use `git filter-repo` unless maintaining an environment where no safer tool is available.

### Collaboration and hosting consequences

- Collaborators should re-clone or carefully realign after a rewrite.
- Open PRs and commit links may break.
- Forks and cached objects can retain old commits.
- Protected branches may reject force pushes.
- GitHub Support may be needed for sensitive cached-object cleanup.
- CI deploy keys and local hooks may need reconfiguration after full reinitialization.

### Secret incident response

1. Revoke/rotate the token, password, bot token, signing key, or certificate.
2. Remove it from the current tree and add correct ignore rules.
3. Search branches, tags, PRs, issues, Actions logs/artifacts, Releases, and forks.
4. Rewrite history if appropriate.
5. Force-push only coordinated rewritten refs.
6. Ask collaborators to discard contaminated clones or remove old objects.
7. Audit service access logs and permissions.

History cleanup is hygiene; rotation is containment.

---

## 14. VS Code workflows and multi-root workspaces

Open Source Control with `Ctrl+Shift+G`. Use the file diff before clicking `+` to stage. The status bar shows the checked-out branch and sync state. The Command Palette offers `Git: Commit`, `Git: Fetch`, `Git: Pull`, `Git: Push`, and conflict actions.

VS Code’s merge editor helps compare Current, Incoming, Base, and Result. Review the final result rather than relying on “Accept Both.”

### Multi-root workspace

Use one window for separate frontend/backend repositories:

1. Open one folder.
2. **File → Add Folder to Workspace**.
3. Save a `.code-workspace` file outside or at the common parent.

```json
{
  "folders": [
    { "name": "Frontend", "path": "frontend" },
    { "name": "Backend", "path": "backend" }
  ],
  "settings": {
    "editor.formatOnSave": true,
    "files.eol": "\n"
  }
}
```

The Source Control panel shows each repository separately. Confirm which repository a terminal is in before committing.

Useful extensions:

- GitLens
- Git Graph
- GitHub Pull Requests and Issues
- EditorConfig
- Error Lens
- Stack-specific formatter/linter extensions

Extensions improve visibility but do not replace command-line inspection of the staged snapshot.

---

## 15. Monorepo versus polyrepo

| Model | Best when | Tradeoff |
|---|---|---|
| Monorepo | Shared code, atomic cross-component changes, one CI policy | Larger checkout and more complex CI ownership |
| Polyrepo | Separate teams, permissions, release cycles, deployments | Cross-repo changes and versions require coordination |

Monorepo example:

```text
product/
  frontend/
  backend/
  shared/
  .github/workflows/
```

Path-filtered CI:

```yaml
on:
  push:
    paths:
      - "frontend/**"
```

Avoid an accidental nested `.git` directory. Use submodules only when the nested repository relationship is intentional.

---

## 16. Configuration extras, attributes, hooks, and signing

### Useful aliases

```ini
[alias]
    st = status --short --branch
    sw = switch
    lg = log --oneline --graph --all --decorate
    last = log -1 HEAD
    unstage = restore --staged
    amend = commit --amend --no-edit
```

Avoid aliases that hide destructive commands.

### Global ignore

```bash
git config --global core.excludesfile ~/.gitignore_global
```

Use it for universal OS/editor noise, not project-required ignore policy.

### `.gitattributes`

```gitattributes
* text=auto eol=lf
*.sh text eol=lf
*.bat text eol=crlf
*.png binary
*.jpg binary
*.pdf binary
*.zip binary
```

Attributes standardize line endings, diffs, binary treatment, LFS rules, and GitHub Linguist behavior.

### Hooks

Local hooks live in `.git/hooks/` and are not committed. Share hooks through a documented installer, a hooks framework, or CI. Typical hooks:

- `pre-commit`: formatting, lint, secret scan
- `commit-msg`: message convention
- `pre-push`: focused verification
- `post-merge`: dependency refresh

Hooks can be bypassed and therefore are not a security boundary.

### Signed commits

SSH signing:

```bash
git config --global gpg.format ssh
git config --global user.signingkey ~/.ssh/id_ed25519.pub
git config --global commit.gpgsign true
```

Upload the public key as a GitHub signing key. GPG signing is also supported. Protect private keys and retain recovery/revocation procedures.

---

## 17. GitHub Actions and repository controls

Minimal generic CI:

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./project-specific-verification-command
```

Pin permissions to the minimum required:

```yaml
permissions:
  contents: read
```

Store secrets in GitHub Actions Secrets/Environments, not YAML. Protect production environments with approvals. Treat Actions logs and uploaded artifacts as possible leak locations.

Repository safeguards:

- Branch rulesets/protection
- Required PR review and status checks
- Dependabot and dependency review
- Secret scanning and push protection
- CODEOWNERS
- Environment protection
- Restricted release permissions

Never add a CI build merely by copying a stack-inappropriate example. Match the actual toolchain and repository policy.

---

## 18. Stack-specific repository practices

### Frontend JavaScript/TypeScript

- Commit one package-manager lockfile.
- Ignore `node_modules/`, build output, caches, and local `.env` files.
- Commit `.env.example` with placeholders.
- Pin supported Node versions.
- Run formatter, linter, type check, tests, and build in CI as appropriate.
- Use PR deployment previews without exposing production secrets.

### Python backend

- Ignore virtual environments, `__pycache__`, bytecode, coverage, and local databases.
- Commit `pyproject.toml`/lockfile or requirements.
- Commit migrations when the project relies on migrations; never commit the live database.
- Use tools such as Ruff/Black, mypy, and pytest as the project specifies.
- Keep deployment secrets in environment/secret stores.

### Android/Kotlin

- Ignore `.gradle/`, build outputs, local SDK paths, signing material, APK/AAB artifacts, and IDE user state.
- Commit Gradle wrapper files and version catalogs needed for reproducible builds.
- Do not commit `local.properties`, keystores, passwords, clinical exports, or generated packages.
- Keep release signing separate from debug signing.
- Record compile/target/min SDK and JDK expectations in README or technical state docs.
- Attach APK/AAB files to Releases or an artifact store rather than Git history.

---

## 19. Maintenance and integrity

Normally Git performs maintenance automatically:

```bash
git count-objects -vH
git fsck
git gc
git remote prune origin
```

`git gc --aggressive` can be slow and usually offers little benefit. `git prune` permanently removes unreachable objects and can defeat reflog recovery; do not use it casually, especially immediately after a mistake or rewrite.

Repository health review:

```bash
git status --short --branch
git branch -vv
git remote -v
git tag --list
git fsck --full
```

---

## 20. Common errors and exact recovery paths

### `fatal: remote origin already exists`

```bash
git remote -v
git remote set-url origin <CORRECT-URL>
```

Remove/re-add only after confirming the existing remote is wrong.

### Push rejected because remote contains work

```bash
git fetch origin
git log --left-right --oneline HEAD...origin/<BRANCH>
git rebase origin/<BRANCH>
git push
```

Do not answer a normal non-fast-forward rejection with force push.

### `src refspec main does not match any`

The branch has no commit or has another name:

```bash
git status
git branch --show-current
git add -A
git commit -m "Initial commit"
git branch -M main
```

### `fatal: not a git repository`

```bash
pwd
git rev-parse --show-toplevel
```

Move into the repository; do not initialize a nested repository accidentally.

### Local changes would be overwritten

Choose deliberately:

```bash
# Commit coherent work, or:
git stash push -u -m "wip before update"
git pull --rebase
git stash pop
```

### File remains tracked after ignore rule

```bash
git ls-files path/to/file
git check-ignore -v path/to/file
git rm --cached path/to/file
```

### Line-ending churn

Set `.gitattributes`, configure `core.autocrlf`, then normalize in a dedicated reviewed commit:

```bash
git add --renormalize .
git diff --cached
```

### Detached HEAD

```bash
git switch -c recovered-work
```

### Rebase/merge/cherry-pick appears stuck

```bash
git status
git diff --check
```

Resolve and continue, or use the matching `--abort` command.

### Force push rejected

Check branch protection/rulesets and whether the rewrite is actually authorized. Do not disable protection reflexively. Coordinate, use an exact lease, and restore rules immediately if an exceptional rewrite is approved.

---

## 21. Command reference

### Setup and inspect

```bash
git init -b main
git clone <URL>
git status --short --branch
git log --oneline --graph --all --decorate
git show <SHA>
git blame path/to/file
```

### Stage and commit

```bash
git add path/to/file
git add -p
git add -A
git diff --cached
git commit -m "message"
git commit --amend
```

### Branch

```bash
git branch -a
git switch -c feature/name
git switch main
git branch -d feature/name
git push origin --delete feature/name
```

### Remote synchronization

```bash
git fetch origin --prune
git pull --rebase
git push
git push -u origin <BRANCH>
git remote -v
```

### Compare

```bash
git diff
git diff --cached
git diff <BASE>...<HEAD>
git log <BASE>..<HEAD> --oneline
git log --left-right --oneline <LOCAL>...<REMOTE>
```

### Undo and recover

```bash
git restore --staged path/to/file
git restore path/to/file
git revert <SHA>
git reflog
git branch recovered <SHA>
```

### Ignore

```bash
git check-ignore -v path/to/file
git status --ignored --short
git rm --cached path/to/file
```

### Tags and releases

```bash
git tag --list
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
gh release create v1.0.0 --notes "Release notes"
```

---

## 22. Reusable templates

### General `.gitignore`

```gitignore
# Environment and secrets
.env
.env.*
!.env.example
*.pem
*.key
*.p12
*.pfx

# Logs and diagnostics
*.log
*.hprof

# OS/editor
.DS_Store
Thumbs.db
.idea/
.vscode/*
!.vscode/extensions.json
```

### Frontend additions

```gitignore
node_modules/
dist/
build/
.next/
out/
.vite/
.cache/
.parcel-cache/
.eslintcache
.turbo/
npm-debug.log*
yarn-debug.log*
pnpm-debug.log*
```

### Python additions

```gitignore
__pycache__/
*.py[cod]
.venv/
venv/
env/
dist/
build/
*.egg-info/
.pytest_cache/
.mypy_cache/
.ruff_cache/
.coverage
htmlcov/
*.sqlite3
*.db
```

### Android additions

```gitignore
.gradle/
.kotlin/
**/build/
local.properties
*.local.properties
keys/
keystore.properties
*.jks
*.keystore
*.apk
*.aab
*.apks
.idea/caches/
.idea/workspace.xml
```

### `.gitattributes`

```gitattributes
* text=auto eol=lf
*.md text
*.txt text
*.json text
*.yml text
*.yaml text
*.kt text
*.kts text
*.java text
*.py text
*.js text
*.ts text
*.sh text eol=lf
*.bat text eol=crlf
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.pdf binary
*.zip binary
*.woff binary
*.woff2 binary
```

### Recommended `.gitconfig` baseline

```ini
[user]
    name = Your Name
    email = you@example.com

[init]
    defaultBranch = main

[pull]
    rebase = true

[push]
    default = simple
    autoSetupRemote = true

[fetch]
    prune = true

[core]
    autocrlf = input
    editor = code --wait

[merge]
    conflictstyle = zdiff3

[rebase]
    autosquash = true

[diff]
    algorithm = histogram
    colorMoved = default
```

Adjust identity, signing, credential helper, line endings, and global ignore path for the actual machine.

---

## 23. ShiftReport-specific workflow and current state

This section is intentionally project-specific. The preceding manual remains reusable for other repositories.

### Identity

```text
Repository: https://github.com/Hjjar-AI/ShiftReport.git
Remote: origin
Primary branch: Main
Root commit: 65eee0e Initial commit
```

At the time this guide was updated, local `Main` and the local `origin/Main` tracking ref both pointed to `65eee0e`. A tracking ref can be stale; fetch before relying on it.

The previous full-history replacement is complete. Do not force-push routine changes. The next publication should be a normal commit and normal push after checking the actual remote.

### Files expected in the current change

The deletion of `Plan.md` is intentional. Work tracking now uses:

```text
workDone.md
workRemains.md
```

Project documentation also includes:

```text
README.md
AGENTS.md
currentState.md
```

The application changes include hospital-agnostic setup, demo mode, encrypted join provisioning, report/PDF improvements, drawer/FAB improvements, readiness reporting, stale-write checks, and live admin authorization. Review the real staged diff rather than relying solely on this list.

### Private/ignored local files

These must remain ignored and local:

```text
local.properties
*.local.properties
legacy-hardcoded-credentials.local.properties
GITHUB-GUIDE.local.md
keys/
keystore.properties
*.jks
*.keystore
*.p12
*.pfx
*.pem
*.key
*.srjoin
*.rpb
*.rushdbackup
*.rushdbak
*.apk
*.aab
*.apks
```

Verify key rules:

```bash
git check-ignore -v local.properties
git check-ignore -v legacy-hardcoded-credentials.local.properties
git check-ignore -v GITHUB-GUIDE.local.md
git check-ignore -v keys
```

Do not use `git add -f` on these paths.

### Current publication procedure

1. Inspect without changing the index:

```bash
git status --short --branch
git diff --stat
git diff --check
git diff
```

2. Confirm remote state:

```bash
git fetch origin
git log --left-right --oneline Main...origin/Main
```

3. Stage and review every tracked change:

```bash
git add -A
git status --short
git diff --cached --stat
git diff --cached --check
git diff --cached
```

Confirm there are no tokens, credentials, signing files, real clinical records, exported reports, databases, backups, APKs, or AABs.

4. Commit:

```bash
git commit -m "Improve setup, report workflow, and concurrency safety"
git show --stat --oneline HEAD
git show --check HEAD
```

5. Fetch again and push normally:

```bash
git fetch origin
git log --left-right --oneline Main...origin/Main
git push origin Main
```

If remote-only commits appear with `>`, inspect and integrate them. Do not overwrite them with force.

6. Verify:

```bash
git fetch origin
git rev-parse Main
git rev-parse origin/Main
git status --short --branch
git log --oneline --decorate --graph -8
```

### Authentication

HTTPS remote:

```bash
git remote -v
```

Use a GitHub personal access token through a credential manager, never in the URL or command. SSH alternative:

```bash
git remote set-url origin git@github.com:Hjjar-AI/ShiftReport.git
ssh -T git@github.com
```

### Project verification rules

- Follow `AGENTS.md`.
- Always run `git diff --check` after edits.
- Do not inspect or change migration files unless explicitly requested.
- Do not inspect test-suite files unless explicitly requested.
- Do not run Gradle/build tasks unless explicitly authorized.
- Do not change Gradle/toolchain versions unless explicitly requested.
- Preserve the existing logo and About/credits page.

### Release privacy and signing checklist

- The current release build references debug signing; configure protected production signing before real distribution.
- Keep keys and passwords outside the repository.
- Confirm demo data is synthetic and in memory only.
- Confirm no hospital token, chat ID, real patient data, report, backup, or provisioning bundle is tracked.
- Build from the intended commit and record `git rev-parse HEAD`.
- Publish APK/AAB through a release/artifact channel, not Git history.

### If a credential is exposed

For a Telegram bot token, revoke/rotate through BotFather immediately. For other credentials, revoke them at their issuer. Then remove the value from the current tree, inspect all refs and GitHub surfaces, and decide whether `git filter-repo` plus a coordinated lease-protected rewrite is required.

The ignored file `legacy-hardcoded-credentials.local.properties` is for private reuse only. Its presence on disk is not permission to stage, print, share, or upload it.

### Force-push policy for ShiftReport

The initial-history rewrite has already been completed. Normal development uses:

```bash
git push origin Main
```

Only perform another rewrite after a separate explicit decision, verified backup, current remote fetch, recorded remote SHA, and exact `--force-with-lease=Main:<EXPECTED-SHA>`. Never use plain `--force` as a routine fix.
