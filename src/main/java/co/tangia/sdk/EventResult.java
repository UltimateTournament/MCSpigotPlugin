package co.tangia.sdk;

public class EventResult {
    public EventResult(String eventID, boolean executed, String message) {
        EventID = eventID;
        Executed = executed;
        Message = message;
    }
    public String EventID;
    public boolean Executed;
    public String Message;
}
