package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_action")
public class CustomActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_key", nullable = false, unique = true)
    private String actionKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_role", nullable = false)
    private String targetRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String command;

    @Column(name = "working_dir")
    private String workingDir;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 300;

    @Column(name = "after_action")
    private String afterAction;

    @Column(name = "auto_run", nullable = false)
    private boolean autoRun = false;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    @Column(name = "execution_type", nullable = false)
    private String executionType = "EXEC";

    @Column(name = "allowed_exit_codes")
    private String allowedExitCodes;

    @Column(name = "run_as_user")
    private String runAsUser;

    @Column(name = "verbose", nullable = false)
    private boolean verbose = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_config_id")
    private ImageConfigEntity imageConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private EnvironmentEntity environment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getAfterAction() { return afterAction; }
    public void setAfterAction(String afterAction) { this.afterAction = afterAction; }

    public boolean isAutoRun() { return autoRun; }
    public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public String getExecutionType() { return executionType; }
    public void setExecutionType(String executionType) { this.executionType = executionType; }

    public String getAllowedExitCodes() { return allowedExitCodes; }
    public void setAllowedExitCodes(String allowedExitCodes) { this.allowedExitCodes = allowedExitCodes; }

    public String getRunAsUser() { return runAsUser; }
    public void setRunAsUser(String runAsUser) { this.runAsUser = runAsUser; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public ImageConfigEntity getImageConfig() { return imageConfig; }
    public void setImageConfig(ImageConfigEntity imageConfig) { this.imageConfig = imageConfig; }

    public EnvironmentEntity getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentEntity environment) { this.environment = environment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
