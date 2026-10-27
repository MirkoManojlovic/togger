package com.togger.tgprompt

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tgprompt.TgPromptVersionRepoImpl.TG_PROMPT_VERSION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgPromptVersionRepoImpl extends TgPromptVersionRepo with TgLogging {

  private lazy val returnFields = List("version", "tg_prompt_id", "title", "prompt", "created_at")

  override def insert(promptVersion: TgPromptVersion): ConnectionIO[TgPromptVersion] = {
    import promptVersion._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_VERSION_TABLE_FRAG)(fr"""
        (tg_prompt_id, title, prompt, created_at)
        values ($tgPromptId, $title, $prompt, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptVersion](returnFields: _*)
    )(s"Inserting ${sp(promptVersion)}", saved => s"Prompt version inserted ${sp(saved)}")
  }

  override def insertWithId(promptVersion: TgPromptVersion): ConnectionIO[TgPromptVersion] = {
    import promptVersion._
    logAroundInfoQuery(
      insertStatement(TG_PROMPT_VERSION_TABLE_FRAG)(fr"""
        (version, tg_prompt_id, title, prompt, created_at)
        values ($version, $tgPromptId, $title, $prompt, $createdAt)
      """).update
        .withUniqueGeneratedKeys[TgPromptVersion](returnFields: _*)
    )(
      s"Inserting ${sp(promptVersion)} with existing version",
      saved => s"Prompt version inserted with existing version ${sp(saved)}"
    )
  }

  override def findByVersion(version: UUID): ConnectionIO[TgPromptVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++ fr"where version = $version")
        .query[TgPromptVersion]
        .unique
    )(
      s"Finding prompt version with ${sp(version)}",
      found => s"Prompt version found ${sp(found)}"
    )

  override def findLatestByTgPromptId(tgPromptId: UUID): ConnectionIO[TgPromptVersion] =
    logAroundInfoQuery(
      (selectAllStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++
        fr"where tg_prompt_id = $tgPromptId" ++ orderByDescLimitOne(fr"version"))
        .query[TgPromptVersion]
        .unique
    )(
      s"Finding latest prompt version for ${sp(tgPromptId)}",
      found => s"Latest prompt version found ${sp(found)}"
    )

  override def findPaginatedByTgPromptId(
    tgPromptId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptVersion]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TgPromptVersion](
        pageQuery = request =>
          (selectAllStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++ fr"where tg_prompt_id = $tgPromptId" ++
            PaginationSql.orderBy(fr"version desc") ++
            PaginationSql.limitOffset(request)).query[TgPromptVersion].to[List],
        countQuery = (countStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++
          fr"where tg_prompt_id = $tgPromptId").query[Long].unique
      ).run(page)
    )(
      s"Finding prompt versions of ${sp(tgPromptId)} with ${sp(page)}",
      found => s"Prompt versions found ${sp(found)} for ${sp(tgPromptId)}"
    )

  override def countByTgPromptId(tgPromptId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (countStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++ fr"where tg_prompt_id = $tgPromptId").query[Int].unique
    )(
      s"Counting prompt versions for ${sp(tgPromptId)}",
      count => s"Prompt version count ${sp(count)} for ${sp(tgPromptId)}"
    )

  override def deleteByVersion(version: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(TG_PROMPT_VERSION_TABLE_FRAG) ++ fr"where version = $version").update.run
    )(
      s"Deleting prompt version with ${sp(version)}",
      numDeleted => s"Prompt version deleted ${sp(numDeleted)}"
    )
}

object TgPromptVersionRepoImpl {
  val TG_PROMPT_VERSION_TABLE: String = "tg_prompt_version"
  val TG_PROMPT_VERSION_TABLE_FRAG: Fragment = Fragment.const(TG_PROMPT_VERSION_TABLE)
}
