package com.togger.project

import com.togger.common.TgError

object ProjectError {
  sealed trait ProjectError extends TgError

  case object NotAssignedToProject extends ProjectError {
    override def message: String = "Account is not assigned to this project"
  }

  case object NoDumpPathConfigured extends ProjectError {
    override def message: String = "No dump path configured for this project. Configure at least one task dump config path first."
  }
}
