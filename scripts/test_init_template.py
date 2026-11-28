import os
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "init-template.sh")
GIT = ["git", "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false"]


class InitTemplateTest(unittest.TestCase):
    """Runs init-template.sh against a small throwaway repository (not this checkout)."""

    def setUp(self):
        self.repo = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.repo)

        self.write("settings.gradle", "rootProject.name = 'javalin-seed'\n")
        self.write("api/App.java", 'title("Javalin Seed API"); name("javalin-seed");\n')
        self.write("docs/notes with spaces.md", "javalin-seed-api image\n")
        self.write("untouched.txt", "nothing to rename\n")
        os.makedirs(os.path.join(self.repo, "scripts"))
        shutil.copy(SCRIPT, os.path.join(self.repo, "scripts", "init-template.sh"))

        subprocess.run(GIT + ["init", "-q"], cwd=self.repo, check=True)
        subprocess.run(GIT + ["add", "-A"], cwd=self.repo, check=True)
        subprocess.run(GIT + ["commit", "-q", "-m", "template"], cwd=self.repo, check=True)

    def write(self, path, content):
        full_path = os.path.join(self.repo, path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w") as f:
            f.write(content)

    def read(self, path):
        with open(os.path.join(self.repo, path)) as f:
            return f.read()

    def run_script(self, *args):
        return subprocess.run(
            ["bash", "scripts/init-template.sh", *args], cwd=self.repo, capture_output=True, text=True
        )

    def test_renames_kebab_and_derived_display_name_then_deletes_itself(self):
        result = self.run_script("payments-api")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("rootProject.name = 'payments-api'\n", self.read("settings.gradle"))
        self.assertEqual('title("Payments Api API"); name("payments-api");\n', self.read("api/App.java"))
        self.assertEqual("payments-api-api image\n", self.read("docs/notes with spaces.md"))
        self.assertEqual("nothing to rename\n", self.read("untouched.txt"))
        self.assertFalse(os.path.exists(os.path.join(self.repo, "scripts", "init-template.sh")))

    def test_uses_display_name_literally_even_with_regex_and_shell_characters(self):
        result = self.run_script("orders", "Orders & Co/$HOME \\1")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('title("Orders & Co/$HOME \\1 API"); name("orders");\n', self.read("api/App.java"))

    def test_rejects_invalid_project_names_without_changing_anything(self):
        for name in ["Payments", "-payments", "payments-", "p", "pay_ments", "pay ments"]:
            with self.subTest(name=name):
                result = self.run_script(name)

                self.assertEqual(1, result.returncode)
                self.assertIn("Invalid project name", result.stderr)
                self.assertEqual("rootProject.name = 'javalin-seed'\n", self.read("settings.gradle"))

    def test_refuses_to_run_on_a_dirty_working_tree(self):
        self.write("untouched.txt", "local edit\n")

        result = self.run_script("payments-api")

        self.assertEqual(1, result.returncode)
        self.assertIn("Working tree is dirty", result.stderr)
        self.assertEqual("rootProject.name = 'javalin-seed'\n", self.read("settings.gradle"))


if __name__ == "__main__":
    unittest.main()
