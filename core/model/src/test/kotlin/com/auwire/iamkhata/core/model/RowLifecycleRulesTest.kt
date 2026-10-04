package com.auwire.iamkhata.core.model

import org.junit.Test

class RowLifecycleRulesTest {
    @Test
    fun draftCanRemainDraftOrFinalize() {
        RowLifecycleRules.requireEditableTransition(RowStatus.DRAFT, RowStatus.DRAFT)
        RowLifecycleRules.requireEditableTransition(RowStatus.DRAFT, RowStatus.FINAL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun finalCannotReturnToDraft() {
        RowLifecycleRules.requireEditableTransition(RowStatus.FINAL, RowStatus.DRAFT)
    }

    @Test(expected = IllegalStateException::class)
    fun voidRowsCannotBeEdited() {
        RowLifecycleRules.requireEditableTransition(RowStatus.VOID, RowStatus.FINAL)
    }
}
