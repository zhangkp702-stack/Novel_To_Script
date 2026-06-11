const SESSION_ID_KEY = "ntc_session_id";
const SESSION_ID_HEADER = "X-Session-Id";

function buildHeaders(extra = {}) {
  const headers = { ...extra };
  const sessionId = localStorage.getItem(SESSION_ID_KEY);
  if (sessionId) {
    headers[SESSION_ID_HEADER] = sessionId;
  }
  return headers;
}

async function request(url, method, body) {
  const headers = buildHeaders(body ? { "Content-Type": "application/json" } : {});
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
  return { response, payload };
}

function extractSseDataValue(line) {
  let value = line.slice(5);
  // SSE 规范：冒号后最多去掉一个空格，必须保留 payload 内的缩进（YAML 依赖行首空格）
  if (value.startsWith(" ")) {
    value = value.slice(1);
  }
  return value;
}

function parseSseChunk(buffer) {
  const events = [];
  const blocks = buffer.split("\n\n");
  const remainder = blocks.pop() ?? "";

  for (const block of blocks) {
    if (!block.trim()) {
      continue;
    }
    let eventName = "message";
    const dataLines = [];
    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(extractSseDataValue(line));
      }
    }
    events.push({ event: eventName, data: dataLines.join("\n") });
  }

  return { events, remainder };
}

export function generateScript(payload) {
  return request("/api/scripts/generate", "POST", payload);
}

export function saveScript(payload) {
  return request("/api/scripts", "POST", payload);
}

export function getScript(id) {
  return request(`/api/scripts/${id}`, "GET");
}

export function listWorks() {
  return request("/api/scripts/works", "GET");
}

export function createWork(title = "") {
  return request("/api/scripts/works", "POST", { title });
}

export function generateWorkTitle(workId, novelExcerpt) {
  return request(`/api/scripts/works/${encodeURIComponent(workId)}/title/generate`, "POST", {
    novelExcerpt
  });
}

export function updateWorkTitle(workId, title = "") {
  return request(`/api/scripts/works/${encodeURIComponent(workId)}/title`, "PUT", { title });
}

export function deleteWork(workId) {
  const query = new URLSearchParams({ workId });
  return request(`/api/scripts/works?${query.toString()}`, "DELETE");
}

export function listScriptsByWorkId(workId = "") {
  const query = new URLSearchParams();
  if (workId) {
    query.set("workId", workId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/api/scripts${suffix}`, "GET");
}

export function getRecordTrace(recordId) {
  return request(`/api/scripts/${recordId}/trace`, "GET");
}

export function listTraces(workId = "") {
  const query = new URLSearchParams();
  if (workId) {
    query.set("workId", workId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return request(`/api/traces${suffix}`, "GET");
}

export function listCharacters(workId) {
  return request(`/api/works/${encodeURIComponent(workId)}/characters`, "GET");
}

export function createCharacter(workId, payload) {
  return request(`/api/works/${encodeURIComponent(workId)}/characters`, "POST", payload);
}

export function updateCharacter(workId, characterId, payload) {
  return request(
    `/api/works/${encodeURIComponent(workId)}/characters/${encodeURIComponent(characterId)}`,
    "PUT",
    payload
  );
}

export function deleteCharacter(workId, characterId) {
  return request(
    `/api/works/${encodeURIComponent(workId)}/characters/${encodeURIComponent(characterId)}`,
    "DELETE"
  );
}

export function listRefineMessages(workId, chapterNumber) {
  const query = new URLSearchParams({
    workId,
    chapterNumber: String(chapterNumber)
  });
  return request(`/api/scripts/messages?${query.toString()}`, "GET");
}

async function postSseStream(url, payload, handlers = {}, signal) {
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    headers: buildHeaders({
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    }),
    body: JSON.stringify(payload),
    signal
  });

  if (!response.ok) {
    const contentType = response.headers.get("content-type") || "";
    const errorPayload = contentType.includes("application/json")
      ? await response.json()
      : await response.text();
    return { response, payload: errorPayload };
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error("浏览器不支持流式响应");
  }

  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  function dispatchEvents(events) {
    for (const item of events) {
      if (item.event === "open" && handlers.onOpen) {
        handlers.onOpen(item.data);
      } else if (item.event === "token" && handlers.onToken) {
        handlers.onToken(item.data);
      } else if (item.event === "warn" && handlers.onWarn) {
        handlers.onWarn(item.data);
      } else if (item.event === "artifact" && handlers.onArtifact) {
        handlers.onArtifact(item.data);
      } else if (item.event === "meta" && handlers.onMeta) {
        handlers.onMeta(item.data);
      } else if (item.event === "done" && handlers.onDone) {
        handlers.onDone(item.data);
      } else if (item.event === "error" && handlers.onError) {
        handlers.onError(item.data);
      }
    }
  }

  function consumeBuffer(chunk = "") {
    buffer += chunk;
    const parsed = parseSseChunk(buffer);
    buffer = parsed.remainder;
    dispatchEvents(parsed.events);
  }

  while (true) {
    const { done, value } = await reader.read();
    if (value) {
      consumeBuffer(decoder.decode(value, { stream: true }));
    }
    if (done) {
      break;
    }
  }

  consumeBuffer(decoder.decode());
  if (buffer.trim()) {
    consumeBuffer("\n\n");
  }

  return { response };
}

export async function generateScriptStream(payload, handlers = {}, signal) {
  return postSseStream("/api/scripts/generate/stream", payload, handlers, signal);
}

export async function refineScriptStream(payload, handlers = {}, signal) {
  return postSseStream("/api/scripts/refine/stream", payload, handlers, signal);
}
