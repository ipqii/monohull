package io.monohull.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Prefix must match application.yml, where the action list lives under monohull.actions.
// It was once "app" while the yml key sat elsewhere — a silent mismatch that left the
// list empty, so yml edits to built-in actions never seeded. Built-ins resync on boot.
@Component
@ConfigurationProperties(prefix = "monohull")
public class ActionProperties {

    private List<ActionDefinition> actions = new ArrayList<>();

    public List<ActionDefinition> getActions() { return actions; }
    public void setActions(List<ActionDefinition> actions) { this.actions = actions; }

    public static class ActionDefinition {
        private String id;
        private String name;
        private String description;
        private String targetRole;
        private String command;
        private String workingDir;
        private String afterAction;
        private boolean autoRun = false;
        private int timeout = 300;
        private String executionType = "EXEC";
        private String runAsUser;
        private boolean verbose = false;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

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

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }

        public String getAfterAction() { return afterAction; }
        public void setAfterAction(String afterAction) { this.afterAction = afterAction; }

        public boolean isAutoRun() { return autoRun; }
        public void setAutoRun(boolean autoRun) { this.autoRun = autoRun; }

        public String getExecutionType() { return executionType; }
        public void setExecutionType(String executionType) { this.executionType = executionType; }

        public String getRunAsUser() { return runAsUser; }
        public void setRunAsUser(String runAsUser) { this.runAsUser = runAsUser; }

        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean verbose) { this.verbose = verbose; }
    }
}
