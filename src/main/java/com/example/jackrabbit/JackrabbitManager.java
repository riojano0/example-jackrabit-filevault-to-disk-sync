package com.example.jackrabbit;

import org.apache.jackrabbit.core.TransientRepository;

import javax.jcr.*;
import javax.jcr.observation.Event;
import javax.jcr.observation.ObservationManager;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import com.example.jackrabbit.vault.RepositoryToDiskSyncListener;
import com.example.jackrabbit.vault.domain.RepositorySync;
import com.example.jackrabbit.vault.domain.WorkspaceFilter;
import com.example.jackrabbit.vault.domain.WorkspaceFilterConfiguration;

public class JackrabbitManager {

   private static final JackrabbitManager jackrabbitInstance;

   public static final String DATA_JACKRABBIT_REPLICATION = "data/jackrabbit-replication";
   public static final String DATA_JACKRABBIT_REPLICATION_LOG = "data/jackrabbit-replication.log";

   static {
      try {
         jackrabbitInstance = new JackrabbitManager();

         ObservationManager observationManager = jackrabbitInstance.getSession().getWorkspace().getObservationManager();

         RepositorySync repositorySync = new RepositorySync();
         repositorySync.setName("sync-configuration");
         repositorySync.setReplicationDirectory(DATA_JACKRABBIT_REPLICATION);
         repositorySync.setSyncLogPath(DATA_JACKRABBIT_REPLICATION_LOG);
         WorkspaceFilterConfiguration workspaceFilterConfiguration = new WorkspaceFilterConfiguration();
         workspaceFilterConfiguration.setRoot("/");
         WorkspaceFilter workspaceFilter = new WorkspaceFilter();
         workspaceFilter.setType("exclude");
         workspaceFilter.setPattern("/__specificFolder");
         workspaceFilterConfiguration.setWorkspaceFilters(Collections.singletonList(workspaceFilter));
         repositorySync.setWorkspaceFilterConfiguration(workspaceFilterConfiguration);

         observationManager.addEventListener(
               new RepositoryToDiskSyncListener(repositorySync),
               Event.NODE_ADDED | Event.NODE_MOVED | Event.NODE_REMOVED | Event.PROPERTY_CHANGED,
               repositorySync.getWorkspaceFilterConfiguration().getRoot(),
               true,
               null,
               null, // Not filter any node type name
               false
         );

      } catch (RepositoryException e) {
         System.out.println("Unable to generate Instance");
         throw new RuntimeException(e);
      }
   }

   private final Repository repository;
   private final Session session;

   private JackrabbitManager() throws RepositoryException {
      File repoDir = new File("data/jackrabbit-repo");
      if (!repoDir.exists() && !repoDir.mkdirs()) {
         throw new RuntimeException("Unable to create");
      }

      File replicationDir = new File(DATA_JACKRABBIT_REPLICATION);
      if (!replicationDir.exists() && !replicationDir.mkdirs()) {
         throw new RuntimeException("Unable to create");
      }

      this.repository = new TransientRepository(repoDir);
      this.session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
   }

   public static JackrabbitManager getJackrabbitInstance() {
      return jackrabbitInstance;
   }

   public Repository getRepository() {
      return repository;
   }

   public Session getSession() {
      return session;
   }

   public Node getRootNode() throws RepositoryException {
      return session.getRootNode();
   }

   public void addNode(String name, String content) throws RepositoryException {
      Node rootNode = getRootNode();
      if (!rootNode.hasNode(name)) {
         Node newNode = rootNode.addNode(name);
         newNode.setProperty("content", content);
         session.save();
      } else {
         throw new RepositoryException("Node already exists");
      }
   }

   public void updateNode(String name, String content) throws RepositoryException {
      Node rootNode = getRootNode();
      if (rootNode.hasNode(name)) {
         Node node = rootNode.getNode(name);
         node.setProperty("content", content);
         session.save();
      } else {
         throw new RepositoryException("Node not exist");
      }
   }

   public void deleteNode(String name) throws RepositoryException {
      Node rootNode = getRootNode();
      if (rootNode.hasNode(name)) {
         Node node = rootNode.getNode(name);
         node.remove();
         session.save();
      } else {
         throw new RepositoryException("Node not exist");
      }
   }

   public void shutdown() {
      if (session != null) {
         session.logout();
      }
   }
}

