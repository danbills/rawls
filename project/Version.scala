import sbt.Keys._
import sbt._

object Version {
  val baseModelVersion = "0.1"
  val lastModelCommit = "git log -n 1 --pretty=format:%h model" !!

  val modelVersionSettings: Seq[Setting[_]] =
    Seq( version := versionString )

  def versionString = {
    val version = baseModelVersion + "-" + lastModelCommit

    // The project isSnapshot string passed in via command line settings, if desired.
    val isSnapshot = sys.props.get("project.isSnapshot").forall(_.toBoolean)

    // For now, obfuscate SNAPSHOTs from sbt's developers: https://github.com/sbt/sbt/issues/2687#issuecomment-236586241
    if (isSnapshot) s"$version-SNAP" else version
  }
}