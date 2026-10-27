package com.togger.task

import cats.data.EitherNec
import cats.implicits._
import com.togger.common.TgError
import com.togger.dto.{CreateLinkReq, UpdateLinkReq}

object LinkValidation {

  sealed trait LinkValidationError extends TgError

  case object LinkNameRequired extends LinkValidationError {
    override def message: String = "Link name can't be empty when the link value is set"
  }

  case object LinkValueRequired extends LinkValidationError {
    override def message: String = "Link value can't be empty when the link name is set"
  }

  def isBlank(name: String, url: String): Boolean = name.isBlank && url.isBlank

  /** A link with both name and value blank is not a link, so it is left out instead of being rejected. */
  def withoutBlankLinks(links: Seq[CreateLinkReq]): Seq[CreateLinkReq] =
    links.filterNot(link => isBlank(link.name, link.url))

  def validate(name: String, url: String): EitherNec[TgError, Unit] =
    if (name.isBlank && url.nonBlank) (LinkNameRequired: TgError).leftNec
    else if (url.isBlank && name.nonBlank) (LinkValueRequired: TgError).leftNec
    else ().rightNec

  /** Links to create may be blank on both sides (they are dropped), links to update may not. */
  def validateAll(toCreate: Seq[CreateLinkReq], toUpdate: Seq[UpdateLinkReq]): EitherNec[TgError, Unit] =
    (withoutBlankLinks(toCreate).map(link => validate(link.name, link.url)) ++
      toUpdate.map(link =>
        if (isBlank(link.name, link.url)) (LinkNameRequired: TgError).leftNec[Unit]
        else validate(link.name, link.url)
      )).toList.traverse_(_.toValidated).toEither

  private implicit class NonBlankOps(private val value: String) {
    def nonBlank: Boolean = !value.isBlank
  }
}
