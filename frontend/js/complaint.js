document.addEventListener('DOMContentLoaded', () => {
  const apiMode = window.CAMPUSCARE_CONFIG.mode === 'api';
  const unwrap = response => {
    if (!response?.success) throw new Error(response?.message || 'The server returned an unexpected response.');
    return response.data;
  };
  const form = document.getElementById('complaint-form');
  if (form) {
    if (apiMode && !CampusCare.session()?.token) location.href = 'login.html';
    const building = form.elements.building, floor = form.elements.floor, room = form.elements.room;
    const category = form.elements.category;
    const selectedNames = { buildings: new Map(), floors: new Map(), rooms: new Map(), categories: new Map() };
    const fill = (select, rows, idKey, labelKey, placeholder) => {
      select.replaceChildren(new Option(placeholder, ''));
      rows.forEach(item => { selectedNames[idKey === 'buildingId' ? 'buildings' : idKey === 'floorId' ? 'floors' : idKey === 'classroomId' ? 'rooms' : 'categories'].set(item[idKey], item[labelKey]); select.add(new Option(item[labelKey], item[idKey])); });
      select.disabled = rows.length === 0;
    };
    const setLocationPreview = () => {
      const preview = document.querySelector('[data-location-preview]');
      if (building.value && floor.value && room.value) {
        const labels = apiMode ? [selectedNames.buildings.get(building.value), selectedNames.floors.get(floor.value), selectedNames.rooms.get(room.value)] : [building.value, floor.value, room.value];
        document.querySelector('[data-location-label]').textContent = labels.filter(Boolean).join(' · '); preview.classList.remove('hidden');
      } else preview.classList.add('hidden');
    };
    const loadReferenceData = async () => {
      if (!apiMode) {
        const locations = window.CAMPUSCARE_CONFIG.sampleLocations;
        fill(building, Object.keys(locations).map(name => ({ id: name, name })), 'id', 'name', 'Select a building');
        return;
      }
      try {
        const [categoryResult, buildingResult] = await Promise.all([
          CampusCareAPI.request('complaints.categories'), CampusCareAPI.request('locations.buildings')
        ]);
        const categories = unwrap(categoryResult), buildings = unwrap(buildingResult);
        fill(category, categories, 'categoryId', 'categoryName', 'Choose a category');
        fill(building, buildings, 'buildingId', 'buildingName', 'Select a building');
      } catch (error) { CampusCare.toast(error.message, 'error'); }
    };
    loadReferenceData();
    building.addEventListener('change', async () => {
      fill(floor, [], 'floorId', 'floorName', 'Loading floors…'); fill(room, [], 'classroomId', 'classroomName', 'Select a floor first'); setLocationPreview();
      if (!building.value) return;
      if (!apiMode) {
        const rows = Object.keys(window.CAMPUSCARE_CONFIG.sampleLocations[building.value] || {}).map(name => ({ id: name, name }));
        fill(floor, rows, 'id', 'name', rows.length ? 'Select a floor' : 'No floors available'); return;
      }
      try {
        const rows = unwrap(await CampusCareAPI.request('locations.floors', { pathParams: { buildingId: building.value } }));
        fill(floor, rows, 'floorId', 'floorName', rows.length ? 'Select a floor' : 'No floors available');
      } catch (error) { fill(floor, [], 'floorId', 'floorName', 'Could not load floors'); CampusCare.toast(error.message, 'error'); }
    });
    floor.addEventListener('change', async () => {
      fill(room, [], 'classroomId', 'classroomName', 'Loading classrooms…'); setLocationPreview();
      if (!floor.value) return;
      if (!apiMode) {
        const rows = window.CAMPUSCARE_CONFIG.sampleLocations[building.value]?.[floor.value] || [];
        fill(room, rows.map(name => ({ id: name, name })), 'id', 'name', 'Select a room or common area'); return;
      }
      try {
        const rows = unwrap(await CampusCareAPI.request('locations.rooms', { pathParams: { floorId: floor.value } }));
        fill(room, rows, 'classroomId', 'classroomName', rows.length ? 'Select a classroom' : 'No classrooms available');
      } catch (error) { fill(room, [], 'classroomId', 'classroomName', 'Could not load classrooms'); CampusCare.toast(error.message, 'error'); }
    });
    room.addEventListener('change', setLocationPreview);
    const description = form.elements.description;
    description.addEventListener('input', () => document.querySelector('[data-char-count]').textContent = description.value.length);

    const fileInput = form.elements.image, zone = document.querySelector('[data-upload-zone]'), previewBox = document.querySelector('[data-image-preview]'), uploadError = document.querySelector('[data-upload-error]');
    let previewImage = '';
    const removeFile = () => { fileInput.value = ''; previewImage = ''; previewBox.classList.add('hidden'); previewBox.querySelector('img').removeAttribute('src'); uploadError.textContent = ''; };
    const loadFile = async file => {
      uploadError.textContent = ''; if (!file) return;
      if (!['image/jpeg', 'image/png'].includes(file.type) || !/\.(jpe?g|png)$/i.test(file.name)) { removeFile(); uploadError.textContent = 'Choose a JPG, JPEG or PNG image.'; return; }
      if (file.size > 5 * 1024 * 1024) { removeFile(); uploadError.textContent = 'This image is larger than 5 MB. Choose a smaller file.'; return; }
      try {
        const source = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error('Could not read this image.')); reader.readAsDataURL(file); });
        const img = await new Promise((resolve, reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = () => reject(new Error('This image could not be opened.')); image.src = source; });
        const scale = Math.min(1, 1400 / Math.max(img.naturalWidth, img.naturalHeight));
        const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(img.naturalWidth * scale)); canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height); previewImage = canvas.toDataURL(file.type === 'image/png' ? 'image/png' : 'image/jpeg', .82);
        previewBox.querySelector('img').src = previewImage; document.querySelector('[data-file-name]').textContent = file.name;
        document.querySelector('[data-file-size]').textContent = `${(file.size / 1024 / 1024).toFixed(2)} MB`; previewBox.classList.remove('hidden');
      } catch (_) { removeFile(); uploadError.textContent = 'We could not preview that image. Try another file.'; }
    };
    document.querySelector('[data-browse]').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => loadFile(fileInput.files[0]));
    document.querySelector('[data-remove-image]').addEventListener('click', removeFile);
    ['dragenter', 'dragover'].forEach(name => zone.addEventListener(name, event => { event.preventDefault(); zone.classList.add('dragover'); }));
    ['dragleave', 'drop'].forEach(name => zone.addEventListener(name, event => { event.preventDefault(); zone.classList.remove('dragover'); }));
    zone.addEventListener('drop', event => { if (event.dataTransfer.files[0]) { fileInput.files = event.dataTransfer.files; loadFile(fileInput.files[0]); } });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      if (!building.value || !floor.value || !room.value) { CampusCare.toast('Choose a building, floor and classroom.'); (building.value ? (floor.value ? room : floor) : building).focus(); return; }
      const submit = form.querySelector('[type="submit"]'); submit.disabled = true;
      try {
        if (apiMode) {
          const result = unwrap(await CampusCareAPI.request('complaints.submitAnonymous', { method: 'POST', body: {
            title: String(form.elements.title.value).trim(), description: String(description.value).trim(), categoryId: category.value, classroomId: room.value
          }, loadingTarget: submit }));
          let imageUploadFailed = false;
          if (fileInput.files[0]) {
            const image = new FormData(); image.append('file', fileInput.files[0]);
            try { await CampusCareAPI.request('complaints.uploadImage', { method: 'POST', pathParams: { complaintId: result.trackingReference }, body: image }); }
            catch (_) { imageUploadFailed = true; }
          }
          document.querySelector('.form-page-heading').classList.add('hidden'); form.classList.add('hidden');
          const success = document.querySelector('[data-success]'); success.classList.remove('hidden'); success.querySelector('[data-reference]').textContent = result.trackingReference;
          success.querySelector('.mock-warning').textContent = imageUploadFailed
            ? 'Your complaint was submitted, but its photo could not be uploaded. Keep this reference; do not submit the complaint again.'
            : 'Your complaint was submitted to the college system. Keep this reference to track it.';
          window.scrollTo({ top: 0, behavior: 'smooth' });
          return;
        }
        const data = new FormData(form); const id = `CC-${new Date().getFullYear()}-${Math.floor(1000 + Math.random() * 9000)}`;
        const complaint = { id, title: String(data.get('title')).trim(), category: category.value, description: String(data.get('description')).trim(), department: 'Unspecified', building: building.value, floor: floor.value, room: room.value, date: new Date().toISOString().slice(0, 10), status: 'Pending', remarks: '', image: previewImage, imageName: fileInput.files[0]?.name || '', ownerId: CampusCare.session()?.ownerId || 'demo-student' };
        CampusCare.add(complaint); document.querySelector('.form-page-heading').classList.add('hidden'); form.classList.add('hidden');
        const success = document.querySelector('[data-success]'); success.classList.remove('hidden'); success.querySelector('[data-reference]').textContent = id;
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } catch (error) { CampusCare.toast(error.message || 'The complaint could not be submitted.', 'error'); }
      finally { submit.disabled = false; }
    });
    document.querySelector('[data-copy-reference]').addEventListener('click', async () => {
      const reference = document.querySelector('[data-reference]').textContent;
      try { await navigator.clipboard.writeText(reference); CampusCare.toast('Complaint reference copied.', 'success'); }
      catch (_) { CampusCare.toast(`Complaint reference: ${reference}`); }
    });
  }

  const track = document.getElementById('track-form');
  if (track) {
    if (apiMode && !CampusCare.session()?.token) location.href = 'login.html';
    const render = complaint => {
      const result = document.querySelector('[data-status-result]'), empty = document.querySelector('[data-status-empty]');
      const status = CampusCare.displayStatus(complaint.status);
      const building = complaint.building || '', floor = complaint.floor || '', room = complaint.classroom || '';
      result.innerHTML = `<div class="status-result-head"><div><span class="status-id">${CampusCare.esc(complaint.trackingReference || complaint.id)}</span><h2>${CampusCare.esc(complaint.title)}</h2><small class="muted">Submitted ${CampusCare.formatDate(complaint.submittedAt || complaint.date)}</small></div>${CampusCare.statusBadge(status)}</div><p class="status-description">${CampusCare.esc(complaint.description)}</p><div class="status-meta"><div><small>Category</small><b>${CampusCare.esc(complaint.category)}</b></div><div><small>Building</small><b>${CampusCare.esc(building)}</b></div><div><small>Floor</small><b>${CampusCare.esc(floor)}</b></div><div><small>Classroom / room</small><b>${CampusCare.esc(room)}</b></div></div>${complaint.remarks ? `<div class="remarks-box"><b>Staff remarks</b><p>${CampusCare.esc(complaint.remarks)}</p></div>` : '<div class="remarks-box"><b>Staff remarks</b><p>No remarks have been added yet.</p></div>'}`;
      if (Array.isArray(complaint.history) && complaint.history.length) {
        const historyBox = document.createElement('section'); historyBox.className = 'complaint-history';
        const heading = document.createElement('h3'); heading.textContent = 'Status history'; historyBox.append(heading);
        complaint.history.forEach(event => {
          const entry = document.createElement('div'); entry.className = 'complaint-history-entry';
          const title = document.createElement('b'); title.textContent = CampusCare.displayStatus(event.newStatus);
          const date = document.createElement('small'); date.textContent = CampusCare.formatDate(event.changedAt);
          entry.append(title, date);
          if (event.remarks) { const note = document.createElement('p'); note.textContent = event.remarks; entry.append(note); }
          historyBox.append(entry);
        });
        result.append(historyBox);
      }
      result.classList.remove('hidden'); empty.classList.add('hidden');
      if (apiMode) CampusCare.loadComplaintImages(result, complaint.complaintId || complaint.trackingReference);
    };
    track.addEventListener('submit', async event => {
      event.preventDefault(); const reference = String(new FormData(track).get('reference') || '').trim();
      if (!reference) { document.querySelector('[data-track-error]').textContent = 'Enter a complaint reference.'; return; }
      document.querySelector('[data-track-error]').textContent = '';
      if (apiMode) {
        try { render(unwrap(await CampusCareAPI.request('complaints.getMine', { pathParams: { trackingReference: reference } }))); }
        catch (error) { document.querySelector('[data-status-result]').classList.add('hidden'); document.querySelector('[data-status-empty]').classList.remove('hidden'); document.querySelector('[data-track-error]').textContent = error.response?.message || 'That complaint was not found in your account.'; }
        return;
      }
      const complaint = CampusCare.byId(reference);
      if (!complaint || complaint.ownerId !== (CampusCare.session()?.ownerId || 'demo-student')) { document.querySelector('[data-status-result]').classList.add('hidden'); document.querySelector('[data-status-empty]').classList.remove('hidden'); document.querySelector('[data-track-error]').textContent = 'We could not find that report in this preview account.'; return; }
      render(complaint);
    });
    const requestedId = new URLSearchParams(location.search).get('id');
    if (requestedId) { track.elements.reference.value = requestedId; track.requestSubmit(); }
  }
});
