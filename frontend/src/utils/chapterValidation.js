export const MAX_CHAPTER_CONTENT_LENGTH = 50_000;

export function validateChapterContent(content, chapterNumber) {
  if (!content || !content.trim()) {
    return {
      ok: false,
      message: `请先填写第 ${chapterNumber} 章内容`
    };
  }
  if (content.length > MAX_CHAPTER_CONTENT_LENGTH) {
    return {
      ok: false,
      message: `第 ${chapterNumber} 章内容超过 ${MAX_CHAPTER_CONTENT_LENGTH} 字上限`
    };
  }
  return { ok: true, message: "" };
}
