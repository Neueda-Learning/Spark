package com.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.dto.AiChatRequest;
import com.portfolio.dto.AiChatResponse;
import com.portfolio.repository.AiAssistantRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 1200;
    private static final String CHART_MARKER = "<<AI_CHARTS_JSON>>";
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b[A-Z]{1,6}(?:\\.[A-Z]{1,3})?\\b");

            private static final Set<String> MARKET_INTENT_KEYWORDS = Set.of(
                "推荐", "调仓", "配置", "买", "卖", "加仓", "减仓", "建仓", "选股", "机会", "市场", "行情", "分析", "股票", "投资", "股价",
                "allocation", "rebalance", "rebalancing", "portfolio", "position", "positions", "weight", "weights", "exposure", "risk-on", "risk-off",
                "buy", "sell", "add", "trim", "reduce", "increase", "entry", "exit", "rotate", "switch", "opportunity", "opportunities", "valuation",
                "undervalued", "overvalued", "sector", "sectors", "stock", "stocks", "equity", "equities", "market", "price", "prices"
            );

    private static final Set<String> PERFORMANCE_INTENT_KEYWORDS = Set.of(
                "收益曲线", "曲线", "走势图", "趋势", "预测", "回撤", "盈亏",
                "performance", "return", "returns", "pnl", "profit", "loss", "gain", "forecast", "projection", "scenario", "trend", "trajectory",
                "drawdown", "volatility", "risk", "sharpe", "alpha", "beta", "momentum", "backtest", "history", "historical", "chart", "charts", "graph", "line", "curve"
    );

    private final AiAssistantRepository aiAssistantRepository;
    private final PriceService priceService;
    private final OfficialMarketDataService officialMarketDataService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AiAssistantServiceImpl(AiAssistantRepository aiAssistantRepository,
                                  PriceService priceService,
                                  OfficialMarketDataService officialMarketDataService,
                                  LlmGateway llmGateway,
                                  ObjectMapper objectMapper) {
        this.aiAssistantRepository = aiAssistantRepository;
        this.priceService = priceService;
        this.officialMarketDataService = officialMarketDataService;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(Long portfolioId, AiChatRequest request) {
        List<LlmGateway.Message> messages = buildMessages(portfolioId, request);
        String reply = llmGateway.chat(messages);
        return new AiChatResponse(reply);
    }

    @Override
    public void streamChat(Long portfolioId, AiChatRequest request, Consumer<String> chunkConsumer) {
        llmGateway.streamChat(buildMessages(portfolioId, request), chunkConsumer);
    }

    private List<LlmGateway.Message> buildMessages(Long portfolioId, AiChatRequest request) {
        ContextScope scope = detectContextScope(request.message());
        String responseLanguage = detectResponseLanguage(request.message());
        ContextPayload contextPayload = buildContextJson(portfolioId, scope, request.message());
        LocalDate today = LocalDate.now();

        List<LlmGateway.Message> messages = new ArrayList<>();
        messages.add(new LlmGateway.Message(
            "system",
            buildSystemPrompt(
                scope.shouldGenerateCharts(),
                scope.chartMandatory(),
                today,
                responseLanguage,
                contextPayload.officialDataUsable()
            )
        ));
        messages.add(new LlmGateway.Message("system", "Portfolio context (internal reference only):\n" + contextPayload.contextJson()));

        if (request.history() != null) {
            int start = Math.max(0, request.history().size() - MAX_HISTORY_MESSAGES);
            for (int i = start; i < request.history().size(); i++) {
                AiChatRequest.ChatMessage h = request.history().get(i);
                String content = shrinkText(stripChartsPayload(h.content()));
                if (!content.isBlank()) {
                    messages.add(new LlmGateway.Message(normalizeRole(h.role()), content));
                }
            }
        }

        messages.add(new LlmGateway.Message("user", request.message()));
        return messages;
    }

    private ContextPayload buildContextJson(Long portfolioId, ContextScope scope, String question) {
        LocalDate today = LocalDate.now();
        LocalDate forecastStart = today.withDayOfMonth(1).plusMonths(1);
        LocalDate forecastEnd = forecastStart.plusMonths(12);

        List<String> warnings = new ArrayList<>();
        boolean officialDataUsable = true;

        List<Map<String, Object>> heldStocks = aiAssistantRepository.findHeldStocks(portfolioId)
                .stream()
                .map(row -> {
                    BigDecimal latestPrice = priceService.getCurrentPrice(row.symbol());
                    BigDecimal marketValue = latestPrice.multiply(row.quantity());
                    BigDecimal costValue = row.averageCost().multiply(row.quantity());
                    BigDecimal profitRate = BigDecimal.ZERO;
                    if (costValue.compareTo(BigDecimal.ZERO) > 0) {
                        profitRate = marketValue.subtract(costValue)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(costValue, 2, RoundingMode.HALF_UP);
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stock_id", row.stockId());
                    item.put("symbol", row.symbol());
                    item.put("name", row.name());
                    item.put("asset_type", row.assetType());
                    item.put("quantity", row.quantity().setScale(4, RoundingMode.HALF_UP));
                    item.put("average_cost", row.averageCost().setScale(4, RoundingMode.HALF_UP));
                    item.put("latest_price", latestPrice.setScale(2, RoundingMode.HALF_UP));
                    item.put("profit_rate", profitRate);
                    return item;
                })
                .toList();

        List<AiAssistantRepository.AvailableStockRow> availableStocks = aiAssistantRepository.findAvailableStocks(portfolioId);

            if (heldStocks.isEmpty()) {
                warnings.add("No holdings were read. The account may be empty or underlying table structures may have changed.");
            }

            BigDecimal cashBalance = aiAssistantRepository.findPortfolioCashBalance(portfolioId)
                .map(v -> v.setScale(2, RoundingMode.HALF_UP))
                .orElse(ZERO_MONEY);
            if (cashBalance.compareTo(ZERO_MONEY) == 0) {
                warnings.add("Cash balance could not be reliably identified and is treated as 0 for this round.");
            }

        BigDecimal totalMarketValue = heldStocks.stream()
                .map(item -> ((BigDecimal) item.get("latest_price")).multiply((BigDecimal) item.get("quantity")))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAsset = totalMarketValue.add(cashBalance).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_asset", totalAsset);
        summary.put("total_market_value", totalMarketValue);
            summary.put("cash_balance", cashBalance);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context_scope", Map.of(
                "include_market_candidates", scope.includeMarketCandidates(),
                "include_performance_series", scope.includePerformanceSeries(),
                "should_generate_charts", scope.shouldGenerateCharts(),
                "chart_mandatory", scope.chartMandatory()
        ));
        payload.put("time_anchor", Map.of(
            "today", today.toString(),
            "forecast_window_start", forecastStart.toString(),
            "forecast_window_end", forecastEnd.toString(),
            "forecast_window_label", "Use monthly points from start to end inclusive"
        ));
        payload.put("held_stocks", heldStocks);
        payload.put("portfolio_summary", summary);

        Set<String> knownSymbols = heldStocks.stream()
            .map(item -> String.valueOf(item.get("symbol")))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (scope.includeMarketCandidates()) {
            List<Map<String, Object>> marketCandidates = availableStocks
                    .stream()
                    .limit(12)
                    .map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("stock_id", row.stockId());
                        item.put("symbol", row.symbol());
                        item.put("name", row.name());
                        item.put("asset_type", row.assetType());
                        item.put("latest_price", priceService.getCurrentPrice(row.symbol()).setScale(2, RoundingMode.HALF_UP));
                        item.put("change_percent", priceService.getChangePercent(row.symbol()).setScale(2, RoundingMode.HALF_UP));
                        return item;
                    })
                    .toList();
            payload.put("market_candidates", marketCandidates);
            knownSymbols.addAll(marketCandidates.stream().map(item -> String.valueOf(item.get("symbol"))).toList());
        }

        if (scope.includePerformanceSeries()) {
            List<AiAssistantRepository.PerformancePointRow> snapshots = aiAssistantRepository.findPerformanceSeries(portfolioId, 14);
            if (snapshots.isEmpty()) {
                warnings.add("No portfolio snapshot series was found. Forecast charts will be conservatively inferred from current holdings and prices.");
            }

            List<Map<String, Object>> performanceSeries = snapshots.stream()
                    .map(snap -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("date", DATE_FORMATTER.format(snap.date()));
                        item.put("total_value", snap.totalValue().setScale(2, RoundingMode.HALF_UP));
                        item.put("cash_balance", snap.cashBalance().setScale(2, RoundingMode.HALF_UP));
                        return item;
                    })
                    .toList();
            payload.put("performance_series", performanceSeries);
        }

        List<String> symbolsForOfficialData = selectSymbolsForOfficialData(question, heldStocks, availableStocks, knownSymbols);
        if (!symbolsForOfficialData.isEmpty()) {
            List<Map<String, Object>> marketInsights = officialMarketDataService.buildInsights(symbolsForOfficialData);
            payload.put("official_market_intelligence", marketInsights);

            long officialRealtimeCount = marketInsights.stream()
                .filter(item -> "official_realtime".equals(String.valueOf(item.get("data_status"))))
                .count();
            long trustedInternalCount = marketInsights.stream()
                .filter(item -> "trusted_internal_feed".equals(String.valueOf(item.get("data_status"))))
                .count();
            long availableCount = officialRealtimeCount + trustedInternalCount;
            long unavailableCount = marketInsights.size() - availableCount;

            payload.put("official_market_intelligence_note",
                "This field may come from Yahoo official quotes or the internal trusted market feed. data_status=official_realtime/trusted_internal_feed can both be used for quantitative analysis."
            );
            payload.put("official_market_intelligence_quality", Map.of(
                "requested", marketInsights.size(),
                "official_realtime_count", officialRealtimeCount,
                "trusted_internal_feed_count", trustedInternalCount,
                "usable_count", availableCount,
                "official_unavailable_count", unavailableCount
            ));

            officialDataUsable = availableCount > 0;

            if (availableCount == 0) {
            warnings.add("No usable market source is currently available (official or internal), so quantitative return-rate forecasts are not allowed.");
            }
        } else {
            warnings.add("No symbols were identified for external market data in this round; analysis is based only on portfolio context.");
        }

        payload.put("context_quality", Map.of(
                "mode", "schema-adaptive",
                "warning_count", warnings.size()
        ));
        payload.put("official_data_usable", officialDataUsable);
        payload.put("response_language", detectResponseLanguage(question));
        if (!warnings.isEmpty()) {
            payload.put("context_warnings", warnings);
        }

        return new ContextPayload(toPrettyJson(payload), officialDataUsable);
    }

        private String buildSystemPrompt(boolean shouldGenerateCharts,
                                         boolean chartMandatory,
                                         LocalDate today,
                                         String responseLanguage,
                                         boolean officialDataUsable) {
        String chartInstruction = shouldGenerateCharts
            ? """
                 5) Visualization is required for this question: after the natural-language analysis, output chart JSON.
               - First output one marker line: <<AI_CHARTS_JSON>>
               - Then output one valid JSON object
                 - Return only chart fields relevant to the question
                 - Number of charts should depend on complexity (1 to 3)
                 6) Optional chart fields (return on demand):
               {
                 \"current_portfolio_forecast\": {\"title\":\"Current Portfolio Forecast Return\",\"dates\":[...],\"values\":[...]},
                 \"recommended_portfolio_forecast\": {\"title\":\"Recommended Portfolio Forecast Return\",\"dates\":[...],\"values\":[...]},
                 \"recommended_allocation\": {\"title\":\"Recommended Allocation\",\"labels\":[...],\"values\":[...]},
                 \"expected_drivers\": {\"title\":\"Expected Return Drivers\",\"labels\":[...],\"values\":[...]}
               }
            7) Array lengths must match; all values must be numeric; allocation percentages should sum close to 100.
            8) For forecast/evaluation/trend/curve questions, return at least one line-chart-related series.
            9) Forecast chart timeline must strictly follow time_anchor.forecast_window_start to forecast_window_end
               with monthly increments (for example 2026-08, 2026-09 ... 2027-08). Do not use outdated years.
            10) If official_market_intelligence_quality.usable_count=0, do not output quantitative forecast charts.
                Provide qualitative judgment only.
            11) Language consistency rule: chart JSON textual fields (such as title and labels) must use the same
                language as the user question and the main response text. Do not mix languages in one answer.
                """
            : """
            5) Visualization is not required for this question: output natural-language analysis only.
               Do not output <<AI_CHARTS_JSON>> or any JSON.
            """;

        String mandatoryInstruction = chartMandatory
            ? "10) This is a forecast or evaluation question: charts are required; text-only output is not allowed."
            : "";

        String languageInstruction = "zh".equals(responseLanguage)
            ? "9) Reply in Chinese. Keep stock symbols and standard financial abbreviations as-is."
            : "9) Reply in English. Use Chinese only when quoting user-provided Chinese source terms.";

        String lengthInstruction = "zh".equals(responseLanguage)
            ? "5) Keep the response concise: about 220-420 Chinese characters, 3-5 short paragraphs, max 3 sentences each."
            : "5) Keep the response concise: about 160-320 English words, 3-5 short paragraphs, max 3 sentences each.";

        String dataAvailabilityInstruction = officialDataUsable
            ? "10) When official_data_usable=true, do not state that official data is unavailable."
            : "10) Only when official_data_usable=false, you may state that official data is unavailable and "
                + "quantitative prediction cannot be provided, and this statement can appear at most once.";

        String antiBoilerplateInstruction = "11) Risk notes must be specific to this question and context. "
            + "Do not repeat fixed boilerplate templates across every answer.";

        return """
            You are a professional portfolio analysis assistant.
            You will receive an internally injected portfolio context JSON (not visible to the user).
            Use that context to answer the user question.
            System date: %s.
            Response rules:
            1) Give the conclusion first, then explain the evidence.
            2) For buy/sell suggestions, specify symbol, direction, and suggested position/range.
            3) Include risk notes and stop-loss/rebalancing conditions when relevant.
            4) Never fabricate holdings or numbers that are not present in context.
            %s
            6) Prefer short sentences and charts over long repetitive text.
            7) If official_market_intelligence is present, prioritize it and combine it with portfolio context.
            8) Never invent real-time prices, historical ranges, or returns. Use only numbers from context.
            %s
            %s
            %s
            %s
            %s
            """.formatted(
                today,
                lengthInstruction,
                languageInstruction,
                chartInstruction,
                mandatoryInstruction,
                dataAvailabilityInstruction,
                antiBoilerplateInstruction
            );
        }

    private List<String> selectSymbolsForOfficialData(String question,
                                                      List<Map<String, Object>> heldStocks,
                                                      List<AiAssistantRepository.AvailableStockRow> availableStocks,
                                                      Set<String> knownSymbols) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();

        // Always include top holdings first so portfolio advice has external market context.
        heldStocks.stream()
                .map(item -> String.valueOf(item.get("symbol")))
                .limit(3)
                .forEach(symbols::add);

        String normalizedQuestion = question == null ? "" : question.toUpperCase();
        Matcher matcher = SYMBOL_PATTERN.matcher(normalizedQuestion);
        while (matcher.find()) {
            String token = matcher.group();
            if (knownSymbols.contains(token) || token.contains(".")) {
                symbols.add(token);
            }
        }

        String rawQuestion = question == null ? "" : question;
        addMatchedStocks(rawQuestion, heldStocks, symbols);
        addMatchedAvailableStocks(rawQuestion, availableStocks, symbols);

        // If user asks general market analysis without explicit symbol match,
        // include broader candidates so major symbols like TSLA are still covered.
        if (symbols.size() <= 3 && detectContextScope(question).includeMarketCandidates()) {
            availableStocks.stream()
                    .map(AiAssistantRepository.AvailableStockRow::symbol)
                    .filter(s -> s != null && !s.isBlank())
                    .limit(20)
                    .forEach(symbols::add);
        }

        return symbols.stream().limit(20).toList();
    }

    private void addMatchedStocks(String question,
                                  List<Map<String, Object>> stocks,
                                  Set<String> symbols) {
        for (Map<String, Object> stock : stocks) {
            String symbol = String.valueOf(stock.get("symbol"));
            String name = String.valueOf(stock.get("name"));
            if (matchesQuestion(question, symbol, name)) {
                symbols.add(symbol);
            }
        }
    }

    private void addMatchedAvailableStocks(String question,
                                           List<AiAssistantRepository.AvailableStockRow> stocks,
                                           Set<String> symbols) {
        for (AiAssistantRepository.AvailableStockRow stock : stocks) {
            if (matchesQuestion(question, stock.symbol(), stock.name())) {
                symbols.add(stock.symbol());
            }
        }
    }

    private boolean matchesQuestion(String question, String symbol, String name) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String normalizedQuestion = question.toUpperCase();
        if (symbol != null && !symbol.isBlank() && normalizedQuestion.contains(symbol.toUpperCase())) {
            return true;
        }

        return name != null && !name.isBlank() && question.contains(name);
    }

    private ContextScope detectContextScope(String question) {
        String q = question == null ? "" : question.toLowerCase();
        Set<String> matched = new HashSet<>();
        for (String keyword : MARKET_INTENT_KEYWORDS) {
            if (q.contains(keyword)) {
                matched.add("market");
                break;
            }
        }
        for (String keyword : PERFORMANCE_INTENT_KEYWORDS) {
            if (q.contains(keyword)) {
                matched.add("performance");
                break;
            }
        }

        boolean includeMarketCandidates = matched.contains("market");
        boolean includePerformanceSeries = matched.contains("performance");
        boolean chartMandatory = q.contains("预测")
            || q.contains("评估")
            || q.contains("曲线")
            || q.contains("趋势")
            || q.contains("走势")
            || q.contains("forecast")
            || q.contains("evaluate")
            || q.contains("trend");
        boolean shouldGenerateCharts = includePerformanceSeries
            || chartMandatory
                || q.contains("图")
                || q.contains("可视化")
                || q.contains("chart");

        return new ContextScope(includeMarketCandidates, includePerformanceSeries, shouldGenerateCharts, chartMandatory);
    }

    private String stripChartsPayload(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int markerIdx = content.indexOf(CHART_MARKER);
        if (markerIdx < 0) {
            return content;
        }
        return content.substring(0, markerIdx).trim();
    }

    private String shrinkText(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_HISTORY_CONTENT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_HISTORY_CONTENT_LENGTH) + "...";
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        String normalized = role.toLowerCase();
        return switch (normalized) {
            case "system", "assistant", "user" -> normalized;
            default -> "user";
        };
    }

    private String detectResponseLanguage(String question) {
        if (question == null || question.isBlank()) {
            return "zh";
        }

        long cjkCount = question.codePoints()
            .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
            .count();
        long latinCount = question.codePoints()
            .filter(codePoint -> Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN)
            .count();

        if (latinCount > cjkCount && latinCount > 3) {
            return "en";
        }
        return "zh";
    }

    private String toPrettyJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build AI assistant context JSON", e);
        }
    }

    private record ContextScope(
            boolean includeMarketCandidates,
            boolean includePerformanceSeries,
            boolean shouldGenerateCharts,
            boolean chartMandatory
    ) {
    }

        private record ContextPayload(
            String contextJson,
            boolean officialDataUsable
        ) {
        }
}
