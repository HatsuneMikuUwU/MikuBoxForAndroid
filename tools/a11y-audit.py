#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Standalone accessibility audit for the MikuBox Android app.

Drives the app on a device/emulator over adb, captures a uiautomator dump for
each key screen, and scores two questions this project tracks:

  * UNLABELED-CLICKABLE (subtree) — clickable nodes whose node AND whole
    subtree expose no text or content-desc. TalkBack merges descendant text
    into a clickable container's announcement, so a card whose children carry
    the title is fine; a bare icon button is not.
  * UNADDRESSABLE-CLICKABLE — clickable nodes with no resource-id AND no label
    anywhere in their subtree: reachable only by blind screen coordinates.

Strict node-level counts (own-label only / id-less regardless of label) are
reported as information in the JSON alongside the scored ones.

A JSON report is written to --out. The script exits 0 only when every
*required* screen passes (by default: the home screen and the settings
centre). Other screens are reported for information only.

Usage:
  python tools/a11y-audit.py --adb adb --serial emulator-5554 --out report.json

Requires: a running emulator/device with the debug app installed, USB/window
debugging enabled. No app code is exercised beyond UI navigation.
"""
import argparse
import datetime
import io
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEFAULT_PACKAGE = "com.mikubox.mihomo"
DEFAULT_MAIN_ACTIVITY = "com.miku.ray.ui.main.MainActivity"
DEFAULT_SETTINGS_ACTIVITY = "com.miku.ray.ui.preference.activity.SettingsActivity"

# The settings centre is not exported, so try it directly first (works when
# the platform allows it) and fall back to UI navigation from the home screen.
SETTINGS_NAV_BY_UI = ["com.mikubox.mihomo:id/btn_home", "com.mikubox.mihomo:id/menu_settings"]

ADB_TIMEOUT = 30
DUMP_ATTEMPTS = 4
SETTLE_SECONDS = 2.5

BOUNDS_RE = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")


class AdbError(RuntimeError):
    pass


class Adb:
    def __init__(self, binary, serial):
        self.binary = binary
        self.serial = serial

    def run(self, *args, timeout=ADB_TIMEOUT, check=True):
        command = [self.binary, "-s", self.serial] + list(args)
        try:
            done = subprocess.run(
                command, capture_output=True, text=True, timeout=timeout,
                encoding="utf-8", errors="replace",
            )
        except FileNotFoundError:
            raise AdbError("adb binary not found: %s" % self.binary)
        except subprocess.TimeoutExpired:
            raise AdbError("adb command timed out: %s" % " ".join(args))
        if check and done.returncode != 0:
            raise AdbError(
                "adb %s failed (%d): %s" % (" ".join(args), done.returncode, done.stderr.strip())
            )
        return done.stdout

    def shell(self, *args, timeout=ADB_TIMEOUT, check=True):
        return self.run("shell", *args, timeout=timeout, check=check)

    def wait_for_device(self):
        self.run("wait-for-device", timeout=60)

    def current_activity(self):
        out = self.shell("dumpsys", "window", "windows", check=False)
        match = re.search(r"mCurrentFocus=.*? ([\w.]+/[\w.$]+)", out)
        if not match:
            out = self.shell("dumpsys", "activity", "top", check=False)
            match = re.search(r"ACTIVITY ([\w.]+/[\w.$]+)", out)
        return match.group(1) if match else ""

    def dump_ui(self):
        """Capture one uiautomator dump and return its XML text."""
        self.shell("uiautomator", "dump", "/sdcard/a11y_audit_dump.xml",
                   timeout=45, check=False)
        for _ in range(DUMP_ATTEMPTS):
            out = self.shell("cat", "/sdcard/a11y_audit_dump.xml", check=False)
            if out.lstrip().startswith("<?xml"):
                return out
            time.sleep(1.5)
            self.shell("uiautomator", "dump", "/sdcard/a11y_audit_dump.xml",
                       timeout=45, check=False)
        raise AdbError("uiautomator dump never produced XML (is the screen idle?)")

    def launch(self, package, activity=None):
        if activity:
            self.shell("am", "start", "-n", "%s/%s" % (package, activity), check=False)
        else:
            self.run("shell", "monkey", "-p", package,
                     "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(SETTLE_SECONDS)

    def tap_node(self, resource_id):
        """Tap the centre of the first node with this resource-id.

        Returns True when the node was found and tapped."""
        xml = self.dump_ui()
        for node in parse_xml(xml):
            if node["resource_id"] == resource_id and node["clickable"]:
                x, y = node["center"]
                if x is None:
                    continue
                self.shell("input", "tap", str(x), str(y))
                time.sleep(SETTLE_SECONDS)
                return True
        return False


def parse_xml(text):
    """Flatten a uiautomator dump into dicts of the attributes that matter.

    Each entry carries ``subtree_label``: whether the node or ANY descendant
    exposes text or a content-desc. TalkBack merges descendant text into a
    clickable container's announcement, so subtree label is what makes a
    clickable node meaningful to a screen reader.
    """
    nodes = []

    def walk(element):
        attrib = element.attrib
        if not attrib:
            # Attribute-less wrappers (e.g. <hierarchy>) still have children.
            for child in element:
                walk(child)
            return None
        label = bool((attrib.get("text") or "").strip()
                     or (attrib.get("content-desc") or "").strip())
        subtree = label
        for child in element:
            child_node = walk(child)
            if child_node is not None:
                subtree = subtree or child_node["subtree_label"]
        bounds_match = BOUNDS_RE.match(attrib.get("bounds", ""))
        center = None
        if bounds_match:
            x1, y1, x2, y2 = (int(g) for g in bounds_match.groups())
            if x2 > x1 and y2 > y1:
                center = ((x1 + x2) // 2, (y1 + y2) // 2)
        node = {
            "class": attrib.get("class", ""),
            "resource_id": attrib.get("resource-id", ""),
            "text": attrib.get("text", ""),
            "content_desc": attrib.get("content-desc", ""),
            "clickable": attrib.get("clickable") == "true",
            "bounds": attrib.get("bounds", ""),
            "center": center,
            "own_label": label,
            "subtree_label": subtree,
        }
        nodes.append(node)
        return node

    root = ET.fromstring(io_bytes(text))
    walk(root)
    return nodes


def io_bytes(text):
    """uiautomator output can carry a BOM; hand ElementTree clean bytes."""
    return text.encode("utf-8").lstrip(b"\xef\xbb\xbf")


def audit_screen(name, required, xml):
    """Score one screen.

    Strict node-level counts (``empty_clickable``, ``clickable_without_id``)
    are reported for information. The gate uses what a screen reader and
    automation actually need:

    * ``unlabeled_clickable_subtree`` — clickable nodes where the node AND its
      whole subtree carry no text/content-desc: pure unlabeled buttons;
    * ``unaddressable_clickable`` — clickable nodes with no resource-id AND no
      label anywhere in their subtree: reachable only by screen coordinates.
    """
    nodes = parse_xml(xml)
    empty_clickable = []
    clickable_no_id = []
    unlabeled_subtree = []
    unaddressable = []
    for node in nodes:
        if not node["clickable"]:
            continue
        if not node["text"].strip() and not node["content_desc"].strip():
            empty_clickable.append(node)
        if not node["resource_id"].strip():
            clickable_no_id.append(node)
        if not node["subtree_label"]:
            unlabeled_subtree.append(node)
        if not node["resource_id"].strip() and not node["subtree_label"]:
            unaddressable.append(node)

    def offenders(items, limit=20):
        return [
            {
                "class": n["class"],
                "resource_id": n["resource_id"],
                "bounds": n["bounds"],
            }
            for n in items[:limit]
        ]

    result = {
        "name": name,
        "required": required,
        "clickable_nodes": sum(1 for n in nodes if n["clickable"]),
        # Strict, informational:
        "empty_clickable": len(empty_clickable),
        "clickable_without_id": len(clickable_no_id),
        # Scored (what a screen reader / automation can actually use):
        "unlabeled_clickable_subtree": len(unlabeled_subtree),
        "unaddressable_clickable": len(unaddressable),
        "empty_clickable_offenders": offenders(empty_clickable),
        "no_id_offenders": offenders(clickable_no_id),
        "unlabeled_subtree_offenders": offenders(unlabeled_subtree),
        "unaddressable_offenders": offenders(unaddressable),
    }
    result["pass"] = (result["unlabeled_clickable_subtree"] == 0
                      and result["unaddressable_clickable"] == 0)
    return result


def ensure_foreground(adb, package, activity):
    """Bring the expected screen up; return the focused activity string."""
    focused = adb.current_activity()
    if activity and activity.split("/")[-1] not in focused:
        adb.launch(package, activity)
        focused = adb.current_activity()
    return focused


def main():
    parser = argparse.ArgumentParser(
        description="Audit app screens for unlabeled/untargetable clickable controls.")
    parser.add_argument("--adb", required=True, help="path to the adb binary")
    parser.add_argument("--serial", required=True, help="device/emulator serial")
    parser.add_argument("--out", required=True, help="path of the JSON report to write")
    parser.add_argument("--package", default=DEFAULT_PACKAGE,
                        help="application id of the installed debug build")
    parser.add_argument("--main-activity", default=DEFAULT_MAIN_ACTIVITY,
                        help="home activity, used when the launcher intent is ambiguous")
    parser.add_argument("--settings-activity", default=DEFAULT_SETTINGS_ACTIVITY,
                        help="settings centre activity, tried directly before UI navigation")
    parser.add_argument("--skip-settings", action="store_true",
                        help="audit the home screen only (report then fails: settings is required)")
    args = parser.parse_args()

    adb = Adb(args.adb, args.serial)
    report = {
        "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "serial": args.serial,
        "package": args.package,
        "screens": [],
        "pass": False,
    }

    try:
        adb.wait_for_device()
        # A fixed launch point keeps the navigation deterministic between screens.
        adb.launch(args.package, args.main_activity)
        # A fresh install (pm clear) lands on the first-run guide; get past it
        # so the audited screen is the home screen. No-op when already past.
        adb.tap_node("%s:id/page_1_skip" % args.package)

        # --- required screen: home ------------------------------------------
        home_xml = adb.dump_ui()
        report["screens"].append(audit_screen("main", True, home_xml))

        # --- required screen: settings centre --------------------------------
        if args.skip_settings:
            report["screens"].append({
                "name": "settings", "required": True, "skipped": True, "pass": False,
                "note": "--skip-settings given; a required screen cannot pass when skipped",
            })
        else:
            settings_activity = args.settings_activity
            adb.launch(args.package, settings_activity)
            focused = adb.current_activity()
            navigated_via_ui = False
            if "SettingsActivity" not in focused:
                # Direct start refused (not exported): navigate like a user.
                adb.launch(args.package, args.main_activity)
                for target in SETTINGS_NAV_BY_UI:
                    if not adb.tap_node(target):
                        break
                    focused = adb.current_activity()
                    if "SettingsActivity" in focused:
                        break
                else:
                    navigated_via_ui = True
                navigated_via_ui = "SettingsActivity" in adb.current_activity()
            settings_xml = adb.dump_ui()
            entry = audit_screen("settings", True, settings_xml)
            entry["navigation"] = "direct" if "SettingsActivity" in focused else (
                "ui" if navigated_via_ui else "unverified")
            report["screens"].append(entry)
    except AdbError as error:
        report["error"] = str(error)

    report["pass"] = (not report.get("error")
                      and report["screens"]
                      and all(screen.get("pass") for screen in report["screens"]
                              if screen.get("required")))
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    for screen in report["screens"]:
        print("%-10s required=%s pass=%s unlabeled_subtree=%d unaddressable=%d "
              "(strict: empty_clickable=%d clickable_without_id=%d)" % (
                  screen["name"], screen.get("required"), screen.get("pass"),
                  screen.get("unlabeled_clickable_subtree", -1),
                  screen.get("unaddressable_clickable", -1),
                  screen.get("empty_clickable", -1),
                  screen.get("clickable_without_id", -1)))
    if report.get("error"):
        print("error: %s" % report["error"], file=sys.stderr)
    print("overall pass: %s" % report["pass"])
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
