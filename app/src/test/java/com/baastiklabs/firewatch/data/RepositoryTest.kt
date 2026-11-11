package com.baastiklabs.firewatch.data

import com.baastiklabs.firewatch.core.model.Dose
import com.baastiklabs.firewatch.core.records.RecordEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch

class RepositoryTest {
    /** A store whose writes can be held open, like a slow disk. */
    private class SlowStore : RecordSource {
        val gate = CountDownLatch(1)
        @Volatile var slow = false
        val saved = java.util.concurrent.ConcurrentHashMap<String, RecordEnvelope>()
        override fun all(): List<RecordEnvelope> = saved.values.toList()
        override fun putAll(records: Collection<RecordEnvelope>) {
            if (slow) gate.await()
            records.forEach { saved[it.id] = it }
        }
    }

    @Test
    fun `an edited dose time saved as its sheet closes is stored and shown at once`() = runBlocking {
        val store = SlowStore()
        val repo = Repository(store) { 1_000L }
        val dose = Dose("d1", "gum-4", at = 100_000L, productName = "Nicotine gum 4 mg")
        repo.logDose(dose)
        store.slow = true
        // The sheet's own scope: it goes away (is cancelled) while the save is still writing.
        val sheet = CoroutineScope(Dispatchers.Default)
        val job = sheet.launch { repo.updateDose(dose.copy(at = 200_000L)) }
        delay(200)
        job.cancel()
        store.gate.countDown()
        withTimeout(5_000) { while (repo.data.value.doses.first().at != 200_000L) delay(10) }
        assertEquals(200_000L, repo.data.value.doses.single().at)
        assertEquals(true, store.saved["d1"]?.json?.contains("200000"))
    }

    @Test
    fun `choosing what to step in shows at once and survives a reload`() = runBlocking {
        val store = SlowStore()
        val repo = Repository(store) { 1_000L }
        repo.setStepUnit(com.baastiklabs.firewatch.core.model.DefaultProducts.POUCH_2MG)
        assertEquals(com.baastiklabs.firewatch.core.model.DefaultProducts.POUCH_2MG, repo.data.value.unitChanges.single().productId)
        // Every record type the app writes is read back the same way the web app reads it.
        val fresh = com.baastiklabs.firewatch.core.records.FirewatchData.fromRecords(store.all())
        assertEquals(fresh.unitChanges, repo.data.value.unitChanges)
        val reloaded = Repository(store) { 2_000L }
        reloaded.setStepUnit("")
        assertEquals(listOf(com.baastiklabs.firewatch.core.model.DefaultProducts.POUCH_2MG, ""), reloaded.data.value.unitChanges.map { it.productId })
    }
}
