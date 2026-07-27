package com.vicovpn.client.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ServerStore(context: Context) {

    companion object {
        private const val PREFERENCES_NAME = "saved_servers"
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
    }

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun getServers(): List<SavedServer> {
        val json = preferences.getString(KEY_SERVERS, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(json)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    add(
                        SavedServer(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            rawLink = item.getString("rawLink"),
                            protocol = item.getString("protocol"),
                            address = item.getString("address"),
                            port = item.getInt("port"),
                            transport = item.optString(
                                "transport",
                                "TCP"
                            ),
                            createdAt = item.optLong(
                                "createdAt",
                                System.currentTimeMillis()
                            ),
                            origin = runCatching {
                                ServerOrigin.valueOf(
                                    item.optString(
                                        "origin",
                                        ServerOrigin.MANUAL.name
                                    )
                                )
                            }.getOrDefault(ServerOrigin.MANUAL),
                            latencyMs = item.optLong("latencyMs", 0),
                            exitIp = item.optString("exitIp", ""),
                            countryCode = item.optString(
                                "countryCode",
                                ""
                            ),
                            countryName = item.optString(
                                "countryName",
                                ""
                            ),
                            city = item.optString("city", ""),
                            isp = item.optString("isp", ""),
                            asn = item.optString("asn", ""),
                            lastTestedAt = item.optLong(
                                "lastTestedAt",
                                0
                            )
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun priorityMode():
        ConnectionPriorityMode {
        return ConnectionPrioritySettings(
            appContext
        ).getMode()
    }

    fun getEligibleAutomaticServers(
        mode: ConnectionPriorityMode =
            priorityMode()
    ): List<SavedServer> {
        if (
            !mode.automaticSelection
        ) {
            return emptyList()
        }

        val servers =
            getServers()

        val vip =
            if (mode.allowsVip) {
                servers.filter {
                    it.origin ==
                        ServerOrigin
                            .VIP_SUBSCRIPTION
                }
            } else {
                emptyList()
            }

        val free =
            if (mode.allowsFree) {
                servers.filter {
                    it.origin ==
                        ServerOrigin
                            .FREE_SUBSCRIPTION
                }
            } else {
                emptyList()
            }

        return when (mode) {
            ConnectionPriorityMode
                .VIP_ONLY ->
                sortAutomatic(
                    vip
                )

            ConnectionPriorityMode
                .VIP_AND_FREE ->
                sortAutomatic(
                    vip
                ) +
                    sortAutomatic(
                        free
                    )

            ConnectionPriorityMode
                .FREE_ONLY ->
                sortAutomatic(
                    free
                )

            ConnectionPriorityMode
                .NONE ->
                emptyList()
        }
    }

    fun getBestAutomaticServer(
        mode: ConnectionPriorityMode =
            priorityMode()
    ): SavedServer? {
        return getEligibleAutomaticServers(
            mode
        ).firstOrNull()
    }

    fun activateBestForPriority(
        mode: ConnectionPriorityMode =
            priorityMode()
    ): SavedServer? {
        val best =
            getBestAutomaticServer(
                mode
            ) ?: return null

        setActiveServer(
            best.id
        )

        return best
    }

    @Synchronized
    fun updateVipRouteMeasurement(
        measured: SavedServer
    ) {
        if (
            measured.origin !=
                ServerOrigin
                    .VIP_SUBSCRIPTION
        ) {
            return
        }

        val updated =
            getServers().map {
                    server ->
                if (
                    server.origin ==
                        ServerOrigin
                            .VIP_SUBSCRIPTION &&
                    server.rawLink ==
                        measured.rawLink
                ) {
                    server.copy(
                        latencyMs =
                            measured.latencyMs,
                        lastTestedAt =
                            measured.lastTestedAt
                    )
                } else {
                    server
                }
            }

        saveServers(
            updated
        )
    }

    private fun sortAutomatic(
        servers: List<SavedServer>
    ): List<SavedServer> {
        return servers.sortedWith(
            compareBy<SavedServer> {
                if (
                    it.latencyMs > 0
                ) {
                    it.latencyMs
                } else {
                    Long.MAX_VALUE
                }
            }.thenByDescending {
                it.lastTestedAt
            }.thenBy {
                it.name
            }
        )
    }

    fun getActiveServerId(): String? {
        return preferences.getString(
            KEY_ACTIVE_SERVER_ID,
            null
        )
    }

    fun getActiveServer(): SavedServer? {
        val activeId = getActiveServerId()
            ?: return null

        return getServers().firstOrNull {
            it.id == activeId
        }
    }

    @Synchronized
    fun addServer(
        name: String,
        rawLink: String,
        protocol: String,
        address: String,
        port: Int,
        transport: String
    ): SavedServer {
        val currentServers = getServers().toMutableList()

        val existing = currentServers.firstOrNull {
            it.rawLink == rawLink
        }

        if (existing != null) {
            setActiveServer(existing.id)
            return existing
        }

        val server = SavedServer(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank {
                "$protocol server"
            },
            rawLink = rawLink,
            protocol = protocol,
            address = address,
            port = port,
            transport = transport,
            createdAt = System.currentTimeMillis(),
            origin = ServerOrigin.MANUAL
        )

        currentServers.add(server)
        saveServers(currentServers)

        if (getActiveServerId() == null) {
            setActiveServer(server.id)
        }

        return server
    }

    fun setActiveServer(serverId: String) {
        val serverExists = getServers().any {
            it.id == serverId
        }

        if (!serverExists) return

        preferences.edit()
            .putString(
                KEY_ACTIVE_SERVER_ID,
                serverId
            )
            .apply()
    }

    @Synchronized
    fun renameServer(
        serverId: String,
        newName: String
    ) {
        if (newName.isBlank()) return

        val updated = getServers().map { server ->
            if (
                server.id == serverId &&
                server.origin == ServerOrigin.MANUAL
            ) {
                server.copy(name = newName.trim())
            } else {
                server
            }
        }

        saveServers(updated)
    }

    @Synchronized
    fun deleteServer(serverId: String) {
        val target = getServers().firstOrNull {
            it.id == serverId
        } ?: return

        if (target.origin != ServerOrigin.MANUAL) {
            return
        }

        val remaining = getServers().filterNot {
            it.id == serverId
        }

        saveServers(remaining)
        repairActiveServer(remaining)
    }

    /**
     * Replaces all old free-subscription servers in one transaction.
     * Manual servers are always preserved.
     *
     * Returns false when the verified list is empty, leaving old free servers
     * untouched.
     */
    /**
     * Adds newly verified free servers without deleting older working
     * servers. This lets the app save the very first working route
     * immediately while discovery continues in the background.
     */
    @Synchronized
    fun mergeFreeServers(
        verifiedServers: List<SavedServer>,
        activateBestWhenNeeded: Boolean = true
    ): Boolean {
        val incoming =
            verifiedServers
                .filter {
                    it.origin ==
                        ServerOrigin.FREE_SUBSCRIPTION
                }

        if (incoming.isEmpty()) {
            return false
        }

        val current =
            getServers()

        /*
         * Preserve manual and VIP routes. A free-route refresh must never
         * delete a paid subscription imported from the profile screen.
         */
        val preserved =
            current.filter {
                it.origin !=
                    ServerOrigin.FREE_SUBSCRIPTION
            }

        val oldFreeByRaw =
            current.filter {
                it.origin ==
                    ServerOrigin.FREE_SUBSCRIPTION
            }.associateBy {
                it.rawLink
            }.toMutableMap()

        incoming.forEach { candidate ->
            val old =
                oldFreeByRaw[
                    candidate.rawLink
                ]

            oldFreeByRaw[
                candidate.rawLink
            ] =
                if (old == null) {
                    candidate
                } else {
                    candidate.copy(
                        id = old.id,
                        createdAt =
                            minOf(
                                old.createdAt,
                                candidate.createdAt
                            )
                    )
                }
        }

        val free =
            oldFreeByRaw.values
                .distinctBy {
                    it.rawLink
                }
                .sortedWith(
                    compareBy<SavedServer> {
                        if (
                            it.latencyMs > 0
                        ) {
                            it.latencyMs
                        } else {
                            Long.MAX_VALUE
                        }
                    }.thenBy {
                        it.name
                    }
                )

        val merged =
            preserved + free

        saveServers(
            merged
        )

        val activeId =
            getActiveServerId()

        val activeStillExists =
            activeId != null &&
                merged.any {
                    it.id == activeId
                }

        if (
            activateBestWhenNeeded &&
            !activeStillExists
        ) {
            preferences.edit()
                .putString(
                    KEY_ACTIVE_SERVER_ID,
                    preserved.firstOrNull()
                        ?.id
                        ?: free.first().id
                )
                .apply()
        }

        return true
    }

    fun getBestFreeServer():
        SavedServer? {
        return getServers()
            .asSequence()
            .filter {
                it.origin ==
                    ServerOrigin.FREE_SUBSCRIPTION
            }
            .sortedWith(
                compareBy<SavedServer> {
                    if (
                        it.latencyMs > 0
                    ) {
                        it.latencyMs
                    } else {
                        Long.MAX_VALUE
                    }
                }.thenByDescending {
                    it.lastTestedAt
                }
            )
            .firstOrNull()
    }

    @Synchronized
    fun activateBestFreeServer():
        SavedServer? {
        return activateBestForPriority()
    }




    /**
     * Returns the best untried free server. Used for automatic failover when
     * the active route stops unexpectedly.
     */
    fun getNextFreeServer(
        excludedRawLinks: Set<String>
    ): SavedServer? {
        return getEligibleAutomaticServers()
            .firstOrNull {
                it.rawLink !in
                    excludedRawLinks
            }
    }




    /**
     * Manual disconnect remains a disconnect, but the next Connect tap uses
     * the next verified free route instead of always retrying the same one.
     */
    @Synchronized
    fun rotateActiveFreeServer():
        SavedServer? {
        val mode =
            priorityMode()

        if (
            !mode.automaticSelection
        ) {
            return null
        }

        val eligible =
            getEligibleAutomaticServers(
                mode
            )

        if (eligible.isEmpty()) {
            return null
        }

        val active =
            getActiveServer()

        val currentIndex =
            eligible.indexOfFirst {
                it.id ==
                    active?.id
            }

        val next =
            if (
                currentIndex < 0 ||
                currentIndex ==
                    eligible.lastIndex
            ) {
                eligible.first()
            } else {
                eligible[
                    currentIndex + 1
                ]
            }

        setActiveServer(
            next.id
        )

        return next
    }




    @Synchronized
    fun replaceFreeServers(
        verifiedServers: List<SavedServer>
    ): Boolean {
        if (verifiedServers.isEmpty()) {
            return false
        }

        val oldServers =
            getServers()

        val preservedServers =
            oldServers.filter {
                it.origin !=
                    ServerOrigin.FREE_SUBSCRIPTION
            }

        val oldActiveId =
            getActiveServerId()

        val oldActive =
            oldServers.firstOrNull {
                it.id == oldActiveId
            }

        val deduplicatedFree =
            verifiedServers
                .asSequence()
                .filter {
                    it.origin ==
                        ServerOrigin.FREE_SUBSCRIPTION
                }
                .distinctBy {
                    it.rawLink
                }
                .sortedWith(
                    compareBy<SavedServer> {
                        if (
                            it.latencyMs > 0
                        ) {
                            it.latencyMs
                        } else {
                            Long.MAX_VALUE
                        }
                    }.thenBy {
                        it.name
                    }
                )
                .toList()

        if (deduplicatedFree.isEmpty()) {
            return false
        }

        val merged =
            preservedServers +
                deduplicatedFree

        saveServers(
            merged
        )

        val nextActiveId =
            when {
                oldActive != null &&
                    preservedServers.any {
                        it.id == oldActive.id
                    } -> {
                    oldActive.id
                }

                oldActive?.origin ==
                    ServerOrigin
                        .FREE_SUBSCRIPTION -> {
                    deduplicatedFree
                        .firstOrNull {
                            it.rawLink ==
                                oldActive.rawLink
                        }
                        ?.id
                        ?: deduplicatedFree
                            .first()
                            .id
                }

                else -> {
                    preservedServers
                        .firstOrNull()
                        ?.id
                        ?: deduplicatedFree
                            .first()
                            .id
                }
            }

        preferences.edit()
            .putString(
                KEY_ACTIVE_SERVER_ID,
                nextActiveId
            )
            .apply()

        return true
    }

    @Synchronized
    fun updateConnectionMetadata(
        rawLink: String,
        exitIp: String,
        countryCode: String,
        countryName: String,
        city: String,
        isp: String,
        asn: String
    ) {
        if (rawLink.isBlank()) {
            return
        }

        val updated =
            getServers().map { server ->
                if (server.rawLink == rawLink) {
                    server.copy(
                        exitIp = exitIp,
                        countryCode = countryCode,
                        countryName = countryName,
                        city = city,
                        isp = isp,
                        asn = asn
                    )
                } else {
                    server
                }
            }

        saveServers(updated)
    }

    @Synchronized
    fun mergeVipServers(
        vipServers: List<SavedServer>
    ): Boolean {
        val incoming =
            vipServers
                .filter {
                    it.origin ==
                        ServerOrigin.VIP_SUBSCRIPTION
                }
                .distinctBy {
                    it.rawLink
                }

        if (incoming.isEmpty()) {
            return false
        }

        val current =
            getServers()

        val preserved =
            current.filter {
                it.origin !=
                    ServerOrigin.VIP_SUBSCRIPTION
            }

        val oldByRaw =
            current.filter {
                it.origin ==
                    ServerOrigin.VIP_SUBSCRIPTION
            }.associateBy {
                it.rawLink
            }

        val vip =
            incoming.map { candidate ->
                val old =
                    oldByRaw[
                        candidate.rawLink
                    ]

                if (old == null) {
                    candidate
                } else {
                    candidate.copy(
                        id = old.id,
                        createdAt =
                            minOf(
                                old.createdAt,
                                candidate.createdAt
                            )
                    )
                }
            }

        saveServers(
            preserved + vip
        )

        if (
            priorityMode()
                .allowsVip
        ) {
            preferences.edit()
                .putString(
                    KEY_ACTIVE_SERVER_ID,
                    vip.first().id
                )
                .apply()
        }

        return true
    }

    @Synchronized
    fun removeVipServers() {
        val remaining =
            getServers()
                .filter {
                    it.origin !=
                        ServerOrigin.VIP_SUBSCRIPTION
                }

        saveServers(
            remaining
        )
        repairActiveServer(
            remaining
        )
    }

    @Synchronized
    fun clearAll() {
        preferences.edit()
            .remove(KEY_SERVERS)
            .remove(KEY_ACTIVE_SERVER_ID)
            .apply()
    }

    private fun repairActiveServer(
        servers: List<SavedServer>
    ) {
        val activeId = getActiveServerId()

        if (
            activeId != null &&
            servers.any { it.id == activeId }
        ) {
            return
        }

        val next = servers.firstOrNull()?.id

        preferences.edit().apply {
            if (next == null) {
                remove(KEY_ACTIVE_SERVER_ID)
            } else {
                putString(KEY_ACTIVE_SERVER_ID, next)
            }
        }.apply()
    }

    private fun saveServers(
        servers: List<SavedServer>
    ) {
        val array = JSONArray()

        servers.forEach { server ->
            array.put(
                JSONObject().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("rawLink", server.rawLink)
                    put("protocol", server.protocol)
                    put("address", server.address)
                    put("port", server.port)
                    put("transport", server.transport)
                    put("createdAt", server.createdAt)
                    put("origin", server.origin.name)
                    put("latencyMs", server.latencyMs)
                    put("exitIp", server.exitIp)
                    put("countryCode", server.countryCode)
                    put("countryName", server.countryName)
                    put("city", server.city)
                    put("isp", server.isp)
                    put("asn", server.asn)
                    put("lastTestedAt", server.lastTestedAt)
                }
            )
        }

        preferences.edit()
            .putString(
                KEY_SERVERS,
                array.toString()
            )
            .commit()
    }
}
