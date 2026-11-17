document.addEventListener('DOMContentLoaded', () => {
  const body = document.querySelector('[data-complaint-rows]'), empty = document.querySelector('[data-empty]');
  if (window.CAMPUSCARE_CONFIG.mode === 'api') {
    if (!CampusCare.session()?.token) { location.href = 'login.html'; return; }
    const render = rows => {
      const items = rows.map(item => ({ ...item, id: item.trackingReference, date: item.submittedAt, status: CampusCare.displayStatus(item.status) }));
      CampusCare.updateStats(items);
      body.innerHTML = items.slice(0, 5).map(item => `<tr><td class="complaint-title-cell" data-label="Complaint"><span class="complaint-ref">${CampusCare.esc(item.id)}</span><br>${CampusCare.esc(item.title)}<small>${CampusCare.esc(item.category)}</small></td><td data-label="Submitted">${CampusCare.formatDate(item.date)}</td><td data-label="Status">${CampusCare.statusBadge(item.status)}</td><td data-label="Action"><a class="table-action" href="complaint-status.html?id=${encodeURIComponent(item.id)}">Track &rarr;</a></td></tr>`).join('');
      empty.classList.toggle('hidden', items.length > 0);
    };
    const load = async () => {
      try { const response = await CampusCareAPI.request('complaints.listMine'); if (!response?.success) throw new Error(response?.message || 'Could not load your complaints.'); render(response.data || []); }
      catch (error) { CampusCare.toast(error.response?.message || error.message, 'error'); }
    };
    load(); window.addEventListener('campuscare:refresh', load); return;
  }
  const render = () => {
    const rows = CampusCare.forStudent(); CampusCare.updateStats(rows);
    body.innerHTML = rows.slice(0, 5).map(item => `<tr><td class="complaint-title-cell" data-label="Complaint"><span class="complaint-ref">${CampusCare.esc(item.id)}</span><br>${CampusCare.esc(item.title)}<small>${CampusCare.esc(item.category)}</small></td><td data-label="Submitted">${CampusCare.formatDate(item.date)}</td><td data-label="Status">${CampusCare.statusBadge(item.status)}</td><td data-label="Action"><a class="table-action" href="complaint-status.html?id=${encodeURIComponent(item.id)}">Track &rarr;</a></td></tr>`).join('');
    empty.classList.toggle('hidden', rows.length > 0);
  };
  render(); window.addEventListener('campuscare:refresh', render);
});
