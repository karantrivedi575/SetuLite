package com.paysetu.app.domain.security

interface PinAuthorizer {
    fun authorize(): Boolean
}
