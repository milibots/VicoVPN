package com.vicovpn.client

import com.vicovpn.client.update.GitHubUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun semanticVersionsAreComparedNumerically() {
        assertEquals(1, GitHubUpdateChecker.compareVersions("v1.10.0", "1.9.9"))
        assertEquals(-1, GitHubUpdateChecker.compareVersions("1.2.3", "v2.0.0"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.2", "1.2.0"))
    }

    @Test
    fun prereleaseSuffixDoesNotBreakComparison() {
        assertEquals(1, GitHubUpdateChecker.compareVersions("v2.1.0-beta.1", "2.0.9"))
    }
}
