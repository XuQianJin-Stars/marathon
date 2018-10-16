package mesosphere.marathon
package storage.migration

import akka.Done
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.StrictLogging
import mesosphere.AkkaUnitTest
import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.core.storage.store.impl.zk.ZkPersistenceStore
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.core.task.state.NetworkInfo
import mesosphere.marathon.state.{PathId, Timestamp}
import mesosphere.marathon.storage.repository.InstanceRepository
import org.apache.mesos
import org.apache.mesos.Protos.NetworkInfo.Protocol
import org.scalatest.Inspectors
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import scala.concurrent.Future

class MigrationTo18Test extends AkkaUnitTest with StrictLogging with Inspectors {

  "Migration to 18" should {
    "Update instance condition from Created to Provisioned" in {

      Given("two instances in Created state")
      val f = new Fixture()

      val instanceId1 = Instance.Id.forRunSpec(PathId("/app"))
      val instanceId2 = Instance.Id.forRunSpec(PathId("/app2"))

      val instance1Json = f.created(instanceId1)
      val instance1 = instance1Json.as[Instance]
      val migratedInstance1 = f.setProvisionedCondition(instance1)

      val instance2Json = f.created(instanceId2)
      val instance2 = instance2Json.as[Instance]
      val migratedInstance2 = f.setProvisionedCondition(instance2)

      f.instanceRepository.ids() returns Source(List(instanceId1, instanceId2))
      f.persistenceStore.get[Instance.Id, JsValue](equalTo(instanceId1))(any, any) returns Future(Some(instance1Json))
      f.persistenceStore.get[Instance.Id, JsValue](equalTo(instanceId2))(any, any) returns Future(Some(instance2Json))
      f.instanceRepository.store(equalTo(migratedInstance1)) returns Future.successful(Done)
      f.instanceRepository.store(equalTo(migratedInstance2)) returns Future.successful(Done)

      When("they are migrated")
      MigrationTo18.migrateInstanceConditions(f.instanceRepository, f.persistenceStore).futureValue

      Then("all updated instances are saved")
      verify(f.instanceRepository, times(2)).store(any)
    }

  }

  class Fixture {

    val instanceRepository: InstanceRepository = mock[InstanceRepository]
    val persistenceStore: ZkPersistenceStore = mock[ZkPersistenceStore]

    /**
      * Construct JSON for an instance.
      * @param i The id of the instance.
      * @return The JSON of the instance.
      */

    def setProvisionedCondition(instance: Instance): Instance = {
      instance.copy(
        state = instance.state.copy(condition = Condition.Provisioned),
        tasksMap = instance.tasksMap.mapValues { task =>
          task.copy(status = task.status.copy(condition = Condition.Provisioned))
        }
      )
    }

    def taskString(i: Instance.Id, condition: String) = {
      val taskStatus = Json.toJson(Task.Status(
        stagedAt = Timestamp.now(),
        condition = Condition.Running,
        networkInfo = NetworkInfo(
          "127.0.0.1",
          8888 :: Nil,
          mesos.Protos.NetworkInfo.IPAddress.newBuilder()
            .setProtocol(Protocol.IPv4)
            .setIpAddress("127.0.0.1")
            .build() :: Nil)
      )).as[JsObject] + ("condition" -> JsString(condition))

      s"""
         |{
         |  "taskId": "${Task.Id.forInstanceId(i, None)}",
         |  "runSpecVersion": "${Timestamp.now}",
         |  "status": ${taskStatus.toString()}
         |}
       """.stripMargin
    }

    def created(i: Instance.Id): JsObject = {

      Json.parse(
        s"""
           |{
           |  "instanceId": { "idString": "${i.idString}" },
           |  "tasksMap": {
           |     "${Task.Id.forInstanceId(i, None)}": ${taskString(i, "Created")}
           |  },
           |  "runSpecVersion": "2015-01-01T12:00:00.000Z",
           |  "agentInfo": { "host": "localhost", "attributes": [] },
           |  "state": { "since": "2015-01-01T12:00:00.000Z", "condition": "Created", "goal": "Running" }
           |}""".stripMargin).as[JsObject]
    }

  }
}