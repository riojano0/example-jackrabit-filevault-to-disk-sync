package com.example.jackrabbit.vault.domain;

import java.util.List;

public class WorkspaceFilterConfiguration {

	private String root;
	private List<WorkspaceFilter> workspaceFilters;

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}

	public List<WorkspaceFilter> getWorkspaceFilters() {
		return workspaceFilters;
	}

	public void setWorkspaceFilters(List<WorkspaceFilter> workspaceFilters) {
		this.workspaceFilters = workspaceFilters;
	}
}
