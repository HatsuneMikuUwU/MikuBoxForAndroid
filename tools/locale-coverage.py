#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Locale resource gate for MikuBox.

Audits every locale directory (res/values-*) of both resource roots — the app
module and the vendored mikuray-ui layer — against their own base (res/values):

  * key coverage: every translatable base name exists in the locale (an entry
    marked translatable="false" in the locale counts as a deliberate skip);
  * plurals quantities: a locale copy of a <plurals> covers every quantity the
    base defines (a locale that only ships "other" silently falls back for
    English counts);
  * format placeholders: a locale <string> uses exactly the base's %n$x
    placeholder set (a missing one drops data at runtime; an extra one breaks
    the call site);
  * bare config-key titles in the default locale (settings titles like
    "sniffer.enable" are leaks, not translations).

--apply <file> with --locale <dir> backfills missing keys of ONE mikuray-ui
locale from a JSON file {key: {"strings": ...} | {"plurals": {q: text}}},
appended before </resources> of the matching file (falling back to
strings.xml). --dump <file> writes missing keys + base values for one locale.

--ci exits 1 when any root reports any issue (for CI gates).
"""
import argparse
import glob
import io
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
RES_ROOTS = [
    ("mikuray-ui", os.path.join(ROOT, "mikuray-ui", "src", "main", "res")),
    ("app", os.path.join(ROOT, "app", "src", "main", "res")),
]
# The mikuray-ui root is the primary translation backlog target for --apply/--dump.
PRIMARY_ROOT = RES_ROOTS[0][1]

ENTRY_TAGS = ("string", "plurals", "string-array", "integer-array")
NON_LOCALE_DIRS = ("values-night",)
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")


def _visible_dirs(res_dir):
    return sorted(
        os.path.basename(d)
        for d in glob.glob(os.path.join(res_dir, "values-*"))
        if os.path.basename(d) not in NON_LOCALE_DIRS
    )


def collect_entries(directory):
    """{resource_name: element} across one res directory's xml files."""
    entries = {}
    for path in sorted(glob.glob(os.path.join(directory, "*.xml"))):
        if os.path.basename(path) == "attrs.xml":
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            print("PARSE ERROR %s: %s" % (path, e), file=sys.stderr)
            continue
        for element in root:
            name = element.get("name")
            if name and element.tag in ENTRY_TAGS:
                entries[name] = element
    return entries


def collect(directory, skip_untranslatable=False):
    """Compatibility shape used by --apply: {name: (kind, path)}."""
    keys = {}
    for path in sorted(glob.glob(os.path.join(directory, "*.xml"))):
        if os.path.basename(path) == "attrs.xml":
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for element in root:
            name = element.get("name")
            if not name:
                continue
            if skip_untranslatable and element.get("translatable") == "false":
                continue
            if element.tag in ENTRY_TAGS:
                kind = "plurals" if element.tag == "plurals" else "string"
                if element.tag in ("string-array", "integer-array"):
                    kind = "array"
                keys[name] = (kind, path)
    return keys


def plural_quantity_gaps(base_entries, locale_entries):
    """{name: [missing quantities]} for locale copies of base <plurals>."""
    gaps = {}
    for name, element in locale_entries.items():
        base = base_entries.get(name)
        if base is None or base.tag != "plurals" or element.tag != "plurals":
            continue
        if element.get("translatable") == "false":
            continue
        base_q = {item.get("quantity") for item in base}
        have_q = {item.get("quantity") for item in element}
        missing = sorted(base_q - have_q)
        if missing:
            gaps[name] = missing
    return gaps


def placeholder_gaps(base_entries, locale_entries):
    """{name: (missing, extra)} %n$x tokens for locale copies of base <string>."""
    gaps = {}
    for name, element in locale_entries.items():
        base = base_entries.get(name)
        if base is None or base.tag != "string" or element.tag != "string":
            continue
        if element.get("translatable") == "false":
            continue
        base_ph = set(PLACEHOLDER_RE.findall(base.text or ""))
        have_ph = set(PLACEHOLDER_RE.findall(element.text or ""))
        if not base_ph and not have_ph:
            continue
        missing = sorted(base_ph - have_ph)
        extra = sorted(have_ph - base_ph)
        if missing or extra:
            gaps[name] = (missing, extra)
    return gaps


