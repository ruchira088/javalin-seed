#!/usr/bin/env python3
"""
Deletes the GHCR container image versions that were published for a branch.

The build pipeline tags every image as `<branch>` and `<branch>-<short commit>`. Each of those tags points at a
multi-platform image index whose per-platform and attestation manifests are stored as separate *untagged* package
versions, so deleting only the tagged versions would leave orphans behind. This script deletes the tagged versions
for the branch and every child manifest they reference, unless a surviving index still references that child.

Requires a classic PAT with `read:packages` and `delete:packages` in the GITHUB_TOKEN environment variable.
"""

import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

PROTECTED_BRANCHES = {"main"}

GITHUB_API = "https://api.github.com"
REGISTRY = "https://ghcr.io"

INDEX_MEDIA_TYPES = (
    "application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
)


def tags_of(version):
    return version["metadata"]["container"]["tags"]


def is_branch_tag(tag, branch):
    return tag == branch or re.fullmatch(re.escape(branch) + r"-[0-9a-f]{7}", tag) is not None


def child_digests(manifest):
    if manifest.get("mediaType") not in INDEX_MEDIA_TYPES:
        return set()
    return {entry["digest"] for entry in manifest.get("manifests", [])}


def plan_deletions(versions, branch, fetch_manifest):
    """
    Returns the package versions to delete, parents before children.

    `fetch_manifest(digest)` must return the parsed manifest stored under that digest.
    """
    if branch in PROTECTED_BRANCHES:
        raise ValueError(f"Refusing to delete images for protected branch '{branch}'")

    by_digest = {version["name"]: version for version in versions}

    selected = [version for version in versions if any(is_branch_tag(tag, branch) for tag in tags_of(version))]
    selected_digests = {version["name"] for version in selected}

    still_referenced = set()
    for version in versions:
        if tags_of(version) and version["name"] not in selected_digests:
            still_referenced |= child_digests(fetch_manifest(version["name"]))

    children = []
    for version in selected:
        for digest in sorted(child_digests(fetch_manifest(version["name"]))):
            child = by_digest.get(digest)
            if child is not None and digest not in still_referenced and child not in children:
                children.append(child)

    return selected + children


class GitHubPackages:
    def __init__(self, owner, package, token):
        self.owner = owner
        self.package = package
        self.token = token

    def _request(self, method, url, headers=None):
        request = urllib.request.Request(url, method=method, headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            **(headers or {}),
        })
        with urllib.request.urlopen(request) as response:
            body = response.read()
            return json.loads(body) if body else None

    def list_versions(self):
        versions = []
        page = 1
        while True:
            url = (
                f"{GITHUB_API}/users/{self.owner}/packages/container/{urllib.parse.quote(self.package, safe='')}"
                f"/versions?per_page=100&page={page}"
            )
            batch = self._request("GET", url)
            versions.extend(batch)
            if len(batch) < 100:
                return versions
            page += 1

    def delete_version(self, version_id):
        url = (
            f"{GITHUB_API}/users/{self.owner}/packages/container/{urllib.parse.quote(self.package, safe='')}"
            f"/versions/{version_id}"
        )
        self._request("DELETE", url)


class Registry:
    def __init__(self, owner, package, token):
        self.repository = f"{owner}/{package}"
        self.bearer = self._registry_token(owner, token)

    def _registry_token(self, owner, token):
        credentials = base64.b64encode(f"{owner}:{token}".encode()).decode()
        request = urllib.request.Request(
            f"{REGISTRY}/token?scope=repository:{self.repository}:pull",
            headers={"Authorization": f"Basic {credentials}"},
        )
        with urllib.request.urlopen(request) as response:
            return json.load(response)["token"]

    def fetch_manifest(self, digest):
        request = urllib.request.Request(
            f"{REGISTRY}/v2/{self.repository}/manifests/{digest}",
            headers={"Authorization": f"Bearer {self.bearer}", "Accept": ", ".join(INDEX_MEDIA_TYPES)},
        )
        with urllib.request.urlopen(request) as response:
            return json.load(response)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--owner", required=True, help="GitHub user that owns the package")
    parser.add_argument("--package", required=True, help="container package name, e.g. javalin-seed-api")
    parser.add_argument("--branch", required=True, help="sanitized branch name used in the image tags")
    parser.add_argument("--dry-run", action="store_true", help="print the versions that would be deleted")
    args = parser.parse_args(argv)

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        parser.error("GITHUB_TOKEN environment variable is not set")

    packages = GitHubPackages(args.owner, args.package, token)
    registry = Registry(args.owner, args.package, token)

    versions = packages.list_versions()
    plan = plan_deletions(versions, args.branch, registry.fetch_manifest)

    print(f"{len(versions)} versions in {args.owner}/{args.package}; {len(plan)} to delete for branch '{args.branch}'")
    for version in plan:
        label = ", ".join(tags_of(version)) or "untagged"
        print(f"{'would delete' if args.dry_run else 'deleting'} {version['id']} {version['name']} ({label})")
        if not args.dry_run:
            packages.delete_version(version["id"])

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ValueError as error:
        print(error, file=sys.stderr)
        sys.exit(1)
    except urllib.error.HTTPError as error:
        print(f"{error.code} {error.reason} from {error.url}: {error.read().decode(errors='replace')}", file=sys.stderr)
        sys.exit(1)
