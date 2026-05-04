package com.nexters.api.service

import com.nexters.api.util.LocalCache
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Aspect
@Component
class ExploreContentsCacheEvictAspect {
    @AfterReturning("@annotation(com.nexters.external.annotation.ExposureContentChanged)")
    fun evictAfterSuccessfulExposureContentChange() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        LocalCache.deleteByPrefix(EXPLORE_CONTENTS_CACHE_KEY_PREFIX)
                    }
                },
            )
        } else {
            LocalCache.deleteByPrefix(EXPLORE_CONTENTS_CACHE_KEY_PREFIX)
        }
    }

    companion object {
        private const val EXPLORE_CONTENTS_CACHE_KEY_PREFIX = "exposure:contents:"
    }
}
