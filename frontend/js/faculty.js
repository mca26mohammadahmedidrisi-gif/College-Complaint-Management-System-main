document.addEventListener('DOMContentLoaded', () => {
  if (window.CAMPUSCARE_CONFIG.mode === 'api') {
    const user = CampusCare.session(); if (!user?.token || !['faculty', 'admin'].includes(user.role)) { location.href = 'login.html'; return; }
    const list = document.querySelector('[data-staff-list]'), empty = document.querySelector('[data-empty]');
    const filter = document.querySelector('[data-status-filter]'), modal = document.querySelector('[data-modal]'), content = document.querySelector('[data-modal-content]');
    let rows = [];
    const statuses = [['PENDING','Pending'],['ASSIGNED','Assigned'],['IN_PROGRESS','In Progress'],['RESOLVED','Resolved'],['REJECTED','Rejected']];
    const optionList = current => statuses.map(([value,label]) => `<option value="${value}" ${value === current ? 'selected' : ''}>${label}</option>`).join('');
    const load = async () => {
      try { const response = await CampusCareAPI.request('complaints.listFaculty'); if (!response?.success) throw new Error(response?.message || 'Could not load complaints.'); rows = response.data || []; render(); }
      catch (error) { CampusCare.toast(error.response?.message || error.message, 'error'); }
    };
    const card = row => `<article class="staff-card"><div class="staff-card-top"><div><span class="complaint-ref">${CampusCare.esc(row.trackingReference)}</span><h3>${CampusCare.esc(row.title)}</h3></div><span class="anonymous-badge">Anonymous</span>${CampusCare.statusBadge(row.status)}</div><div class="staff-card-meta"><span>${CampusCare.esc(row.category)}</span><span>${CampusCare.formatDate(row.submittedAt)}</span></div><div class="location-line"><b>${CampusCare.esc(row.building)}</b> › ${CampusCare.esc(row.floor)} › ${CampusCare.esc(row.classroom)}</div><p class="staff-card-description">${CampusCare.esc(row.description)}</p><div class="staff-card-actions"><label class="sr-only" for="status-${row.complaintId}">Update complaint status</label><select id="status-${row.complaintId}" data-update-status="${row.complaintId}">${optionList(row.status)}</select><button type="button" data-view="${row.complaintId}">Details &amp; remarks</button></div></article>`;
    const render = () => { const visible = rows.filter(row => filter.value === 'all' || CampusCare.displayStatus(row.status) === filter.value); list.innerHTML = visible.map(card).join(''); empty.classList.toggle('hidden', visible.length > 0); CampusCare.updateStats(rows.map(row => ({ status: CampusCare.displayStatus(row.status) }))); };
    const close = () => { modal.classList.add('hidden'); document.body.style.overflow = ''; };
    const open = id => { const row = rows.find(item => item.complaintId === id); if (!row) return; content.innerHTML = `<span class="complaint-ref">${CampusCare.esc(row.trackingReference)}</span><span class="anonymous-badge">Anonymous complaint</span><h2 id="detail-title">${CampusCare.esc(row.title)}</h2>${CampusCare.statusBadge(row.status)}<p>${CampusCare.esc(row.description)}</p><p>${CampusCare.esc(row.building)} · ${CampusCare.esc(row.floor)} · ${CampusCare.esc(row.classroom)}</p><label class="field-label" for="modal-status">Update status</label><select id="modal-status" data-modal-status>${optionList(row.status)}</select><label class="field-label" for="modal-remarks">Administrative remarks</label><textarea class="remarks-input" id="modal-remarks">${CampusCare.esc(row.remarks || '')}</textarea><button class="button button-primary" data-save-update="${row.complaintId}">Save update</button>`; modal.classList.remove('hidden'); document.body.style.overflow = 'hidden'; };
    const save = async (id, status, remarks) => {
      try { const response = await CampusCareAPI.request('complaints.updateStatus', { method: 'PUT', pathParams: { complaintId: id }, body: { status, remarks: remarks || null } }); if (!response?.success) throw new Error(response?.message || 'Could not update complaint.'); CampusCare.toast('Complaint update saved.', 'success'); close(); await load(); }
      catch (error) { CampusCare.toast(error.response?.message || error.message, 'error'); }
    };
    list.addEventListener('change', event => { const id = event.target.dataset.updateStatus; if (!id) return; const row = rows.find(item => item.complaintId === id); save(id, event.target.value, row?.remarks || null); });
    list.addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button) { open(button.dataset.view); CampusCare.loadComplaintImages(content, button.dataset.view); } });
    content.addEventListener('click', event => { const button = event.target.closest('[data-save-update]'); if (button) save(button.dataset.saveUpdate, content.querySelector('[data-modal-status]').value, content.querySelector('#modal-remarks').value.trim()); });
    filter.addEventListener('change', render); document.querySelector('[data-close-modal]').addEventListener('click', close); modal.addEventListener('click', event => { if (event.target === modal) close(); }); document.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });
    document.querySelectorAll('[data-nav-filter]').forEach(link => link.addEventListener('click', () => { filter.value = link.dataset.navFilter; setTimeout(render, 0); }));
    window.addEventListener('campuscare:refresh', load); load(); return;
  }
  const list = document.querySelector('[data-staff-list]'); const empty = document.querySelector('[data-empty]');
  const filter = document.querySelector('[data-status-filter]'); const modal = document.querySelector('[data-modal]'); const content = document.querySelector('[data-modal-content]');
  const render = () => {
    const rows = CampusCare.all(); CampusCare.updateStats(rows);
    const visible = rows.filter(row => filter.value === 'all' || row.status === filter.value);
    list.innerHTML = visible.map(row => `<article class="staff-card"><div class="staff-card-top"><div><span class="complaint-ref">${CampusCare.esc(row.id)}</span><h3>${CampusCare.esc(row.title)}</h3></div><span class="anonymous-badge">Anonymous</span>${CampusCare.statusBadge(row.status)}</div><div class="staff-card-meta"><span>${CampusCare.esc(row.category)}</span><span>${CampusCare.formatDate(row.date)}</span></div><div class="location-line"><span>⌖</span><b>${CampusCare.esc(row.building)}</b><span>›</span>${CampusCare.esc(row.floor)}<span>›</span>${CampusCare.esc(row.room)}</div><p class="staff-card-description">${CampusCare.esc(row.description)}</p>${CampusCare.safeImage(row.image) ? `<img class="detail-photo staff-card-photo" src="${CampusCare.safeImage(row.image)}" alt="Attached campus issue photo">` : ''}<div class="staff-card-actions"><label class="sr-only" for="status-${CampusCare.esc(row.id)}">Update complaint status</label><select id="status-${CampusCare.esc(row.id)}" data-update-status="${CampusCare.esc(row.id)}"><option ${row.status === 'Pending' ? 'selected' : ''}>Pending</option><option ${row.status === 'In Progress' ? 'selected' : ''}>In Progress</option><option ${row.status === 'Resolved' ? 'selected' : ''}>Resolved</option><option ${row.status === 'Rejected' ? 'selected' : ''}>Rejected</option></select><button type="button" data-view="${CampusCare.esc(row.id)}">Details & remarks</button></div></article>`).join('');
    empty.classList.toggle('hidden', visible.length > 0);
  };
  const open = id => {
    const row = CampusCare.byId(id); if (!row) return;
    content.innerHTML = `<span class="complaint-ref">${CampusCare.esc(row.id)}</span> <span class="anonymous-badge">Anonymous complaint</span><h2 id="detail-title">${CampusCare.esc(row.title)}</h2>${CampusCare.statusBadge(row.status)}<p class="detail-description">${CampusCare.esc(row.description)}</p><div class="detail-grid"><div class="detail-item"><small>Category</small><b>${CampusCare.esc(row.category)}</b></div><div class="detail-item"><small>Submitted</small><b>${CampusCare.formatDate(row.date)}</b></div><div class="detail-item"><small>Building</small><b>⌖ ${CampusCare.esc(row.building)}</b></div><div class="detail-item"><small>Floor</small><b>⌖ ${CampusCare.esc(row.floor)}</b></div><div class="detail-item"><small>Classroom / room</small><b>⌖ ${CampusCare.esc(row.room)}</b></div><div class="detail-item"><small>Department</small><b>${CampusCare.esc(row.department || 'Unspecified')}</b></div></div>${CampusCare.safeImage(row.image) ? `<img class="detail-photo" src="${CampusCare.safeImage(row.image)}" alt="Image attached to complaint">` : ''}<label class="field-label" for="modal-status">Update status</label><select id="modal-status" data-modal-status>${['Pending','In Progress','Resolved','Rejected'].map(value => `<option ${row.status === value ? 'selected' : ''}>${value}</option>`).join('')}</select><label class="field-label" for="modal-remarks">Administrative remarks</label><textarea class="remarks-input" id="modal-remarks" placeholder="Add a note for the student…">${CampusCare.esc(row.remarks || '')}</textarea><button class="button button-primary" data-save-update="${CampusCare.esc(row.id)}">Save update</button>`;
    modal.classList.remove('hidden'); document.body.style.overflow = 'hidden';
  };
  const close = () => { modal.classList.add('hidden'); document.body.style.overflow = ''; };
  list.addEventListener('change', event => { const id = event.target.dataset.updateStatus; if (!id) return; CampusCare.update(id, { status: event.target.value }); CampusCare.toast('Complaint status updated in this preview.', 'success'); render(); });
  list.addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button) open(button.dataset.view); });
  document.querySelectorAll('[data-nav-filter]').forEach(link => link.addEventListener('click', () => { filter.value = link.dataset.navFilter; setTimeout(render, 0); }));
  content.addEventListener('click', event => {
    const save = event.target.closest('[data-save-update]'); if (!save) return;
    CampusCare.update(save.dataset.saveUpdate, { status: content.querySelector('[data-modal-status]').value, remarks: content.querySelector('#modal-remarks').value.trim() });
    close(); render(); CampusCare.toast('Status and remarks saved in this preview.', 'success');
  });
  document.querySelector('[data-close-modal]').addEventListener('click', close);
  modal.addEventListener('click', event => { if (event.target === modal) close(); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });
  filter.addEventListener('change', render); window.addEventListener('campuscare:refresh', render); render();
});
