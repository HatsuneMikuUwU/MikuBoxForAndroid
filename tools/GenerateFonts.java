import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the font + bold-text theme overlays and the picker arrays, one overlay
 * per bundled font, so the app can apply a typeface with theme.applyStyle()
 * without hand-maintaining a style per font.
 *
 * Run: java GenerateFonts.java <our-res-dir>
 */
public final class GenerateFonts {

    /** key -> (font resource, display label). Keys are persisted, keep them stable. */
    private static final Map<String, String[]> FONTS = new LinkedHashMap<>();

    static {
        FONTS.put("default", new String[]{"", "Default"});
        FONTS.put("uwu_font_title", new String[]{"uwu_font_title", "Miku Title"});
        FONTS.put("uwu_font_summary", new String[]{"uwu_font_summary", "Miku Summary"});
        FONTS.put("uwu_font_typography", new String[]{"uwu_font_typography", "Miku Typography"});
        FONTS.put("googlesansregular", new String[]{"googlesansregular", "Google Sans"});
        FONTS.put("robotoregular", new String[]{"robotoregular", "Roboto"});
        FONTS.put("poppinsregular", new String[]{"poppinsregular", "Poppins"});
        FONTS.put("sfprodisplay", new String[]{"sfprodisplay", "SF Pro Display"});
        FONTS.put("oneui", new String[]{"oneui", "One UI"});
        FONTS.put("rine", new String[]{"rine", "Rine"});
        FONTS.put("chococookyregular", new String[]{"chococookyregular", "Choco Cooky"});
        FONTS.put("simpleday", new String[]{"simpleday", "Simple Day"});
        FONTS.put("fucek", new String[]{"fucek", "Fucek"});
        FONTS.put("dancingscript", new String[]{"dancingscript", "Dancing Script"});
        FONTS.put("cream", new String[]{"cream", "Cream"});
        FONTS.put("emilyscandy", new String[]{"emilyscandy", "Emilys Candy"});
        FONTS.put("summerdream", new String[]{"summerdream", "Summer Dream"});
        FONTS.put("incosolata", new String[]{"incosolata", "Inconsolata"});
        FONTS.put("jetbrains_mono", new String[]{"jetbrains_mono", "JetBrains Mono"});
    }

    public static void main(String[] args) throws IOException {
        Path res = Path.of(args[0]);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<!--\n");
        xml.append("    Typography overlays: one per bundled font plus the bold variant. The app\n");
        xml.append("    applies the selected one with theme.applyStyle(overlay, true) before the\n");
        xml.append("    first view is inflated. Generated file.\n");
        xml.append("-->\n<resources>\n");

        for (Map.Entry<String, String[]> font : FONTS.entrySet()) {
            String key = font.getKey();
            String resource = font.getValue()[0];
            String suffix = camel(key);
            if (!resource.isEmpty()) {
                xml.append("\n    <style name=\"Miku.Font.").append(suffix).append("\" parent=\"\">\n");
                xml.append("        <item name=\"android:fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("        <item name=\"titleTextAppearance\">@style/Miku.Title.").append(suffix).append("</item>\n");
                xml.append("        <item name=\"collapsingToolbarLayoutStyle\">@style/Miku.CollapsingToolbar.").append(suffix).append("</item>\n");
                xml.append("    </style>\n");

                xml.append("\n    <style name=\"Miku.Title.").append(suffix).append("\" parent=\"@style/TextAppearance.Material3.TitleLarge\">\n");
                xml.append("        <item name=\"fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("        <item name=\"android:fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("    </style>\n");

                xml.append("\n    <style name=\"Miku.CollapsingToolbar.").append(suffix).append("\" parent=\"@style/Miku.CollapsingToolbar.Large\">\n");
                xml.append("        <item name=\"expandedTitleTextAppearance\">@style/Miku.CollapsingExpanded.").append(suffix).append("</item>\n");
                xml.append("        <item name=\"collapsedTitleTextAppearance\">@style/Miku.CollapsingCollapsed.").append(suffix).append("</item>\n");
                xml.append("    </style>\n");

                xml.append("\n    <style name=\"Miku.CollapsingExpanded.").append(suffix).append("\" parent=\"@style/TextAppearance.Material3.HeadlineLarge\">\n");
                xml.append("        <item name=\"fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("        <item name=\"android:fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("    </style>\n");

                xml.append("\n    <style name=\"Miku.CollapsingCollapsed.").append(suffix).append("\" parent=\"@style/TextAppearance.Material3.TitleLarge\">\n");
                xml.append("        <item name=\"fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("        <item name=\"android:fontFamily\">@font/").append(resource).append("</item>\n");
                xml.append("    </style>\n");
            }
        }

        // Bold text: the release build pairs a global textStyle with a heavier
        // title face so headers stay readable in every font.
        xml.append("\n    <style name=\"Miku.BoldText\" parent=\"\">\n");
        xml.append("        <item name=\"android:textStyle\">bold</item>\n");
        xml.append("        <item name=\"titleTextAppearance\">@style/Miku.Title.Bold</item>\n");
        xml.append("        <item name=\"collapsingToolbarLayoutStyle\">@style/Miku.CollapsingToolbar.Bold</item>\n");
        xml.append("    </style>\n");
        xml.append("\n    <style name=\"Miku.Title.Bold\" parent=\"@style/TextAppearance.Material3.TitleLarge\">\n");
        xml.append("        <item name=\"android:textStyle\">bold</item>\n");
        xml.append("    </style>\n");
        xml.append("\n    <style name=\"Miku.CollapsingToolbar.Bold\" parent=\"@style/Miku.CollapsingToolbar.Large\">\n");
        xml.append("        <item name=\"expandedTitleTextAppearance\">@style/Miku.CollapsingExpanded.Bold</item>\n");
        xml.append("        <item name=\"collapsedTitleTextAppearance\">@style/Miku.CollapsingCollapsed.Bold</item>\n");
        xml.append("    </style>\n");
        xml.append("\n    <style name=\"Miku.CollapsingExpanded.Bold\" parent=\"@style/TextAppearance.Miku.CollapsingExpanded\">\n");
        xml.append("        <item name=\"android:textStyle\">bold</item>\n");
        xml.append("    </style>\n");
        xml.append("\n    <style name=\"Miku.CollapsingCollapsed.Bold\" parent=\"@style/TextAppearance.Miku.CollapsingCollapsed\">\n");
        xml.append("        <item name=\"android:textStyle\">bold</item>\n");
        xml.append("    </style>\n");
        xml.append("</resources>\n");
        Files.writeString(res.resolve("values/themes_fonts.xml"), xml.toString(), StandardCharsets.UTF_8);

        // Picker arrays.
        StringBuilder keys = new StringBuilder();
        StringBuilder labels = new StringBuilder();
        for (Map.Entry<String, String[]> font : FONTS.entrySet()) {
            keys.append("        <item>").append(font.getKey()).append("</item>\n");
            labels.append("        <item>").append(font.getValue()[1]).append("</item>\n");
        }
        String arrays = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Generated by GenerateFonts: the font picker options. -->\n"
                + "<resources>\n"
                + "    <string-array name=\"font_family_values\" translatable=\"false\">\n" + keys + "    </string-array>\n"
                + "    <string-array name=\"font_family_labels\">\n" + labels + "    </string-array>\n"
                + "</resources>\n";
        Files.writeString(res.resolve("values/arrays_fonts.xml"), arrays, StandardCharsets.UTF_8);

        System.out.println("fonts=" + FONTS.size());
    }

    private static String camel(String key) {
        StringBuilder out = new StringBuilder();
        for (String part : key.split("[_\\-]")) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
