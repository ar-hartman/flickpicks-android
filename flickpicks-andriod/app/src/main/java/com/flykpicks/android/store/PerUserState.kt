package com.flykpicks.android.store

/**
 * In-memory state that belongs to the signed-in account. Every implementation is bound into a
 * Hilt set (`di/AppModule`) and reset whenever the session changes hands: on sign-out, when the
 * network layer ends the session, and again on sign-in. iOS resets none of its stores, so the
 * next account sees the previous one's data (ios-gaps.md G21).
 */
interface PerUserState {
    /** Drop everything held for the current account, including results still in flight. */
    fun reset()
}
