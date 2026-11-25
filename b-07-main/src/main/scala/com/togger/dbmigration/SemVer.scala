package com.togger.dbmigration

final case class SemVer(major: Int, minor: Int, patch: Int) extends Ordered[SemVer] {
  override def compare(that: SemVer): Int =
    Ordering[(Int, Int, Int)].compare((major, minor, patch), (that.major, that.minor, that.patch))

  def render: String = s"$major.$minor.$patch"
}

object SemVer {
  private val Release = """^v?(\d+)\.(\d+)\.(\d+).*$""".r

  /** Reads the release part of a version: `0.5.0+3-abcd` is `0.5.0`, `ci` is none. */
  def parse(version: String): Option[SemVer] =
    version.trim match {
      case Release(major, minor, patch) => Some(SemVer(major.toInt, minor.toInt, patch.toInt))
      case _                            => None
    }
}
