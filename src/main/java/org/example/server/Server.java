

package org.example.server;

import org.example.dao.UserDAO;
import org.example.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Server {

    private static final Logger journal = LoggerFactory.getLogger(Server.class);
    public static final int PORT = 5000;


    private static final Map<String, ClientHandler> sessionsActives = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        UserDAO daoUtilisateur = new UserDAO();
        daoUtilisateur.trouverTous().forEach(u ->
                daoUtilisateur.mettreAJourStatut(u.getId(), User.Statut.HORS_LIGNE));

        journal.info("Serveur démarré sur le port {}", PORT);

        try (ServerSocket socketServeur = new ServerSocket(PORT)) {
            while (true) {
                Socket socketClient = socketServeur.accept();
                journal.info("Nouvelle connexion depuis {}", socketClient.getInetAddress());
                ClientHandler gestionnaire = new ClientHandler(socketClient, sessionsActives);
                Thread thread = new Thread(gestionnaire);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException e) {
            journal.error("Erreur serveur : {}", e.getMessage());
        }
    }



    public static void register(String nomUtilisateur, ClientHandler gestionnaire) {
        sessionsActives.put(nomUtilisateur, gestionnaire);
    }

    public static void unregister(String nomUtilisateur) {
        sessionsActives.remove(nomUtilisateur);
    }

    public static ClientHandler getHandler(String nomUtilisateur) {
        return sessionsActives.get(nomUtilisateur);
    }

    public static boolean estEnLigne(String nomUtilisateur) {
        return sessionsActives.containsKey(nomUtilisateur);
    }

    
    public static void broadcastUserList() {
        sessionsActives.values().forEach(ClientHandler::envoyerListeUtilisateurs);
    }
}