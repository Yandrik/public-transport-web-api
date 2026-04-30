package de.fewi.ptwa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "thingspeak.key=",
        "provider.default=Kvv"
})
class PublicTransportWebApiApplicationTest {

    @Test
    void contextLoads() {
    }
}
