import unittest

from delete_ghcr_branch_images import plan_deletions


def version(version_id, digest, *tags):
    return {"id": version_id, "name": digest, "metadata": {"container": {"tags": list(tags)}}}


def index(*child_digests):
    return {
        "mediaType": "application/vnd.oci.image.index.v1+json",
        "manifests": [{"digest": digest} for digest in child_digests],
    }


SINGLE_MANIFEST = {"mediaType": "application/vnd.oci.image.manifest.v1+json", "layers": []}


class PlanDeletionsTest(unittest.TestCase):

    def test_selects_versions_tagged_for_the_branch(self):
        versions = [
            version(1, "sha256:a", "feature-x", "feature-x-abc1234"),
            version(2, "sha256:b", "feature-x-def5678"),
            version(3, "sha256:c", "feature-xy-abc1234"),
            version(4, "sha256:d", "main", "main-abc1234"),
            version(5, "sha256:e", "feature-x-notahash"),
        ]

        plan = plan_deletions(versions, "feature-x", lambda digest: SINGLE_MANIFEST)

        self.assertEqual([1, 2], [v["id"] for v in plan])

    def test_includes_untagged_children_of_selected_indexes(self):
        versions = [
            version(1, "sha256:index", "feature-x-abc1234"),
            version(2, "sha256:arm64"),
            version(3, "sha256:amd64"),
            version(4, "sha256:unrelated"),
        ]
        manifests = {"sha256:index": index("sha256:arm64", "sha256:amd64")}

        plan = plan_deletions(versions, "feature-x", lambda digest: manifests.get(digest, SINGLE_MANIFEST))

        self.assertEqual({1, 2, 3}, {v["id"] for v in plan})

    def test_deletes_indexes_before_their_children(self):
        versions = [
            version(2, "sha256:arm64"),
            version(1, "sha256:index", "feature-x-abc1234"),
        ]
        manifests = {"sha256:index": index("sha256:arm64")}

        plan = plan_deletions(versions, "feature-x", lambda digest: manifests.get(digest, SINGLE_MANIFEST))

        self.assertEqual([1, 2], [v["id"] for v in plan])

    def test_keeps_children_still_referenced_by_a_surviving_index(self):
        versions = [
            version(1, "sha256:feature-index", "feature-x-abc1234"),
            version(2, "sha256:main-index", "main-abc1234"),
            version(3, "sha256:shared"),
            version(4, "sha256:feature-only"),
        ]
        manifests = {
            "sha256:feature-index": index("sha256:shared", "sha256:feature-only"),
            "sha256:main-index": index("sha256:shared"),
        }

        plan = plan_deletions(versions, "feature-x", lambda digest: manifests.get(digest, SINGLE_MANIFEST))

        self.assertEqual({1, 4}, {v["id"] for v in plan})

    def test_ignores_children_that_are_not_package_versions(self):
        versions = [version(1, "sha256:index", "feature-x-abc1234")]
        manifests = {"sha256:index": index("sha256:missing")}

        plan = plan_deletions(versions, "feature-x", lambda digest: manifests.get(digest, SINGLE_MANIFEST))

        self.assertEqual([1], [v["id"] for v in plan])

    def test_no_matching_versions_is_a_noop(self):
        versions = [version(1, "sha256:a", "main", "main-abc1234")]

        plan = plan_deletions(versions, "feature-x", lambda digest: SINGLE_MANIFEST)

        self.assertEqual([], plan)

    def test_refuses_to_plan_deletions_for_main(self):
        versions = [version(1, "sha256:a", "main")]

        with self.assertRaises(ValueError):
            plan_deletions(versions, "main", lambda digest: SINGLE_MANIFEST)

    def test_branch_name_is_matched_literally_not_as_a_regex(self):
        versions = [
            version(1, "sha256:a", "feature.x-abc1234"),
            version(2, "sha256:b", "featureXx-abc1234"),
        ]

        plan = plan_deletions(versions, "feature.x", lambda digest: SINGLE_MANIFEST)

        self.assertEqual([1], [v["id"] for v in plan])


if __name__ == "__main__":
    unittest.main()
