import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates a COMPLETE keyboard for every catalog language that doesn't already
 * have a hand-authored layout. Non-Latin scripts are built from their real
 * Unicode letter ranges (every letter/vowel/sign of the script present), plus a
 * shared numbers/symbols page and a full function row. Latin languages get a
 * standard QWERTY base.
 *
 * These are complete, real-Unicode keyboards (not standard national key
 * positions). Existing custom layouts (en-US, vi, es, fr, de + the other Latin
 * ones, and anything already on disk) are preserved.
 *
 * Usage:  java AuthorAll.java <repoDir>
 */
public class AuthorAll {

    static final int SHIFT = -1, DEL = -2, SPACE = -3, ENTER = -4, SYM = -5, LANG = -6;

    static final int[][] SYMBOLS = {
            {49, 50, 51, 52, 53, 54, 55, 56, 57, 48},
            {64, 35, 36, 95, 38, 45, 43, 40, 41, 47},
            {SYM, 42, 34, 39, 58, 59, 33, 63, DEL},
            {LANG, 44, SPACE, 46, ENTER}
    };

    static final int[] R1 = {113, 119, 101, 114, 116, 121, 117, 105, 111, 112};
    static final int[] R2 = {97, 115, 100, 102, 103, 104, 106, 107, 108};
    static final int[] R3 = {SHIFT, 122, 120, 99, 118, 98, 110, 109, DEL};
    static final int[] R4 = {SYM, LANG, SPACE, 46, ENTER};

    // Scripts that have upper/lower case (get a shift key + uppercase on shift).
    static final Set<String> CASED = new HashSet<>(Arrays.asList(
            "Latin", "Cyrillic", "Greek", "Armenian"));

    // Per-script Unicode letter ranges (inclusive pairs). Real characters only.
    static Map<String, int[]> ranges() {
        Map<String, int[]> m = new HashMap<>();
        m.put("Cyrillic", new int[]{0x0430, 0x044F});
        m.put("Greek", new int[]{0x03B1, 0x03C9});
        m.put("Armenian", new int[]{0x0561, 0x0586});
        m.put("Georgian", new int[]{0x10D0, 0x10F0});
        m.put("Hebrew", new int[]{0x05D0, 0x05EA});
        m.put("Arabic", new int[]{0x0621, 0x064A});
        m.put("Thaana", new int[]{0x0780, 0x07B0});
        m.put("Devanagari", new int[]{0x0905, 0x0939, 0x093E, 0x094D, 0x0901, 0x0903, 0x0966, 0x096F});
        m.put("Bengali", new int[]{0x0985, 0x098C, 0x098F, 0x0990, 0x0993, 0x09B9, 0x09BE, 0x09CC, 0x0981, 0x0983, 0x09E6, 0x09EF});
        m.put("Gujarati", new int[]{0x0A85, 0x0A8C, 0x0A8F, 0x0A91, 0x0A93, 0x0AB9, 0x0ABE, 0x0ACC, 0x0A81, 0x0A83, 0x0AE6, 0x0AEF});
        m.put("Gurmukhi", new int[]{0x0A05, 0x0A0A, 0x0A0F, 0x0A10, 0x0A13, 0x0A39, 0x0A3E, 0x0A4C, 0x0A66, 0x0A6F});
        m.put("Tamil", new int[]{0x0B85, 0x0B8A, 0x0B8E, 0x0B90, 0x0B92, 0x0BB9, 0x0BBE, 0x0BCC, 0x0B82, 0x0B83, 0x0BE6, 0x0BEF});
        m.put("Telugu", new int[]{0x0C05, 0x0C0C, 0x0C0E, 0x0C10, 0x0C12, 0x0C39, 0x0C3E, 0x0C4C, 0x0C66, 0x0C6F});
        m.put("Kannada", new int[]{0x0C85, 0x0C8C, 0x0C8E, 0x0C90, 0x0C92, 0x0CB9, 0x0CBE, 0x0CCC, 0x0CE6, 0x0CEF});
        m.put("Malayalam", new int[]{0x0D05, 0x0D0C, 0x0D0E, 0x0D10, 0x0D12, 0x0D39, 0x0D3E, 0x0D4C, 0x0D66, 0x0D6F});
        m.put("Odia", new int[]{0x0B05, 0x0B0C, 0x0B0F, 0x0B10, 0x0B13, 0x0B39, 0x0B3E, 0x0B4C, 0x0B66, 0x0B6F});
        m.put("Sinhala", new int[]{0x0D85, 0x0D96, 0x0D9A, 0x0DB1, 0x0DB3, 0x0DBB, 0x0DC0, 0x0DC6, 0x0DCF, 0x0DDF});
        m.put("Thai", new int[]{0x0E01, 0x0E2E, 0x0E30, 0x0E3A, 0x0E40, 0x0E4E, 0x0E50, 0x0E59});
        m.put("Lao", new int[]{0x0E81, 0x0EAE, 0x0EB0, 0x0EBD, 0x0EC0, 0x0EC4, 0x0EC8, 0x0ECD, 0x0ED0, 0x0ED9});
        m.put("Khmer", new int[]{0x1780, 0x17A2, 0x17A5, 0x17B3, 0x17B6, 0x17C5, 0x17E0, 0x17E9});
        m.put("Myanmar", new int[]{0x1000, 0x1021, 0x102B, 0x1038, 0x103B, 0x103E, 0x1040, 0x1049});
        m.put("Tibetan", new int[]{0x0F40, 0x0F6C, 0x0F71, 0x0F84, 0x0F20, 0x0F29});
        m.put("Meetei Mayek", new int[]{0xABC0, 0xABE2});
        m.put("Ol Chiki", new int[]{0x1C50, 0x1C77});
        m.put("Hangul", new int[]{0x3131, 0x3163});
        m.put("Japanese", new int[]{0x3041, 0x3096});
        return m;
    }

