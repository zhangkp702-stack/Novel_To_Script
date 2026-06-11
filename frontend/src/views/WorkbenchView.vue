<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { currentUser, logout } from "../api/auth";
import {
  createCharacter,
  createWork,
  deleteCharacter,
  deleteWork,
  generateScriptStream,
  refineScriptStream,
  listRefineMessages,
  generateWorkTitle,
  updateWorkTitle,
  listCharacters,
  listScriptsByWorkId,
  listWorks,
  saveScript,
  updateCharacter
} from "../api/script";
import ChapterFieldList from "../components/ChapterFieldList.vue";
import TaskSidebar from "../components/TaskSidebar.vue";
import { validateChapterContent } from "../utils/chapterValidation";
import {
  loadWorkbenchDraft,
  loadWorkbenchState,
  removeWorkbenchDraft,
  saveWorkbenchDraft
} from "../utils/workbenchDraft";

const SIDEBAR_COLLAPSED_KEY = "ntc_task_sidebar_collapsed";
const TITLE_MIN_EXCERPT = 20;

const router = useRouter();
const title = ref("");
const workId = ref("");
const works = ref([]);
const characters = ref([]);
const characterForm = ref(createEmptyCharacterForm());
const editingCharacterId = ref("");
const characterLoading = ref(false);
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1");
const titleNamingWorkId = ref("");
const titleNamedWorkIds = new Set();
const titleManualWorkIds = new Set();
const taskTitleInput = ref("");
const titleSaving = ref(false);
let titleGenerateTimer = null;
let titleSaveTimer = null;
const chapterItems = ref([createChapterItem()]);
const resultsById = ref({});
const notice = ref("");
const noticeType = ref("info");
const currentUsername = ref("");
const loading = ref(false);
const streamingIds = ref(new Set());
const refiningIds = ref(new Set());
const resultPanelRefs = ref({});
const streamControllers = new Map();
let draftSaveTimer = null;

function createChapterItem(content = "") {
  return { id: crypto.randomUUID(), content };
}

function createEmptyCharacterForm() {
  return {
    name: "",
    displayName: "",
    description: "",
    personality: ""
  };
}

function createScriptVersion({
  kind = "generate",
  label = "初稿",
  instruction = "",
  content = "",
  status = "idle",
  collapsed = false
} = {}) {
  return {
    id: crypto.randomUUID(),
    kind,
    label,
    instruction,
    content,
    warning: "",
    error: "",
    status,
    collapsed
  };
}

function getActiveVersion(result) {
  if (!result?.versions?.length) {
    return null;
  }
  return result.versions.find((version) => version.id === result.activeVersionId)
    || result.versions[result.versions.length - 1];
}

function patchVersionById(chapterId, versionId, patch) {
  const result = ensureResult(chapterId);
  const version = result.versions?.find((item) => item.id === versionId);
  if (!version) {
    return null;
  }
  patch(version, result);
  syncResultFromActiveVersion(result);
  return version;
}

function syncResultFromActiveVersion(result) {
  const active = getActiveVersion(result);
  if (active) {
    result.content = active.content;
    result.warning = active.warning;
    result.error = active.error || "";
  }
}

function addScriptVersion(result, version, { collapseOthers = true } = {}) {
  if (!result.versions) {
    result.versions = [];
  }
  if (collapseOthers) {
    result.versions.forEach((item) => {
      item.collapsed = true;
    });
    version.collapsed = false;
  }
  result.versions.push(version);
  result.activeVersionId = version.id;
  syncResultFromActiveVersion(result);
}

function hasScriptContent(result) {
  return Boolean(getActiveVersion(result)?.content?.trim() || result.content?.trim());
}

function nextRefineLabel(result) {
  const count = (result.versions || []).filter((version) => version.kind === "refine").length;
  return `改编 ${count + 1}`;
}

function hasRefineVersions(result) {
  return (result?.versions || []).some((version) => version.kind === "refine");
}

function toggleVersionCollapsed(chapterId, versionId) {
  const result = ensureResult(chapterId);
  const version = result.versions.find((item) => item.id === versionId);
  if (!version) {
    return;
  }
  const willExpand = version.collapsed;
  if (willExpand) {
    result.versions.forEach((item) => {
      item.collapsed = item.id !== versionId;
    });
  } else {
    version.collapsed = true;
  }
}

function createEmptyResult() {
  return {
    content: "",
    model: "",
    status: "idle",
    error: "",
    warning: "",
    saved: false,
    recordId: null,
    traceId: "",
    generationId: "",
    versions: [],
    activeVersionId: null,
    refineMessages: [],
    refineInput: ""
  };
}

function ensureResult(id) {
  if (!resultsById.value[id]) {
    resultsById.value[id] = createEmptyResult();
  }
  return resultsById.value[id];
}

function syncResultsWithChapters() {
  const next = { ...resultsById.value };
  const activeIds = new Set(chapterItems.value.map((item) => item.id));
  for (const id of Object.keys(next)) {
    if (!activeIds.has(id)) {
      delete next[id];
    }
  }
  for (const item of chapterItems.value) {
    if (!next[item.id]) {
      next[item.id] = createEmptyResult();
    }
  }
  resultsById.value = next;
}

watch(chapterItems, syncResultsWithChapters, { deep: true, immediate: true });

function showNotice(type, message) {
  noticeType.value = type;
  notice.value = message;
}

function setStreaming(id, streaming) {
  const next = new Set(streamingIds.value);
  if (streaming) {
    next.add(id);
  } else {
    next.delete(id);
  }
  streamingIds.value = next;
  const result = ensureResult(id);
  result.status = streaming ? "streaming" : result.status === "streaming" ? "idle" : result.status;
}

function setRefining(id, refining) {
  const next = new Set(refiningIds.value);
  if (refining) {
    next.add(id);
  } else {
    next.delete(id);
  }
  refiningIds.value = next;
}

function extractRefineInstruction(userContent) {
  const marker = "修改要求：";
  const markerIndex = userContent.indexOf(marker);
  if (markerIndex >= 0) {
    return userContent.slice(markerIndex + marker.length).trim().split("\n")[0];
  }
  return userContent.trim().slice(0, 120);
}

