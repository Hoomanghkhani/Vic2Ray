"""Validate shared Android versions and decide whether a CI run may publish.

This script only reads Git/GitHub state. Publication remains an explicit workflow step.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


def load_version(path):
    properties = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key.strip() in properties:
            raise ValueError("Version properties must contain unique key=value entries")
        properties[key.strip()] = value.strip()
    version = properties.get("versionName", "")
    code = properties.get("versionCode", "")
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", version):
        raise ValueError("versionName must be a numeric major.minor.patch version")
    if not re.fullmatch(r"[1-9][0-9]*", code) or not 0 < int(code) <= 2100000000:
        raise ValueError("versionCode must be a positive Android version code")
    return {"version": version, "code": code, "tag": "v" + version}


def decide_release(event, ref_type, ref_name, tag, commit, existing_tag, release_exists):
    if ref_type == "tag" and ref_name != tag:
        raise ValueError("Git tag must exactly match version.properties: " + tag)
    if event != "push" or (ref_type != "tag" and (ref_type != "branch" or ref_name != "main")):
        return False
    if existing_tag and existing_tag != commit:
        if ref_type == "tag":
            raise ValueError("Remote release tag points to a different commit")
        return False
    return not release_exists


def remote_tag_commit(tag):
    output = subprocess.check_output(
        ["git", "ls-remote", "--tags", "origin", "refs/tags/" + tag, "refs/tags/" + tag + "^{}"],
        text=True,
    )
    refs = dict(reversed(line.split()) for line in output.splitlines())
    return refs.get("refs/tags/" + tag + "^{}", refs.get("refs/tags/" + tag))


def github_release_exists(repository, tag):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("GITHUB_REPOSITORY must identify owner/repository")
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        raise ValueError("GH_TOKEN is required to inspect the release state")
    request = Request(
        "https://api.github.com/repos/" + repository + "/releases/tags/" + quote(tag, safe=""),
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28"},
    )
    try:
        with urlopen(request, timeout=30) as response:
            json.load(response)
        return True
    except HTTPError as error:
        if error.code == 404:
            return False
        raise RuntimeError("Could not inspect GitHub releases: HTTP " + str(error.code)) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["metadata", "plan"])
    parser.add_argument("--version-file", default="version.properties")
    parser.add_argument("--github-output")
    args = parser.parse_args()
    values = load_version(args.version_file)
    ref_type = os.environ.get("GITHUB_REF_TYPE", "")
    ref_name = os.environ.get("GITHUB_REF_NAME", "")
    if ref_type == "tag" and ref_name != values["tag"]:
        raise ValueError("Git tag must exactly match version.properties: " + values["tag"])
    if args.command == "plan":
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        if commit != os.environ.get("GITHUB_SHA"):
            raise ValueError("Checkout must be the exact validated GITHUB_SHA")
        existing = remote_tag_commit(values["tag"])
        exists = github_release_exists(os.environ.get("GITHUB_REPOSITORY", ""), values["tag"])
        publish = decide_release(os.environ.get("GITHUB_EVENT_NAME", ""), ref_type, ref_name,
                                 values["tag"], commit, existing, exists)
        values.update(publish=str(publish).lower(), create_tag=str(existing is None).lower())
    if args.github_output:
        with Path(args.github_output).open("a", encoding="utf-8") as output:
            output.writelines(key + "=" + value + "\n" for key, value in values.items())
    print(json.dumps(values))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print("Release validation failed: " + str(error), file=sys.stderr)
        sys.exit(1)
