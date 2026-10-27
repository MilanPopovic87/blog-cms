import { describe, expect, it } from "vitest";
import type { MarkdownSourceRef } from "@/types/markdown";
import { isUnsavedUpload } from "./markdownSourceLifecycle";

function source(id: string): MarkdownSourceRef {
  return { id, fileName: `${id}.md`, sizeBytes: 10, url: `/api/v1/markdown-sources/${id}/file` };
}

describe("isUnsavedUpload", () => {
  it("is false when there is no current source", () => {
    expect(isUnsavedUpload(null, null)).toBe(false);
    expect(isUnsavedUpload(null, "saved-id")).toBe(false);
  });

  it("is true for a fresh upload on a post with no saved attachment", () => {
    expect(isUnsavedUpload(source("a"), null)).toBe(true);
  });

  it("is false for the source already saved on the post", () => {
    expect(isUnsavedUpload(source("a"), "a")).toBe(false);
  });

  it("is true for a replacement uploaded over a saved attachment", () => {
    // "a" is saved on the post; the source on screen is now "b", a fresh upload.
    expect(isUnsavedUpload(source("b"), "a")).toBe(true);
  });
});
