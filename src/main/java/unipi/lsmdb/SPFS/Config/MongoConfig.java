package unipi.lsmdb.SPFS.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:SPFS_DB}")
    private String databaseName;

    @Override
    protected String getDatabaseName() {
        // Fallback to "SPFS_DB" if the property injection fails or is empty
        String dbName = (databaseName != null) ? databaseName.trim() : null;
        return (dbName != null && !dbName.isEmpty()) ? dbName : "SPFS_DB";
    }

    @Override
    public com.mongodb.client.MongoClient mongoClient() {
        com.mongodb.ConnectionString connectionString = new com.mongodb.ConnectionString(
                mongoUri != null && !mongoUri.isEmpty() ? mongoUri : null);

        com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return com.mongodb.client.MongoClients.create(settings);
    }
}
