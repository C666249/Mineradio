# ArcDock v1.0.0 · Android Global Edge Launcher

ArcDock is a fully offline Android floating radial shortcut launcher.
Uses a persistent user-initiated foreground service with a TYPE_APPLICATION_OVERLAY handle. No accessibility service, network access, account, or paid service.

**How to start:** Open ArcDock → tap 开启全局悬浮控制环 → grant Display over other apps → return. Drag the small edge handle horizontally inward, hover a radial item, and release. Tap a dock item to execute. Drag the collapsed handle vertically to reposition. You can turn it off in the app or by pressing Stop in the ongoing notification.

**Customize:** App settings → 管理五个快捷项. Change hand, colors, haptics, or the five actions; native overlay settings update live. Actions: Camera, Browser/URL, Dialer, Share, Settings, Installed app, Torch, Copy to clipboard.

**Android requirements:** Android 8+, recommended Android 12+. Android 14+ needs a special-use foreground-service declaration. Android 13+ can block the visible notification unless the notification permission is granted. Huawei/Honor MagicOS may need enabling auto-launch/background activity under system Battery/App launch settings. System security screens may hide overlays. Overlay is not a system navigation gesture replacement.

**Build:** Gradle 8.7, Java 17, AGP 8.6.1, compileSdk 35. CI builds a debug-signed APK and publishes it to the public GitHub Release. Each CI runner generates a fresh debug signing key; remove v1.0.0 before installing a future differently signed build, unless you set up a consistent release signing key. This v1.0.0 app uses applicationId dev.arcdock.launcher (different from v0.1.0).

**Release location:** https://github.com/C666249/Mineradio/releases/tag/arcdock-v1.0.0 ; project hosted in isolated arcdock-cloud-v010 branch, main is unchanged.