package com.togger.milestone

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._
import com.togger.common.TgError

import java.time.{DayOfWeek, LocalDate}

class MilestoneDateValidation {

  def isWeekend(date: LocalDate): Boolean =
    date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY

  def validateWorkDay(fieldName: String, date: LocalDate): ValidatedNec[TgError, LocalDate] =
    if (isWeekend(date)) MilestoneDateValidation.WeekendDateNotAllowed(fieldName, date).invalidNec else date.validNec

  private def validateWorkDay(fieldName: String, date: Option[LocalDate]): ValidatedNec[TgError, Option[LocalDate]] =
    date.traverse(validateWorkDay(fieldName, _))

  def validate(
    startDate: Option[LocalDate],
    estimatedEndDate: Option[LocalDate],
    endDate: Option[LocalDate]
  ): EitherNec[TgError, Unit] =
    (
      validateWorkDay("Start date", startDate),
      validateWorkDay("Estimated end date", estimatedEndDate),
      validateWorkDay("End date", endDate)
    ).mapN((_, _, _) => ()).toEither

}

object MilestoneDateValidation {

  sealed trait MilestoneDateError extends TgError

  case class WeekendDateNotAllowed(fieldName: String, date: LocalDate) extends MilestoneDateError {
    override def message: String = s"$fieldName must be a work day, not a Saturday or Sunday (was $date)"
  }

}
