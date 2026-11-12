package kreyj.sitzplatzreservierung.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.sitzplatzreservierung.dto.WagenAnlegenRequest;
import kreyj.sitzplatzreservierung.model.Wagen;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Legt beim Start für jedes Wagenlayout des Katalogs einen Demo-Wagen mit gleicher Id an, sofern es ihn noch nicht gibt.
 */
@ApplicationScoped
public class WagenInitialisierung {

    private static final Logger LOG = Logger.getLogger(WagenInitialisierung.class);

    @ConfigProperty(name = "wagen.demo.anlegen", defaultValue = "true")
    boolean anlegen;

    @Inject
    WagenlayoutKatalog katalog;

    @Inject
    WagenAnlage wagenAnlage;

    @Transactional
    void beimStart(@Observes StartupEvent ereignis) {
        if (!anlegen)
            return;
        for (var kennung : katalog.kennungen())
            if (Wagen.count("id", kennung) == 0)
                try {
                    wagenAnlage.anlegen(new WagenAnlegenRequest(null, kennung, null, null, true));
                } catch (RuntimeException e) {
                    LOG.warnf(e, "Wagen für das Layout %s nicht angelegt", kennung);
                }
    }
}
