package com.xhub.browser.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying the O(1) tab lookup data structure behavior that was added
 * to [TabsManager] via [tabMap].
 *
 * Since [TabsManager] requires Android Activity context to instantiate full [WebPageTab]s,
 * these tests verify the Map logic in isolation using a lightweight fake tab model,
 * replicating exactly the insertion, removal, and lookup operations in TabsManager.
 */
class TabsManagerMapTest {

    // Lightweight fake representing the unique ID each WebPageTab holds.
    data class FakeTab(val id: Int, val title: String)

    // Replicate the same dual data structures used in TabsManager.
    private val tabList = arrayListOf<FakeTab>()
    private val tabMap = mutableMapOf<Int, FakeTab>()

    // ---------- Helpers mirroring TabsManager methods ----------

    private fun addTab(tab: FakeTab) {
        tabList.add(tab)
        tabMap[tab.id] = tab        // mirrors: tabMap[tab.id] = tab in newTab()
    }

    private fun removeTabAt(position: Int) {
        val tab = tabList.removeAt(position)
        tabMap.remove(tab.id)       // mirrors: tabMap.remove(tab.id) in removeTab()
    }

    private fun getTabById(id: Int): FakeTab? = tabMap[id]   // O(1)

    private fun findTabByIdLinear(id: Int): FakeTab? =        // O(N) – old way
        tabList.find { it.id == id }

    // -----------------------------------------------------------

    @Before
    fun setup() {
        tabList.clear()
        tabMap.clear()
    }

    // ------------------------------------------------------------------ //
    //  Basic correctness
    // ------------------------------------------------------------------ //

    @Test
    fun `getTabById returns correct tab after single insertion`() {
        addTab(FakeTab(id = 1, title = "Google"))

        assertThat(getTabById(1)).isNotNull
        assertThat(getTabById(1)?.title).isEqualTo("Google")
    }

    @Test
    fun `getTabById returns null for unknown id`() {
        addTab(FakeTab(id = 1, title = "Google"))

        assertThat(getTabById(999)).isNull()
    }

    @Test
    fun `getTabById returns correct tab among many tabs`() {
        repeat(50) { i -> addTab(FakeTab(id = i, title = "Tab $i")) }

        assertThat(getTabById(25)?.title).isEqualTo("Tab 25")
        assertThat(getTabById(0)?.title).isEqualTo("Tab 0")
        assertThat(getTabById(49)?.title).isEqualTo("Tab 49")
    }

    @Test
    fun `tabMap and tabList stay in sync after adding tabs`() {
        val tabs = listOf(
            FakeTab(1, "Home"),
            FakeTab(2, "Google"),
            FakeTab(3, "YouTube")
        )
        tabs.forEach { addTab(it) }

        assertThat(tabList.size).isEqualTo(tabMap.size)
        tabList.forEach { tab ->
            assertThat(tabMap).containsKey(tab.id)
            assertThat(tabMap[tab.id]).isEqualTo(tab)
        }
    }

    @Test
    fun `tabMap and tabList stay in sync after removing a tab`() {
        addTab(FakeTab(1, "Home"))
        addTab(FakeTab(2, "Google"))
        addTab(FakeTab(3, "YouTube"))

        // Remove the middle tab (index 1 = Google)
        removeTabAt(1)

        assertThat(tabList.size).isEqualTo(2)
        assertThat(tabMap.size).isEqualTo(2)
        // Removed tab must NOT be in the map
        assertThat(tabMap).doesNotContainKey(2)
        // Others must still be there
        assertThat(getTabById(1)?.title).isEqualTo("Home")
        assertThat(getTabById(3)?.title).isEqualTo("YouTube")
    }

    @Test
    fun `getTabById returns null after its tab is removed`() {
        addTab(FakeTab(id = 42, title = "Deleted Tab"))
        assertThat(getTabById(42)).isNotNull

        removeTabAt(0)

        assertThat(getTabById(42)).isNull()
    }

    @Test
    fun `getTabById result matches linear search result`() {
        repeat(30) { i -> addTab(FakeTab(id = i * 3, title = "Tab ${i * 3}")) }

        // Every O(1) map result should equal the O(N) list result
        (0 until 90 step 3).forEach { id ->
            assertThat(getTabById(id)).isEqualTo(findTabByIdLinear(id))
        }
        // Non-existent IDs
        assertThat(getTabById(1)).isEqualTo(findTabByIdLinear(1))
        assertThat(getTabById(1000)).isEqualTo(findTabByIdLinear(1000))
    }

    @Test
    fun `removing all tabs empties both list and map`() {
        repeat(10) { i -> addTab(FakeTab(id = i, title = "Tab $i")) }
        repeat(10) { removeTabAt(0) }

        assertThat(tabList).isEmpty()
        assertThat(tabMap).isEmpty()
    }

    // ------------------------------------------------------------------ //
    //  Performance: O(1) map must be dramatically faster than O(N) scan
    //  when the tab count is large.
    // ------------------------------------------------------------------ //

    @Test
    fun `O(1) map lookup is faster than O(N) linear search at scale`() {
        val tabCount = 5_000
        repeat(tabCount) { i -> addTab(FakeTab(id = i, title = "Tab $i")) }
        val targetId = tabCount - 1   // worst case for linear search

        val mapTime = measureNanos(iterations = 1_000) { getTabById(targetId) }
        val linearTime = measureNanos(iterations = 1_000) { findTabByIdLinear(targetId) }

        println("O(1) map avg: ${mapTime}ns  |  O(N) linear avg: ${linearTime}ns  |  speedup: ${linearTime / mapTime.coerceAtLeast(1)}x")

        // The map should be at least 10x faster at 5 000 tabs.
        assertThat(mapTime * 10).isLessThan(linearTime)
    }

    // ------------------------------------------------------------------ //
    //  Unique IDs — no two tabs should share the same key
    // ------------------------------------------------------------------ //

    @Test
    fun `adding two tabs with different ids produces two map entries`() {
        addTab(FakeTab(id = 100, title = "A"))
        addTab(FakeTab(id = 200, title = "B"))

        assertThat(tabMap).hasSize(2)
        assertThat(getTabById(100)?.title).isEqualTo("A")
        assertThat(getTabById(200)?.title).isEqualTo("B")
    }

    // ------------------------------------------------------------------ //
    //  Helper
    // ------------------------------------------------------------------ //

    private inline fun measureNanos(iterations: Int, block: () -> Unit): Long {
        // warm-up
        repeat(100) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        return (System.nanoTime() - start) / iterations
    }
}
