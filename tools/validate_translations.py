"""Validate runtime translation and subtitle keys against generated en_us."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src/generated/resources/assets/quantumflux/lang/en_us.json"
JAVA_ROOT = ROOT / "src/main/java"
SOUNDS = ROOT / "src/main/resources/assets/quantumflux/sounds.json"

VANILLA_GUI_LITERAL = re.compile(r'"(gui\.[a-z0-9_.-]+)"')
LOCAL_KEY_LITERAL = re.compile(
    r'"((?:block|item|itemGroup|message|overlay|screen|subtitles|tooltip)'
    r'\.quantumflux(?:\.[a-z0-9_.-]+)?)"'
)
ACTION_RESULT_ENTRY = re.compile(
    r'\b[A-Z][A-Z0-9_]*\(\s*(?:true|false)\s*,\s*"([^"]+)"\s*\)'
)
COLOR_CHOICE = re.compile(r'new ColorChoice\([^,]+,\s*"([a-z0-9_]+)"\)')

# These keys are supplied by Minecraft, not by Flux Pylons's language file. Keep
# the allowlist narrow so a typo or an unreviewed vanilla dependency still fails.
ALLOWED_VANILLA_GUI_KEYS = {"gui.back", "gui.cancel"}


def reject_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate translation key: {key}")
        result[key] = value
    return result


def simple_enum_values(path: Path, enum_name: str) -> set[str]:
    source = path.read_text(encoding="utf-8")
    match = re.search(rf"\benum\s+{re.escape(enum_name)}\s*\{{([^}}]+)\}}", source, re.DOTALL)
    if match is None:
        raise ValueError(f"could not find enum {enum_name} in {path.relative_to(ROOT)}")
    body = match.group(1).split(";", 1)[0]
    values = {
        token.group(1).lower()
        for part in body.split(",")
        if (token := re.search(r"\b([A-Z][A-Z0-9_]*)\b", part)) is not None
    }
    if not values:
        raise ValueError(f"enum {enum_name} has no parseable values")
    return values


def dynamic_families() -> dict[str, set[str]]:
    package = JAVA_ROOT / "com/zerotheabsolute/quantumflux"
    screen = package / "client/screen/GadgetScreen.java"
    colors = set(COLOR_CHOICE.findall(screen.read_text(encoding="utf-8")))
    if not colors:
        raise ValueError("GadgetScreen has no parseable color choices")
    receiver_states = simple_enum_values(package / "util/ConnectionStatus.java", "ConnectionStatus")
    upgrade_types = simple_enum_values(package / "item/UpgradeType.java", "UpgradeType")
    guide_keys = {
        'title',
        'help',
        'previous',
        'next',
        'replay',
        'play',
        'pause',
        'done',
        'timeline',
        'timeline.help',
        'time',
        'setup.title',
        'linking.title',
        'power.title',
        'status.title',
        'upgrades.title',
        'setup.step1',
        'setup.step2',
        'setup.step3',
        'setup.step4',
        'linking.step1',
        'linking.step2',
        'linking.step3',
        'linking.step4',
        'power.step1',
        'power.step2',
        'power.step3',
        'power.step4',
        'status.step1',
        'status.step2',
        'status.step3',
        'status.step4',
        'status.step5',
        'status.step6',
        'upgrades.step1',
        'upgrades.step2',
        'upgrades.step3',
        'upgrades.step4',
        'network',
        'example_network',
        'selected',
        'create',
        'source',
        'machine',
        'input',
        'wireless',
        'loaded_only',
        'linking_active',
        'linking_finished',
        'example',
        'upgrade_limit',
        'sneak_use',
        'use',
    }
    return {
        "screen.quantumflux.guide.": guide_keys,
        "message.quantumflux.pylon.result.": simple_enum_values(
            package / "network/data/QuantumFluxNetworkManager.java", "PylonMutationResult"
        ),
        "screen.quantumflux.priority.": simple_enum_values(
            package / "util/PriorityMode.java", "PriorityMode"
        ),
        "screen.quantumflux.beam.": simple_enum_values(
            package / "util/BeamStyle.java", "BeamStyle"
        ),
        "screen.quantumflux.receiver.": receiver_states | {state + "_hint" for state in receiver_states},
        "screen.quantumflux.upgrade_slot.": upgrade_types,
        "tooltip.quantumflux.upgrade.": upgrade_types,
        "screen.quantumflux.access.": {"private", "public", "password", "unknown"},
        "screen.quantumflux.color.": colors,
        "screen.quantumflux.tab.": simple_enum_values(screen, "Tab"),
        "screen.quantumflux.redstone.mode.": simple_enum_values(package / "util/RedstoneMode.java", "RedstoneMode"),
    }


def main() -> int:
    translations = json.loads(
        LANG.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicates
    )
    families = dynamic_families()
    required = {
        "block.quantumflux.quantum_pylon",
        "item.quantumflux.quantum_gadget",
    }
    unsupported_vanilla_keys: set[str] = set()

    for source in JAVA_ROOT.rglob("*.java"):
        # The provider creates the file being checked. Only runtime consumers belong
        # in this scan, otherwise generated definitions would validate themselves.
        if source.name == "QFLanguageProvider.java":
            continue
        text = source.read_text(encoding="utf-8")
        for key in VANILLA_GUI_LITERAL.findall(text):
            if key.startswith("gui.") and key not in ALLOWED_VANILLA_GUI_KEYS:
                unsupported_vanilla_keys.add(key)
        for key in LOCAL_KEY_LITERAL.findall(text):
            if key not in families:
                required.add(key)

    for prefix, suffixes in families.items():
        required.update(prefix + suffix for suffix in suffixes)

    action_result_source = (
        JAVA_ROOT
        / "com/zerotheabsolute/quantumflux/network/ActionResultS2CPayload.java"
    ).read_text(encoding="utf-8")
    action_result_keys = set(ACTION_RESULT_ENTRY.findall(action_result_source))
    if not action_result_keys:
        raise ValueError("ActionResultS2CPayload.Result has no parseable translation keys")
    invalid_result_keys = sorted(
        key for key in action_result_keys if not key.startswith("screen.quantumflux.result.")
    )
    if invalid_result_keys:
        raise ValueError(f"unexpected action-result translation keys: {invalid_result_keys}")
    required.update(action_result_keys)

    sound_manifest = json.loads(SOUNDS.read_text(encoding="utf-8"))
    required.update(
        event["subtitle"]
        for event in sound_manifest.values()
        if isinstance(event, dict) and isinstance(event.get("subtitle"), str)
    )

    if unsupported_vanilla_keys:
        print("Unapproved vanilla GUI translation keys:")
        for key in sorted(unsupported_vanilla_keys):
            print(f"  {key}")
        return 1

    missing = sorted(required.difference(translations))
    if missing:
        print("Missing generated English translations:")
        for key in missing:
            print(f"  {key}")
        return 1
    print(f"Validated {len(required)} runtime translation keys, 0 missing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
