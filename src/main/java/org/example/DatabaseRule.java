package org.example;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.Callable;

import static com.github.dockerjava.api.model.Ports.Binding.bindPort;

public class DatabaseRule extends ExternalResource {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseRule.class);

    private DockerClient docker = newDefaultDockerClient();
    private String imageName = "postgres:9.5";
    private String postgresDb = "postgres";
    private String postgresUser = "postgres";
    private String postgresPassword = "postgres";
    private int port = findRandomFreePort();    // TODO: Parameterize port + default random
    private int maxInitialConnectionAttempts = 20;
    private Callable<Boolean> initialConnectionAttempts = null;
    private final List<String> initSql = new ArrayList<>();

    private String containerId;

    public DatabaseRule usingDockerClient(DockerClient dockerClient) {
        this.docker = dockerClient;
        return this;
    }

    public DatabaseRule usingImage(String dockerImage) {
        this.imageName = dockerImage;
        return this;
    }

    public DatabaseRule withPostgresDb(String postgresDb) {
        this.postgresDb = postgresDb;
        return this;
    }

    public DatabaseRule withPostgresUser(String postgresUser) {
        this.postgresUser = postgresUser;
        return this;
    }

    public DatabaseRule withPostgresPassword(String postgresPassword) {
        this.postgresPassword = postgresPassword;
        return this;
    }

    public DatabaseRule withPort(int port) {
        this.port = port;
        return this;
    }

    public DatabaseRule withInitSql(List<String> initSql) {
        this.initSql.addAll(initSql);
        return this;
    }

    public DatabaseRule withInitSql(String... initSql) {
        this.initSql.addAll(Arrays.asList(initSql));
        return this;
    }

    public DatabaseRule withInitialConnectionAttempts(Callable<Boolean> attempts) {
        this.initialConnectionAttempts = attempts;
        return this;
    }

    public DatabaseRule withNoInitialConnectionAttempts() {
        this.initialConnectionAttempts = () -> false;
        return this;
    }

    public DatabaseRule withMaxNumberOfInitialConnectionAttempts(int maxAttempts) {
        this.maxInitialConnectionAttempts = maxAttempts;
        return this;
    }

    @Override
    public void before() throws Exception {
        try {
            docker.inspectImageCmd(imageName).exec();
            LOG.info("Image '{}' will be used", imageName);
        } catch (NotFoundException e) {
            LOG.info("Image '{}' not found. Image will be pulled", imageName);
            docker.pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitSuccess();
            LOG.info("Image '{}' succesfully pulled", imageName);
        }

        // TODO: Parameterize automatic pull even if image already exist?

        CreateContainerResponse container = docker.createContainerCmd(imageName)
                .withPortBindings(new PortBinding(bindPort(this.port), new ExposedPort(5432)))
                .withEnv("POSTGRES_DB=" + postgresDb
                        , "POSTGRES_USER=" + postgresUser
                        , "POSTGRES_PASSWORD=" + postgresPassword)
                .withName("postgres-rule-" + this.port())
                .exec();
        this.containerId = container.getId();
        if (container.getWarnings() != null) {
            LOG.warn("Container warnings: {}", Arrays.toString(container.getWarnings()));
        }

        // Start the container
        docker.startContainerCmd(this.containerId).exec();

        boolean initialConnection = executeInitialConnectionAttempts();

        if (!initSql.isEmpty()) {
            if (!initialConnection) {
                throw new DatabaseRuleException("Can't run initial SQL statements, if no successful connection attempts have been made");
            }
            for (String sql : initSql) {
                int sqlExitCode = executeSql(sql);
                if (sqlExitCode != 0) {
                    throw new DatabaseRuleException("Failed to execute SQL statement: '" + sql + "'");
                }
            }
        }
    }

    private boolean executeInitialConnectionAttempts() throws Exception {
        return Optional.ofNullable(initialConnectionAttempts)
                .orElse(initialConnectionAttempts()).call();
    }

    private Callable<Boolean> initialConnectionAttempts() {
        Properties properties = new Properties();
        properties.put("user", this.postgresUser);
        properties.put("password", this.postgresPassword);
        String url = "jdbc:postgresql://localhost:" + this.port() + "/" + this.postgresDb;
        return new RetryAttempts(
                maxInitialConnectionAttempts,
                new ConnectionAttempt(url, properties),
                500L);
    }

    private int executeSql(String sql) throws InterruptedException {
        ExecCreateCmdResponse sqlCmd = docker.execCreateCmd(this.containerId)
                .withAttachStdout(true)
                .withCmd("psql", "-t", "-d", postgresDb, "-U", postgresUser, "-c", sql)
                .exec();
        docker.execStartCmd(sqlCmd.getId()).withDetach(false)
                .exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
        InspectExecResponse inspect = docker.inspectExecCmd(sqlCmd.getId()).exec();
        return inspect.getExitCode();
    }

    @Override
    public void after() {
        // TODO: Parameterize automatic stop of container
        docker.stopContainerCmd(this.containerId).withTimeout(10).exec();

        // TODO: Parameterize automatic removal of container
        docker.removeContainerCmd(this.containerId).withForce(true).exec();
    }

    public int port() {
        return this.port;
    }

    private static int findRandomFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException ex) {
            throw new AssertionError("Could not find free port", ex);
        }
    }

    private static DockerClient newDefaultDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        return DockerClientBuilder.getInstance(config).build();
    }
}
