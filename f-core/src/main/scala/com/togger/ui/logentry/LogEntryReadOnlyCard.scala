package com.togger.ui.logentry

import com.raquo.laminar.api.L._
import com.togger.dto.LogEntryDto
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.TgCard
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.TgBadge
import com.togger.ui.components.TgBadgeColor
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
            cls("flex flex-col gap-1"),
            span(cls("min-w-0 truncate"), title := taskName, taskName),
            LogEntryBadgeRow(
              TgBadge(
                text = tgDateFormater.asAmericanDateWithDayAbrv(day),
                bordered = true,
                color = TgBadgeColor.Gray
              ).element,
              TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element,
              LogEntryMemberBadge(entry.createdByName)
            )
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(div(tgMarkdown.toLaminarDiv(entry.text)))
      )
      .element
}
