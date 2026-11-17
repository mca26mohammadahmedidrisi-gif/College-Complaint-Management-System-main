/* API contract for the Java backend. Use ?mode=mock to open the browser preview. */
(() => {
  const hostname = window.location.hostname;
  const isLocal = window.location.protocol === 'file:' || hostname === 'localhost' || hostname === '127.0.0.1';
  const environment = isLocal ? 'development' : 'production';
  const queryMode = new URLSearchParams(window.location.search).get('mode');
  const storedMode = isLocal ? sessionStorage.getItem('campuscare.mode') : null;
  if (isLocal && (queryMode === 'mock' || queryMode === 'api')) sessionStorage.setItem('campuscare.mode', queryMode);
  // Production must never silently fall back to browser-only mock storage because of an old tab setting.
  if (!isLocal) sessionStorage.removeItem('campuscare.mode');
  const selectedMode = isLocal ? queryMode || storedMode || 'api' : 'api';
  const productionApiUrl = window.CAMPUSCARE_API_BASE_URL || '';
  const apiBaseUrl = environment === 'development' ? 'http://localhost:8080' : productionApiUrl;
  const endpoints = {
    departments: { method: 'GET', path: '/api/departments' },
    'auth.student.register': { method: 'POST', path: '/api/auth/register' },
    'auth.student.login': { method: 'POST', path: '/api/auth/login' },
    'auth.faculty.login': { method: 'POST', path: '/api/auth/login' },
    'auth.admin.login': { method: 'POST', path: '/api/auth/login' },
    'auth.verifyEmail': { method: 'POST', path: '/api/auth/verify-email' },
    'auth.resendVerification': { method: 'POST', path: '/api/auth/resend-verification' },
    'auth.forgotPassword': { method: 'POST', path: '/api/auth/forgot-password' },
    'auth.resetPassword': { method: 'POST', path: '/api/auth/reset-password' },
    'auth.logout': { method: 'POST', path: '/api/auth/logout' },
    'admin.listUsers': { method: 'GET', path: '/api/admin/users' },
    'admin.createUser': { method: 'POST', path: '/api/admin/users' },
    'locations.buildings': { method: 'GET', path: '/api/locations/buildings' },
    'locations.floors': { method: 'GET', path: '/api/locations/buildings/{buildingId}/floors' },
    'locations.rooms': { method: 'GET', path: '/api/locations/floors/{floorId}/classrooms' },
    'complaints.categories': { method: 'GET', path: '/api/complaints/categories' },
    'complaints.submitAnonymous': { method: 'POST', path: '/api/complaints' },
    'complaints.uploadImage': { method: 'POST', path: '/api/complaints/{complaintId}/images' },
    'complaints.getImages': { method: 'GET', path: '/api/complaints/{complaintId}/images' },
    'complaints.listMine': { method: 'GET', path: '/api/complaints/mine' },
    'complaints.getMine': { method: 'GET', path: '/api/complaints/mine/{trackingReference}' },
    'complaints.listFaculty': { method: 'GET', path: '/api/complaints/staff' },
    'complaints.listAdmin': { method: 'GET', path: '/api/complaints/staff' },
    'complaints.updateStatus': { method: 'PUT', path: '/api/complaints/{complaintId}/status' },
    'complaints.addRemarks': { method: 'PUT', path: '/api/complaints/{complaintId}/status' },
    'complaints.remove': { method: 'PUT', path: '/api/complaints/{complaintId}/remove' },
    'complaints.edit': { method: 'PUT', path: '/api/complaints/{complaintId}' },
    'complaints.comments': { method: 'GET', path: '/api/complaints/{complaintId}/comments' },
    'complaints.addComment': { method: 'POST', path: '/api/complaints/{complaintId}/comments' },
    'complaints.assign': { method: 'PUT', path: '/api/complaints/{complaintId}/assignment' },
    'notifications.list': { method: 'GET', path: '/api/notifications' },
    'notifications.read': { method: 'PUT', path: '/api/notifications/{notificationId}/read' }
  };
  const sampleLocations = Object.freeze({
    'Main Building': Object.freeze({ 'Ground Floor': ['Room 101','Room 102','Computer Lab 103','Canteen','Dining Area','Reception','Other / Common Area'], 'First Floor': ['Room 201','Room 202','Room 203','Library','Other / Common Area'], 'Second Floor': ['Room 301','Room 302','Seminar Hall','Other / Common Area'], 'Third Floor': ['Room 401','Room 402','Lecture Hall','Other / Common Area'] }),
    'Science Building': Object.freeze({ 'Ground Floor': ['Science Lab 101','Science Lab 102','Other / Common Area'], 'First Floor': ['Biology Lab','Research Lab','Other / Common Area'] }),
    'Sports Complex': Object.freeze({ 'Ground Level': ['Gymnasium','Indoor Sports Hall','Badminton Court','Basketball Court','Sports Ground / Field','Changing Room','Other Sports Area'] })
  });

  window.CAMPUSCARE_CONFIG = Object.freeze({
    environment,
    mode: selectedMode === 'mock' ? 'mock' : 'api',
    apiBaseUrl,
    request: Object.freeze({ timeoutMs: 30000, credentials: 'same-origin' }),
    auth: Object.freeze({ headerName: 'Authorization', scheme: 'Bearer' }),
    endpoints: Object.freeze(endpoints),
    sampleLocations
  });
})();
