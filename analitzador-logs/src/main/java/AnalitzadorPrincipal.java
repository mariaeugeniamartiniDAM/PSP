/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Procés pare. Llança dos fills FiltreLog en paral·lel
 * (ERROR I WARNING), els envia el mateix text i recull els resultats.
 * 
 * @author María Eugenia Martini
 */
public class AnalitzadorPrincipal {
    
    // Fitxer on es redirigeix el stderr dels fills
    static final File FITXER_ERRORS = new File("error_filtre.log");
    
    static final String TEXT_PROVA =
            "2024-05-01 10:00:01 INFO  Servidor iniciat\n"
            + "2024-05-01 10:00:05 ERROR Connexio rebutjada\n"
            + "2024-05-01 10:00:07 WARNING Memoria al 80%\n"
            + "2024-05-01 10:01:10 WARNING Latencia alta\n"
            + "2024-05-01 10:02:15 ERROR Timeout a la BD\n"
            + "2024-05-01 10:02:16 INFO  Reintentant\n"
            + "2024-05-01 10:03:20 WARNING Disc al 90%\n"
            + "2024-05-01 10:04:25 ERROR Fitxer no trobat\n"
            + "2024-05-01 10:05:30 WARNING Certificat proper a caducar\n"
            + "2024-05-01 10:06:35 WARNING Intent de login fallit\n"
            + "2024-05-01 10:07:40 INFO  Fi de la jornada\n";

    // Resultat d'un fill: valor comptat + codi de sortida.
    static class ResultatFill {
        final int valor;
        final int exitCode;
        ResultatFill(int valor, int exitCode) {
            this.valor = valor;
            this.exitCode = exitCode;
        }
    }
    
    // Construeix la comanda per executar FiltreLog amb el mateix JVM i classpath.
    static List <String> comandaFiltre(String paraula) throws URISyntaxException {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        
        // Carpeta/jar d'on s'ha carregat FiltreLog.class
        String ubicacio = Paths.get(FiltreLog.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String classpath = ubicacio + File.pathSeparator + System.getProperty("java.class.path");
        
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add("FiltreLog");
        cmd.add(paraula);
        return cmd;        
    }
    
    //Llança un fill amb stderr redirigit a errors_filtre.log
    static Process llancaFill(String paraula) throws IOException, URISyntaxException{
        ProcessBuilder pb = new ProcessBuilder(comandaFiltre(paraula));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(FITXER_ERRORS)); // appendTo perquè els dos fills comparteixen el mateix fitxer sense trepitjar-se
        return pb.start();
    }
    
    /**
     * Tasca concurrent per a cada fill:
     * 1. Un fil auxiliar ESCRIU el text al fill i tanca el flux;
     * 2. Aquest fil LLEGEIX la sortida del fill alhora, sense deadlock de buffers;
     * 3. waitFor() i exitValue().
     */
    static class TascaFill implements Callable<ResultatFill> {
        private final Process proces;
        private final String text;
        
        TascaFill(Process proces, String text) {
            this.proces = proces;
            this.text = text;
        }
        
        @Override
        public ResultatFill call() throws Exception {
            Thread escriptor = new Thread (() -> { //evita que el pare es quedi bloquejat escrivint
                try (OutputStream out = proces.getOutputStream()) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    System.err.println("Error escrivint al fill: " + e.getMessage());
                }
            });
            escriptor.start();
            
            //Lectura de la resposta del fill 
            String resposta = null; 
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proces.getInputStream(), StandardCharsets.UTF_8))) {
                resposta = br.readLine();
            }
            
            escriptor.join();
            proces.waitFor(); //espera finalització del fill
            int exit = proces.exitValue();
            
            int valor = 0;
            if (resposta !=null) {
                try { 
                    valor = Integer.parseInt(resposta.trim());
                } catch (NumberFormatException e) {
                    exit = (exit == 0) ? 2 : exit;
                }
            }
            else if (exit == 0) {
                exit = 2;
            }
            return new ResultatFill(valor, exit);
        }
    }
    
    public static void main (String[] args) {
        int exitGlobal = 0;
        int errors = 0;
        int warnings = 0;
        
        //Neteja el log d'execucions anteriors
        if (FITXER_ERRORS.exists()) {
            FITXER_ERRORS.delete();
        }
        
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // es llancen els dos fills abans d'esperar cap resultat, execució simultània
            Process fillA = llancaFill("ERROR");
            Process fillB = llancaFill("WARNING");
            
            Future<ResultatFill> futA = pool.submit(new TascaFill(fillA, TEXT_PROVA));
            Future<ResultatFill> futB = pool.submit(new TascaFill(fillB, TEXT_PROVA));
            
            ResultatFill resA = futA.get();
            ResultatFill resB = futB.get();
            
            errors = resA.valor;
            warnings = resB.valor;
            
            //Estat final segons els codis de sortida
            System.out.println("Fill A (ERROR) -> exit code: " + resA.exitCode
                                + (resA.exitCode == 0 ? " (OK)": "(ERROR, mireu errors_filtre.log)"));
            System.out.println("FILL B (WARNING) -> exit code: " + resB.exitCode 
                                + (resB.exitCode == 0 ? " (OK)" : " (ERROR, mireu errors_filtre.log)"));
            
            exitGlobal = (resA.exitCode != 0) ? resA.exitCode : resB.exitCode;
        } catch (Exception e) {
            System.out.println("Error en l'orquestració: " + e.getMessage());
            exitGlobal = 1; 
        } finally {
            pool.shutdown();
        }
        
        //Format de la última línia impresa
        System.out.println("RESULTAT: ERRORS=" + errors + " | WARNINGS=" + warnings + " | EXIT_CODE=" + exitGlobal);
    }
    
}
