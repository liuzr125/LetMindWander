// A temporary server/network failure must not discard an otherwise valid local session.
export async function restoreAdminProfile({ request, getToken, clearToken }) {
  if (!getToken()) return { state: 'missing' };
  try {
    const profile = await request('/admin/auth/me', { silentError: true });
    return { state: 'ready', profile };
  } catch (error) {
    if (error.status === 401) {
      clearToken();
      return { state: 'expired' };
    }
    return { state: 'unavailable', error };
  }
}
