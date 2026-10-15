package com.flykpicks.android.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import com.flykpicks.android.model.enums.ItemCondition
import com.flykpicks.android.model.enums.MediaFormat
import com.flykpicks.android.service.AssetService
import com.flykpicks.android.service.GenreService
import com.flykpicks.android.service.LibraryService
import com.flykpicks.android.store.GenreStore
import com.flykpicks.android.store.LibraryStore
import com.flykpicks.android.testing.HEAT_BLURAY
import com.flykpicks.android.testing.HEAT_DVD
import com.flykpicks.android.testing.LIBRARY_PATH
import com.flykpicks.android.testing.LIBRARY_REMOVE_PATH
import com.flykpicks.android.testing.LIBRARY_SEASONS_PATH
import com.flykpicks.android.testing.LIBRARY_UPDATE_PATH
import com.flykpicks.android.testing.PEAKS_BLURAY
import com.flykpicks.android.testing.Routes
import com.flykpicks.android.testing.SEASONS_JSON
import com.flykpicks.android.testing.TestCopy
import com.flykpicks.android.testing.TestNetwork
import com.flykpicks.android.testing.errorJson
import com.flykpicks.android.testing.heatJson
import com.flykpicks.android.testing.inventoryTitleJson
import com.flykpicks.android.testing.json
import com.flykpicks.android.testing.libraryListJson
import com.flykpicks.android.testing.libraryPageJson
import com.flykpicks.android.testing.status
import com.flykpicks.android.testing.twinPeaksJson
import com.flykpicks.android.ui.components.ToastMessage
import com.flykpicks.android.ui.components.ToastStore
import com.flykpicks.android.ui.navigation.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val PEAKS_SEASONS_PATH = "v1/media/tv/peaks/seasons"

