import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, Tooltip, Legend, ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid } from 'recharts';
import { portfolioApi } from '../api';
import type { PortfolioOverviewResponse, WeeklyPerformanceResponse } from '../types';

const COLORS: Record<string, string> = {
  STOCK: '#3b82f6',
  BOND: '#22c55e',
  CASH: '#f97316',
};

function formatMoney(value: number): string {
  return `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number): string {
  return `${value.toFixed(2)}%`;
}

function Overview() {
  const [overview, setOverview] = useState<PortfolioOverviewResponse | null>(null);
  const [weekly, setWeekly] = useState<WeeklyPerformanceResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);
        const [overviewData, weeklyData] = await Promise.all([
          portfolioApi.getOverview(),
          portfolioApi.getWeeklyPerformance(),
        ]);
        setOverview(overviewData);
        setWeekly(weeklyData);
      } catch (err) {
        setError('Failed to load portfolio data. Please try again later.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <span className="text-gray-500 text-lg">Loading...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
          <p className="text-red-600 text-lg font-medium">{error}</p>
        </div>
      </div>
    );
  }

  if (!overview || !weekly) return null;

  const isProfitPositive = overview.totalProfitLoss >= 0;
  const profitColor = isProfitPositive ? 'text-green-600' : 'text-red-600';

  const pieData = overview.assetAllocation.map((item) => ({
    name: item.assetType,
    value: item.value,
    percentage: item.percentage,
  }));

  const weeklyChartData = weekly.dates.map((date, idx) => ({
    date,
    dailyProfit: weekly.dailyProfits[idx],
    dailyReturn: weekly.dailyReturns[idx],
  }));

  return (
    <div className="space-y-6 p-4">
      {/* Section 1: Top Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Total Value */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500 font-medium uppercase tracking-wide">Total Value</p>
          <div className="mt-2 flex items-end gap-2">
            <span className="text-3xl font-bold text-gray-900">
              {formatMoney(overview.totalValue)}
            </span>
            <span className={isProfitPositive ? 'text-green-600' : 'text-red-600'}>
              {isProfitPositive ? '▲' : '▼'}
            </span>
          </div>
          <p className="mt-1 text-sm text-gray-400">Portfolio market value</p>
        </div>

        {/* Total Profit / Loss */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500 font-medium uppercase tracking-wide">Total Profit / Loss</p>
          <p className={`mt-2 text-3xl font-bold ${profitColor}`}>
            {isProfitPositive ? '+' : ''}{formatMoney(overview.totalProfitLoss)}
          </p>
          <p className={`mt-1 text-sm ${profitColor}`}>
            {isProfitPositive ? '+' : ''}{formatPercent(overview.totalProfitLossPercent)}
          </p>
        </div>

        {/* Cash Balance */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <p className="text-sm text-gray-500 font-medium uppercase tracking-wide">Cash Balance</p>
          <p className="mt-2 text-3xl font-bold text-blue-600">
            {formatMoney(overview.cashBalance)}
          </p>
          <p className="mt-1 text-sm text-gray-400">Available for trading</p>
        </div>
      </div>

      {/* Section 2: Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Pie Chart - Asset Allocation */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">Asset Allocation</h3>
          <div className="flex items-center justify-center">
            <PieChart width={360} height={300}>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                innerRadius={70}
                outerRadius={110}
                dataKey="value"
                nameKey="name"
                label={({ name, percentage }) => `${name} ${formatPercent(percentage)}`}
              >
                {pieData.map((entry) => (
                  <Cell key={entry.name} fill={COLORS[entry.name] || '#6b7280'} />
                ))}
              </Pie>
              <Tooltip
                formatter={(value: number) => formatMoney(value)}
                contentStyle={{ borderRadius: '8px', border: '1px solid #e5e7eb' }}
              />
              <Legend />
              <text
                x="50%"
                y="48%"
                textAnchor="middle"
                dominantBaseline="middle"
                className="text-sm fill-gray-500"
              >
                Total
              </text>
              <text
                x="50%"
                y="55%"
                textAnchor="middle"
                dominantBaseline="middle"
                className="text-base font-bold fill-gray-900"
              >
                {formatMoney(overview.totalValue)}
              </text>
            </PieChart>
          </div>
        </div>

        {/* Composed Chart - Weekly Performance */}
        <div className="bg-white rounded-xl shadow-md p-6">
          <h3 className="text-lg font-semibold text-gray-800 mb-4">7-Day Performance</h3>
          <ComposedChart width={440} height={300} data={weeklyChartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(v: string) => v.slice(5)}
            />
            <YAxis
              yAxisId="left"
              tick={{ fontSize: 12 }}
              tickFormatter={(v: number) => `$${v.toFixed(0)}`}
            />
            <YAxis
              yAxisId="right"
              orientation="right"
              tick={{ fontSize: 12 }}
              tickFormatter={(v: number) => `${v.toFixed(2)}%`}
            />
            <Tooltip
              contentStyle={{ borderRadius: '8px', border: '1px solid #e5e7eb' }}
              formatter={(value: number, name: string) => {
                if (name === 'Daily Profit') return formatMoney(value);
                return formatPercent(value);
              }}
            />
            <Legend />
            <Bar
              yAxisId="left"
              dataKey="dailyProfit"
              name="Daily Profit"
              fill="#3b82f6"
              radius={[4, 4, 0, 0]}
            />
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="dailyReturn"
              name="Daily Return"
              stroke="#f97316"
              strokeWidth={2}
              dot={{ r: 4, fill: '#f97316' }}
            />
          </ComposedChart>
        </div>
      </div>
    </div>
  );
}

export default Overview;
