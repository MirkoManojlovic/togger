package com.togger.tgaiaction

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.db.Util._
import com.togger.logging.TgLogging
import com.togger.tgaiaction.TgAiActionEndpointRepoImpl.TG_AI_ACTION_ENDPOINT_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments

import java.util.UUID

class TgAiActionEndpointRepoImpl extends TgAiActionEndpointRepo with TgLogging {

  override def replaceBindings(actionId: UUID, endpointIds: List[Int]): ConnectionIO[Unit] =
    logAroundInfoQuery(
      for {
        _ <- removeUnlisted(actionId, endpointIds)
        _ <- endpointIds.traverse_(endpointId =>
          (insertStatement(TG_AI_ACTION_ENDPOINT_TABLE_FRAG)(
            fr"(tg_ai_action_id, ai_endpoint_id) values ($actionId, $endpointId) on conflict do nothing"
          )).update.run
        )
      } yield ()
    )(s"Binding AI action ${sp(actionId)} to ${sp(endpointIds)}", _ => s"AI action ${sp(actionId)} bound")

  // Deleting a binding also deletes the defaults that use it (cascade).
  private def removeUnlisted(actionId: UUID, endpointIds: List[Int]): ConnectionIO[Int] = {
    val unlisted = NonEmptyList.fromList(endpointIds).fold(Fragment.empty)(ids =>
      fr"and" ++ fragments.notIn(fr"ai_endpoint_id", ids)
    )
    (fr"delete from" ++ TG_AI_ACTION_ENDPOINT_TABLE_FRAG ++ fr"where tg_ai_action_id = $actionId" ++ unlisted).update.run
  }

  override def findByActionIds(actionIds: List[UUID]): ConnectionIO[List[TgAiActionEndpoint]] =
    NonEmptyList.fromList(actionIds) match {
      case None => List.empty[TgAiActionEndpoint].pure[ConnectionIO]
      case Some(ids) =>
        logAroundInfoQuery(
          (selectAllStatement(TG_AI_ACTION_ENDPOINT_TABLE_FRAG) ++ fr"where" ++
            fragments.in(fr"tg_ai_action_id", ids) ++ fr"order by ai_endpoint_id").query[TgAiActionEndpoint].to[List]
        )(s"Finding bindings of ${sp(actionIds)}", found => s"Found ${found.size} bindings")
    }

  override def isBound(actionId: UUID, endpointId: Int): ConnectionIO[Boolean] =
    (fr"select exists (select 1 from" ++ TG_AI_ACTION_ENDPOINT_TABLE_FRAG ++
      fr"where tg_ai_action_id = $actionId and ai_endpoint_id = $endpointId)").query[Boolean].unique
}

object TgAiActionEndpointRepoImpl {
  val TG_AI_ACTION_ENDPOINT_TABLE: String = "tg_ai_action_endpoint"
  val TG_AI_ACTION_ENDPOINT_TABLE_FRAG: Fragment = Fragment.const(TG_AI_ACTION_ENDPOINT_TABLE)
}
