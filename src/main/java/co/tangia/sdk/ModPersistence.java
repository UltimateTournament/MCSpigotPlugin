package co.tangia.sdk;

import com.google.gson.Gson;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ModPersistence {
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
    private static final Gson gson = new Gson();
    private static final String fileName = "./tangia-persistence.json";

    public static ModPersistenceData data = new ModPersistenceData(new HashMap<>());

    private ModPersistence() {
    }

    public static void store() {
        executor.execute(ModPersistence::storeData);
    }

    private static void storeData() {
        try (FileWriter fw = new FileWriter(fileName, false)) {
            fw.write(gson.toJson(data));
        } catch (IOException e) {
            System.out.println("WARN: couldn't store data " + e);
        }
    }

    public static void load() {
        try (FileReader fr = new FileReader(fileName)) {
            data = gson.fromJson(fr, ModPersistenceData.class);
            if (data == null) {
                data = new ModPersistenceData(new HashMap<>());
            }
            if (data.sessions() == null) {
                data.setSessions(new HashMap<>());
            }
        } catch (IOException e) {
            System.out.println("WARN: couldn't load data " + e);
        }
    }
}