# F33/N4 regression gate: a settings title whose whole value is the bare
# config key it edits ("sniffer.enable", "keep-alive-interval") is a leak, not
# a translation. Titles must be human-readable; the key stays visible via the
# "Label · key" pattern. Plain single words ("address", "port") are legitimate
# form labels, so the heuristic only flags key-shaped compounds: dotted or
# hyphenated all-lowercase tokens.
BARE_KEY_RE = re.compile(r"^[a-z0-9][a-z0-9.\-]*$")
BARE_KEY_ALLOWED = {
    # Input-format hints, not config keys: the field wants a "min-max" range.
    "title_pref_fragment_interval_tip",
    "title_pref_fragment_length_tip",
}


def bare_key_offenders(res_dir):
    """Default-locale <string> values that read as bare config keys."""
    offenders = {}
    for path in sorted(glob.glob(os.path.join(res_dir, "values", "*.xml"))):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for element in root:
            if element.tag != "string":
                continue
            name = element.get("name")
            if not name or element.get("translatable") == "false":
                continue
            value = (element.text or "").strip()
            if value and BARE_KEY_RE.match(value) and ("." in value or "-" in value) and name not in BARE_KEY_ALLOWED:
                offenders[name] = value
    return offenders


def base_value(name, kind):
    """Best-effort base text for a key, for translation reference dumps."""
    for path in sorted(glob.glob(os.path.join(PRIMARY_ROOT, "values", "*.xml"))):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for element in root:
            if element.get("name") != name:
                continue
            if element.tag == "plurals":
                return {item.get("quantity"): (item.text or "") for item in element}
            if element.tag in ("string-array", "integer-array"):
                return [(item.text or "") for item in element]
            return element.text or ""
    return None


