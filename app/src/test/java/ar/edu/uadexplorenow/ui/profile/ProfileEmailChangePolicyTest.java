package ar.edu.uadexplorenow.ui.profile;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileEmailChangePolicyTest {

    @Test
    public void emailToKey_normalizesLikeFirebaseIndex() {
        assertEquals(
                "persona_at_example_dot_com",
                ProfileEmailChangePolicy.emailToKey(" Persona@Example.COM ")
        );
    }

    @Test
    public void canUseEmailIndex_onlyAcceptsEmptyOrSameUser() {
        assertTrue(ProfileEmailChangePolicy.canUseEmailIndex(null, "uid-1"));
        assertTrue(ProfileEmailChangePolicy.canUseEmailIndex("", "uid-1"));
        assertTrue(ProfileEmailChangePolicy.canUseEmailIndex("uid-1", "uid-1"));
        assertFalse(ProfileEmailChangePolicy.canUseEmailIndex("uid-2", "uid-1"));
    }

    @Test
    public void canRequestFirebaseEmailChange_rejectsAnonymousOtpSession() {
        assertTrue(ProfileEmailChangePolicy.canRequestFirebaseEmailChange(false));
        assertFalse(ProfileEmailChangePolicy.canRequestFirebaseEmailChange(true));
    }

    @Test
    public void buildProfileUpdates_preservesConfirmedEmailAndEveryProfileField() {
        Map<String, Object> updates = ProfileEmailChangePolicy.buildProfileUpdates(
                "uid-1",
                "actual@example.com",
                "Nombre",
                "1122334455",
                "file:///profile.jpg",
                Arrays.asList("aventura", "cultura"),
                Collections.singletonList("otra")
        );

        assertEquals("uid-1", updates.get("id"));
        assertEquals("actual@example.com", updates.get("email"));
        assertEquals("Nombre", updates.get("name"));
        assertEquals("1122334455", updates.get("phone"));
        assertEquals("file:///profile.jpg", updates.get("photoUrl"));
        assertEquals(Arrays.asList("aventura", "cultura"), updates.get("preferences"));
        assertEquals(Collections.singletonList("otra"), updates.get("legacy_preferences"));
    }

    @Test
    public void resolveDisplayedEmail_usesOtpEmailWithoutChangingClassicSessions() {
        assertEquals(
                "nuevo@example.com",
                ProfileEmailChangePolicy.resolveDisplayedEmail(
                        "anterior@example.com",
                        "nuevo@example.com",
                        true
                )
        );
        assertEquals(
                "anterior@example.com",
                ProfileEmailChangePolicy.resolveDisplayedEmail(
                        "anterior@example.com",
                        "nuevo@example.com",
                        false
                )
        );
    }
}
