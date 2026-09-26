package com.togger.ui.components

import com.raquo.laminar.api.L.Observer
import com.togger.common.suites.AsyncJsWordSpec

class TgBadgeTest extends AsyncJsWordSpec {

  private val noAction = Option.empty[TgBadgeAction]
  private val link = Some(TgBadgeAction.Link("https://flowbite.com"))
  private val dismiss = Some(TgBadgeAction.Dismiss(Observer[Unit](_ => ())))

  private def container(
    color: TgBadgeColor = TgBadgeColor.Brand,
    size: TgBadgeSize = TgBadgeSize.Default,
    bordered: Boolean = false,
    pill: Boolean = false,
    action: Option[TgBadgeAction] = None,
    extraCls: String = ""
  ): String = TgBadgeStyles.container(color, size, bordered, pill, action, extraCls)

  private def containerClasses(
    color: TgBadgeColor = TgBadgeColor.Brand,
    size: TgBadgeSize = TgBadgeSize.Default,
    bordered: Boolean = false,
    pill: Boolean = false,
    action: Option[TgBadgeAction] = None,
    extraCls: String = ""
  ): Set[String] = container(color, size, bordered, pill, action, extraCls).split(" ").toSet

  private def hasClass(name: String, action: Option[TgBadgeAction]): Boolean =
    containerClasses(action = action).contains(name)

  /** A color fragment can itself be several classes (`"bg-indigo-100 dark:bg-indigo-900"`), so match on every token. */
  private def hasAll(classes: Set[String], fragment: String): Boolean =
    fragment.split(" ").forall(classes.contains)

  private val paletteColors =
    TgBadgeColor.values.filter(color => color.bgCls.startsWith("bg-") && color.bgCls.contains("-100"))

  testNameFromClassName should {

    "carry the background and text class of every color" in { _ =>
      val missing = TgBadgeColor.values.filterNot { color =>
        val classes = containerClasses(color = color)
        hasAll(classes, color.bgCls) && hasAll(classes, color.textCls)
      }
      missing shouldBe empty
    }

    "give every palette color a dark counterpart for background and text" in { _ =>
      val missing = paletteColors.filterNot { color =>
        color.bgCls.contains("dark:bg-") && color.textCls.contains("dark:text-")
      }
      (paletteColors.size, missing) shouldBe ((14, IndexedSeq.empty))
    }

    "leave the semantic colors free of dark: classes, since the theme swaps their tokens" in { _ =>
      val semantic = TgBadgeColor.values.diff(paletteColors)
      val leaked = semantic.filter { color =>
        Seq(color.bgCls, color.textCls, color.borderCls, color.linkHoverCls).exists(_.contains("dark:"))
      }
      (semantic.size, leaked) shouldBe ((6, IndexedSeq.empty))
    }

    "omit the border when not bordered" in { _ =>
      containerClasses(bordered = false) should not contain "border"
    }

    "add the border and the color's border class of every color when bordered" in { _ =>
      val missing = TgBadgeColor.values.filterNot { color =>
        val classes = containerClasses(color = color, bordered = true)
        classes.contains("border") && hasAll(classes, color.borderCls)
      }
      missing shouldBe empty
    }

    "be rounded by default" in { _ =>
      containerClasses() should contain("rounded")
    }

    "be rounded-full and not rounded as a pill" in { _ =>
      val classes = containerClasses(pill = true)
      (classes.contains("rounded-full"), classes.contains("rounded")) shouldBe ((true, false))
    }

    "use the default size text and padding" in { _ =>
      val classes = container()
      (classes.contains("text-xs"), classes.contains("px-1.5 py-0.5")) shouldBe ((true, true))
    }

    "use the large size text and padding" in { _ =>
      val classes = container(size = TgBadgeSize.Large)
      (
        classes.contains("text-sm"),
        classes.contains("px-2 py-1"),
        classes.contains("text-xs")
      ) shouldBe ((true, true, false))
    }

    "use asymmetric padding and a gap for a dismissable badge" in { _ =>
      val classes = container(action = dismiss)
      (
        classes.contains("ps-1.5 pe-0.5 py-0.5"),
        classes.contains("gap-1"),
        classes.contains("px-1.5")
      ) shouldBe ((true, true, false))
    }

    "use asymmetric padding for a large dismissable badge" in { _ =>
      container(size = TgBadgeSize.Large, action = dismiss) should include("ps-2 pe-1 py-1")
    }

    "add the link hover class only for a link action" in { _ =>
      val hover = "hover:bg-brand-soft"
      (hasClass(hover, link), hasClass(hover, dismiss), hasClass(hover, noAction)) shouldBe ((true, false, false))
    }

    "make a link badge focusable-visible" in { _ =>
      val ring = "focus-visible:ring-2"
      (hasClass(ring, link), hasClass(ring, noAction)) shouldBe ((true, false))
    }

    "append the caller's extra classes" in { _ =>
      containerClasses(extraCls = "me-2") should contain("me-2")
    }

    "never produce empty, doubled or edge whitespace" in { _ =>
      val combinations = for {
        color <- TgBadgeColor.values
        size <- TgBadgeSize.values
        bordered <- Seq(true, false)
        pill <- Seq(true, false)
        action <- Seq(noAction, link, dismiss)
        extraCls <- Seq("", "  ", "me-2")
      } yield container(color, size, bordered, pill, action, extraCls)

      val malformed = combinations.filter(classes => classes != classes.trim || classes.contains("  "))
      malformed shouldBe empty
    }

    "render an icon-only badge as a circle of the size's box" in { _ =>
      val classes = TgBadgeStyles.square(TgBadgeColor.Gray, TgBadgeSize.Large, bordered = true, extraCls = "")
      classes.split(" ").toSet should contain allOf ("rounded-full", "h-6", "w-6", "border-default-medium")
    }

    // plain `hidden` loses to the badge's own `inline-flex`, so the important modifier must stay
    "hide a dismissed badge with an important display utility" in { _ =>
      TgBadgeStyles.dismissedCls shouldBe "hidden!"
    }

    "carry the color's dismiss hover class on the dismiss button" in { _ =>
      TgBadgeStyles.dismissButton(TgBadgeColor.Gray).split(" ").toSet should contain("hover:bg-neutral-quaternary")
    }

    "give a leading icon a trailing margin" in { _ =>
      val icons = (TgBadgeStyles.icon(TgBadgeSize.Default), TgBadgeStyles.icon(TgBadgeSize.Large))
      icons shouldBe (("w-3 h-3 me-1", "w-3.5 h-3.5 me-1"))
    }

  }

}
