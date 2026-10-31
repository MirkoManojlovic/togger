package com.togger.ui.project

import cats.effect.IO
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{AccountSearchTerm, PageRequest, PaginatedResponse}
import com.togger.dto.SearchAccountDto
import com.togger.preview.{AccountsPreviewData, PreviewContext, PreviewResponses}

class FindAccountServicePreview(ctx: PreviewContext, accounts: AccountsPreviewData) extends FindAccountService {

  override def findByFirstNameLastNameOrEmail(
    input: String,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    PreviewResponses.authenticated(ctx.loginRepo) { _ =>
      AccountSearchTerm.validate(input) match {
        case Left(error) => PreviewResponses.badRequest[PaginatedResponse[SearchAccountDto]](error.message)
        case Right(term) => IO.defer(PreviewResponses.paged(matching(term), page))
      }
    }

  private def matching(term: String): List[SearchAccountDto] = {
    val lowered = term.toLowerCase
    accounts.accounts
      .filter(a => List(a.firstName, a.lastName, a.email).exists(_.toLowerCase.contains(lowered)))
      .sorted(AccountsPreviewData.byName)
      .map(_.toSearchDto)
  }
}
