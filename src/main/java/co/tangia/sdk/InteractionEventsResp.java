package co.tangia.sdk;

public class InteractionEventsResp {

    public static class ActionExecution {
        public String ID;
        public String Trigger;
        public InteractionEvent Body;
        public String Ttl;
    }

    ActionExecution[] ActionExecutions;
}
