package com.vicovpn.client.onboarding

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.subscription.FreeServerUpdateStore
import com.vicovpn.client.subscription.SubscriptionImporter
import com.vicovpn.client.subscription.SubscriptionSettings
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object OnboardingDiscoveryCoordinator {

    enum class State {
        IDLE,
        PREPARING,
        READY,
        FAILED
    }

    fun interface Listener {
        fun onStateChanged(
            state: State
        )
    }

    private val executor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val running =
        AtomicBoolean(false)

    private val listeners =
        CopyOnWriteArrayList<
            WeakReference<Listener>
            >()

    @Volatile
    private var currentState =
        State.IDLE

    fun state(): State =
        currentState

    fun addListener(
        listener: Listener
    ) {
        listeners +=
            WeakReference(listener)

        listener.onStateChanged(
            currentState
        )
    }

    fun removeListener(
        listener: Listener
    ) {
        listeners.removeAll {
            it.get() == null ||
                it.get() === listener
        }
    }

    fun start(
        context: Context
    ) {
        if (
            currentState ==
                State.READY
        ) {
            notifyListeners()
            return
        }

        if (
            !running.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        updateState(
            State.PREPARING
        )

        val appContext =
            context.applicationContext

        executor.execute {
            try {
                val result =
                    SubscriptionImporter(
                        registryUrl =
                            SubscriptionSettings(
                                appContext
                            ).getRegistryUrl(),
                        maxSourceCount = 50,
                        maxConfigs = 5_000,
                        downloadThreads = 4
                    ).run(
                        onProgress = {
                            // Intentionally hidden during onboarding.
                        }
                    ).getOrThrow()

                val candidates =
                    result.configs
                        .asSequence()
                        .map {
                            it.trim()
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .filter {
                            rawLink ->
                            runCatching {
                                ShareLinkParser.parse(
                                    rawLink
                                )
                            }.isSuccess
                        }
                        .distinct()
                        .toList()

                require(
                    candidates.isNotEmpty()
                ) {
                    "No usable routes"
                }

                FreeServerUpdateStore(
                    appContext
                ).saveCandidates(
                    candidates
                )

                updateState(
                    State.READY
                )
            } catch (
                ignored: Throwable
            ) {
                updateState(
                    State.FAILED
                )
            } finally {
                running.set(false)
            }
        }
    }

    private fun updateState(
        state: State
    ) {
        currentState = state
        notifyListeners()
    }

    private fun notifyListeners() {
        mainHandler.post {
            listeners.removeAll {
                it.get() == null
            }

            listeners.forEach {
                reference ->
                reference.get()
                    ?.onStateChanged(
                        currentState
                    )
            }
        }
    }
}
