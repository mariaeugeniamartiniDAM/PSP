/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Procés fill: llegeix línies per System.in i compta les que contenen
 * la paraula clau (per defecte "ERROR").
 * Escriu el número per System.out.
 * Codi de sortida: 0 = OK, 1 = text buit.
 * @author María Eugenia Martini
 */
public class FiltreLog {
    
    public static void main(String[] args) {
    
        String paraulaClau = (args.length > 0) ? args [0] : "ERROR";

        int liniesLlegides = 0;
        int coincidencies = 0;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            String linia;
            while ((linia = br.readLine()) != null) { //acaba quan el para tanca el seu OutputStream
                if (!linia.isBlank()) {
                liniesLlegides++;
                }
                if (linia.contains(paraulaClau)) {
                coincidencies++;
                }
            }    
        } catch (IOException e){
            System.err.println("Error: no s'ha pogut llegir l'entrada (" + e.getMessage() + ").");
            System.exit(1);
        }

        // Validació d'entrada buida
        if (liniesLlegides == 0) {
            System.err.println("Error: text buit");
            System.exit(1);
        }

        // Resultat numèric en una única línia
        System.out.println(coincidencies);
        System.out.flush();
        System.exit(0);
    }
}
