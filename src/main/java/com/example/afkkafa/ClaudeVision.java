package com.example.afkkafa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kafa resimlerini Claude'a gosterip "hangisi X?" diye sorar. Minecraft'a bagimli degil. */
final class ClaudeVision {

    static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    static final String DEFAULT_GEMINI = "gemini-flash-latest";

    static boolean isClaudeKey(String key) {
        return key.startsWith("sk-ant");
    }

    static String defaultModel(String key) {
        return isClaudeKey(key) ? DEFAULT_MODEL : DEFAULT_GEMINI;
    }

    static byte[] download(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "afkkafa");
        try (InputStream is = con.getInputStream()) {
            return is.readAllBytes();
        }
    }

    /** Skin PNG'sinden kafanin on, sag, arka, sol ve ust yuzunu (ust katman dahil) yan yana buyutulmus PNG yapar. */
    static byte[] faceStrip(byte[] skinPng) throws IOException {
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinPng));
        if (skin == null) throw new IOException("doku okunamadi");
        int[][] base = {{8, 8}, {0, 8}, {24, 8}, {16, 8}, {8, 0}};
        int[][] hat = {{40, 8}, {32, 8}, {56, 8}, {48, 8}, {40, 0}};
        final int S = 6, GAP = 4;
        BufferedImage out = new BufferedImage(base.length * (8 * S + GAP), 8 * S, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < out.getWidth(); x++) for (int y = 0; y < out.getHeight(); y++) out.setRGB(x, y, 0x808080);

        for (int f = 0; f < base.length; f++) {
            int ox = f * (8 * S + GAP);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int c = pixel(skin, base[f][0] + x, base[f][1] + y);
                    int h = pixel(skin, hat[f][0] + x, hat[f][1] + y);
                    int rgb = ((h >>> 24) > 20) ? (h & 0xFFFFFF) : ((c >>> 24) > 20 ? (c & 0xFFFFFF) : 0x808080);
                    for (int dy = 0; dy < S; dy++) for (int dx = 0; dx < S; dx++) out.setRGB(ox + x * S + dx, y * S + dy, rgb);
                }
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    /** Tum kafa dokularini ayni anda indirip serit resmine cevirir (sira korunur). */
    static List<byte[]> faceStrips(List<String> urls) throws IOException {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, urls.size()));
        try {
            List<java.util.concurrent.Callable<byte[]>> jobs = new java.util.ArrayList<>();
            for (String u : urls) jobs.add(() -> faceStrip(download(u)));
            List<byte[]> out = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<byte[]> f : pool.invokeAll(jobs)) {
                try {
                    out.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IOException("doku indirilemedi: " + e.getCause());
                }
            }
            return out;
        } catch (InterruptedException e) {
            throw new IOException("kesildi");
        } finally {
            pool.shutdownNow();
        }
    }

    private static int pixel(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return 0;
        return img.getRGB(x, y);
    }

    private static JsonObject text(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("text", t);
        return o;
    }

    /** Anahtar sk-ant ile basliyorsa Claude, degilse Google Gemini (ucretsiz kota) kullanilir. */
    static int ask(String apiKey, String model, String word, List<String> terms, List<String> textureUrls) throws IOException {
        if (isClaudeKey(apiKey)) return askClaude(apiKey, model, word, terms, textureUrls);
        return askGemini(apiKey, model, word, terms, textureUrls);
    }

    private static int firstNumber(String answer) {
        Matcher m = Pattern.compile("\\d+").matcher(answer);
        return m.find() ? Integer.parseInt(m.group()) : -1;
    }

    static final class HttpError extends IOException {
        final int code;

        HttpError(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private static String postJson(String url, String[][] headers, String body) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(8000);
        con.setReadTimeout(40000);
        con.setDoOutput(true);
        con.setRequestProperty("content-type", "application/json");
        for (String[] h : headers) con.setRequestProperty(h[0], h[1]);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = con.getResponseCode();
        InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String resp = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code != 200) {
            throw new HttpError(code, "HTTP " + code + " " + (resp.length() > 160 ? resp.substring(0, 160) : resp));
        }
        return resp;
    }

    private static int askGemini(String apiKey, String model, String word, List<String> terms, List<String> textureUrls) throws IOException {
        String names = String.join(" / ", terms);
        JsonArray parts = new JsonArray();
        JsonObject first = new JsonObject();
        first.addProperty("text", "Minecraft server asks the player to click the head of: \"" + word + "\" (also: " + names + "). "
            + "Below are custom player-head skins. Each picture shows one head: front, right, back, left and top faces side by side. "
            + "Pixel art, so think about what the head depicts overall (animal, fruit, plant, object).");
        parts.add(first);
        List<byte[]> strips = faceStrips(textureUrls);
        for (int i = 0; i < textureUrls.size(); i++) {
            byte[] png = strips.get(i);
            JsonObject label = new JsonObject();
            label.addProperty("text", "Head " + (i + 1) + ":");
            parts.add(label);
            JsonObject inline = new JsonObject();
            inline.addProperty("mime_type", "image/png");
            inline.addProperty("data", Base64.getEncoder().encodeToString(png));
            JsonObject part = new JsonObject();
            part.add("inline_data", inline);
            parts.add(part);
        }
        JsonObject q = new JsonObject();
        q.addProperty("text", "Which head number depicts \"" + word + "\" (" + names + ")? Reply with only the number.");
        parts.add(q);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject gen = new JsonObject();
        gen.addProperty("maxOutputTokens", 1024);
        gen.addProperty("temperature", 0);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", gen);

        String bodyText = body.toString();
        List<String> models = new java.util.ArrayList<>();
        models.add(model);
        for (String m : new String[]{"gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.0-flash"}) {
            if (!models.contains(m)) models.add(m);
        }

        String resp = null;
        IOException last = null;
        for (int attempt = 0; attempt < 8 && resp == null; attempt++) {
            String mdl = models.get(attempt % models.size());
            try {
                resp = postJson("https://generativelanguage.googleapis.com/v1beta/models/" + mdl + ":generateContent",
                    new String[][]{{"x-goog-api-key", apiKey}}, bodyText);
            } catch (HttpError e) {
                last = e;
                // anahtar sorunu: bosuna deneme
                if (e.code == 401 || e.code == 403) throw e;
                // asiri yuk (503), kota (429), sunucu hatasi, model yok (404/400): siradaki modele gec
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ie) {
                    throw e;
                }
            }
        }
        if (resp == null) throw last != null ? last : new IOException("cevap alinamadi");

        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
        JsonArray cands = root.getAsJsonArray("candidates");
        if (cands == null || cands.size() == 0) {
            throw new IOException("cevap bos: " + (resp.length() > 160 ? resp.substring(0, 160) : resp));
        }
        JsonObject cont = cands.get(0).getAsJsonObject().getAsJsonObject("content");
        StringBuilder sb = new StringBuilder();
        if (cont != null && cont.getAsJsonArray("parts") != null) {
            for (int i = 0; i < cont.getAsJsonArray("parts").size(); i++) {
                JsonObject po = cont.getAsJsonArray("parts").get(i).getAsJsonObject();
                if (po.has("text")) sb.append(po.get("text").getAsString()).append(' ');
            }
        }
        return firstNumber(sb.toString());
    }

    /** 1'den baslayan kafa numarasini dondurur, anlasilamazsa -1. */
    private static int askClaude(String apiKey, String model, String word, List<String> terms, List<String> textureUrls) throws IOException {
        JsonArray content = new JsonArray();
        String names = String.join(" / ", terms);
        content.add(text("Minecraft server asks the player to click the head of: \"" + word + "\" (also: " + names + "). "
            + "Below are custom player-head skins. Each picture shows one head: front, right, back, left and top faces side by side. "
            + "Pixel art, so think about what the head depicts overall (animal, fruit, plant, object)."));
        List<byte[]> strips = faceStrips(textureUrls);
        for (int i = 0; i < textureUrls.size(); i++) {
            byte[] png = strips.get(i);
            content.add(text("Head " + (i + 1) + ":"));
            JsonObject src = new JsonObject();
            src.addProperty("type", "base64");
            src.addProperty("media_type", "image/png");
            src.addProperty("data", Base64.getEncoder().encodeToString(png));
            JsonObject img = new JsonObject();
            img.addProperty("type", "image");
            img.add("source", src);
            content.add(img);
        }
        content.add(text("Which head number depicts \"" + word + "\" (" + names + ")? Reply with only the number."));

        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(msg);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 20);
        body.add("messages", messages);

        String resp = postJson("https://api.anthropic.com/v1/messages",
            new String[][]{{"x-api-key", apiKey}, {"anthropic-version", "2023-06-01"}}, body.toString());
        String answer = JsonParser.parseString(resp).getAsJsonObject()
            .getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        return firstNumber(answer);
    }
}
