#!/usr/bin/env python3
"""Static validation for the AetherX 3.6 follow-up source patch.

This is not a substitute for Android Gradle compilation or device testing.
It checks that the expected source wiring and read-only diagnostic script stay intact.
"""
from pathlib import Path
import re
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/aether/x"
errors = []
checks = []

def check(name, ok, details=""):
    checks.append((name, bool(ok), details))
    if not ok:
        errors.append(name + (f": {details}" if details else ""))

# Resources must remain well-formed XML.
xml_files = list((ROOT / "src/main").rglob("*.xml"))
xml_failures = []
for path in xml_files:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        xml_failures.append(f"{path.relative_to(ROOT)}: {exc}")
check("XML resources parse", not xml_failures, "; ".join(xml_failures[:5]))

# Verify true backdrop blur is wired to the screen source and bottom capsule.
main_screen = (JAVA / "ui/main/MainScreen.kt").read_text()
nav = (JAVA / "ui/main/AetherBottomNavBar.kt").read_text()
theme = (JAVA / "ui/theme/Theme.kt").read_text()
popup = (JAVA / "ui/components/PopupDialog.kt").read_text()
activity = (ROOT / "src/main/java/com/aether/x/MainActivity.kt").read_text()
settings = (JAVA / "ui/settings/SettingsScreen.kt").read_text()
check("Haze dependency declared", 'dev.chrisbanes.haze:haze:1.6.1' in (ROOT / "build.gradle.kts").read_text())
check("screen backdrop source present", ".hazeSource(hazeState)" in main_screen)
check("navbar captures backdrop and blurs it", ".hazeSource(LocalAetherHazeState.current, zIndex = 1f)" in nav and ".hazeEffect(state = LocalAetherHazeState.current)" in nav and "blurRadius = (glassIntensity * 30f).dp" in nav)
check("global blur intensity reaches dialogs", "LocalHazeStyle provides HazeStyle(" in theme and "blurRadius = (normalizedGlassIntensity * 30f).dp" in theme)
check("shared blurred dialog used by shared popup components", popup.count("HazeDialog(") >= 2 and "hazeState = LocalAetherHazeState.current" in popup)
check("root dialog capture state exists", "LocalAetherHazeState provides rootHazeState" in activity and ".hazeSource(rootHazeState, zIndex = -1f)" in activity)
check("reboot confirmation uses shared blurred popup", "PopupDialog(" in settings and "Konfirmasi tindakan sistem" in settings and "AlertDialog(" not in settings)
check("slider value remains connected to preferences", "onValueChange = viewModel::setGlassIntensity" in settings and "Blur latar" in settings)

# Extract Kotlin triple-quoted shell command, resolve escaped dollar expressions,
# and validate its syntax with bash. This validates parsing only, not Android sysfs availability.
probe_path = JAVA / "core/kernel/KernelDiagnostics.kt"
probe = probe_path.read_text()
match = re.search(r'val readOnlyCommand: String = """(.*?)"""\.trimIndent\(\)', probe, re.S)
check("kernel diagnostic command found", match is not None)
if match:
    shell = textwrap.dedent(match.group(1)).strip().replace("${'$'}", "$")
    proc = subprocess.run(["bash", "-n"], input=shell, text=True, capture_output=True)
    check("kernel diagnostic shell syntax", proc.returncode == 0, proc.stderr.strip())
    # Explicitly refuse accidental mutating commands in the read-only probe.
    mutation_patterns = [r'\becho\s+[^\n]*>\s*/sys/', r'\b(?:chmod|chown|rm|setprop)\b', r'\btee\s+/sys/']
    found = [pat for pat in mutation_patterns if re.search(pat, shell)]
    check("kernel probe is read-only", not found, ", ".join(found))

# There should be no remaining direct Compose Dialog calls that would bypass the shared blur path.
all_kotlin = list(JAVA.rglob("*.kt"))
direct_dialogs = []
for path in all_kotlin:
    source = path.read_text(errors="ignore")
    if re.search(r'import androidx\.compose\.ui\.window\.Dialog\s*$', source, re.M) or re.search(r'(?<!Haze)\bDialog\s*\(', source):
        # A type import or text doc comment doesn't count as a call; report likely remaining use.
        if re.search(r'import androidx\.compose\.ui\.window\.Dialog\s*$', source, re.M) or re.search(r'(?m)^\s*Dialog\s*\(', source):
            direct_dialogs.append(str(path.relative_to(ROOT)))
check("direct dialogs migrated to blur path", not direct_dialogs, ", ".join(direct_dialogs))

for name, ok, details in checks:
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f" — {details}" if details else ""))
print(f"\n{sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
if errors:
    sys.exit(1)
