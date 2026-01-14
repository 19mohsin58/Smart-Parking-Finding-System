package unipi.lsmdb.SPFS.Config;

import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
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
    public MongoClient mongoClient() {
        // Enforce Eventual Consistency: Prefer reading from Secondaries (Replicas)
        com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                .applyConnectionString(new com.mongodb.ConnectionString(
                        mongoUri != null ? mongoUri : "mongodb://localhost:27017/SPFS_DB"))
                .readPreference(ReadPreference.secondaryPreferred())
                .build();

        return MongoClients.create(settings);
    }
}

