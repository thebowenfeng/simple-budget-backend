package com.example.simplebudgetbackend.commbank_client
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CommBankClient private constructor(private val username: String, private val password: String) {
    private val httpClient = HttpClient(CIO) {
        BrowserUserAgent()
        install(HttpCookies)
    }
    private val paging = HashMap<Int, String>()

    companion object {
        suspend operator fun invoke(username: String, password: String): CommBankClient {
            val instance = CommBankClient(username, password)
            instance.login()
            return instance
        }
    }

    private fun parseForm(inputElems: Elements): Map<String, String> {
        val filtered = inputElems.filter { element ->
            element.attr("value") != ""
        }
        return filtered.associateBy({it.attr("name")}, {it.attr("value")})
    }

    private suspend fun login() {
        val initResp = httpClient.get("https://www.my.commbank.com.au/netbank/Logon/Logon.aspx")
        val initDoc = Jsoup.parse(initResp.bodyAsText())
        val initPayload = parseForm(initDoc.getElementById("form1").select("input")).toMutableMap()
        initPayload["JS"] = "E"
        initPayload["txtMyClientNumber\$field"] = username
        initPayload["txtMyPassword\$field"] = password

        val legacyRedirectResp = httpClient.submitForm(
            url = "https://www.my.commbank.com.au/netbank/Logon/Logon.aspx",
            formParameters = parameters {
                initPayload.forEach {
                    append(it.key, it.value)
                }
            }
        )

        if (legacyRedirectResp.status.value != 307) {
            throw Exception("Unable to login")
        }

        val entryRedirectResp = legacyRedirectResp.headers["Location"]?.let { it ->
            httpClient.submitForm(
                url = it,
                formParameters = parameters {
                    initPayload.forEach { it2 ->
                        append(it2.key, it2.value)
                    }
                }
            )
        }

        val oidc = entryRedirectResp?.headers?.get("Location")?.let { httpClient.get(it) }
        val oidcDoc = oidc?.bodyAsText().let {
            Jsoup.parse(it)
        }
        val oidcForm = oidcDoc.select("form")[0]
        val url = oidcForm.attr("action")
        if (url != "https://www.commbank.com.au/retail/netbank/identity/signin-oidc") throw Exception("Unable to login")
        val oidcPayload = parseForm(oidcForm.select("input"))
        val oidcResp = httpClient.submitForm(
            url = url,
            formParameters = parameters {
                oidcPayload.forEach {
                    append(it.key, it.value)
                }
            }
        )
        val resp = httpClient.get("https://www.commbank.com.au${oidcResp.headers["Location"]}")
        if (resp.request.url.toString() != "https://www.commbank.com.au/retail/netbank/home/") throw Exception("Unable to login")
    }

    suspend fun getAccounts(): List<Account> {
        val res = httpClient.get("https://www.commbank.com.au/retail/netbank/api/home/v1/accounts")
        val mapper = ObjectMapper()
        val rootNode = mapper.readTree(res.bodyAsText())
        return rootNode.get("accounts").map {
            Account(
                accountNumber = it.get("number").asText(),
                id = it.get("link").get("url").asText().replace("/retail/netbank/accounts/?account=", ""),
                name = it.get("displayName").asText(),
                balance = it.get("balance")[0].get("amount").asDouble(),
                funds = it.get("availableFunds")[0].get("amount").asDouble(),
                currency = it.get("balance")[0].get("currency").asText()
            )
        }
    }

    private suspend fun getPagingKey(accountId: String, page: Int): String {
        val mapper = ObjectMapper()
        if (paging.containsKey(page)) return paging[page] as String

        if (paging.isEmpty()) {
            val res = httpClient.get("https://www.commbank.com.au/retail/netbank/accounts/api/transactions?account=${accountId}")
            paging[1] = mapper.readTree(res.bodyAsText()).get("pagingKey").asText()
        }

        val lastEntry = paging.maxBy { it.key }
        for (i in lastEntry.key until page) {
            val res = httpClient.get("https://www.commbank.com.au/retail/netbank/accounts/api/transactions?account=${accountId}&pagingKey=${paging[i]}")
            paging[i + 1] = mapper.readTree(res.bodyAsText()).get("pagingKey").asText()
        }
        return paging[page] as String
    }

    suspend fun getTransactions(accountId: String, page: Int = 1): List<Transaction> {
        val mapper = ObjectMapper()
        val pagingQueryArg = if (page > 1) "&pagingKey=${getPagingKey(accountId, page)}" else ""
        val res = httpClient.get("https://www.commbank.com.au/retail/netbank/accounts/api/transactions?account=${accountId}${pagingQueryArg}")
        val resJson = mapper.readTree(res.bodyAsText())
        paging[page + 1] = resJson.get("pagingKey").asText()

        val pendingTransactions = if (resJson.has("pendingTransactions")) resJson.get("pendingTransactions").map {
            Transaction(
                id = null,
                transactionDetailsRequest = it.get("transactionDetailsRequest").asText(),
                description = it.get("description").asText(),
                created = ZonedDateTime.of(LocalDateTime.parse(it.get("createdDate").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                    ZoneId.of("Australia/Sydney")),
                amount = it.get("amount").asDouble(),
                pending = true
            )
        } else emptyList()
        val transactions = resJson.get("transactions").map {
            Transaction(
                id = it.get("transactionId").asText(),
                transactionDetailsRequest = null,
                description = it.get("description").asText(),
                created = ZonedDateTime.of(LocalDateTime.parse(it.get("createdDate").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                    ZoneId.of("Australia/Sydney")),
                amount = it.get("amount").asDouble(),
                pending = false
            )
        } + pendingTransactions
        transactions.sortedBy {
            it.created
        }
        return transactions
    }
}