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

  def uploadIcon(className: String) = {
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
          "M12 5v9m0-9 4 4m-4-4-4 4M7 14H5a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4a1 1 0 0 0-1-1h-2m-8 5h.01"
        )
      )
    )
  }

  def listIcon(className: String) = {
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
        svg.d("M9 8h10M9 12h10M9 16h10M4.99 8H5m-.02 4h.01m0 4H5")
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

  def statusIcon(className: String) = {
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
          "M11 9h6m-6 3h6m-6 3h6M6.996 9h.01m-.01 3h.01m-.01 3h.01M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z"
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

  def filterIcon(className: String) =
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      svg.path(
        svg.stroke("currentColor"),
        svg.strokeLineCap("round"),
        svg.strokeWidth("2"),
        svg.d(
          "M18.796 4H5.204a1 1 0 0 0-.753 1.659l5.302 6.058a1 1 0 0 1 .247.659v4.874a.5.5 0 0 0 .2.4l3 2.25a.5.5 0 0 0 .8-.4v-7.124a1 1 0 0 1 .247-.659l5.302-6.059c.566-.646.106-1.658-.753-1.658Z"
        )
      )
    )

  def chevronDownIcon(className: String) =
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
        svg.d("m19 9-7 7-7-7")
      )
    )

  def chevronLeftIcon(className: String) =
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
        svg.d("m15 19-7-7 7-7")
      )
    )

  def chevronRightIcon(className: String) =
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
        svg.d("m9 5 7 7-7 7")
      )
    )

  def homeIcon(className: String) =
    strokeIcon(
      className,
      "m4 12 8-8 8 8M6 10.5V19a1 1 0 0 0 1 1h3v-3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v3h3a1 1 0 0 0 1-1v-8.5"
    )

  def closeIcon(className: String) =
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
        svg.d("M6 18 17.94 6M18 18 6.06 6")
      )
    )

  private def strokeIcon(className: String, path: String) =
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
        svg.d(path)
      )
    )

  def boldIcon(className: String) =
    strokeIcon(className, "M8 5h4.5a3.5 3.5 0 1 1 0 7H8V5Zm0 7h5.5a3.5 3.5 0 1 1 0 7H8v-7Z")

  def italicIcon(className: String) =
    strokeIcon(className, "M10 5h6M8 19h6m.5-14-5 14")

  def codeIcon(className: String) =
    strokeIcon(className, "m9 8-4 4 4 4m6-8 4 4-4 4")

  def headingIcon(className: String) =
    strokeIcon(className, "M6 5v14M18 5v14M6 12h12")

  def linkIcon(className: String) =
    strokeIcon(
      className,
      "M13.2 10.8a4 4 0 0 0-5.7 0l-2.8 2.8a4 4 0 0 0 5.7 5.7l1.4-1.4m-2.8-6.7a4 4 0 0 1 5.7 0l2.8-2.8a4 4 0 0 0-5.7-5.7l-1.4 1.4"
    )

  def orderedListIcon(className: String) =
    strokeIcon(className, "M10 7h9m-9 5h9m-9 5h9M5 6v3m0 0H4m1 0h1m-2 3h2l-2 3h2")

  def quoteIcon(className: String) =
    strokeIcon(
      className,
      "M9 6H5a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h4v2a2 2 0 0 1-2 2H6m13-10h-4a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h4v2a2 2 0 0 1-2 2h-1"
    )

  def taskListIcon(className: String) =
    strokeIcon(className, "M11 7h9m-9 10h9M3 7.5 4.5 9 7.5 6M3 16.5 4.5 18l3-3")

  def copyIcon(className: String) =
    strokeIcon(
      className,
      "M9 8v3a1 1 0 0 1-1 1H5m11 4h2a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1h-7a1 1 0 0 0-1 1v1m4 3v10a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7.13a1 1 0 0 1 .24-.65L7.7 8.35A1 1 0 0 1 8.46 8H13a1 1 0 0 1 1 1Z"
    )

  def sunIcon(className: String) =
    strokeIcon(
      className,
      "M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z"
    )

  def moonIcon(className: String) =
    strokeIcon(
      className,
      "M21.752 15.002A9.72 9.72 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z"
    )

  def desktopIcon(className: String) =
    strokeIcon(
      className,
      "M9 17.25v1.007a3 3 0 0 1-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0 1 15 18.257V17.25m6-12V15a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 15V5.25m18 0A2.25 2.25 0 0 0 18.75 3H5.25A2.25 2.25 0 0 0 3 5.25m18 0V12a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 12V5.25"
    )

}
