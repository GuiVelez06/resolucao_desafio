package com.example.selection

import com.netflix.graphql.dgs.exceptions.DgsBadRequestException
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.graphql.dgs.exceptions.DgsException
import com.netflix.graphql.types.errors.ErrorType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

const val PHOTOS_PER_MODULE = 2000
const val MAX_PAGE_LIMIT = 200

data class PlatformPhotoPage(
    val items: List<Photo>, val offset: Int, val limit: Int,
    val total: Int, val hasNextPage: Boolean,
)

class RestApiUnavailableException(message: String, cause: Exception) :
    DgsException(message, cause, ErrorType.UNAVAILABLE)

@Service
class PlatformPhotoService(private val restApi: RestApiClient) {

    /**
     * Trata as fotos de todos os módulos da plataforma como uma sequência única.
     * Como cada módulo tem [PHOTOS_PER_MODULE] fotos, a posição global indica o módulo
     * (divisão) e a posição dentro dele (resto), então só as fotos da página são buscadas:
     * 1 chamada para os módulos e no máximo 2 para as fotos, já que limit <= 200.
     */
    fun photos(platformId: Long, offset: Int, limit: Int): PlatformPhotoPage {
        if (offset < 0) throw DgsBadRequestException("offset deve ser >= 0")
        if (limit !in 1..MAX_PAGE_LIMIT) throw DgsBadRequestException("limit deve estar entre 1 e $MAX_PAGE_LIMIT")
    /* Abaixo está o tratamento dos parâmetros inválidos, plataforma inexistente e falhas da REST */
        val modules = try {
            restApi.modules(platformId)
        } catch (e: HttpClientErrorException.NotFound) {
            throw DgsEntityNotFoundException("Plataforma $platformId não encontrada")
        } catch (e: RestClientException) {
            throw RestApiUnavailableException("Falha ao consultar os módulos na API REST", e)
        }

        val total = modules.size * PHOTOS_PER_MODULE
        val end = offset + minOf(limit, maxOf(total - offset, 0))
        val items = mutableListOf<Photo>()
        var position = offset

        while (position < end) 
        {
            val module = modules[position / PHOTOS_PER_MODULE] /* qual modulo */
            val offsetInModule = position % PHOTOS_PER_MODULE /* onde dentro do módulo */
            val count = minOf(end - position, PHOTOS_PER_MODULE - offsetInModule) /* limite */
            val page = try {
                restApi.photos(module.id, offsetInModule, count)
            } catch (e: RestClientException) {
                throw RestApiUnavailableException("Falha ao consultar as fotos do módulo ${module.id} na API REST", e)
            }
            if (page.items.isEmpty()) break /* pergunta se a REST devolveu uma lista vazia*/
            items += page.items /* Adiciona as fotos recebidas no fim da lista items */
            position += page.items.size /* Avança o "cursor" pelo número de fotos que acabaram de chegar */
        }

        return PlatformPhotoPage(items, offset, limit, total, hasNextPage = offset + items.size < total)
    }
}