function extractYamlFromFirstUserMessage(userContent) {
  const marker = "当前剧本 YAML 如下：";
  const markerIndex = userContent.indexOf(marker);
  if (markerIndex < 0) {
    return "";
  }
  const tail = userContent.slice(markerIndex + marker.length);
  const instructionIndex = tail.indexOf("\n\n修改要求：");
  return sanitizeYamlDisplayText(
    instructionIndex >= 0 ? tail.slice(0, instructionIndex) : tail
  );
}

function rebuildVersionsFromHistory(result, messages, fallbackContent = "") {
  const versions = [];
  let pendingInstruction = "";

  for (const message of messages) {
    if (message.role === "user") {
      if (message.content.includes("当前剧本 YAML 如下：")) {
        const initialYaml = extractYamlFromFirstUserMessage(message.content);
        if (initialYaml && !versions.length) {
          versions.push(createScriptVersion({
            kind: "generate",
            label: "初稿",
            content: initialYaml,
            status: "done",
            collapsed: true
          }));
        }
        pendingInstruction = extractRefineInstruction(message.content);
      } else {
        pendingInstruction = message.content.trim();
      }
    } else if (message.role === "assistant" && message.content?.trim()) {
      versions.push(createScriptVersion({
        kind: "refine",
        label: `改编 ${versions.filter((item) => item.kind === "refine").length + 1}`,
        instruction: pendingInstruction,
        content: sanitizeYamlDisplayText(message.content),
        status: "done",
        collapsed: true
      }));
      pendingInstruction = "";
    }
  }

  const hasGenerateVersion = versions.some((version) => version.kind === "generate");
  if (!hasGenerateVersion && fallbackContent?.trim()) {
    versions.unshift(createScriptVersion({
      kind: "generate",
      label: "初稿",
      content: sanitizeYamlDisplayText(fallbackContent),
      status: "done",
      collapsed: true
    }));
  }

  if (!versions.length) {
    return;
  }

  versions.forEach((version, index) => {
    version.collapsed = index < versions.length - 1;
  });
  result.versions = versions;
  result.activeVersionId = versions[versions.length - 1].id;
  syncResultFromActiveVersion(result);
}

async function loadRefineMessages(chapterId, chapterNumber) {
  if (!workId.value) {
    return;
  }
  try {
    const { response, payload } = await listRefineMessages(workId.value, chapterNumber);
    if (!response.ok || !Array.isArray(payload)) {
      return;
    }
    const result = ensureResult(chapterId);
    result.refineMessages = payload.map((item) => ({
      role: item.role,
      content: item.content
    }));
    rebuildVersionsFromHistory(result, result.refineMessages, result.content);
  } catch {
    // 改编历史加载失败不阻断主流程
  }
}

function scheduleDraftSave() {
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer);
  }
  draftSaveTimer = setTimeout(persistDraft, 400);
}

function persistDraft() {
  if (!workId.value) {
    return;
  }
  saveWorkbenchDraft(workId.value, {
    chapterItems: chapterItems.value,
    resultsById: resultsById.value
  });
}

function normalizeDraftVersion(version) {
  return {
    id: version?.id || crypto.randomUUID(),
    kind: version?.kind || "generate",
    label: version?.label || "初稿",
    instruction: version?.instruction || "",
    content: version?.content || "",
    warning: version?.warning || "",
    error: version?.error || "",
    status: version?.status === "streaming" ? "idle" : (version?.status || "done"),
    collapsed: Boolean(version?.collapsed)
  };
}

function normalizeDraftResult(result) {
  if (!result || typeof result !== "object") {
    return createEmptyResult();
  }
  const versions = Array.isArray(result.versions)
    ? result.versions.map((version) => normalizeDraftVersion(version))
    : [];
  const normalized = {
    content: result.content || "",
    model: result.model || "",
    status: result.status === "streaming" ? "idle" : (result.status || "idle"),
    error: result.error || "",
    warning: result.warning || "",
    saved: result.saved || false,
    recordId: result.recordId || null,
    traceId: result.traceId || "",
    generationId: result.generationId || "",
    versions,
    activeVersionId: result.activeVersionId
      || (versions.length ? versions[versions.length - 1].id : null),
    refineMessages: [],
    refineInput: result.refineInput || ""
  };
  if (versions.length) {
    syncResultFromActiveVersion(normalized);
  }
  return normalized;
}

function applyDraft(draft) {
  if (!draft) {
    chapterItems.value = [createChapterItem()];
    resultsById.value = {};
    return;
  }
  if (Array.isArray(draft.chapterItems) && draft.chapterItems.length > 0) {
    chapterItems.value = draft.chapterItems.map((item) => ({
      id: item.id || crypto.randomUUID(),
      content: item.content || ""
    }));
  } else {
    chapterItems.value = [createChapterItem()];
  }
  if (draft.resultsById && typeof draft.resultsById === "object") {
    resultsById.value = Object.fromEntries(
      Object.entries(draft.resultsById).map(([id, result]) => [
        id,
        normalizeDraftResult(result)
      ])
    );
  } else {
    resultsById.value = {};
  }
}

function restoreDraftForWork(selectedWorkId) {
  applyDraft(loadWorkbenchDraft(selectedWorkId));
}

function firstChapterExcerpt() {
  const content = chapterItems.value[0]?.content ?? "";
  return content.trim();
}

function isUntitledWorkName(value) {
  const normalized = (value || "").trim();
  return !normalized || normalized === "未命名作品";
}

function syncTaskTitleInput() {
  taskTitleInput.value = isUntitledWorkName(title.value) ? "" : title.value;
}

function scheduleTitleGeneration() {
  if (!workId.value || titleManualWorkIds.has(workId.value) || titleNamingWorkId.value) {
    return;
  }
  if (!isUntitledWorkName(title.value)) {
    titleNamedWorkIds.add(workId.value);
    return;
  }
  const excerpt = firstChapterExcerpt();
  if (excerpt.length < TITLE_MIN_EXCERPT) {
    return;
  }
  if (titleGenerateTimer) {
    clearTimeout(titleGenerateTimer);
  }
  titleGenerateTimer = setTimeout(() => {
    void generateTitleForCurrentWork(excerpt);
  }, 900);
}

