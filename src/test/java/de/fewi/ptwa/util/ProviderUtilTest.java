package de.fewi.ptwa.util;

import de.schildbach.pte.AvvAugsburgProvider;
import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.VbbProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertInstanceOf(BvgProvider.class, ProviderUtil.getObjectForProvider("BVG"));
        assertInstanceOf(VbbProvider.class, ProviderUtil.getObjectForProvider("VBB"));
    }

    @Test
    void createsProvidersWithUnderscoreIds() {
        assertInstanceOf(AvvAugsburgProvider.class, ProviderUtil.getObjectForProvider("AVV_AUGSBURG"));
    }

    @Test
    void discoversAllConcreteProviders() {
        List<ProviderUtil.ProviderInfo> providers = ProviderUtil.getAvailableProviders();

        assertTrue(providers.size() > 3);
        assertTrue(providers.stream().anyMatch(provider -> "KVV".equals(provider.id())));
        assertTrue(providers.stream().anyMatch(provider -> "BVG".equals(provider.id())));
        assertTrue(providers.stream().anyMatch(provider -> "VBB".equals(provider.id())));
        assertTrue(providers.stream().anyMatch(provider -> "AVV_AUGSBURG".equals(provider.id())));
    }

    @Test
    void returnsNullForUnknownProviders() {
        assertNull(ProviderUtil.getObjectForProvider("NoSuchProvider"));
    }
}
