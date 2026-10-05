package com.togger.ui.navbar

import com.raquo.laminar.api.L._
import com.togger.ui.organization.TgIcons
import com.togger.ui.theme.{ThemeMode, ThemeStore}

class ThemeToggleButton(themeStore: ThemeStore) {

  val element =
    button(
      `type`("button"),
      cls(ThemeToggleButton.buttonClass),
      title <-- themeStore.mode.map(ThemeToggleButton.labelFor),
      onClick.mapToUnit --> (_ => themeStore.cycle()),
      span(cls("sr-only"), "Toggle color theme"),
      child <-- themeStore.mode.map(ThemeToggleButton.iconFor)
    )
}

object ThemeToggleButton {
  // same icon-button treatment as NavbarComponent's mobileSidebarButton
  val buttonClass: String =
    "text-heading bg-transparent box-border border border-transparent hover:bg-neutral-secondary-medium " +
      "focus:ring-4 focus:ring-neutral-tertiary font-medium leading-5 rounded-base text-sm p-2 " +
      "focus:outline-none cursor-pointer"

  def iconFor(mode: ThemeMode) = mode match {
    case ThemeMode.Light  => TgIcons.sunIcon("w-5 h-5")
    case ThemeMode.Dark   => TgIcons.moonIcon("w-5 h-5")
    case ThemeMode.System => TgIcons.desktopIcon("w-5 h-5")
  }

  def labelFor(mode: ThemeMode): String = mode match {
    case ThemeMode.Light  => "Light theme — click for dark"
    case ThemeMode.Dark   => "Dark theme — click to follow system"
    case ThemeMode.System => "Following system theme — click for light"
  }
}
