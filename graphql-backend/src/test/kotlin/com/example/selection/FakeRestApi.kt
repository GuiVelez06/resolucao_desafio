package com.example.selection

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Servidor HTTP que imita a API REST Python e registra as requisições recebidas. */
class FakeRestApi : AutoCloseable 
{
    val requests = CopyOnWriteArrayList<String>()

    @Volatile
    var failing = false

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { handle(it) }
        start()
    }

    val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        requests += exchange.requestURI.toString()
        val (status, json) = if (failing) 500 to """{"error":"Falha simulada"}""" else route(exchange)
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun route(exchange: HttpExchange): Pair<Int, String> {
        val path = exchange.requestURI.path
        Regex("/platforms/(\\d+)/modules").matchEntire(path)?.let { match ->
            val platformId = match.groupValues[1].toInt()
            if (platformId !in 1..PLATFORMS) return NOT_FOUND
            val ids = (1..MODULES_PER_PLATFORM).map { (platformId - 1) * MODULES_PER_PLATFORM + it }
            return 200 to ids.joinToString(",", "[", "]") { """{"id":$it,"name":"Módulo $it"}""" }
        }
        Regex("/modules/(\\d+)/photos").matchEntire(path)?.let { match ->
            val moduleId = match.groupValues[1].toInt()
            val params = exchange.requestURI.query.orEmpty().split("&").filter { "=" in it }
                .associate { it.substringBefore("=") to it.substringAfter("=").toInt() }
            val offset = params["offset"] ?: 0
            val limit = params["limit"] ?: 50
            val end = minOf(offset + limit, PHOTOS_PER_MODULE)
            val items = (offset until end).joinToString(",", "[", "]") { i ->
                val id = (moduleId - 1) * PHOTOS_PER_MODULE + i + 1
                """{"id":$id,"moduleId":$moduleId,"name":"Foto ${i + 1}","url":"https://example.com/$id"}"""
            }
            return 200 to """{"items":$items,"offset":$offset,"limit":$limit,"total":$PHOTOS_PER_MODULE,"hasMore":${end < PHOTOS_PER_MODULE}}"""
        }
        return NOT_FOUND
    }

    private companion object {
        const val PLATFORMS = 40
        const val MODULES_PER_PLATFORM = 40
        val NOT_FOUND = 404 to """{"error":"Não encontrado"}"""
    }
}
