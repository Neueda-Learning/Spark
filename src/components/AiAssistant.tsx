import { FormEvent, useMemo, useRef, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { AiChatMessage } from '../types';

type ChartSeries = {
  title?: string;
  labels?: string[];
  values?: number[];
  dates?: string[];
};

type AssistantCharts = {
  current_portfolio_forecast?: ChartSeries;
  recommended_portfolio_forecast?: ChartSeries;
  recommended_allocation?: ChartSeries;
  expected_drivers?: ChartSeries;
};

type AssistantMessage = AiChatMessage & {
  charts?: AssistantCharts | null;
};

const CHART_MARKER = '<<AI_CHARTS_JSON>>';

function splitAssistantPayload(raw: string): { text: string; charts: AssistantCharts | null } {
  const markerIdx = raw.indexOf(CHART_MARKER);
  if (markerIdx < 0) {
    const fallback = tryParseChartsFallback(raw);
    return fallback ?? { text: raw, charts: null };
  }

  const text = raw.slice(0, markerIdx).trim();
  const chartText = raw.slice(markerIdx + CHART_MARKER.length).trim();
  if (!chartText) {
    return { text, charts: null };
  }

  try {
    const parsed = JSON.parse(chartText) as AssistantCharts;
    return { text, charts: parsed };
  } catch {
    return { text, charts: null };
  }
}

function tryParseChartsFallback(raw: string): { text: string; charts: AssistantCharts | null } | null {
  const braceIndices: number[] = [];
  for (let i = 0; i < raw.length; i += 1) {
    if (raw[i] === '{') {
      braceIndices.push(i);
    }
  }

  for (const idx of braceIndices) {
    const maybeJson = raw.slice(idx).trim();
    if (!maybeJson.includes('current_portfolio_forecast')
      && !maybeJson.includes('recommended_allocation')
      && !maybeJson.includes('expected_drivers')) {
      continue;
    }
    try {
      const parsed = JSON.parse(maybeJson) as AssistantCharts;
      return { text: raw.slice(0, idx).trim(), charts: parsed };
    } catch {
      // Try next candidate opening brace.
    }
  }

  return null;
}

function hasValidChartData(charts: AssistantCharts | null | undefined): boolean {
  if (!charts) return false;

  const hasLine =
    Array.isArray(charts.current_portfolio_forecast?.dates) &&
    Array.isArray(charts.current_portfolio_forecast?.values) &&
    charts.current_portfolio_forecast.dates!.length > 1 &&
    charts.current_portfolio_forecast.values!.length > 1;

  const hasPie =
    Array.isArray(charts.recommended_allocation?.labels) &&
    Array.isArray(charts.recommended_allocation?.values) &&
    charts.recommended_allocation.labels!.length > 0 &&
    charts.recommended_allocation.values!.length > 0;

  const hasBar =
    Array.isArray(charts.expected_drivers?.labels) &&
    Array.isArray(charts.expected_drivers?.values) &&
    charts.expected_drivers.labels!.length > 0 &&
    charts.expected_drivers.values!.length > 0;

  return hasLine || hasPie || hasBar;
}

function pickChartKeys(charts: AssistantCharts | null | undefined): Array<'line' | 'pie' | 'bar'> {
  if (!charts) return [];
  const keys: Array<'line' | 'pie' | 'bar'> = [];

  const hasLine =
    Array.isArray(charts.current_portfolio_forecast?.dates) &&
    Array.isArray(charts.current_portfolio_forecast?.values) &&
    charts.current_portfolio_forecast.dates!.length > 1 &&
    charts.current_portfolio_forecast.values!.length > 1;
  const hasPie =
    Array.isArray(charts.recommended_allocation?.labels) &&
    Array.isArray(charts.recommended_allocation?.values) &&
    charts.recommended_allocation.labels!.length > 0;
  const hasBar =
    Array.isArray(charts.expected_drivers?.labels) &&
    Array.isArray(charts.expected_drivers?.values) &&
    charts.expected_drivers.labels!.length > 0;

  if (hasLine) keys.push('line');
  if (hasPie) keys.push('pie');
  if (hasBar) keys.push('bar');

  return keys;
}

function buildLineData(charts: AssistantCharts) {
  const current = charts.current_portfolio_forecast;
  const recommended = charts.recommended_portfolio_forecast;
  if (!current?.dates || !current?.values) return [];

  return current.dates.map((d, i) => ({
    date: d,
    current: Number(current.values?.[i] ?? 0),
    recommended: recommended?.values?.[i] == null ? null : Number(recommended.values[i]),
  }));
}

function hasRecommendedSeries(charts: AssistantCharts): boolean {
  return Array.isArray(charts.recommended_portfolio_forecast?.values)
    && charts.recommended_portfolio_forecast!.values!.length > 1;
}

function buildPieData(charts: AssistantCharts) {
  const allocation = charts.recommended_allocation;
  if (!allocation?.labels || !allocation?.values) return [];
  return allocation.labels.map((label, i) => ({
    name: label,
    value: Number(allocation.values?.[i] ?? 0),
  }));
}

function buildDriverData(charts: AssistantCharts) {
  const drivers = charts.expected_drivers;
  if (!drivers?.labels || !drivers?.values) return [];
  return drivers.labels.map((label, i) => ({
    name: label,
    value: Number(drivers.values?.[i] ?? 0),
  }));
}

function AiAssistant() {
  const [messages, setMessages] = useState<AssistantMessage[]>([
    {
      role: 'assistant',
      content: '你好，我是你的投资助手。你可以直接问我：当前持仓风险、调仓建议、建仓方向等。',
    },
  ]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messagesRef = useRef<AssistantMessage[]>(messages);

  const setMessagesSync = (updater: (prev: AssistantMessage[]) => AssistantMessage[]) => {
    setMessages((prev) => {
      const next = updater(prev);
      messagesRef.current = next;
      return next;
    });
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const text = input.trim();
    if (!text || sending) return;

    const userMessage: AssistantMessage = { role: 'user', content: text };
    const placeholder: AssistantMessage = { role: 'assistant', content: '' };

    setMessagesSync((prev) => [...prev, userMessage, placeholder]);
    setInput('');
    setSending(true);
    setError(null);

    try {
      const requestHistory = messagesRef.current
        .filter((item) => item.role === 'user' || (item.role === 'assistant' && item.content.trim()))
        .map((item) => ({ role: item.role, content: item.content }));

      const resp = await fetch('/api/portfolio/ai-assistant/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: text,
          history: requestHistory,
        }),
      });

      if (!resp.ok || !resp.body) {
        const t = await resp.text();
        throw new Error(t || 'AI 接口调用失败');
      }

      const reader = resp.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let streamedText = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        streamedText += decoder.decode(value, { stream: true });

        const parsed = splitAssistantPayload(streamedText);
        setMessagesSync((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last && last.role === 'assistant') {
            last.content = parsed.text;
          }
          return next;
        });
      }

      streamedText += decoder.decode();
      const parsed = splitAssistantPayload(streamedText);
      const shouldRenderCharts = hasValidChartData(parsed.charts);

      setMessagesSync((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last && last.role === 'assistant') {
          last.content = parsed.text.trim() || '模型本次没有返回内容。';
          last.charts = shouldRenderCharts ? parsed.charts : null;
        }
        return next;
      });
    } catch (err) {
      console.error(err);
      setMessagesSync((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last?.role === 'assistant' && !last.content.trim()) {
          next.pop();
        }
        return next;
      });
      setError('AI 助手暂时不可用，请检查后端模型配置后重试。');
    } finally {
      setSending(false);
    }
  };

  const renderedMessages = useMemo(() => messages, [messages]);

  return (
    <div className="bg-white rounded-xl shadow-md p-4 md:p-6 h-[82vh] min-h-[640px] flex flex-col">
      <div className="mb-2">
        <h2 className="text-lg font-semibold text-gray-900">投资助手</h2>
        <p className="text-xs text-gray-500 mt-0.5">投资助手</p>
      </div>

      <div className="flex-1 overflow-y-auto border border-gray-200 rounded-lg p-3 space-y-3 bg-slate-50">
        {renderedMessages.map((m, idx) => {
          const lineData = m.charts ? buildLineData(m.charts) : [];
          const pieData = m.charts ? buildPieData(m.charts) : [];
          const driverData = m.charts ? buildDriverData(m.charts) : [];
          const selectedCharts = pickChartKeys(m.charts);
          const showRecommendedLine = m.charts ? hasRecommendedSeries(m.charts) : false;
          const showCharts = hasValidChartData(m.charts);

          return (
            <div key={`${m.role}-${idx}`} className="space-y-2">
              <div
                className={`max-w-[92%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap ${
                  m.role === 'user'
                    ? 'ml-auto bg-blue-600 text-white'
                    : 'mr-auto bg-white border border-gray-200 text-gray-800'
                }`}
              >
                {m.content}
              </div>

              {m.role === 'assistant' && showCharts && (
                <div className="mr-auto max-w-[92%] grid grid-cols-1 lg:grid-cols-2 gap-3">
                  {selectedCharts.includes('line') && lineData.length > 1 && (
                    <div className="bg-white border border-gray-200 rounded-lg p-3 lg:col-span-2">
                      <p className="text-xs text-gray-600 mb-2">
                        {m.charts?.current_portfolio_forecast?.title || '当前持仓预测盈亏'}
                      </p>
                      <div className="h-44">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={lineData}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="date" />
                            <YAxis tickFormatter={(v) => `${v}%`} />
                            <Tooltip formatter={(value: number) => `${value.toFixed(2)}%`} />
                            <Legend />
                            <Line type="monotone" dataKey="current" name="当前组合" stroke="#ef4444" strokeWidth={2} dot={false} />
                            {showRecommendedLine && (
                              <Line type="monotone" dataKey="recommended" name="建议组合" stroke="#2563eb" strokeWidth={2} dot={false} />
                            )}
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {selectedCharts.includes('pie') && pieData.length > 0 && (
                    <div className="bg-white border border-gray-200 rounded-lg p-3">
                      <p className="text-xs text-gray-600 mb-2">
                        {m.charts?.recommended_allocation?.title || '建议持仓占比'}
                      </p>
                      <div className="h-40">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie data={pieData} dataKey="value" nameKey="name" outerRadius={72} label />
                            <Tooltip formatter={(value: number) => `${value.toFixed(2)}%`} />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {selectedCharts.includes('bar') && driverData.length > 0 && (
                    <div className="bg-white border border-gray-200 rounded-lg p-3">
                      <p className="text-xs text-gray-600 mb-2">
                        {m.charts?.expected_drivers?.title || '收益驱动因子'}
                      </p>
                      <div className="h-40">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={driverData}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="name" />
                            <YAxis tickFormatter={(v) => `${v}%`} />
                            <Tooltip formatter={(value: number) => `${value.toFixed(2)}%`} />
                            <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
        {sending && <div className="text-xs text-gray-500">助手思考中...</div>}
      </div>

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      <form onSubmit={onSubmit} className="mt-3 flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入你的投资问题，例如：请预测当前收益曲线"
          className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={sending}
        />
        <button
          type="submit"
          disabled={sending || !input.trim()}
          className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          发送
        </button>
      </form>
    </div>
  );
}

export default AiAssistant;
