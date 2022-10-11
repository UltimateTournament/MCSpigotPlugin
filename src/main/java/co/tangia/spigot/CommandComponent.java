package co.tangia.spigot;

public class CommandComponent {
    public String command;
    public long delayTicks;
    private String displayName;
    private String playerName;

    public CommandComponent(String command, String displayName, String playerName, long delayTicks) {
        this.command = command;
        this.displayName = displayName;
        this.playerName = playerName;
        this.delayTicks = delayTicks;
    }

    public String getCommand() {
        return this.command.replaceAll("\\$DISPLAYNAME", this.displayName).replaceAll("\\$PLAYERNAME", this.playerName);
    }
}
