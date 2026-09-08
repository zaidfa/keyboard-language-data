import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Inserts an emoji key immediately before every language (globe) key in all
 * language layout/symbols JSON files. Idempotent: it first removes any existing
 * emoji-before-language pair, then inserts exactly one. Run BuildRepo afterwards
 * to repackage + regenerate the catalog.
 *
 * Usage:  java AddEmoji.java <repoDir>
 */
public class AddEmoji {
    public static void main(String[] args) throws Exception {
        File repo = new File(args.length > 0 ? args[0] : ".");
        File langs = new File(repo, "languages");
        File[] dirs = langs.listFiles();
        if (dirs == null) { System.out.println("no languages dir"); return; }
        int changed = 0;
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            for (String name : new String[]{"layout.json", "symbols.json"}) {
                File f = new File(d, name);
                if (!f.exists()) continue;
                String before = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (!before.contains("\"language\"")) continue;
                String after = before
                        // undo any existing emoji-immediately-before-language (idempotent)
                        .replaceAll("\\{\"type\":\\s*\"emoji\"\\}\\s*,\\s*(\\{\"type\":\\s*\"language\"\\})", "$1")
                        // insert one emoji key before each language key
                        .replaceAll("(\\{\"type\":\\s*\"language\"\\})", "{\"type\": \"emoji\"}, $1");
                if (!after.equals(before)) {
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                        w.write(after);
                    }
                    changed++;
                }
            }
        }
        System.out.println("updated " + changed + " layout/symbols files with an emoji key");
    }
}
