const STATE_KEY = "ntc_workbench_state";

function emptyState() {
  return {
    activeWorkId: "",
    drafts: {}
  };
}

export function loadWorkbenchState() {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (!raw) {
      return emptyState();
    }
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") {
      return emptyState();
    }
    return {
      activeWorkId: typeof parsed.activeWorkId === "string" ? parsed.activeWorkId : "",
      drafts: parsed.drafts && typeof parsed.drafts === "object" ? parsed.drafts : {}
    };
  } catch {
    return emptyState();
  }
}

export function saveWorkbenchState(state) {
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify(state));
  } catch {
    // 存储失败时静默忽略
  }
}

export function saveWorkbenchDraft(workId, draft) {
  if (!workId) {
    return;
  }
  const state = loadWorkbenchState();
  state.activeWorkId = workId;
  state.drafts[workId] = draft;
  saveWorkbenchState(state);
}

export function loadWorkbenchDraft(workId) {
  if (!workId) {
    return null;
  }
  const state = loadWorkbenchState();
  const draft = state.drafts[workId];
  if (!draft || typeof draft !== "object") {
    return null;
  }
  return draft;
}

export function removeWorkbenchDraft(workId) {
  if (!workId) {
    return;
  }
  const state = loadWorkbenchState();
  delete state.drafts[workId];
  if (state.activeWorkId === workId) {
    state.activeWorkId = "";
  }
  saveWorkbenchState(state);
}

export function clearWorkbenchDraft() {
  localStorage.removeItem(STATE_KEY);
}
