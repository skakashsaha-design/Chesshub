package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.bots.BotCatalog
import com.example.chess.ChessRules
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.EngineBackendKind
import com.example.engine.StockfishEngineService
import com.example.engine.StockfishUciWrapper
import com.example.settings.LocaleHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and localization resolve properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("ChessHub", context.getString(R.string.app_name))

    val bnContext = LocaleHelper.wrapContext(context, "bn")
    assertEquals("হোম", bnContext.getString(R.string.nav_home))
  }

  @Test
  fun `stockfish wrapper computes legal bot move and position evaluation`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val wrapper = StockfishUciWrapper(context)
      val state = ChessRules.initialBoardState()
      val bot = BotCatalog.presets.first()
      val budget = DeviceMemoryAdvisor.computeSearchBudget(
        requestedDepth = 2,
        requestedTimeMs = 250L,
        lowMemoryMode = true,
        batterySavingMode = false,
        isLowRamHardware = true,
      )

      val moveEval = wrapper.findBotMove(state, bot, budget)
      assertNotNull(moveEval)
      assertTrue(moveEval!!.bestMove in ChessRules.legalMoves(state))
      assertEquals(EngineBackendKind.BUILTIN_KOTLIN, moveEval.evaluation.backend)

      val posEval = wrapper.evaluatePosition(state, budget)
      assertNotNull(posEval.bestMoveUci)
      assertTrue(posEval.depthReached >= 1)
      wrapper.shutdown()
    }
  }

  @Test
  fun `stockfish engine service binds and evaluates positions off UI thread`() {
    runBlocking {
      val controller = Robolectric.buildService(StockfishEngineService::class.java).create().bind()
      val service = controller.get()
      val binder = service.onBind(Intent()) as StockfishEngineService.LocalBinder
      assertEquals(service, binder.getService())

      val state = ChessRules.initialBoardState()
      val budget = DeviceMemoryAdvisor.computeSearchBudget(
        requestedDepth = 2,
        requestedTimeMs = 200L,
        lowMemoryMode = true,
        batterySavingMode = false,
        isLowRamHardware = true,
      )
      val botMove = service.computeBotMove(state, BotCatalog.presets[1], budget)
      assertNotNull(botMove)
      assertTrue(botMove!!.bestMove in ChessRules.legalMoves(state))

      val eval = service.evaluatePosition(state, budget)
      assertTrue(eval.depthReached >= 1)
      assertTrue(service.engineStatus.value.isReady)

      controller.unbind().destroy()
    }
  }
}
