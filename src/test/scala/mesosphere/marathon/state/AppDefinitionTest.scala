package mesosphere.marathon.state

import javax.validation.Validation

import com.google.common.collect.Lists
import mesosphere.marathon.upgrade.DeploymentPlan
import mesosphere.marathon.{ Protos, MarathonSpec }
import mesosphere.marathon.Protos.{ Constraint, ServiceDefinition }
import mesosphere.marathon.Protos.HealthCheckDefinition.Protocol
import mesosphere.marathon.api.ModelValidation
import mesosphere.marathon.api.v2.json.EnrichedTask
import mesosphere.marathon.health.{ HealthCheck, HealthCounts }
import mesosphere.marathon.state.Container.Docker
import mesosphere.marathon.state.PathId._
import org.apache.mesos.{ Protos => mesos }
import org.scalatest.Matchers
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

class AppDefinitionTest extends MarathonSpec with Matchers {

  test("ToProto") {
    val app1 = AppDefinition(
      id = "play".toPath,
      cmd = Some("bash foo-*/start -Dhttp.port=$PORT"),
      cpus = 4,
      mem = 256,
      instances = 5,
      ports = Seq(8080, 8081),
      executor = "//cmd"
    )

    val proto1 = app1.toProto
    assert("play" == proto1.getId)
    assert(proto1.getCmd.hasValue)
    assert(proto1.getCmd.getShell)
    assert("bash foo-*/start -Dhttp.port=$PORT" == proto1.getCmd.getValue)
    assert(5 == proto1.getInstances)
    assert(Lists.newArrayList(8080, 8081) == proto1.getPortsList)
    assert("//cmd" == proto1.getExecutor)
    assert(4 == getScalarResourceValue(proto1, "cpus"), 1e-6)
    assert(256 == getScalarResourceValue(proto1, "mem"), 1e-6)
    assert("bash foo-*/start -Dhttp.port=$PORT" == proto1.getCmd.getValue)
    assert(!proto1.hasContainer)
    assert(1.0 == proto1.getUpgradeStrategy.getMinimumHealthCapacity)
    assert(1.0 == proto1.getUpgradeStrategy.getMaximumOverCapacity)

    val app2 = AppDefinition(
      id = "play".toPath,
      cmd = None,
      args = Some(Seq("a", "b", "c")),
      container = Some(
        Container(docker = Some(Container.Docker("group/image")))
      ),
      cpus = 4,
      mem = 256,
      instances = 5,
      ports = Seq(8080, 8081),
      executor = "//cmd",
      upgradeStrategy = UpgradeStrategy(0.7, 0.4)
    )

    val proto2 = app2.toProto
    assert("play" == proto2.getId)
    assert(!proto2.getCmd.hasValue)
    assert(!proto2.getCmd.getShell)
    assert(Seq("a", "b", "c") == proto2.getCmd.getArgumentsList.asScala)
    assert(5 == proto2.getInstances)
    assert(Lists.newArrayList(8080, 8081) == proto2.getPortsList)
    assert("//cmd" == proto2.getExecutor)
    assert(4 == getScalarResourceValue(proto2, "cpus"), 1e-6)
    assert(256 == getScalarResourceValue(proto2, "mem"), 1e-6)
    assert(proto2.hasContainer)
    assert(0.7 == proto2.getUpgradeStrategy.getMinimumHealthCapacity)
    assert(0.4 == proto2.getUpgradeStrategy.getMaximumOverCapacity)
  }

  test("MergeFromProto") {
    val cmd = mesos.CommandInfo.newBuilder
      .setValue("bash foo-*/start -Dhttp.port=$PORT")

    val proto1 = ServiceDefinition.newBuilder
      .setId("play")
      .setCmd(cmd)
      .setInstances(3)
      .setExecutor("//cmd")
      .setVersion(Timestamp.now.toString)
      .build

    val app1 = AppDefinition().mergeFromProto(proto1)

    assert("play" == app1.id.toString)
    assert(3 == app1.instances)
    assert("//cmd" == app1.executor)
    assert(Some("bash foo-*/start -Dhttp.port=$PORT") == app1.cmd)
  }

  test("ProtoRoundtrip") {
    val app1 = AppDefinition(
      id = "play".toPath,
      cmd = Some("bash foo-*/start -Dhttp.port=$PORT"),
      cpus = 4,
      mem = 256,
      instances = 5,
      ports = Seq(8080, 8081),
      executor = "//cmd",
      labels = Map(
        "one" -> "aaa",
        "two" -> "bbb",
        "three" -> "ccc"
      )
    )
    val result1 = AppDefinition().mergeFromProto(app1.toProto)
    assert(result1 == app1)

    val app2 = AppDefinition(cmd = None, args = Some(Seq("a", "b", "c")))
    val result2 = AppDefinition().mergeFromProto(app2.toProto)
    assert(result2 == app2)
  }

  def getScalarResourceValue(proto: ServiceDefinition, name: String) = {
    proto.getResourcesList.asScala
      .find(_.getName == name)
      .get.getScalar.getValue
  }
}
