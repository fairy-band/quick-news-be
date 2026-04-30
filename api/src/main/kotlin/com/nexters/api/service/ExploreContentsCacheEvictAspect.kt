package com.nexters.api.service

import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Aspect
@Component
class ExploreContentsCacheEvictAspect {
    @Pointcut(
        "execution(* com.nexters.external.service.ExposureContentService.createExposureContentFromSummary(..)) || " +
            "execution(* com.nexters.external.service.ExposureContentService.setActiveSummaryAsExposureContent(..)) || " +
            "execution(* com.nexters.external.service.ExposureContentService.updateExposureContent(..)) || " +
            "execution(* com.nexters.external.service.ExposureContentService.deleteExposureContent(..)) || " +
            "execution(* com.nexters.external.service.ExposureContentService.createOrUpdateExposureContent(..))",
    )
    fun exposureContentChange() = Unit

    @AfterReturning("exposureContentChange()")
    fun evictAfterSuccessfulExposureContentChange() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        ExploreContentsCache.evict()
                    }
                },
            )
        } else {
            ExploreContentsCache.evict()
        }
    }
}
