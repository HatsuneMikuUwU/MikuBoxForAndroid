import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ports the UwU branch's Material 3 palettes into this project:
 * - res/values/colors_themes.xml        (light roles)
 * - res/values-night/colors_themes.xml  (night roles, same names)
 * - res/values/themes_families.xml      (one style per family binding the roles)
 *
 * Run: java GenerateThemes.java <uwu-res-dir> <our-res-dir>
 */
public final class GenerateThemes {

    /** Families kept from the source palette list, in swatch order. */
    private static final List<String> FAMILIES = List.of(
            "teal", "cyan", "light_blue", "blue", "indigo", "deep_purple", "purple",
            "magenta", "pink", "red", "deep_orange", "orange", "amber", "yellow",
            "light_green", "green", "blue_grey");

    private static final String[] DISPLAY_NAMES = {
            "Teal", "Cyan", "Light Blue", "Blue", "Indigo", "Deep Purple", "Purple",
            "Magenta", "Pink", "Red", "Deep Orange", "Orange", "Amber", "Yellow",
            "Light Green", "Green", "Blue Grey"};

    /** Roles carried over, in the order the theme style lists them. */
    private static final Map<String, String> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put("primary", "colorPrimary");
        ROLES.put("onPrimary", "colorOnPrimary");
        ROLES.put("primaryContainer", "colorPrimaryContainer");
        ROLES.put("onPrimaryContainer", "colorOnPrimaryContainer");
        ROLES.put("secondary", "colorSecondary");
        ROLES.put("onSecondary", "colorOnSecondary");
        ROLES.put("secondaryContainer", "colorSecondaryContainer");
        ROLES.put("onSecondaryContainer", "colorOnSecondaryContainer");
        ROLES.put("tertiary", "colorTertiary");
        ROLES.put("onTertiary", "colorOnTertiary");
        ROLES.put("tertiaryContainer", "colorTertiaryContainer");
        ROLES.put("onTertiaryContainer", "colorOnTertiaryContainer");
        ROLES.put("error", "colorError");
        ROLES.put("onError", "colorOnError");
        ROLES.put("errorContainer", "colorErrorContainer");
        ROLES.put("onErrorContainer", "colorOnErrorContainer");
        ROLES.put("background", "android:colorBackground");
        ROLES.put("onBackground", "colorOnBackground");
        ROLES.put("surface", "colorSurface");
        ROLES.put("onSurface", "colorOnSurface");
        ROLES.put("surfaceVariant", "colorSurfaceVariant");
        ROLES.put("onSurfaceVariant", "colorOnSurfaceVariant");
        ROLES.put("outline", "colorOutline");
        ROLES.put("outlineVariant", "colorOutlineVariant");
        ROLES.put("inverseSurface", "colorSurfaceInverse");
        ROLES.put("inverseOnSurface", "colorOnSurfaceInverse");
        ROLES.put("surfaceDim", "colorSurfaceDim");
        ROLES.put("surfaceBright", "colorSurfaceBright");
        ROLES.put("surfaceContainerLowest", "colorSurfaceContainerLowest");
        ROLES.put("surfaceContainerLow", "colorSurfaceContainerLow");
        ROLES.put("surfaceContainer", "colorSurfaceContainer");
        ROLES.put("surfaceContainerHigh", "colorSurfaceContainerHigh");
        ROLES.put("surfaceContainerHighest", "colorSurfaceContainerHighest");
        ROLES.put("primaryFixed", "colorPrimaryFixed");
        ROLES.put("onPrimaryFixed", "colorOnPrimaryFixed");
        ROLES.put("primaryFixedDim", "colorPrimaryFixedDim");
        ROLES.put("onPrimaryFixedVariant", "colorOnPrimaryFixedVariant");
        ROLES.put("secondaryFixed", "colorSecondaryFixed");
        ROLES.put("onSecondaryFixed", "colorOnSecondaryFixed");
        ROLES.put("secondaryFixedDim", "colorSecondaryFixedDim");
        ROLES.put("onSecondaryFixedVariant", "colorOnSecondaryFixedVariant");
        ROLES.put("tertiaryFixed", "colorTertiaryFixed");
        ROLES.put("onTertiaryFixed", "colorOnTertiaryFixed");
        ROLES.put("tertiaryFixedDim", "colorTertiaryFixedDim");
        ROLES.put("onTertiaryFixedVariant", "colorOnTertiaryFixedVariant");
    }

    public static void main(String[] args) throws IOException {
        Path uwuRes = Path.of(args[0]);
        Path ourRes = Path.of(args[1]);

        Map<String, String> light = parse(uwuRes.resolve("values/colors.xml"));
        Map<String, String> night = parse(uwuRes.resolve("values-night/colors.xml"));

        writeColors(ourRes.resolve("values/colors_themes.xml"), light, "light");
        writeColors(ourRes.resolve("values-night/colors_themes.xml"), night, "night");
        writeStyles(ourRes.resolve("values/themes_families.xml"));

        System.out.println("families=" + FAMILIES.size() + " roles=" + ROLES.size());
    }

    private static Map<String, String> parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("<color name=\"([a-zA-Z0-9_]+)\">(#[0-9a-fA-F]{6,8})</color>");
        Matcher matcher = pattern.matcher(text);
        Map<String, String> out = new LinkedHashMap<>();
        while (matcher.find()) {
            out.put(matcher.group(1), matcher.group(2));
        }
        return out;
    }

    private static void writeColors(Path file, Map<String, String> source, String label)
            throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<!--\n");
        xml.append("    Material 3 tonal palettes (").append(label).append(") ported from the\n");
        xml.append("    release branch so every colour role sits on the same tonal ramp.\n");
        xml.append("    Names are t_<family>_<role>; the night file redeclares the same names.\n");
        xml.append("    Generated file - edit the family list in the generator, not this file.\n");
        xml.append("-->\n<resources>\n");

        Set<String> missing = new LinkedHashSet<>();
        for (String family : FAMILIES) {
            xml.append("\n    <!-- ").append(family).append(" -->\n");
            for (String role : ROLES.keySet()) {
                String key = family + "_" + role;
                String value = source.get(key);
                if (value == null) {
                    missing.add(key);
                    continue;
                }
                xml.append("    <color name=\"t_")
                        .append(family)
                        .append('_')
                        .append(role)
                        .append("\">")
                        .append(value)
                        .append("</color>\n");
            }
        }
        xml.append("</resources>\n");
        Files.createDirectories(file.getParent());
        Files.writeString(file, xml.toString(), StandardCharsets.UTF_8);
        if (!missing.isEmpty()) {
            System.out.println("MISSING in " + label + ": " + missing);
        }
    }

    private static void writeStyles(Path file) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<!--\n");
        xml.append("    One theme per palette family. Each inherits the app theme so the\n");
        xml.append("    chrome, dialog overlays, shapes and window animations stay shared;\n");
        xml.append("    only the colour roles differ. Day/night resolve through the colour\n");
        xml.append("    resources themselves.\n");
        xml.append("-->\n<resources>\n");

        for (int i = 0; i < FAMILIES.size(); i++) {
            String family = FAMILIES.get(i);
            String styleName = "Theme.MikuBoxCla." + DISPLAY_NAMES[i].replace(" ", "");
            xml.append("\n    <style name=\"").append(styleName).append("\" parent=\"Theme.MikuBoxCla\">\n");
            for (Map.Entry<String, String> role : ROLES.entrySet()) {
                xml.append("        <item name=\"")
                        .append(role.getValue())
                        .append("\">@color/t_")
                        .append(family)
                        .append('_')
                        .append(role.getKey())
                        .append("</item>\n");
            }
            // colorBg / colorCard deliberately stay at the base theme's day/night
            // bindings (?colorSurfaceVariant light, ?colorSurface night); adding
            // them here would pin the page colour and break the night contrast.
            xml.append("    </style>\n");
        }
        xml.append("</resources>\n");
        Files.writeString(file, xml.toString(), StandardCharsets.UTF_8);
    }
}
