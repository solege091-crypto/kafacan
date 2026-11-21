package com.example.afkkafa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Turkce kelime -> kafanin icinde aranacak yazilar (Turkce + Ingilizce). Minecraft'a bagimli degil. */
final class HeadWords {

    private static final Map<String, String[]> TR_EN = new HashMap<>();

    private static void p(String tr, String... en) {
        TR_EN.put(tr, en);
    }

    static {
        // meyve / sebze / yiyecek
        p("elma", "apple"); p("armut", "pear"); p("muz", "banana"); p("cilek", "strawberry");
        p("kiraz", "cherry"); p("visne", "cherry"); p("uzum", "grape", "grapes"); p("karpuz", "watermelon");
        p("kavun", "melon", "cantaloupe"); p("seftali", "peach"); p("kayisi", "apricot"); p("erik", "plum");
        p("portakal", "orange"); p("mandalina", "tangerine", "mandarin"); p("limon", "lemon"); p("ananas", "pineapple");
        p("mango", "mango"); p("nar", "pomegranate"); p("incir", "fig"); p("avokado", "avocado"); p("kivi", "kiwi");
        p("hindistan cevizi", "coconut"); p("bogurtlen", "blackberry"); p("ahududu", "raspberry");
        p("yaban mersini", "blueberry"); p("domates", "tomato"); p("patates", "potato"); p("havuc", "carrot");
        p("misir", "corn"); p("biber", "pepper"); p("patlican", "eggplant"); p("salatalik", "cucumber");
        p("sogan", "onion"); p("sarimsak", "garlic"); p("mantar", "mushroom"); p("balkabagi", "pumpkin");
        p("kaktus", "cactus"); p("ekmek", "bread"); p("peynir", "cheese"); p("yumurta", "egg");
        p("pasta", "cake"); p("kurabiye", "cookie"); p("cikolata", "chocolate"); p("dondurma", "ice cream");
        p("bal", "honey"); p("sut", "milk"); p("lahana", "cabbage"); p("brokoli", "broccoli"); p("fistik", "peanut");
        // hayvanlar
        p("ayi", "bear"); p("panda", "panda"); p("kedi", "cat"); p("kopek", "dog"); p("tavsan", "rabbit", "bunny");
        p("inek", "cow"); p("domuz", "pig"); p("koyun", "sheep"); p("tavuk", "chicken"); p("at", "horse");
        p("esek", "donkey"); p("kurt", "wolf"); p("tilki", "fox"); p("aslan", "lion"); p("kaplan", "tiger");
        p("fil", "elephant"); p("maymun", "monkey"); p("penguen", "penguin"); p("kurbaga", "frog");
        p("balik", "fish"); p("kus", "bird"); p("baykus", "owl"); p("kartal", "eagle"); p("yilan", "snake");
        p("kaplumbaga", "turtle"); p("kopekbaligi", "shark"); p("balina", "whale"); p("yunus", "dolphin");
        p("ahtapot", "octopus"); p("yengec", "crab"); p("ari", "bee"); p("kelebek", "butterfly");
        p("ordek", "duck"); p("kaz", "goose"); p("hindi", "turkey"); p("deve", "camel"); p("zurafa", "giraffe");
        p("gergedan", "rhino", "rhinoceros"); p("su aygiri", "hippo"); p("geyik", "deer"); p("sincap", "squirrel");
        p("kirpi", "hedgehog"); p("yarasa", "bat"); p("fare", "mouse"); p("kanguru", "kangaroo"); p("goril", "gorilla");
        p("leopar", "leopard"); p("cita", "cheetah"); p("karinca", "ant"); p("orumcek", "spider");
        p("ejderha", "dragon"); p("tavus kusu", "peacock"); p("flamingo", "flamingo"); p("lama", "llama");
        p("keci", "goat"); p("marti", "seagull"); p("papagan", "parrot"); p("timsah", "crocodile", "alligator");
        p("mors", "walrus"); p("fok", "seal"); p("kopek baligi", "shark"); p("denizati", "seahorse");
        p("yengec", "crab"); p("istakoz", "lobster"); p("salyangoz", "snail"); p("solucan", "worm");
        p("ceylan", "gazelle"); p("bufalo", "buffalo"); p("boga", "bull"); p("tay", "foal"); p("civciv", "chick");
    }

    static List<String> terms(String word) {
        List<String> out = new ArrayList<>();
        String w = word.trim();
        out.add(w);
        if (w.contains(" ")) {
            out.add(w.replace(' ', '_'));
            out.add(w.replace(" ", ""));
        }
        String[] en = TR_EN.get(w);
        if (en != null) for (String e : en) out.add(e);
        return out;
    }

    /** hay: normalize edilmis (kucuk harf, aksansiz) metin. */
    static boolean matches(String hay, List<String> terms) {
        for (String t : terms) {
            String re = "(?<![a-z0-9])" + Pattern.quote(t) + (t.length() <= 3 ? "(?![a-z0-9])" : "");
            if (Pattern.compile(re).matcher(hay).find()) return true;
        }
        return false;
    }
}
