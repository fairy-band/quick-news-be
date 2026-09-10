package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProcessingState
import com.nexters.external.enums.ContentProcessingStage
import com.nexters.external.enums.ContentProcessingStatus
import com.nexters.external.repository.ContentProcessingStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentProcessingStateService(
    private val repository: ContentProcessingStateRepository,
) {
    @Transactional(readOnly = true)
    fun eligible(contents: List<Content>, stage: ContentProcessingStage): List<Content> {
        val states = repository.findByContentIdInAndStage(contents.mapNotNull { it.id }, stage).associateBy { it.contentId }
        val now = LocalDateTime.now()
        return contents.filter { content ->
            val state = states[content.id] ?: return@filter true
            state.status != ContentProcessingStatus.FAILED && (state.status != ContentProcessingStatus.RETRY_AT || state.retryAt?.isAfter(now) != true)
        }
    }

    @Transactional
    fun processing(contents: Collection<Content>, stage: ContentProcessingStage) = contents.forEach { content ->
        update(requireNotNull(content.id), stage) { state ->
            state.status = ContentProcessingStatus.PROCESSING
            state.attemptCount += 1
            state.retryAt = null
            state.lastError = null
        }
    }

    @Transactional
    fun pending(contentId: Long, stage: ContentProcessingStage) = update(contentId, stage) { state ->
        if (state.status != ContentProcessingStatus.READY) {
            state.status = ContentProcessingStatus.PENDING
            state.retryAt = null
            state.lastError = null
        }
    }

    @Transactional
    fun ready(contentId: Long, stage: ContentProcessingStage) = update(contentId, stage) { state ->
        state.status = ContentProcessingStatus.READY
        state.retryAt = null
        state.lastError = null
    }

    @Transactional
    fun retry(contents: Collection<Content>, stage: ContentProcessingStage, retryAt: LocalDateTime?, error: Throwable) = contents.forEach { content ->
        update(requireNotNull(content.id), stage) { state ->
            state.lastError = error.message?.take(MAX_ERROR_LENGTH) ?: error.javaClass.simpleName
            if (state.attemptCount >= MAX_ATTEMPTS) {
                state.status = ContentProcessingStatus.FAILED
                state.retryAt = null
            } else {
                state.status = ContentProcessingStatus.RETRY_AT
                state.retryAt = retryAt ?: LocalDateTime.now().plusMinutes(DEFAULT_RETRY_MINUTES)
            }
        }
    }

    @Transactional
    fun failed(contentId: Long, stage: ContentProcessingStage, error: Throwable) = update(contentId, stage) { state ->
        state.status = ContentProcessingStatus.FAILED
        state.retryAt = null
        state.lastError = error.message?.take(MAX_ERROR_LENGTH) ?: error.javaClass.simpleName
    }

    private fun update(contentId: Long, stage: ContentProcessingStage, mutate: (ContentProcessingState) -> Unit) {
        val state = repository.findByContentIdAndStage(contentId, stage)
            ?: ContentProcessingState(contentId = contentId, stage = stage)
        mutate(state)
        state.updatedAt = LocalDateTime.now()
        repository.save(state)
    }

    companion object {
        private const val DEFAULT_RETRY_MINUTES = 15L
        private const val MAX_ATTEMPTS = 3
        private const val MAX_ERROR_LENGTH = 1000
    }
}
