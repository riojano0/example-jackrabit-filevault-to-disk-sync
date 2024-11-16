package com.example.jackrabbit.vault.domain;

import java.util.List;

public class RepositorySyncConfiguration {

	private String enable = "false";
	private List<RepositorySync> repositorySyncList;

	public String getEnable() {
		return enable;
	}

	public void setEnable(String enable) {
		this.enable = enable;
	}

	public List<RepositorySync> getRepositorySyncList() {
		return repositorySyncList;
	}

	public void setRepositorySyncList(List<RepositorySync> repositorySyncList) {
		this.repositorySyncList = repositorySyncList;
	}
}
