package dev.geolens.audit;

/** Bilinen AI tarayıcı/bot bilgisi — Go {@code audit.AICrawler} portu. */
public record AICrawler(String userAgent, String name, String source) {

    /** Bilinen AI crawler listesi (Go ile birebir). Source: chatgpt, gemini, perplexity. */
    public static final java.util.List<AICrawler> KNOWN = java.util.List.of(
            new AICrawler("ChatGPT-User/1.0", "ChatGPT (OpenAI)", "chatgpt"),
            new AICrawler("Google-Extended/1.0", "Google-Extended (Gemini)", "gemini"),
            new AICrawler("PerplexityBot/1.0", "PerplexityBot", "perplexity"),
            new AICrawler("CCBot/1.0", "CCBot (Common Crawl)", "perplexity"),
            new AICrawler("Claude-Web/1.0", "Claude (Anthropic)", "chatgpt"),
            new AICrawler("Applebot-Extended/1.0", "Applebot-Extended", "chatgpt"));
}