package com.dhikr.app.feature.tasbih

import com.dhikr.app.core.ai.GeminiResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BenefitsErrorMappingTest {

    @Test
    fun every_kind_maps_to_a_matching_view_error() {
        assertEquals(BenefitsError.NO_KEY, GeminiResult.Kind.NO_KEY.toBenefitsError())
        assertEquals(BenefitsError.NETWORK, GeminiResult.Kind.NETWORK.toBenefitsError())
        assertEquals(BenefitsError.AUTH, GeminiResult.Kind.AUTH.toBenefitsError())
        assertEquals(BenefitsError.RATE_LIMIT, GeminiResult.Kind.RATE_LIMIT.toBenefitsError())
        assertEquals(BenefitsError.BLOCKED, GeminiResult.Kind.BLOCKED.toBenefitsError())
        assertEquals(BenefitsError.MALFORMED, GeminiResult.Kind.MALFORMED.toBenefitsError())
        assertEquals(BenefitsError.UNKNOWN, GeminiResult.Kind.UNKNOWN.toBenefitsError())
    }
}
