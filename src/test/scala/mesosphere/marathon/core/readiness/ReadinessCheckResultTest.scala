package mesosphere.marathon
package core.readiness

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import mesosphere.UnitTest
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.core.readiness.ReadinessCheckExecutor.ReadinessCheckSpec
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.PathId

import scala.concurrent.duration._

class ReadinessCheckResultTest extends UnitTest {
  "ReadinessCheckResult" should {
    "response from check and ready result (preserveLastResponse = true)" in {
      val f = new Fixture
      When("converting a ready result")
      val result = ReadinessCheckResult.forSpecAndResponse(f.check, f.readyResponse)
      Then("we get the expected result")
      result should equal(
        ReadinessCheckResult(
          f.check.checkName,
          f.check.taskId,
          ready = true,
          lastResponse = Some(f.readyResponse)
        )
      )
    }

    "response from check and ready result (preserveLastResponse = false)" in {
      val f = new Fixture
      When("converting a NOT ready result")
      val result = ReadinessCheckResult.forSpecAndResponse(f.check.copy(preserveLastResponse = false), f.readyResponse)
      Then("we get the expected result")
      result should equal(
        ReadinessCheckResult(
          f.check.checkName,
          f.check.taskId,
          ready = true,
          lastResponse = None
        )
      )
    }

    "response from check and NOT ready result (preserveLastResponse = true)" in {
      val f = new Fixture
      When("converting a NOT ready result")
      val result = ReadinessCheckResult.forSpecAndResponse(f.check, f.notReadyResponse)
      Then("we get the expected result")
      result should equal(
        ReadinessCheckResult(
          f.check.checkName,
          f.check.taskId,
          ready = false,
          lastResponse = Some(f.notReadyResponse)
        )
      )
    }

    "response from check and NOT ready result (preserveLastResponse = false)" in {
      val f = new Fixture
      When("converting a ready result")
      val result = ReadinessCheckResult.forSpecAndResponse(f.check.copy(preserveLastResponse = false), f.notReadyResponse)
      Then("we get the expected result")
      result should equal(
        ReadinessCheckResult(
          f.check.checkName,
          f.check.taskId,
          ready = false,
          lastResponse = None
        )
      )
    }
  }
  class Fixture {
    val instanceId = Instance.Id.forRunSpec(PathId("/test"))
    val check = ReadinessCheckSpec(
      taskId = Task.Id(instanceId),
      checkName = "testCheck",
      url = "http://sample.url:123",
      interval = 3.seconds,
      timeout = 1.second,
      httpStatusCodesForReady = Set(StatusCodes.OK.intValue),
      preserveLastResponse = true
    )

    val readyResponse = HttpResponse(
      status = 200,
      contentType = ContentTypes.`text/plain(UTF-8)`.value,
      body = "Hi"
    )

    val notReadyResponse = HttpResponse(
      status = 503,
      contentType = ContentTypes.`text/plain(UTF-8)`.value,
      body = "Hi"
    )
  }
}