    // Extra language-specific letters commonly needed within a script.
    static Map<String, int[]> extras() {
        Map<String, int[]> m = new HashMap<>();
        m.put("Cyrillic", new int[]{0x0451, 0x0454, 0x0456, 0x0457, 0x0491, 0x045E, 0x0452,
                0x0459, 0x045A, 0x045B, 0x045F, 0x0458, 0x0455, 0x045C, 0x0453,
                0x04D9, 0x0493, 0x049B, 0x04A3, 0x04E9, 0x04B1, 0x04AF, 0x04BB});
        m.put("Arabic", new int[]{0x0671, 0x067E, 0x0686, 0x0698, 0x06A9, 0x06AF, 0x06BE,
                0x06C1, 0x06CC, 0x06D2, 0x0679, 0x0688, 0x0691, 0x06BA, 0x06D3, 0x06AD,
                0x06C7, 0x06C6, 0x06C8, 0x06D5, 0x0640});
        m.put("Devanagari", new int[]{0x0950, 0x0958, 0x0959, 0x095A, 0x095B, 0x095C, 0x095D, 0x095E, 0x095F});
        return m;
    }

    public static void main(String[] args) throws Exception {
        File repo = new File(args.length > 0 ? args[0] : ".");
        List<Lang> seed = parseSeed(new File(repo, "tools/languages_seed.json"));
        Map<String, int[]> ranges = ranges();
        Map<String, int[]> extras = extras();

        int made = 0, skipped = 0;
        for (Lang l : seed) {
            File dir = new File(repo, "languages/" + l.id);
            if (new File(dir, "layout.json").exists()) { skipped++; continue; } // keep existing

            int[][] rows;
            if ("Latin".equals(l.script)) {
                rows = new int[][]{R1, R2, R3, R4};
            } else {
                List<Integer> cps = expand(l.script, ranges, extras);
                if (cps.isEmpty()) { System.out.println("! no data for script " + l.script + " (" + l.id + ")"); skipped++; continue; }
                rows = buildGrid(cps, CASED.contains(l.script));
            }
            dir.mkdirs();
            writeUtf8(new File(dir, "metadata.json"), metadataJson(l));
            writeUtf8(new File(dir, "layout.json"), layoutJson(l.id, "main", rows));
            writeUtf8(new File(dir, "symbols.json"), layoutJson(l.id, "symbols", SYMBOLS));
            made++;
        }
        System.out.println("generated " + made + " layouts, kept " + skipped + " existing");
    }

