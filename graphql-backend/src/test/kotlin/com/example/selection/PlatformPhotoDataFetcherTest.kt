package com.example.selection

import com.netflix.graphql.dgs.DgsQueryExecutor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class PlatformPhotoDataFetcherTest {
    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Test
    fun `query usa offset e limit padrao`() {
        val page: Map<String, Any> = queryExecutor.executeAndExtractJsonPath(
            "{ platformPhotos(platformId: 1) { offset limit total hasNextPage items { id moduleId } } }",
            "data.platformPhotos",
        )

        assertEquals(0, page["offset"])
        assertEquals(50, page["limit"])
        assertEquals(80_000, page["total"])
        assertEquals(true, page["hasNextPage"])
        assertEquals(50, (page["items"] as List<*>).size)
    }

    @Test
    fun `erros chegam ao cliente com tipo e mensagem`() {
        assertError("{ platformPhotos(platformId: 41) { total } }", "NOT_FOUND", "Plataforma 41 não encontrada")
        assertError("{ platformPhotos(platformId: 1, limit: 500) { total } }", "BAD_REQUEST", "limit deve estar entre 1 e 200")
    }

    private fun assertError(query: String, errorType: String, message: String) {
        val error = queryExecutor.execute(query).errors.single()
        assertEquals(errorType, error.extensions["errorType"])
        assertTrue(error.message.contains(message), error.message)
    }

    companion object {
        private val rest = FakeRestApi()

        @JvmStatic
        @DynamicPropertySource
        fun restApiUrl(registry: DynamicPropertyRegistry) {
            registry.add("rest-api.base-url") { rest.baseUrl }
        }

        @JvmStatic
        @AfterAll
        fun stop() = rest.close()
    }
}
