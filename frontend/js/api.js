/*
 * Shared transport for the documented Spring Boot API contract.
 */
(() => {
  const config = window.CAMPUSCARE_CONFIG;
  const allowedMethods = new Set(['GET', 'POST', 'PUT', 'DELETE']);
  const loadingTargets = new WeakMap();
  const listeners = new Set();
  let activeRequests = 0;
  let tokenProvider = null;

  class ApiError extends Error {
    constructor(message, details = {}) {
      super(message);
      this.name = 'CampusCareApiError';
      this.operation = details.operation || '';
      this.status = Number.isFinite(details.status) ? details.status : 0;
      this.kind = details.kind || 'request_error';
      this.cause = details.cause;
      this.response = details.response;
    }
  }

  const emit = (type, detail) => {
    const event = new CustomEvent(`campuscare:api-${type}`, { detail });
    window.dispatchEvent(event);
    listeners.forEach(listener => listener({ type, ...detail }));
  };

  const resolveLoadingTarget = target => {
    if (typeof target === 'string') return document.querySelector(target);
    return target && typeof target === 'object' ? target : null;
  };

  const setTargetLoading = (target, loading) => {
    const element = resolveLoadingTarget(target);
    if (!element) return;
    if (loading) {
      let state = loadingTargets.get(element);
      if (!state) {
        state = { disabled: 'disabled' in element ? element.disabled : undefined, count: 0 };
        loadingTargets.set(element, state);
      }
      state.count += 1;
      if ('disabled' in element) element.disabled = true;
      element.classList.add('api-loading');
      element.setAttribute('aria-busy', 'true');
      return;
    }
    const previous = loadingTargets.get(element);
    if (!previous) return;
    previous.count -= 1;
    if (previous.count > 0) return;
    if (previous && 'disabled' in element) element.disabled = previous.disabled;
    loadingTargets.delete(element);
    element.classList.remove('api-loading');
    element.removeAttribute('aria-busy');
  };

  const beginLoading = (operation, target) => {
    activeRequests += 1;
    document.documentElement.dataset.apiLoading = 'true';
    setTargetLoading(target, true);
    emit('loading', { active: true, activeCount: activeRequests, operation });
  };

  const endLoading = (operation, target) => {
    activeRequests = Math.max(0, activeRequests - 1);
    if (activeRequests === 0) delete document.documentElement.dataset.apiLoading;
    setTargetLoading(target, false);
    emit('loading', { active: activeRequests > 0, activeCount: activeRequests, operation });
  };

  const appendQuery = (url, query) => {
    if (!query) return;
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null) return;
      (Array.isArray(value) ? value : [value]).forEach(item => url.searchParams.append(key, String(item)));
    });
  };

  const parseResponse = async response => {
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.toLowerCase().includes('application/json')) return text;
    try { return JSON.parse(text); }
    catch (cause) { throw new ApiError('The server returned invalid JSON.', { status: response.status, kind: 'invalid_response', cause }); }
  };

  const request = async (operation, options = {}) => {
    if (config.mode !== 'api') {
      const error = new ApiError('API mode is not enabled; no request was sent.', { operation, kind: 'api_mode_disabled' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }
    const contract = config.endpoints[operation];
    if (!contract || typeof contract !== 'object' || !contract.path) {
      const error = new ApiError(`API contract is not configured for operation "${operation}".`, { operation, kind: 'contract_missing' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }

    const method = String(options.method || contract.method || '').toUpperCase();
    if (!allowedMethods.has(method)) {
      const error = new ApiError(`A documented HTTP method is required for operation "${operation}".`, { operation, kind: 'method_missing' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }

    const baseUrl = options.baseUrl || config.apiBaseUrl;
    if (!baseUrl) {
      const error = new ApiError('The API base URL is not configured for this environment.', { operation, kind: 'base_url_missing' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }

    let url;
    try {
      let path = contract.path;
      Object.entries(options.pathParams || {}).forEach(([key, value]) => {
        path = path.replace(`{${key}}`, encodeURIComponent(String(value)));
      });
      if (/\{[^}]+\}/.test(path)) throw new Error('A required path parameter is missing.');
      url = new URL(path, baseUrl);
      appendQuery(url, options.query);
    } catch (cause) {
      const error = new ApiError('The configured API URL is invalid.', { operation, kind: 'invalid_url', cause });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }

    if ((method === 'GET' || method === 'DELETE') && options.body !== undefined) {
      const error = new ApiError(`${method} requests cannot include a body through this service.`, { operation, kind: 'invalid_request' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    }

    const headers = new Headers(options.headers || {});
    const isMultipart = typeof FormData !== 'undefined' && options.body instanceof FormData;
    let body;
    if (isMultipart) {
      body = options.body;
      // The browser must add the multipart boundary; do not set Content-Type here.
      headers.delete('Content-Type');
    } else if (options.body !== undefined) {
      body = JSON.stringify(options.body);
      if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    }

    if (config.auth.headerName && typeof tokenProvider === 'function') {
      let token;
      try {
        token = await tokenProvider({ operation, contract });
      } catch (cause) {
        const error = new ApiError('Authentication credentials could not be prepared.', {
          operation, kind: 'auth_context_error', cause
        });
        emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
        throw error;
      }
      if (token) {
        const value = config.auth.scheme ? `${config.auth.scheme} ${token}` : String(token);
        headers.set(config.auth.headerName, value);
      }
    }

    const controller = new AbortController();
    const timeout = Number(options.timeoutMs ?? config.request.timeoutMs) || 15000;
    const timer = setTimeout(() => controller.abort(new DOMException('Request timed out.', 'TimeoutError')), timeout);
    const externalSignal = options.signal;
    const abortFromCaller = () => controller.abort(externalSignal.reason);
    if (externalSignal) {
      if (externalSignal.aborted) abortFromCaller();
      else externalSignal.addEventListener('abort', abortFromCaller, { once: true });
    }

    beginLoading(operation, options.loadingTarget);
    try {
      const response = await fetch(url, {
        method,
        headers,
        body,
        credentials: options.credentials || config.request.credentials,
        signal: controller.signal
      });
      const data = await parseResponse(response);
      if (!response.ok) {
        const serverMessage = data && typeof data === 'object' && typeof data.message === 'string' ? data.message : `The server returned HTTP ${response.status}.`;
        throw new ApiError(serverMessage, {
          operation, status: response.status, kind: 'http_error', response: data
        });
      }
      return data;
    } catch (cause) {
      const error = cause instanceof ApiError ? cause : new ApiError(
        cause?.name === 'AbortError' || cause?.name === 'TimeoutError' ? 'The request was cancelled or timed out.' : 'The server could not be reached.',
        { operation, kind: cause?.name === 'AbortError' || cause?.name === 'TimeoutError' ? 'aborted' : 'network_error', cause }
      );
      if (!error.operation) error.operation = operation;
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      throw error;
    } finally {
      clearTimeout(timer);
      if (externalSignal) externalSignal.removeEventListener('abort', abortFromCaller);
      endLoading(operation, options.loadingTarget);
    }
  };

  const runByMode = (operation, adapters = {}) => {
    if (config.mode === 'mock') {
      if (typeof adapters.mock !== 'function') {
        const error = new ApiError(`No mock adapter was supplied for "${operation}".`, { operation, kind: 'mock_adapter_missing' });
        emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
        return Promise.reject(error);
      }
      return Promise.resolve().then(() => adapters.mock()).catch(cause => {
        const error = cause instanceof ApiError ? cause : new ApiError(
          'The preview operation could not be completed.',
          { operation, kind: 'mock_error', cause }
        );
        emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
        throw error;
      });
    }
    if (config.mode !== 'api') {
      const error = new ApiError('The configured data mode is invalid; no request was sent.', { operation, kind: 'invalid_mode' });
      emit('error', { operation, message: error.message, status: error.status, kind: error.kind });
      return Promise.reject(error);
    }
    return request(operation, adapters.api || {});
  };

  const operation = name => adapters => runByMode(name, adapters);
  const api = {
    ApiError,
    request,
    run: runByMode,
    isLoading: () => activeRequests > 0,
    subscribe(listener) {
      if (typeof listener !== 'function') throw new TypeError('Listener must be a function.');
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    configure({ getToken } = {}) {
      tokenProvider = typeof getToken === 'function' ? getToken : null;
    }
  };

  window.CampusCareAPI = api;
  api.configure({ getToken: () => {
    try { return JSON.parse(sessionStorage.getItem('campuscare.session.v1') || 'null')?.token || null; }
    catch (_) { return null; }
  } });
  window.CampusCareServices = Object.freeze({
    mode: () => config.mode,
    auth: Object.freeze({
      login(role, adapters) {
        if (!['student', 'faculty', 'admin'].includes(role)) throw new TypeError('Unsupported account role.');
        return runByMode(`auth.${role}.login`, adapters);
      },
      registerStudent: operation('auth.student.register'),
      logout: operation('auth.logout'),
      currentUser: operation('auth.currentUser')
    }),
    complaints: Object.freeze({
      submitAnonymous: operation('complaints.submitAnonymous'),
      uploadImage: operation('complaints.uploadImage'),
      listMine: operation('complaints.listMine'),
      getMine: operation('complaints.getMine'),
      listFaculty: operation('complaints.listFaculty'),
      listAdmin: operation('complaints.listAdmin'),
      updateStatus: operation('complaints.updateStatus'),
      addRemarks: operation('complaints.addRemarks')
    }),
    locations: Object.freeze({
      buildings: operation('locations.buildings'),
      floors: operation('locations.floors'),
      rooms: operation('locations.rooms')
    })
  });
})();
