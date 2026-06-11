<script setup>
const chapterItems = defineModel({ type: Array, default: () => [] });

defineProps({
  streamingIds: {
    type: Set,
    default: () => new Set()
  }
});

const emit = defineEmits(["generate", "cancel", "remove"]);

function addChapter() {
  chapterItems.value.push({
    id: crypto.randomUUID(),
    content: ""
  });
}

function onGenerate(index) {
  emit("generate", index);
}

function onCancel(index) {
  emit("cancel", index);
}

function onRemove(index) {
  emit("remove", index);
}
</script>

<template>
  <div class="chapter-list">
    <div v-for="(chapter, index) in chapterItems" :key="chapter.id" class="chapter-item">
      <div class="chapter-item-header">
        <label :for="`chapter-${chapter.id}`">第 {{ index + 1 }} 章</label>
        <button
          v-if="chapterItems.length > 1"
          type="button"
          class="chapter-remove-btn"
          :disabled="streamingIds.has(chapter.id)"
          @click="onRemove(index)"
        >
          删除
        </button>
      </div>
      <textarea
        :id="`chapter-${chapter.id}`"
        v-model="chapter.content"
        rows="5"
        placeholder="请输入本章小说内容"
      />
      <div class="chapter-actions">
        <button
          type="button"
          class="chapter-generate-btn"
          :disabled="streamingIds.has(chapter.id)"
          @click="onGenerate(index)"
        >
          {{ streamingIds.has(chapter.id) ? "生成中..." : "生成本章剧本" }}
        </button>
        <button
          v-if="streamingIds.has(chapter.id)"
          type="button"
          class="chapter-cancel-btn"
          @click="onCancel(index)"
        >
          取消生成
        </button>
      </div>
    </div>
    <button type="button" class="add-chapter-btn" @click="addChapter">添加章节</button>
  </div>
</template>
