package mesosphere.marathon
package integration

import mesosphere.{AkkaIntegrationTest, WaitTestSupport}
import mesosphere.marathon.integration.setup._
import mesosphere.marathon.raml.App
import mesosphere.marathon.state.PathId._
import scala.concurrent.duration._

class LaunchQueueIntegrationTest extends AkkaIntegrationTest with EmbeddedMarathonTest {
  override def afterEach(): Unit = {
    marathon.deleteRoot(force = true)
    super.afterEach()
  }

  "LaunchQueue" should {
    "GET /v2/queue with an empty queue" in {
      Given("no pending deployments")
      marathon.listDeploymentsForBaseGroup().value should have size 0

      Then("the launch queue should be empty")
      val response = marathon.launchQueue()
      response should be(OK)

      val queue = response.value.queue
      queue should have size 0
    }

    "GET /v2/queue with pending app" in {
      Given("a new app with constraints that cannot be fulfilled")
      val c = Seq("nonExistent", "CLUSTER", "na")
      val appId = testBasePath / "app"
      val app = App(appId.toString, constraints = Set(c), cmd = Some("na"), instances = 5, portDefinitions = None)
      val create = marathon.createAppV2(app)
      create should be(Created)

      Then("the app shows up in the launch queue")
      eventually {
        val response = marathon.launchQueue()
        response should be(OK)

        val queue = response.value.queue
        queue should have size 1
        queue.head.app.id.toPath should be (appId)
        queue.head.count should be (5)
      }
    }

    "GET /v2/queue with backed-off failing app" in {
      Given("a new app with constraints that cannot be fulfilled")
      val appId = testBasePath / "fail-app"
      val app = App(appId.toString, cmd = Some("exit 1"), instances = 1, portDefinitions = None, backoffSeconds = 60)
      val create = marathon.createAppV2(app)
      create should be(Created)

      Then("the app shows up in the launch queue")
      WaitTestSupport.waitUntil("Deployment is put in the deployment queue", 30.seconds) { marathon.launchQueue().value.queue.size == 1 }

      eventually {
        val response = marathon.launchQueue()
        response should be(OK)

        val queue = response.value.queue
        queue should have size 1
        queue.head.app.id.toPath should be (appId)
        queue.head.delay.timeLeftSeconds should be > 0
      }
    }
  }
}
