/*
 * Copyright 2018, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hipstershop;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import hipstershop.Demo.Ad;
import hipstershop.Demo.AdRequest;
import hipstershop.Demo.AdResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.services.*;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AdService {

  private static final Logger logger = LogManager.getLogger(AdService.class);

  @SuppressWarnings("FieldCanBeLocal")
  private static int MAX_ADS_TO_SERVE = 2;

  private Server server;
  private HealthStatusManager healthMgr;
  private Connection dbConnection;

  private static final AdService service = new AdService();

  private void start() throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9555"));
    healthMgr = new HealthStatusManager();

    logger.info("connecting to database...");
    try {
      String dbHost = System.getenv("DB_HOST");
      String dbPort = System.getenv("DB_PORT");
      String dbName = System.getenv("DB_NAME");
      String dbUser = System.getenv("DB_USER");
      String dbPassword = System.getenv("POSTGRES_PASSWORD");
      String dbSslMode = System.getenv("DB_SSL_MODE");

      // Ensure all required environment variables are set.
      if (dbHost == null || dbPort == null || dbName == null || dbUser == null || dbPassword == null || dbSslMode == null) {
        logger.fatal("Database connection information is missing from environment variables.");
        throw new IOException("Missing database environment variables.");
      }

      Properties props = new Properties();
      props.setProperty("user", dbUser);
      props.setProperty("password", dbPassword);

      String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s", dbHost, dbPort, dbName, dbSslMode);

      // Step 1: Ensure the database exists.
      ensureDatabaseExists(dbHost, dbPort, dbUser, dbPassword, dbSslMode, dbName);

      // Step 2: Connect to the application database.
      this.dbConnection = DriverManager.getConnection(jdbcUrl, props);
      logger.info("database connection established");

      // Step 3: Initialize the table.
      initializeTable();
    } catch (SQLException e) {
      logger.fatal("failed to connect to database", e);
      throw new IOException("Failed to connect to database.", e);
    }

    server =
        ServerBuilder.forPort(port)
            .addService(new AdServiceImpl())
            .addService(healthMgr.getHealthService())
            .build()
            .start();
    logger.info("Ad Service started, listening on " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                  System.err.println(
                      "*** shutting down gRPC ads server since JVM is shutting down");
                  AdService.this.stop();
                  System.err.println("*** server shut down");
                }));
    healthMgr.setStatus("", ServingStatus.SERVING);
  }

  private void stop() {
    if (server != null) {
      healthMgr.clearStatus("");
      server.shutdown();
    }
    if (dbConnection != null) {
      try {
        dbConnection.close();
        logger.info("database connection closed");
      } catch (SQLException e) {
        logger.warn("failed to close database connection", e);
      }
    }
  }

  private void ensureDatabaseExists(String host, String port, String user, String password, String sslmode, String dbName) throws SQLException {
    String postgresJdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s",
        host, port, dbName, sslmode);
    Properties props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);

    try (Connection conn = DriverManager.getConnection(postgresJdbcUrl, props)) {
      try (Statement stmt = conn.createStatement()) {
        ResultSet rs = stmt.executeQuery(String.format("SELECT 1 FROM pg_database WHERE datname = '%s'", dbName));
        if (!rs.next()) {
          logger.info("database '{}' does not exist. creating database...", dbName);
          stmt.executeUpdate(String.format("CREATE DATABASE %s", dbName));
          logger.info("database '{}' created.", dbName);
        } else {
          logger.info("database '{}' already exists.", dbName);
        }
      }
    }
  }

  private void initializeTable() {
    try (Statement statement = dbConnection.createStatement()) {
      // Check if the 'ads' table exists
      ResultSet rs = dbConnection.getMetaData().getTables(null, null, "ads", null);
      if (!rs.next()) {
        logger.info("table 'ads' does not exist. creating and populating table...");
        // Create the table
        statement.executeUpdate(
            "CREATE TABLE ads (" +
                "id SERIAL PRIMARY KEY," +
                "category VARCHAR(255) NOT NULL," +
                "redirect_url VARCHAR(255) NOT NULL," +
                "ad_text TEXT NOT NULL)");

        // Populate the table
        ImmutableListMultimap<String, Ad> adsMap = createAdsMap();
        String insertSql = "INSERT INTO ads (category, redirect_url, ad_text) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = dbConnection.prepareStatement(insertSql)) {
          for (String category : adsMap.keySet()) {
            for (Ad ad : adsMap.get(category)) {
              pstmt.setString(1, category);
              pstmt.setString(2, ad.getRedirectUrl());
              pstmt.setString(3, ad.getText());
              pstmt.addBatch();
            }
          }
          pstmt.executeBatch();
        }
        logger.info("table 'ads' created and populated.");
      } else {
        logger.info("table 'ads' already exists. skipping initialization.");
      }
    } catch (SQLException e) {
      logger.fatal("failed to initialize database", e);
    }
  }

  private static ImmutableListMultimap<String, Ad> createAdsMap() {
    Ad hairdryer =
        Ad.newBuilder()
            .setRedirectUrl("/product/2ZYFJ3GM2N")
            .setText("Hairdryer for sale. 50% off.")
            .build();
    Ad tankTop =
        Ad.newBuilder()
            .setRedirectUrl("/product/66VCHSJNUP")
            .setText("Tank top for sale. 20% off.")
            .build();
    Ad candleHolder =
        Ad.newBuilder()
            .setRedirectUrl("/product/0PUK6V6EV0")
            .setText("Candle holder for sale. 30% off.")
            .build();
    Ad bambooGlassJar =
        Ad.newBuilder()
            .setRedirectUrl("/product/9SIQT8TOJO")
            .setText("Bamboo glass jar for sale. 10% off.")
            .build();
    Ad watch =
        Ad.newBuilder()
            .setRedirectUrl("/product/1YMWWN1N4O")
            .setText("Watch for sale. Buy one, get second kit for free")
            .build();
    Ad mug =
        Ad.newBuilder()
            .setRedirectUrl("/product/6E92ZMYYFZ")
            .setText("Mug for sale. Buy two, get third one for free")
            .build();
    Ad loafers =
        Ad.newBuilder()
            .setRedirectUrl("/product/L9ECAV7KIM")
            .setText("Loafers for sale. Buy one, get second one for free")
            .build();
    return ImmutableListMultimap.<String, Ad>builder()
        .putAll("clothing", tankTop)
        .putAll("accessories", watch)
        .putAll("footwear", loafers)
        .putAll("hair", hairdryer)
        .putAll("decor", candleHolder)
        .putAll("kitchen", bambooGlassJar, mug)
        .build();
  }

  private static class AdServiceImpl extends hipstershop.AdServiceGrpc.AdServiceImplBase {

    /**
     * Retrieves ads based on context provided in the request {@code AdRequest}.
     *
     * @param req the request containing context.
     * @param responseObserver the stream observer which gets notified with the value of {@code
     *     AdResponse}
     */
    @Override
    public void getAds(AdRequest req, StreamObserver<AdResponse> responseObserver) {
      AdService service = AdService.getInstance();
      try {
        List<Ad> allAds = new ArrayList<>();
        logger.info("received ad request (context_words=" + req.getContextKeysList() + ")");
        if (req.getContextKeysCount() > 0) {
          for (int i = 0; i < req.getContextKeysCount(); i++) {
            Collection<Ad> ads = service.getAdsByCategory(req.getContextKeys(i));
            allAds.addAll(ads);
          }
        } else {
          allAds = service.getRandomAds();
        }
        if (allAds.isEmpty()) {
          // Serve random ads.
          allAds = service.getRandomAds();
        }
        AdResponse reply = AdResponse.newBuilder().addAllAds(allAds).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
      } catch (StatusRuntimeException e) {
        logger.log(Level.WARN, "GetAds Failed with status {}", e.getStatus());
        responseObserver.onError(e);
      }
    }
  }

  private Collection<Ad> getAdsByCategory(String category) {
    List<Ad> ads = new ArrayList<>();
    String sql = "SELECT redirect_url, ad_text FROM ads WHERE category = ?";
    try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
      pstmt.setString(1, category);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        Ad ad = Ad.newBuilder()
            .setRedirectUrl(rs.getString("redirect_url"))
            .setText(rs.getString("ad_text"))
            .build();
        ads.add(ad);
      }
    } catch (SQLException e) {
      logger.warn("failed to retrieve ads by category", e);
    }
    return ads;
  }

  private List<Ad> getRandomAds() {
    List<Ad> ads = new ArrayList<>(MAX_ADS_TO_SERVE);
    String sql = "SELECT redirect_url, ad_text FROM ads ORDER BY RANDOM() LIMIT ?";
    try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
      pstmt.setInt(1, MAX_ADS_TO_SERVE);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        Ad ad = Ad.newBuilder()
            .setRedirectUrl(rs.getString("redirect_url"))
            .setText(rs.getString("ad_text"))
            .build();
        ads.add(ad);
      }
    } catch (SQLException e) {
      logger.warn("failed to retrieve random ads", e);
    }
    return ads;
  }

  private static AdService getInstance() {
    return service;
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }



  private static void initStats() {
    if (System.getenv("DISABLE_STATS") != null) {
      logger.info("Stats disabled.");
      return;
    }
    logger.info("Stats enabled, but temporarily unavailable");

    long sleepTime = 10; /* seconds */
    int maxAttempts = 5;

    // TODO(arbrown) Implement OpenTelemetry stats

  }

  private static void initTracing() {
    if (System.getenv("DISABLE_TRACING") != null) {
      logger.info("Tracing disabled.");
      return;
    }
    logger.info("Tracing enabled but temporarily unavailable");
    logger.info("See https://github.com/GoogleCloudPlatform/microservices-demo/issues/422 for more info.");

    // TODO(arbrown) Implement OpenTelemetry tracing
    
    logger.info("Tracing enabled - Stackdriver exporter initialized.");
  }

  /** Main launches the server from the command line. */
  public static void main(String[] args) throws IOException, InterruptedException {

    new Thread(
            () -> {
              initStats();
              initTracing();
            })
        .start();

    // Start the RPC server. You shouldn't see any output from gRPC before this.
    logger.info("AdService starting.");
    final AdService service = AdService.getInstance();
    service.start();
    service.blockUntilShutdown();
  }
}
