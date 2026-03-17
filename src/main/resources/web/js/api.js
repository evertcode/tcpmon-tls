async function fetchJson(url, options, allowAuthRetry = true) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  if (response.status === 401 && allowAuthRetry) {
    await ensureAuthSession();
    return fetchJson(url, options, false);
  }
  if (!response.ok) {
    const error = new Error(data.error || 'Request failed');
    error.payload = data;
    throw error;
  }
  return data;
}

async function ensureAuthSession() {
  const authPromptInFlight = getState('authPromptInFlight');
  if (authPromptInFlight) {
    return authPromptInFlight;
  }
  const promptRequest = (async () => {
    const token = window.prompt('Enter the control plane API token');
    if (!token) {
      throw new Error('Authentication required');
    }
    const response = await fetch('/api/auth/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(data.error || 'Authentication failed');
    }
  })();
  setState('authPromptInFlight', promptRequest);
  try {
    await promptRequest;
  } finally {
    setState('authPromptInFlight', null);
  }
}
