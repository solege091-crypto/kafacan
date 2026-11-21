package com.example.afkkafa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hangi kafa hangi kelimeye ait: ogrenir, eleme yapar, dosyaya kaydeder. (Minecraft'a bagimli degil) */
final class HeadBrain {

    private final Map<String, String> known = new HashMap<>();      // kafa kimligi -> kelime
    private final Map<String, Set<String>> cand = new HashMap<>();  // kelime -> aday kafalar

    /** Tiklanacak slotu dondurur, bilmiyorsa null. keys: slot -> kafa kimligi */
    Integer choose(String word, Map<Integer, String> keys) {
        for (Map.Entry<Integer, String> e : keys.entrySet()) {
            if (word.equals(known.get(e.getValue()))) return e.getKey();
        }

        Set<String> cands = new LinkedHashSet<>();
        for (String k : keys.values()) {
            if (known.get(k) == null) cands.add(k); // baska kelimeye ait oldugu bilinenler elenir
        }
        Set<String> prev = cand.get(word);
        if (prev != null) {
            Set<String> inter = new LinkedHashSet<>(cands);
            inter.retainAll(prev);
            if (!inter.isEmpty()) cands = inter;
        }
        cand.put(word, new HashSet<>(cands));

        if (cands.size() == 1) {
            String k = cands.iterator().next();
            known.put(k, word);
            for (Map.Entry<Integer, String> e : keys.entrySet()) {
                if (e.getValue().equals(k)) return e.getKey();
            }
        }
        return null;
    }

    void learn(String word, String key) {
        known.put(key, word);
    }

    int knownCount() {
        return known.size();
    }

    void load(Path p) {
        try {
            if (!Files.exists(p)) return;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String[] t = line.split("\t");
                if (t.length < 3) continue;
                if (t[0].equals("K")) known.put(t[2], t[1]);
                else if (t[0].equals("C")) {
                    Set<String> s = new HashSet<>();
                    for (String k : t[2].split(",")) if (!k.isEmpty()) s.add(k);
                    cand.put(t[1], s);
                }
            }
        } catch (IOException ignored) {
        }
    }

    void save(Path p) {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : known.entrySet()) lines.add("K\t" + e.getValue() + "\t" + e.getKey());
            for (Map.Entry<String, Set<String>> e : cand.entrySet()) {
                lines.add("C\t" + e.getKey() + "\t" + String.join(",", e.getValue()));
            }
            Files.write(p, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
