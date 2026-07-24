package io.monohull.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

  @Bean
  public DockerClient dockerClient (@Value("${app.docker.host}") String dockerHost, @Value("${app.docker.tlsVerify}") boolean tlsVerify) {

    DefaultDockerClientConfig config = DefaultDockerClientConfig
      .createDefaultConfigBuilder()
      .withDockerHost(dockerHost)
      .withDockerTlsVerify(tlsVerify)
      .build();

    DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
      .dockerHost(config.getDockerHost())
      .sslConfig(config.getSSLConfig())
      .connectionTimeout(Duration.ofSeconds(30))
      .responseTimeout(Duration.ofMinutes(10))
      .build();

    return DockerClientImpl.getInstance(config, httpClient);

  }

}
