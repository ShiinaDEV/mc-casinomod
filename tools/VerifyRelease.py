"""Check the actual installable JAR, resources, encoding and Java target after Gradle build."""
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
version = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M).group(1).strip()
jar = ROOT / "build/libs" / f"shiinacasino-{version}.jar"
bad_sequences = ("\ufffd", "Ã¤", "Ã¶", "Ã¼", "ÃŸ", "â€", "Â·")
checked_text = 0
for folder in ("src", "tools"):
    for path in (ROOT / folder).rglob("*"):
        if path.is_file() and path.suffix in (".java", ".json", ".toml", ".py") and path.name != "VerifyRelease.py":
            text = path.read_text(encoding="utf-8", errors="strict")
            # The regression tests intentionally name corruption markers, but contain no corrupted UI text.
            if "src/test" not in path.as_posix():
                assert not any(sequence in text for sequence in bad_sequences), str(path)
            if path.suffix == ".json":
                json.loads(text)
            checked_text += 1

class_count = 0
resource_count = 0
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if name.endswith(".class"):
            assert struct.unpack(">H", archive.read(name)[6:8])[0] == 65, name
            assert not any(part in name for part in ("GameTests", "SmokeTest", "examplemod", "Probe")), name
            class_count += 1
        elif name.endswith((".json", ".toml")):
            data = archive.read(name)
            text = data.decode("utf-8", errors="strict")
            assert not any(sequence in text for sequence in bad_sequences), name
            built = ROOT / "build/resources/main" / name
            assert built.read_bytes() == data, name
            if name.endswith(".json"):
                json.loads(text)
                source = ROOT / "src/main/resources" / name
                if source.exists():
                    assert source.read_bytes() == data, name
            resource_count += 1
    metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
    assert f'version="{version}"' in metadata or f'version = "{version}"' in metadata
    languages = [json.loads(archive.read(f"assets/shiinacasino/lang/{locale}.json").decode("utf-8")) for locale in ("de_de", "en_us")]
    assert languages[0].keys() == languages[1].keys()
    assert languages[0]["block.shiinacasino.fortune_wheel"] == "Glücksrad"
    assert languages[0]["casino.roulette.low"] == "1-18"
    assert languages[0]["casino.roulette.high"] == "19-36"

suites = [ET.parse(path).getroot() for path in (ROOT / "build/test-results/test").glob("TEST-*.xml")]
tests = sum(int(suite.attrib["tests"]) for suite in suites)
failures = sum(int(suite.attrib["failures"]) + int(suite.attrib["errors"]) for suite in suites)
assert tests > 0 and failures == 0
report = {"jar": str(jar), "version": version, "bytes": jar.stat().st_size,
          "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "java_class_version": 65,
          "classes": class_count, "built_resources_verified": resource_count, "utf8_source_files": checked_text,
          "language_keys": len(languages[0]), "junit_tests": tests, "failures": failures}
(ROOT / "build" / f"release-verification-{version}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False, indent=2))
