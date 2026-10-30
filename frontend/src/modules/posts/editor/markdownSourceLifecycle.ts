import { ApiError } from "@/types/api";
import type { MarkdownSourceRef } from "@/types/markdown";
import { deleteMarkdownSource } from "../api";

/** Missing already means removed, including a retry after a lost success response. */
export async function deleteSourceIfPresent(id: string): Promise<void> {
  try {
    await deleteMarkdownSource(id);
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) throw error;
  }
}

/**
 * Whether `source` is an upload this session that was never saved on the post, so it's
 * ours to delete immediately. A source that matches the post's saved attachment is left
 * for `deleteIfUnused` to clean up once the change (replacement or removal) is saved.
 */
export function isUnsavedUpload(
  source: MarkdownSourceRef | null,
  savedSourceId: string | null,
): source is MarkdownSourceRef {
  return source !== null && source.id !== savedSourceId;
}
