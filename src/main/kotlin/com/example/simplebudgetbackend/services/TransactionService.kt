package com.example.simplebudgetbackend.services

import com.example.simplebudgetbackend.commbank_client.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class TransactionService(@Autowired private val sessionService: UserSessionService) {
    fun getTransactions(userId: String, accountNum: String, prevIdArray: List<String>?): Flow<Transaction> = flow {
        val client = sessionService.getClient(userId)
        val accountId = client.getAccounts().filter { it.accountNumber == accountNum }[0].id
        val transactions: ArrayList<Transaction> = ArrayList()

        if (prevIdArray != null) {
            var page = 1
            var breakFlag = false
            var lastTransaction: Transaction? = null
            while (true) {
                val currTransactions = client.getTransactions(accountId, page)
                if (page == 1) {
                    lastTransaction = currTransactions[0]
                }

                for (transaction in currTransactions) {
                    if (transaction.created < LocalDateTime.now().atZone(ZoneId.of("Australia/Sydney")).minusDays(10)) {
                        breakFlag = true
                        break
                    }
                    if (!prevIdArray.contains(transaction.id)) {
                        transactions.add(transaction)
                    }
                }
                if (breakFlag) break
                page++
            }
            transactions.reversed().forEach {
                emit(it)
                delay(1000)
            }
            // Add latest transaction to mark most current received transaction
            if (lastTransaction != null) {
                transactions.add(0, lastTransaction)
            } else {
                throw Error("Unable to fetch transactions from Commbank")
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