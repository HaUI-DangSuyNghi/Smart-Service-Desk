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
    private final vn.haui.dx.hpdi.intelligence.api.AdvisoryAssistanceCapability advisory;
    private final vn.haui.dx.apps.servicedesk.intelligence.LocalModelRuntime runtime;

    @Autowired
    public TicketAssistanceService(
            TicketService tickets,
            GroundedAssistanceCapability intelligence,
            ServiceDeskAssistanceConfiguration.Settings settings,
            AssistanceRouting routing,
            HumanContext human,
            vn.haui.dx.hpdi.intelligence.api.AdvisoryAssistanceCapability advisory,
            vn.haui.dx.apps.servicedesk.intelligence.LocalModelRuntime runtime) {
        this.tickets = tickets;
        this.intelligence = intelligence;
        this.settings = settings;
        this.routing = routing;
        this.human = human;
        this.advisory = advisory;
        this.runtime = runtime;
    }

    public TicketAssistanceService(TicketService tickets, GroundedAssistanceCapability intelligence,
            ServiceDeskAssistanceConfiguration.Settings settings, AssistanceRouting routing, HumanContext human) {
        this(tickets, intelligence, settings, routing, human, null,
                vn.haui.dx.apps.servicedesk.intelligence.LocalModelRuntime.external());
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

        if (!availableFor(ticket.workspace())) {
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

        if ("local-api".equals(settings.mode())) {
            runtime.authorize(workspace, "API_ASSISTANCE", "advisory@0.1.0");
            var actor = human.currentActor(workspace);
            if (actor == null) throw CapabilityException.forbidden("Authenticated actor required");
            var result = advisory.advise(question, java.util.Map.of("title", ticket.title(), "description", ticket.description()));
            var current = tickets.get(workspace, id);
            if (!actor.equals(human.currentActor(workspace))) throw CapabilityException.forbidden("Actor changed during assistance");
            runtime.authorize(workspace, "API_ASSISTANCE", "advisory@0.1.0");
            if (!ticket.id().equals(current.id()) || !ticket.workspace().equals(current.workspace())
                    || !ticket.title().equals(current.title()) || !ticket.description().equals(current.description())) {
                throw CapabilityException.conflict("Ticket changed during assistance; ask again");
            }
            return result;
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
    public boolean apiAdviceMode() { return "local-api".equals(settings.mode()); }

    public boolean availableFor(String workspace) {
        return settings.enabled() && ("local-api".equals(settings.mode())
                ? runtime.supports(workspace) : settings.catalogs().containsKey(workspace));
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
