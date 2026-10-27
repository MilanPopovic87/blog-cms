"use client";

import { useRef, useState } from "react";
import type { Editor } from "@tiptap/react";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";
import { isDocEmpty } from "@/lib/content";
import { clampHeadingLevels, MARKDOWN_EXTENSIONS, readMarkdownFile, validateMarkdownFile } from "@/lib/markdown-import";
import type { ContentDoc } from "@/types/content";
import type { MarkdownSourceRef } from "@/types/markdown";
import { uploadMarkdownSource } from "../api";
import { deleteSourceIfPresent, isUnsavedUpload } from "./markdownSourceLifecycle";

interface ParsedImport {
  file: File;
  doc: ContentDoc;
}

/** Replaces the post body with an imported .md file (asks first if the post isn't empty). */
export function useMarkdownImport(
  editor: Editor | null,
  onSourceChange: (source: MarkdownSourceRef | null) => void,
  onImportingChange: (importing: boolean) => void,
  currentSource: MarkdownSourceRef | null,
  savedSourceId: string | null,
) {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [pending, setPending] = useState<ParsedImport | null>(null);
  const [uploading, setUploading] = useState(false);
  const [removing, setRemoving] = useState(false);

  const setImporting = (importing: boolean) => {
    setUploading(importing);
    onImportingChange(importing);
  };

  // The original file is stored first, so a failed upload leaves the post as it was.
  const apply = async ({ file, doc }: ParsedImport) => {
    if (!editor) return;
    const previous = currentSource;
    setImporting(true);
    try {
      const source = await uploadMarkdownSource(file);
      // The page can still be left from outside the guarded links (e.g. the browser's back button).
      if (editor.isDestroyed) {
        await deleteSourceIfPresent(source.id);
        return;
      }
      editor.chain().focus().setContent(doc).run();
      onSourceChange(source);
      toast.success(`Imported "${file.name}".`);
      // The old upload is only ours to clean up if it was never saved on the post —
      // deleteIfUnused already handles a saved attachment once the replacement is saved.
      if (isUnsavedUpload(previous, savedSourceId)) {
        try {
          await deleteSourceIfPresent(previous.id);
        } catch {
          toast.error(`Imported "${file.name}", but couldn't remove the previous upload.`);
        }
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : `Couldn't upload "${file.name}".`);
      throw error;
    } finally {
      setImporting(false);
    }
  };

  // An unsaved upload (never attached to the saved post) is safe to delete immediately.
  // A saved attachment is left for deleteIfUnused to clean up once the removal is saved.
  const removeSource = async () => {
    if (!currentSource) return;
    if (!isUnsavedUpload(currentSource, savedSourceId)) {
      onSourceChange(null);
      return;
    }
    setRemoving(true);
    try {
      await deleteSourceIfPresent(currentSource.id);
      onSourceChange(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Couldn't remove the Markdown file.");
    } finally {
      setRemoving(false);
    }
  };

  const onFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Allows picking the same file again.
    event.target.value = "";
    if (!file || !editor) return;

    const invalid = validateMarkdownFile(file);
    if (invalid) {
      toast.error(invalid);
      return;
    }

    let parsed: ParsedImport;
    try {
      const markdown = await readMarkdownFile(file);
      if (!editor.markdown) throw new Error("The editor can't read Markdown.");
      let doc: ContentDoc;
      try {
        doc = clampHeadingLevels(editor.markdown.parse(markdown) as ContentDoc);
      } catch {
        throw new Error(`Couldn't convert "${file.name}" from Markdown.`);
      }
      if (isDocEmpty(doc)) throw new Error(`"${file.name}" has no content to import.`);
      parsed = { file, doc };
    } catch (error) {
      toast.error(error instanceof Error ? error.message : `Couldn't import "${file.name}".`);
      return;
    }

    if (isDocEmpty(editor.getJSON() as ContentDoc)) apply(parsed).catch(() => {});
    else setPending(parsed);
  };

  const ui = (
    <>
      <input
        ref={inputRef}
        type="file"
        accept={[...MARKDOWN_EXTENSIONS, "text/markdown"].join(",")}
        className="hidden"
        tabIndex={-1}
        aria-hidden
        onChange={onFileChange}
      />
      {pending && (
        <ConfirmDialog
          title="Replace post content?"
          message={
            <>
              The content of <strong>{pending.file.name}</strong> will replace everything in the editor. You can undo
              this with Ctrl+Z.
            </>
          }
          confirmLabel="Replace content"
          onConfirm={() => apply(pending)}
          onClose={() => setPending(null)}
        />
      )}
    </>
  );

  return { openFilePicker: () => inputRef.current?.click(), uploading, removing, removeSource, ui };
}
