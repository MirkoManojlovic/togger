package com.togger.tgaiaction

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.tgaiaction.TgAiActionRepoImpl.TG_AI_ACTION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class TgAiActionRepoImpl extends TgAiActionRepo with TgLogging {

  private lazy val returnFields =
    List("id", "created_by", "name", "prompt", "tg_prompt_template_id", "model", "created_at", "updated_at")

  override def insert(action: TgAiAction): ConnectionIO[TgAiAction] = {
    import action._
    logAroundInfoQuery(
      insertStatement(TG_AI_ACTION_TABLE_FRAG)(fr"""
        (created_by, name, prompt, tg_prompt_template_id, model, created_at, updated_at)
        values ($createdBy, $name, $prompt, $tgPromptTemplateId, $model, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TgAiAction](returnFields: _*)
    )(s"Inserting ${sp(action)}", saved => s"AI action inserted ${sp(saved)}")
  }

  override def insertWithId(action: TgAiAction): ConnectionIO[TgAiAction] = {
    import action._
    logAroundInfoQuery(
      insertStatement(TG_AI_ACTION_TABLE_FRAG)(fr"""
        (id, created_by, name, prompt, tg_prompt_template_id, model, created_at, updated_at)
        values ($id, $createdBy, $name, $prompt, $tgPromptTemplateId, $model, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[TgAiAction](returnFields: _*)
    )(s"Inserting ${sp(action)} with existing id", saved => s"AI action inserted with existing id ${sp(saved)}")
  }

  override def findById(id: UUID): ConnectionIO[TgAiAction] =
    logAroundInfoQuery(findByIdStatementUUID(TG_AI_ACTION_TABLE_FRAG)(id).query[TgAiAction].unique)(
      s"Finding AI action with ${sp(id)}",
      found => s"AI action found ${sp(found)}"
    )

  override def update(action: TgAiAction): ConnectionIO[TgAiAction] = {
    import action._
    logAroundInfoQuery(
      updateStatementUUID(TG_AI_ACTION_TABLE_FRAG)(id)(fr"""
        name = $name, prompt = $prompt, tg_prompt_template_id = $tgPromptTemplateId, model = $model,
        updated_at = $updatedAt
      """).update.withUniqueGeneratedKeys[TgAiAction](returnFields: _*)
    )(s"Updating ${sp(action)}", saved => s"AI action updated ${sp(saved)}")
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(TG_AI_ACTION_TABLE_FRAG)(id).update.run)(
      s"Deleting AI action with ${sp(id)}",
      _ => s"AI action with ${sp(id)} deleted"
    )

  override def existsByName(createdBy: UUID, name: String, exceptId: Option[UUID]): ConnectionIO[Boolean] = {
    val exceptFilter = exceptId.fold(Fragment.empty)(id => fr"and id <> $id")
    logAroundInfoQuery(
      (fr"select exists (select 1 from" ++ TG_AI_ACTION_TABLE_FRAG ++
        fr"where created_by = $createdBy and name = $name" ++ exceptFilter ++ fr")").query[Boolean].unique
    )(s"Checking AI action name ${sp(name)} of ${sp(createdBy)}", exists => s"AI action name exists: $exists")
  }

  override def findPaginatedByCreatedBy(
    createdBy: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgAiAction]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[TgAiAction](
        pageQuery = request =>
          (selectAllStatement(TG_AI_ACTION_TABLE_FRAG) ++ fr"where created_by = $createdBy" ++
            PaginationSql.orderBy(fr"id desc") ++
            PaginationSql.limitOffset(request)).query[TgAiAction].to[List],
        countQuery = (countStatement(TG_AI_ACTION_TABLE_FRAG) ++ fr"where created_by = $createdBy")
          .query[Long]
          .unique
      ).run(page)
    )(
      s"Finding AI actions of ${sp(createdBy)} with ${sp(page)}",
      found => s"AI actions found ${sp(found)} for ${sp(createdBy)}"
    )
}

object TgAiActionRepoImpl {
  val TG_AI_ACTION_TABLE: String = "tg_ai_action"
  val TG_AI_ACTION_TABLE_FRAG: Fragment = Fragment.const(TG_AI_ACTION_TABLE)
}
