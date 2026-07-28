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
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b[A-Z]{1,6}\\b");

        private static final Set<String> MARKET_INTENT_KEYWORDS = Set.of(
            "推荐", "调仓", "配置", "买", "卖", "加仓", "减仓", "建仓", "选股", "机会", "市场", "行情", "分析", "股票", "投资", "股价", "allocation", "rebalance", "buy", "sell"
    );

    private static final Set<String> PERFORMANCE_INTENT_KEYWORDS = Set.of(
            "收益曲线", "曲线", "走势图", "趋势", "预测", "回撤", "盈亏", "performance", "forecast", "trend", "drawdown", "chart", "line"
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
        messages.add(new LlmGateway.Message("system", "投资组合上下文（仅供内部参考）：\n" + contextPayload.contextJson()));

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
                warnings.add("未读取到持仓记录，可能是账户为空或底层表结构发生变化。");
            }

            BigDecimal cashBalance = aiAssistantRepository.findPortfolioCashBalance(portfolioId)
                .map(v -> v.setScale(2, RoundingMode.HALF_UP))
                .orElse(ZERO_MONEY);
            if (cashBalance.compareTo(ZERO_MONEY) == 0) {
                warnings.add("现金余额未能稳定识别，当前按 0 处理（不影响持仓推理）。");
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
                warnings.add("未读取到组合快照序列，预测图将基于当前持仓与价格进行保守推断。");
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
                "该字段可来自 Yahoo 官方行情或系统内部可信行情源。data_status=official_realtime/trusted_internal_feed 均可用于定量分析。"
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
            warnings.add("当前未获取到可用行情源（官方或内部），禁止输出具体收益率数值预测。");
            }
        } else {
            warnings.add("本轮未识别到可用于外部市场数据拉取的标的代码，已仅基于组合上下文分析。");
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
                 5) 当前问题需要可视化：请在自然语言分析后输出图表 JSON。
               - 先输出一行标记 <<AI_CHARTS_JSON>>
               - 再输出一个合法 JSON 对象
                 - 仅输出与问题相关的图表字段，不相关字段不要输出
                 - 图表数量由问题复杂度决定（可为 1~3 张）
                 6) 可选图表字段（按需返回）：
               {
                 \"current_portfolio_forecast\": {\"title\":\"当前持仓预测盈亏\",\"dates\":[...],\"values\":[...]},
                 \"recommended_portfolio_forecast\": {\"title\":\"建议调仓后预测盈亏\",\"dates\":[...],\"values\":[...]},
                 \"recommended_allocation\": {\"title\":\"建议持仓占比\",\"labels\":[...],\"values\":[...]},
                 \"expected_drivers\": {\"title\":\"收益驱动因子\",\"labels\":[...],\"values\":[...]}
               }
            7) 数组长度必须匹配，数值必须是数字，推荐占比之和尽量接近 100。
            8) 对“预测/评估/趋势/曲线”问题，至少返回一张折线相关图表。
                9) 预测图时间轴必须严格使用上下文 time_anchor 中的 forecast_window_start 到 forecast_window_end，
                    并按月递进（例如 2026-08, 2026-09 ... 2027-08），禁止使用历史过时年份。
                10) 若 official_market_intelligence_quality.usable_count=0，禁止输出具体数值预测图，只能给出定性判断。
                """
            : """
            5) 当前问题不需要可视化：只输出自然语言分析，禁止输出 <<AI_CHARTS_JSON>> 或任意 JSON。
            """;

        String mandatoryInstruction = chartMandatory
            ? "10) 本次属于预测或评估问题：图表是必需项，禁止只返回纯文字。"
            : "";

        String languageInstruction = "zh".equals(responseLanguage)
            ? "9) 使用中文回答，除股票代码、财务缩写或必要英文术语外，不要切换到英文。"
            : "9) Reply in English. Use Chinese only for stock symbols, financial abbreviations, or when quoting source terms. ";

        String lengthInstruction = "zh".equals(responseLanguage)
            ? "5) 文字必须精炼：总长度控制在 220-420 个汉字，分 3-5 个短段落，每段不超过 3 句。"
            : "5) Keep the answer concise: about 160-320 English words, split into 3-5 short paragraphs, no more than 3 sentences each. ";

        String dataAvailabilityInstruction = officialDataUsable
            ? "10) 上下文 official_data_usable=true 时，禁止输出“官方数据暂不可得”或同义句。"
            : "10) 仅当 official_data_usable=false 时，才允许说明“官方数据暂不可得，无法给出该项定量预测”，且最多出现 1 次。";

        String antiBoilerplateInstruction = "11) 风险提示必须与本轮问题和上下文数据直接相关，禁止每次复用固定模板（如回测、汇率、利率对冲等）";

        return """
            你是一名专业的投资组合分析助手，擅长基于结构化持仓与市场数据输出可执行建议。
            你会收到系统注入的投资组合上下文 JSON（用户不可见），并据此回答用户问题。
            当前系统日期：%s。
            回答规范：
            1) 先给结论，再解释依据；
            2) 涉及买卖建议时，明确标的、方向、建议仓位或比例区间；
                3) 同时给出风险提示和止损/再平衡条件；
            4) 禁止编造上下文中不存在的持仓数据。
            %s
            6) 优先用短句 + 图表表达，不要输出大段复述。
            7) 若上下文包含 official_market_intelligence，必须优先参考该外部市场数据，再结合组合数据给结论。
            8) 严禁虚构实时价格、历史区间、收益率。只能使用上下文里出现的数字；若数据缺失必须明确写“官方数据暂不可得，无法给出该项定量预测”。
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
        if (symbols.size() <= 3) {
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
