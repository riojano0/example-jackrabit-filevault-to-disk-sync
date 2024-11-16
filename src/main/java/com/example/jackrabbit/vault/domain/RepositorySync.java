package com.example.jackrabbit.vault.domain;

import org.apache.commons.lang3.StringUtils;

public class RepositorySync {

	private String name;
	private String serverId;
	private String replicationDirectory;
	private WorkspaceFilterConfiguration workspaceFilterConfiguration;
	private String syncLogPath;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getServerId() {
		// Default is JACKRABBIT
		return StringUtils.isBlank(serverId) ? "JACKRABBIT" : serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public String getReplicationDirectory() {
		return replicationDirectory;
	}

	public void setReplicationDirectory(String replicationDirectory) {
		this.replicationDirectory = replicationDirectory;
	}

	public WorkspaceFilterConfiguration getWorkspaceFilterConfiguration() {
		return workspaceFilterConfiguration;
	}

	public void setWorkspaceFilterConfiguration(WorkspaceFilterConfiguration workspaceFilterConfiguration) {
		this.workspaceFilterConfiguration = workspaceFilterConfiguration;
	}

	public String getSyncLogPath() {
		return syncLogPath;
	}

	public void setSyncLogPath(String syncLogPath) {
		this.syncLogPath = syncLogPath;
	}
}
