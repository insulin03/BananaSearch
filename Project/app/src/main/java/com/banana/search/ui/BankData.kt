package com.banana.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.banana.search.core.BankBatch
import com.banana.search.core.QuestionNormalizer
import com.banana.search.core.StoredQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 200L

@Composable
fun rememberBankBatches(host: AppHost, tick: Int, revision: Int): List<BankBatch> =
    produceState(emptyList(), tick, revision) {
        value = withContext(Dispatchers.IO) { host.bankBatches() }
    }.value

@Composable
fun rememberBankQuestions(host: AppHost, batchId: String): List<StoredQuestion> =
    produceState(emptyList(), batchId) {
        value = withContext(Dispatchers.IO) { host.bankQuestions(batchId) }
    }.value

@Composable
fun rememberBankSearch(
    host: AppHost,
    batches: List<BankBatch>,
    keyword: String
): List<Pair<BankBatch, StoredQuestion>> {
    val norm = QuestionNormalizer.normalize(keyword.trim())
    return produceState(emptyList(), batches, norm) {
        if (norm.isEmpty()) {
            value = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            value = withContext(Dispatchers.IO) {
                batches.flatMap { batch ->
                    host.bankQuestions(batch.id)
                        .filter { QuestionNormalizer.normalize(it.question).contains(norm) }
                        .map { batch to it }
                }
            }
        }
    }.value
}
