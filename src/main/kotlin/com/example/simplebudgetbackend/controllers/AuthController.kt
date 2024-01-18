package com.example.simplebudgetbackend.controllers

import com.example.simplebudgetbackend.commbank_client.Account
import com.example.simplebudgetbackend.services.UserSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStream
import java.security.Security
import javax.crypto.Cipher

@RestController
class AuthController(@Autowired private val userSession: UserSessionService) {
    private val KEY_DIR = "C:\\Users\\85751\\Desktop\\Projects\\SimpleBudgetBackend\\crypt"

    @CrossOrigin
    @GetMapping("/auth/pubkey")
    suspend fun getPublicKey(): String = withContext(Dispatchers.IO) {
        FileInputStream("${KEY_DIR}\\cyrpt.pub").readAllBytes()
    }.decodeToString()

    @CrossOrigin
    @PostMapping("/auth/login")
    suspend fun login(@RequestParam userId: String, body: InputStream): List<Account> {
        val decrypted = decrypt(withContext(Dispatchers.IO) {
            body.readAllBytes()
        }).split(" ")
        userSession.instantiateClient(userId, decrypted[0], decrypted[1])
        return userSession.getClient(userId).getAccounts()
    }

    suspend fun decrypt(bytes: ByteArray): String {
        Security.addProvider(BouncyCastleProvider())
        val pp = PEMParser(BufferedReader(withContext(Dispatchers.IO) {
            FileReader("${KEY_DIR}\\cyrpt")
        }))
        val pemKeyPair = pp.readObject() as PEMKeyPair
        val keyPair = JcaPEMKeyConverter().getKeyPair(pemKeyPair)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(bytes).decodeToString()
    }
}