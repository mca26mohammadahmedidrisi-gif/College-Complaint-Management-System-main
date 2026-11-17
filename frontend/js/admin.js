document.addEventListener('DOMContentLoaded', () => {
  if (window.CAMPUSCARE_CONFIG.mode === 'api') {
    const user = CampusCare.session(); if (!user?.token || user.role !== 'admin') { location.href = 'login.html'; return; }
    const rowsEl = document.querySelector('[data-admin-rows]'), empty = document.querySelector('[data-empty]');
    const statusFilter = document.querySelector('[data-status-filter]'), search = document.querySelector('[data-search]');
    const selects = [...document.querySelectorAll('[data-filter]')], modal = document.querySelector('[data-modal]'), content = document.querySelector('[data-modal-content]');
    const palette = { Pending: '#f3ae36', Assigned: '#5185ea', 'In Progress': '#5185ea', Resolved: '#2aab69', Rejected: '#d65c66' };
    const statuses = [['PENDING','Pending'],['ASSIGNED','Assigned'],['IN_PROGRESS','In Progress'],['RESOLVED','Resolved'],['REJECTED','Rejected']];
    let rows = [];
    const facultyPanel = document.querySelector('[data-faculty-account-panel]');
    const directory = document.querySelector('[data-account-directory]');
    const accountRows = document.querySelector('[data-account-rows]');
    const accountSearch = document.querySelector('[data-account-search]');
    const accountCount = document.querySelector('[data-account-count]');
    const accountNotice = document.querySelector('[data-account-notice]');
    let accounts = [];
    const renderAccounts = () => {
      if (!accountRows) return;
      const query = String(accountSearch?.value || '').trim().toLowerCase();
      const filtered = accounts.filter(account => [account.fullName, account.email, account.role, account.departmentName]
        .some(value => String(value || '').toLowerCase().includes(query)));
      accountCount.textContent = `(${accounts.length})`;
      accountRows.innerHTML = filtered.map(account => `<tr><td data-label="Name">${CampusCare.esc(account.fullName)}</td><td data-label="Email">${CampusCare.esc(account.email)}</td><td data-label="Role">${CampusCare.esc(account.role)}</td><td data-label="Department">${CampusCare.esc(account.departmentName || '—')}</td><td data-label="Registered">${CampusCare.formatDate(account.createdAt)}${account.active ? '' : ' · Inactive'}</td></tr>`).join('')
        || `<tr><td colspan="5">${query ? 'No accounts match your search.' : 'No accounts are registered yet.'}</td></tr>`;
    };
    const loadAccounts = async () => {
      if (!directory) return;
      directory.hidden = false;
      accountNotice.textContent = 'Loading accounts…';
      try {
        const response = await CampusCareAPI.request('admin.listUsers');
        if (!response?.success || !Array.isArray(response.data)) throw new Error(response?.message || 'The account list could not be loaded.');
        accounts = response.data;
        accountNotice.textContent = '';
        renderAccounts();
      } catch (error) {
        accountNotice.className = 'form-notice error-text';
        accountNotice.textContent = error.response?.message || error.message || 'The account list could not be loaded.';
      }
    };
    accountSearch?.addEventListener('input', renderAccounts);
    window.addEventListener('campuscare:refresh', loadAccounts);
    const facultyForm = document.querySelector('[data-faculty-account-form]');
    if (facultyPanel && facultyForm) {
      const department = facultyForm.elements.departmentId;
      const notice = facultyForm.querySelector('[data-faculty-account-notice]');
      facultyPanel.hidden = false;
      CampusCareAPI.request('departments').then(response => {
        if (!response?.success || !Array.isArray(response.data)) throw new Error('Departments could not be loaded.');
        department.replaceChildren(new Option('Choose a department', ''));
        response.data.forEach(item => department.add(new Option(item.departmentName, item.departmentId)));
        if (!response.data.length) throw new Error('Add departments before creating faculty accounts.');
      }).catch(error => {
        department.replaceChildren(new Option('Departments unavailable', ''));
        department.disabled = true;
        notice.className = 'form-notice error-text';
        notice.textContent = error.response?.message || error.message || 'Departments could not be loaded.';
      });
      facultyForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (!facultyForm.reportValidity()) return;
        const values = new FormData(facultyForm);
        notice.className = 'form-notice';
        notice.textContent = 'Creating faculty account...';
        try {
          const response = await CampusCareAPI.request('admin.createUser', {
            method: 'POST',
            body: {
              fullName: String(values.get('fullName')).trim(),
              email: String(values.get('email')).trim(),
              password: String(values.get('password')),
              departmentId: String(values.get('departmentId')),
              role: 'FACULTY'
            },
            loadingTarget: facultyForm.querySelector('[type="submit"]')
          });
          if (!response?.success) throw new Error(response?.message || 'Faculty account could not be created.');
          facultyForm.reset();
          notice.className = 'form-notice success-text';
          notice.textContent = `Faculty account created for ${response.data?.user?.email || 'the selected email'}. Share the initial password securely.`;
          await loadAccounts();
        } catch (error) {
          notice.className = 'form-notice error-text';
          notice.textContent = error.response?.message || error.message || 'Faculty account could not be created.';
        }
      });
    }
    const allowedNext = { PENDING: ['PENDING','ASSIGNED','IN_PROGRESS','REJECTED'], ASSIGNED: ['ASSIGNED','IN_PROGRESS','REJECTED'], IN_PROGRESS: ['IN_PROGRESS','RESOLVED','REJECTED'], RESOLVED: ['RESOLVED'], REJECTED: ['REJECTED'] };
    const statusOptions = current => (allowedNext[current] || [current]).map(value => {
      const label = statuses.find(([status]) => status === value)?.[1] || value;
      return `<option value="${value}" ${value === current ? 'selected' : ''}>${label}</option>`;
    }).join('');
    const valueFor = (row, key) => key === 'room' ? row.classroom : row[key];
    const load = async () => { try { const response = await CampusCareAPI.request('complaints.listAdmin'); if (!response?.success) throw new Error(response?.message || 'Could not load complaints.'); rows = response.data || []; fillFilters(); render(); } catch (error) { CampusCare.toast(error.response?.message || error.message, 'error'); } };
    const fillFilters = () => selects.forEach(select => { const key = select.dataset.filter, current = select.value; const values = [...new Set(rows.map(row => valueFor(row, key)).filter(Boolean))].sort(); const label = { department:'All departments', building:'All buildings', floor:'All floors', room:'All rooms' }[key] || 'All'; select.replaceChildren(new Option(label, 'all')); values.forEach(value => select.add(new Option(value, value))); if (values.includes(current)) select.value = current; });
    const filtered = () => { const term = search.value.trim().toLowerCase(); return rows.filter(row => { const status = CampusCare.displayStatus(row.status); if (statusFilter.value !== 'all' && status !== statusFilter.value) return false; if (selects.some(select => select.value !== 'all' && String(valueFor(row, select.dataset.filter)) !== select.value)) return false; return !term || [row.trackingReference,row.title,row.category,row.building,row.floor,row.classroom,row.department,row.description].some(value => String(value || '').toLowerCase().includes(term)); }); };
    const renderChart = () => { const counts = { Pending:0, Assigned:0, 'In Progress':0, Resolved:0, Rejected:0 }; rows.forEach(row => counts[CampusCare.displayStatus(row.status)]++); const total = rows.length; let start = 0; const colors = Object.values(palette); const segments = Object.entries(counts).map(([name,count],index) => { const from = total ? start / total * 100 : 0; start += count; return `${colors[index]} ${from}% ${total ? start / total * 100 : 0}%`; }); document.querySelector('[data-chart]').style.background = total ? `conic-gradient(${segments.join(',')})` : '#e9eef5'; document.querySelector('[data-chart-total]').textContent = total; document.querySelector('[data-chart-legend]').innerHTML = Object.entries(counts).map(([name,count]) => `<div class="legend-row"><i class="legend-swatch" style="background:${palette[name]}"></i>${CampusCare.esc(name)}<b>${count}</b></div>`).join(''); };
    const render = () => { CampusCare.updateStats(rows.map(row => ({ status: CampusCare.displayStatus(row.status) }))); renderChart(); const activity = document.querySelector('[data-activity-list]'); activity.innerHTML = rows.slice(0, 4).map(row => `<div class="activity-item"><i class="activity-dot" style="background:${palette[CampusCare.displayStatus(row.status)] || palette.Pending}"></i><div><b>${CampusCare.esc(row.title)}</b><small>${CampusCare.esc(row.trackingReference)} Â· ${CampusCare.formatDate(row.submittedAt)}</small></div>${CampusCare.statusBadge(row.status)}</div>`).join('') || '<div class="empty-state"><p>No reports yet.</p></div>'; const list = filtered(); rowsEl.innerHTML = list.map(row => `<tr><td class="complaint-title-cell" data-label="Complaint"><span class="complaint-ref">${CampusCare.esc(row.trackingReference)}</span><br>${CampusCare.esc(row.title)}<small>${CampusCare.esc(row.category)}</small></td><td data-label="Location">${CampusCare.esc(row.building)}<small>${CampusCare.esc(row.floor)} Â· ${CampusCare.esc(row.classroom)}</small></td><td data-label="Department">${CampusCare.esc(row.department || 'Unspecified')}</td><td data-label="Submitted">${CampusCare.formatDate(row.submittedAt)}</td><td data-label="Status">${CampusCare.statusBadge(row.status)}${row.removed ? '<small>Removed from faculty queue</small>' : ''}</td><td data-label="Action"><button class="table-action" data-view="${row.complaintId}">Manage â†’</button></td></tr>`).join(''); empty.classList.toggle('hidden', list.length > 0); };
    const close = () => { modal.classList.add('hidden'); document.body.style.overflow = ''; };
    const assignmentMarkup = row => {
      const faculty = accounts.filter(account => account.role === 'FACULTY' && account.active && account.departmentId === row.departmentId);
      const options = faculty.map(account => `<option value="${CampusCare.esc(account.userId)}" ${account.userId === row.assignedToUserId ? 'selected' : ''}>${CampusCare.esc(account.fullName)}</option>`).join('');
      return row.removed ? '' : `<label class="field-label" for="assignment-${row.complaintId}">Assigned faculty</label><select id="assignment-${row.complaintId}" data-assignee><option value="">Unassigned</option>${options}</select><button class="button button-outline" type="button" data-save-assignment="${row.complaintId}" ${faculty.length ? '' : 'disabled'}>Save assignment</button>${faculty.length ? '' : '<small>No active faculty account exists in this department yet.</small>'}`;
    };
    const open = id => { const row = rows.find(item => item.complaintId === id); if (!row) return; const history = (row.history || []).map(item => `<article class="complaint-history-entry"><b>${CampusCare.esc(CampusCare.displayStatus(item.newStatus))}</b><small>${CampusCare.formatDate(item.changedAt)} | ${CampusCare.esc(item.changedBy || 'Staff')}</small>${item.remarks ? `<p>${CampusCare.esc(item.remarks)}</p>` : ''}</article>`).join(''); const historyMarkup = history ? `<section class="complaint-history"><h3>Status history</h3>${history}</section>` : ''; content.innerHTML = `<span class="complaint-ref">${CampusCare.esc(row.trackingReference)}</span><span class="anonymous-badge">Anonymous complaint</span><h2 id="detail-title">${CampusCare.esc(row.title)}</h2>${CampusCare.statusBadge(row.status)}${row.removed ? `<div class="remarks-box"><b>Removed from faculty queue</b><p>${CampusCare.esc(row.removalReason || 'No reason recorded')}</p></div>` : ''}<p>${CampusCare.esc(row.description)}</p><div class="detail-grid"><div class="detail-item"><small>Category</small><b>${CampusCare.esc(row.category)}</b></div><div class="detail-item"><small>Department</small><b>${CampusCare.esc(row.department || 'Unspecified')}</b></div><div class="detail-item"><small>Building</small><b>${CampusCare.esc(row.building)}</b></div><div class="detail-item"><small>Floor</small><b>${CampusCare.esc(row.floor)}</b></div><div class="detail-item"><small>Classroom</small><b>${CampusCare.esc(row.classroom)}</b></div><div class="detail-item"><small>Submitted</small><b>${CampusCare.formatDate(row.submittedAt)}</b></div></div>${historyMarkup}${assignmentMarkup(row)}<div data-complaint-conversation></div>${row.removed ? '<p>This complaint is archived from the active faculty queue.</p>' : `<label class="field-label" for="modal-status">Complaint status</label><select id="modal-status" data-modal-status>${statusOptions(row.status)}</select><label class="field-label" for="modal-remarks">Administrative remarks</label><textarea class="remarks-input" id="modal-remarks">${CampusCare.esc(row.remarks || '')}</textarea><button class="button button-primary" data-save-update="${row.complaintId}">Save complaint update</button>`}`; modal.classList.remove('hidden'); document.body.style.overflow = 'hidden'; CampusCare.loadComplaintConversation(content.querySelector('[data-complaint-conversation]'), row.complaintId, { allowReply: !row.removed }); };
    const save = async (id,status,remarks) => { try { const response = await CampusCareAPI.request('complaints.updateStatus',{method:'PUT',pathParams:{complaintId:id},body:{status,remarks:remarks || null}}); if (!response?.success) throw new Error(response?.message || 'Could not update complaint.'); CampusCare.toast('Complaint update saved.','success'); close(); await load(); } catch (error) { CampusCare.toast(error.response?.message || error.message,'error'); } };
    rowsEl.addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button) { open(button.dataset.view); CampusCare.loadComplaintImages(content, button.dataset.view); } });
    content.addEventListener('click', event => { const button = event.target.closest('[data-save-update]'); if (button) save(button.dataset.saveUpdate,content.querySelector('[data-modal-status]').value,content.querySelector('#modal-remarks').value.trim()); });
    content.addEventListener('click', async event => {
      const button = event.target.closest('[data-save-assignment]');
      if (!button) return;
      try {
        const response = await CampusCareAPI.request('complaints.assign', { method: 'PUT', pathParams: { complaintId: button.dataset.saveAssignment }, body: { facultyUserId: content.querySelector('[data-assignee]').value || null } });
        if (!response?.success) throw new Error(response?.message || 'Assignment could not be saved.');
        CampusCare.toast('Faculty assignment saved.', 'success'); close(); await load();
      } catch (error) { CampusCare.toast(error.response?.message || error.message, 'error'); }
    });
    document.querySelector('[data-close-modal]').addEventListener('click',close); modal.addEventListener('click',event=>{if(event.target===modal)close();}); document.addEventListener('keydown',event=>{if(event.key==='Escape')close();});
    [statusFilter,search,...selects].forEach(el=>el.addEventListener('input',render));
    document.querySelectorAll('[data-nav-filter]').forEach(link=>link.addEventListener('click',()=>{statusFilter.value=link.dataset.navFilter;setTimeout(render,0);}));
    document.querySelector('[data-clear-filters]').addEventListener('click',()=>{statusFilter.value='all';search.value='';selects.forEach(select=>select.value='all');render();});
    window.addEventListener('campuscare:refresh',load); loadAccounts().then(load); return;
  }
  const rowsEl = document.querySelector('[data-admin-rows]'); const empty = document.querySelector('[data-empty]');
  const statusFilter = document.querySelector('[data-status-filter]'); const search = document.querySelector('[data-search]');
  const selects = [...document.querySelectorAll('[data-filter]')]; const modal = document.querySelector('[data-modal]'); const content = document.querySelector('[data-modal-content]');
  const palette = { Pending: '#f3ae36', 'In Progress': '#5185ea', Resolved: '#2aab69', Rejected: '#d65c66' };
  const option = value => `<option value="${CampusCare.esc(value)}">${CampusCare.esc(value)}</option>`;
  const fillFilters = rows => selects.forEach(select => {
    const key = select.dataset.filter; const current = select.value; const values = [...new Set(rows.map(row => key === 'room' ? row.room : row[key]).filter(Boolean))].sort();
    const label = { department: 'All departments', building: 'All buildings', floor: 'All floors', room: 'All rooms' }[key];
    select.innerHTML = option('all').replace('>all<', `>${label}<`) + values.map(option).join('');
    if (values.includes(current)) select.value = current;
  });
  const filtered = () => {
    const term = search.value.trim().toLowerCase();
    return CampusCare.all().filter(row => {
      if (statusFilter.value !== 'all' && row.status !== statusFilter.value) return false;
      if (selects.some(select => select.value !== 'all' && String(row[select.dataset.filter === 'room' ? 'room' : select.dataset.filter]) !== select.value)) return false;
      return !term || [row.id,row.title,row.category,row.building,row.floor,row.room,row.department,row.description].some(value => String(value || '').toLowerCase().includes(term));
    });
  };
  const renderChart = rows => {
    const counts = { Pending: 0, 'In Progress': 0, Resolved: 0, Rejected: 0 }; rows.forEach(row => counts[row.status]++);
    const total = rows.length; const colors = Object.values(palette); let start = 0;
    const segments = Object.entries(counts).map(([name, count], index) => { const from = total ? start / total * 100 : 0; start += count; const to = total ? start / total * 100 : 0; return `${colors[index]} ${from}% ${to}%`; });
    document.querySelector('[data-chart]').style.background = total ? `conic-gradient(${segments.join(',')})` : '#e9eef5';
    document.querySelector('[data-chart-total]').textContent = total;
    document.querySelector('[data-chart-legend]').innerHTML = Object.entries(counts).map(([name,count]) => `<div class="legend-row"><i class="legend-swatch" style="background:${palette[name]}"></i>${CampusCare.esc(name)}<b>${count}</b></div>`).join('');
    const activity = document.querySelector('[data-activity-list]');
    activity.innerHTML = rows.slice(0, 4).map(row => `<div class="activity-item"><i class="activity-dot" style="background:${palette[row.status]}"></i><div><b>${CampusCare.esc(row.title)}</b><small>${CampusCare.esc(row.id)} Â· ${CampusCare.formatDate(row.date)}</small></div>${CampusCare.statusBadge(row.status)}</div>`).join('') || '<div class="empty-state"><p>No reports yet.</p></div>';
  };
  const render = () => {
    const all = CampusCare.all(); CampusCare.updateStats(all); fillFilters(all); renderChart(all);
    const rows = filtered();
    rowsEl.innerHTML = rows.map(row => `<tr><td class="complaint-title-cell" data-label="Complaint"><span class="complaint-ref">${CampusCare.esc(row.id)}</span><br>${CampusCare.esc(row.title)}<small>${CampusCare.esc(row.category)}</small></td><td data-label="Location">âŒ– ${CampusCare.esc(row.building)}<small>${CampusCare.esc(row.floor)} Â· ${CampusCare.esc(row.room)}</small></td><td data-label="Department">${CampusCare.esc(row.department || 'Unspecified')}</td><td data-label="Submitted">${CampusCare.formatDate(row.date)}</td><td data-label="Status">${CampusCare.statusBadge(row.status)}${row.removed ? '<small>Removed from faculty queue</small>' : ''}</td><td data-label="Action"><button class="table-action" data-view="${CampusCare.esc(row.id)}">Manage â†’</button></td></tr>`).join('');
    empty.classList.toggle('hidden', rows.length > 0);
  };
  const open = id => {
    const row = CampusCare.byId(id); if (!row) return;
    content.innerHTML = `<span class="complaint-ref">${CampusCare.esc(row.id)}</span> <span class="anonymous-badge">Anonymous complaint</span><h2 id="detail-title">${CampusCare.esc(row.title)}</h2>${CampusCare.statusBadge(row.status)}${row.removed ? `<div class="remarks-box"><b>Removed from faculty queue</b><p>${CampusCare.esc(row.removalReason || 'No reason recorded')}</p></div>` : ''}<p class="detail-description">${CampusCare.esc(row.description)}</p><div class="detail-grid"><div class="detail-item"><small>Category</small><b>${CampusCare.esc(row.category)}</b></div><div class="detail-item"><small>Submitted</small><b>${CampusCare.formatDate(row.date)}</b></div><div class="detail-item"><small>Building</small><b>âŒ– ${CampusCare.esc(row.building)}</b></div><div class="detail-item"><small>Floor</small><b>âŒ– ${CampusCare.esc(row.floor)}</b></div><div class="detail-item"><small>Classroom / room</small><b>âŒ– ${CampusCare.esc(row.room)}</b></div><div class="detail-item"><small>Department</small><b>${CampusCare.esc(row.department || 'Unspecified')}</b></div></div>${CampusCare.safeImage(row.image) ? `<img class="detail-photo" src="${CampusCare.safeImage(row.image)}" alt="Image attached to complaint">` : ''}${row.removed ? '<p>This complaint is archived from the active faculty queue.</p>' : `<label class="field-label" for="modal-status">Complaint status</label><select id="modal-status" data-modal-status>${['Pending','In Progress','Resolved','Rejected'].map(value => `<option ${row.status === value ? 'selected' : ''}>${value}</option>`).join('')}</select><label class="field-label" for="modal-remarks">Administrative remarks</label><textarea class="remarks-input" id="modal-remarks" placeholder="Add an update for the studentâ€¦">${CampusCare.esc(row.remarks || '')}</textarea><button class="button button-primary" data-save-update="${CampusCare.esc(row.id)}">Save complaint update</button>`}`;
    modal.classList.remove('hidden'); document.body.style.overflow = 'hidden';
  };
  const close = () => { modal.classList.add('hidden'); document.body.style.overflow = ''; };
  rowsEl.addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button) open(button.dataset.view); });
  content.addEventListener('click', event => { const button = event.target.closest('[data-save-update]'); if (!button) return; CampusCare.update(button.dataset.saveUpdate, { status: content.querySelector('[data-modal-status]').value, remarks: content.querySelector('#modal-remarks').value.trim() }); close(); render(); CampusCare.toast('Complaint updated in this preview.', 'success'); });
  document.querySelector('[data-close-modal]').addEventListener('click', close); modal.addEventListener('click', event => { if (event.target === modal) close(); }); document.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });
  [statusFilter,search,...selects].forEach(el => el.addEventListener('input', render));
  document.querySelectorAll('[data-nav-filter]').forEach(link => link.addEventListener('click', () => { statusFilter.value = link.dataset.navFilter; setTimeout(render, 0); }));
  document.querySelector('[data-clear-filters]').addEventListener('click', () => { statusFilter.value = 'all'; search.value = ''; selects.forEach(select => select.value = 'all'); render(); });
  window.addEventListener('campuscare:refresh', render); render();
});
