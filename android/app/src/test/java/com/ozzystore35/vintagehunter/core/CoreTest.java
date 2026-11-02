package com.ozzystore35.vintagehunter.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Gradle `testDebugUnitTest` için JUnit sarmalayıcısı. */
public class CoreTest {
    @Test
    public void coreLogic() throws Exception {
        List<String> fails = CoreTests.run();
        assertTrue(String.join("\n", fails), fails.isEmpty());
    }
}