def audit_root(label, res_dir, check_bare_keys):
    """Returns (missing_all, quantity_all, placeholder_all, offenders)."""
    base_entries = collect_entries(os.path.join(res_dir, "values"))
    base_names = {
        name for name, element in base_entries.items()
        if element.get("translatable") != "false"
    }
    missing_all, quantity_all, placeholder_all = {}, {}, {}

    for locale in _visible_dirs(res_dir):
        locale_entries = collect_entries(os.path.join(res_dir, locale))
        # A locale entry marked translatable="false" is a deliberate skip and
        # counts as present; only truly absent names are missing.
        missing = sorted(base_names - set(locale_entries))
        missing_all[locale] = missing
        quantity_all[locale] = plural_quantity_gaps(base_entries, locale_entries)
        placeholder_all[locale] = placeholder_gaps(base_entries, locale_entries)

    offenders = bare_key_offenders(res_dir) if check_bare_keys else {}

    print("[%s] base=%d keys, locales=%s" % (label, len(base_names), ", ".join(missing_all) or "-"))
    for locale, missing in missing_all.items():
        q = quantity_all[locale]
        p = placeholder_all[locale]
        print("  %s: %d missing, %d plurals-quantity, %d placeholder" % (locale, len(missing), len(q), len(p)))
        for name in missing:
            print("    missing: %s" % name)
        for name, quantities in sorted(q.items()):
            print("    plurals: %s misses %s" % (name, ", ".join(quantities)))
        for name, (miss, extra) in sorted(p.items()):
            print("    placeholder: %s missing=%s extra=%s" % (name, miss, extra))
    if offenders:
        print("  bare config-key titles in the default locale:")
        for name, value in sorted(offenders.items()):
            print("    %s = %s" % (name, value))
    return missing_all, quantity_all, placeholder_all, offenders


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", metavar="JSON", help="backfill missing keys of --locale (mikuray-ui) from this JSON file")
    parser.add_argument("--locale", metavar="DIR", help="locale directory name, e.g. values-in")
    parser.add_argument("--dump", metavar="FILE", help="write missing keys + base values (JSON) for --locale")
    parser.add_argument("--ci", action="store_true", help="exit 1 on any missing key or locale defect")
    args = parser.parse_args()

    if args.apply:
        if not args.locale:
            parser.error("--apply needs --locale")
        res_dir = PRIMARY_ROOT
        with io.open(args.apply, "r", encoding="utf-8") as f:
            translations = json.load(f)
        directory = os.path.join(res_dir, args.locale)
        locale_keys = collect(directory)
        added = 0
        for name, value in translations.items():
            if name in locale_keys:
                continue
            kind = collect(os.path.join(res_dir, "values"), skip_untranslatable=True).get(name, ("string", None))[0]
            if kind == "plurals" and isinstance(value, dict):
                items = "".join(
                    '        <item quantity="%s">%s</item>\n' % (q, esc(t))
                    for q, t in sorted(value.items())
                )
                entry = '    <plurals name="%s">\n%s    </plurals>\n' % (name, items)
            elif kind == "array" and isinstance(value, list):
                items = "".join("        <item>%s</item>\n" % esc(t) for t in value)
                entry = '    <string-array name="%s" translatable="false">\n%s    </string-array>\n' % (name, items)
            else:
                text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
                entry = '    <string name="%s">%s</string>\n' % (name, esc(text))
            target = locale_keys.get(name, (None, os.path.join(directory, "strings.xml")))[1]
            with io.open(target, "r", encoding="utf-8") as f:
                content = f.read()
            idx = content.rfind("</resources>")
            content = content[:idx] + entry + content[idx:]
            with io.open(target, "w", encoding="utf-8", newline="\n") as f:
                f.write(content)
            locale_keys[name] = (kind, target)
            added += 1
        print("added %d keys to %s" % (added, args.locale))
        return 0

    totals = {"missing": 0, "plurals": 0, "placeholder": 0, "bare": 0}
    primary_missing = {}
    for label, res_dir in RES_ROOTS:
        missing_all, quantity_all, placeholder_all, offenders = audit_root(
            label, res_dir, check_bare_keys=True
        )
        if res_dir == PRIMARY_ROOT:
            primary_missing = missing_all
        totals["missing"] += sum(len(v) for v in missing_all.values())
        totals["plurals"] += sum(len(v) for v in quantity_all.values())
        totals["placeholder"] += sum(len(v) for v in placeholder_all.values())
        totals["bare"] += len(offenders)

    if args.dump:
        if not args.locale:
            parser.error("--dump needs --locale")
        base = collect(os.path.join(PRIMARY_ROOT, "values"), skip_untranslatable=True)
        payload = {}
        for name in primary_missing.get(args.locale, []):
            payload[name] = {"kind": base[name][0], "base": base_value(name, base[name][0])}
        with io.open(args.dump, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=1)
        print("dumped %d entries to %s" % (len(payload), args.dump))

    if args.ci:
        problems = totals["missing"] + totals["plurals"] + totals["placeholder"] + totals["bare"]
        if problems:
            print(
                "CI FAIL: %d missing keys, %d plurals-quantity gaps, %d placeholder mismatches, %d bare config-key titles"
                % (totals["missing"], totals["plurals"], totals["placeholder"], totals["bare"]),
                file=sys.stderr,
            )
            return 1
        print("locale coverage OK")
    return 0


_APT_APOS = re.compile(r"(?<!\\)'")


def esc(text):
    # XML-escape entities (ElementTree hands us decoded text), then escape
    # apostrophes for AAPT — but only ones not already escaped: base values
    # arrive pre-escaped (Emily\'s Candy) and double-escaping produces an
    # unescaped apostrophe error in aapt2 compile.
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return _APT_APOS.sub(lambda m: "\\" + m.group(0), text)


if __name__ == "__main__":
    sys.exit(main())
