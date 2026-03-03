# Network platform differences

The shared network contract centralizes timeout, redirect, base header, user-agent, and cookie semantics in `SharedHttpClientPolicy`.

## Expected platform differences (not regressions)

- **Android app cookie store (`AndroidCookieJar`)** uses `android.webkit.CookieManager`, so cookie persistence and lifecycle are delegated to WebView/system behavior.
- **Shared Android cookie store (`AndroidSharedCookieStore`)** is in-memory only and scoped to process lifetime because the shared module cannot depend on Android WebView APIs.
- **Desktop cookie store (`DesktopCookieStore`)** uses `java.net.CookieManager` via `JavaNetCookieJar`, inheriting JVM/system cookie handling.
- **TLS/SSL trust roots** come from the underlying platform trust store (Android system, JVM/system certificates on desktop). Differences in accepted certificates across devices/OSes are expected.
- **Challenge bypass support** is platform specific: Android app wires `CloudflareInterceptor`, while shared/desktop factories expose no active solver unless explicitly enabled.
