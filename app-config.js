const appConfig = {
  isDesktopMode: import.meta.env.VITE_IS_DESKTOP_MODE,
  isPreviewMode: import.meta.env.VITE_IS_PREVIEW_MODE,
  previewBuildId: import.meta.env.VITE_PREVIEW_BUILD_ID
}

window.appConfig = appConfig;
