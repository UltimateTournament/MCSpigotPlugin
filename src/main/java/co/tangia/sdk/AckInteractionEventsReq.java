package co.tangia.sdk;

public class AckInteractionEventsReq {
    public AckInteractionEventsReq(EventResult[] eventResults) {
        EventResults = eventResults;
    }

    EventResult[] EventResults;
}
