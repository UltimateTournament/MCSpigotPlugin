package co.tangia.sdk;

public class IntegrationLoginReq {
    public String VersionInfo;
    public String Code;

    public IntegrationLoginReq(String versionInfo, String code) {
        VersionInfo = versionInfo;
        Code = code;
    }
}
