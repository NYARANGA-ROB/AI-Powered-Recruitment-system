import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { Result } from '../types';
import { useAuthStore } from '../store/auth';
import { message } from 'antd';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const request = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// ==================== Request Interceptor ====================
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ==================== Response Interceptor ====================
let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

const processQueue = (token: string) => {
  pendingRequests.forEach((cb) => cb(token));
  pendingRequests = [];
};

request.interceptors.response.use(
  (response) => {
    const data = response.data as Result<unknown>;

    // Business error code handling
    if (data.code !== 200) {
      message.error(data.message || 'Request failed');
      return Promise.reject(new Error(data.message));
    }

    return response;
  },
  async (error: AxiosError<Result<unknown>>) => {
    const originalRequest = error.config;

    // 401 → Auto-refresh token
    if (error.response?.status === 401 && originalRequest) {
      const authStore = useAuthStore.getState();
      const refreshToken = authStore.refreshToken;

      if (!refreshToken) {
        authStore.logout();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            resolve(request(originalRequest));
          });
        });
      }

      isRefreshing = true;

      try {
        const res = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
        const { accessToken, refreshToken: newRefresh } = res.data.data;
        authStore.setTokens(accessToken, newRefresh);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        }
        processQueue(accessToken);
        return request(originalRequest);
      } catch {
        authStore.logout();
        window.location.href = '/login';
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    // 403
    if (error.response?.status === 403) {
      message.error('Access denied');
    }

    // Network error
    if (!error.response) {
      message.error('Network connection failed, please check your connection');
    }

    return Promise.reject(error);
  },
);

export default request;