async function saveTaskTitle(manualTitle, { manual = true } = {}) {
  if (!workId.value || titleSaving.value) {
    return false;
  }
  const normalized = (manualTitle || "").trim();
  titleSaving.value = true;
  try {
    const { response, payload } = await updateWorkTitle(workId.value, normalized);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return false;
    }
    if (!response.ok) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `任务名称保存失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return false;
    }
    title.value = payload?.title ?? normalized;
    syncTaskTitleInput();
    if (manual && normalized) {
      titleManualWorkIds.add(workId.value);
      titleNamedWorkIds.add(workId.value);
    } else if (!normalized) {
      titleManualWorkIds.delete(workId.value);
      titleNamedWorkIds.delete(workId.value);
      scheduleTitleGeneration();
    } else {
      titleNamedWorkIds.add(workId.value);
    }
    await onRefreshWorks();
    return true;
  } catch (error) {
    showNotice("error", `任务名称保存失败：${error.message}`);
    return false;
  } finally {
    titleSaving.value = false;
  }
}

function scheduleTaskTitleSave() {
  if (!workId.value) {
    return;
  }
  if (titleSaveTimer) {
    clearTimeout(titleSaveTimer);
  }
  titleSaveTimer = setTimeout(() => {
    const input = taskTitleInput.value.trim();
    const current = isUntitledWorkName(title.value) ? "" : title.value.trim();
    if (input === current) {
      return;
    }
    void saveTaskTitle(input, { manual: true });
  }, 500);
}

async function onRenameTask({ work, title: nextTitle }) {
  if (!work?.workId) {
    return;
  }
  if (work.workId !== workId.value) {
    await loadWork(work);
  }
  taskTitleInput.value = nextTitle;
  const saved = await saveTaskTitle(nextTitle, { manual: Boolean(nextTitle) });
  if (saved && nextTitle) {
    showNotice("success", `任务已命名为「${nextTitle}」`);
  }
}

async function generateTitleForCurrentWork(excerpt = firstChapterExcerpt()) {
  const currentWorkId = workId.value;
  if (!currentWorkId || titleManualWorkIds.has(currentWorkId) || titleNamedWorkIds.has(currentWorkId)) {
    return;
  }
  if (excerpt.length < TITLE_MIN_EXCERPT) {
    return;
  }
  titleNamingWorkId.value = currentWorkId;
  try {
    const { response, payload } = await generateWorkTitle(currentWorkId, excerpt);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok) {
      return;
    }
    if (payload?.title) {
      title.value = payload.title;
      titleNamedWorkIds.add(currentWorkId);
      syncTaskTitleInput();
      await onRefreshWorks();
    }
  } catch {
    // 标题生成失败不阻断创作
  } finally {
    if (titleNamingWorkId.value === currentWorkId) {
      titleNamingWorkId.value = "";
    }
  }
}

function toggleSidebarCollapsed() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed.value ? "1" : "0");
}

async function persistChapterResult(index, chapter) {
  const result = resultsById.value[chapter.id];
  if (!result) {
    return;
  }
  syncResultFromActiveVersion(result);
  const active = getActiveVersion(result);
  const scriptContent = active?.content || result?.content;
  if (!scriptContent) {
    return;
  }
  const payload = {
    workId: workId.value || null,
    chapterNumber: index + 1,
    chapterContent: chapter.content.trim(),
    scriptContent,
    modelName: result.model || null,
    traceId: result.traceId || null,
    generationId: result.generationId || null
  };
  try {
    const { response, payload: savePayload } = await saveScript(payload);
    if (response.ok && savePayload?.id) {
      result.saved = true;
      result.recordId = savePayload.id;
      await onRefreshWorks();
    }
  } catch {
    // 保存失败不阻断生成结果展示
  }
}

function normalizeWorkTitle(value) {
  return (value || "").trim();
}

function sanitizeYamlDisplayText(text) {
  if (!text) {
    return "";
  }
  let cleaned = text.replace(/\n*\[系统提示：[^\]]+\]\s*/g, "");
  const fenceMatch = cleaned.match(/```(?:yaml|yml)?\s*([\s\S]*?)```/i);
  if (fenceMatch) {
    cleaned = fenceMatch[1];
  }
  const start = cleaned.search(/^\s*文档类型\s*[:：]/m);
  if (start > 0) {
    cleaned = cleaned.slice(start);
  }
  return cleaned.replace(/```\s*$/g, "").trim();
}

function hasUnclosedDoubleQuote(line) {
  let quoteCount = 0;
  for (let i = 0; i < line.length; i += 1) {
    if (line[i] === '"' && line[i - 1] !== "\\") {
      quoteCount += 1;
    }
  }
  return quoteCount % 2 === 1;
}

const YAML_HANG_WRAP_EXTRA_CH = 4;

const YAML_KEY_VALUE_PATTERN = /^([\w\u4e00-\u9fff_]+[:：]\s*)(.+)$/;
const SCRIPT_BODY_HEADER_PATTERN = /^剧本正文\s*[:：]\s*(?:[|>][-+]?)?\s*$/;
const SCRIPT_CONTENT_LINE_PATTERN = /^(旁白|动作|转场|广播声|电话声|.+视频)[:：]/;
const SCRIPT_BODY_SIBLING_FIELD_PATTERN =
  /^(戏剧功能|场景编号|场景标题|场景头|地点|时间|氛围|出场人物|分隔)[:：]/;

function isYamlKeyValueLine(content) {
  return YAML_KEY_VALUE_PATTERN.test(content);
}

function usesHangingWrapIndent(displayMode) {
  return displayMode === "keyValue" || displayMode === "scriptBody";
}

function isScriptBodyContentLine(indentCh, scriptBodyIndent, content) {
  if (scriptBodyIndent < 0) {
    return false;
  }
  if (content === "") {
    return true;
  }
  if (indentCh > scriptBodyIndent) {
    return true;
  }
  if (SCRIPT_CONTENT_LINE_PATTERN.test(content)) {
    return true;
  }
  if (/^[\w\u4e00-\u9fff_]{1,16}[:：]/.test(content)
      && !SCRIPT_BODY_SIBLING_FIELD_PATTERN.test(content)) {
    return true;
  }
  return !SCRIPT_BODY_SIBLING_FIELD_PATTERN.test(content)
    && !/^-\s/.test(content)
    && !/^[\w\u4e00-\u9fff_]+[:：]/.test(content);
}

