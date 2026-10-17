package com.togger.ui.logentry

import com.raquo.laminar.api.L._
import com.togger.dto.LogEntryDto
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.TgCard
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.task.TgDateFormater

import java.time.LocalDate

/** A log entry as the card in the log entries column of the task page, without the button to edit it. */
object LogEntryReadOnlyCard {

  def apply(
    entry: LogEntryDto,
    day: LocalDate,
    taskName: String,
    tgDateFormater: TgDateFormater,
    tgMarkdown: TgMarkdown
  ): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex items-center gap-2"),
            span(cls("min-w-0 truncate"), title := taskName, taskName),
            TgBadge(
              text = tgDateFormater.asAmericanDateWithDayAbrv(day),
              bordered = true,
              color = TgBadgeColor.Gray
            ).element,
            TgBadge(text = entry.status.statusName, color = entry.status.badgeColor, bordered = true).element
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(div(tgMarkdown.toLaminarDiv(entry.textExternal)))
      )
      .element
}
