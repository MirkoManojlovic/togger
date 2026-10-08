package com.togger.common

/** The account search lists accounts across all tenants, so a term too short to narrow anything down is refused. */
object AccountSearchTerm {

  val MinLength: Int = 2

  case class SearchTermTooShort(minLength: Int) extends TgError {
    override def message: String = s"Search term must have at least $minLength characters"
  }

  def validate(term: String): Either[TgError, String] = {
    val trimmed = term.trim
    Either.cond(trimmed.length >= MinLength, trimmed, SearchTermTooShort(MinLength))
  }
}
