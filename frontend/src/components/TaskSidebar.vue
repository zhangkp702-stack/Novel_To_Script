<script setup>
import { nextTick, ref } from "vue";

defineProps({
  works: {
    type: Array,
    default: () => []
  },
  activeWorkId: {
    type: String,
    default: ""
  },
  collapsed: {
    type: Boolean,
    default: false
  },
  namingWorkId: {
    type: String,
    default: ""
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["select", "new", "delete", "rename", "toggle-collapse"]);

const editingWorkId = ref("");
const editingTitle = ref("");
const editInputRef = ref(null);

function displayTitle(work) {
  const raw = work?.displayTitle || work?.workTitle || "";
  if (!raw.trim() || raw === "未命名作品") {
    return "新任务";
  }
  return raw.trim();
}

async function startRename(work) {
  if (!work?.workId || editingWorkId.value) {
    return;
  }
  editingWorkId.value = work.workId;
  editingTitle.value = displayTitle(work) === "新任务" ? "" : displayTitle(work);
  await nextTick();
  editInputRef.value?.focus();
  editInputRef.value?.select();
}

function cancelRename() {
  editingWorkId.value = "";
  editingTitle.value = "";
}

function commitRename(work) {
  if (!work?.workId || editingWorkId.value !== work.workId) {
    return;
  }
  emit("rename", { work, title: editingTitle.value.trim() });
  cancelRename();
}
</script>

<template>
  <aside class="task-sidebar" :class="{ collapsed }">
    <div class="task-sidebar-inner">
      <div class="task-sidebar-top">
        <button
          v-if="!collapsed"
          type="button"
          class="task-new-btn"
          :disabled="loading"
          @click="emit('new')"
        >
          <span class="task-new-icon" aria-hidden="true">+</span>
          <span>新建任务</span>
        </button>
        <button
          type="button"
          class="task-collapse-btn"
          :title="collapsed ? '展开任务列表' : '收起任务列表'"
          @click="emit('toggle-collapse')"
        >
          <span class="task-collapse-icon" aria-hidden="true">{{ collapsed ? "›" : "‹" }}</span>
        </button>
      </div>

      <p v-if="!collapsed" class="task-sidebar-label">我的任务</p>

      <ul v-if="!collapsed" class="task-list">
        <li
          v-for="work in works"
          :key="work.workId"
          class="task-list-item"
          :class="{ active: work.workId === activeWorkId, editing: editingWorkId === work.workId }"
        >
          <input
            v-if="editingWorkId === work.workId"
            ref="editInputRef"
            v-model="editingTitle"
            class="task-rename-input"
            maxlength="32"
            placeholder="输入任务名称"
            @keydown.enter.prevent="commitRename(work)"
            @keydown.esc.prevent="cancelRename"
            @blur="commitRename(work)"
          />
          <template v-else>
            <button
              type="button"
              class="task-title-btn"
              :disabled="loading"
              :title="`${displayTitle(work)}（双击可重命名）`"
              @click="emit('select', work)"
              @dblclick.stop="startRename(work)"
            >
              <span class="task-item-dot" aria-hidden="true" />
              <span class="task-title-text">
                {{ namingWorkId === work.workId ? "命名中..." : displayTitle(work) }}
              </span>
            </button>
            <div
              v-if="work.workId === activeWorkId"
              class="task-item-actions"
            >
              <button
                type="button"
                class="task-icon-btn"
                :disabled="loading"
                title="重命名"
                @click="startRename(work)"
              >
                <span aria-hidden="true">✎</span>
              </button>
              <button
                type="button"
                class="task-icon-btn task-icon-btn-danger"
                :disabled="loading"
                title="删除当前任务"
                @click="emit('delete', work)"
              >
                <span aria-hidden="true">×</span>
              </button>
            </div>
          </template>
        </li>
      </ul>
    </div>
  </aside>
</template>
