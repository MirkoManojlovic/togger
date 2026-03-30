package com.togger.style

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.KeySetter.StyleSetter

object TgCommonStyleJs {

  lazy val primaryColor = "#5755d9"
  lazy val chipBackgroundColor = "#eef0f3"

  lazy val heightFitContent: StyleSetter = height("fit-content")
  lazy val widthFitContent: StyleSetter = width("fit-content")
  lazy val heightAndWidthFitContent: Seq[StyleSetter] = Seq(heightFitContent, widthFitContent)

  lazy val borderNone: StyleSetter = border("none")

  lazy val timelineBroder: Seq[StyleSetter] = List(
    borderNone,
    borderRight("1px solid #f1f1f1")
  )

  lazy val centeredFlex: Seq[StyleSetter] = List(
    display.flex,
    alignItems.center,
    justifyContent.center
  )

  lazy val justifyCenterFlex: Seq[StyleSetter] = List(
    display.flex,
    justifyContent.center
  )

}
