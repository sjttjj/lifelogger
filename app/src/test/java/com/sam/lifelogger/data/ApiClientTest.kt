package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientTest {

    @Test
    fun normalizeBaseUrlStripsUploadPathAndArbitraryTaskQuery() {
        val uploadUrl = "http://100.78.20.28:8000/?task=transcribing to K and seeing if you have questions"

        val baseUrl = ApiClient.normalizeBaseUrl(uploadUrl)

        assertEquals("http://100.78.20.28:8000", baseUrl)
    }
}
