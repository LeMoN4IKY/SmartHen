package com.smarthen.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HousesViewModelTest {
    private open class FakeHouses : HousesRepository {
        override suspend fun list(server: String, token: String) = listOf(HouseDto("h1", "Home"))
        override suspend fun create(server: String, token: String, name: String) = HouseDto("h2", name)
        override suspend fun claim(server: String, token: String, houseId: String, claimToken: String) = ClaimedDevice("d1", "Controller")
    }
    @Test fun accountChangeCannotPublishOldHouseList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val vm = HousesViewModel(object : FakeHouses() {
                override suspend fun list(server: String, token: String): List<HouseDto> {
                    if (token == "old") withContext(NonCancellable) { gate.await() }
                    return listOf(HouseDto(token, token))
                }
            })
            vm.configure("https://server.example", "old"); runCurrent()
            vm.configure("https://server.example", "new"); runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals("new", vm.state.value.houses.single().id)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun logoutRemovesPreviousHouseListImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = HousesViewModel(FakeHouses())
            vm.configure("https://server.example", "token"); runCurrent()
            assertEquals(1, vm.state.value.houses.size)
            vm.configure("https://server.example", null)
            assertTrue(vm.state.value.houses.isEmpty())
            assertFalse(vm.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun claimCannotTargetHouseOutsideLoadedOwnershipList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var called = false
            val vm = HousesViewModel(object : FakeHouses() {
                override suspend fun claim(server: String, token: String, houseId: String, claimToken: String): ClaimedDevice { called = true; return super.claim(server, token, houseId, claimToken) }
            })
            vm.configure("https://server.example", "token"); runCurrent()
            vm.claim("foreign", "one-time-code-long-enough"); runCurrent()
            assertFalse(called)
            assertEquals(HousesError.INVALID_INPUT, vm.state.value.error)
        } finally { Dispatchers.resetMain() }
    }
}
