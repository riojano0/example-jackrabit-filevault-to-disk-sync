package com.example.jackrabbit.vault;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.sync.impl.SyncLog;
import org.apache.jackrabbit.vault.sync.impl.XmlAnalyzer;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/*
	RepositoryToDiskSync is a custom version of org.apache.jackrabbit.vault.sync.impl.TreeSync
	that allow to handle File types with mixinNodes
*/
public class RepositoryToDiskSync {

   private static final Logger log = LoggerFactory.getLogger(RepositoryToDiskSync.class);
   private static final String UPDATE_MARKER = "U";
   private static final String ADDED_MARKER = "A";

   private final SyncLog syncLog;
   private final FileFilter fileFilter;
   private final WorkspaceFilter wspFilter;
   private final String[] FULL_COVERAGE_NTS;
   private boolean preserveFileDate;

   public RepositoryToDiskSync(SyncLog syncLog, FileFilter fileFilter, WorkspaceFilter wspFilter) {
      this.preserveFileDate = true;
      this.FULL_COVERAGE_NTS = new String[]{"rep:AccessControl", "rep:Policy", "cq:Widget",
            "cq:EditConfig", "cq:WorkflowModel", "vlt:FullCoverage", "mix:language", "sling:OsgiConfig"};
      this.syncLog = syncLog;
      this.fileFilter = fileFilter;
      this.wspFilter = wspFilter;
   }

   public SyncLog getSyncLog() {
      return syncLog;
   }

   public WorkspaceFilter getWspFilter() {
      return wspFilter;
   }

   public void setPreserveFileDate(boolean preserveFileDate) {
      this.preserveFileDate = preserveFileDate;
   }

   public void sync(Node node, File dir) throws RepositoryException, IOException {
      validateFolderPath(dir);
      syncExecution(node, dir);
   }

   private void validateFolderPath(File file) {
      if (file == null || !StringUtils.containsIgnoreCase(file.getAbsolutePath(), "-replication")) {
         String currentPath = file != null ? file.getAbsolutePath() : "none";
         throw new IllegalArgumentException("Current Path: " + currentPath
               + " Must create a folder that contain the '-replication' keyword for allow to sync");
      }
   }

   public void syncSingle(Node parentNode, Node node, File file, boolean recursive) throws RepositoryException, IOException {
      validateFolderPath(file);

      Entry entry;
      if (node == null) {
         entry = new Entry(parentNode, file);
         entry.jcrType = Type.MISSING;
      } else {
         entry = new Entry(parentNode, file.getParentFile(), node);
         entry.jcrType = getJcrType(node);
      }

      entry.fsType = getFsType(file);
      entry.fStat = getFilterStatus(entry.getJcrPath());
      syncExecution(entry, recursive);
   }

   private FilterStatus getFilterStatus(String path) {
      if (path == null) {
         return FilterStatus.CONTAINED;
      } else if (wspFilter.contains(path)) {
         return FilterStatus.CONTAINED;
      } else if (wspFilter.covers(path)) {
         return FilterStatus.COVERED;
      } else {
         return wspFilter.isAncestor(path) ? FilterStatus.ANCESTOR : FilterStatus.OUTSIDE;
      }
   }

   private Type getJcrType(Node node) throws RepositoryException {
      if (node == null) {
         return Type.MISSING;
      } else if (node.isNodeType("{http://www.jcp.org/jcr/nt/1.0}file")) {
         return Type.FILE;
      } else {
         String[] fullCoverageNts = FULL_COVERAGE_NTS;

         for (int i = 0; i < ArrayUtils.getLength(fullCoverageNts); ++i) {
            String nt = fullCoverageNts[i];

            try {
               if (node.isNodeType(nt)) {
                  return Type.FULL_COVERAGE;
               }
            } catch (RepositoryException var7) {
               //Do Nothing
            }
         }

         return node.isNodeType("{http://www.jcp.org/jcr/nt/1.0}hierarchyNode") ? Type.DIRECTORY : Type.UNSUPPORTED;
      }
   }

   private Type getFsType(File file) {
      if (!file.exists()) {
         return Type.MISSING;
      } else if (file.isDirectory()) {
         return file.getName().endsWith(".dir") ? Type.UNSUPPORTED : Type.DIRECTORY;
      } else if (!file.isFile()) {
         return Type.UNSUPPORTED;
      } else {
         try {
            SerializationType type = XmlAnalyzer.analyze(new FileInputSource(file));
            if (type == SerializationType.XML_DOCVIEW) {
               return Type.UNSUPPORTED;
            }
         } catch (IOException e) {
            log.warn("Unable to analyze {}: {}", file.getAbsolutePath(), e.toString());
            return Type.UNSUPPORTED;
         }

         return Type.FILE;
      }
   }

