package com.togger.ui.organization
import com.raquo.laminar.api.L._

object TgIcons {

  def editIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "m14.304 4.844 2.852 2.852M7 7H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-4.5m2.409-9.91a2.017 2.017 0 0 1 0 2.853l-6.844 6.844L8 14l.713-3.565 6.844-6.844a2.015 2.015 0 0 1 2.852 0Z"
        )
      )
    )
  }

  def briefcaseIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M8 7H5a2 2 0 0 0-2 2v4m5-6h8M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m0 0h3a2 2 0 0 1 2 2v4m0 0v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6m18 0s-4 2-9 2-9-2-9-2m9-2h.01"
        )
      )
    )
  }

  def userGroupIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M4.5 17H4a1 1 0 0 1-1-1 3 3 0 0 1 3-3h1m0-3.05A2.5 2.5 0 1 1 9 5.5M19.5 17h.5a1 1 0 0 0 1-1 3 3 0 0 0-3-3h-1m0-3.05a2.5 2.5 0 1 0-2-4.45m.5 13.5h-7a1 1 0 0 1-1-1 3 3 0 0 1 3-3h3a3 3 0 0 1 3 3 1 1 0 0 1-1 1Zm-1-9.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0Z"
        )
      )
    )
  }

  def hammerIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "m20.9532 11.7634-2.0523-2.05225-2.0523 2.05225 2.0523 2.0523 2.0523-2.0523Zm-1.3681-2.73651-4.1046-4.10457L12.06 8.3428l4.1046 4.1046 3.4205-3.42051Zm-4.1047 2.73651-2.7363-2.73638-8.20919 8.20918 2.73639 2.7364 8.2091-8.2092Z"
        )
      ),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "m12.9306 3.74083 1.8658 1.86571-2.0523 2.05229-1.5548-1.55476c-.995-.99505-3.23389-.49753-3.91799.18657l2.73639-2.73639c.6841-.68409 1.9901-.74628 2.9229.18658Z"
        )
      )
    )
  }

  def minusIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M5 12h14")
      )
    )
  }

  def minusCircleIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M7.757 12h8.486M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z")
      )
    )
  }

  def plusIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M5 12h14m-7 7V5")
      )
    )
  }

  def searchIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("m21 21-3.5-3.5M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z")
      )
    )
  }

  def checkIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M8.5 11.5 11 14l4-4m6 2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z")
      )
    )
  }

  def downloadIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M12 13V4M7 14H5a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4a1 1 0 0 0-1-1h-2m-1-5-4 5-4-5m9 8h.01"
        )
      )
    )
  }

  def trashIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M5 7h14M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3"
        )
      )
    )
  }

  def userIcon(className: String) = {
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M7 17v1a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-1a3 3 0 0 0-3-3h-4a3 3 0 0 0-3 3Zm8-9a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"
        )
      )
    )
  }

  def toastSuccessIcon(className: String) =
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M5 11.917 9.724 16.5 19 7.5")
      )
    )

  def toastWarningIcon(className: String) =
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M12 13V8m0 8h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z")
      )
    )

  def toastErrorIcon(className: String) =
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeLineJoin("round"),
        svg.strokeWidth("2"),
        svg.d("M6 18 17.94 6M18 18 6.06 6")
      )
    )

}
