document.addEventListener('DOMContentLoaded', () => {
  const token = new URLSearchParams(location.search).get('token') || '';
  const verifyNotice = document.querySelector('[data-auth-verify]');
  if (verifyNotice) {
    if (!token) verifyNotice.textContent = 'The verification link is missing its token.';
    else CampusCareAPI.request('auth.verifyEmail', { method: 'POST', body: { token }, timeoutMs: 30000 })
      .then(response => { verifyNotice.className = 'form-notice success-text'; verifyNotice.textContent = response?.message || 'Email verified. You can now sign in.'; })
      .catch(error => { verifyNotice.className = 'form-notice error-text'; verifyNotice.textContent = error.response?.message || error.message || 'This link could not be verified.'; });
  }
  const form = document.querySelector('[data-auth-action]');
  if (!form) return;
  const action = form.dataset.authAction;
  const notice = form.querySelector('[data-form-notice]');
  const show = (message, type = '') => { notice.className = `form-notice ${type}`; notice.textContent = message; };
  const submit = async body => {
    if (window.CAMPUSCARE_CONFIG.mode !== 'api') throw new Error('Connect to the live CampusCare API to use this account action.');
    const response = await CampusCareAPI.request(`auth.${action}`, { method: 'POST', body, timeoutMs: 30000 });
    if (!response?.success) throw new Error(response?.message || 'The request could not be completed.');
    return response;
  };
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const values = new FormData(form);
    const button = form.querySelector('[type="submit"]'); button.disabled = true;
    try {
      if (action === 'forgotPassword') {
        await submit({ email: String(values.get('email')).trim() });
        show('If an account matches that email, a password reset link has been sent.', 'success-text');
      } else if (action === 'resetPassword') {
        if (!token) throw new Error('The reset link is missing its token. Request a new link.');
        if (values.get('password') !== values.get('confirmPassword')) throw new Error('The passwords do not match.');
        await submit({ token, password: String(values.get('password')) });
        show('Password changed. You can now sign in.', 'success-text');
        setTimeout(() => location.href = 'login.html', 1200);
      } else if (action === 'resendVerification') {
        await submit({ email: String(values.get('email')).trim() });
        show('If the account needs verification, a new link has been sent.', 'success-text');
      }
    } catch (error) {
      show(error.response?.message || error.message || 'The request could not be completed.', 'error-text');
    } finally { button.disabled = false; }
  });
});
