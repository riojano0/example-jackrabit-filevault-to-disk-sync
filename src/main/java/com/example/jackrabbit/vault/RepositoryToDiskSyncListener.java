package com.example.jackrabbit.vault;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.example.jackrabbit.JackrabbitManager;
import com.example.jackrabbit.vault.domain.RepositorySync;

public class RepositoryToDiskSyncListener implements EventListener {

	public static final Map<String, Boolean> initializedSyncMap = new HashMap<>();
	public static final Map<String, RepositoryToDiskSyncListener> repositoryToDiskSyncListenerMap = new HashMap<>();
	
	private static final Logger logger = LoggerFactory.getLogger(RepositoryToDiskSyncListener.class);
	private final JackrabbitManager jackrabbitManager = JackrabbitManager.getJackrabbitInstance();
	private final RepositorySync repositorySync;
	private final RepositoryToDiskSync repositoryToDiskSync;

	public RepositoryToDiskSyncListener(RepositorySync repositorySync) {
		this.repositorySync = repositorySync;
		this.repositoryToDiskSync = buildRepositoryToDiskSync(repositorySync);
		repositoryToDiskSyncListenerMap.put(repositorySync.getName() +"-"+ repositorySync.getServerId(), this);
	}

	@Override
	public void onEvent(EventIterator eventIterator) {
		Repository repository = jackrabbitManager.getRepository();
		Session session = null;

		try {
			session = repository.login();

			while (eventIterator.hasNext()) {
				Event event = eventIterator.nextEvent();
				String nodePath = getNodePath(event);
				repositoryToDiskSync.syncSingle(
						session.getRootNode(),
						session.getNode(nodePath),
						getTargetLocation(event),
						true);
			}
		} catch (RepositoryException | IOException e) {
			logger.error("Can Sync error:{}", e.getMessage(), e);
		} finally {
			if (session != null) {
				session.logout();
			}
		}
	}
	
	public void initializeSyncFolder(String pathTarget) {
		String pathTargetRelative = !StringUtils.startsWith(pathTarget, "/") 
				? "/" + pathTarget 
				: pathTarget;
		Repository repository = JackrabbitManager.getJackrabbitInstance().getRepository();
		Session session = null;

		try {
			session = repository.login();
			String root = repositorySync.getWorkspaceFilterConfiguration().getRoot();
			String rootLocation = ("/").equals(root) ? "" : root;
			String folderLocation = rootLocation + pathTargetRelative;
			repositoryToDiskSync.syncSingle(
					session.getRootNode(),
					session.getNode(folderLocation),
					getTargetLocation(folderLocation),
					true);
		} catch (RepositoryException | IOException e) {
			logger.error("Can Sync error:{}", e.getMessage(), e);
		} finally {
			if (session != null) {
				session.logout();
			}
		}
	}

	public static void initializeSyncAll(String repositorySyncName) {
		RepositoryToDiskSyncListener repositoryToDiskSyncListenerInstance = repositoryToDiskSyncListenerMap.get(repositorySyncName);
		if(repositoryToDiskSyncListenerInstance != null) {
			Repository repository = JackrabbitManager.getJackrabbitInstance().getRepository();
			Session session = null;

			try {
				Boolean alreadyStarted = initializedSyncMap.get(repositorySyncName);
				if (alreadyStarted == null || !alreadyStarted) {
					logger.info("Start initialize Sync All from RepositorySync: {}", repositorySyncName);
					session = repository.login();
					repositoryToDiskSyncListenerInstance.repositoryToDiskSync
							.sync(session.getRootNode(), 
									new File(repositoryToDiskSyncListenerInstance.repositorySync.getReplicationDirectory()));
					initializedSyncMap.put(repositorySyncName, true);
				} else {
					logger.info("Was already started Sync All from RepositorySync: {}", repositorySyncName);
				}
			} catch (RepositoryException | IOException e) {
				logger.error("Can Initialize Sync error:{}", e.getMessage(), e);
			} finally {
				if (session != null) {
					session.logout();
				}
			}
		} else {
			logger.error("Not configure RepositorySync with name: {}", repositorySyncName);
		}
	}

	private File getTargetLocation(Event event) throws RepositoryException {
		return new File(repositorySync.getReplicationDirectory() + getNodePath(event));
	}

	private File getTargetLocation(String pathTarget) {
		return new File(repositorySync.getReplicationDirectory() + pathTarget);
	}

	private String getNodePath(Event event) throws RepositoryException {
		String path = event.getPath();
		if (isEventThatNeedTheParentRoot(event.getType())) {
			path = path.substring(0, path.lastIndexOf("/"));
		}

		return path;
	}

	private boolean isEventThatNeedTheParentRoot(int eventType) {
		return Event.NODE_REMOVED == eventType
				|| Event.PROPERTY_CHANGED == eventType
				|| Event.PROPERTY_ADDED == eventType
				|| Event.PROPERTY_REMOVED == eventType;
	}

	private RepositoryToDiskSync buildRepositoryToDiskSync(RepositorySync repositorySync) {
		return RepositoryToDiskSyncFactory.buildRepositoryToDiskSync(repositorySync);
	}

}