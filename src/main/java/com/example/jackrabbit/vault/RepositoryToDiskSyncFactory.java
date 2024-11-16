package com.example.jackrabbit.vault;

import java.io.File;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.sync.impl.SyncLog;

import com.example.jackrabbit.vault.domain.RepositorySync;
import com.example.jackrabbit.vault.domain.WorkspaceFilter;
import com.example.jackrabbit.vault.domain.WorkspaceFilterConfiguration;

class RepositoryToDiskSyncFactory {

	static RepositoryToDiskSync buildRepositoryToDiskSync(RepositorySync repositorySync) {
		DefaultWorkspaceFilter workspaceFilter = new DefaultWorkspaceFilter();
		WorkspaceFilterConfiguration workspaceFilterConfiguration = repositorySync.getWorkspaceFilterConfiguration();
		PathFilterSet pathFilterSet = new PathFilterSet(workspaceFilterConfiguration.getRoot());
		List<WorkspaceFilter> workspaceFilters = workspaceFilterConfiguration.getWorkspaceFilters();
		try {
			configurePathFilterSet(pathFilterSet, workspaceFilters);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		workspaceFilter.add(pathFilterSet);
		RepositoryToDiskSync repositoryToDiskSync = new RepositoryToDiskSync(
				getSyncLogger(repositorySync.getSyncLogPath()), null, workspaceFilter);
		repositoryToDiskSync.setPreserveFileDate(true);

		return repositoryToDiskSync;
	}

	private static void configurePathFilterSet(PathFilterSet pathFilterSet, List<WorkspaceFilter> workspaceFilters) throws ConfigurationException {
		if (CollectionUtils.isNotEmpty(workspaceFilters)) {
			for (WorkspaceFilter filter : workspaceFilters) {
				if (StringUtils.equalsIgnoreCase(filter.getType(), "include")) {
					pathFilterSet.addInclude(new DefaultPathFilter(filter.getPattern()));
				} else if (StringUtils.equalsIgnoreCase(filter.getType(), "exclude")) {
					pathFilterSet.addExclude(new DefaultPathFilter(filter.getPattern()));
				}
			}
		}
	}

	private static SyncLog getSyncLogger(String syncLogPath) {
		if (StringUtils.isNotBlank(syncLogPath)) {
			return new SyncLog(new File(syncLogPath));
		}

		return null;
	}

}
