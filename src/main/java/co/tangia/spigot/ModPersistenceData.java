package co.tangia.spigot;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public final class ModPersistenceData {
    private HashMap<UUID, PlayerSession> sessions;

    public ModPersistenceData(HashMap<UUID, PlayerSession> sessions) {
        this.sessions = sessions;
    }

    public HashMap<UUID, PlayerSession> sessions() {
        return sessions;
    }

    public void setSessions(HashMap<UUID, PlayerSession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        ModPersistenceData that = (ModPersistenceData) obj;
        return Objects.equals(this.sessions, that.sessions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessions);
    }

    @Override
    public String toString() {
        return "ModPersistenceData[" +
            "sessions=" + sessions + ']';
    }

    public static final class PlayerSession {
        private String sessionToken;

        public PlayerSession(String sessionToken) {
            this.sessionToken = sessionToken;
        }

        public String sessionToken() {
            return sessionToken;
        }

        public void setSessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            PlayerSession that = (PlayerSession) obj;
            return Objects.equals(this.sessionToken, that.sessionToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionToken);
        }

        @Override
        public String toString() {
            return "PlayerSession[" +
                "sessionToken=" + sessionToken + ']';
        }

    }
}
