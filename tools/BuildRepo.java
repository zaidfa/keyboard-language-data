import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * One-off bootstrap builder for the GitHub language repository.
 *
 * It exports the REAL English + Vietnamese layouts from the Android project's
 * compile-time keyboard XML (res/xml) into the runtime JSON package format,
 * zips each package, computes the real SHA-256 + size, writes each manifest,
 * and generates catalog/languages.json from tools/languages_seed.json.
 *
 * Everything in the seed that we do not have real data for is emitted as
 * "coming_soon" (no fake keyboard data is ever produced).
 *
 * Usage:  java BuildRepo.java <repoDir> <resXmlDir>
 */
public class BuildRepo {

    static final int VERSION = 1;

    public static void main(String[] args) throws Exception {
        File repoDir = new File(args.length > 0 ? args[0] : ".");
        File resXml = new File(args.length > 1 ? args[1] : "../app/src/main/res/xml");

        File seedFile = new File(repoDir, "tools/languages_seed.json");
        List<Lang> seed = parseSeed(seedFile);

        // Built-in languages we have real data for -> package id : {mainXml, symbolsXml}
        Map<String, String[]> builtin = new LinkedHashMap<>();
        builtin.put("en-US", new String[]{"z6.xml", "z10.xml"});
        builtin.put("vi", new String[]{"qwerty_khmer.xml", "symbols_khmer.xml"});

        Map<String, PkgResult> built = new HashMap<>();

        // 1) Built-ins exported from the app's compiled keyboard XML.
        for (Map.Entry<String, String[]> e : builtin.entrySet()) {
            String id = e.getKey();
            Lang meta = find(seed, id);
            if (meta == null) { System.out.println("! seed missing " + id); continue; }
            File mainXml = new File(resXml, e.getValue()[0]);
            File symXml = new File(resXml, e.getValue()[1]);
            PkgResult r = buildPackage(repoDir, meta, mainXml, symXml);
            built.put(id, r);
            System.out.println("built(xml) " + id + "  size=" + r.size + "  sha256=" + r.sha256);
        }

        // 2) Hand-authored languages: any languages/<id>/ that already has
        //    metadata.json + layout.json (real layout data committed by hand).
        File langsDir = new File(repoDir, "languages");
        File[] dirs = langsDir.listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                String id = d.getName();
                if (built.containsKey(id)) continue; // already built above
                if (new File(d, "metadata.json").exists() && new File(d, "layout.json").exists()) {
                    PkgResult r = packageAuthored(d);
                    built.put(id, r);
                    System.out.println("built(src) " + id + "  size=" + r.size + "  sha256=" + r.sha256);
                }
            }
        }

        writeCatalog(repoDir, seed, built);
        System.out.println("catalog written with " + seed.size() + " languages ("
                + built.size() + " available, " + (seed.size() - built.size()) + " coming_soon)");
    }

    // ---- package building ----

    static class PkgResult { long size; String sha256; }

    static PkgResult buildPackage(File repoDir, Lang meta, File mainXml, File symXml) throws Exception {
        File dir = new File(repoDir, "languages/" + meta.id);
        dir.mkdirs();

        String metadata = metadataJson(meta);
        String layout = layoutJson(meta.id, "main", extractRows(readUtf8(mainXml)));
        String symbols = layoutJson(meta.id, "symbols", extractRows(readUtf8(symXml)));

        // Write the readable source files alongside the zip (handy for editing/diffs).
        writeUtf8(new File(dir, "metadata.json"), metadata);
        writeUtf8(new File(dir, "layout.json"), layout);
        writeUtf8(new File(dir, "symbols.json"), symbols);

        File zip = new File(dir, "language.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", metadata.getBytes(StandardCharsets.UTF_8));
        entries.put("layout.json", layout.getBytes(StandardCharsets.UTF_8));
        entries.put("symbols.json", symbols.getBytes(StandardCharsets.UTF_8));
        writeZip(zip, entries);

        PkgResult r = new PkgResult();
        r.size = zip.length();
        r.sha256 = sha256(zip);

        writeUtf8(new File(dir, "manifest.json"), manifestJson(meta, r));
        return r;
    }

    /** Packages an already hand-authored language folder (metadata/layout/symbols). */
    static PkgResult packageAuthored(File dir) throws Exception {
        String metadata = readUtf8(new File(dir, "metadata.json"));
        Lang meta = new Lang();
        meta.id = jsonValue(metadata, "id");
        meta.name = jsonValue(metadata, "name");
        meta.nativeName = jsonValue(metadata, "nativeName");
        meta.locale = jsonValue(metadata, "locale");
        String vStr = jsonValue(metadata, "version");
        int version = VERSION;
        try { if (vStr != null) version = Integer.parseInt(vStr.trim()); } catch (Exception ignore) {}

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", metadata.getBytes(StandardCharsets.UTF_8));
        for (String name : new String[]{"layout.json", "symbols.json"}) {
            File f = new File(dir, name);
            if (f.exists()) entries.put(name, readUtf8(f).getBytes(StandardCharsets.UTF_8));
        }

        File zip = new File(dir, "language.zip");
        writeZip(zip, entries);

        PkgResult r = new PkgResult();
        r.size = zip.length();
        r.sha256 = sha256(zip);

        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"id\": ").append(q(meta.id)).append(",\n");
        b.append("  \"name\": ").append(q(meta.name)).append(",\n");
        b.append("  \"nativeName\": ").append(q(meta.nativeName)).append(",\n");
        b.append("  \"locale\": ").append(q(meta.locale)).append(",\n");
        b.append("  \"version\": ").append(version).append(",\n");
        b.append("  \"package\": \"language.zip\",\n");
        b.append("  \"sizeBytes\": ").append(r.size).append(",\n");
        b.append("  \"sha256\": ").append(q(r.sha256)).append("\n");
        b.append("}\n");
        writeUtf8(new File(dir, "manifest.json"), b.toString());
        return r;
    }

    static String jsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static void writeZip(File zip, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.setLevel(9);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(946684800000L); // fixed 2000-01-01 for reproducible zips
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
    }

    // ---- keyboard XML -> rows of keys ----

    static class Key { String label; Integer code; String type; }

    static List<List<Key>> extractRows(String xml) {
        // strip comments so commented-out keys are ignored
        xml = xml.replaceAll("(?s)<!--.*?-->", "");
        List<List<Key>> rows = new ArrayList<>();
        String[] parts = xml.split("<Row");
        for (int i = 1; i < parts.length; i++) {
            String rowXml = parts[i];
            List<Key> keys = new ArrayList<>();
            Matcher m = Pattern.compile("(?s)<Key\\b(.*?)/?>").matcher(rowXml);
            while (m.find()) {
                String attrs = m.group(1);
                Key k = parseKey(attrs);
                if (k != null) keys.add(k);
            }
            if (!keys.isEmpty()) rows.add(keys);
        }
        return rows;
    }

    static Key parseKey(String attrs) {
        String label = attr(attrs, "keyLabel");
        boolean hasIcon = attr(attrs, "keyIcon") != null;
        String codeStr = attr(attrs, "codes");
        Integer code = null;
        if (codeStr != null) {
            try { code = Integer.parseInt(codeStr.split(",")[0].trim()); } catch (Exception ignore) {}
        }
        Key k = new Key();
        String type = typeForCode(code);
        if (type != null) { k.type = type; return k; }
        if (label != null && label.startsWith("@string/")) {
            // language switch label etc. -> function key
            k.type = (code != null && code == -14) ? "language" : "function";
            return k;
        }
        if (label != null && label.length() > 0) {
            k.label = label;
            k.code = (code != null) ? code : (int) label.charAt(0);
            return k;
        }
        if (hasIcon) { k.type = "function"; return k; }
        return null;
    }

    static String typeForCode(Integer c) {
        if (c == null) return null;
        switch (c) {
            case -1: return "shift";
            case -5: return "delete";
            case 32: return "space";
            case 10: return "enter";
            case -2: return "mode";
            case -200: return "symbols";
            case -14: return "language";
            case -15: return "language";
            case -16: return "settings";
            case -17: return "theme";
            case -100: return "mode";
            case -300: return "emoji";
            default: return null;
        }
    }

    static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("android:" + name + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    // ---- JSON emit (UTF-8, minimal + safe escaping) ----

    static String metadataJson(Lang l) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"id\": ").append(q(l.id)).append(",\n");
        b.append("  \"name\": ").append(q(l.name)).append(",\n");
        b.append("  \"nativeName\": ").append(q(l.nativeName)).append(",\n");
        b.append("  \"locale\": ").append(q(l.locale)).append(",\n");
        b.append("  \"script\": ").append(q(l.script)).append(",\n");
        b.append("  \"version\": ").append(VERSION).append(",\n");
        b.append("  \"layouts\": [\"layout.json\", \"symbols.json\"]\n");
        b.append("}\n");
        return b.toString();
    }

    static String layoutJson(String id, String type, List<List<Key>> rows) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"id\": ").append(q(id)).append(",\n");
        b.append("  \"type\": ").append(q(type)).append(",\n");
        b.append("  \"rows\": [\n");
        for (int r = 0; r < rows.size(); r++) {
            b.append("    { \"keys\": [");
            List<Key> keys = rows.get(r);
            for (int k = 0; k < keys.size(); k++) {
                Key key = keys.get(k);
                if (key.type != null) {
                    b.append("{\"type\": ").append(q(key.type)).append("}");
                } else {
                    b.append("{\"label\": ").append(q(key.label))
                            .append(", \"code\": ").append(key.code).append("}");
                }
                if (k < keys.size() - 1) b.append(", ");
            }
            b.append("] }");
            if (r < rows.size() - 1) b.append(",");
            b.append("\n");
        }
        b.append("  ]\n");
        b.append("}\n");
        return b.toString();
    }

    static String manifestJson(Lang l, PkgResult r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"id\": ").append(q(l.id)).append(",\n");
        b.append("  \"name\": ").append(q(l.name)).append(",\n");
        b.append("  \"nativeName\": ").append(q(l.nativeName)).append(",\n");
        b.append("  \"locale\": ").append(q(l.locale)).append(",\n");
        b.append("  \"version\": ").append(VERSION).append(",\n");
        b.append("  \"package\": \"language.zip\",\n");
        b.append("  \"sizeBytes\": ").append(r.size).append(",\n");
        b.append("  \"sha256\": ").append(q(r.sha256)).append("\n");
        b.append("}\n");
        return b.toString();
    }

    static void writeCatalog(File repoDir, List<Lang> seed, Map<String, PkgResult> built) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"schemaVersion\": 1,\n");
        b.append("  \"catalogVersion\": 1,\n");
        b.append("  \"languages\": [\n");
        for (int i = 0; i < seed.size(); i++) {
            Lang l = seed.get(i);
            PkgResult r = built.get(l.id);
            boolean available = r != null;
            b.append("    {");
            b.append(" \"id\": ").append(q(l.id)).append(",");
            b.append(" \"name\": ").append(q(l.name)).append(",");
            b.append(" \"nativeName\": ").append(q(l.nativeName)).append(",");
            b.append(" \"locale\": ").append(q(l.locale)).append(",");
            b.append(" \"script\": ").append(q(l.script)).append(",");
            b.append(" \"version\": ").append(VERSION).append(",");
            b.append(" \"status\": ").append(q(available ? "available" : "coming_soon")).append(",");
            b.append(" \"package\": ").append(q(available ? "languages/" + l.id + "/language.zip" : "")).append(",");
            b.append(" \"sizeBytes\": ").append(available ? r.size : 0).append(",");
            b.append(" \"sha256\": ").append(q(available ? r.sha256 : ""));
            b.append(" }");
            if (i < seed.size() - 1) b.append(",");
            b.append("\n");
        }
        b.append("  ]\n");
        b.append("}\n");
        File cat = new File(repoDir, "catalog/languages.json");
        cat.getParentFile().mkdirs();
        writeUtf8(cat, b.toString());
    }

    static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: b.append(c);
            }
        }
        return b.append("\"").toString();
    }

    // ---- seed parsing (one language object per line) ----

    static class Lang { String id, name, nativeName, locale, script; }

    static List<Lang> parseSeed(File f) throws IOException {
        List<Lang> out = new ArrayList<>();
        Pattern p = Pattern.compile(
                "\"id\"\\s*:\\s*\"([^\"]*)\".*?\"name\"\\s*:\\s*\"([^\"]*)\".*?"
                        + "\"nativeName\"\\s*:\\s*\"([^\"]*)\".*?\"locale\"\\s*:\\s*\"([^\"]*)\".*?"
                        + "\"script\"\\s*:\\s*\"([^\"]*)\"");
        for (String line : readUtf8(f).split("\n")) {
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

    // ---- io + hashing ----

    static String readUtf8(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static void writeUtf8(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(s);
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
