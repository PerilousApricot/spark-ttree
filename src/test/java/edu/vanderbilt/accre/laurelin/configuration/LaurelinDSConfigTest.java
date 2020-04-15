package edu.vanderbilt.accre.laurelin.configuration;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.Test;

import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.ConfigBuilder;
import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.ConfigOption;
import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.ConfigValueType;
import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.IntegerValueType;
import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.LongValueType;
import edu.vanderbilt.accre.laurelin.configuration.ConfigListing.StringValueType;

public class LaurelinDSConfigTest {
    public static ConfigOption[] getConfigList() {
        return new ConfigOption[] {
                newConfig("threadCount")
                    .type(INTEGER_TYPE)
                    .literalDefault("16")
                    .description("Number of (system-wide) threads to use for background I/O and decompression")
                    .build(),
                newConfig("tree")
                    .type(STRING_TYPE)
                    .literalDefault("Events")
                    .description("The name of the TTree to load from our file(s)")
                    .build(),
                newConfig("longWithLiteral")
                    .type(LONG_TYPE)
                    .literalDefault("12345678900") // too big for an int
                    .build(),
                newConfig("longWithDependent")
                    .type(LONG_TYPE)
                    .dependentDefault("longWithLiteral")
                    .build(),
                newConfig("longWithNoDefault")
                    .type(LONG_TYPE)
                    .build(),
        };
    }

    @Test
    public void testDefaults() {
        ConfigListing configList = new ConfigListing(getConfigList());
        HashMap<String, String> map = new HashMap<String, String>();
        LaurelinDSConfig dsConfig = new LaurelinDSConfig(map, configList);
        assertEquals(12345678900L, dsConfig.getLong("longWithLiteral"));
        assertEquals(12345678900L, dsConfig.getLong("longWithDependent"));
        assertEquals(16, dsConfig.getInt("threadCount"));
        assertEquals("Events", dsConfig.getString("tree"));
    }

    @Test(expected = RuntimeException.class)
    public void testDefaults_fail_nodef() {
        ConfigListing configList = new ConfigListing(getConfigList());
        HashMap<String, String> map = new HashMap<String, String>();
        LaurelinDSConfig dsConfig = new LaurelinDSConfig(map, configList);
        assertEquals(12345678900L, dsConfig.getLong("longWithNoDefault"));
    }

    public static ConfigBuilder newConfig(String name) {
        return new ConfigBuilder(name);
    }

    private static final ConfigValueType INTEGER_TYPE = new IntegerValueType();
    private static final ConfigValueType LONG_TYPE = new LongValueType();
    private static final ConfigValueType STRING_TYPE = new StringValueType();
}
