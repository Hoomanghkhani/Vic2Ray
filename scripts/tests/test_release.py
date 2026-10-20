import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "release.py"
spec = importlib.util.spec_from_file_location("release", SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class VersionTests(unittest.TestCase):
    def read(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            path.write_text(text)
            return release.load_version(path)

    def test_one_source_provides_tag_and_apk_version(self):
        version = self.read("versionName=1.1.0\nversionCode=22\n")
        self.assertEqual(version, {"version": "1.1.0", "code": "22", "tag": "v1.1.0"})

    def test_malformed_version_is_rejected_before_shell_interpolation(self):
        with self.assertRaises(ValueError):
            self.read("versionName=1.1.0;echo-secret\nversionCode=22\n")

    def test_missing_or_nonpositive_code_is_rejected(self):
        for code in ["", "0", "-1", "2147483648"]:
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.read("versionName=1.1.0\nversionCode=" + code)


class ReleaseDecisionTests(unittest.TestCase):
    def decide(self, **changes):
        values = dict(event="push", ref_type="branch", ref_name="main", tag="v1.1.0",
                      commit="a" * 40, existing_tag=None, release_exists=False)
        values.update(changes)
        return release.decide_release(**values)

    def test_new_main_version_publishes(self):
        self.assertTrue(self.decide())

    def test_existing_tag_on_an_older_commit_is_not_moved(self):
        self.assertFalse(self.decide(existing_tag="b" * 40))

    def test_retry_can_finish_release_after_its_tag_was_created(self):
        self.assertTrue(self.decide(existing_tag="a" * 40))

    def test_existing_release_is_never_overwritten(self):
        self.assertFalse(self.decide(existing_tag="a" * 40, release_exists=True))

    def test_matching_tag_publishes_the_validated_commit(self):
        self.assertTrue(self.decide(ref_type="tag", ref_name="v1.1.0", existing_tag="a" * 40))

    def test_mismatched_version_tag_is_rejected(self):
        with self.assertRaises(ValueError):
            self.decide(ref_type="tag", ref_name="v1.0.21")

    def test_remote_tag_cannot_point_to_a_different_commit(self):
        with self.assertRaises(ValueError):
            self.decide(ref_type="tag", ref_name="v1.1.0", existing_tag="b" * 40)

    def test_pull_requests_other_branches_and_manual_runs_only_validate(self):
        for changes in [dict(event="pull_request"), dict(event="workflow_dispatch"),
                        dict(ref_name="feature/test")]:
            with self.subTest(changes=changes):
                self.assertFalse(self.decide(**changes))


if __name__ == "__main__":
    unittest.main()
