import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates real standard Latin keyboard layouts (metadata.json + layout.json +
 * symbols.json) for a set of languages, using Unicode code points (so no
 * non-ASCII appears in this source file). Native names/locales/scripts are read
 * from tools/languages_seed.json.
 *
 * These are genuine national keyboard layouts (plain QWERTY, Nordic +aa/ae/oe,
 * Portuguese +c-cedilla, Turkish-Q, etc.) — NOT fake translations or dictionaries.
 *
 * Usage:  java AuthorLangs.java <repoDir>
 * Then run BuildRepo to zip + checksum + regenerate the catalog.
 */
public class AuthorLangs {

    // function-key sentinels (negative)
    static final int SHIFT = -1, DEL = -2, SPACE = -3, ENTER = -4, SYM = -5, LANG = -6;

    // shared, real symbols page (ASCII)
    static final int[][] SYMBOLS = {
            {49, 50, 51, 52, 53, 54, 55, 56, 57, 48},
            {64, 35, 36, 95, 38, 45, 43, 40, 41, 47},
            {SYM, 42, 34, 39, 58, 59, 33, 63, DEL},
            {LANG, 44, SPACE, 46, ENTER}
    };

    static final int[] R1 = {113, 119, 101, 114, 116, 121, 117, 105, 111, 112}; // q..p
    static final int[] R2 = {97, 115, 100, 102, 103, 104, 106, 107, 108};       // a..l
    static final int[] R3 = {SHIFT, 122, 120, 99, 118, 98, 110, 109, DEL};      // z..m
    static final int[] R4 = {SYM, LANG, SPACE, 46, ENTER};

    public static void main(String[] args) throws Exception {
        File repo = new File(args.length > 0 ? args[0] : ".");
        List<Lang> seed = parseSeed(new File(repo, "tools/languages_seed.json"));

        Map<String, int[][]> layouts = new LinkedHashMap<>();

        // Plain QWERTY languages
        for (String id : new String[]{"it", "id", "nl", "pl", "ms-MY", "tl", "af", "sw"}) {
            layouts.put(id, new int[][]{R1, R2, R3, R4});
        }
        // Portuguese: + c-cedilla (231) on home row
        int[] r2c = append(R2, 231);
        for (String id : new String[]{"pt-BR", "pt-PT"}) {
            layouts.put(id, new int[][]{R1, r2c, R3, R4});
        }
        // Swedish/Finnish: + a-ring(229) row1, o-diaeresis(246)+a-diaeresis(228) row2
        int[] r1ring = append(R1, 229);
        int[] r2se = append(append(R2, 246), 228);
        for (String id : new String[]{"sv", "fi"}) {
            layouts.put(id, new int[][]{r1ring, r2se, R3, R4});
        }
        // Norwegian/Danish: + a-ring(229) row1, ae(230)+o-slash(248) row2
        int[] r2no = append(append(R2, 230), 248);
        for (String id : new String[]{"nb", "da"}) {
            layouts.put(id, new int[][]{r1ring, r2no, R3, R4});
        }
        // Albanian: + e-diaeresis(235) row2, c-cedilla(231) row3
        int[] r2sq = append(R2, 235);
        int[] r3sq = {SHIFT, 122, 120, 99, 118, 98, 110, 109, 231, DEL};
        layouts.put("sq", new int[][]{R1, r2sq, r3sq, R4});
        // Turkish-Q: q w e r t y u ı o p ğ ü / a s d f g h j k l ş i / z x c v b n m ö ç
        int[] r1tr = {113, 119, 101, 114, 116, 121, 117, 305, 111, 112, 287, 252};
        int[] r2tr = {97, 115, 100, 102, 103, 104, 106, 107, 108, 351, 105};
        int[] r3tr = {SHIFT, 122, 120, 99, 118, 98, 110, 109, 246, 231, DEL};
        layouts.put("tr", new int[][]{r1tr, r2tr, r3tr, R4});

        // ---- QWERTZ base (z<->y swapped) for Central-European languages ----
        int[] R1QZ = {113, 119, 101, 114, 116, 122, 117, 105, 111, 112}; // q w e r t z u i o p
        int[] R3QZ = {SHIFT, 121, 120, 99, 118, 98, 110, 109, DEL};      // y x c v b n m
        for (String id : new String[]{"cs", "sk", "lb"}) {
            layouts.put(id, new int[][]{R1QZ, R2, R3QZ, R4});
        }
        // Slovenian: QWERTZ + c-caron(269) s-caron(353) z-caron(382)
        layouts.put("sl", new int[][]{R1QZ, append(append(append(R2, 269), 353), 382), R3QZ, R4});
        // Croatian: q..p s-caron(353) d-stroke(273) / a..l c-caron(269) c-acute(263) z-caron(382) / y..m
        layouts.put("hr", new int[][]{
                append(append(R1QZ, 353), 273),
                append(append(append(R2, 269), 263), 382),
                R3QZ, R4});
        // Hungarian: QWERTZ / a..l e-acute(233) a-acute(225) / y..m o-diaeresis(246) u-diaeresis(252)
        layouts.put("hu", new int[][]{
                R1QZ,
                append(append(R2, 233), 225),
                new int[]{SHIFT, 121, 120, 99, 118, 98, 110, 109, 246, 252, DEL},
                R4});
        // Romanian: QWERTY / a..l a-breve(259) i-circ(238) / z..m s-comma(537) t-comma(539) a-circ(226)
        layouts.put("ro", new int[][]{
                R1,
                append(append(R2, 259), 238),
                new int[]{SHIFT, 122, 120, 99, 118, 98, 110, 109, 537, 539, 226, DEL},
                R4});
        // Catalan: QWERTY + c-cedilla(231)
        layouts.put("ca", new int[][]{R1, append(R2, 231), R3, R4});
        // Basque / Galician: QWERTY + n-tilde(241)
        for (String id : new String[]{"eu", "gl"}) {
            layouts.put(id, new int[][]{R1, append(R2, 241), R3, R4});
        }
        // Estonian: QWERTY + o-tilde(245) row1, a-diaeresis(228) o-diaeresis(246) row2
        layouts.put("et", new int[][]{append(R1, 245), append(append(R2, 228), 246), R3, R4});
        // Icelandic: q..p eth(240) / a..l ae(230) o-diaeresis(246) / z..m thorn(254)
        layouts.put("is", new int[][]{
                append(R1, 240),
                append(append(R2, 230), 246),
                new int[]{SHIFT, 122, 120, 99, 118, 98, 110, 109, 254, DEL},
                R4});
        // Plain QWERTY (accents via future long-press): Lithuanian, Latvian, Irish, Maltese, Esperanto
        for (String id : new String[]{"lt", "lv", "ga", "mt", "eo"}) {
            layouts.put(id, new int[][]{R1, R2, R3, R4});
        }

        int count = 0;
        for (Map.Entry<String, int[][]> e : layouts.entrySet()) {
            Lang meta = find(seed, e.getKey());
            if (meta == null) { System.out.println("! seed missing " + e.getKey()); continue; }
            writeLanguage(repo, meta, e.getValue());
            count++;
        }
        System.out.println("authored " + count + " Latin layouts (run BuildRepo next)");
    }

