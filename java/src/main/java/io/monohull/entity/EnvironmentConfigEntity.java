package io.monohull.entity;

import io.monohull.dto.ExtraBind;
import io.monohull.dto.ExtraEnvVar;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "environment_config")
public class EnvironmentConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false, unique = true)
    private EnvironmentEntity environment;

    @Column(name = "host_volume_path")
    private String hostVolumePath;

    @Column(name = "db_volume_name")
    private String dbVolumeName;

    @Column(name = "static_ports", nullable = false)
    private boolean staticPorts;

    @Column(name = "app_http_port")
    private Integer appHttpPort;

    @Column(name = "app_https_port")
    private Integer appHttpsPort;

    @Column(name = "db_port")
    private Integer dbPort;

    @Column(name = "db_password")
    private String dbPassword;

    @Column(name = "mock_enabled", nullable = false)
    private boolean mockEnabled;

    @Column(name = "mock_host_port")
    private Integer mockHostPort;

    @Column(name = "smtp_enabled", nullable = false)
    private boolean smtpEnabled;

    @Column(name = "smtp_host_port")
    private Integer smtpHostPort;

    @Column(name = "smtp_ui_host_port")
    private Integer smtpUiHostPort;

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

    /** Per-build override for the workspace bind source (host path). When set, it replaces
     *  ImageConfig.workspacePath as the mount source while the target folder name is kept. */
    @Column(name = "workspace_path_override", length = 1000)
    private String workspacePathOverride;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EnvironmentEntity getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentEntity environment) { this.environment = environment; }

    public String getHostVolumePath() { return hostVolumePath; }
    public void setHostVolumePath(String hostVolumePath) { this.hostVolumePath = hostVolumePath; }

    public String getDbVolumeName() { return dbVolumeName; }
    public void setDbVolumeName(String dbVolumeName) { this.dbVolumeName = dbVolumeName; }

    public boolean isStaticPorts() { return staticPorts; }
    public void setStaticPorts(boolean staticPorts) { this.staticPorts = staticPorts; }

    public Integer getAppHttpPort() { return appHttpPort; }
    public void setAppHttpPort(Integer appHttpPort) { this.appHttpPort = appHttpPort; }

    public Integer getAppHttpsPort() { return appHttpsPort; }
    public void setAppHttpsPort(Integer appHttpsPort) { this.appHttpsPort = appHttpsPort; }

    public Integer getDbPort() { return dbPort; }
    public void setDbPort(Integer dbPort) { this.dbPort = dbPort; }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }

    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }

    public Integer getMockHostPort() { return mockHostPort; }
    public void setMockHostPort(Integer mockHostPort) { this.mockHostPort = mockHostPort; }

    public boolean isSmtpEnabled() { return smtpEnabled; }
    public void setSmtpEnabled(boolean smtpEnabled) { this.smtpEnabled = smtpEnabled; }

    public Integer getSmtpHostPort() { return smtpHostPort; }
    public void setSmtpHostPort(Integer smtpHostPort) { this.smtpHostPort = smtpHostPort; }

    public Integer getSmtpUiHostPort() { return smtpUiHostPort; }
    public void setSmtpUiHostPort(Integer smtpUiHostPort) { this.smtpUiHostPort = smtpUiHostPort; }

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

    public String getWorkspacePathOverride() { return workspacePathOverride; }
    public void setWorkspacePathOverride(String workspacePathOverride) { this.workspacePathOverride = workspacePathOverride; }
}
