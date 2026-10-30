"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

/*
 * Guards in-app navigation while a screen has unsaved changes. The App Router has
 * no route-change event to cancel, so every shell link goes through <GuardedLink>,
 * which asks first when a screen has called useBlockNavigation(true). Other ways of
 * leaving the screen, like signing out, go through useGuardedAction. Tab close and
 * reload are covered separately by useUnsavedChangesWarning.
 */

interface NavigationGuardApi {
  setBlocked: (blocked: boolean) => void;
  /** Navigates to `href`, asking first if navigation is blocked. */
  navigate: (href: string) => void;
  /** Runs `action`, asking first if navigation is blocked. */
  run: (action: () => void) => void;
  isBlocked: () => boolean;
}

const NavigationGuardContext = createContext<NavigationGuardApi | null>(null);

export function NavigationGuardProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const blocked = useRef(false);
  const [pendingAction, setPendingAction] = useState<(() => void) | null>(null);

  const api = useMemo<NavigationGuardApi>(() => {
    const run = (action: () => void) => {
      // Wrapped, because a function passed to a state setter would be called as an updater.
      if (blocked.current) setPendingAction(() => action);
      else action();
    };
    return {
      setBlocked: (value) => {
        blocked.current = value;
      },
      isBlocked: () => blocked.current,
      navigate: (href) => run(() => router.push(href)),
      run,
    };
  }, [router]);

  return (
    <NavigationGuardContext.Provider value={api}>
      {children}
      {pendingAction && (
        <ConfirmDialog
          title="Discard unsaved changes?"
          message="You have changes that haven’t been saved. Leave anyway?"
          confirmLabel="Discard changes"
          danger
          onClose={() => setPendingAction(null)}
          onConfirm={() => {
            blocked.current = false;
            pendingAction();
          }}
        />
      )}
    </NavigationGuardContext.Provider>
  );
}

function useNavigationGuard(): NavigationGuardApi {
  const context = useContext(NavigationGuardContext);
  if (!context) throw new Error("Navigation guard used outside <NavigationGuardProvider>");
  return context;
}

/** Blocks shell navigation while `dirty` is true; unblocks on unmount. */
export function useBlockNavigation(dirty: boolean): void {
  const { setBlocked } = useNavigationGuard();
  useEffect(() => {
    setBlocked(dirty);
    return () => setBlocked(false);
  }, [dirty, setBlocked]);
}

/** Programmatic navigation that respects the guard (e.g. after an action). */
export function useGuardedNavigate(): (href: string) => void {
  return useNavigationGuard().navigate;
}

/** Runs an action that leaves the screen some other way (e.g. signing out), asking first like a guarded link. */
export function useGuardedAction(): (action: () => void) => void {
  return useNavigationGuard().run;
}

/** A next/link that asks before leaving a screen with unsaved changes. */
export function GuardedLink({ href, onClick, ...props }: React.ComponentProps<typeof Link> & { href: string }) {
  const guard = useNavigationGuard();
  const handleClick = useCallback(
    (event: React.MouseEvent<HTMLAnchorElement>) => {
      onClick?.(event);
      // Let modified clicks (new tab, etc.) through untouched.
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return;
      if (guard.isBlocked()) {
        event.preventDefault();
        guard.navigate(href);
      }
    },
    [guard, href, onClick],
  );
  return <Link href={href} onClick={handleClick} {...props} />;
}
