package de.fewi.ptwa.util;

import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.VbbProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderUtilTest {

    @Test
    void createsDefaultProviderWhenNoProviderNameIsGiven() {
        NetworkProvider provider = ProviderUtil.getObjectForProvider(null);

        assertInstanceOf(KvvProvider.class, provider);
    }

    @Test
    void normalizesUppercaseProviderNames() {
        NetworkProvider provider = ProviderUtil.getObjectForProvider("KVV");

        assertInstanceOf(KvvProvider.class, provider);
    }

    @Test
    void createsProvidersThatRequireAuthorizationConstructors() {
        assertInstanceOf(BvgProvider.class, ProviderUtil.getObjectForProvider("Bvg"));
        assertInstanceOf(VbbProvider.class, ProviderUtil.getObjectForProvider("Vbb"));
    }

    @Test
    void returnsNullForUnknownProviders() {
        assertNull(ProviderUtil.getObjectForProvider("NoSuchProvider"));
    }
}
