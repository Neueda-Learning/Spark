import axios from 'axios';
import {
  StockInfoResponse,
  PriceHistoryResponse,
  PortfolioOverviewResponse,
  WeeklyPerformanceResponse,
  HoldingResponse,
  TransactionRequest,
  TransactionResponse,
} from '../types';

const api = axios.create({
  baseURL: '/api',
});

export const stockApi = {
  getAll: () => api.get<StockInfoResponse[]>('/stocks').then((r) => r.data),
  getById: (id: number) =>
    api.get<StockInfoResponse>(`/stocks/${id}`).then((r) => r.data),
  getPriceHistory: (id: number) =>
    api.get<PriceHistoryResponse>(`/stocks/${id}/prices`).then((r) => r.data),
};

export const portfolioApi = {
  getOverview: () =>
    api.get<PortfolioOverviewResponse>('/portfolio/overview').then((r) => r.data),
  getWeeklyPerformance: () =>
    api
      .get<WeeklyPerformanceResponse[]>('/portfolio/weekly-performance')
      .then((r) => r.data),
  getHoldings: () =>
    api.get<HoldingResponse[]>('/portfolio/holdings').then((r) => r.data),
};

export const transactionApi = {
  execute: (req: TransactionRequest) =>
    api
      .post<TransactionResponse>('/portfolio/transactions', req)
      .then((r) => r.data),
};

export default api;
