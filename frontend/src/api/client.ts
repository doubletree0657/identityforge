import axios, { AxiosError, AxiosRequestConfig, Method } from 'axios';
import { ErrorResponse } from '../types/api';
import { getAccessToken, getApiBaseUrl } from './storage';

export class ApiError extends Error {
  readonly status?: number;
  readonly code: string;

  constructor(message: string, code: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

const http = axios.create({
  headers: {
    'Content-Type': 'application/json',
  },
});

http.interceptors.request.use((config) => {
  config.baseURL = getApiBaseUrl();
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export async function apiRequest<T>(
  method: Method,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>,
): Promise<T> {
  try {
    const config: AxiosRequestConfig = { method, url, data, params };
    const response = await http.request<T>(config);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      throw normalizeAxiosError(error);
    }
    throw error;
  }
}

function normalizeAxiosError(error: AxiosError<ErrorResponse>): ApiError {
  const status = error.response?.status;
  const body = error.response?.data;
  if (body?.message) {
    return new ApiError(body.message, body.error || 'api_error', status);
  }
  if (status === 401) {
    return new ApiError('A bearer token is required for this API.', 'unauthorized', status);
  }
  if (status === 403) {
    return new ApiError('This account is authenticated but is not authorized for this Admin API action.', 'forbidden', status);
  }
  return new ApiError(error.message || 'API request failed', 'api_error', status);
}
