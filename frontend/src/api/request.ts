import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { Result } from '../types';
import { useAuthStore } from '../store/auth';
import { message, notification } from 'antd';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const request = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

const getErrorMessage = (error: AxiosError<Result<unknown>> | Error): string => {
  const payload = (error as AxiosError<Result<unknown>>)?.response?.data;

  if (payload?.message) return payload.message;
  if (payload?.error) return payload.error;
  if ((error as AxiosError)?.code === 'ERR_NETWORK') {
    return 'Unable to reach the backend service. Please check your connection and server status.';
  }
  if ((error as AxiosError)?.code === 'ECONNABORTED') {
    return 'Request timed out. Please try again.';
  }
  if (error.message) {
    return error.message;
  }

  return 'Something went wrong. Please try again.';
};

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (config.headers) {
      config.headers.Accept = 'application/json';
    }

    return config;
  },
  (error) => Promise.reject(error),
);

let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

const processQueue = (token: string) => {
  pendingRequests.forEach((cb) => cb(token));
  pendingRequests = [];
};

request.interceptors.response.use(
  (response) => {
    const payload = response.data as Result<unknown> | undefined;

    if (payload && payload.code !== undefined && payload.code !== 200 && payload.code !== 201) {
      const messageText = payload.message || 'Request failed';
      message.error(messageText);
      return Promise.reject(new Error(messageText));
    }

    return response;
  },
  async (error: AxiosError<Result<unknown>>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean } | undefined;
    const errorMessage = getErrorMessage(error);

    if (error.response?.status === 401 && originalRequest) {
      originalRequest._retry = true;
      const authStore = useAuthStore.getState();
      const refreshToken = authStore.refreshToken;

      if (!refreshToken) {
        authStore.logout();
        notification.warning({
          message: 'Session expired',
          description: 'Please sign in again to continue.',
          duration: 4,
        });
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingRequests.push((token: string) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            request(originalRequest)
              .then(resolve)
              .catch(reject);
          });
        });
      }

      isRefreshing = true;

      try {
        const res = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
        const payload = res.data as Result<{ accessToken: string; refreshToken: string }>;
        const accessToken = payload.data?.accessToken;
        const newRefresh = payload.data?.refreshToken;

        if (!accessToken || !newRefresh) {
          throw new Error('Invalid refresh response');
        }

        authStore.setTokens(accessToken, newRefresh);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        }

        processQueue(accessToken);
        return request(originalRequest);
      } catch (refreshError) {
        authStore.logout();
        notification.error({
          message: 'Session refresh failed',
          description: 'Your session has expired. Please sign in again.',
          duration: 4,
        });
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    if (error.response?.status === 403) {
      message.warning('Access denied. You do not have permission for this action.');
    } else if (error.response?.status === 404) {
      message.warning('The requested resource could not be found.');
    } else if (!error.response) {
      message.error('Network connection failed. Please check your connection and try again.');
    } else {
      message.error(errorMessage);
    }

    return Promise.reject(error);
  },
);

export default request;
