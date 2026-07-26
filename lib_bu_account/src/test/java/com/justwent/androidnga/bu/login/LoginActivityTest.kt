package com.justwent.androidnga.bu.login

import org.junit.Assert.assertEquals
import org.junit.Test
import sp.phone.common.User

class LoginActivityTest {
    @Test
    fun accountSelectionResolvesStableIdAgainstCurrentList() {
        val intendedAccount = User("42", "reader", "session-42")
        val otherAccount = User("7", "other", "session-7")
        val updatedUsers = listOf(otherAccount, intendedAccount)

        assertEquals(1, findAccountIndex(updatedUsers, intendedAccount.userId))
        assertEquals(-1, findAccountIndex(listOf(otherAccount), intendedAccount.userId))
    }
}