function shouldExitScriptBodyBlock(indentCh, scriptBodyIndent, content) {
  if (scriptBodyIndent < 0 || !content) {
    return false;
  }
  if (indentCh < scriptBodyIndent) {
    return true;
  }
  if (indentCh === scriptBodyIndent && /^-\s/.test(content)) {
    return true;
  }
  return indentCh <= scriptBodyIndent && SCRIPT_BODY_SIBLING_FIELD_PATTERN.test(content);
}

function splitYamlDisplayLines(text) {
  const sanitized = sanitizeYamlDisplayText(text);
  if (!sanitized) {
    return [];
  }
  let lastIndent = 0;
  let inQuotedContinuation = false;
  let scriptBodyIndent = -1;

  return sanitized.split("\n").map((line) => {
    const leading = line.match(/^[ \t]*/)?.[0] ?? "";
    let indentCh = leading.replace(/\t/g, "  ").length;
    const content = line.slice(leading.length);
    let displayMode = "plain";

    if (shouldExitScriptBodyBlock(indentCh, scriptBodyIndent, content)) {
      scriptBodyIndent = -1;
    }

    if (SCRIPT_BODY_HEADER_PATTERN.test(content)) {
      scriptBodyIndent = indentCh;
      lastIndent = indentCh;
      inQuotedContinuation = false;
      displayMode = "keyValue";
    } else if (isScriptBodyContentLine(indentCh, scriptBodyIndent, content)) {
      if (content === "" && indentCh <= scriptBodyIndent) {
        indentCh = scriptBodyIndent + YAML_HANG_WRAP_EXTRA_CH;
      }
      lastIndent = indentCh;
      inQuotedContinuation = hasUnclosedDoubleQuote(content);
      displayMode = "scriptBody";
    } else if (isYamlKeyValueLine(content)) {
      lastIndent = indentCh;
      inQuotedContinuation = hasUnclosedDoubleQuote(content);
      displayMode = "keyValue";
    } else if (indentCh === 0 && inQuotedContinuation && lastIndent > 0) {
      indentCh = lastIndent + YAML_HANG_WRAP_EXTRA_CH;
      displayMode = "valueContinuation";
      inQuotedContinuation = hasUnclosedDoubleQuote(content);
    } else {
      if (indentCh > 0) {
        lastIndent = indentCh;
      }
      inQuotedContinuation = hasUnclosedDoubleQuote(content);
    }

    return {
      indentCh,
      wrapIndentCh: indentCh + YAML_HANG_WRAP_EXTRA_CH,
      displayMode,
      text: content
    };
  });
}

function displayWorkTitle(workTitle) {
  const normalized = normalizeWorkTitle(workTitle);
  if (!normalized || normalized === "未命名作品") {
    return "新任务";
  }
  return normalized;
}

function applyRecords(records) {
  const sorted = [...records].sort((a, b) => a.chapterNumber - b.chapterNumber);
  chapterItems.value = sorted.map((record) => ({
    id: crypto.randomUUID(),
    content: record.chapterContent || ""
  }));
  resultsById.value = Object.fromEntries(
    chapterItems.value.map((item, idx) => {
      const scriptContent = sorted[idx].scriptContent || "";
      const result = createEmptyResult();
      if (scriptContent.trim()) {
        addScriptVersion(
          result,
          createScriptVersion({
            kind: "generate",
            label: "初稿",
            content: sanitizeYamlDisplayText(scriptContent),
            status: "done",
            collapsed: false
          }),
          { collapseOthers: false }
        );
      }
      result.model = sorted[idx].modelName || "";
      result.status = "done";
      result.saved = true;
      result.recordId = sorted[idx].id;
      result.traceId = sorted[idx].traceId || "";
      result.generationId = sorted[idx].generationId || "";
      return [item.id, result];
    })
  );
  return sorted.length;
}

function resetWorkbenchState() {
  title.value = "";
  chapterItems.value = [createChapterItem()];
  resultsById.value = {};
  characters.value = [];
  editingCharacterId.value = "";
  characterForm.value = createEmptyCharacterForm();
}

function resetCharacterForm() {
  editingCharacterId.value = "";
  characterForm.value = createEmptyCharacterForm();
}

function startEditCharacter(character) {
  editingCharacterId.value = character.id;
  characterForm.value = {
    name: character.name || "",
    displayName: character.displayName || "",
    description: character.description || "",
    personality: character.personality || ""
  };
}

async function onRefreshCharacters() {
  if (!workId.value) {
    characters.value = [];
    return;
  }
  characterLoading.value = true;
  try {
    const { response, payload } = await listCharacters(workId.value);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (response.status === 404) {
      characters.value = [];
      return;
    }
    if (!response.ok) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `人物列表加载失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    characters.value = Array.isArray(payload) ? payload : [];
  } catch (error) {
    showNotice("error", `人物列表加载失败：${error.message}`);
  } finally {
    characterLoading.value = false;
  }
}

async function onSaveCharacter() {
  if (!workId.value) {
    showNotice("info", "请先创建或选择作品，再添加人物设定");
    return;
  }
  const payload = {
    name: characterForm.value.name.trim(),
    displayName: characterForm.value.displayName.trim() || null,
    description: characterForm.value.description.trim() || null,
    personality: characterForm.value.personality.trim() || null
  };
  if (!payload.name) {
    showNotice("error", "人物名称不能为空");
    return;
  }
  characterLoading.value = true;
  try {
    const action = editingCharacterId.value
      ? updateCharacter(workId.value, editingCharacterId.value, payload)
      : createCharacter(workId.value, payload);
    const { response, payload: result } = await action;
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok) {
      const message = typeof result === "object" && result?.message
        ? result.message
        : `保存人物失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    resetCharacterForm();
    await onRefreshCharacters();
    showNotice("success", "人物设定已保存，下次生成将自动带上");
  } catch (error) {
    showNotice("error", `保存人物失败：${error.message}`);
  } finally {
    characterLoading.value = false;
  }
}

