package com.togger.dto

import java.util.UUID

/** Whether pressing the export (or import) button of the project would change anything. */
case class DataSyncPendingResp(projectId: UUID, pending: Boolean)
