(() => {
  // This module is the existing browser-local preview store, not authenticated API storage.
  const KEY = 'campuscare.preview.complaints.v1';
  const SESSION_KEY = 'campuscare.session.v1';
  const PREVIEW_SESSION_KEY = 'campuscare.preview.session.v1';
  // Escape every untrusted string interpolated into the small HTML templates below.
  const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const safeImage = value => typeof value === 'string' && /^data:image\/(?:webp|png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value) ? value : '';
  const seed = [
    { id: 'CC-2026-1042', title: 'Ceiling fan making a loud noise', category: 'Building maintenance', description: 'The fan near the back row makes a loud rattling noise when switched on. It may need a safety check.', building: 'Main Academic Block', floor: 'Second Floor', room: 'Room 204', department: 'Computer Science', date: '2026-11-12', status: 'Pending', remarks: '', ownerId: 'demo-student' },
    { id: 'CC-2026-1038', title: 'Water leakage near lab entrance', category: 'Building maintenance', description: 'Water is collecting by the entrance to the physics lab after the recent rain.', building: 'Science Block', floor: 'Ground Floor', room: 'Other / Common Area', department: 'Physics', date: '2026-11-09', status: 'In Progress', remarks: 'Facilities team has been notified and will inspect the area.', ownerId: 'demo-student' },
    { id: 'CC-2026-1021', title: 'Projector not displaying clearly', category: 'Classroom equipment', description: 'The image from the projector is very dim and difficult to read from the back of the room.', building: 'Main Academic Block', floor: 'First Floor', room: 'Room 103', department: 'Computer Science', date: '2026-11-03', status: 'Resolved', remarks: 'The projector lamp has been replaced.', ownerId: 'demo-student' },
    { id: 'CC-2026-1014', title: 'Loose handrail on east staircase', category: 'Safety and security', description: 'The handrail is loose between the first and second floor. Please check before it becomes a hazard.', building: 'Management Block', floor: 'First Floor', room: 'Other / Common Area', department: 'Administration', date: '2026-10-30', status: 'Pending', remarks: '', ownerId: 'demo-student' },
    { id: 'CC-2026-1008', title: 'Wi-Fi signal drops during lectures', category: 'IT and connectivity', description: 'Connection drops repeatedly in this room during afternoon lectures.', building: 'Science Block', floor: 'Second Floor', room: 'Room 202', department: 'Biology', date: '2026-10-26', status: 'Rejected', remarks: 'This report duplicates an existing campus-wide connectivity ticket.', ownerId: 'demo-student' }
  ];
  const read = () => {
    try { const saved = JSON.parse(localStorage.getItem(KEY)); if (Array.isArray(saved)) return saved; }
    catch (_) { /* corrupted mock data is reset below */ }
    localStorage.setItem(KEY, JSON.stringify(seed));
    return seed.map(item => ({ ...item }));
  };
  const save = rows => localStorage.setItem(KEY, JSON.stringify(rows));
  const session = () => { try { return JSON.parse(sessionStorage.getItem(window.CAMPUSCARE_CONFIG.mode === 'mock' ? PREVIEW_SESSION_KEY : SESSION_KEY)) || null; } catch (_) { return null; } };
  const setSession = value => sessionStorage.setItem(window.CAMPUSCARE_CONFIG.mode === 'mock' ? PREVIEW_SESSION_KEY : SESSION_KEY, JSON.stringify(value));
  const clearSession = () => { sessionStorage.removeItem(SESSION_KEY); sessionStorage.removeItem(PREVIEW_SESSION_KEY); };
  const all = () => read();
  const forStudent = () => read().filter(row => row.ownerId === (session()?.ownerId || 'demo-student'));
  const byId = id => read().find(row => row.id.toLowerCase() === String(id).trim().toLowerCase());
  const add = item => { const rows = read(); rows.unshift(item); save(rows); return item; };
  const update = (id, values) => { const rows = read(); const index = rows.findIndex(row => row.id === id); if (index < 0) return null; rows[index] = { ...rows[index], ...values }; save(rows); return rows[index]; };
  const toast = (message, type = '') => {
    let region = document.querySelector('.toast-region');
    if (!region) { region = document.createElement('div'); region.className = 'toast-region'; region.setAttribute('aria-live', 'polite'); document.body.append(region); }
    const el = document.createElement('div'); el.className = `toast ${type}`; el.textContent = message; region.append(el); setTimeout(() => el.remove(), 3600);
  };
  const formatDate = value => { if (!value) return '—'; const text = String(value); const d = new Date(/^\d{4}-\d{2}-\d{2}$/.test(text) ? `${text}T12:00:00` : text); return Number.isNaN(d.valueOf()) ? value : new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' }).format(d); };
  const displayStatus = status => ({ PENDING: 'Pending', ASSIGNED: 'Assigned', IN_PROGRESS: 'In Progress', RESOLVED: 'Resolved', REJECTED: 'Rejected' }[status] || status || 'Pending');
  const statusClass = status => ({ PENDING: 'status-pending', Pending: 'status-pending', ASSIGNED: 'status-progress', Assigned: 'status-progress', IN_PROGRESS: 'status-progress', 'In Progress': 'status-progress', RESOLVED: 'status-resolved', Resolved: 'status-resolved', REJECTED: 'status-rejected', Rejected: 'status-rejected' }[status] || 'status-pending');
  const statusBadge = status => `<span class="status-badge ${statusClass(status)}">${esc(displayStatus(status))}</span>`;
  const location = item => [item.building, item.floor, item.room].filter(Boolean).join(' · ');
  const currentUser = () => session() || { role: document.body.dataset.dashboard || 'student', name: `${(document.body.dataset.dashboard || 'student')[0].toUpperCase()}${(document.body.dataset.dashboard || 'student').slice(1)} account`, ownerId: 'demo-student' };
  const refreshUser = () => {
    const user = currentUser();
    document.querySelectorAll('[data-user-name]').forEach(el => el.textContent = user.name || 'CampusCare account');
    document.querySelectorAll('[data-first-name]').forEach(el => el.textContent = (user.name || 'Student').split(' ')[0]);
    document.querySelectorAll('.small-avatar').forEach(el => el.textContent = (user.name || 'S')[0].toUpperCase());
  };
  const updateStats = rows => {
    const counts = { total: rows.length, pending: 0, 'in-progress': 0, resolved: 0, rejected: 0 };
    rows.forEach(row => { const key = row.status === 'In Progress' ? 'in-progress' : row.status.toLowerCase(); counts[key] = (counts[key] || 0) + 1; });
    Object.entries(counts).forEach(([key, count]) => document.querySelectorAll(`[data-stat="${key}"]`).forEach(el => el.textContent = count));
  };
  const loadComplaintImages = async (container, complaintId) => {
    if (window.CAMPUSCARE_CONFIG.mode !== 'api' || !container || !complaintId) return;
    container.dataset.imageComplaintId = String(complaintId);
    try {
      const response = await CampusCareAPI.request('complaints.getImages', { pathParams: { complaintId } });
      if (container.dataset.imageComplaintId !== String(complaintId) || !response?.success || !Array.isArray(response.data) || response.data.length === 0) return;
      const gallery = document.createElement('div'); gallery.className = 'complaint-image-gallery';
      response.data.forEach(item => {
        let imageUrl;
        try { imageUrl = new URL(item.signedUrl); } catch (_) { return; }
        if (imageUrl.protocol !== 'https:') return;
        const image = document.createElement('img'); image.className = 'detail-photo'; image.src = imageUrl.href;
        image.alt = 'Photo attached to the complaint'; image.loading = 'lazy'; gallery.append(image);
      });
      if (gallery.childElementCount) container.append(gallery);
    } catch (_) { /* A missing image does not block the complaint details. */ }
  };
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-year]').forEach(el => el.textContent = new Date().getFullYear());
    document.querySelectorAll('[data-current-date]').forEach(el => el.textContent = new Intl.DateTimeFormat(undefined, { month: 'long', day: 'numeric', year: 'numeric' }).format(new Date()).toUpperCase());
    refreshUser();
    // Keep modal keyboard focus inside the dialog and return it to the opener.
    const modalOpeners = new WeakMap();
    document.querySelectorAll('[data-modal]').forEach(backdrop => {
      const observer = new MutationObserver(() => {
        const open = !backdrop.classList.contains('hidden');
        if (open && !modalOpeners.has(backdrop)) {
          modalOpeners.set(backdrop, document.activeElement);
          backdrop.querySelector('[data-close-modal]')?.focus();
        } else if (!open && modalOpeners.has(backdrop)) {
          const opener = modalOpeners.get(backdrop);
          modalOpeners.delete(backdrop);
          if (opener?.isConnected) opener.focus();
        }
      });
      observer.observe(backdrop, { attributes: true, attributeFilter: ['class'] });
    });
    document.addEventListener('keydown', event => {
      if (event.key !== 'Tab') return;
      const backdrop = [...document.querySelectorAll('[data-modal]')].find(item => !item.classList.contains('hidden'));
      if (!backdrop) return;
      const focusable = [...backdrop.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')]
        .filter(item => item.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); backdrop.querySelector('[role="dialog"]')?.focus(); return; }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || !backdrop.contains(document.activeElement))) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !backdrop.contains(document.activeElement))) {
        event.preventDefault(); first.focus();
      }
    });
    document.querySelectorAll('.preview-pill').forEach(el => { if (window.CAMPUSCARE_CONFIG.mode === 'api') { el.textContent = 'Live system'; el.classList.add('live-pill'); } });
    document.querySelectorAll('.demo-hint').forEach(el => { if (window.CAMPUSCARE_CONFIG.mode === 'api') el.innerHTML = '<span>i</span><span><b>College system</b><br>Your requests are sent to the backend.</span>'; });
    if (window.CAMPUSCARE_CONFIG.mode === 'api') {
      document.querySelectorAll('.panel-tag').forEach(el => { if (/preview data/i.test(el.textContent)) el.textContent = 'Live data'; });
      document.querySelectorAll('.stat-card small').forEach(el => { if (/visible in preview/i.test(el.textContent)) el.textContent = 'Visible to your role'; });
      document.querySelectorAll('.privacy-banner p').forEach(el => { el.textContent = 'Staff API responses omit submitter identity. Your account is still associated with your reports for private tracking.'; });
    }
    document.querySelectorAll('.menu-toggle').forEach(button => button.addEventListener('click', () => { const nav = document.querySelector('.top-nav'); const open = nav?.classList.toggle('open'); button.setAttribute('aria-expanded', String(!!open)); }));
    document.querySelectorAll('.mobile-menu').forEach(button => button.addEventListener('click', () => {
      const open = document.querySelector('.sidebar')?.classList.toggle('open') || false;
      button.setAttribute('aria-expanded', String(open));
    }));
    document.querySelectorAll('[data-go-back]').forEach(button => button.addEventListener('click', () => {
      if (history.length > 1) history.back(); else location.href = 'index.html';
    }));
    document.querySelectorAll('[data-logout]').forEach(link => link.addEventListener('click', async event => {
      if (window.CAMPUSCARE_CONFIG.mode !== 'api') { clearSession(); return; }
      event.preventDefault();
      // The API uses stateless short-lived JWTs and logout does not revoke them.
      // Clear the browser credential before navigating; an expired token must never block sign-out.
      clearSession();
      location.replace(link.href);
    }));
    document.querySelectorAll('[data-refresh]').forEach(button => button.addEventListener('click', () => {
      window.dispatchEvent(new CustomEvent('campuscare:refresh'));
      if (window.CAMPUSCARE_CONFIG.mode === 'mock') toast('Preview data refreshed.', 'success');
    }));
    document.querySelectorAll('[data-notice]').forEach(link => link.addEventListener('click', event => { event.preventDefault(); toast(link.dataset.notice); }));
    document.querySelectorAll('[data-profile-link]').forEach(link => link.addEventListener('click', event => { event.preventDefault(); toast('Profile management will connect when the backend supports it.'); }));
  });
  let redirectingForExpiredSession = false;
  window.addEventListener('campuscare:api-error', event => {
    if (event.detail?.operation === 'auth.logout') return;
    if (event.detail?.status === 401 && !String(event.detail.operation || '').endsWith('.login')
        && event.detail.operation !== 'auth.logout' && session()?.token) {
      clearSession();
      if (!redirectingForExpiredSession && !['login.html', 'register.html'].includes(location.pathname.split('/').pop())) {
        redirectingForExpiredSession = true;
        location.replace('login.html?reason=session-expired');
      }
      return;
    }
    if (event.detail?.message) toast(event.detail.message, 'error');
  });
  window.CampusCare = { esc, safeImage, read, save, all, forStudent, byId, add, update, session, setSession, clearSession, toast, formatDate, displayStatus, statusClass, statusBadge, location, updateStats, loadComplaintImages, currentUser, KEY };
})();
