package com.example.simplebudgetbackend

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun handle(e: Exception): ResponseEntity<String> = ResponseEntity("${e::class.toString()} : ${e.message}",
        HttpStatus.INTERNAL_SERVER_ERROR)
}