async function onDeleteCharacter(character) {
  if (!workId.value || !character?.id) {
    return;
  }
  if (!window.confirm(`确定删除人物「${character.name}」吗？`)) {
    return;
  }
  characterLoading.value = true;
  try {
    const { response, payload } = await deleteCharacter(workId.value, character.id);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok && response.status !== 204) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `删除人物失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    if (editingCharacterId.value === character.id) {
      resetCharacterForm();
    }
    await onRefreshCharacters();
    showNotice("success", `已删除人物「${character.name}」`);
  } catch (error) {
    showNotice("error", `删除人物失败：${error.message}`);
  } finally {
    characterLoading.value = false;
  }
}

async function onRefreshWorks() {
  try {
    const { response, payload } = await listWorks();
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `作品列表加载失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    works.value = Array.isArray(payload) ? payload : [];
  } catch (error) {
    showNotice("error", `作品列表加载失败：${error.message}`);
  }
}

async function loadWork(work) {
  if (!work?.workId) {
    return;
  }
  for (const controller of streamControllers.values()) {
    controller.abort();
  }
  streamControllers.clear();
  streamingIds.value = new Set();

  workId.value = work.workId;
  title.value = work.workTitle ?? "";
  syncTaskTitleInput();
  if (!isUntitledWorkName(title.value)) {
    titleNamedWorkIds.add(work.workId);
  }

  loading.value = true;
  try {
    const { response, payload } = await listScriptsByWorkId(work.workId);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `任务加载失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    if (Array.isArray(payload) && payload.length > 0) {
      applyRecords(payload);
    } else {
      restoreDraftForWork(work.workId);
    }
    await Promise.all(
      chapterItems.value.map((chapter, index) => loadRefineMessages(chapter.id, index + 1))
    );
    await onRefreshCharacters();
    persistDraft();
  } catch (error) {
    showNotice("error", `任务加载失败：${error.message}`);
  } finally {
    loading.value = false;
  }
}

async function onNewWork() {
  for (const controller of streamControllers.values()) {
    controller.abort();
  }
  streamControllers.clear();
  streamingIds.value = new Set();

  loading.value = true;
  try {
    const { response, payload } = await createWork("");
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok || !payload?.workId) {
      showNotice("error", "新建任务失败，请稍后重试");
      return;
    }
    workId.value = payload.workId;
    resetWorkbenchState();
    syncTaskTitleInput();
    await onRefreshWorks();
    persistDraft();
    scheduleTitleGeneration();
    showNotice("info", "已创建新任务，粘贴小说内容后将自动命名");
  } catch (error) {
    showNotice("error", `新建任务失败：${error.message}`);
  } finally {
    loading.value = false;
  }
}

async function onDeleteWork(work = null) {
  const targetWorkId = work?.workId || workId.value;
  if (!targetWorkId) {
    return;
  }
  const label = displayWorkTitle(work?.displayTitle || work?.workTitle || title.value);
  if (!window.confirm(`确定删除任务「${label}」及其全部内容吗？`)) {
    return;
  }
  loading.value = true;
  try {
    const { response, payload } = await deleteWork(targetWorkId);
    if (response.status === 401) {
      showNotice("error", "登录已过期，请重新登录");
      router.push("/login");
      return;
    }
    if (!response.ok && response.status !== 204 && response.status !== 404) {
      const message = typeof payload === "object" && payload?.message
        ? payload.message
        : `删除失败（HTTP ${response.status}）`;
      showNotice("error", message);
      return;
    }
    removeWorkbenchDraft(targetWorkId);
    titleNamedWorkIds.delete(targetWorkId);
    titleManualWorkIds.delete(targetWorkId);
    await onRefreshWorks();
    if (works.value.length > 0) {
      await loadWork(works.value[0]);
    } else {
      workId.value = "";
      resetWorkbenchState();
    }
    showNotice("success", `已删除任务「${label}」`);
  } catch (error) {
    showNotice("error", `删除任务失败：${error.message}`);
  } finally {
    loading.value = false;
  }
}

watch(workId, async (nextWorkId, previousWorkId) => {
  if (nextWorkId === previousWorkId) {
    return;
  }
  resetCharacterForm();
  await onRefreshCharacters();
});

watch([chapterItems, resultsById], scheduleDraftSave, { deep: true });

watch(taskTitleInput, () => {
  scheduleTaskTitleSave();
});

watch(
  () => chapterItems.value[0]?.content ?? "",
  () => {
    scheduleTitleGeneration();
  }
);

function handleBeforeUnload() {
  persistDraft();
}

async function loadCurrentUsername() {
  try {
    const { response, payload } = await currentUser();
    if (response.ok && payload?.username) {
      currentUsername.value = payload.username;
    }
  } catch {
    // 用户名展示失败不阻断工作台
  }
}

onMounted(async () => {
  await loadCurrentUsername();
  await onRefreshWorks();
  const state = loadWorkbenchState();
  const initialWork = state.activeWorkId
    ? works.value.find((item) => item.workId === state.activeWorkId)
    : works.value[0];
  if (initialWork) {
    await loadWork(initialWork);
  } else {
    await onNewWork();
  }
  window.addEventListener("beforeunload", handleBeforeUnload);
});

onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", handleBeforeUnload);
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer);
  }
  if (titleGenerateTimer) {
    clearTimeout(titleGenerateTimer);
  }
  if (titleSaveTimer) {
    clearTimeout(titleSaveTimer);
  }
  persistDraft();
  for (const controller of streamControllers.values()) {
    controller.abort();
  }
});

async function scrollResultToBottom(id) {
  await nextTick();
  const panel = resultPanelRefs.value[id];
  if (panel) {
    panel.scrollTop = panel.scrollHeight;
  }
}

async function onLogout() {
  loading.value = true;
  try {
    await logout();
  } finally {
    loading.value = false;
    router.push("/login");
  }
}

function stopChapterStream(id) {
  const controller = streamControllers.get(id);
  if (controller) {
    controller.abort();
    streamControllers.delete(id);
  }
}

function onCancelChapter(index) {
  const chapter = chapterItems.value[index];
  if (!chapter) {
    return;
  }
  stopChapterStream(chapter.id);
  const result = ensureResult(chapter.id);
  result.status = "cancelled";
  result.error = "";
  setStreaming(chapter.id, false);
}

function onRemoveChapter(index) {
  const chapter = chapterItems.value[index];
  if (!chapter) {
    return;
  }
  if (streamingIds.value.has(chapter.id)) {
    return;
  }
  stopChapterStream(chapter.id);
  chapterItems.value.splice(index, 1);
  if (chapterItems.value.length === 0) {
    chapterItems.value.push(createChapterItem());
  }
}

async function copyChapterResult(id) {
  const result = resultsById.value[id];
  const active = getActiveVersion(result);
  const content = active?.content || result?.content;
  if (!content) {
    return;
  }
  try {
    await navigator.clipboard.writeText(content);
    showNotice("success", "本章结果已复制到剪贴板");
  } catch (error) {
    showNotice("error", `复制失败：${error.message}`);
  }
}

function statusLabel(status, chapterId) {
  if (refiningIds.value.has(chapterId)) {
    return "改编中";
  }
  switch (status) {
    case "streaming":
      return "生成中";
    case "done":
      return "已完成";
    case "error":
      return "失败";
    case "cancelled":
      return "已取消";
    default:
      return "待生成";
  }
}

async function onRefineChapter(index) {
  const chapter = chapterItems.value[index];
  if (!chapter) {
    return;
  }
  if (!workId.value) {
    showNotice("info", "请先创建或选择任务");
    return;
  }
  const result = ensureResult(chapter.id);
  const instruction = (result.refineInput || "").trim();
  if (!instruction) {
    showNotice("error", "请输入修改要求");
    return;
  }
  const baseVersion = getActiveVersion(result);
  if (!baseVersion?.content?.trim()) {
    showNotice("error", "请先生成或加载本章剧本，再继续改编");
    return;
  }
  if (streamingIds.value.has(chapter.id) || refiningIds.value.has(chapter.id)) {
    return;
  }

  stopChapterStream(chapter.id);
  const controller = new AbortController();
  streamControllers.set(chapter.id, controller);

  const chapterNumber = index + 1;
  const refineVersion = createScriptVersion({
    kind: "refine",
    label: nextRefineLabel(result),
    instruction,
    status: "streaming"
  });
  addScriptVersion(result, refineVersion);
  const streamingVersionId = refineVersion.id;

  result.error = "";
  result.saved = false;
  result.generationId = crypto.randomUUID();
  setRefining(chapter.id, true);

  try {
    const payload = {
      workId: workId.value,
      generationId: result.generationId,
      chapterNumber,
      instruction,
      currentScriptContent: baseVersion.content.trim()
    };

    const { response, payload: errorPayload } = await refineScriptStream(
      payload,
      {
        onOpen(modelName) {
          result.model = modelName;
        },
        onMeta(metaJson) {
          try {
            const meta = JSON.parse(metaJson);
            if (meta.traceId) {
              result.traceId = meta.traceId;
            }
            if (meta.generationId) {
              result.generationId = meta.generationId;
            }
          } catch {
            // 忽略 meta 解析失败
          }
        },
        onToken(token) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.content += token || "";
          });
          scrollResultToBottom(chapter.id);
        },
        onWarn(message) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.warning = message;
          });
        },
        onDone() {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.content = sanitizeYamlDisplayText(version.content);
            version.status = "done";
          });
          result.status = "done";
        },
        onError(message) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.error = message || "改编失败";
            version.status = "error";
          });
          result.error = message || "改编失败";
        }
      },
      controller.signal
    );

    if (!response.ok) {
      const message = typeof errorPayload === "object" && errorPayload?.message
        ? errorPayload.message
        : `改编失败（HTTP ${response.status}）`;
      patchVersionById(chapter.id, streamingVersionId, (version) => {
        version.status = "error";
        version.error = message;
      });
      result.status = "error";
      result.error = message;
      showNotice("error", message);
      return;
    }

    if (!result.error) {
      result.status = "done";
      result.refineInput = "";
      await loadRefineMessages(chapter.id, chapterNumber);
      if (hasScriptContent(result)) {
        await persistChapterResult(index, chapter);
      }
      showNotice("success", `第 ${chapterNumber} 章改编完成`);
    }
  } catch (error) {
    if (error.name === "AbortError") {
      patchVersionById(chapter.id, streamingVersionId, (version) => {
        if (version.status === "streaming") {
          version.status = "cancelled";
        }
      });
      if (result.status === "streaming") {
        result.status = "cancelled";
      }
    } else {
      patchVersionById(chapter.id, streamingVersionId, (version) => {
        version.status = "error";
        version.error = error.message;
      });
      result.status = "error";
      result.error = error.message;
      showNotice("error", `改编失败：${error.message}`);
    }
  } finally {
    setRefining(chapter.id, false);
    streamControllers.delete(chapter.id);
  }
}

async function onGenerateChapter(index) {
  const chapter = chapterItems.value[index];
  if (!chapter) {
    return;
  }
  const chapterNumber = index + 1;
  const content = chapter.content ?? "";
  const validation = validateChapterContent(content, chapterNumber);
  if (!validation.ok) {
    const result = ensureResult(chapter.id);
    result.status = "error";
    result.error = validation.message;
    return;
  }

  const result = ensureResult(chapter.id);
  if (hasRefineVersions(result)) {
    const confirmed = window.confirm("重新生成将清空本章所有改编版本，是否继续？");
    if (!confirmed) {
      return;
    }
  }

  stopChapterStream(chapter.id);
  const controller = new AbortController();
  streamControllers.set(chapter.id, controller);

  const generateVersion = createScriptVersion({
    kind: "generate",
    label: "初稿",
    status: "streaming"
  });
  result.versions = [];
  addScriptVersion(result, generateVersion);
  const streamingVersionId = generateVersion.id;

  result.model = "";
  result.error = "";
  result.warning = "";
  result.saved = false;
  result.recordId = null;
  result.traceId = "";
  result.generationId = crypto.randomUUID();
  result.status = "streaming";
  setStreaming(chapter.id, true);

  try {
    const payload = {
      workId: workId.value || null,
      generationId: result.generationId,
      chapterNumber,
      chapterContent: content.trim()
    };

    const { response, payload: errorPayload } = await generateScriptStream(
      payload,
      {
        onOpen(modelName) {
          result.model = modelName;
        },
        onMeta(metaJson) {
          try {
            const meta = JSON.parse(metaJson);
            if (meta.workId && !workId.value) {
              workId.value = meta.workId;
            }
            if (meta.traceId) {
              result.traceId = meta.traceId;
            }
            if (meta.generationId) {
              result.generationId = meta.generationId;
            }
          } catch {
            // 忽略 meta 解析失败
          }
        },
        onToken(token) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.content += token;
          });
          scrollResultToBottom(chapter.id);
        },
        onWarn(message) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.warning = message;
          });
        },
        onDone() {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.content = sanitizeYamlDisplayText(version.content);
            version.status = "done";
          });
          result.status = "done";
        },
        onError(message) {
          patchVersionById(chapter.id, streamingVersionId, (version) => {
            version.status = "error";
            version.error = message;
          });
          result.status = "error";
          result.error = message;
        }
      },
      controller.signal
    );

    if (response.status === 401) {
      result.status = "error";
      result.error = "登录已过期，请重新登录";
      router.push("/login");
      return;
    }

    if (!response.ok) {
      const message = typeof errorPayload === "object" && errorPayload?.message
        ? errorPayload.message
        : `请求失败（HTTP ${response.status}）`;
      result.status = "error";
      result.error = message;
    } else if (result.status === "done" && hasScriptContent(result)) {
      await persistChapterResult(index, chapter);
    }
  } catch (error) {
    if (error.name === "AbortError") {
      patchVersionById(chapter.id, streamingVersionId, (version) => {
        if (version.status === "streaming") {
          version.status = "cancelled";
        }
      });
      if (result.status === "streaming") {
        result.status = "cancelled";
      }
    } else {
      patchVersionById(chapter.id, streamingVersionId, (version) => {
        version.status = "error";
        version.error = error.message;
      });
      result.status = "error";
      result.error = error.message;
    }
  } finally {
    setStreaming(chapter.id, false);
    streamControllers.delete(chapter.id);
  }
}
</script>

<template>
  <main class="workbench-page">
    <header class="workbench-header">
      <div>
        <h1>剧本工作台</h1>
        <p class="sub-text">
          左侧为任务列表，中间按章填写并生成，右侧展示流式输出。
          <template v-if="title">当前任务：{{ displayWorkTitle(title) }}</template>
        </p>
      </div>
      <div class="header-actions">
        <span v-if="currentUsername" class="user-badge">{{ currentUsername }}</span>
        <button
          type="button"
          class="logout-btn"
          :disabled="loading"
          @click="onLogout"
        >
          退出登录
        </button>
      </div>
    </header>

    <p class="notice workbench-notice" :data-type="noticeType" v-if="notice">{{ notice }}</p>

    <section class="workbench-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <TaskSidebar
        :works="works"
        :active-work-id="workId"
        :collapsed="sidebarCollapsed"
        :naming-work-id="titleNamingWorkId"
        :loading="loading"
        @select="loadWork"
        @new="onNewWork"
        @delete="onDeleteWork"
        @rename="onRenameTask"
        @toggle-collapse="toggleSidebarCollapsed"
      />

      <section class="workbench-layout">
      <div class="workbench-panel input-panel">
        <header class="panel-header">
          <div>
            <h2 class="panel-title">用户提交</h2>
            <p class="panel-subtitle">填写章节原文、维护人物，并按章触发生成</p>
          </div>
        </header>

        <section class="task-title-field">
          <label for="task-title" class="field-label">任务名称</label>
          <input
            id="task-title"
            v-model="taskTitleInput"
            class="field-input"
            :disabled="!workId || titleSaving || Boolean(titleNamingWorkId)"
            maxlength="32"
            placeholder="自定义名称；留空则根据第一章内容自动生成短标题"
          />
          <p class="task-title-hint">
            <template v-if="titleNamingWorkId === workId">正在自动生成标题...</template>
            <template v-else>也可在左侧列表双击任务名快速重命名</template>
          </p>
        </section>

        <section class="character-manager">
          <div class="character-manager-header">
            <div>
              <h3 class="section-title">人物设定</h3>
              <p class="section-subtitle">跨章节共享，生成时自动注入 Prompt</p>
            </div>
            <button
              type="button"
              class="ghost-btn work-action-btn"
              :disabled="characterLoading || !workId"
              @click="onRefreshCharacters"
            >
              刷新
            </button>
          </div>
          <p v-if="!workId" class="character-empty">新建或选择任务后，可在此维护跨章人物设定。</p>
          <template v-else>
            <ul v-if="characters.length > 0" class="character-list">
              <li
                v-for="character in characters"
                :key="character.id"
                class="character-list-item"
                :class="{ active: editingCharacterId === character.id }"
              >
                <button type="button" class="character-select-btn" @click="startEditCharacter(character)">
                  <span class="character-avatar" aria-hidden="true">{{ (character.name || "?").slice(0, 1) }}</span>
                  <span class="character-meta">
                    <span class="character-name">{{ character.name }}</span>
                    <span v-if="character.displayName" class="character-alias">{{ character.displayName }}</span>
                  </span>
                </button>
                <button
                  type="button"
                  class="character-delete-btn"
                  :disabled="characterLoading"
                  @click="onDeleteCharacter(character)"
                >
                  删除
                </button>
              </li>
            </ul>
            <p v-else class="character-empty">暂无人物设定，添加后下次生成会自动注入 prompt。</p>

            <form class="character-form" autocomplete="off" @submit.prevent>
              <div class="character-form-row">
                <label for="character-name">名称</label>
                <input
                  id="character-name"
                  v-model="characterForm.name"
                  name="ntc-character-name"
                  autocomplete="off"
                  placeholder="剧本中使用的名称"
                />
              </div>
              <div class="character-form-row">
                <label for="character-display-name">别名</label>
                <input
                  id="character-display-name"
                  v-model="characterForm.displayName"
                  name="ntc-character-display-name"
                  autocomplete="off"
                  placeholder="可选"
                />
              </div>
              <div class="character-form-row">
                <label for="character-description">身份</label>
                <textarea
                  id="character-description"
                  v-model="characterForm.description"
                  rows="2"
                  placeholder="身份或背景描述"
                />
              </div>
              <div class="character-form-row">
                <label for="character-personality">性格</label>
                <textarea
                  id="character-personality"
                  v-model="characterForm.personality"
                  rows="2"
                  placeholder="性格特征"
                />
              </div>
              <div class="character-form-actions">
                <button
                  type="button"
                  class="primary-btn work-action-btn"
                  :disabled="characterLoading"
                  @click="onSaveCharacter"
                >
                  {{ editingCharacterId ? "更新人物" : "添加人物" }}
                </button>
                <button
                  v-if="editingCharacterId"
                  type="button"
                  class="secondary work-action-btn"
                  :disabled="characterLoading"
                  @click="resetCharacterForm"
                >
                  取消编辑
                </button>
              </div>
            </form>
          </template>
        </section>

        <ChapterFieldList
          v-model="chapterItems"
          :streaming-ids="streamingIds"
          @generate="onGenerateChapter"
          @cancel="onCancelChapter"
          @remove="onRemoveChapter"
        />
      </div>

      <div class="workbench-panel output-panel">
        <header class="panel-header">
          <div>
            <h2 class="panel-title">大模型结果</h2>
            <p class="panel-subtitle">按章流式输出 YAML 剧本，支持多轮改编与历史版本查看</p>
          </div>
        </header>

        <div class="chapter-results">
          <div
            v-for="(chapter, index) in chapterItems"
            :key="chapter.id"
            class="chapter-result-item"
          >
            <div class="chapter-result-header">
              <h3 class="chapter-result-title">第 {{ index + 1 }} 章</h3>
              <div class="chapter-result-actions">
                <span class="chapter-status" :data-status="resultsById[chapter.id]?.status || 'idle'">
                  {{ statusLabel(resultsById[chapter.id]?.status || 'idle', chapter.id) }}
                  <template v-if="resultsById[chapter.id]?.saved"> · 已保存</template>
                </span>
                <button
                  type="button"
                  class="copy-result-btn"
                  :disabled="!hasScriptContent(resultsById[chapter.id])"
                  @click="copyChapterResult(chapter.id)"
                >
                  复制
                </button>
              </div>
            </div>

            <p v-if="resultsById[chapter.id]?.model" class="result-meta">
              模型：{{ resultsById[chapter.id].model }}
              <template v-if="resultsById[chapter.id]?.traceId">
                · trace：{{ resultsById[chapter.id].traceId }}
              </template>
            </p>
            <p v-else-if="streamingIds.has(chapter.id) || refiningIds.has(chapter.id)" class="result-meta">
              已连接，等待模型首包输出（大模型冷启动可能需要 10～60 秒）...
            </p>

            <p
              v-if="resultsById[chapter.id]?.error"
              class="chapter-error"
            >
              {{ resultsById[chapter.id].error }}
            </p>
            <div
              :ref="(el) => { if (el) resultPanelRefs[chapter.id] = el; }"
              class="version-list"
            >
              <p
                v-if="!resultsById[chapter.id]?.versions?.length && !streamingIds.has(chapter.id) && !refiningIds.has(chapter.id)"
                class="version-empty"
              >
                本章生成结果将在这里展示
              </p>

              <section
                v-for="version in resultsById[chapter.id]?.versions || []"
                :key="version.id"
                class="version-card"
                :class="{
                  active: version.id === resultsById[chapter.id]?.activeVersionId,
                  collapsed: version.collapsed,
                  streaming: version.status === 'streaming'
                }"
              >
                <header class="version-card-header">
                  <button
                    type="button"
                    class="version-toggle-btn"
                    @click="toggleVersionCollapsed(chapter.id, version.id)"
                  >
                    <span class="version-toggle-icon">{{ version.collapsed ? '▸' : '▾' }}</span>
                    <span class="version-badge" :data-kind="version.kind">{{ version.label }}</span>
                    <span v-if="version.id === resultsById[chapter.id]?.activeVersionId" class="version-active-tag">当前使用</span>
                  </button>
                  <div class="version-card-meta">
                    <span v-if="version.instruction" class="version-instruction">「{{ version.instruction }}」</span>
                    <span v-if="version.content" class="version-size">{{ version.content.length }} 字</span>
                  </div>
                </header>

                <div v-show="!version.collapsed" class="version-card-body">
                  <p v-if="version.warning" class="chapter-warning version-warning">
                    结构提示：{{ version.warning }}
                  </p>
                  <p v-if="version.error" class="chapter-error version-error">
                    {{ version.error }}
                  </p>
                  <pre
                    class="result-content"
                    :class="{ streaming: version.status === 'streaming' }"
                  >
                    <template v-if="version.status === 'streaming'">
                      <span class="yaml-stream-raw">{{ version.content }}</span><span class="stream-cursor">▋</span>
                    </template>
                    <template v-else-if="version.content">
                      <span
                        v-for="(line, lineIndex) in splitYamlDisplayLines(version.content)"
                        :key="lineIndex"
                        class="yaml-line"
                        :class="{
                          'yaml-line-kv': usesHangingWrapIndent(line.displayMode),
                          'yaml-line-value-continuation': line.displayMode === 'valueContinuation'
                        }"
                        :style="usesHangingWrapIndent(line.displayMode)
                          ? {
                              paddingLeft: `${line.wrapIndentCh}ch`,
                              textIndent: `-${YAML_HANG_WRAP_EXTRA_CH}ch`
                            }
                          : { paddingLeft: `${line.indentCh}ch` }"
                      >{{ line.text }}</span>
                    </template>
                  </pre>
                </div>
              </section>
            </div>

            <section
              v-if="hasScriptContent(resultsById[chapter.id]) || resultsById[chapter.id]?.versions?.length"
              class="refine-panel"
            >
              <h4 class="refine-panel-title">继续改编</h4>
              <p class="refine-panel-hint">
                每次改编会保留旧版本并新开一个结果框；历史版本仅可展开查看，后续改编始终基于当前最新版本。
              </p>
              <div class="refine-input-row">
                <input
                  v-model="resultsById[chapter.id].refineInput"
                  type="text"
                  class="refine-input"
                  placeholder="例如：把第二幕对白写得更紧张"
                  :disabled="!workId || streamingIds.has(chapter.id) || refiningIds.has(chapter.id)"
                  @keyup.enter="onRefineChapter(index)"
                />
                <button
                  type="button"
                  class="refine-submit-btn"
                  :disabled="!workId || !hasScriptContent(resultsById[chapter.id]) || streamingIds.has(chapter.id) || refiningIds.has(chapter.id)"
                  @click="onRefineChapter(index)"
                >
                  继续改编
                </button>
              </div>
            </section>
          </div>
        </div>
      </div>
      </section>
    </section>
  </main>
</template>
