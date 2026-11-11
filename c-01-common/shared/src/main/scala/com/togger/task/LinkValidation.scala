package com.togger.task

import cats.data.EitherNec
import cats.implicits._
import com.togger.common.TgError
import com.togger.dto.CreateLinkReq
import com.togger.dto.UpdateLinkReq

object LinkValidation {

  sealed trait LinkValidationError extends TgError

  case object LinkNameRequired extends LinkValidationError {
    override def message: String = "Link name can't be empty when the link value is set"
  }

  case object LinkValueRequired extends LinkValidationError {
    override def message: String = "Link value can't be empty when the link name is set"
  }

  case object LinkUrlNotAllowed extends LinkValidationError {
    override def message: String = "This kind of link address is not allowed"
  }

  private val UnsafeScheme =
    "(?i)^\\s*(javascript|data|vbscript|file|blob|view-source|ms-msdt|search-ms|ms-officecmd):".r
  private val AnyScheme = "(?i)^[a-z][a-z0-9+.-]*:".r
  private val HostWithPort = "(?i)^[a-z0-9.-]+:\\d+(/.*)?$".r

  /** The address to open: any scheme (`https://`, `togger://`, `mailto:`) as it is except the unsafe ones, a bare one
    * like `example.com/x` or `localhost:3000` as https, nothing for an empty address or an unsafe scheme.
    */
  def safeHref(url: String): Option[String] = {
    val trimmed = url.trim
    if (trimmed.isEmpty || UnsafeScheme.findPrefixOf(trimmed).isDefined) None
    else if (HostWithPort.matches(trimmed)) Some(s"https://$trimmed")
    else if (AnyScheme.findPrefixOf(trimmed).isDefined) Some(trimmed)
    else Some(s"https://$trimmed")
  }

  def isBlank(name: String, url: String): Boolean = name.isBlank && url.isBlank

  /** A link with both name and value blank is not a link, so it is left out instead of being rejected. */
  def withoutBlankLinks(links: Seq[CreateLinkReq]): Seq[CreateLinkReq] =
    links.filterNot(link => isBlank(link.name, link.url))

  def validate(name: String, url: String): EitherNec[TgError, Unit] =
    if (name.isBlank && url.nonBlank) (LinkNameRequired: TgError).leftNec
    else if (url.isBlank && name.nonBlank) (LinkValueRequired: TgError).leftNec
    else if (url.nonBlank && safeHref(url).isEmpty) (LinkUrlNotAllowed: TgError).leftNec
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
