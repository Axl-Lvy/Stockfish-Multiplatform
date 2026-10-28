package fr.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

class EngineDownloadTest {

  @Test
  fun fractionIsZeroBeforeAnyByte() {
    EngineDownload.InProgress(receivedBytes = 0, totalBytes = 1_000).fraction shouldBe 0f
  }

  @Test
  fun fractionAfterTheFirstByte() {
    EngineDownload.InProgress(receivedBytes = 1, totalBytes = 1_000).fraction shouldBe 0.001f
  }

  @Test
  fun fractionJustBelowTheTotal() {
    EngineDownload.InProgress(receivedBytes = 999, totalBytes = 1_000).fraction shouldBe 0.999f
  }

  @Test
  fun fractionIsOneAtTheTotal() {
    EngineDownload.InProgress(receivedBytes = 1_000, totalBytes = 1_000).fraction shouldBe 1f
  }

  @Test
  fun fractionIsCappedAtOneBeyondTheTotal() {
    EngineDownload.InProgress(receivedBytes = 1_001, totalBytes = 1_000).fraction shouldBe 1f
  }

  @Test
  fun fractionOfTheFullEngineSize() {
    EngineDownload.InProgress(receivedBytes = 56_503_670, totalBytes = 113_007_340)
      .fraction shouldBe 0.5f
  }

  @Test
  fun downloadIsDoneOnceAnEngineIsCreated() =
    runTest(timeout = 5.minutes) {
      getStockfish().close()

      stockfishDownload.value shouldBe EngineDownload.Done
    }
}
