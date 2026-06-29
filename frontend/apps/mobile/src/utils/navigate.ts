import type { RouteLocationRaw, Router } from "vue-router";

/** 生成完整 URL，供长按「在新标签页打开」等场景使用 */
export function resolveMobileHref(router: Router, to: string | RouteLocationRaw): string {
  const resolved = router.resolve(to);
  if (resolved.href.startsWith("http://") || resolved.href.startsWith("https://")) {
    return resolved.href;
  }
  return `${window.location.origin}${resolved.href}`;
}

/** 优先 SPA 内跳转，避免 Safari 整页刷新白屏 */
export function spaNavigate(router: Router, to: string | RouteLocationRaw) {
  return router.push(to).catch((error: unknown) => {
    const name = error && typeof error === "object" && "name" in error ? String(error.name) : "";
    if (name === "NavigationDuplicated") return;
    window.location.assign(resolveMobileHref(router, to));
  });
}
