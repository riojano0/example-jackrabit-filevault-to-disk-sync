package com.example.jackrabbit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class NodeManager {
   private static final JackrabbitManager manager = JackrabbitManager.getJackrabbitInstance();
   private static final Gson gson = new Gson();


   public static String getAllNodes() throws RepositoryException {
      Session session = manager.getSession();
      Node rootNode = session.getRootNode();
      List<JsonObject> nodesList = new ArrayList<>();

      for (NodeIterator it = rootNode.getNodes(); it.hasNext(); ) {
         Node node = it.nextNode();

         JsonObject nodeJson = new JsonObject();
         nodeJson.addProperty("name", node.getName());
         if (node.hasProperty("content")) {
            nodeJson.addProperty("content", node.getProperty("content").getString());
         }
         String fileName = node.getName() + ".txt";
         if (rootNode.hasNode(fileName)) {
            nodeJson.addProperty("fileLink", "/files/" + fileName);
         }

         nodesList.add(nodeJson);
      }

      return gson.toJson(nodesList);
   }

   public static String createNode(String content) throws RepositoryException {
      Session session = manager.getSession();
      Node rootNode = session.getRootNode();

      String nodeName = "node-" + System.currentTimeMillis();
      Node newNode = rootNode.addNode(nodeName, "nt:unstructured");
      newNode.setProperty("content", content);

      createFileNode(rootNode, nodeName + ".txt", content);

      session.save();
      return "Node created: " + nodeName;
   }

   private static void createFileNode(Node rootNode, String fileName, String content) throws RepositoryException {
      Node fileNode = rootNode.addNode(fileName, "nt:file");
      Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
      contentNode.setProperty("jcr:data", manager.getSession().getValueFactory().createBinary(new ByteArrayInputStream(content.getBytes())));
      contentNode.setProperty("jcr:mimeType", "text/plain");
   }

}

