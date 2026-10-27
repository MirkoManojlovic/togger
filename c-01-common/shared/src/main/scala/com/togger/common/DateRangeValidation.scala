package com.togger.common

import cats.data.{EitherNec, NonEmptyChain, ValidatedNec}
import cats.implicits._

import java.time.{DayOfWeek, LocalDate}

class DateRangeValidation {

  def isWeekend(date: LocalDate, workDays: Set[DayOfWeek]): Boolean =
    !workDays.contains(date.getDayOfWeek)

  def validateWorkDay(fieldName: String, date: LocalDate, workDays: Set[DayOfWeek]): ValidatedNec[TgError, LocalDate] =
    if (isWeekend(date, workDays)) DateRangeValidation.WeekendDateNotAllowed(fieldName, date).invalidNec
    else date.validNec

  private def validateWorkDay(
    fieldName: String,
    date: Option[LocalDate],
    workDays: Set[DayOfWeek]
  ): ValidatedNec[TgError, Option[LocalDate]] =
    date.traverse(validateWorkDay(fieldName, _, workDays))

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

  def validateEndDateWhenDone(isDone: Boolean, endDate: Option[LocalDate]): EitherNec[TgError, Unit] =
    Either.cond(!isDone || endDate.isDefined, (), NonEmptyChain.one(DateRangeValidation.EndDateRequiredWhenDone))

  def validate(
    startDate: Option[LocalDate],
    dueDate: Option[LocalDate],
    endDate: Option[LocalDate],
    workDays: Set[DayOfWeek]
  ): EitherNec[TgError, Unit] =
    (
      validateWorkDay("Start date", startDate, workDays),
      validateWorkDay("Due date", dueDate, workDays),
      validateWorkDay("End date", endDate, workDays),
      validateOnOrAfterStart("Due date", dueDate, startDate),
      validateOnOrAfterStart("End date", endDate, startDate)
    ).mapN((_, _, _, _, _) => ()).toEither

}

object DateRangeValidation {

  sealed trait DateRangeError extends TgError

  case class WeekendDateNotAllowed(fieldName: String, date: LocalDate) extends DateRangeError {
    override def message: String =
      s"$fieldName must be one of this project's configured work days (was $date, a ${date.getDayOfWeek})"
  }

  case object EndDateRequiredWhenDone extends DateRangeError {
    override def message: String = "End date is required when the status is Done"
  }

  case class DateBeforeStartDate(fieldName: String, date: LocalDate, startDate: LocalDate) extends DateRangeError {
    override def message: String = s"$fieldName must be on or after the start date ($startDate), but was $date"
  }

}
