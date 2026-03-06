package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String nomUtilisateur;

    @Column(name = "password", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Statut statut = Statut.HORS_LIGNE;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    public enum Statut { EN_LIGNE, HORS_LIGNE }

    public User() {}

    public User(String nomUtilisateur, String motDePasse) {
        this.nomUtilisateur = nomUtilisateur;
        this.motDePasse     = motDePasse;
        this.statut         = Statut.HORS_LIGNE;
        this.dateCreation   = LocalDateTime.now();
    }

    public Long getId()                                    { return id; }
    public String getNomUtilisateur()                      { return nomUtilisateur; }
    public void setNomUtilisateur(String nomUtilisateur)   { this.nomUtilisateur = nomUtilisateur; }
    public String getMotDePasse()                          { return motDePasse; }
    public void setMotDePasse(String motDePasse)           { this.motDePasse = motDePasse; }
    public Statut getStatut()                              { return statut; }
    public void setStatut(Statut statut)                   { this.statut = statut; }
    public LocalDateTime getDateCreation()                 { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation){ this.dateCreation = dateCreation; }
}