/** The title and season screens' shared ViewModel, against the production networking stack. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryItemDetailViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var net: TestNetwork
    private lateinit var store: LibraryStore
    private lateinit var toasts: ToastStore
    private val routes = Routes()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { dispatcher = routes; start() }
        net = TestNetwork(server).apply { signIn() }
        store = LibraryStore(LibraryService(net.api(), net.genres), GenreStore(GenreService(net.api(), net.genres)), appScope)
        toasts = ToastStore(appScope)
        routes.on(PEAKS_SEASONS_PATH, json(SEASONS_JSON))
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    private fun loadLibrary(vararg titles: String = arrayOf(heatJson(), twinPeaksJson())) {
        routes.on(LIBRARY_PATH, json(libraryPageJson(*titles)))
        runBlocking { store.load() }
    }

    private fun viewModel(titleId: String) = LibraryItemDetailViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_TITLE_ID to titleId)),
        store,
        AssetService(net.api(), net.genres),
        toasts,
    )

    /** The Twin Peaks screen, once its seasons have arrived. */
    private fun peaks() = viewModel("peaks").also { vm -> eventually { vm.seasons.isNotEmpty() } }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Condition not met in time" }
            Thread.sleep(5)
        }
    }

    private fun Job?.await() = runBlocking { this@await?.join() }

    private fun copy(id: String) = store.state.value.item(id)!!

    // MARK: Seasons of the series

    @Test
    fun aSeries_fetchesItsSeasons_attributedToTheLibrary() {
        loadLibrary()

        val vm = peaks()

        assertEquals(listOf(1, 2, 3), vm.seasons.map { it.seasonNumber })
        assertFalse(vm.seasonsLoading)
        assertEquals("library.detail", routes.requestsTo(PEAKS_SEASONS_PATH).single().headers["X-Flyk-Source"])
    }

    @Test
    fun aFilm_neverAsksForSeasons() {
        loadLibrary()

        val vm = viewModel("heat")

        assertTrue(vm.seasons.isEmpty())
        assertFalse(vm.seasonsLoading)
        assertTrue(routes.paths().none { it.endsWith("/seasons") })
    }

    @Test
    fun seasonsThatCannotBeFetched_areLeftEmpty() {
        loadLibrary(inventoryForUnknownSeries())

        val vm = viewModel("lost")
        eventually { routes.requestsTo("v1/media/tv/lost/seasons").size == 1 && !vm.seasonsLoading }

        assertTrue(vm.seasons.isEmpty())
    }

    private fun inventoryForUnknownSeries() = inventoryTitleJson("lost", "Lost", TestCopy("lost-dvd"), tv = true)

    @Test
    fun openedBeforeTheLibraryHasLoaded_loadsItFirst() {
        // As after process death: the back stack is restored, the store is empty.
        routes.on(LIBRARY_PATH, json(libraryPageJson(twinPeaksJson())))

        val vm = peaks()

        assertTrue(vm.library.value.hasLoaded)
        assertEquals(listOf("peaks-bluray"), vm.library.value.items(forTitleId = "peaks").map { it.id })
        assertEquals(3, vm.seasons.size)
    }

    // MARK: Edit sheet

    @Test
    fun editing_startsFromTheCopy_andSavesOnlyAChange() {
        loadLibrary()
        val vm = viewModel("heat")
        val saved = HEAT_DVD.copy(format = "BLURAY", condition = "VERY_GOOD")
        routes.on(LIBRARY_UPDATE_PATH, json(libraryListJson(heatJson(saved, HEAT_BLURAY), twinPeaksJson())))

        vm.startEditing(copy("heat-dvd"))
        val sheet = vm.edit!!.sheet
        assertEquals("Heat", sheet.title)
        assertEquals(MediaFormat.DVD, sheet.format)
        assertTrue(sheet.canRemove)
        assertFalse(sheet.hasChanges)

        vm.saveEdit().await()
        assertEquals("nothing changed, nothing sent", 0, routes.requestsTo(LIBRARY_UPDATE_PATH).size)

        vm.selectFormat(MediaFormat.BLURAY)
        vm.selectCondition(ItemCondition.VERY_GOOD)
        assertTrue(vm.edit!!.sheet.hasChanges)
        vm.saveEdit().await()

        assertNull("the sheet closes once the change is saved", vm.edit)
        assertEquals(
            """{"mediaInventoryItemID":"heat-dvd","format":"BLURAY","condition":"VERY_GOOD"}""",
            routes.bodyOf(LIBRARY_UPDATE_PATH),
        )
        assertEquals(ItemCondition.VERY_GOOD, copy("heat-dvd").condition)
    }

    @Test
    fun aSaveThatFails_keepsTheSheetOpen_withTheReason_andTheOldValuesInTheLibrary() {
        loadLibrary()
        val vm = viewModel("heat")
        routes.on(LIBRARY_UPDATE_PATH, errorJson(409, "conflict", "That copy is in an open order."))

        vm.startEditing(copy("heat-dvd"))
        vm.selectFormat(MediaFormat.VHS)
        vm.saveEdit().await()

        val sheet = vm.edit!!.sheet
        assertEquals("That copy is in an open order.", sheet.errorMessage)
        assertFalse(sheet.isSaving)
        assertEquals("the choice is kept for another try", MediaFormat.VHS, sheet.format)
        assertEquals(MediaFormat.DVD, copy("heat-dvd").format)
    }

    @Test
    fun cancelEdit_closesTheSheet_withoutSaving() {
        loadLibrary()
        val vm = viewModel("heat")

        vm.startEditing(copy("heat-dvd"))
        vm.selectFormat(MediaFormat.VHS)
        vm.cancelEdit()

        assertNull(vm.edit)
        assertEquals(MediaFormat.DVD, copy("heat-dvd").format)
    }

    @Test
    fun removingTheEditedCopy_toasts_closesTheSheet_andLeavesTheOtherCopies() {
        loadLibrary()
        val vm = viewModel("heat")
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson(HEAT_BLURAY), twinPeaksJson())))

        vm.startEditing(copy("heat-dvd"))
        vm.removeEdited().await()

        assertNull(vm.edit)
        assertEquals(ToastMessage("Removed from library"), toasts.current.value)
        assertEquals(listOf("heat-bluray"), vm.library.value.items(forTitleId = "heat").map { it.id })
        assertFalse(vm.isBusy)
    }

    @Test
    fun aRemovalThatFails_keepsTheSheetOpen_withTheReason_andTheCopyInTheLibrary() {
        loadLibrary(heatJson(HEAT_DVD))
        val vm = viewModel("heat")
        routes.on(LIBRARY_REMOVE_PATH, status(500))

        vm.startEditing(copy("heat-dvd"))
        vm.removeEdited().await()

        assertEquals("Something went wrong. Try again.", vm.edit!!.sheet.errorMessage)
        assertFalse(vm.edit!!.sheet.isRemoving)
        assertNull(toasts.current.value)
        assertEquals("the title's last copy is back, so the screen stays", 1, vm.library.value.items(forTitleId = "heat").size)
    }

    @Test
    fun whileTheLastCopysRemovalIsInFlight_theScreenIsBusy_soItDoesNotCloseEarly() {
        loadLibrary(heatJson(HEAT_DVD))
        val vm = viewModel("heat")
        routes.on(LIBRARY_REMOVE_PATH, status(500))
        val inFlight = routes.hold(LIBRARY_REMOVE_PATH)

        vm.startEditing(copy("heat-dvd"))
        val removal = vm.removeEdited()
        assertTrue(vm.library.value.items(forTitleId = "heat").isEmpty())
        assertTrue(vm.isBusy)
        assertTrue(vm.edit!!.sheet.isRemoving)

        inFlight.countDown()
        removal.await()
        assertFalse(vm.isBusy)
    }

    @Test
    fun theSeasonScreensSheet_hasItsOwnTitle_noPoster_andNoRemove() {
        loadLibrary()
        val vm = peaks()

        vm.startEditing(copy("peaks-bluray"), title = "Twin Peaks — Season 2", posterUrl = null, canRemove = false)

        val sheet = vm.edit!!.sheet
        assertEquals("Twin Peaks — Season 2", sheet.title)
        assertNull(sheet.posterUrl)
        assertFalse(sheet.canRemove)
    }

    // MARK: Changing a copy's seasons

    private val peaksWithSeasons123 = json(libraryListJson(heatJson(), twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(1, 2, 3)))))

    @Test
    fun addSeason_sendsTheCopysSeasonsWithTheNewOne_inOrder() {
        loadLibrary(twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(3, 1))))
        val vm = peaks()
        routes.on(LIBRARY_SEASONS_PATH, peaksWithSeasons123)

        vm.addSeason(copy("peaks-bluray"), 2).await()

        assertEquals(
            """{"mediaInventoryItemID":"peaks-bluray","ownedSeasons":[1,2,3],"selectAllSeasons":false}""",
            routes.bodyOf(LIBRARY_SEASONS_PATH),
        )
        assertTrue(vm.library.value.isSeasonOwned(2, titleId = "peaks"))
        assertFalse(vm.isUpdating)
    }

    @Test
    fun addAllSeasons_asksTheServerForEverySeason() {
        loadLibrary()
        val vm = peaks()
        routes.on(LIBRARY_SEASONS_PATH, peaksWithSeasons123)

        vm.addAllSeasons(copy("peaks-bluray")).await()

        assertEquals("""{"mediaInventoryItemID":"peaks-bluray","selectAllSeasons":true}""", routes.bodyOf(LIBRARY_SEASONS_PATH))
        assertTrue(vm.seasons.all { vm.library.value.isSeasonOwned(it.seasonNumber, titleId = "peaks") })
    }

    @Test
    fun removeSeason_sendsWhatIsLeft() {
        loadLibrary()
        val vm = peaks()
        routes.on(LIBRARY_SEASONS_PATH, json(libraryListJson(heatJson(), twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(2))))))

        vm.removeSeason(copy("peaks-bluray"), 1).await()

        assertEquals(
            """{"mediaInventoryItemID":"peaks-bluray","ownedSeasons":[2],"selectAllSeasons":false}""",
            routes.bodyOf(LIBRARY_SEASONS_PATH),
        )
        assertNull(vm.pendingCopyRemoval)
        assertFalse(vm.library.value.isSeasonOwned(1, titleId = "peaks"))
    }

    @Test
    fun removingACopysLastSeason_asksFirst_thenRemovesTheCopy_andClosesTheSeasonScreen() {
        loadLibrary(heatJson(), twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(2))))
        val vm = peaks()
        routes.on(LIBRARY_REMOVE_PATH, json(libraryListJson(heatJson())))

        vm.removeSeason(copy("peaks-bluray"), 2).await()
        assertEquals("peaks-bluray", vm.pendingCopyRemoval)
        assertTrue("an empty season list is never sent", routes.requestsTo(LIBRARY_SEASONS_PATH).isEmpty())
        assertTrue(routes.requestsTo(LIBRARY_REMOVE_PATH).isEmpty())

        vm.confirmCopyRemoval().await()

        assertNull(vm.pendingCopyRemoval)
        assertEquals("""{"mediaInventoryItemID":"peaks-bluray"}""", routes.bodyOf(LIBRARY_REMOVE_PATH))
        assertTrue(vm.seasonScreenShouldClose)
        assertTrue(vm.library.value.items(forTitleId = "peaks").isEmpty())

        vm.onSeasonScreenClosed()
        assertFalse(vm.seasonScreenShouldClose)
    }

    @Test
    fun cancellingThatQuestion_keepsTheCopy() {
        loadLibrary(twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(2))))
        val vm = peaks()

        vm.removeSeason(copy("peaks-bluray"), 2)
        vm.cancelCopyRemoval()
        vm.confirmCopyRemoval().await()

        assertNull(vm.pendingCopyRemoval)
        assertTrue(routes.requestsTo(LIBRARY_REMOVE_PATH).isEmpty())
        assertFalse(vm.seasonScreenShouldClose)
    }

    @Test
    fun aCopyThatOwnsTheWholeSeries_hasNoSeasonToTakeOff() {
        loadLibrary(twinPeaksJson(PEAKS_BLURAY.copy(seasons = null)))
        val vm = peaks()

        assertNull(vm.removeSeason(copy("peaks-bluray"), 1))

        assertNull(vm.pendingCopyRemoval)
        assertTrue(routes.requestsTo(LIBRARY_SEASONS_PATH).isEmpty())
    }

    @Test
    fun aSeasonChangeThatFails_isReported_untilDismissed_andLeavesTheScreenOpen() {
        loadLibrary(twinPeaksJson(PEAKS_BLURAY.copy(seasons = listOf(2))))
        val vm = peaks()
        routes.on(LIBRARY_SEASONS_PATH, errorJson(404, "not_found", "No inventory item found."))
        routes.on(LIBRARY_REMOVE_PATH, status(500))

        vm.addSeason(copy("peaks-bluray"), 1).await()
        assertEquals("No inventory item found.", vm.updateError)
        assertFalse(vm.isUpdating)
        vm.dismissUpdateError()
        assertNull(vm.updateError)

        vm.removeSeason(copy("peaks-bluray"), 2)
        vm.confirmCopyRemoval().await()
        assertEquals("Something went wrong. Try again.", vm.updateError)
        assertFalse(vm.seasonScreenShouldClose)
        assertEquals(1, vm.library.value.items(forTitleId = "peaks").size)
    }
}