   private void syncExecution(Node node, File dir) throws RepositoryException, IOException {
      Map<String, Entry> jcrEntries = new HashMap<>();
      Map<String, Entry> fsEntries = new HashMap<>();
      NodeIterator iter = node.getNodes();

      Entry entry;
      while (iter.hasNext()) {
         Node child = iter.nextNode();
         entry = new Entry(node, dir, child);
         entry.jcrType = getJcrType(child);
         entry.fStat = getFilterStatus(entry.getJcrPath());
         jcrEntries.put(entry.jcrName, entry);
         fsEntries.put(entry.file.getName(), entry);
      }

      if (dir.isDirectory()) {
         File[] listFiles = dir.listFiles(fileFilter);

         for (int i = 0; i < ArrayUtils.getLength(listFiles); ++i) {
            File file = listFiles[i];
            Entry entryFile = (Entry) fsEntries.get(file.getName());
            if (entryFile == null) {
               entryFile = new Entry(node, file);
            }

            entryFile.fsType = getFsType(file);
            entryFile.fStat = getFilterStatus(entryFile.getJcrPath());
            jcrEntries.put(entryFile.jcrName, entryFile);
            fsEntries.put(entryFile.file.getName(), entryFile);
         }
      }

      Iterator entryIterator = jcrEntries.values().iterator();

      while (entryIterator.hasNext()) {
         entry = (Entry) entryIterator.next();
         syncExecution(entry, true);
      }
   }

   private void syncExecution(Entry entry, boolean recursive) throws RepositoryException, IOException {
      try {
         if (entry.fStat == FilterStatus.OUTSIDE) {
            if (entry.fsType == Type.FILE) {
               deleteFile(entry);
            } else if (entry.fsType == Type.DIRECTORY) {
               deleteDirectory(entry);
            }
         } else if (entry.jcrType == Type.DIRECTORY) {
            if (entry.fsType == Type.DIRECTORY) {
               if (recursive) {
                  syncExecution(entry.node, entry.file);
               }
            } else if (entry.fsType == Type.MISSING) {
               createDirectory(entry);
               if (recursive) {
                  syncExecution(entry.node, entry.file);
               }
            } else {
               logConflict(entry);
            }
         } else if (entry.jcrType == Type.FILE) {
            if (entry.fsType == Type.FILE) {
               if (entry.fStat == FilterStatus.CONTAINED) {
                  syncFiles(entry);
               }
            } else if (entry.fsType == Type.MISSING) {
               if (entry.fStat == FilterStatus.CONTAINED) {
                  writeFile(entry);
               }
            } else {
               logConflict(entry);
            }
         } else if (entry.jcrType == Type.FULL_COVERAGE) {
            log.debug("refusing to traverse full coverage aggregates {}", entry.node.getPath());
         } else if (entry.jcrType == Type.UNSUPPORTED) {
            log.debug("refusing to traverse unsupported {}", entry.node.getPath());
         } else if (entry.jcrType == Type.MISSING) {
            if (entry.fsType == Type.FILE) {
               if (entry.fStat == FilterStatus.CONTAINED) {
                  deleteFile(entry);
               }
            } else if (entry.fsType == Type.DIRECTORY) {
               if (entry.fStat == FilterStatus.CONTAINED) {
                  deleteDirectory(entry);
               }
            } else {
               logConflict(entry);
            }
         }
      } catch (IOException | RepositoryException e) {
         log.error("Problem on sync entry: {}", e.getMessage(), e);
      }

   }

   private void deleteFile(Entry entry) throws IOException {
      String path = entry.file.getAbsolutePath();
      FileUtils.forceDelete(entry.file);
      logSync("D file://%s", path);
   }

   private void deleteDirectory(Entry entry) throws IOException, RepositoryException {
      deleteRecursive(entry.file, entry.getJcrPath());
   }

   private void deleteRecursive(File directory, String jcrPath) throws IOException {
      String message;
      if (!directory.exists()) {
         message = directory + " does not exist";
         throw new IllegalArgumentException(message);
      } else if (!directory.isDirectory()) {
         message = directory + " is not a directory";
         throw new IllegalArgumentException(message);
      } else {
         File[] files = directory.listFiles();
         if (files == null) {
            throw new IOException("Failed to list contents of " + directory);
         } else {

            for (int i = 0; i < ArrayUtils.getLength(files); ++i) {
               File file = files[i];
               String subPath = jcrPath + "/" + PlatformNameFormat.getPlatformName(file.getName());
               if (file.isDirectory()) {
                  deleteRecursive(file, subPath);
               } else {
                  FileUtils.forceDelete(file);
                  logSync("D file://%s", new Object[]{file.getAbsolutePath()});
               }
            }

            directory.delete();
            logSync("D file://%s/", new Object[]{directory.getAbsolutePath()});
         }
      }
   }

