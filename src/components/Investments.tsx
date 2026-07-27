import { useEffect, useState, useCallback, useRef } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip } from 'recharts';
import { portfolioApi, stockApi, transactionApi } from '../api';
import type { HoldingResponse, StockInfoResponse, PriceHistoryResponse } from '../types';

/* ───────── 交易弹窗子组件 ───────── */
interface TradeModalProps {
  open: boolean;
  side: 'BUY' | 'SELL';
  stock: { symbol: string; name: string; currentPrice: number; holdingId?: number; maxQuantity?: number } | null;
  onClose: () => void;
  onSuccess: () => void;
}

function TradeModal({ open, side, stock, onClose, onSuccess }: TradeModalProps) {
  const [quantity, setQuantity] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setQuantity('');
      setError('');
    }
  }, [open]);

  if (!open || !stock) return null;

  const numQty = Number(quantity) || 0;
  const total = numQty * stock.currentPrice;

  const handleSubmit = async () => {
    if (numQty <= 0) {
      setError('请输入有效数量');
      return;
    }
    if (side === 'SELL' && stock.maxQuantity !== undefined && numQty > stock.maxQuantity) {
      setError(`卖出数量不能超过持有数量 ${stock.maxQuantity}`);
      return;
    }
    setLoading(true);
    setError('');
    try {
      await transactionApi.execute({
        stockId: stock.holdingId ?? undefined,
        type: side,
        quantity: numQty,
        price: stock.currentPrice,
      });
      onSuccess();
      onClose();
    } catch (e: any) {
      setError(e?.message || '交易失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-semibold mb-4">{side === 'BUY' ? '买入' : '卖出'} — {stock.symbol} {stock.name}</h2>

        <div className="text-sm text-gray-600 mb-4 space-y-1">
          <p>当前价格：<span className="font-medium text-gray-900">¥{stock.currentPrice.toFixed(2)}</span></p>
          {side === 'SELL' && stock.maxQuantity !== undefined && (
            <p>最大可卖出：<span className="font-medium text-gray-900">{stock.maxQuantity}</span></p>
          )}
        </div>

        <label className="block text-sm font-medium mb-1">交易数量</label>
        <input
          type="number"
          min={1}
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          className="w-full border border-gray-300 rounded-lg px-3 py-2 mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="请输入数量"
        />

        <div className="text-sm mb-4">
          {side === 'BUY' ? (
            <p>预计花费：<span className="font-semibold text-blue-600">¥{total.toFixed(2)}</span></p>
          ) : (
            <p>预计收入：<span className="font-semibold text-green-600">¥{total.toFixed(2)}</span></p>
          )}
        </div>

        {error && <p className="text-red-500 text-sm mb-3">{error}</p>}

        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50">取消</button>
          <button
            onClick={handleSubmit}
            disabled={loading}
            className={`px-4 py-2 rounded-lg text-white font-medium disabled:opacity-50 ${side === 'BUY' ? 'bg-blue-600 hover:bg-blue-700' : 'bg-red-500 hover:bg-red-600'}`}
          >
            {loading ? '提交中…' : '确认'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ───────── 悬停价格弹窗 ───────── */
interface HoverChartProps {
  stockId: number;
  symbol: string;
  anchor: { top: number; left: number } | null;
  visible: boolean;
}

function HoverChart({ stockId, symbol, anchor, visible }: HoverChartProps) {
  const [data, setData] = useState<PriceHistoryResponse[]>([]);
  const [loaded, setLoaded] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!visible || !stockId) {
      setData([]);
      setLoaded(false);
      return;
    }
    setLoaded(false);
    stockApi.getPriceHistory(stockId).then((res) => {
      setData(res);
      setLoaded(true);
    }).catch(() => setLoaded(true));
  }, [stockId, visible]);

  if (!visible || !anchor) return null;

  return (
    <div
      className="fixed z-40 bg-white rounded-xl shadow-xl border border-gray-200 p-4"
      style={{ top: anchor.top, left: anchor.left, width: 350 }}
    >
      <p className="text-sm font-semibold mb-2">{symbol} — 近 7 日走势</p>
      {!loaded ? (
        <div className="h-40 flex items-center justify-center text-gray-400 text-sm">加载中…</div>
      ) : data.length === 0 ? (
        <div className="h-40 flex items-center justify-center text-gray-400 text-sm">暂无数据</div>
      ) : (
        <LineChart width={320} height={160} data={data}>
          <XAxis dataKey="date" tick={{ fontSize: 11 }} />
          <YAxis domain={['auto', 'auto']} tick={{ fontSize: 11 }} width={50} />
          <Tooltip />
          <Line type="monotone" dataKey="close" stroke="#3b82f6" dot={false} strokeWidth={2} />
        </LineChart>
      )}
    </div>
  );
}

/* ───────── 主页面 ───────── */
export default function Investments() {
  const [holdings, setHoldings] = useState<HoldingResponse[]>([]);
  const [allStocks, setAllStocks] = useState<StockInfoResponse[]>([]);
  const [loading, setLoading] = useState(true);

  /* 弹窗状态 */
  const [tradeModal, setTradeModal] = useState<{
    open: boolean;
    side: 'BUY' | 'SELL';
    stock: { symbol: string; name: string; currentPrice: number; holdingId?: number; maxQuantity?: number } | null;
  }>({ open: false, side: 'BUY', stock: null });

  /* 悬停弹窗 */
  const [hoverInfo, setHoverInfo] = useState<{ stockId: number; symbol: string; anchor: { top: number; left: number } } | null>(null);
  const hoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [h, s] = await Promise.all([portfolioApi.getHoldings(), stockApi.getAll()]);
      setHoldings(h);
      setAllStocks(s);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  /* 分组 */
  const holdingSymbolSet = new Set(holdings.map((h) => h.symbol));
  const heldStocks = allStocks.filter((s) => holdingSymbolSet.has(s.symbol));
  const unheldStocks = allStocks.filter((s) => !holdingSymbolSet.has(s.symbol));

  /* 为已持仓列表补充均价等 */
  const enrichedHeld = heldStocks.map((s) => {
    const h = holdings.find((h) => h.symbol === s.symbol);
    return { ...s, ...h };
  });

  /* 交易弹窗 helpers */
  const openTrade = (side: 'BUY' | 'SELL', row: any) => {
    setTradeModal({
      open: true,
      side,
      stock: {
        symbol: row.symbol,
        name: row.name,
        currentPrice: row.currentPrice,
        holdingId: row.id ?? row.holdingId,
        maxQuantity: side === 'SELL' ? row.quantity : undefined,
      },
    });
  };

  /* 悬停 helpers — 延迟关闭，防止移入弹窗时消失 */
  const handleRowEnter = (e: React.MouseEvent, stockId: number, symbol: string) => {
    if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setHoverInfo({ stockId, symbol, anchor: { top: rect.top, left: rect.right + 12 } });
  };

  const handleRowLeave = () => {
    hoverTimerRef.current = setTimeout(() => setHoverInfo(null), 200);
  };

  const handleChartEnter = () => {
    if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
  };

  const handleChartLeave = () => {
    hoverTimerRef.current = setTimeout(() => setHoverInfo(null), 200);
  };

  /* ───── 渲染 ───── */
  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-400">加载中…</div>;
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 space-y-8">
      {/* ──── 已持仓 ──── */}
      <section className="bg-white rounded-2xl shadow p-6">
        <h2 className="text-xl font-bold mb-4">我的持仓</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="border-b border-gray-200 text-gray-500">
                <th className="py-2 px-3">代码</th>
                <th className="py-2 px-3">名称</th>
                <th className="py-2 px-3">类型</th>
                <th className="py-2 px-3 text-right">持有数量</th>
                <th className="py-2 px-3 text-right">均价</th>
                <th className="py-2 px-3 text-right">现价</th>
                <th className="py-2 px-3 text-right">盈亏</th>
                <th className="py-2 px-3 text-right">盈亏%</th>
                <th className="py-2 px-3 text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              {enrichedHeld.map((row, idx) => {
                const profit = row.profitLoss ?? 0;
                const profitPct = row.profitLossPercent ?? 0;
                const color = profit >= 0 ? 'text-green-600' : 'text-red-500';
                return (
                  <tr
                    key={row.symbol}
                    className={`border-b border-gray-100 hover:bg-blue-50/40 ${idx % 2 === 1 ? 'bg-gray-50/60' : ''}`}
                    onMouseEnter={(e) => handleRowEnter(e, row.id ?? row.holdingId ?? 0, row.symbol)}
                    onMouseLeave={handleRowLeave}
                  >
                    <td className="py-2 px-3 font-medium">{row.symbol}</td>
                    <td className="py-2 px-3">{row.name}</td>
                    <td className="py-2 px-3">{row.assetType}</td>
                    <td className="py-2 px-3 text-right">{row.quantity}</td>
                    <td className="py-2 px-3 text-right">¥{(row.averageCost ?? 0).toFixed(2)}</td>
                    <td className="py-2 px-3 text-right">¥{(row.currentPrice ?? 0).toFixed(2)}</td>
                    <td className={`py-2 px-3 text-right font-medium ${color}`}>{profit >= 0 ? '+' : ''}{profit.toFixed(2)}</td>
                    <td className={`py-2 px-3 text-right font-medium ${color}`}>{profitPct >= 0 ? '+' : ''}{profitPct.toFixed(2)}%</td>
                    <td className="py-2 px-3 text-center whitespace-nowrap">
                      <button onClick={() => openTrade('BUY', row)} className="px-2 py-1 mr-1 rounded bg-blue-50 text-blue-600 hover:bg-blue-100 text-xs font-medium">买入</button>
                      <button onClick={() => openTrade('SELL', row)} className="px-2 py-1 rounded bg-red-50 text-red-500 hover:bg-red-100 text-xs font-medium">卖出</button>
                    </td>
                  </tr>
                );
              })}
              {enrichedHeld.length === 0 && (
                <tr><td colSpan={9} className="text-center text-gray-400 py-8">暂无持仓</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* ──── 未持仓 ──── */}
      <section className="bg-white rounded-2xl shadow p-6">
        <h2 className="text-xl font-bold mb-4">市场标的</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="border-b border-gray-200 text-gray-500">
                <th className="py-2 px-3">代码</th>
                <th className="py-2 px-3">名称</th>
                <th className="py-2 px-3">类型</th>
                <th className="py-2 px-3">板块</th>
                <th className="py-2 px-3 text-right">现价</th>
                <th className="py-2 px-3 text-right">日涨跌</th>
                <th className="py-2 px-3 text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              {unheldStocks.map((row, idx) => {
                const change = row.dailyChange ?? 0;
                const changePct = row.dailyChangePercent ?? 0;
                const color = change >= 0 ? 'text-green-600' : 'text-red-500';
                return (
                  <tr
                    key={row.symbol}
                    className={`border-b border-gray-100 hover:bg-blue-50/40 ${idx % 2 === 1 ? 'bg-gray-50/60' : ''}`}
                    onMouseEnter={(e) => handleRowEnter(e, row.id, row.symbol)}
                    onMouseLeave={handleRowLeave}
                  >
                    <td className="py-2 px-3 font-medium">{row.symbol}</td>
                    <td className="py-2 px-3">{row.name}</td>
                    <td className="py-2 px-3">{row.assetType}</td>
                    <td className="py-2 px-3">{row.sector ?? '-'}</td>
                    <td className="py-2 px-3 text-right">¥{(row.currentPrice ?? 0).toFixed(2)}</td>
                    <td className={`py-2 px-3 text-right font-medium ${color}`}>
                      {change >= 0 ? '+' : ''}{change.toFixed(2)} ({changePct >= 0 ? '+' : ''}{changePct.toFixed(2)}%)
                    </td>
                    <td className="py-2 px-3 text-center">
                      <button onClick={() => openTrade('BUY', row)} className="px-3 py-1 rounded bg-blue-50 text-blue-600 hover:bg-blue-100 text-xs font-medium">买入</button>
                    </td>
                  </tr>
                );
              })}
              {unheldStocks.length === 0 && (
                <tr><td colSpan={7} className="text-center text-gray-400 py-8">暂无更多标的</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* ──── 悬停走势图 ──── */}
      {hoverInfo && (
        <div onMouseEnter={handleChartEnter} onMouseLeave={handleChartLeave}>
          <HoverChart stockId={hoverInfo.stockId} symbol={hoverInfo.symbol} anchor={hoverInfo.anchor} visible />
        </div>
      )}

      {/* ──── 交易弹窗 ──── */}
      <TradeModal
        open={tradeModal.open}
        side={tradeModal.side}
        stock={tradeModal.stock}
        onClose={() => setTradeModal({ open: false, side: 'BUY', stock: null })}
        onSuccess={fetchData}
      />
    </div>
  );
}
