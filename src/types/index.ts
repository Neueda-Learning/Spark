export interface StockInfoResponse {
  id: number;
  symbol: string;
  name: string;
  assetType: string;
  sector: string;
  exchange: string;
  currentPrice: number;
  dailyChange: number;
  dailyChangePercent: number;
}

export interface HoldingResponse {
  stockId: number;
  symbol: string;
  name: string;
  assetType: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  totalCost: number;
  marketValue: number;
  profitLoss: number;
  profitLossPercent: number;
}

export interface AssetAllocation {
  assetType: string;
  value: number;
  percentage: number;
}

export interface PortfolioOverviewResponse {
  totalValue: number;
  totalProfitLoss: number;
  totalProfitLossPercent: number;
  cashBalance: number;
  investedValue: number;
  assetAllocation: AssetAllocation[];
}

export interface WeeklyPerformanceResponse {
  dates: string[];
  dailyProfits: number[];
  dailyReturns: number[];
  cumulativeReturn: number;
}

export interface PriceHistoryResponse {
  stockId: number;
  symbol: string;
  dates: string[];
  prices: number[];
}

export interface TransactionRequest {
  stockId: number;
  type: 'BUY' | 'SELL';
  quantity: number;
}

export interface TransactionResponse {
  transactionId: number;
  type: string;
  stockId: number;
  symbol: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  newCashBalance: number;
  message: string;
}

export interface AiChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface AiChatRequest {
  message: string;
  history: AiChatMessage[];
}

export interface AiChatResponse {
  reply: string;
}
