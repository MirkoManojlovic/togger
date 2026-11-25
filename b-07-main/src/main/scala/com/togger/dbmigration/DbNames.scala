package com.togger.dbmigration

object DbNames {
  val LegacyProdName = "togger-local"
  private val Prefix = s"$LegacyProdName-"
  private val Versioned = s"""^${java.util.regex.Pattern.quote(Prefix)}(\\d+\\.\\d+\\.\\d+)$$""".r

  /** `togger-local-0.5.0`, or the unversioned name when the version is not a release version. */
  def prodName(appVersion: String): String =
    SemVer.parse(appVersion).fold(LegacyProdName)(prodName)

  def prodName(version: SemVer): String = s"$Prefix${version.render}"

  def versionOf(dbName: String): Option[SemVer] =
    dbName match {
      case Versioned(version) => SemVer.parse(version)
      case _                  => None
    }

  /** The newest versioned database below `current`, else the legacy one, else none. */
  def previousOf(current: SemVer, existing: List[String]): Option[String] =
    existing
      .flatMap(name => versionOf(name).map(name -> _))
      .filter { case (_, version) => version < current }
      .maxByOption { case (_, version) => version }
      .map { case (name, _) => name }
      .orElse(existing.find(_ == LegacyProdName))
}
