package com.togger.tgprompttemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tgprompttemplate.TgPromptTemplateRepoImpl.TG_PROMPT_TEMPLATE_TABLE_FRAG
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepoImpl.TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgPromptTemplateRepoImpl extends TgPromptTemplateRepo with TgLogging {

  private lazy val returnFields = List("id", "created_by", "created_at")

  override def insert(tgPromptTemplate: TgPromptTemplate): ConnectionIO[TgPromptTemplate] = {
    import tgPromptTemplate._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TEMPLATE_TABLE_FRAG)(fr"""
        (created_by, created_at)
        values ($createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptTemplate](returnFields: _*)
    )(s"Inserting ${sp(tgPromptTemplate)}", saved => s"Prompt inserted ${sp(saved)}")
  }

  override def insertWithId(tgPromptTemplate: TgPromptTemplate): ConnectionIO[TgPromptTemplate] = {
    import tgPromptTemplate._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TEMPLATE_TABLE_FRAG)(fr"""
        (id, created_by, created_at)
        values ($id, $createdBy, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptTemplate](returnFields: _*)
    )(s"Inserting ${sp(tgPromptTemplate)} with existing id", saved => s"Prompt inserted with existing id ${sp(saved)}")
  }

  override def findById(id: UUID): ConnectionIO[TgPromptTemplate] =
    logAroundInfoQuery(findByIdStatementUUID(TG_PROMPT_TEMPLATE_TABLE_FRAG)(id).query[TgPromptTemplate].unique)(
      s"Finding prompt with ${sp(id)}",
      found => s"Prompt found ${sp(found)}"
    )

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TG_PROMPT_TEMPLATE_TABLE_FRAG)(id).update.run)(
      s"Deleting prompt with ${sp(id)}",
      _ => s"Prompt with ${sp(id)} deleted"
    )

  override def findFullById(id: UUID): ConnectionIO[TgPromptTemplateFull] =
    logAroundInfoQuery(
      (fullSelect ++ fr"where p.id = $id").query[(TgPromptTemplate, TgPromptTemplateVersion)].unique.map(toFull)
    )(
      s"Finding full prompt with ${sp(id)}",
      found => s"Full prompt found ${sp(found)}"
    )

  override def findPaginatedFullByCreatedBy(
    createdBy: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptTemplateFull]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TgPromptTemplateFull](
        pageQuery = request =>
          (fullSelect ++ fr"where p.created_by = $createdBy" ++
            PaginationSql.orderBy(fr"p.id desc") ++
            PaginationSql.limitOffset(request))
            .query[(TgPromptTemplate, TgPromptTemplateVersion)]
            .map(toFull)
            .to[List],
        countQuery = (countStatement(TG_PROMPT_TEMPLATE_TABLE_FRAG) ++ fr"where created_by = $createdBy")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding prompts of ${sp(createdBy)} with ${sp(page)}",
      found => s"Prompts found ${sp(found)} for ${sp(createdBy)}"
    )

  private def toFull(row: (TgPromptTemplate, TgPromptTemplateVersion)): TgPromptTemplateFull =
    TgPromptTemplateFull(tgPromptTemplate = row._1, latestVersion = row._2)

  // The prompt and its latest version (via LATERAL).
  private def fullSelect: Fragment =
    fr"select p.id, p.created_by, p.created_at," ++
      fr"pv.version, pv.tg_prompt_template_id, pv.title, pv.prompt, pv.created_at from" ++
      TG_PROMPT_TEMPLATE_TABLE_FRAG ++ fr"p join lateral (select * from" ++ TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG ++
      fr"where tg_prompt_template_id = p.id order by version desc limit 1) pv on true"
}

object TgPromptTemplateRepoImpl {
  val TG_PROMPT_TEMPLATE_TABLE: String = "tg_prompt_template"
  val TG_PROMPT_TEMPLATE_TABLE_FRAG: Fragment = Fragment.const(TG_PROMPT_TEMPLATE_TABLE)
}
