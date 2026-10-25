package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.shared.InputData;

/**
 * Servidor WebSocket autoritatiu.
 *
 * Protocol (JSON):
 *  - Client -> Server:  { "type": "clientInput", "keys": [...], "aimX": n, "aimY": n, "shoot": bool }
 *  - Server -> Clients: { "type": "serverData", "clientName": "...", "clientsList": [...],
 *                         "tanksList": [...], "bulletsList": [...], "obstaclesList": [...] }
 *  - Server -> Clients: { "type": "countdown", "value": n }
 *  - Server -> Clients: { "type": "gameOver", "winner": "...", "loser": "..." }
 *
 * El servidor es l'unica font de veritat de l'estat del joc: els clients
 * nomes envien intencions (clientInput) i reben l'estat complet a 30 fps.
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    /** Llista de noms disponibles per als clients connectats. */
    private static final List<String> PLAYER_NAMES = Arrays.asList(
        "Bulbasaur", "Charizard", "Blaziken", "Umbreon", "Mewtwo", "Pikachu", "Wartortle"
    );

    /** Llista de colors disponibles per als clients connectats. */
    private static final List<String> PLAYER_COLORS = Arrays.asList(
        "GREEN", "ORANGE", "RED", "GRAY", "PURPLE", "YELLOW", "BLUE"
    );

    /** Nombre de clients necessaris per iniciar el compte enrere. */
    private static final int REQUIRED_CLIENTS = 2;

    /** Mides del tauler de joc (coincideixen amb el Canvas del client). */
    private static final double BOARD_WIDTH = 600;
    private static final double BOARD_HEIGHT = 420;

    /** Escenari per defecte quan cap client no n'ha triat cap. */
    private static final String DEFAULT_SCENARIO = "arena";

    /** Escenari triat: ultim valor "setScenario" vàlid rebut abans de crear la partida. */
    private String chosenScenario = DEFAULT_SCENARIO;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENTS_LIST = "clientsList";
    private static final String K_TANKS_LIST = "tanksList";
    private static final String K_BULLETS_LIST = "bulletsList";
    private static final String K_OBSTACLES_LIST = "obstaclesList";
    private static final String K_WINNER = "winner";
    private static final String K_LOSER = "loser";
    private static final String K_SCENARIO = "scenario";

    // Tipus de missatge
    private static final String T_CLIENT_INPUT = "clientInput";   // client -> server
    private static final String T_SERVER_DATA = "serverData";     // server -> clients
    private static final String T_COUNTDOWN = "countdown";         // server -> clients
    private static final String T_GAME_OVER = "gameOver";          // server -> clients

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    /** Estat autoritatiu de la partida; null = no hi ha partida en curs. */
    private Game game = null;

    private volatile boolean countdownRunning = false;

    /** Freqüència d'actualitzacio i enviament de l'estat (frames per segon). */
    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     *
     * @param address adreça i port d'escolta del servidor
     */
    public Main(InetSocketAddress address) {
        super(address);
        setReuseAddr(true); // permet rebind immediat del port despres d'aturada/restart
        this.clients = new ClientRegistry(PLAYER_NAMES);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    /**
     * Obté el color per un nom de client.
     *
     * @return color assignat
     */
    private synchronized String getColorForName(String name) {
        int idx = PLAYER_NAMES.indexOf(name);
        if (idx < 0) idx = 0; // fallback si el nom no està a la llista
        return PLAYER_COLORS.get(idx % PLAYER_COLORS.size());
    }

    // ----------------- Cicle de partida -----------------

    /** Inicia el compte enrere quan hi ha prou clients; despres crea la partida. */
    private void startCountdown() {
        synchronized (this) {
            if (countdownRunning || game != null) return;
            if (clients.connectedOrder().size() < REQUIRED_CLIENTS) return;
            countdownRunning = true;
        }

        new Thread(() -> {
            try {
                for (int i = 5; i >= 0; i--) {
                    // Si durant el compte enrere ja no hi ha els clients requerits, cancel·la
                    if (clients.connectedOrder().size() < REQUIRED_CLIENTS) {
                        return;
                    }
                    sendCountdownToAll(i);
                    if (i > 0) Thread.sleep(750); // ritme del compte enrere
                }

                // Compte enrere completat: crear la partida amb els dos primers clients.
                // Si durant el compte enrere se n'ha anat un i n'ha entrat un altre,
                // els dos que queden juguen igualment (el nou ocupa la plaça lliure).
                synchronized (Main.this) {
                    List<String> players = clients.connectedOrder();
                    if (players.size() >= REQUIRED_CLIENTS) {
                        String scenario = chosenScenario;
                        game = new Game(scenario, BOARD_WIDTH, BOARD_HEIGHT);
                        for (String name : players.subList(0, REQUIRED_CLIENTS)) {
                            game.addPlayer(name);
                        }
                        chosenScenario = DEFAULT_SCENARIO; // la proxima partida comenca amb el defecte
                        System.out.println("Partida iniciada amb escenari: " + scenario);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }
        }, "CountdownThread").start();
    }

    // ----------------- Helpers JSON -----------------

    /** Crea un objecte JSON amb el camp type inicialitzat. */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /** Envia de forma segura un payload i, si el socket no està connectat, el neteja del registre. */
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Envia un missatge a tots els clients connectats. */
    private void broadcastAll(String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            sendSafe(e.getKey(), payload);
        }
    }

    /** Envia l'estat complet (clients, tancs, bales, obstacles) a tots els clients. */
    private void broadcastStatus() {
        Game current;
        synchronized (this) {
            current = game;
        }
        if (current == null) {
            broadcastLobby();
            return;
        }

        JSONObject rst = msg(T_SERVER_DATA)
                        .put(K_CLIENTS_LIST, current.clientsJson())
                        .put(K_TANKS_LIST, current.tanksJson())
                        .put(K_BULLETS_LIST, current.bulletsJson())
                        .put(K_OBSTACLES_LIST, current.obstaclesJson())
                        .put(K_SCENARIO, current.scenario);

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            String name = clients.nameBySocket(conn);
            rst.put(K_CLIENT_NAME, name);
            sendSafe(conn, rst.toString());
        }
    }

    /** Envia la llista de jugadors (encara sense tancs) per alimentar la sala d'espera. */
    private void broadcastLobby() {
        JSONArray names = clients.currentNames();

        JSONObject rst = msg(T_SERVER_DATA)
                        .put(K_CLIENTS_LIST, names)
                        .put(K_TANKS_LIST, new JSONArray())
                        .put(K_BULLETS_LIST, new JSONArray())
                        .put(K_OBSTACLES_LIST, new JSONArray());

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            String name = clients.nameBySocket(conn);
            rst.put(K_CLIENT_NAME, name);
            sendSafe(conn, rst.toString());
        }
    }

    /** Envia a tots els clients el compte enrere. */
    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastAll(rst.toString());
    }

    // ----------------- WebSocketServer overrides -----------------

    /** Assigna un nom i color al client i, si ja hi ha dos clients, inicia el compte enrere. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        String color = getColorForName(name);

        System.out.println("WebSocket client connected: " + name + " (" + color + ")");
        broadcastLobby();
        startCountdown();
    }

    /** Elimina el client del registre; si era en partida, perd per abandonament. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);

        synchronized (this) {
            if (game != null && game.hasPlayer(name)) {
                game.forfeit(name);
                System.out.println("Partida decidida per abandonament de: " + name);
            }
        }

        System.out.println("WebSocket client disconnected: " + name);
    }

    /** Processa els missatges rebuts: nomes intencions d'input, mai estats. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // JSON invàlid
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_CLIENT_INPUT -> {
                String clientName = clients.nameBySocket(conn);
                if (clientName == null) return;
                synchronized (this) {
                    // Els espectadors (clients sense tanc) no tenen input: s'ignora.
                    if (game != null && game.hasPlayer(clientName)) {
                        game.setInput(clientName, InputData.fromJSON(obj));
                    }
                }
            }

            case "setScenario" -> {
                // El primer client que connecta (amfitrio) comunica l'escenari triat.
                // Si ja ha comencat el compte enrere o la partida, s'ignora.
                synchronized (this) {
                    if (game == null && !countdownRunning) {
                        String s = obj.optString(K_VALUE, DEFAULT_SCENARIO);
                        if (s.equals("corridor") || s.equals("maze")) {
                            chosenScenario = s;
                        } else {
                            chosenScenario = DEFAULT_SCENARIO;
                        }
                    }
                }
            }

            default -> {
                // Ignora altres tipus
            }
        }
    }

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        startTicker();
    }

    // ----------------- Lifecycle util -----------------

    /** Registra un shutdown hook per aturar netament el servidor en finalitzar el procés. */
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stopTicker();      // <- atura el bucle periòdic
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    /** Bloqueja el fil principal indefinidament fins que sigui interromput. */
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ----------------- Ticker util -----------------

    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        double dt = 1.0 / SEND_FPS;

        ticker.scheduleAtFixedRate(() -> {
            try {
                Game current;
                boolean finished;
                String winner;
                String loser;

                synchronized (this) {
                    if (game == null) return;

                    if (game.isOver()) {
                        // Partida acabada: avisar els clients i tancar la partida
                        winner = game.winner();
                        loser = game.loser();
                        game = null;
                    } else {
                        game.tick(dt);
                        if (!game.isOver()) {
                            broadcastStatus(); // l'estat encara és viu
                            return;
                        }
                        winner = game.winner();
                        loser = game.loser();
                        game = null;
                    }
                }

                JSONObject rst = msg(T_GAME_OVER).put(K_WINNER, winner).put(K_LOSER, loser);
                broadcastAll(rst.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Punt d'entrada. */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}
