package vn.haui.dx.apps.servicedesk.assistance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.AssistanceRouting;
import vn.haui.dx.hpdi.intelligence.api.GroundedAssistanceCapability;

import java.util.UUID;

@Service
public class TicketAssistanceService {
    private final TicketService tickets;
    private final GroundedAssistanceCapability intelligence;
    private final ServiceDeskAssistanceConfiguration.Settings settings;
    private final AssistanceRouting routing;
    private final HumanContext human;

    @Autowired
    public TicketAssistanceService(
            TicketService tickets,
            GroundedAssistanceCapability intelligence,
            ServiceDeskAssistanceConfiguration.Settings settings,
            AssistanceRouting routing,
            HumanContext human) {
        this.tickets = tickets;
        this.intelligence = intelligence;
        this.settings = settings;
        this.routing = routing;
        this.human = human;
    }

    public TicketAssistanceService(
            TicketService tickets,
            GroundedAssistanceCapability intelligence,
            ServiceDeskAssistanceConfiguration.Settings settings) {
        this(tickets, intelligence, settings, null, null);
    }

    public AssistanceOutcome assist(String workspace, UUID id, String question) {
        // Enforce ticket visibility before any AI or knowledge access.
        var ticket = tickets.get(workspace, id);

        if (!settings.enabled() || !settings.catalogs().containsKey(ticket.workspace())) {
            throw CapabilityException.notFound("Assistance is unavailable");
        }

        if (question == null
                || question.isBlank()
                || question.length() > 2000
                || question.codePoints()
                        .anyMatch(
                                value ->
                                        Character.isISOControl(value)
                                                && value != '\n'
                                                && value != '\r'
                                                && value != '\t')) {
            throw CapabilityException.validation(
                    "A question of at most 2000 characters is required");
        }

        var pins = settings.catalogs().get(ticket.workspace());
        vn.haui.dx.hpdi.intelligence.api.AssistanceRoute route = null;

        if ("synthetic-cloud-dev".equals(settings.mode())) {
            if (routing == null || human == null)
                throw CapabilityException.forbidden("Cloud processing is not configured");
            var actor = human.currentActor(workspace);
            var digests =
                    pins.stream()
                            .map(AssistanceInput.SourcePin::checksum)
                            .collect(java.util.stream.Collectors.toSet());
            routing.authorize(
                    workspace,
                    actor.subject(),
                    id.toString(),
                    digest(ticket.title() + "\n" + ticket.description()),
                    digest(question),
                    digests);
            route = routing.select("synthetic-assistance", "synthetic-public-evaluation");
        }

        var result =
                intelligence.assist(
                        ticket.workspace(),
                        new AssistanceInput(
                                question,
                                "service-desk",
                                "0.3.0",
                                ServiceDeskAssistanceConfiguration.POLICY,
                                pins,
                                route == null
                                        ? ServiceDeskAssistanceConfiguration.referenceIdentity()
                                        : route.expectedRuntime(),
                                route));

        // The read is a fresh use-case call after the bounded AI call. It must
        // stay outside a surrounding repeatable-read transaction.
        tickets.get(workspace, id);
        return result;
    }

    /** Configuration availability only; callers must still authorize ticket and sources. */
    public boolean availableFor(String workspace) {
        return settings.enabled() && settings.catalogs().containsKey(workspace);
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Digest unavailable");
        }
    }
}