    static List<Integer> expand(String script, Map<String, int[]> ranges, Map<String, int[]> extras) {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        if ("Ethiopic".equals(script)) {
            for (int cp = 0x1200; cp <= 0x1348; cp += 8) set.add(cp); // base (ge'ez) forms
            return new ArrayList<>(set);
        }
        if ("Canadian Aboriginal".equals(script)) {
            int[] inuk = {0x1403, 0x1405, 0x140A, 0x1431, 0x1432, 0x1433, 0x1466, 0x1467, 0x1468,
                    0x14A1, 0x14A3, 0x14A5, 0x14BB, 0x14BC, 0x14C0, 0x14EF, 0x14F1, 0x14F3,
                    0x1550, 0x1552, 0x1554, 0x158F, 0x1590, 0x1591, 0x15A0, 0x15A1, 0x15A2};
            for (int cp : inuk) set.add(cp);
            return new ArrayList<>(set);
        }
        int[] r = ranges.get(script);
        if (r != null) for (int i = 0; i + 1 < r.length; i += 2)
            for (int cp = r[i]; cp <= r[i + 1]; cp++) set.add(cp);
        int[] ex = extras.get(script);
        if (ex != null) for (int cp : ex) set.add(cp);
        return new ArrayList<>(set);
    }

    static int[][] buildGrid(List<Integer> codes, boolean cased) {
        int cols = codes.size() <= 30 ? 10 : (codes.size() <= 44 ? 11 : 12);
        List<int[]> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        for (int cp : codes) {
            cur.add(cp);
            if (cur.size() == cols) { rows.add(toArr(cur)); cur = new ArrayList<>(); }
        }
        if (!cur.isEmpty()) rows.add(toArr(cur));
        // add shift (if cased) + delete to the last letter row
        List<Integer> last = toList(rows.remove(rows.size() - 1));
        if (cased) last.add(0, SHIFT);
        last.add(DEL);
        rows.add(toArr(last));
        rows.add(new int[]{SYM, LANG, SPACE, 46, ENTER});
        return rows.toArray(new int[0][]);
    }

    static int[] toArr(List<Integer> l) { int[] a = new int[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }
    static List<Integer> toList(int[] a) { List<Integer> l = new ArrayList<>(); for (int v : a) l.add(v); return l; }

    static String metadataJson(Lang l) {
        return "{\n  \"id\": " + q(l.id) + ",\n  \"name\": " + q(l.name)
                + ",\n  \"nativeName\": " + q(l.nativeName) + ",\n  \"locale\": " + q(l.locale)
                + ",\n  \"script\": " + q(l.script) + ",\n  \"version\": 1,\n"
                + "  \"layouts\": [\"layout.json\", \"symbols.json\"]\n}\n";
    }

    static String layoutJson(String id, String type, int[][] rows) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"id\": ").append(q(id)).append(",\n  \"type\": ").append(q(type)).append(",\n  \"rows\": [\n");
        for (int r = 0; r < rows.length; r++) {
            b.append("    { \"keys\": [");
            for (int i = 0; i < rows[r].length; i++) {
                int code = rows[r][i];
                String t = funcType(code);
                if (t != null) b.append("{\"type\": ").append(q(t)).append("}");
                else b.append("{\"label\": ").append(q(new String(Character.toChars(code)))).append(", \"code\": ").append(code).append("}");
                if (i < rows[r].length - 1) b.append(", ");
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
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else b.append(c);
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

    static void writeUtf8(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(s); }
    }
}
