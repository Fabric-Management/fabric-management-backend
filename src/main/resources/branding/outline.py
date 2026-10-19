"""Private worker: local fonts only; never silently substitute a face or a glyph."""
import os
import re
import resource
import subprocess
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
WEIGHTS = {100: 0, 200: 40, 300: 50, 400: 80, 500: 100, 600: 180, 700: 200, 800: 205, 900: 210}


class MissingFont(Exception):
    pass


class UnsupportedText(Exception):
    pass


def charset_contains(charset, text):
    ranges = []
    for span in charset.split():
        parts = span.split("-")
        ranges.append((int(parts[0], 16), int(parts[-1], 16)))
    return all(c.isspace() or any(a <= ord(c) <= b for a, b in ranges) for c in text)


def require_font(style, text, font_match):
    # Require an explicit first-choice family; a fallback list must not change the logo.
    family = style.get("font-family", "").split(",")[0].strip().strip("\"'")
    if not re.fullmatch(r"[\w .()-]{1,128}", family):
        raise MissingFont()
    weight = style.get("font-weight", "400")
    weight = {"normal": "400", "bold": "700"}.get(weight, weight)
    if not weight.isdigit() or int(weight) not in WEIGHTS:
        raise UnsupportedText()
    slant = {"normal": 0, "italic": 100, "oblique": 110}.get(style.get("font-style", "normal"))
    if slant is None or style.get("font-stretch", "normal") != "normal":
        raise UnsupportedText()
    if style.get("font-variant", "normal") != "normal":
        raise UnsupportedText()
    if style.get("writing-mode", "horizontal-tb") not in ("horizontal-tb", "lr-tb"):
        raise UnsupportedText()
    # Literal family names cannot inject Fontconfig pattern properties.
    pattern = f"{family}:weight={WEIGHTS[int(weight)]}:slant={slant}"
    matched = subprocess.run([font_match, "--format", "%{family}\n%{weight}\n%{slant}\n%{charset}\n", pattern],
                             capture_output=True, text=True, check=True, timeout=2).stdout.splitlines()
    if len(matched) < 4 or family.casefold() not in [f.strip().casefold() for f in matched[0].split(",")]:
        raise MissingFont()
    # Do not allow synthetic bold/italic; the exact requested face must exist.
    if matched[1] != str(WEIGHTS[int(weight)]) or matched[2] != str(slant):
        raise MissingFont()
    if not charset_contains(matched[3], text):
        raise MissingFont()


def check_fonts(element, inherited, font_match):
    style = dict(inherited)
    style.update(element.attrib)  # preflight flattened permitted CSS to attributes
    local = element.tag.removeprefix(SVG)
    if local in ("text", "tspan"):
        if element.text and element.text.strip():
            require_font(style, element.text, font_match)
        for child in element:
            if child.tail and child.tail.strip():
                require_font(style, child.tail, font_match)
    for child in element:
        check_fonts(child, style, font_match)


def main():
    source, output, inkscape, font_match = sys.argv[1:]
    resource.setrlimit(resource.RLIMIT_CPU, (10, 10))
    resource.setrlimit(resource.RLIMIT_FSIZE, (1024 * 1024, 1024 * 1024))
    resource.setrlimit(resource.RLIMIT_NOFILE, (128, 128))
    # Linux hard memory ceiling; macOS uses a different virtual-memory model.
    if sys.platform.startswith("linux"):
        resource.setrlimit(resource.RLIMIT_AS, (1024**3, 1024**3))
    root = ET.parse(source).getroot()
    if sum(len(value) for value in root.itertext()) > 16384:
        raise UnsupportedText()
    check_fonts(root, {}, font_match)
    subprocess.run([inkscape, source, "--export-text-to-path", "--export-plain-svg", "--export-type=svg",
                             f"--export-filename={output}"], stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, timeout=10, check=True)
    if not os.path.isfile(output):
        raise UnsupportedText()
    if any(el.tag in (SVG + "text", SVG + "tspan") for el in ET.parse(output).getroot().iter()):
        raise UnsupportedText()


if __name__ == "__main__":
    try:
        main()
    except MissingFont:
        sys.exit(20)
    except UnsupportedText:
        sys.exit(22)
    except (OSError, ValueError, ET.ParseError, subprocess.SubprocessError):
        sys.exit(21)
