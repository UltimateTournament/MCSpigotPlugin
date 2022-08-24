package co.tangia.sdk;

public class GameLoginReq {
    public String GameID;
    public String Code;

    public GameLoginReq(String gameID, String code) {
        GameID = gameID;
        Code = code;
    }
}
