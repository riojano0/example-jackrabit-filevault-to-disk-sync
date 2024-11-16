package com.example.jackrabbit;

import com.google.gson.Gson;
import spark.Spark;

import javax.jcr.*;

public class Main {

   private static JackrabbitManager jackrabbitManager = JackrabbitManager.getJackrabbitInstance();
   private static final Gson gson = new Gson();

   public static void main(String[] args) {
      Spark.port(8080);
      Spark.staticFiles.location("/public");

      Spark.get("/nodes", (req, res) -> {
         res.type("application/json");
         return NodeManager.getAllNodes();
      });

      Spark.post("/nodes", (req, res) -> {
         res.type("application/json");
         String body = req.body();
         return NodeManager.createNode(body);
      });

      Spark.get("/files/:fileName", (req, res) -> {
         String fileName = req.params(":fileName");
         Session fileSession = JackrabbitManager.getJackrabbitInstance().getSession();
         Node rootNode = fileSession.getRootNode();

         if (!rootNode.hasNode(fileName)) {
            res.status(404);
            return "File not found";
         }

         Node fileNode = rootNode.getNode(fileName);
         Node contentNode = fileNode.getNode("jcr:content");
         res.type(contentNode.getProperty("jcr:mimeType").getString());
         return contentNode.getProperty("jcr:data").getBinary().getStream();
      });

      Spark.awaitInitialization();

      Runtime.getRuntime().addShutdownHook(new Thread(jackrabbitManager::shutdown));
      System.out.println("Server is running on http://localhost:8080");
   }

}
