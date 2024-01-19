package com.example.simplebudgetbackend.services

import com.example.simplebudgetbackend.commbank_client.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TransactionService(@Autowired private val sessionService: UserSessionService) {
    fun getTransactions(userId: String, accountNum: String, lastTransnID: String?): Flow<Transaction> = flow {
        val client = sessionService.getClient(userId)
        val accountId = client.getAccounts().filter { it.accountNumber == accountNum }[0].id
        val transactions: ArrayList<Transaction> = ArrayList()

        if (lastTransnID != null) {
            for (page in 1..100) {
                val currTransactions = client.getTransactions(accountId, page)
                val index = currTransactions.indexOf(currTransactions.find {
                    it.id == lastTransnID
                })
                if (index != -1) {
                    transactions.addAll(currTransactions.subList(0, index))
                    break
                }
                transactions.addAll(currTransactions)
            }
            transactions.reversed().forEach {
                emit(it)
                delay(1000)
            }
        } else {
            // Do not emit past transactions if prev transn ID not specified
            transactions.addAll(client.getTransactions(accountId))
        }

        while (true) {
            val transn = client.getTransactions(accountId)
            val lastId = transactions[0].id

            if (transn[0].id != lastId) {
                val index = transn.indexOf(transn.find {
                    it.id == lastId
                })
                transn.subList(0, index).reversed().forEach {
                    emit(it)
                    delay(1000)
                }
                transactions.addAll(0, transn.subList(0, index))
            }
            delay(5000)
        }
    }
}