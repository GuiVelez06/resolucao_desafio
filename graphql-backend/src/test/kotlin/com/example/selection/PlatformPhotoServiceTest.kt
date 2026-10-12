package com.example.selection

import com.netflix.graphql.dgs.exceptions.DgsBadRequestException
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlatformPhotoServiceTest {
    private val rest = FakeRestApi()
    private val service = PlatformPhotoService(RestApiClient(rest.baseUrl))

    @AfterEach
    fun stop() = rest.close()

    @Test
    fun `primeira pagina vem do primeiro modulo`() {
        val page = service.photos(1, 0, 50)

        assertEquals((1L..50L).toList(), page.items.map { it.id })
        assertTrue(page.items.all { it.moduleId == 1L })
        assertEquals(80_000, page.total)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `pagina que atravessa a fronteira entre modulos`() {
        val page = service.photos(1, 1990, 30)

        assertEquals((1991L..2020L).toList(), page.items.map { it.id })
        assertEquals(10, page.items.count { it.moduleId == 1L })
        assertEquals(20, page.items.count { it.moduleId == 2L })
        assertEquals(
            listOf("/platforms/1/modules", "/modules/1/photos?offset=1990&limit=10", "/modules/2/photos?offset=0&limit=20"),
            rest.requests,
        )
    }

    @Test
    fun `ultima pagina da plataforma vem incompleta`() {
        val page = service.photos(1, 79_990, 50)

        assertEquals((79_991L..80_000L).toList(), page.items.map { it.id })
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `offset alem do total retorna pagina vazia sem buscar fotos`() {
        val page = service.photos(1, 80_000, 50)

        assertTrue(page.items.isEmpty())
        assertEquals(80_000, page.offset)
        assertFalse(page.hasNextPage)
        assertEquals(listOf("/platforms/1/modules"), rest.requests)
    }

    @Test
    fun `usa os modulos da plataforma informada`() {
        val page = service.photos(2, 0, 1)

        assertEquals(41L, page.items.single().moduleId)
    }

    @Test
    fun `parametros invalidos sao rejeitados sem chamar a REST`() {
        assertThrows(DgsBadRequestException::class.java) { service.photos(1, -1, 50) }
        assertThrows(DgsBadRequestException::class.java) { service.photos(1, 0, 0) }
        assertThrows(DgsBadRequestException::class.java) { service.photos(1, 0, 201) }
        assertTrue(rest.requests.isEmpty())
    }

    @Test
    fun `plataforma inexistente`() {
        assertThrows(DgsEntityNotFoundException::class.java) { service.photos(41, 0, 50) }
    }

    @Test
    fun `falha da REST`() {
        rest.failing = true

        assertThrows(RestApiUnavailableException::class.java) { service.photos(1, 0, 50) }
    }
}
