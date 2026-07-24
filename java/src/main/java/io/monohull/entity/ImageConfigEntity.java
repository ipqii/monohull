package io.monohull.entity;

import io.monohull.dto.ExtraBind;
import io.monohull.dto.ExtraEnvVar;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "image_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"client", "project", "maximo_version"})
})
public class ImageConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String client;

    @Column(nullable = false)
    private String project;

    @Column(name = "maximo_version", nullable = false)
    private String maximoVersion;

    @Column(name = "app_image", nullable = false)
    private String appImage;

    @Column(name = "db_image", nullable = false)
    private String dbImage;

    @Column(name = "adm_image", nullable = false)
    private String admImage;

    @Column(name = "db_vendor", nullable = false)
    private String dbVendor = "DB2";

    @Column(name = "db_name")
    private String dbName = "maxdb76";

    @Column(name = "db_container_port")
    private Integer dbContainerPort;

    @Column(name = "host_volume_path")
    private String hostVolumePath;

    @Column(name = "db_volume_name")
    private String dbVolumeName;

    @Column(name = "workspace_path")
    private String workspacePath;

    @Column(name = "app_http_port")
    private Integer appHttpPort;

    @Column(name = "app_https_port")
    private Integer appHttpsPort;

    @Column(name = "db_port")
    private Integer dbPort;

    @Column(name = "mock_host_port")
    private Integer mockHostPort;

    @Column(name = "smtp_host_port")
    private Integer smtpHostPort;

    @Column(name = "smtp_ui_host_port")
    private Integer smtpUiHostPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_definition_id")
    private PipelineDefinitionEntity pipelineDefinition;

    @Convert(converter = ExtraEnvVarsConverter.class)
    @Column(name = "db_extra_env_json", columnDefinition = "TEXT")
    private List<ExtraEnvVar> dbExtraEnv;

    @Convert(converter = ExtraBindsConverter.class)
    @Column(name = "db_extra_binds_json", columnDefinition = "TEXT")
    private List<ExtraBind> dbExtraBinds;

    @Convert(converter = ExtraEnvVarsConverter.class)
    @Column(name = "app_extra_env_json", columnDefinition = "TEXT")
    private List<ExtraEnvVar> appExtraEnv;

    @Convert(converter = ExtraBindsConverter.class)
    @Column(name = "app_extra_binds_json", columnDefinition = "TEXT")
    private List<ExtraBind> appExtraBinds;

    @Convert(converter = ExtraEnvVarsConverter.class)
    @Column(name = "adm_extra_env_json", columnDefinition = "TEXT")
    private List<ExtraEnvVar> admExtraEnv;

    @Convert(converter = ExtraBindsConverter.class)
    @Column(name = "adm_extra_binds_json", columnDefinition = "TEXT")
    private List<ExtraBind> admExtraBinds;

    // Launch defaults: what a one-click profile launch uses in place of New Build input.
    @Column(name = "launch_description", length = 500)
    private String launchDescription;

    @Column(name = "launch_static_ports", nullable = false)
    private boolean launchStaticPorts;

    @Column(name = "launch_include_mock", nullable = false)
    private boolean launchIncludeMock;

    @Column(name = "launch_include_smtp", nullable = false)
    private boolean launchIncludeSmtp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getMaximoVersion() { return maximoVersion; }
    public void setMaximoVersion(String maximoVersion) { this.maximoVersion = maximoVersion; }

    public String getAppImage() { return appImage; }
    public void setAppImage(String appImage) { this.appImage = appImage; }

    public String getDbImage() { return dbImage; }
    public void setDbImage(String dbImage) { this.dbImage = dbImage; }

    public String getAdmImage() { return admImage; }
    public void setAdmImage(String admImage) { this.admImage = admImage; }

    public String getDbVendor() { return dbVendor; }
    public void setDbVendor(String dbVendor) { this.dbVendor = dbVendor; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public Integer getDbContainerPort() { return dbContainerPort; }
    public void setDbContainerPort(Integer dbContainerPort) { this.dbContainerPort = dbContainerPort; }

    public String getHostVolumePath() { return hostVolumePath; }
    public void setHostVolumePath(String hostVolumePath) { this.hostVolumePath = hostVolumePath; }

    public String getDbVolumeName() { return dbVolumeName; }
    public void setDbVolumeName(String dbVolumeName) { this.dbVolumeName = dbVolumeName; }

    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }

    public Integer getAppHttpPort() { return appHttpPort; }
    public void setAppHttpPort(Integer appHttpPort) { this.appHttpPort = appHttpPort; }

    public Integer getAppHttpsPort() { return appHttpsPort; }
    public void setAppHttpsPort(Integer appHttpsPort) { this.appHttpsPort = appHttpsPort; }

    public Integer getDbPort() { return dbPort; }
    public void setDbPort(Integer dbPort) { this.dbPort = dbPort; }

    public Integer getMockHostPort() { return mockHostPort; }
    public void setMockHostPort(Integer mockHostPort) { this.mockHostPort = mockHostPort; }

    public Integer getSmtpHostPort() { return smtpHostPort; }
    public void setSmtpHostPort(Integer smtpHostPort) { this.smtpHostPort = smtpHostPort; }

    public Integer getSmtpUiHostPort() { return smtpUiHostPort; }
    public void setSmtpUiHostPort(Integer smtpUiHostPort) { this.smtpUiHostPort = smtpUiHostPort; }

    public PipelineDefinitionEntity getPipelineDefinition() { return pipelineDefinition; }
    public void setPipelineDefinition(PipelineDefinitionEntity pipelineDefinition) { this.pipelineDefinition = pipelineDefinition; }

    public List<ExtraEnvVar> getDbExtraEnv() { return dbExtraEnv; }
    public void setDbExtraEnv(List<ExtraEnvVar> dbExtraEnv) { this.dbExtraEnv = dbExtraEnv; }

    public List<ExtraBind> getDbExtraBinds() { return dbExtraBinds; }
    public void setDbExtraBinds(List<ExtraBind> dbExtraBinds) { this.dbExtraBinds = dbExtraBinds; }

    public List<ExtraEnvVar> getAppExtraEnv() { return appExtraEnv; }
    public void setAppExtraEnv(List<ExtraEnvVar> appExtraEnv) { this.appExtraEnv = appExtraEnv; }

    public List<ExtraBind> getAppExtraBinds() { return appExtraBinds; }
    public void setAppExtraBinds(List<ExtraBind> appExtraBinds) { this.appExtraBinds = appExtraBinds; }

    public List<ExtraEnvVar> getAdmExtraEnv() { return admExtraEnv; }
    public void setAdmExtraEnv(List<ExtraEnvVar> admExtraEnv) { this.admExtraEnv = admExtraEnv; }

    public List<ExtraBind> getAdmExtraBinds() { return admExtraBinds; }
    public void setAdmExtraBinds(List<ExtraBind> admExtraBinds) { this.admExtraBinds = admExtraBinds; }

    public String getLaunchDescription() { return launchDescription; }
    public void setLaunchDescription(String launchDescription) { this.launchDescription = launchDescription; }

    public boolean isLaunchStaticPorts() { return launchStaticPorts; }
    public void setLaunchStaticPorts(boolean launchStaticPorts) { this.launchStaticPorts = launchStaticPorts; }

    public boolean isLaunchIncludeMock() { return launchIncludeMock; }
    public void setLaunchIncludeMock(boolean launchIncludeMock) { this.launchIncludeMock = launchIncludeMock; }

    public boolean isLaunchIncludeSmtp() { return launchIncludeSmtp; }
    public void setLaunchIncludeSmtp(boolean launchIncludeSmtp) { this.launchIncludeSmtp = launchIncludeSmtp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
