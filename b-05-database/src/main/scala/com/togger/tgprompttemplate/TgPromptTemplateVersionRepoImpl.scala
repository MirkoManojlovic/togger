package com.togger.tgprompttemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepoImpl.TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgPromptTemplateVersionRepoImpl extends TgPromptTemplateVersionRepo with TgLogging {

  private lazy val returnFields = List("version", "tg_prompt_template_id", "title", "prompt", "created_at")

  override def insert(promptVersion: TgPromptTemplateVersion): ConnectionIO[TgPromptTemplateVersion] = {
    import promptVersion._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (tg_prompt_template_id, title, prompt, created_at)
        values ($tgPromptTemplateId, $title, $prompt, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptTemplateVersion](returnFields: _*)
    )(s"Inserting ${sp(promptVersion)}", saved => s"Prompt version inserted ${sp(saved)}")
  }

  override def insertWithId(promptVersion: TgPromptTemplateVersion): ConnectionIO[TgPromptTemplateVersion] = {
    import promptVersion._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG)(fr"""
        (version, tg_prompt_template_id, title, prompt, created_at)
        values ($version, $tgPromptTemplateId, $title, $prompt, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptTemplateVersion](returnFields: _*)
    )(
      s"Inserting ${sp(promptVersion)} with existing version",
      saved => s"Prompt version inserted with existing version ${sp(saved)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[TgPromptTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[TgPromptTemplateVersion]
        .unique
    )(
      s"Finding prompt version with ${sp(version)}",
      found => s"Prompt version found ${sp(found)}"
    )

  override def findLatestByTgPromptTemplateId(tgPromptTemplateId: UUID): ConnectionIO[TgPromptTemplateVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++
        fr"where tg_prompt_template_id = $tgPromptTemplateId" ++ orderByDescLimitOne(fr"version"))
        .query[TgPromptTemplateVersion]
        .unique
    )(
      s"Finding latest prompt version for ${sp(tgPromptTemplateId)}",
      found => s"Latest prompt version found ${sp(found)}"
    )

  override def findPaginatedByTgPromptTemplateId(
    tgPromptTemplateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptTemplateVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TgPromptTemplateVersion](
        pageQuery = request =>
          (selectAllStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where tg_prompt_template_id = $tgPromptTemplateId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[TgPromptTemplateVersion].to[List],
        countQuery = (countStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++
          fr"where tg_prompt_template_id = $tgPromptTemplateId").query[Long].unique
      ).run(page)
    )(
      s"Finding prompt versions of ${sp(tgPromptTemplateId)} with ${sp(page)}",
      found => s"Prompt versions found ${sp(found)} for ${sp(tgPromptTemplateId)}"
    )

  override def countByTgPromptTemplateId(tgPromptTemplateId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where tg_prompt_template_id = $tgPromptTemplateId").query[Int].unique
    )(
      s"Counting prompt versions for ${sp(tgPromptTemplateId)}",
      count => s"Prompt version count ${sp(count)} for ${sp(tgPromptTemplateId)}"
    )

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting prompt version with ${sp(version)}",
      numDeleted => s"Prompt version deleted ${sp(numDeleted)}"
    )
}

object TgPromptTemplateVersionRepoImpl {
  val TG_PROMPT_TEMPLATE_VERSION_TABLE: String = "tg_prompt_template_version"
  val TG_PROMPT_TEMPLATE_VERSION_TABLE_FRAG: Fragment = Fragment.const(TG_PROMPT_TEMPLATE_VERSION_TABLE)
}
