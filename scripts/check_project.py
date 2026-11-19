"""Offline structural checks only; this does not compile Kotlin or run Android tests."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def check_delimiters(path: Path) -> None:
    # Skip comments and strings before checking balanced (), [] and {}. This
    # deliberately makes no claim about Kotlin's grammar or type correctness.
    text = path.read_text(encoding="utf-8")
    tokens = re.sub(
        r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
        "", text, flags=re.S,
    )
    stack: list[str] = []
    pairs = {"(": ")", "[": "]", "{": "}"}
    for char in tokens:
        if char in pairs:
            stack.append(pairs[char])
        elif char in pairs.values():
            check(bool(stack) and stack.pop() == char, f"Unbalanced delimiter: {path.relative_to(ROOT)}")
    check(not stack, f"Unclosed delimiter: {path.relative_to(ROOT)}")


def main() -> None:
    xml_files = list((ROOT / "app/src/main").rglob("*.xml"))
    trees = {path: ET.parse(path) for path in xml_files}
    manifest = trees[ROOT / "app/src/main/AndroidManifest.xml"].getroot()
    permission_nodes = manifest.findall("uses-permission")
    permissions = {node.get(ANDROID + "name") for node in permission_nodes if node.get(TOOLS + "node") != "remove"}
    check(permissions == {"android.permission.CAMERA"}, "Only camera permission should be requested")
    check(any(node.get(ANDROID + "name") == "android.permission.INTERNET" and node.get(TOOLS + "node") == "remove"
              for node in permission_nodes), "Transitive Internet permission must be removed")
    application = manifest.find("application")
    check(application is not None, "Missing application")
    check(application.get(ANDROID + "allowBackup") == "false", "Private captures must not be backed up")
    for activity in application.findall("activity"):
        name = activity.get(ANDROID + "name", "").removeprefix(".")
        check((ROOT / f"app/src/main/java/vn/dunggoc/camera/{name}.kt").is_file(), f"Missing activity: {name}")
    resources: set[str] = set()
    for path, tree in trees.items():
        if path.parent.name == "values":
            resources.update(f"{child.tag}/{child.attrib['name']}" for child in tree.getroot() if "name" in child.attrib)
        elif path.name != "AndroidManifest.xml":
            resources.add(f"{path.parent.name}/{path.stem}")
    for path in xml_files:
        for resource in re.findall(r'@([a-zA-Z_]+/[a-zA-Z_][\w.]*)', path.read_text(encoding="utf-8")):
            check(resource in resources, f"Unresolved resource {resource} in {path.relative_to(ROOT)}")

    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    check('include(":app", ":guidance")' in settings, "Both modules must be included")
    app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    check(re.search(r"minSdk\s*=\s*26\b", app_build) is not None, "Android 8 must remain supported")
    check('dependsOn(preparePoseModel)' in app_build, "Build must prepare bundled pose assets")
    check('generated/poseAssets' in app_build and 'assets.srcDir' in app_build, "Generated model must be packaged")
    model_name = re.search(r'setModelAssetPath\("([^"]+)"\)',
        (ROOT / "app/src/main/java/vn/dunggoc/camera/camera/PoseAnalyzer.kt").read_text(encoding="utf-8"))
    check(model_name is not None and model_name[1] in app_build, "Runtime and build model names differ")

    properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    checksum = re.search(r"distributionSha256Sum=([a-f0-9]{64})", properties)
    check(checksum is not None, "Missing pinned Gradle checksum")
    for launcher in ("gradlew", "scripts/gradle.ps1"):
        check(checksum[1] in (ROOT / launcher).read_text(encoding="utf-8"), f"Checksum differs in {launcher}")

    kotlin_files = list((ROOT / "app").rglob("*.kt")) + list((ROOT / "guidance").rglob("*.kt"))
    gradle_files = [ROOT / "settings.gradle.kts", ROOT / "build.gradle.kts", ROOT / "app/build.gradle.kts", ROOT / "guidance/build.gradle.kts"]
    for path in kotlin_files + gradle_files:
        check_delimiters(path)
    tests = (ROOT / "guidance/src/test/kotlin/vn/dunggoc/guidance/GuidanceTest.kt").read_text(encoding="utf-8")
    print(f"Structural checks passed: {len(xml_files)} XML files, {len(kotlin_files)} Kotlin files, resources, model packaging, permissions and bootstrap checksum.")
    print(f"JUnit cases present: {tests.count('@Test')}; NOT executed by this script.")
    print("Kotlin compilation, lint, APK assembly and device acceptance remain separate checks.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
