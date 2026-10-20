#!/usr/bin/env python3
"""Check the app's opaque semantic colors; device rendering still needs inspection."""

from pathlib import Path
import re


THEME = Path(__file__).resolve().parents[1] / "app/src/main/java/com/vic2ray/ui/Theme.kt"


def rgb(value):
    value = re.search(r"0x([\dA-F]{8})", value)[1]
    if value[:2] != "FF":
        raise ValueError("Contrast tokens must be opaque")
    return tuple(int(value[index:index + 2], 16) / 255 for index in (2, 4, 6))


def luminance(color):
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
              for value in color]
    return sum(channel * weight for channel, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(first, second):
    dark, light = sorted((luminance(first), luminance(second)))
    return (light + 0.05) / (dark + 0.05)


def main():
    source = THEME.read_text()
    palette = {key: rgb(value) for key, value in re.findall(
        r"(?:internal val )?(\w+)\s*=\s*(Color\(0x[\dA-F]{8}\))", source)}
    roles = {}
    for role, value in re.findall(r"^    (\w+) = (Color\(0x[\dA-F]+\)|\w+)", source, re.M):
        if value in palette:
            roles[role] = palette[value]
        elif value.startswith("Color("):
            roles[role] = rgb(value)

    checks = []
    for role in ("primary", "primaryContainer", "secondary", "secondaryContainer", "tertiary",
                 "tertiaryContainer", "background", "surface", "surfaceVariant", "error", "errorContainer"):
        foreground = "on" + role[0].upper() + role[1:]
        checks.append((foreground + " / " + role, contrast(roles[foreground], roles[role]), 4.5))
    checks.append(("inverseOnSurface / inverseSurface",
                   contrast(roles["inverseOnSurface"], roles["inverseSurface"]), 4.5))
    for foreground in ("MainText", "Muted", "DisabledText", "Accent", "Mint", "Warning"):
        for background in ("Navy", "Panel", "PanelLight", "AccentPanel", "ConnectedPanel"):
            checks.append((foreground + " / " + background,
                           contrast(palette[foreground], palette[background]), 4.5))
    for background in ("Navy", "Panel", "PanelLight"):
        checks.append(("Outline / " + background, contrast(palette["Outline"], palette[background]), 3.0))

    failures = [f"{name}: {ratio:.2f}:1 (requires {minimum}:1)"
                for name, ratio, minimum in checks if ratio < minimum]
    if failures:
        raise SystemExit("Theme contrast failed:\n" + "\n".join(failures))
    print(f"Theme contrast passed: {len(checks)} color pairs, text >= 4.5:1, controls >= 3:1.")


if __name__ == "__main__":
    main()