    static void writeLanguage(File repo, Lang l, int[][] rows) throws IOException {
        File dir = new File(repo, "languages/" + l.id);
        dir.mkdirs();
        writeUtf8(new File(dir, "metadata.json"), metadataJson(l));
        writeUtf8(new File(dir, "layout.json"), layoutJson(l.id, "main", rows));
        writeUtf8(new File(dir, "symbols.json"), layoutJson(l.id, "symbols", SYMBOLS));
    }

    static int[] append(int[] a, int v) {
        int[] out = Arrays.copyOf(a, a.length + 1);
        out[a.length] = v;
        return out;
    }

    static String metadataJson(Lang l) {
        return "{\n"
                + "  \"id\": " + q(l.id) + ",\n"
                + "  \"name\": " + q(l.name) + ",\n"
                + "  \"nativeName\": " + q(l.nativeName) + ",\n"
                + "  \"locale\": " + q(l.locale) + ",\n"
                + "  \"script\": " + q(l.script) + ",\n"
                + "  \"version\": 1,\n"
                + "  \"layouts\": [\"layout.json\", \"symbols.json\"]\n"
                + "}\n";
    }

    static String layoutJson(String id, String type, int[][] rows) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"id\": ").append(q(id)).append(",\n  \"type\": ").append(q(type))
                .append(",\n  \"rows\": [\n");
        for (int r = 0; r < rows.length; r++) {
            b.append("    { \"keys\": [");
            int[] row = rows[r];
            for (int i = 0; i < row.length; i++) {
                int code = row[i];
                String type2 = funcType(code);
                if (type2 != null) {
                    b.append("{\"type\": ").append(q(type2)).append("}");
                } else {
                    String label = new String(Character.toChars(code));
                    b.append("{\"label\": ").append(q(label)).append(", \"code\": ").append(code).append("}");
                }
                if (i < row.length - 1) b.append(", ");
            }
            b.append("] }");
            if (r < rows.length - 1) b.append(",");
            b.append("\n");
        }
        b.append("  ]\n}\n");
        return b.toString();
    }

    static String funcType(int code) {
        switch (code) {
            case SHIFT: return "shift";
            case DEL: return "delete";
            case SPACE: return "space";
            case ENTER: return "enter";
            case SYM: return "symbols";
            case LANG: return "language";
            default: return null;
        }
    }

    static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                default: b.append(c);
            }
        }
        return b.append("\"").toString();
    }

    static class Lang { String id, name, nativeName, locale, script; }

    static List<Lang> parseSeed(File f) throws IOException {
        List<Lang> out = new ArrayList<>();
        Pattern p = Pattern.compile(
                "\"id\"\\s*:\\s*\"([^\"]*)\".*?\"name\"\\s*:\\s*\"([^\"]*)\".*?"
                        + "\"nativeName\"\\s*:\\s*\"([^\"]*)\".*?\"locale\"\\s*:\\s*\"([^\"]*)\".*?"
                        + "\"script\"\\s*:\\s*\"([^\"]*)\"");
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        for (String line : content.split("\n")) {
            if (!line.contains("\"id\"") || !line.contains("\"script\"")) continue;
            Matcher m = p.matcher(line);
            if (m.find()) {
                Lang l = new Lang();
                l.id = m.group(1); l.name = m.group(2); l.nativeName = m.group(3);
                l.locale = m.group(4); l.script = m.group(5);
                out.add(l);
            }
        }
        return out;
    }

    static Lang find(List<Lang> list, String id) {
        for (Lang l : list) if (l.id.equals(id)) return l;
        return null;
    }

    static void writeUtf8(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(s);
        }
    }
}
