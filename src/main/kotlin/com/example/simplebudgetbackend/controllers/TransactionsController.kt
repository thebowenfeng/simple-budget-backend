package com.example.simplebudgetbackend.controllers

import com.example.simplebudgetbackend.commbank_client.Transaction
import com.example.simplebudgetbackend.services.TransactionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TransactionsController(@Autowired private val transactionService: TransactionService) {
    @GetMapping("/")
    suspend fun helloWorld(): String = "Hello world"

    @CrossOrigin
    @GetMapping("/api/transactions", produces=[TEXT_EVENT_STREAM_VALUE])
    fun getTransactions(@RequestParam userId: String, @RequestParam accountId: String, @RequestParam prevTransnIds: String?): Flow<ServerSentEvent<Transaction>>
        = transactionService.getTransactions(userId, accountId, parseIdArray(prevTransnIds)).map {
            ServerSentEvent.builder<Transaction>().event("transaction").data(it).build()
        }

    fun parseIdArray(idArray: String?): List<String>? = idArray?.split(",")
}
