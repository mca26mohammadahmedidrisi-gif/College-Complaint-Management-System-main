/* API contract for the Java backend. Use ?mode=mock to open the browser preview. */
(() => {
  const hostname = window.location.hostname;
  const isLocal = window.location.protocol === 'file:' || hostname === 'localhost' || hostname === '127.0.0.1';
  const environment = isLocal ? 'development' : 'production';
  const queryMode = new URLSearchParams(window.location.search).get('mode');
  if (queryMode === 'mock' || queryMode === 'api') sessionStorage.setItem('campuscare.mode', queryMode);
  const selectedMode = queryMode || sessionStorage.getItem('campuscare.mode') || 'api';
  const productionApiUrl = window.CAMPUSCARE_API_BASE_URL || '';
  const apiBaseUrl = environment === 'development' ? 'http://localhost:8080' : productionApiUrl;
  const endpoints = {
    departments: { method: 'GET', path: '/api/departments' },
    'auth.student.register': { method: 'POST', path: '/api/auth/register' },
    'auth.student.login': { method: 'POST', path: '/api/auth/login' },
    'auth.faculty.login': { method: 'POST', path: '/api/auth/login' },
    'auth.admin.login': { method: 'POST', path: '/api/auth/login' },
    'auth.logout': { method: 'POST', path: '/api/auth/logout' },
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
    'complaints.addRemarks': { method: 'PUT', path: '/api/complaints/{complaintId}/status' }
  };
  const sampleLocations = Object.freeze({
    'Main Academic Block': Object.freeze({ 'Ground Floor': ['Room 001','Room 002','Other / Common Area'], 'First Floor': ['Room 101','Room 102','Room 103','Other / Common Area'], 'Second Floor': ['Room 201','Room 202','Room 204','Other / Common Area'] }),
    'Science Block': Object.freeze({ 'Ground Floor': ['Physics Lab','Chemistry Lab','Other / Common Area'], 'First Floor': ['Biology Lab','Room 101','Other / Common Area'], 'Second Floor': ['Room 201','Room 202','Other / Common Area'] }),
    'Management Block': Object.freeze({ 'Ground Floor': ['Reception','Seminar Hall','Other / Common Area'], 'First Floor': ['Room 101','Room 102','Other / Common Area'] })
  });

  window.CAMPUSCARE_CONFIG = Object.freeze({
    environment,
    mode: selectedMode === 'mock' ? 'mock' : 'api',
    apiBaseUrl,
    request: Object.freeze({ timeoutMs: 15000, credentials: 'same-origin' }),
    auth: Object.freeze({ headerName: 'Authorization', scheme: 'Bearer' }),
    endpoints: Object.freeze(endpoints),
    sampleLocations
  });
})();
