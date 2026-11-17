document.addEventListener('DOMContentLoaded', () => {
  const apiMode = window.CAMPUSCARE_CONFIG.mode === 'api';
  const showError = (notice, error) => {
    const response = error?.response;
    notice.className = 'form-notice error-text';
    notice.textContent = response?.message || error?.message || 'The request could not be completed. Please try again.';
  };
  document.querySelectorAll('[data-toggle-password]').forEach(button => button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.togglePassword); if (!input) return;
    const visible = input.type === 'password'; input.type = visible ? 'text' : 'password';
    button.textContent = visible ? 'Hide' : 'Show'; button.setAttribute('aria-label', visible ? 'Hide password' : 'Show password');
  }));
  document.querySelectorAll('.role-option').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('.role-option').forEach(item => {
      const selected = item === button;
      item.classList.toggle('is-selected', selected);
      item.setAttribute('aria-pressed', String(selected));
    });
    const roleInput = document.querySelector('input[name="role"]'); if (roleInput) roleInput.value = button.dataset.role;
  }));
  const initialRole = new URLSearchParams(location.search).get('role')?.toLowerCase();
  if (['student', 'faculty', 'admin'].includes(initialRole)) {
    document.querySelector(`.role-option[data-role="${initialRole}"]`)?.click();
  }

  const departmentSelect = document.getElementById('departmentId');
  if (departmentSelect && !apiMode) {
    const previewDepartments = ['Facilities', 'Information Technology', 'Student Affairs'];
    departmentSelect.replaceChildren(new Option('Choose your department', ''));
    previewDepartments.forEach(name => departmentSelect.add(new Option(name, name)));
    const hint = document.querySelector('#register-form .demo-hint');
    if (hint) hint.innerHTML = '<span>i</span><span><b>Preview only</b><br>No account will be created in preview mode.</span>';
  } else if (departmentSelect) {
    const retryButton = document.querySelector('[data-retry-departments]');
    const departmentStatus = document.querySelector('[data-department-status]');
    const loadDepartments = async () => {
      departmentSelect.disabled = true;
      departmentSelect.setAttribute('aria-busy', 'true');
      departmentSelect.replaceChildren(new Option('Loading departments…', ''));
      if (retryButton) retryButton.hidden = true;
      if (departmentStatus) { departmentStatus.hidden = true; departmentStatus.textContent = ''; }
      try {
        const response = await CampusCareAPI.request('departments');
        const departments = response?.data;
        if (!response?.success || !Array.isArray(departments)) throw new Error('The department list returned an invalid response.');
        const validDepartments = departments.filter(item => item?.departmentId && item?.departmentName);
        departmentSelect.replaceChildren(new Option(validDepartments.length ? 'Choose your department' : 'No departments are available', ''));
        validDepartments.forEach(department => departmentSelect.add(new Option(department.departmentName, department.departmentId)));
        departmentSelect.disabled = validDepartments.length === 0;
        if (!validDepartments.length) {
          const notice = document.querySelector('#register-form [data-form-notice]');
          if (notice) { notice.className = 'form-notice error-text'; notice.textContent = 'No departments are configured yet. Please contact the college administrator.'; }
          if (departmentStatus) { departmentStatus.textContent = 'No departments were returned by the backend. Ask the college administrator to add departments.'; departmentStatus.hidden = false; }
        }
      } catch (error) {
        departmentSelect.replaceChildren(new Option('Departments unavailable — retry below', ''));
        if (retryButton) retryButton.hidden = false;
        const notice = document.querySelector('#register-form [data-form-notice]');
        if (notice) showError(notice, error);
        if (departmentStatus) {
          departmentStatus.textContent = error?.kind === 'network_error'
            ? 'The API cannot be reached. Start the backend and check its URL, then retry.'
            : 'The backend could not load departments. Check the API and database connection, then retry.';
          departmentStatus.hidden = false;
        }
      } finally {
        departmentSelect.removeAttribute('aria-busy');
      }
    };
    retryButton?.addEventListener('click', loadDepartments);
    loadDepartments();
  }

  const login = document.getElementById('login-form');
  if (login && new URLSearchParams(location.search).get('reason') === 'session-expired') {
    const notice = login.querySelector('[data-form-notice]');
    notice.className = 'form-notice error-text';
    notice.textContent = 'Your session expired. Sign in again to continue.';
  }
  if (login) login.addEventListener('submit', async event => {
    event.preventDefault();
    const notice = login.querySelector('[data-form-notice]'); const data = new FormData(login);
    if (!login.reportValidity()) return;
    const role = String(data.get('role') || 'student').toLowerCase();
    CampusCare.clearSession();
    if (!apiMode) {
      const email = String(data.get('email') || '');
      const name = email.split('@')[0].replace(/[._-]+/g, ' ').replace(/\b\w/g, c => c.toUpperCase()) || `${role[0].toUpperCase()}${role.slice(1)}`;
      CampusCare.setSession({ role, name, ownerId: 'demo-student' });
      notice.textContent = 'Demo sign-in successful. Opening your workspace…';
      setTimeout(() => location.href = ({ student: 'student-dashboard.html', faculty: 'faculty-dashboard.html', admin: 'admin-dashboard.html' })[role], 450);
      return;
    }
    notice.textContent = 'Connecting to the college system…';
    const slowNotice = setTimeout(() => {
      if (login.isConnected) notice.textContent = 'The backend is taking longer to wake up. Please keep this page open…';
    }, 8000);
    try {
      const response = await CampusCareAPI.request(`auth.${role}.login`, {
        method: 'POST', timeoutMs: 60000,
        body: { email: String(data.get('email')).trim(), password: String(data.get('password')) }, loadingTarget: login.querySelector('[type="submit"]')
      });
      const result = response?.data;
      if (!result?.token || !result?.user?.role) throw new Error('The server returned an incomplete login response.');
      if (result.user.role.toLowerCase() !== role) throw new Error(`This account is registered as ${result.user.role.toLowerCase()}. Choose that role and sign in again.`);
      CampusCare.setSession({ token: result.token, role, name: result.user.fullName });
      notice.className = 'form-notice success-text'; notice.textContent = 'Sign-in successful. Opening your workspace…';
      setTimeout(() => location.href = ({ student: 'student-dashboard.html', faculty: 'faculty-dashboard.html', admin: 'admin-dashboard.html' })[role], 250);
    } catch (error) { showError(notice, error); }
    finally { clearTimeout(slowNotice); }
  });

  const register = document.getElementById('register-form');
  if (register) register.addEventListener('submit', async event => {
    event.preventDefault(); const notice = register.querySelector('[data-form-notice]'); const data = new FormData(register);
    if (!register.reportValidity()) return;
    if (data.get('password') !== data.get('confirmPassword')) { notice.textContent = 'The passwords do not match.'; document.getElementById('confirmPassword').focus(); return; }
    if (!apiMode) { notice.textContent = 'Registration is in preview mode. No account was created.'; CampusCare.toast('No account was created in preview mode.'); return; }
    const departmentId = String(data.get('departmentId') || '');
    if (!departmentId) { notice.textContent = 'Choose your department.'; return; }
    notice.textContent = 'Creating your account…';
    try {
      const response = await CampusCareAPI.request('auth.student.register', {
        method: 'POST', timeoutMs: 60000,
        body: { fullName: String(data.get('fullName')).trim(), email: String(data.get('email')).trim(), password: String(data.get('password')), departmentId }, loadingTarget: register.querySelector('[type="submit"]')
      });
      notice.className = 'form-notice success-text';
      if (response?.data?.requiresEmailVerification) {
        notice.textContent = 'Account created. Check your email and verify your address before signing in.';
      } else {
        notice.textContent = 'Account created. You can sign in with your new account.';
        setTimeout(() => location.href = 'login.html', 900);
      }
    } catch (error) { showError(notice, error); }
  });
});
