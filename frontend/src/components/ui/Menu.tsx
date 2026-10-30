"use client";

import { useEffect, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export interface MenuItem {
  label: string;
  icon?: LucideIcon;
  onSelect: () => void;
  danger?: boolean;
  disabled?: boolean;
}

export interface MenuProps {
  /** Renders the trigger; spread `props` onto a button. */
  trigger: (props: { onClick: () => void; "aria-expanded": boolean; "aria-haspopup": "menu" }) => React.ReactNode;
  items: (MenuItem | "separator")[];
  align?: "left" | "right";
  placement?: "top" | "bottom";
}

/** Small dropdown of actions — closes on outside click, Escape, or selection. */
export function Menu({ trigger, items, align = "right", placement = "bottom" }: MenuProps) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (!boxRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div ref={boxRef} className="relative inline-flex">
      {trigger({ onClick: () => setOpen((v) => !v), "aria-expanded": open, "aria-haspopup": "menu" })}
      {open && (
        <div
          role="menu"
          className={cn(
            "absolute z-[var(--z-dropdown)] min-w-[180px] rounded-md border-[1.5px] border-line bg-card p-1 shadow-popover",
            placement === "top" ? "bottom-[calc(100%+4px)]" : "top-[calc(100%+4px)]",
            align === "right" ? "right-0" : "left-0",
          )}
        >
          {items.map((item, i) =>
            item === "separator" ? (
              <div key={`sep-${i}`} className="my-1 border-t border-line" />
            ) : (
              <button
                key={item.label}
                type="button"
                role="menuitem"
                disabled={item.disabled}
                onClick={(e) => {
                  e.stopPropagation();
                  setOpen(false);
                  item.onSelect();
                }}
                className={cn(
                  "flex w-full cursor-pointer items-center gap-2 rounded-sm px-2.5 py-1.5 text-left text-[12.5px] hover:bg-hover disabled:cursor-not-allowed disabled:opacity-40",
                  item.danger ? "text-error-text" : "text-ink",
                )}
              >
                {item.icon && <item.icon size={13} aria-hidden />}
                {item.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  );
}
