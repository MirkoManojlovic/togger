package com.togger.project

import com.togger.common.TgError

object ProjectError {
  sealed trait ProjectError extends TgError

  case object NotAssignedToProject extends ProjectError {
    override def message: String = "Account is not assigned to this project"
  }

  case object NoExportPathConfigured extends ProjectError {
    override def message: String = "No export path configured for this project. Configure at least one export data config path first."
  }

  case object DumpManifestMissing extends ProjectError {
    override def message: String = "No project.json manifest found at the given dump path, or it could not be parsed"
  }

  case object ImportFolderReadTimeout extends ProjectError {
    override def message: String =
      "Reading the import folder took too long. If it is in a cloud folder, make sure its files are downloaded and try again"
  }

  case object AccountTypeMissing extends ProjectError {
    override def message: String = "Cannot auto-create dump account: no 'developer' account type exists"
  }

  case object EmptyWorkDays extends ProjectError {
    override def message: String = "A project must have at least one work day selected"
  }
}
