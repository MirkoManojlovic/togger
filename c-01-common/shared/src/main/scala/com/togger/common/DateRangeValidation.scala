package com.togger.common

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._

import java.time.{DayOfWeek, LocalDate}

class DateRangeValidation {

  def isWeekend(date: LocalDate): Boolean =
    date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY

  def validateWorkDay(fieldName: String, date: LocalDate): ValidatedNec[TgError, LocalDate] =
    if (isWeekend(date)) DateRangeValidation.WeekendDateNotAllowed(fieldName, date).invalidNec else date.validNec

  private def validateWorkDay(fieldName: String, date: Option[LocalDate]): ValidatedNec[TgError, Option[LocalDate]] =
    date.traverse(validateWorkDay(fieldName, _))

  def validateOnOrAfterStart(
    fieldName: String,
    date: LocalDate,
    startDate: LocalDate
  ): ValidatedNec[TgError, LocalDate] =
    if (date.isBefore(startDate)) DateRangeValidation.DateBeforeStartDate(fieldName, date, startDate).invalidNec
    else date.validNec

  private def validateOnOrAfterStart(
    fieldName: String,
    date: Option[LocalDate],
    startDate: Option[LocalDate]
  ): ValidatedNec[TgError, Option[LocalDate]] =
    (date, startDate) match {
      case (Some(d), Some(s)) => validateOnOrAfterStart(fieldName, d, s).map(Some(_))
      case _                  => date.validNec
    }

  def validate(
    startDate: Option[LocalDate],
    estimatedEndDate: Option[LocalDate],
    endDate: Option[LocalDate]
  ): EitherNec[TgError, Unit] =
    (
      validateWorkDay("Start date", startDate),
      validateWorkDay("Estimated end date", estimatedEndDate),
      validateWorkDay("End date", endDate),
      validateOnOrAfterStart("Estimated end date", estimatedEndDate, startDate),
      validateOnOrAfterStart("End date", endDate, startDate)
    ).mapN((_, _, _, _, _) => ()).toEither

}

object DateRangeValidation {

  sealed trait DateRangeError extends TgError

  case class WeekendDateNotAllowed(fieldName: String, date: LocalDate) extends DateRangeError {
    override def message: String = s"$fieldName must be a work day, not a Saturday or Sunday (was $date)"
  }

  case class DateBeforeStartDate(fieldName: String, date: LocalDate, startDate: LocalDate) extends DateRangeError {
    override def message: String = s"$fieldName must be on or after the start date ($startDate), but was $date"
  }

}
