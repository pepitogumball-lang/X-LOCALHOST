#!/usr/bin/env python3
"""
git_push.py — stages, commits, and pushes the full backend implementation
Run with: python script.py
You may need a GitHub Personal Access Token if HTTPS auth is required.
Set it as: export GITHUB_TOKEN=your_token_here  (or enter when prompted)
"""

import subprocess
import os
import sys

GIT_NAME  = "kruzerova02"
GIT_EMAIL = "60117691-kruzerova02@users.norepl.replit.com"
COMMIT_MSG = (
    "fix: remove invalid 'import kotlin.math.minOf' — minOf is a built-in top-level function\n\n"
    "CI failed with 'Unresolved reference: minOf' because kotlin.math.minOf does not\n"
    "exist in the kotlin.math package. minOf is available without any import as a\n"
    "standard Kotlin built-in. Removed the erroneous import from LocalWebServer.kt."
)
BRANCH = "main"
REMOTE = "origin"


def run(cmd, **kwargs):
    print(f"  $ {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True, **kwargs)
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.returncode != 0:
        print(f"[STDERR] {result.stderr.strip()}", file=sys.stderr)
        sys.exit(result.returncode)
    return result.stdout.strip()


def configure_git():
    print("[1/4] Configuring git identity …")
    run(["git", "config", "user.name",  GIT_NAME])
    run(["git", "config", "user.email", GIT_EMAIL])


def stage_all():
    print("[2/4] Staging all changes (git add .) …")
    run(["git", "add", "."])
    status = subprocess.run(
        ["git", "status", "--short"], capture_output=True, text=True
    ).stdout.strip()
    if status:
        print(status)
    else:
        print("  (nothing to stage — working tree already clean)")


def commit():
    print("[3/4] Committing …")
    result = subprocess.run(
        ["git", "diff", "--cached", "--quiet"],
        capture_output=True
    )
    if result.returncode == 0:
        print("  Nothing to commit (index is empty). Skipping commit.")
        return
    run(["git", "commit", "-m", COMMIT_MSG])


def push():
    print(f"[4/4] Pushing to {REMOTE}/{BRANCH} …")

    token = (
        os.environ.get("GITHUB_PERSONAL_ACCESS_TOKEN", "")
        or os.environ.get("GITHUB_TOKEN", "")
    ).strip()
    if not token:
        token = input(
            "  GitHub Personal Access Token (leave blank to push without token): "
        ).strip()

    if token:
        result = subprocess.run(
            ["git", "remote", "get-url", REMOTE],
            capture_output=True, text=True
        )
        url = result.stdout.strip()
        if url.startswith("https://") and "@" not in url:
            authed = url.replace("https://", f"https://{GIT_NAME}:{token}@")
            run(["git", "remote", "set-url", REMOTE, authed])
            run(["git", "push", REMOTE, BRANCH])
            run(["git", "remote", "set-url", REMOTE, url])
        else:
            run(["git", "push", REMOTE, BRANCH])
    else:
        run(["git", "push", REMOTE, BRANCH])

    print("\nDone! Changes pushed to GitHub.")


if __name__ == "__main__":
    configure_git()
    stage_all()
    commit()
    push()
