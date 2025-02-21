package co.tangia.spigot;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
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
  private static final Logger LOGGER = LoggerFactory.getLogger(ModPersistence.class.getCanonicalName());

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
      LOGGER.warn("couldn't store data", e);
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
    } catch (FileNotFoundException e) {
      LOGGER.info("no data to load - starting with clean state");
    } catch (IOException e) {
      LOGGER.warn("couldn't load data ", e);
    }
  }
}