   private void createDirectory(Entry e) throws RepositoryException {
      e.file.mkdir();
      logSync("A file://%s/", new Object[]{e.getFsPath()});
   }

   private void syncFiles(Entry entry) throws RepositoryException, IOException {
      writeFile(entry);
   }

   private void writeFile(Entry entry) throws IOException, RepositoryException {
      File entryFile = entry.file;
      String action = entryFile.exists() ? UPDATE_MARKER : ADDED_MARKER;
      Node entryNode = entry.node;
      Binary binary = null;
      InputStream inputStream = null;
      FileOutputStream fileOutputStream = null;

      try {
         long entryNodeLastModifiedDateTimeInMillis = getEntryNodeLastModifiedDateTimeInMillis(entryNode);

         if (entryFile.lastModified() != entryNodeLastModifiedDateTimeInMillis) {
            binary = entryNode.getProperty("jcr:content/jcr:data").getBinary();
            inputStream = binary.getStream();
            fileOutputStream = FileUtils.openOutputStream(entryFile);
            IOUtils.copy(inputStream, fileOutputStream);

            // Preserve Date
            if (preserveFileDate && entryNodeLastModifiedDateTimeInMillis != 0) {
               entryFile.setLastModified(entryNodeLastModifiedDateTimeInMillis);
            }

            logSync("%s file://%s", action, entryFile.getAbsolutePath());
         }
      } finally {
         IOUtils.closeQuietly(inputStream);
         IOUtils.closeQuietly(fileOutputStream);
         if (binary != null) {
            binary.dispose();
         }
      }
   }

   private long getEntryNodeLastModifiedDateTimeInMillis(Node entryNode) throws RepositoryException {
      Property entryNodeLastModifiedProperty = entryNode.getProperty("jcr:content/jcr:lastModified");
      Calendar entryNodeLastModifiedDate = entryNodeLastModifiedProperty != null
            ? entryNodeLastModifiedProperty.getDate()
            : null;
      return entryNodeLastModifiedDate != null ?
            entryNodeLastModifiedDate.getTimeInMillis()
            : 0;
   }

   private void logConflict(Entry e) {
      log.error("Sync conflict. JCR type is {}, but FS type is {}", e.jcrType, e.fsType);
   }

   private void logSync(String messagePattern, Object... args) {
      if (syncLog != null) {
         syncLog.log(messagePattern, args);
      }
   }

   enum FilterStatus {
      CONTAINED,
      COVERED,
      ANCESTOR,
      OUTSIDE
   }

   enum Type {
      FILE,
      DIRECTORY,
      MISSING,
      UNSUPPORTED,
      FULL_COVERAGE
   }

   private static final class Entry {
      private final File file;
      private final Node parentNode;
      private final String jcrName;
      private Type fsType;
      private Node node;
      private Type jcrType;
      private FilterStatus fStat;

      private Entry(Node parentNode, File file) {
         this.fsType = Type.MISSING;
         this.jcrType = Type.MISSING;
         this.fStat = FilterStatus.OUTSIDE;
         this.parentNode = parentNode;
         this.file = file;
         this.jcrName = PlatformNameFormat.getRepositoryName(file.getName());
      }

      private Entry(Node parentNode, File parentDir, Node node) throws RepositoryException {
         this.fsType = Type.MISSING;
         this.jcrType = Type.MISSING;
         this.fStat = FilterStatus.OUTSIDE;
         this.parentNode = parentNode;
         this.node = node;
         this.jcrName = node.getName();
         this.file = new File(parentDir, PlatformNameFormat.getPlatformName(this.jcrName));
      }

      public String toString() {
         return "Entry" +
               "{fsName='" + this.file.getName() + '\'' +
               ", fsType=" + this.fsType +
               ", jcrName='" + this.jcrName + '\'' +
               ", jcrType=" + this.jcrType +
               '}';
      }

      public String getFsPath() {
         return this.file.getAbsolutePath();
      }

      public String getJcrPath() throws RepositoryException {
         if (this.parentNode == null && this.node == null) {
            return null;
         } else {
            return this.node == null ? this.parentNode.getPath() + "/" + this.jcrName : this.node.getPath();
         }
      }
   }

}
