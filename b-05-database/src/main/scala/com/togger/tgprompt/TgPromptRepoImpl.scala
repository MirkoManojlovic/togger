package com.togger.tgprompt

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tgprompt.TgPromptRepoImpl.TG_PROMPT_TABLE_FRAG
import com.togger.tgprompt.TgPromptVersionRepoImpl.TG_PROMPT_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgPromptRepoImpl extends TgPromptRepo with TgLogging {

  private lazy val returnFields = List("id", "created_by", "created_at")

  override def insert(tgPrompt: TgPrompt): ConnectionIO[TgPrompt] = {
    import tgPrompt._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TABLE_FRAG)(fr"""
        (created_by, created_at)
        values ($createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPrompt](returnFields: _*)
    )(s"Inserting ${sp(tgPrompt)}", saved => s"Prompt inserted ${sp(saved)}")
  }

  override def insertWithId(tgPrompt: TgPrompt): ConnectionIO[TgPrompt] = {
    import tgPrompt._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TABLE_FRAG)(fr"""
        (id, created_by, created_at)
        values ($id, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPrompt](returnFields: _*)
    )(s"Inserting ${sp(tgPrompt)} with existing id", saved => s"Prompt inserted with existing id ${sp(saved)}")
  }

  override def findById(id: UUID): ConnectionIO[TgPrompt] =
    logAroundInfoQuery(findByIdStatementUUID(TG_PROMPT_TABLE_FRAG)(id).query[TgPrompt].unique)(
      s"Finding prompt with ${sp(id)}",
      found => s"Prompt found ${sp(found)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TG_PROMPT_TABLE_FRAG)(id).update.run)(
      s"Deleting prompt with ${sp(id)}",
      _ => s"Prompt with ${sp(id)} deleted"
    )

  override def findFullById(id: UUID): ConnectionIO[TgPromptFull] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where p.id = $id").query[(TgPrompt, TgPromptVersion)].unique.map(toFull)
    )(
      s"Finding full prompt with ${sp(id)}",
      found => s"Full prompt found ${sp(found)}"
    )

  override def findPaginatedFullByCreatedBy(
    createdBy: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TgPromptFull](
        pageQuery = request =>
          (fullSelect ++ fr"where p.created_by = $createdBy" ++
            PaginationSql.orderBy(fr"p.id desc") ++
            PaginationSql.limitOffset(request))
            .query[(TgPrompt, TgPromptVersion)]
            .map(toFull)
            .to[List],
        countQuery = (countStatement(TG_PROMPT_TABLE_FRAG) ++ fr"where created_by = $createdBy")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding prompts of ${sp(createdBy)} with ${sp(page)}",
      found => s"Prompts found ${sp(found)} for ${sp(createdBy)}"
    )

  private def toFull(row: (TgPrompt, TgPromptVersion)): TgPromptFull =
    TgPromptFull(tgPrompt = row._1, latestVersion = row._2)

  // The prompt and its latest version (via LATERAL).
  private def fullSelect: Fragment =
    fr"select p.id, p.created_by, p.created_at," ++
      fr"pv.version, pv.tg_prompt_id, pv.title, pv.prompt, pv.created_at from" ++
      TG_PROMPT_TABLE_FRAG ++ fr"p join lateral (select * from" ++ TG_PROMPT_VERSION_TABLE_FRAG ++
      fr"where tg_prompt_id = p.id order by version desc limit 1) pv on true"
}

object TgPromptRepoImpl {
  val TG_PROMPT_TABLE: String = "tg_prompt"
  val TG_PROMPT_TABLE_FRAG: Fragment = Fragment.const(TG_PROMPT_TABLE)
}
