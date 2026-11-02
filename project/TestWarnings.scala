import org.typelevel.sbt.tpolecat.TpolecatPlugin
import sbt.*
import sbt.Keys.*

/** ScalaTest assertions are values and several of them in one test body are normal, so test sources skip the two
  * checks that flag discarded values. Applied to every project; `tpolecatExcludeOptions` does not reach the test
  * `scalacOptions` of a project.
  */
object TestWarnings extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = TpolecatPlugin

  private val skipped = Set("-Wnonunit-statement", "-Wvalue-discard", "-Ywarn-value-discard")

  override def projectSettings: Seq[Setting[?]] = Seq(
    Test / scalacOptions ~= (_.filterNot(skipped.contains))
  )
}
