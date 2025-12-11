package com.ridwan.tweetaudit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AlignmentCriteriaTest {

    @Autowired
    private AlignmentCriteria alignmentCriteria;

    @Test
    void shouldLoadConfigurationFromProperties() {
        // Verify configuration is loaded correctly
        assertNotNull(alignmentCriteria, "AlignmentCriteria should be autowired");

        // Verify forbidden words
        assertNotNull(alignmentCriteria.getForbiddenWords(), "Forbidden words should not be null");
        assertFalse(alignmentCriteria.getForbiddenWords().isEmpty(), "Forbidden words should not be empty");
        assertTrue(alignmentCriteria.getForbiddenWords().contains("kill"), "Should contain 'kill'");
        assertTrue(alignmentCriteria.getForbiddenWords().contains("bum"), "Should contain 'bum'");

        // Verify professionalism check
        assertTrue(alignmentCriteria.isCheckProfessionalism(), "Should check professionalism");

        // Verify context
        assertNotNull(alignmentCriteria.getContext(), "Context should not be null");
        assertFalse(alignmentCriteria.getContext().isEmpty(), "Context should not be empty");

        // Verify desired tone
        assertNotNull(alignmentCriteria.getDesiredTone(), "Desired tone should not be null");
        assertFalse(alignmentCriteria.getDesiredTone().isEmpty(), "Desired tone should not be empty");
    }

    @Test
    void shouldContainAllConfiguredForbiddenWords() {
        // Verify all words from application.properties are loaded
        assertEquals(4, alignmentCriteria.getForbiddenWords().size(),
                "Should have exactly 4 forbidden words");

        assertTrue(alignmentCriteria.getForbiddenWords().contains("kill"));
        assertTrue(alignmentCriteria.getForbiddenWords().contains("bum"));
        assertTrue(alignmentCriteria.getForbiddenWords().contains("damn"));
        assertTrue(alignmentCriteria.getForbiddenWords().contains("stupid"));
    }
}
