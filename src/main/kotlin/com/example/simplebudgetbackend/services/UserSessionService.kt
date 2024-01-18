package com.example.simplebudgetbackend.services

import com.example.simplebudgetbackend.commbank_client.CommBankClient
import org.springframework.stereotype.Service
import org.springframework.web.context.annotation.ApplicationScope

@ApplicationScope
@Service
class UserSessionService {
    private val clients: HashMap<String, CommBankClient> = HashMap()

    suspend fun instantiateClient(userId: String, username: String, password: String){
        clients[userId] = CommBankClient(username, password)
    }

    fun getClient(userId: String): CommBankClient = clients[userId] ?: throw Exception("No userID with client found")
}