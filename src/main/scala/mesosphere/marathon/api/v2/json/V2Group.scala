package mesosphere.marathon.api.v2.json

import mesosphere.marathon.state.{ Group, PathId, Timestamp }

case class V2Group(
    id: PathId,
    apps: Set[V2AppDefinition] = V2Group.DefaultApps,
    groups: Set[V2Group] = V2Group.DefaultGroups,
    dependencies: Set[PathId] = Group.DefaultDependencies,
    version: Timestamp = Group.DefaultVersion) {

  /**
    * Returns the canonical internal representation of this API-specific
    * group.
    */
  def toGroup(): Group =
    Group(
      id,
      apps.map(_.toAppDefinition),
      groups.map(_.toGroup),
      dependencies,
      version
    )

}

object V2Group {
  def DefaultApps: Set[V2AppDefinition] = Set.empty
  def DefaultGroups: Set[V2Group] = Set.empty

  def apply(group: Group): V2Group =
    V2Group(
      group.id,
      group.apps.map(V2AppDefinition(_)),
      group.groups.map(V2Group(_)),
      group.dependencies,
      group.version)
}
