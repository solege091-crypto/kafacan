package com.example.afkkafa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Elma kafasina tikla" tarzi menuler: basliktaki kelimeyi okur, dogru kafayi bulup tiklar.
 * Kafalari gorsellerinden tanir. Bilmedigi kafayi senin tikladigindan ogrenir.
 * Her adimi sohbete [AFK-Kafa] diye yazar (sorun olursa ekran goruntusu icin).
 */
public class AfkHeads implements ClientModInitializer {

    private static final Pattern TITLE = Pattern.compile("(.+?)\\s+kafasina\\s+tikla");
    private static final Pattern TEX_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Random RND = new Random();

    private static final HeadBrain BRAIN = new HeadBrain();
    private static Path file;

    private static Object lastScreen = null;
    private static String word = null;
    private static final Map<Integer, String> keys = new HashMap<>();
    private static int ticks = 0;
    private static boolean decided = false;
    private static int clickAt = -1;
    private static int clickSlot = -1;
    private static boolean selfClick = false;
    private static int recast = -1;

    private static int apiGen = 0;
    private static boolean apiRunning = false;
    private static volatile boolean apiDone = false;
    private static volatile int apiIdx = -1;
    private static volatile String apiErr = null;
    private static List<Integer> apiSlots = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        file = FabricLoader.getInstance().getConfigDir().resolve("afk-kafalar.txt");
        BRAIN.load(file);
        ClientTickEvents.END_CLIENT_TICK.register(AfkHeads::tick);
    }

    private static String norm(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = s.replaceAll("\u00A7.", "");
        s = s.toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace('\u0131', 'i');
    }

    /** Su an ekranda "... kafasina tikla" menusu acik mi? */
    public static boolean isHeadMenu(MinecraftClient c) {
        if (c == null || !(c.currentScreen instanceof HandledScreen<?> hs)) return false;
        try {
            return TITLE.matcher(norm(hs.getTitle().getString())).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static void reset() {
        lastScreen = null;
        word = null;
        keys.clear();
        ticks = 0;
        decided = false;
        clickAt = -1;
        apiGen++;
        apiRunning = false;
    }

    private static void say(MinecraftClient c, String t) {
        if (c.player != null) c.player.sendMessage(Text.literal("[AFK-Kafa] " + t), false);
    }

    private static String shortKey(String k) {
        return k.length() > 9 ? k.substring(0, 2) + k.substring(k.length() - 5) : k;
    }

    private static void tick(MinecraftClient c) {
        if (isHeadMenu(c)) {
            c.options.useKey.setPressed(false);
            c.options.attackKey.setPressed(false);
        }
        if (recast >= 0 && c.player != null && c.currentScreen == null) {
            if (--recast < 0) {
                recast = -1;
                if (c.interactionManager != null
                    && c.player.getInventory().getMainHandStack().isOf(Items.FISHING_ROD)
                    && !hasBobber(c)) {
                    c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
                    say(c, "Olta tekrar atıldı");
                }
            }
        }
        if (c.player == null || !(c.currentScreen instanceof HandledScreen<?> hs)) {
            if (lastScreen != null) reset();
            return;
        }
        Matcher m = TITLE.matcher(norm(hs.getTitle().getString()));
        if (!m.find()) {
            if (lastScreen != null) reset();
            return;
        }

        if (c.currentScreen != lastScreen) {
            reset();
            lastScreen = c.currentScreen;
            word = m.group(1).trim();
        }
        ticks++;

        if (!decided && ticks >= 6) decide(c, hs);

        if (apiRunning && apiDone) {
            apiRunning = false;
            if (apiErr != null) {
                say(c, "Yapay zekâ hatası: " + apiErr);
            } else if (apiIdx >= 1 && apiIdx <= apiSlots.size()) {
                clickSlot = apiSlots.get(apiIdx - 1);
                say(c, word + " | yapay zekâ " + apiIdx + ". kafayı seçti (slot " + clickSlot + ")");
                clickAt = ticks + 3 + RND.nextInt(6);
            } else {
                say(c, "Yapay zekâ cevabı anlaşılamadı");
            }
        }

        if (clickAt >= 0 && ticks >= clickAt) {
            clickAt = -1;
            String k = keys.get(clickSlot);
            Slot s = hs.getScreenHandler().slots.get(clickSlot);
            if (k != null && s.hasStack() && k.equals(keyOf(s.getStack()))) {
                selfClick = true;
                try {
                    c.interactionManager.clickSlot(hs.getScreenHandler().syncId, clickSlot, 0, SlotActionType.PICKUP, c.player);
                } finally {
                    selfClick = false;
                }
                say(c, "Tıkladım: slot " + clickSlot);
                recast = 30;
            } else {
                say(c, "Kafa yer değiştirdi, tıklamadım");
            }
        }
    }

    private static void buildKeys(MinecraftClient c, HandledScreen<?> hs) {
        keys.clear();
        for (Slot s : hs.getScreenHandler().slots) {
            if (s.inventory == c.player.getInventory()) continue;
            String k = keyOf(s.getStack());
            if (k != null) keys.put(s.id, k);
        }
        int good = 0;
        for (String k : keys.values()) if (!k.startsWith("x:")) good++;
        if (good >= 2) keys.values().removeIf(k -> k.startsWith("x:"));
    }

    private static boolean distinguishable() {
        return new HashSet<>(keys.values()).size() >= 2;
    }

    private static void decide(MinecraftClient c, HandledScreen<?> hs) {
        buildKeys(c, hs);
        if (keys.size() < 2) {
            if (ticks > 60) {
                decided = true;
                say(c, word + ": menüde kafa bulamadım (" + keys.size() + " eşya)");
            }
            return;
        }
        decided = true;

        // 1) Kafanın içindeki yazılara bak (ad, açıklama, gizli veri, doku bilgisi)
        Map<Integer, String> hay = new HashMap<>();
        List<Integer> hits = new ArrayList<>();
        List<String> terms = HeadWords.terms(word);
        for (Map.Entry<Integer, String> e : keys.entrySet()) {
            String h = haystack(c, hs.getScreenHandler().slots.get(e.getKey()).getStack());
            hay.put(e.getKey(), h);
            if (HeadWords.matches(h, terms)) hits.add(e.getKey());
        }

        Integer pick = null;
        String how = "";
        if (hits.size() == 1) {
            pick = hits.get(0);
            how = "yazıdan";
        } else if (distinguishable()) {
            // 2) Daha önce görülenlerden / eleme
            pick = BRAIN.choose(word, keys);
            BRAIN.save(file);
            how = "kayıttan";
        }

        if (pick == null && startApi(c, hs)) return;

        if (pick == null) {
            say(c, word + " | " + keys.size() + " kafa | " + (hits.size() > 1 ? "birden fazla eşleşme" : "eşleşme yok")
                + " | tıklamadım. Kafaların içeriği:");
            for (Map.Entry<Integer, String> e : hay.entrySet()) {
                String h = e.getValue().replaceAll("\\s+", " ").trim();
                say(c, "slot " + e.getKey() + ": " + (h.length() > 70 ? h.substring(0, 70) : h));
            }
            return;
        }
        say(c, word + " | " + how + " buldum, slot " + pick);
        clickSlot = pick;
        clickAt = ticks + 10 + RND.nextInt(15);
    }

    private static String[] readConfig() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("afkkafa-anahtar.txt");
            if (!Files.exists(p)) return null;
            String key = null, model = null;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (key == null) key = t;
                else if (model == null) model = t;
            }
            if (key == null) return null;
            return new String[]{key, model == null ? ClaudeVision.defaultModel(key) : model};
        } catch (Exception e) {
            return null;
        }
    }

    private static String textureUrl(ItemStack st) {
        NbtCompound nbt = st.getNbt();
        if (nbt == null || !nbt.contains("SkullOwner", NbtElement.COMPOUND_TYPE)) return null;
        NbtCompound owner = nbt.getCompound("SkullOwner");
        if (!owner.contains("Properties", NbtElement.COMPOUND_TYPE)) return null;
        NbtCompound props = owner.getCompound("Properties");
        if (!props.contains("textures", NbtElement.LIST_TYPE)) return null;
        NbtList list = props.getList("textures", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(list.getCompound(0).getString("Value")), StandardCharsets.UTF_8);
            Matcher m = TEX_URL.matcher(json);
            if (m.find()) return m.group(1).replace("http://", "https://");
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean startApi(MinecraftClient c, HandledScreen<?> hs) {
        String[] cfg = readConfig();
        if (cfg == null) return false;
        List<Integer> slots = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (Integer id : new TreeMap<>(keys).keySet()) {
            String u = textureUrl(hs.getScreenHandler().slots.get(id).getStack());
            if (u != null) {
                slots.add(id);
                urls.add(u);
            }
        }
        if (slots.size() < 2) return false;

        apiSlots = slots;
        apiRunning = true;
        apiDone = false;
        apiErr = null;
        apiIdx = -1;
        final int gen = ++apiGen;
        final String w = word;
        final List<String> terms = HeadWords.terms(word);
        final String key = cfg[0];
        final String model = cfg[1];
        Thread t = new Thread(() -> {
            int idx = -1;
            String err = null;
            try {
                idx = ClaudeVision.ask(key, model, w, terms, urls);
            } catch (Throwable ex) {
                err = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            }
            if (gen == apiGen) {
                apiIdx = idx;
                apiErr = err;
                apiDone = true;
            }
        }, "afkkafa-api");
        t.setDaemon(true);
        t.start();
        say(c, w + " | yazıdan bulamadım, yapay zekâya soruyorum (" + slots.size() + " kafa)...");
        return true;
    }

    private static boolean hasBobber(MinecraftClient c) {
        if (c.world == null) return false;
        for (Entity e : c.world.getEntities()) {
            if (e instanceof FishingBobberEntity b && b.getPlayerOwner() == c.player) return true;
        }
        return false;
    }

    /** Kafanin icindeki tum yazilar: ad, aciklama, gizli veriler, doku bilgisi (normalize). */
    private static String haystack(MinecraftClient c, ItemStack st) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(st.getName().getString()).append(' ');
            for (Text t : st.getTooltip(c.player, TooltipContext.Default.BASIC)) sb.append(t.getString()).append(' ');
        } catch (Exception ignored) {
        }
        NbtCompound nbt = st.getNbt();
        if (nbt != null) {
            NbtCompound copy = nbt.copy();
            if (copy.contains("SkullOwner", NbtElement.COMPOUND_TYPE)) {
                NbtCompound owner = copy.getCompound("SkullOwner");
                if (owner.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound props = owner.getCompound("Properties");
                    if (props.contains("textures", NbtElement.LIST_TYPE)) {
                        NbtList list = props.getList("textures", NbtElement.COMPOUND_TYPE);
                        if (!list.isEmpty()) {
                            try {
                                String value = list.getCompound(0).getString("Value");
                                sb.append(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).append(' ');
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    owner.remove("Properties");
                }
            }
            sb.append(copy.toString());
        }
        return norm(sb.toString());
    }

    /** Oyuncu bir slota tikladiginda (mixin cagirir): dogru cevap sayilip ogrenilir. */
    public static void onUserClick(int slotId) {
        if (selfClick || word == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !(c.currentScreen instanceof HandledScreen<?> hs)) return;
        if (keys.isEmpty()) buildKeys(c, hs);
        if (!distinguishable()) return;
        String k = keys.get(slotId);
        if (k == null) return;
        BRAIN.learn(word, k);
        BRAIN.save(file);
        say(c, "Öğrendim: " + word + " = " + shortKey(k));
    }

    private static String keyOf(ItemStack st) {
        if (st.isEmpty()) return null;
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        NbtCompound nbt = st.getNbt();
        if (nbt == null) return "x:" + id;

        if (nbt.contains("SkullOwner", NbtElement.COMPOUND_TYPE)) {
            NbtCompound owner = nbt.getCompound("SkullOwner");
            if (owner.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                NbtCompound props = owner.getCompound("Properties");
                if (props.contains("textures", NbtElement.LIST_TYPE)) {
                    NbtList list = props.getList("textures", NbtElement.COMPOUND_TYPE);
                    if (!list.isEmpty()) {
                        String value = list.getCompound(0).getString("Value");
                        try {
                            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                            Matcher m = TEX_URL.matcher(json);
                            if (m.find()) {
                                String u = m.group(1);
                                int i = u.lastIndexOf('/');
                                return "u:" + (i >= 0 ? u.substring(i + 1) : u);
                            }
                        } catch (Exception ignored) {
                        }
                        return "v:" + Integer.toHexString(value.hashCode());
                    }
                }
            }
            if (owner.contains("Id", NbtElement.INT_ARRAY_TYPE)) {
                return "i:" + Arrays.toString(owner.getIntArray("Id"));
            }
            if (owner.contains("Name", NbtElement.STRING_TYPE)) {
                return "n:" + owner.getString("Name");
            }
        } else if (nbt.contains("SkullOwner", NbtElement.STRING_TYPE)) {
            return "n:" + nbt.getString("SkullOwner");
        }
        if (nbt.contains("CustomModelData", NbtElement.INT_TYPE)) {
            return "m:" + nbt.getInt("CustomModelData");
        }
        return "x:" + id;
    }
}
