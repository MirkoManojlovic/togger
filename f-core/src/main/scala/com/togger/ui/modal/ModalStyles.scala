package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier

object ModalStyles {

  // fixed, full-screen, centers content, doubles as the dark backdrop (we don't use flowbite.js, so there's no
  // separate JS-injected backdrop element -- this div is both)
  val wrapperCls = "fixed inset-0 z-50 flex items-center justify-center w-full h-full bg-dark-backdrop/70 p-4"

  val contentBoxCls = "relative bg-neutral-primary-soft border border-default rounded-base shadow-sm p-4 md:p-6"

  val headerCls = "flex items-center justify-between border-b border-default pb-4 md:pb-5 mb-4 md:mb-5"
  val titleCls = "text-lg font-medium text-heading"
  val closeButtonCls =
    "text-body bg-transparent hover:bg-neutral-tertiary hover:text-heading rounded-base text-sm w-9 h-9 ms-auto inline-flex justify-center items-center"
  val popupCloseButtonCls =
    "absolute top-3 end-2.5 text-body bg-transparent hover:bg-neutral-tertiary hover:text-heading rounded-base text-sm w-9 h-9 inline-flex justify-center items-center"

  val footerCls = "flex items-center border-t border-default space-x-4 pt-4 md:pt-5 mt-4 md:mt-5"
  val popupFooterCls = "flex items-center justify-center space-x-4"

  val primaryButtonCls =
    "text-white bg-brand box-border border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-4 py-2.5 focus:outline-none"
  val dangerButtonCls =
    "text-white bg-danger box-border border border-transparent hover:bg-danger-strong focus:ring-4 focus:ring-danger-medium shadow-xs font-medium leading-5 rounded-base text-sm px-4 py-2.5 focus:outline-none"
  val secondaryButtonCls =
    "text-body bg-neutral-secondary-medium box-border border border-default-medium hover:bg-neutral-tertiary-medium hover:text-heading focus:ring-4 focus:ring-neutral-tertiary shadow-xs font-medium leading-5 rounded-base text-sm px-4 py-2.5 focus:outline-none"

  // Backdrop-click-to-close (only when the click lands on the wrapper itself, not bubbled from the content box --
  // mirrors flowbite.js's `target === this._targetEl` check) + Escape-to-close, following the same onMountBind
  // idiom as TgSelectDropdown.closeOnOutsideClickOrEscape.
  def closeOnBackdropOrEscape(close: Observer[Unit]): Seq[Modifier[HtmlElement]] = Seq(
    onClick.filter(ev => ev.target == ev.currentTarget).mapToUnit --> close,
    onMountBind { _ =>
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> close
    }
  )
}
