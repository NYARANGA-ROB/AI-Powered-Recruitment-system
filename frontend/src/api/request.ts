import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { Result } from '../types';
import { useAuthStore } from '../store/auth';
import { message, notification } from 'antd';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const SUCCESS_CODE = 200;

const request = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

const getErrorMessage = (error: AxiosError<Result<unknown>>): string => {
  const body: unknown = error.response?.data;
  if (body && typeof body === 'object') {
    const responseBody = body as { message?: unknown; error?: unknown };
    if (typeof responseBody.message === 'string' && responseBody.message.trim()) {
      return responseBody.message;
    }
    if (typeof responseBody.error === 'string' && responseBody.error.trim()) {
      return responseBody.error;
    }
  }
  if (error.code === 'ERR_NETWORK') {
    return 'Unable to reach the backend service. Please check your connection and server status.';
  }
  if (error.code === 'ECONNABORTED') {
    return 'Request timed out. Please try again.';
  }
  return error.message || 'Something went wrong. Please try again.';
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

let refreshPromise: Promise<string> | null = null;
let sessionExpiryHandled = false;

const expireSession = () => {
  const authStore = useAuthStore.getState();
  authStore.logout();
  if (!sessionExpiryHandled) {
    sessionExpiryHandled = true;
    notification.warning({
      message: 'Session expired',
      description: 'Please sign in again to continue.',
      duration: 4,
    });
  }
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
};

const refreshAccessToken = (refreshToken: string): Promise<string> => {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${BASE_URL}/auth/refresh`, { refreshToken })
      .then((response) => {
        const result = response.data as Result<{ accessToken: string; refreshToken: string }>;
        const accessToken = result?.data?.accessToken;
        const newRefreshToken = result?.data?.refreshToken;

        if (result?.code !== SUCCESS_CODE || !accessToken || !newRefreshToken) {
          throw new Error(result?.message || 'The server returned an invalid refresh response.');
        }

        useAuthStore.getState().setTokens(accessToken, newRefreshToken);
        sessionExpiryHandled = false;
        return accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

request.interceptors.response.use(
  (response) => {
    const payload: unknown = response.data;

    if (payload && typeof payload === 'object' && 'code' in payload) {
      const result = payload as Partial<Result<unknown>>;
      if (typeof result.code !== 'number' || result.code !== SUCCESS_CODE) {
        const messageText = result.message || 'Request failed';
        message.error(messageText);
        return Promise.reject(new Error(messageText));
      }
    }

    return response;
  },
  async (error: AxiosError<Result<unknown>>) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const requestUrl = originalRequest?.url || '';

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry &&
      !/\/auth\/(login|register|send-verification-code|refresh)(\?|$)/.test(requestUrl)) {
      const refreshToken = useAuthStore.getState().refreshToken;
      if (!refreshToken) {
        expireSession();
        return Promise.reject(error);
      }

      originalRequest._retry = true;
      try {
        const accessToken = await refreshAccessToken(refreshToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return request(originalRequest);
      } catch (refreshError) {
        expireSession();
        return Promise.reject(refreshError);
      }
    }

    if (error.response?.status === 401 &&
      (originalRequest?._retry || requestUrl.includes('/auth/refresh'))) {
      expireSession();
      return Promise.reject(error);
    }

    const errorMessage = getErrorMessage(error);
    if (error.response?.status === 403) {
      message.warning('Access denied. You do not have permission for this action.');
    } else if (error.response?.status === 404) {
      message.warning('The requested resource could not be found.');
    } else if (error.response?.status === 429) {
      message.warning('Too many requests. Please wait a moment and try again.');
    } else if (error.response && error.response.status >= 500) {
      message.error('The service is temporarily unavailable. Please try again later.');
    } else if (!error.response) {
      message.error(errorMessage);
    } else {
      message.error(errorMessage);
    }
    return Promise.reject(error);
  },
);

export default request;
