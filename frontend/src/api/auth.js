const SESSION_ID_KEY = "ntc_session_id";
const SESSION_ID_HEADER = "X-Session-Id";

function getSessionId() {
  return localStorage.getItem(SESSION_ID_KEY);
}

function saveSessionId(sessionId) {
  if (sessionId) {
    localStorage.setItem(SESSION_ID_KEY, sessionId);
  }
}

export function clearSessionId() {
  localStorage.removeItem(SESSION_ID_KEY);
}

export function hasSessionId() {
  return !!getSessionId();
}

export async function isAuthenticated() {
  try {
    const { response } = await currentUser();
    return response.ok;
  } catch {
    return false;
  }
}

async function request(url, method, body) {
  const headers = body ? { "Content-Type": "application/json" } : {};
  const sessionId = getSessionId();
  if (sessionId) {
    headers[SESSION_ID_HEADER] = sessionId;
  }
  const response = await fetch(url, {
    method,
    credentials: "include",
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();
  if (response.status === 401 && !url.includes("/api/auth/login")) {
    clearSessionId();
  }
  return { response, payload };
}

export function register(payload) {
  return request("/api/auth/register", "POST", payload);
}

export function login(payload) {
  return request("/api/auth/login", "POST", payload).then((result) => {
    if (result.response.ok && typeof result.payload === "object" && result.payload?.sessionId) {
      saveSessionId(result.payload.sessionId);
    }
    return result;
  });
}

export function currentUser() {
  return request("/api/auth/me", "GET");
}

export function logout() {
  return request("/api/auth/logout", "POST").then((result) => {
    if (result.response.status === 204) {
      clearSessionId();
    }
    return result;
  });
}
