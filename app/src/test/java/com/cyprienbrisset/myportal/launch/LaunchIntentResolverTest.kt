package com.cyprienbrisset.fukkatsunop.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchIntentResolverTest {
    @Test fun returnsPackageWhenLaunchable() {
        val resolver = LaunchIntentResolver { pkg -> pkg == "com.netflix" }
        assertEquals("com.netflix", resolver.resolvablePackageOrNull("com.netflix"))
    }

    @Test fun returnsNullWhenNotInstalled() {
        val resolver = LaunchIntentResolver { false }
        assertNull(resolver.resolvablePackageOrNull("com.missing"))
    }
}
