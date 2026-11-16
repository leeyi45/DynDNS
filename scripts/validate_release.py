#!/usr/bin/env python3

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLEW = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")

def get_plugin_version():
    result = subprocess.run(
      [str(GRADLEW), "-q", ":dyndns:printPluginVersion"],
      cwd=ROOT,
      capture_output=True,
      text=True,
      check=False # Don't throw an Exception if the command didn't exit with non-zero
    )

    if result.returncode != 0:
        print("ERROR: Could not determine Gradle project version.")
        print(result.stderr)
        sys.exit(1)

    return result.stdout.strip()

def main():
    release_tags = []

    # Git supplies the refs being pushed through stdin.
    for line in sys.stdin:
        fields = line.strip().split()

        if len(fields) != 4:
            continue

        local_ref, local_sha, remote_ref, remote_sha = fields

        if local_ref.startswith("refs/tags/v"):
            tag = local_ref.removeprefix("refs/tags/")
            release_tags.append(tag)

    if not release_tags:
        return

    project_version = get_plugin_version()

    if not project_version:
        print("ERROR: Gradle project version is empty.")
        sys.exit(1)

    print(f"Gradle project version: {project_version}")

    for tag in release_tags:
        tag_version = tag.removeprefix("v")

        print(f"Checking release tag: {tag}")

        if tag_version != project_version:
            print("\nERROR: Version mismatch!")
            print(f"Git tag version: {tag_version}")
            print(f"Plugin version:  {project_version}")
            print("\nPush rejected.")
            sys.exit(1)

        print("Version matches.")

    print("\nAll release tags validated successfully.")

if __name__ == "__main__":
    main()
